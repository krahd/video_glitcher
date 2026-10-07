package tom.videoGlitcher;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

/** Owned encoder results and injected preview-restore errors; no native playback acceptance. */
public final class VideoGlitcherExportOutcomeTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        if (args.length == 3 && args[0].equals("--smoke-outcome-child")) {
            runSmokeOutcomeChild(Path.of(args[1]), Boolean.parseBoolean(args[2]));
            throw new AssertionError("Smoke outcome child did not exit");
        }
        throw new IllegalArgumentException("Only the owned smoke outcome child is supported");
    }
    static void run(Path directory) throws Exception {
        for (String outcome : new String[]{"finish", "cancel", "failure", "collision"}) {
            for (boolean loop : new boolean[]{false, true}) {
                for (boolean cleanupThrows : new boolean[]{false, true}) testOutcome(directory, outcome, loop, cleanupThrows);
            }
        }
        testNoRestoreWhenPausedOrLive(directory, false);
        testNoRestoreWhenPausedOrLive(directory, true);
        testQueuedReplacement(directory, false);
        testQueuedReplacement(directory, true);
        testSmokeOutcome(directory, false);
        testSmokeOutcome(directory, true);
        System.out.println("Export outcome recovery checks passed (" + checks + " checks; real outputs, injected preview failures).");
    }

    private static void testOutcome(Path root, String outcome, boolean loop, boolean cleanupThrows) throws Exception {
        Path directory = Files.createDirectory(root.resolve("outcome-" + outcome + "-" + loop + "-" + cleanupThrows));
        TestApp app = app(loop); List<FfmpegVideoExporter> encoders = new ArrayList<>();
        Path source = directory.resolve("owned-source.mp4"); Files.writeString(source, "owned source marker");
        Path output = directory.resolve("first.mp4");
        try {
            PreviewMovie movie = movie(app); movie.cleanupThrows = cleanupThrows;
            FfmpegVideoExporter encoder = start(app, movie, output, true); encoders.add(encoder); writeGreenFrames(encoder);
            movie.failResume = true;
            if (outcome.equals("collision")) Files.writeString(output, "owned concurrent destination");
            try {
                if (outcome.equals("finish") || outcome.equals("collision")) {
                    app.stopExport();
                    String error = finishing(app).get(10, TimeUnit.SECONDS);
                    check((error == null) == outcome.equals("finish"), "Finalisation result must reflect real encoder/publication success");
                    if (outcome.equals("finish")) {
                        app.cancelExport(); // Publication won already; this must not turn a saved output into a cancellation.
                        check(Files.exists(output), "Cancel after committed publication must preserve saved output");
                    }
                    invoke(app, "completePendingExport");
                } else if (outcome.equals("cancel")) app.cancelExport();
                else invoke(app, "failExport", "Export failed: owned encoder error");
            } catch (RuntimeException | InvocationTargetException exception) {
                throw new AssertionError("Preview restoration must not throw away an export outcome", exception);
            }
            String status = (String) get(app, "statusMessage");
            check(status.contains("Preview could not resume") && status.contains("reload"),
                    "An unusable preview must have an explicit reload instruction");
            check(outcome.equals("finish") ? status.startsWith("Saved export: " + output)
                            : outcome.equals("cancel") ? status.startsWith("Export cancelled.")
                            : status.startsWith("Export failed:"),
                    "Saved, cancelled and failed export outcomes must remain distinct from preview failure");
            if (outcome.equals("failure")) check(status.contains("owned encoder error"), "Original encoder error must not be masked");
            check(!(boolean) get(app, "exporting") && get(app, "pendingExportFinish") == null && get(app, "videoExporter") == null
                            && get(app, "exportMode").toString().equals("NONE"), "Terminal export state must remain consistent");
            check(get(app, "video") == null && !(boolean) get(app, "movieReady") && movie.stops == 1 && movie.disposals == 1,
                    "Failed preview must be retired once even if native cleanup throws");
            check(encoder.awaitCleanup(10, TimeUnit.SECONDS), "Terminal encoder cleanup must finish");
            byte[] firstResult = Files.exists(output) ? Files.readAllBytes(output) : null;
            if (outcome.equals("finish")) verifyGreenFrames(directory, output);
            else if (outcome.equals("collision")) check(Files.readString(output).equals("owned concurrent destination"), "Concurrent destination must be preserved");
            else check(!Files.exists(output), "Cancelled/failed output must not be published");
            app.cancelExport(); app.stopExport();
            check(get(app, "statusMessage").equals(status), "Repeated terminal Cancel/Stop must not erase the actual outcome");

            for (int attempt = 0; attempt < 2; attempt++) {
                set(app, "selectingVideo", true); app.videoSelected(source.toFile()); drain(app);
                PreviewMovie retry = (PreviewMovie) get(app, "video");
                updateMovie(app, retry);
                Path retryOutput = directory.resolve("retry-" + attempt + ".mp4");
                set(app, "exportFilename", retryOutput.toString()); invoke(app, "startFullProcessExport");
                FfmpegVideoExporter retried = (FfmpegVideoExporter) get(app, "videoExporter"); encoders.add(retried);
                writeGreenFrames(retried); app.stopExport();
                check(finishing(app).get(10, TimeUnit.SECONDS) == null, "Reload/retry must really finish encoding");
                invoke(app, "completePendingExport");
                check(get(app, "statusMessage").equals("Saved export: " + retryOutput), "Healthy retry must clear the old warning and show its own output");
                verifyGreenFrames(directory, retryOutput);
            }
            check(Files.readString(source).equals("owned source marker"), "No recovery path may modify the owned source marker");
            check(firstResult == null ? !Files.exists(output) : Arrays.equals(firstResult, Files.readAllBytes(output)),
                    "Later retries must not alter any earlier saved or concurrent output");
            try (var files = Files.list(directory)) {
                check(files.noneMatch(path -> path.getFileName().toString().startsWith(".video-glitcher-")), "No staging file may survive terminal outcomes and retries");
            }
        } finally { clean(encoders); }
    }

    private static void testNoRestoreWhenPausedOrLive(Path root, boolean live) throws Exception {
        TestApp app = app(true); PreviewMovie movie = movie(app);
        Path output = root.resolve("no-restore-" + live + ".mp4");
        FfmpegVideoExporter encoder = start(app, movie, output, !live);
        try {
            writeGreenFrames(encoder); movie.failResume = true;
            if (!live) { set(app, "paused", true); set(app, "pausedFrame", new processing.core.PImage(4, 4)); }
            app.stopExport(); check(finishing(app).get(10, TimeUnit.SECONDS) == null, "Control export must finish");
            invoke(app, "completePendingExport");
            check(get(app, "video") == movie && movie.disposals == 0 && get(app, "statusMessage").equals("Saved export: " + output),
                    "Paused/full or live completion must not needlessly restart or retire preview");
        } finally { clean(List.of(encoder)); }
    }

    private static void testQueuedReplacement(Path root, boolean cancel) throws Exception {
        Path directory = Files.createDirectory(root.resolve("outcome-next-" + cancel));
        TestApp app = app(true); PreviewMovie previous = movie(app);
        Path output = directory.resolve("before-next.mp4"); FfmpegVideoExporter encoder = start(app, previous, output, true);
        try {
            writeGreenFrames(encoder); previous.failResume = true;
            set(app, "pendingVideoFile", directory.resolve("selected-next.mp4").toFile());
            if (cancel) app.cancelExport();
            else { app.stopExport(); check(finishing(app).get(10, TimeUnit.SECONDS) == null, "Queued replacement output must finish"); invoke(app, "completePendingExport"); }
            check(get(app, "video") != previous && get(app, "video") != null && get(app, "pendingVideoFile") == null,
                    "Finish/cancel must load the selected next clip without losing the request");
            check(previous.failedResumeCalls == 0 && previous.disposals == 1,
                    "A movie about to be replaced must not be restarted before release");
            check(encoder.awaitCleanup(10, TimeUnit.SECONDS) && Files.exists(output) != cancel,
                    "Queued replacement must preserve the actual save/cancel outcome");
        } finally { clean(List.of(encoder)); }
    }

    private static void testSmokeOutcome(Path root, boolean collision) throws Exception {
        Path directory = Files.createDirectory(root.resolve("smoke-outcome-" + collision));
        Path log = directory.resolve("child.log");
        String java = Path.of(System.getProperty("java.home"), "bin", System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java").toString();
        Process child = new ProcessBuilder(java, "-Djava.awt.headless=true", "-cp", System.getProperty("java.class.path"),
                VideoGlitcherExportOutcomeTest.class.getName(), "--smoke-outcome-child", directory.toString(), Boolean.toString(collision))
                .redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            check(child.waitFor(15, TimeUnit.SECONDS) && child.exitValue() == (collision ? 1 : 0),
                    "Smoke exit must reflect the encoder/publication result independently of preview restoration");
            String report = Files.readString(log);
            check(report.contains("Preview could not resume") && report.contains(collision ? "Export failed:" : "Smoke export completed"),
                    "Smoke report must preserve the export result and disclose the preview warning");
            Path output = directory.resolve("owned.mp4");
            if (collision) check(Files.readString(output).equals("owned concurrent destination"), "Failed smoke save must preserve the concurrent output");
            else verifyGreenFrames(directory, output);
            try (var files = Files.list(directory)) {
                check(files.noneMatch(path -> path.getFileName().toString().endsWith(".part.mp4")), "Smoke outcome must leave no staging orphan");
            }
        } finally {
            if (child.isAlive()) {
                child.descendants().forEach(process -> process.destroyForcibly());
                child.destroyForcibly(); child.waitFor(5, TimeUnit.SECONDS);
            }
        }
    }

    private static void runSmokeOutcomeChild(Path directory, boolean collision) throws Exception {
        TestApp app = app(true);
        Class<?> options = Class.forName("tom.videoGlitcher.VideoGlitcher$LaunchOptions");
        Method parse = options.getDeclaredMethod("parse", String[].class); parse.setAccessible(true);
        set(app, "launchOptions", parse.invoke(null, (Object) new String[]{"--smoke-test", "--auto-process"}));
        Field surface = processing.core.PApplet.class.getDeclaredField("surface"); surface.setAccessible(true);
        surface.set(app, java.lang.reflect.Proxy.newProxyInstance(processing.core.PSurface.class.getClassLoader(),
                new Class[]{processing.core.PSurface.class}, (proxy, method, values) -> method.getReturnType() == boolean.class ? false : null));
        PreviewMovie movie = movie(app); Path output = directory.resolve("owned.mp4");
        FfmpegVideoExporter encoder = start(app, movie, output, true); writeGreenFrames(encoder);
        movie.failResume = true;
        if (collision) Files.writeString(output, "owned concurrent destination");
        app.stopExport(); finishing(app).get(10, TimeUnit.SECONDS); invoke(app, "completePendingExport");
    }

    private static TestApp app(boolean loop) throws Exception {
        TestApp app = new TestApp(); app.width = 4; app.height = 4; set(app, "loopPlayback", loop); return app;
    }
    private static FfmpegVideoExporter start(TestApp app, PreviewMovie movie, Path output, boolean full) throws Exception {
        set(app, "video", movie); set(app, "movieReady", true); set(app, "exportFilename", output.toString());
        invoke(app, full ? "startFullProcessExport" : "startInteractiveExport");
        return (FfmpegVideoExporter) get(app, "videoExporter");
    }
    private static void writeGreenFrames(FfmpegVideoExporter encoder) throws Exception {
        int[] frame = new int[16]; Arrays.fill(frame, 0xff00ff00); for (int i = 0; i < 8; i++) encoder.writeFrame(frame);
    }
    private static void verifyGreenFrames(Path directory, Path output) throws Exception {
        byte[] pixels = command(directory, "ffmpeg", "-v", "error", "-i", output.toString(), "-f", "rawvideo", "-pix_fmt", "rgb24", "-");
        check(pixels.length == 8 * 4 * 4 * 3, "Saved MP4 must decode exactly the eight captured frames");
        for (int i = 0; i < pixels.length; i += 3) {
            if ((pixels[i] & 255) > 12 || (pixels[i + 1] & 255) < 240 || (pixels[i + 2] & 255) > 12) throw new AssertionError("Saved output colour changed during preview recovery");
        }
        checks++;
    }
    private static byte[] command(Path directory, String... command) throws Exception {
        Path output = Files.createTempFile(directory, "owned-command-", ".tmp"); Process process = null;
        try {
            ProcessBuilder builder = new ProcessBuilder(command).redirectOutput(output.toFile()).redirectError(ProcessBuilder.Redirect.INHERIT);
            FfmpegVideoExporter.configureEncoderEnvironment(builder.environment()); process = builder.start();
            check(process.waitFor(10, TimeUnit.SECONDS) && process.exitValue() == 0, "Owned decode command must finish successfully");
            return Files.readAllBytes(output);
        } finally {
            if (process != null && process.isAlive()) { process.destroyForcibly(); process.waitFor(5, TimeUnit.SECONDS); }
            Files.deleteIfExists(output);
        }
    }
    private static final class TestApp extends VideoGlitcher {
        @Override processing.video.Movie createMovie(String source) {
            try { return movie(this); } catch (Exception exception) { throw new RuntimeException(exception); }
        }
    }
    public static final class PreviewMovie extends processing.video.Movie {
        boolean failResume, cleanupThrows; int failedResumeCalls, stops, disposals;
        private PreviewMovie() { super(null, "unused"); }
        @Override public void play() { if (failResume) { failedResumeCalls++; throw new IllegalStateException("owned preview failure"); } }
        @Override public void loop() { play(); }
        @Override public void noLoop() { }
        @Override public void jump(float value) { }
        @Override public float duration() { return 3; }
        @Override public float time() { return 0; }
        @Override public void read() { }
        @Override public void stop() { stops++; if (cleanupThrows) throw new IllegalStateException("owned stop failure"); }
        @Override public void dispose() { disposals++; if (cleanupThrows) throw new IllegalStateException("owned dispose failure"); }
    }
    private static PreviewMovie movie(TestApp app) throws Exception {
        Field unsafe = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); unsafe.setAccessible(true);
        PreviewMovie movie = (PreviewMovie) ((sun.misc.Unsafe) unsafe.get(null)).allocateInstance(PreviewMovie.class);
        movie.width = 4; movie.height = 4; return movie;
    }
    private static void updateMovie(VideoGlitcher app, processing.video.Movie movie) throws Exception {
        Method method = VideoGlitcher.class.getDeclaredMethod("updateMovieFrame", processing.video.Movie.class); method.setAccessible(true); method.invoke(app, movie);
    }
    @SuppressWarnings("unchecked") private static CompletableFuture<String> finishing(VideoGlitcher app) throws Exception {
        return (CompletableFuture<String>) get(app, "pendingExportFinish");
    }
    private static void clean(List<FfmpegVideoExporter> encoders) throws Exception {
        for (FfmpegVideoExporter encoder : encoders) if (encoder != null) { encoder.abort(); check(encoder.awaitCleanup(10, TimeUnit.SECONDS), "Owned encoder must be cleaned"); }
    }
    @SuppressWarnings("unchecked") private static void drain(VideoGlitcher app) throws Exception {
        var queue = (ConcurrentLinkedQueue<Runnable>) get(app, "pendingGuiActions"); Runnable action; while ((action = queue.poll()) != null) action.run();
    }
    private static void invoke(VideoGlitcher app, String name, String... argument) throws Exception {
        Method method = VideoGlitcher.class.getDeclaredMethod(name, argument.length == 0 ? new Class<?>[]{} : new Class<?>[]{String.class});
        method.setAccessible(true); method.invoke(app, (Object[]) argument);
    }
    private static Object get(VideoGlitcher app, String name) throws Exception { Field field = VideoGlitcher.class.getDeclaredField(name); field.setAccessible(true); return field.get(app); }
    private static void set(VideoGlitcher app, String name, Object value) throws Exception { Field field = VideoGlitcher.class.getDeclaredField(name); field.setAccessible(true); field.set(app, value); }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); checks++; }
}
