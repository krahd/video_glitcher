package tom.videoGlitcher;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Encodes to a private temporary file; never replaces a source or previous export. */
final class FfmpegVideoExporter {
    private static final int MAX_DIAGNOSTIC_BYTES = 8192;
    private final Process process;
    private final OutputStream stdin;
    private final FrameSpec frameSpec;
    private final byte[] frameBuffer;
    private final Path outputPath;
    private final Path temporaryPath;
    private final StringBuilder diagnostics = new StringBuilder();
    private final Thread diagnosticReader;
    private long framesWritten;
    private boolean closed;

    private FfmpegVideoExporter(Process process, FrameSpec frameSpec, Path outputPath, Path temporaryPath) {
        this.process = process;
        this.stdin = process.getOutputStream();
        this.frameSpec = frameSpec;
        this.outputPath = outputPath;
        this.temporaryPath = temporaryPath;
        this.frameBuffer = new byte[Math.multiplyExact(Math.multiplyExact(frameSpec.exportWidth(), frameSpec.exportHeight()), 3)];
        diagnosticReader = new Thread(() -> readDiagnostics(process.getErrorStream()), "ffmpeg-diagnostics");
        diagnosticReader.setDaemon(true);
        diagnosticReader.start();
    }

    static FfmpegVideoExporter start(String outputFilename, int sourceWidth, int sourceHeight, int fps) throws IOException {
        return start("ffmpeg", outputFilename, sourceWidth, sourceHeight, fps);
    }

    // Package-private binary injection lets tests exercise a missing or failed encoder without changing PATH.
    static FfmpegVideoExporter start(String binary, String outputFilename, int sourceWidth, int sourceHeight, int fps) throws IOException {
        FrameSpec spec = frameSpecFor(sourceWidth, sourceHeight);
        if (fps < 1 || fps > 240) throw new IllegalArgumentException("Export frame rate must be between 1 and 240");
        Path output = validateOutputPath(outputFilename);
        Path temporary = Files.createTempFile(output.getParent(), ".video-glitcher-", ".part.mp4");
        try {
            ProcessBuilder builder = new ProcessBuilder(buildCommand(binary, spec.exportWidth(), spec.exportHeight(), fps, temporary.toString()));
            builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            return new FfmpegVideoExporter(builder.start(), spec, output, temporary);
        } catch (IOException | RuntimeException exception) {
            Files.deleteIfExists(temporary);
            throw new IOException("Cannot start ffmpeg. Check it is installed on PATH and the output folder is writable. " + exception.getMessage(), exception);
        }
    }

    static Path validateOutputPath(String filename) throws IOException {
        if (filename == null || filename.isBlank()) throw new IOException("Choose a new MP4 filename.");
        Path output = Path.of(filename).toAbsolutePath().normalize();
        if (Files.exists(output, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("File already exists. Choose a new filename; existing files are never replaced: " + output);
        }
        Path parent = output.getParent();
        if (parent == null || !Files.isDirectory(parent) || !Files.isWritable(parent)) {
            throw new IOException("Choose an existing writable output folder: " + parent);
        }
        return output;
    }

    static FrameSpec frameSpecFor(int sourceWidth, int sourceHeight) {
        if (sourceWidth < 2 || sourceHeight < 2 || (long) sourceWidth * sourceHeight > Integer.MAX_VALUE / 3) {
            throw new IllegalArgumentException("Export size must be at least 2x2 and fit in a frame buffer");
        }
        return new FrameSpec(sourceWidth, sourceHeight, sourceWidth - sourceWidth % 2, sourceHeight - sourceHeight % 2);
    }

    static List<String> buildCommand(String ffmpegBinary, int exportWidth, int exportHeight, int fps, String outputFilename) {
        // -y applies ONLY to the private file created by start(), never to the user's destination.
        return List.of(ffmpegBinary, "-y", "-f", "rawvideo", "-pixel_format", "rgb24", "-video_size",
                exportWidth + "x" + exportHeight, "-framerate", Integer.toString(fps), "-i", "-", "-an", "-c:v", "libx264",
                "-pix_fmt", "yuv420p", "-movflags", "+faststart", "-hide_banner", "-loglevel", "error",
                new File(outputFilename).getAbsolutePath());
    }

    void writeFrame(int[] argbPixels) throws IOException {
        if (closed) throw new IOException("This export is already closed. Start a new export.");
        if (argbPixels == null || argbPixels.length < (long) frameSpec.sourceWidth() * frameSpec.sourceHeight()) {
            throw new IOException("Incomplete video frame; export stopped safely.");
        }
        int index = 0;
        for (int y = 0; y < frameSpec.exportHeight(); y++) {
            int row = y * frameSpec.sourceWidth();
            for (int x = 0; x < frameSpec.exportWidth(); x++) {
                int pixel = argbPixels[row + x];
                frameBuffer[index++] = (byte) (pixel >> 16);
                frameBuffer[index++] = (byte) (pixel >> 8);
                frameBuffer[index++] = (byte) pixel;
            }
        }
        try {
            stdin.write(frameBuffer);
            framesWritten++;
        } catch (IOException exception) {
            throw new IOException("Encoder stopped while writing. Check free disk space and ffmpeg. " + diagnosticSummary(), exception);
        }
    }

    void finish() throws IOException, InterruptedException {
        finish(30, TimeUnit.SECONDS);
    }

    void finish(long timeout, TimeUnit unit) throws IOException, InterruptedException {
        if (closed) throw new IOException("This export is already closed.");
        try {
            stdin.close();
            if (!process.waitFor(timeout, unit)) throw new IOException("ffmpeg did not finish in time. Retry a shorter export or check the output drive.");
            diagnosticReader.join(1000);
            if (process.exitValue() != 0) throw new IOException("ffmpeg failed (" + process.exitValue() + "). " + diagnosticSummary());
            if (framesWritten == 0 || Files.size(temporaryPath) == 0) throw new IOException("No video frames were saved. Play the clip and try again.");
            publishWithoutReplacing();
            closed = true;
        } finally {
            if (!closed) abort();
            else Files.deleteIfExists(temporaryPath);
        }
    }

    private void publishWithoutReplacing() throws IOException {
        // CREATE_NEW provides a no-clobber guarantee even if another app creates the destination during encoding.
        // Copy rather than rename: a provider's check-then-rename may replace a file in that race.
        boolean created = false;
        try (OutputStream destination = Files.newOutputStream(outputPath, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            created = true;
            Files.copy(temporaryPath, destination);
        } catch (IOException exception) {
            if (created) Files.deleteIfExists(outputPath);
            throw new IOException("Could not save export. Choose a new filename and check free disk space. " + exception.getMessage(), exception);
        }
    }

    void abort() {
        if (closed) return;
        closed = true;
        process.destroyForcibly();
        try { stdin.close(); } catch (IOException ignored) { }
        try {
            process.waitFor(5, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
        try { Files.deleteIfExists(temporaryPath); } catch (IOException ignored) { }
    }

    long framesWritten() { return framesWritten; }

    private void readDiagnostics(InputStream input) {
        byte[] buffer = new byte[1024];
        try (input) {
            int count;
            while ((count = input.read(buffer)) != -1) {
                synchronized (diagnostics) {
                    diagnostics.append(new String(buffer, 0, count, StandardCharsets.UTF_8));
                    if (diagnostics.length() > MAX_DIAGNOSTIC_BYTES) diagnostics.delete(0, diagnostics.length() - MAX_DIAGNOSTIC_BYTES);
                }
            }
        } catch (IOException ignored) { }
    }

    private String diagnosticSummary() {
        synchronized (diagnostics) {
            return diagnostics.toString().replace('\n', ' ').replace('\r', ' ').trim();
        }
    }

    static record FrameSpec(int sourceWidth, int sourceHeight, int exportWidth, int exportHeight) { }
}
