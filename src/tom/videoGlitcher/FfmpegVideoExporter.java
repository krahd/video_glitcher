package tom.videoGlitcher;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

final class FfmpegVideoExporter {

    private final Process process;
    private final OutputStream stdin;
    private final FrameSpec frameSpec;
    private final byte[] frameBuffer;

    private FfmpegVideoExporter(Process process, FrameSpec frameSpec) {
        this.process = process;
        this.stdin = process.getOutputStream();
        this.frameSpec = frameSpec;
        this.frameBuffer = new byte[frameSpec.exportWidth() * frameSpec.exportHeight() * 3];
    }

    static FfmpegVideoExporter start(String outputFilename, int sourceWidth, int sourceHeight, int fps) throws IOException {
        FrameSpec frameSpec = frameSpecFor(sourceWidth, sourceHeight);
        ProcessBuilder processBuilder = new ProcessBuilder(buildCommand("ffmpeg", frameSpec.exportWidth(), frameSpec.exportHeight(), fps, outputFilename));
        processBuilder.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        processBuilder.redirectError(ProcessBuilder.Redirect.INHERIT);
        return new FfmpegVideoExporter(processBuilder.start(), frameSpec);
    }

    static FrameSpec frameSpecFor(int sourceWidth, int sourceHeight) {
        if (sourceWidth < 2 || sourceHeight < 2) {
            throw new IllegalArgumentException("Export size must be at least 2x2");
        }

        int exportWidth = sourceWidth - (sourceWidth % 2);
        int exportHeight = sourceHeight - (sourceHeight % 2);
        return new FrameSpec(sourceWidth, sourceHeight, exportWidth, exportHeight);
    }

    static List<String> buildCommand(String ffmpegBinary, int exportWidth, int exportHeight, int fps, String outputFilename) {
        return List.of(
                ffmpegBinary,
                "-y",
                "-f",
                "rawvideo",
                "-pixel_format",
                "rgb24",
                "-video_size",
                exportWidth + "x" + exportHeight,
                "-framerate",
                Integer.toString(fps),
                "-i",
                "-",
                "-an",
                "-c:v",
                "libx264",
                "-pix_fmt",
                "yuv420p",
                "-movflags",
                "+faststart",
                new File(outputFilename).getAbsolutePath());
    }

    void writeFrame(int[] argbPixels) throws IOException {
        int exportWidth = frameSpec.exportWidth();
        int exportHeight = frameSpec.exportHeight();
        int sourceWidth = frameSpec.sourceWidth();
        int bufferIndex = 0;

        for (int y = 0; y < exportHeight; y++) {
            int rowOffset = y * sourceWidth;
            for (int x = 0; x < exportWidth; x++) {
                int pixel = argbPixels[rowOffset + x];
                frameBuffer[bufferIndex++] = (byte) ((pixel >> 16) & 0xFF);
                frameBuffer[bufferIndex++] = (byte) ((pixel >> 8) & 0xFF);
                frameBuffer[bufferIndex++] = (byte) (pixel & 0xFF);
            }
        }

        stdin.write(frameBuffer);
    }

    void finish() throws IOException, InterruptedException {
        stdin.close();
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException("ffmpeg exited with code " + exitCode);
        }
    }

    void abort() {
        try {
            stdin.close();
        } catch (IOException ignored) {
        }
        process.destroyForcibly();
    }

    static record FrameSpec(int sourceWidth, int sourceHeight, int exportWidth, int exportHeight) {
    }
}