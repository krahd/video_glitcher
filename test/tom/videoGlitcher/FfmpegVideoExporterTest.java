package tom.videoGlitcher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Real encoder and decoded-pixel tests. Requires ffmpeg and ffprobe on PATH. */
public final class FfmpegVideoExporterTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        if (args.length == 2 && args[0].equals("--exit-child")) {
            Path output = Path.of(args[1]).resolve("exit.mp4");
            FfmpegVideoExporter exporter = FfmpegVideoExporter.start(output.toString(), 640, 360, 24);
            exporter.writeFrame(new int[640 * 360]);
            exporter.abort(); // Same nonblocking dispose + System.exit sequence as Processing.
            System.exit(0);
        }
        Path directory = Files.createTempDirectory("video-glitcher-tests-");
        try {
            testSuccessfulOutput(directory);
            testExistingOutput(directory);
            testOutputAppearsDuringEncoding(directory);
            testCancelAndRetry(directory);
            testInvalidFrames(directory);
            testMissingEncoderAndFolder(directory);
            testEmptyExport(directory);
            testNormalApplicationExit(directory);
            if (!System.getProperty("os.name").startsWith("Windows")) {
                testEncoderFailure(directory);
                testEncoderTimeout(directory);
                testNonReadingEncoder(directory);
                testSymlinkDestination(directory);
            }
            assertNoStagingFiles(directory);
            System.out.println("All FfmpegVideoExporter integration tests passed (" + checks + " checks).");
        } finally {
            try (var paths = Files.walk(directory)) {
                for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }
    }

    private static void testSuccessfulOutput(Path directory) throws Exception {
        Path output = directory.resolve("clip with spaces ü.mp4");
        FfmpegVideoExporter exporter = FfmpegVideoExporter.start(output.toString(), 65, 49, 24);
        int[] frame = new int[65 * 49];
        Arrays.fill(frame, 0xffff0000);
        // Green odd-width/height borders must be cropped, not leak through a wrong row stride.
        for (int y = 0; y < 49; y++) frame[y * 65 + 64] = 0xff00ff00;
        Arrays.fill(frame, 48 * 65, frame.length, 0xff00ff00);
        for (int i = 0; i < 24; i++) { exporter.writeFrame(frame); Thread.sleep(42); }
        check(!Files.exists(output), "Destination must not exist before finish");
        exporter.finish();
        check(Files.size(output) > 0, "Output must not be empty");
        String probe = new String(run("ffprobe", "-v", "error", "-count_frames", "-show_entries",
                "stream=codec_name,width,height,nb_read_frames,r_frame_rate:format=duration", "-of", "default=noprint_wrappers=1", output.toString()));
        for (String expected : List.of("codec_name=h264", "width=64", "height=48", "r_frame_rate=24/1", "nb_read_frames=24", "duration=1.000000"))
            check(probe.contains(expected), "Missing output property: " + expected + " in " + probe);
        check(!probe.contains("codec_name=aac"), "Exports must remain explicitly silent");
        byte[] decoded = run("ffmpeg", "-v", "error", "-i", output.toString(), "-frames:v", "1", "-f", "rawvideo", "-pix_fmt", "rgb24", "-");
        check(decoded.length == 64 * 48 * 3, "Decoded dimensions must match even crop");
        for (int i = 0; i < decoded.length; i += 3) {
            if ((decoded[i] & 255) < 240 || (decoded[i + 1] & 255) > 12 || (decoded[i + 2] & 255) > 12)
                throw new AssertionError("Decoded colour regression at pixel " + i / 3);
        }
        checks++;
        expectIOException(exporter::finish, "Repeated finish must be rejected");
        expectIOException(() -> exporter.writeFrame(frame), "Writing after finish must be rejected");
        exporter.abort();
        check(Files.exists(output), "Abort after finish must preserve saved output");
        assertNoStagingFiles(directory);
    }

    private static void testExistingOutput(Path directory) throws Exception {
        Path output = directory.resolve("source.mp4");
        Files.writeString(output, "valuable original");
        expectIOException(() -> FfmpegVideoExporter.start(output.toString(), 4, 4, 24), "Existing source/output must be refused");
        check(Files.readString(output).equals("valuable original"), "Original must survive unchanged");
    }

    private static void testOutputAppearsDuringEncoding(Path directory) throws Exception {
        Path output = directory.resolve("race.mp4");
        FfmpegVideoExporter exporter = FfmpegVideoExporter.start(output.toString(), 4, 4, 24);
        exporter.writeFrame(new int[16]);
        Files.writeString(output, "created by another app");
        expectIOException(exporter::finish, "Newly appeared destination must not be replaced");
        check(Files.readString(output).equals("created by another app"), "Concurrent destination preserved");
        assertNoStagingFiles(directory);
    }

    private static void testCancelAndRetry(Path directory) throws Exception {
        Path output = directory.resolve("cancel.mp4");
        FfmpegVideoExporter exporter = FfmpegVideoExporter.start(output.toString(), 4, 4, 24);
        exporter.writeFrame(new int[16]);
        exporter.abort();
        exporter.abort();
        check(exporter.awaitCleanup(10, TimeUnit.SECONDS), "Cancellation cleanup must finish");
        check(!Files.exists(output), "Cancel must not publish output");
        assertNoStagingFiles(directory);
        exporter = FfmpegVideoExporter.start(output.toString(), 4, 4, 24);
        exporter.writeFrame(new int[16]);
        exporter.finish();
        check(Files.exists(output), "Retry after cancellation must succeed");
    }

    private static void testInvalidFrames(Path directory) throws Exception {
        FfmpegVideoExporter exporter = FfmpegVideoExporter.start(directory.resolve("invalid.mp4").toString(), 4, 4, 24);
        expectIOException(() -> exporter.writeFrame(new int[1]), "Short frame must fail descriptively");
        expectIOException(() -> exporter.writeFrame(null), "Null frame must fail descriptively");
        exporter.abort();
        check(exporter.awaitCleanup(10, TimeUnit.SECONDS), "Invalid-frame cleanup must finish");
        for (int[] dimensions : new int[][]{{0, 4}, {4, 1}, {Integer.MAX_VALUE, 4}}) {
            try { FfmpegVideoExporter.frameSpecFor(dimensions[0], dimensions[1]); throw new AssertionError("Invalid dimensions accepted"); }
            catch (IllegalArgumentException expected) { checks++; }
        }
        try { FfmpegVideoExporter.start(directory.resolve("fps.mp4").toString(), 4, 4, 0); throw new AssertionError("Zero fps accepted"); }
        catch (IllegalArgumentException expected) { checks++; }
    }

    private static void testMissingEncoderAndFolder(Path directory) throws Exception {
        expectIOException(() -> FfmpegVideoExporter.start(directory.resolve("missing-ffmpeg").toString(), directory.resolve("missing.mp4").toString(), 4, 4, 24), "Missing encoder must be actionable");
        expectIOException(() -> FfmpegVideoExporter.start(directory.resolve("no-folder/output.mp4").toString(), 4, 4, 24), "Missing folder refused");
        assertNoStagingFiles(directory);
    }

    private static void testEmptyExport(Path directory) throws Exception {
        Path output = directory.resolve("empty.mp4");
        FfmpegVideoExporter exporter = FfmpegVideoExporter.start(output.toString(), 4, 4, 24);
        expectIOException(exporter::finish, "Zero-frame export must not count as success");
        check(!Files.exists(output), "Zero-frame export must not publish a file");
    }

    private static void testNormalApplicationExit(Path directory) throws Exception {
        String java = Path.of(System.getProperty("java.home"), "bin", System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java").toString();
        for (int i = 0; i < 10; i++) {
            Process child = new ProcessBuilder(java, "-cp", System.getProperty("java.class.path"),
                    FfmpegVideoExporterTest.class.getName(), "--exit-child", directory.toString())
                    .inheritIO().start();
            check(child.waitFor(15, TimeUnit.SECONDS) && child.exitValue() == 0, "Normal exit must finish cleanup promptly");
            check(!Files.exists(directory.resolve("exit.mp4")), "Normal exit must not publish unfinished output");
            assertNoStagingFiles(directory);
        }
    }

    private static void testEncoderFailure(Path directory) throws Exception {
        Path binary = directory.resolve("failed-encoder.sh");
        Files.writeString(binary, "#!/bin/sh\ncat >/dev/null\necho codec-test-failure >&2\nexit 7\n");
        check(binary.toFile().setExecutable(true), "Test encoder executable");
        Path output = directory.resolve("failed.mp4");
        FfmpegVideoExporter exporter = FfmpegVideoExporter.start(binary.toString(), output.toString(), 4, 4, 24);
        exporter.writeFrame(new int[16]);
        try { exporter.finish(); throw new AssertionError("Failed encoder accepted"); }
        catch (IOException expected) { check(expected.getMessage().contains("codec-test-failure"), "Encoder diagnostic must reach error"); }
        check(!Files.exists(output), "Failed encoder must not publish");
    }

    private static void testEncoderTimeout(Path directory) throws Exception {
        Path binary = directory.resolve("stalled-encoder.sh");
        Files.writeString(binary, "#!/bin/sh\ncat >/dev/null\nexec sleep 60\n");
        check(binary.toFile().setExecutable(true), "Timeout test encoder executable");
        Path output = directory.resolve("timeout.mp4");
        FfmpegVideoExporter exporter = FfmpegVideoExporter.start(binary.toString(), output.toString(), 4, 4, 24);
        exporter.writeFrame(new int[16]);
        long started = System.nanoTime();
        expectIOException(() -> exporter.finish(100, TimeUnit.MILLISECONDS), "Stalled encoder must time out");
        check(TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - started) < 10, "Timeout must be bounded");
        check(!Files.exists(output), "Timeout must not publish");
    }

    private static void testNonReadingEncoder(Path directory) throws Exception {
        Path binary = directory.resolve("non-reading-encoder.sh");
        Files.writeString(binary, "#!/bin/sh\nexec sleep 60\n");
        check(binary.toFile().setExecutable(true), "Non-reading test encoder executable");
        Path output = directory.resolve("blocked-pipe.mp4");
        FfmpegVideoExporter exporter = FfmpegVideoExporter.start(binary.toString(), output.toString(), 640, 360, 24);
        int[] frame = new int[640 * 360];
        long started = System.nanoTime();
        exporter.writeFrame(frame); // Far larger than a pipe buffer; must not block this caller.
        check(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) < 1000, "Render thread must not wait for a blocked pipe");
        boolean overflow = false;
        for (int i = 0; i < 10; i++) {
            try { exporter.writeFrame(frame); } catch (IOException expected) { overflow = true; break; }
        }
        check(overflow, "Bounded queue must fail rather than silently drop frames or grow indefinitely");
        started = System.nanoTime();
        check(exporter.abort(), "Blocked write must be cancellable");
        check(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) < 1000, "Cancel must return promptly");
        check(exporter.awaitCleanup(10, TimeUnit.SECONDS), "Blocked writer/process must terminate");
        check(!Files.exists(output), "Blocked export must not publish");
        assertNoStagingFiles(directory);
        exporter = FfmpegVideoExporter.start(binary.toString(), output.toString(), 640, 360, 24);
        exporter.writeFrame(frame);
        FfmpegVideoExporter finishing = exporter;
        started = System.nanoTime();
        expectIOException(() -> finishing.finish(100, TimeUnit.MILLISECONDS), "Deadline must include pipe write/close, not just process wait");
        check(TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - started) < 10, "Blocked-pipe finish must terminate");
        assertNoStagingFiles(directory);
    }

    private static void testSymlinkDestination(Path directory) throws Exception {
        Path source = directory.resolve("source.mp4");
        Path link = directory.resolve("source-link.mp4");
        Files.createSymbolicLink(link, source);
        expectIOException(() -> FfmpegVideoExporter.start(link.toString(), 4, 4, 24), "Source symlink refused");
        Path dangling = directory.resolve("dangling.mp4");
        Files.createSymbolicLink(dangling, directory.resolve("absent-target"));
        expectIOException(() -> FfmpegVideoExporter.start(dangling.toString(), 4, 4, 24), "Dangling symlink refused");
    }

    private static byte[] run(String... command) throws Exception {
        ProcessBuilder builder = new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.INHERIT);
        FfmpegVideoExporter.configureEncoderEnvironment(builder.environment());
        Process process = builder.start();
        byte[] output = process.getInputStream().readAllBytes();
        check(process.waitFor(30, TimeUnit.SECONDS) && process.exitValue() == 0, "Command failed: " + Arrays.toString(command));
        return output;
    }
    private static void assertNoStagingFiles(Path directory) throws IOException {
        try (var paths = Files.list(directory)) { check(paths.noneMatch(p -> p.getFileName().toString().startsWith(".video-glitcher-")), "Temporary encoder files must be cleaned"); }
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); checks++; }
    private static void expectIOException(ThrowingRunnable action, String message) throws Exception {
        try { action.run(); throw new AssertionError(message); } catch (IOException expected) { checks++; }
    }
    @FunctionalInterface private interface ThrowingRunnable { void run() throws Exception; }
}
