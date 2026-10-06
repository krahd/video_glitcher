package tom.videoGlitcher;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
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
    private enum State { OPEN, FINISHING, PUBLISHING, FINISHED, ABORTED }
    private final AtomicReference<State> state = new AtomicReference<>(State.OPEN);
    private final ArrayBlockingQueue<byte[]> pendingFrames;
    private final CountDownLatch writerStopped = new CountDownLatch(1);
    private final CountDownLatch cleanupFinished = new CountDownLatch(1);
    private volatile IOException writerFailure;
    private final Thread frameWriter;
    private final Thread shutdownHook;

    private FfmpegVideoExporter(Process process, FrameSpec frameSpec, Path outputPath, Path temporaryPath) {
        this.process = process;
        this.stdin = process.getOutputStream();
        this.frameSpec = frameSpec;
        this.outputPath = outputPath;
        this.temporaryPath = temporaryPath;
        this.frameBuffer = new byte[Math.multiplyExact(Math.multiplyExact(frameSpec.exportWidth(), frameSpec.exportHeight()), 3)];
        // Allow short encoder startup/scheduling delays without unbounded buffering. At least one frame fits.
        int queueCapacity = Math.max(1, Math.min(48, (64 * 1024 * 1024) / frameBuffer.length));
        this.pendingFrames = new ArrayBlockingQueue<>(queueCapacity);
        diagnosticReader = new Thread(() -> readDiagnostics(process.getErrorStream()), "ffmpeg-diagnostics");
        diagnosticReader.setDaemon(true);
        diagnosticReader.start();
        frameWriter = new Thread(this::writeQueuedFrames, "ffmpeg-frame-writer");
        frameWriter.setDaemon(true);
        frameWriter.start();
        // Processing uses System.exit, which does not wait for ordinary cleanup threads.
        shutdownHook = new Thread(() -> {
            abort();
            try { cleanupFinished.await(10, TimeUnit.SECONDS); }
            catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
        }, "ffmpeg-shutdown-cleanup");
        Runtime.getRuntime().addShutdownHook(shutdownHook);
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
            configureEncoderEnvironment(builder.environment());
            builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            return new FfmpegVideoExporter(builder.start(), spec, output, temporary);
        } catch (IOException | RuntimeException exception) {
            Files.deleteIfExists(temporary);
            throw new IOException("Cannot start ffmpeg. Check it is installed on PATH and the output folder is writable. " + exception.getMessage(), exception);
        }
    }

    static void configureEncoderEnvironment(java.util.Map<String, String> environment) {
        // The Java video runtime may need older bundled libraries; never leak that override into system ffmpeg.
        String original = environment.remove("VIDEO_GLITCHER_FFMPEG_LD_LIBRARY_PATH");
        if (original != null) {
            if (original.isEmpty()) environment.remove("LD_LIBRARY_PATH");
            else environment.put("LD_LIBRARY_PATH", original);
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
        if (state.get() != State.OPEN) throw new IOException("This export is already closing. Start a new export.");
        if (writerFailure != null) throw writerFailure;
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
        // Never wait for ffmpeg on the Processing/UI thread. A bounded queue prevents unlimited memory use.
        if (!pendingFrames.offer(frameBuffer.clone())) {
            throw new IOException("Encoder cannot keep up; export stopped without dropping frames. Try a smaller preview canvas or a faster local drive.");
        }
        framesWritten++;
    }

    private void writeQueuedFrames() {
        try {
            while (state.get() != State.ABORTED) {
                byte[] frame = pendingFrames.poll(25, TimeUnit.MILLISECONDS);
                if (frame != null) stdin.write(frame);
                else if (state.get() == State.FINISHING) break;
            }
            stdin.close();
        } catch (IOException exception) {
            writerFailure = new IOException("Encoder stopped while writing. Check free disk space and ffmpeg. " + diagnosticSummary(), exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            writerFailure = new IOException("Encoder frame writer interrupted", exception);
        } finally {
            writerStopped.countDown();
        }
    }

    void finish() throws IOException, InterruptedException {
        finish(30, TimeUnit.SECONDS);
    }

    void finish(long timeout, TimeUnit unit) throws IOException, InterruptedException {
        if (!state.compareAndSet(State.OPEN, State.FINISHING)) throw new IOException("This export is already closing.");
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        try {
            // Includes pipe writes AND stdin.close, not merely the process exit wait.
            if (!writerStopped.await(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS)
                    || !process.waitFor(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS)) {
                throw new IOException("ffmpeg did not finish in time. Retry a shorter export or check the output drive.");
            }
            diagnosticReader.join(1000);
            if (process.exitValue() != 0) throw new IOException("ffmpeg failed (" + process.exitValue() + "). " + diagnosticSummary());
            if (writerFailure != null) throw writerFailure;
            if (framesWritten == 0 || Files.size(temporaryPath) == 0) throw new IOException("No video frames were saved. Play the clip and try again.");
            if (!state.compareAndSet(State.FINISHING, State.PUBLISHING)) throw new IOException("Export cancelled.");
            publishWithoutReplacing();
            state.set(State.FINISHED);
        } finally {
            if (state.get() != State.FINISHED) {
                state.set(State.ABORTED);
                cleanUp(); // finish runs off the UI thread; cleanup remains bounded for ordinary processes.
            } else {
                try { Files.deleteIfExists(temporaryPath); } catch (IOException exception) {
                    synchronized (diagnostics) { diagnostics.append(" Temporary file cleanup failed: ").append(temporaryPath); }
                }
                cleanupFinished.countDown();
                removeShutdownHook();
            }
        }
    }

    private void publishWithoutReplacing() throws IOException {
        // Same-directory hard-link creation is a single, no-replace publication operation.
        // Never remove the destination on failure: another process may own or replace that path.
        try {
            Files.createLink(outputPath, temporaryPath);
        } catch (IOException | UnsupportedOperationException exception) {
            throw new IOException("Could not save export safely. Choose a NEW filename on a local drive supporting hard links. " + exception.getMessage(), exception);
        }
    }

    /** Non-blocking cancellation. False means publication has already committed or started. */
    boolean abort() {
        State current;
        do {
            current = state.get();
            if (current == State.ABORTED) return true;
            if (current == State.FINISHED || current == State.PUBLISHING) return false;
        } while (!state.compareAndSet(current, State.ABORTED));
        Thread cleanup = new Thread(this::cleanUp, "ffmpeg-cancel-cleanup");
        // Non-daemon: normal JVM shutdown gives cancellation its bounded cleanup opportunity.
        cleanup.start();
        return true;
    }

    private void cleanUp() {
        pendingFrames.clear();
        // PATH may resolve to a wrapper script. Terminate its current encoder descendants as well.
        process.descendants().forEach(child -> { if (child.isAlive()) child.destroyForcibly(); });
        process.destroyForcibly();
        frameWriter.interrupt();
        try {
            process.waitFor(5, TimeUnit.SECONDS);
            writerStopped.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
        try { Files.deleteIfExists(temporaryPath); } catch (IOException ignored) { }
        cleanupFinished.countDown();
        removeShutdownHook();
    }

    private void removeShutdownHook() {
        try { Runtime.getRuntime().removeShutdownHook(shutdownHook); }
        catch (IllegalStateException ignored) { /* JVM shutdown already in progress. */ }
    }

    boolean awaitCleanup(long timeout, TimeUnit unit) throws InterruptedException {
        return cleanupFinished.await(timeout, unit);
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
