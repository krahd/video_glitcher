package tom.videoGlitcher;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

/** Owned playback failure injection with real encoders; no native video pipeline or user media. */
public final class VideoGlitcherExportStartTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        if (args.length == 2 && args[0].equals("--smoke-start-child")) {
            runSmokeStartChild(Path.of(args[1]));
            throw new AssertionError("Smoke startup failure returned instead of exiting");
        }
        throw new IllegalArgumentException("This helper only runs the owned smoke-start child");
    }
    static void run(Path directory) throws Exception {
        for (String scenario : new String[]{"live-loop", "live-play", "live-duration", "live-time", "live-jump", "full-noLoop", "full-jump", "full-play"}) {
            testStartupRollbackAndRetry(directory, scenario, false);
        }
        testStartupRollbackAndRetry(directory, "full-play", true);
        testExistingDestinationPreservesPreview(directory, false);
        testExistingDestinationPreservesPreview(directory, true);
        testSmokeStartupFailure(directory);
        System.out.println("Export startup rollback checks passed (" + checks + " checks; injected playback failures with real encoders).");
    }

    private static void testStartupRollbackAndRetry(Path directory, String scenario, boolean cleanupThrows) throws Exception {
        TestApp app = new TestApp(); app.width = 4; app.height = 4;
        boolean full = scenario.startsWith("full-");
        String failure = scenario.substring(scenario.indexOf('-') + 1);
        Path output = directory.resolve("startup-" + scenario + "-" + cleanupThrows + ".mp4");
        try {
            for (int attempt = 0; attempt < 3; attempt++) {
                ScriptedMovie movie = movie(app);
                movie.failure = failure; movie.atEnd = failure.equals("jump") && !full;
                movie.cleanupThrows = cleanupThrows;
                set(app, "video", movie); set(app, "movieReady", true); set(app, "paused", true);
                set(app, "pausedFrame", new processing.core.PImage(4, 4));
                set(app, "frozenFrame", new processing.core.PImage(4, 4));
                set(app, "previousFrame", new processing.core.PImage(4, 4));
                set(app, "freezeManual", true); set(app, "freezeFramesLeft", 3);
                set(app, "loopPlayback", scenario.equals("live-loop")); set(app, "exportFilename", output.toString());
                invokeStart(app, full);
                check(!(boolean) get(app, "exporting") && get(app, "videoExporter") == null && get(app, "pendingExportFinish") == null,
                        "Failed startup must release encoder ownership and leave no recording/finalisation state");
                check(get(app, "exportMode").toString().equals("NONE") && get(app, "lockedRenderSettings") == null,
                        "Failed startup must unlock controls and clear its export mode");
                check(get(app, "video") == null && !(boolean) get(app, "movieReady") && !(boolean) get(app, "paused"),
                        "Broken playback must become a reloadable state, not a ready stale movie");
                check(get(app, "pausedFrame") == null && get(app, "frozenFrame") == null && get(app, "previousFrame") == null
                                && !(boolean) get(app, "freezeManual") && (int) get(app, "freezeFramesLeft") == 0,
                        "Failed startup must clear frame caches belonging to the retired movie");
                check(movie.stops == 1 && movie.disposals == 1, "Rollback must attempt pipeline release even if native cleanup throws");
                check(((String) get(app, "statusMessage")).contains("reload")
                                && ((String) get(app, "statusMessage")).contains("owned " + failure + " failure"),
                        "Failure status must explain the cause and reload/retry action");
                app.cancelExport(); app.stopExport();
                check(!Files.exists(output), "Cancel/Stop after failed startup must never publish an output");
            }
            check(app.failedEncoders.size() == 3, "Every playback failure must occur after a real encoder was acquired");
            // Retry without waiting for prior asynchronous abort cleanup. All temporary paths are private.
            set(app, "selectingVideo", true); app.videoSelected(directory.resolve("reload-owned.mp4").toFile()); drain(app);
            ScriptedMovie retry = (ScriptedMovie) get(app, "video");
            Method update = VideoGlitcher.class.getDeclaredMethod("updateMovieFrame", processing.video.Movie.class); update.setAccessible(true); update.invoke(app, retry);
            set(app, "exportFilename", output.toString());
            invokeStart(app, full);
            check((boolean) get(app, "exporting"), "Reload after failed startup must allow a new export");
            FfmpegVideoExporter encoder = (FfmpegVideoExporter) get(app, "videoExporter");
            encoder.writeFrame(new int[16]); app.stopExport(); finish(app);
            check(Files.size(output) > 0 && ((String) get(app, "statusMessage")).equals("Saved export: " + output),
                    "Retry must really save the new output and report its destination");
            for (FfmpegVideoExporter failed : app.failedEncoders) {
                check(failed.awaitCleanup(10, TimeUnit.SECONDS), "Each failed-start encoder must finish rollback without manual cancellation");
                Field process = FfmpegVideoExporter.class.getDeclaredField("process"); process.setAccessible(true);
                check(!((Process) process.get(failed)).isAlive(), "Failed-start encoder process must not remain alive");
            }
            try (var paths = Files.list(directory)) {
                check(paths.noneMatch(p -> p.getFileName().toString().startsWith(".video-glitcher-")),
                        "Repeated startup failure/retry must leave no private staging files");
            }
        } finally { cleanEncoders(app); }
    }

    private static void testExistingDestinationPreservesPreview(Path directory, boolean full) throws Exception {
        TestApp app = new TestApp(); app.width = 4; app.height = 4;
        ScriptedMovie movie = movie(app); movie.failure = "play";
        Path existing = directory.resolve("valuable-existing-" + full + ".mp4");
        Files.writeString(existing, "owned previous output: preserve bytes");
        set(app, "video", movie); set(app, "movieReady", true); set(app, "paused", true);
        Object pausedFrame = new processing.core.PImage(4, 4); set(app, "pausedFrame", pausedFrame);
        set(app, "exportFilename", existing.toString());
        try {
            invokeStart(app, full);
            check(Files.readString(existing).equals("owned previous output: preserve bytes"), "Existing output bytes must never be replaced");
            check(get(app, "video") == movie && (boolean) get(app, "movieReady") && (boolean) get(app, "paused")
                            && get(app, "pausedFrame") == pausedFrame && movie.stops == 0 && movie.disposals == 0,
                    "Destination validation failure must preserve the usable preview and pause state");
            check(app.failedEncoders.isEmpty() && get(app, "videoExporter") == null && movie.transportCalls == 0,
                    "Existing destination must be rejected before encoder/playback acquisition");
        } finally { cleanEncoders(app); }
    }

    private static void testSmokeStartupFailure(Path directory) throws Exception {
        Path owned = Files.createDirectory(directory.resolve("smoke-start-failure"));
        String java = Path.of(System.getProperty("java.home"), "bin", System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java").toString();
        Process child = new ProcessBuilder(java, "-Djava.awt.headless=true", "-cp", System.getProperty("java.class.path"),
                VideoGlitcherExportStartTest.class.getName(), "--smoke-start-child", owned.toString()).inheritIO().start();
        try {
            check(child.waitFor(15, TimeUnit.SECONDS) && child.exitValue() == 1,
                    "Smoke playback-start failure must exit 1 rather than hang or report success");
            check(!Files.exists(owned.resolve("owned.mp4")), "Failed smoke startup must not publish a partial output");
            try (var paths = Files.list(owned)) {
                check(paths.noneMatch(path -> path.getFileName().toString().endsWith(".part.mp4")),
                        "Smoke process shutdown must clean the failed-start staging file");
            }
            long encoderPid = Long.parseLong(Files.readString(owned.resolve("encoder.pid")));
            check(ProcessHandle.of(encoderPid).map(process -> !process.isAlive()).orElse(true),
                    "Smoke failure must not orphan its encoder after application exit");
        } finally {
            if (child.isAlive()) {
                child.descendants().forEach(process -> process.destroyForcibly());
                child.destroyForcibly(); child.waitFor(5, TimeUnit.SECONDS);
            }
        }
    }

    private static void runSmokeStartChild(Path directory) throws Exception {
        TestApp app = new TestApp(); app.width = 4; app.height = 4;
        Class<?> options = Class.forName("tom.videoGlitcher.VideoGlitcher$LaunchOptions");
        Method parse = options.getDeclaredMethod("parse", String[].class); parse.setAccessible(true);
        set(app, "launchOptions", parse.invoke(null, (Object) new String[]{"--smoke-test", "--auto-export"}));
        ScriptedMovie movie = movie(app); movie.failure = "loop"; movie.pidFile = directory.resolve("encoder.pid");
        set(app, "video", movie); set(app, "movieReady", true); set(app, "loopPlayback", true);
        set(app, "exportFilename", directory.resolve("owned.mp4").toString());
        Field surface = processing.core.PApplet.class.getDeclaredField("surface"); surface.setAccessible(true);
        surface.set(app, java.lang.reflect.Proxy.newProxyInstance(processing.core.PSurface.class.getClassLoader(),
                new Class[]{processing.core.PSurface.class}, (proxy, method, values) -> method.getReturnType() == boolean.class ? false : null));
        invokeStart(app, false);
    }

    private static final class TestApp extends VideoGlitcher {
        final List<FfmpegVideoExporter> failedEncoders = new ArrayList<>();
        @Override processing.video.Movie createMovie(String source) {
            try { return movie(this); } catch (Exception exception) { throw new RuntimeException(exception); }
        }
    }
    public static final class ScriptedMovie extends processing.video.Movie {
        TestApp owner;
        String failure;
        Path pidFile;
        boolean atEnd, cleanupThrows;
        int stops, disposals, transportCalls;
        private ScriptedMovie() { super(null, "unused"); }
        void call(String operation) {
            transportCalls++;
            if (operation.equals(failure)) {
                try {
                    FfmpegVideoExporter encoder = (FfmpegVideoExporter) VideoGlitcherExportStartTest.get(owner, "videoExporter");
                    owner.failedEncoders.add(encoder);
                    if (pidFile != null) {
                        Field process = FfmpegVideoExporter.class.getDeclaredField("process"); process.setAccessible(true);
                        Files.writeString(pidFile, Long.toString(((Process) process.get(encoder)).pid()));
                    }
                } catch (Exception exception) { throw new RuntimeException(exception); }
                throw new IllegalStateException("owned " + operation + " failure");
            }
        }
        @Override public void play() { call("play"); }
        @Override public void loop() { call("loop"); }
        @Override public void noLoop() { call("noLoop"); }
        @Override public void jump(float value) { call("jump"); atEnd = false; }
        @Override public float duration() { call("duration"); return 3; }
        @Override public float time() { call("time"); return atEnd ? 3 : 0; }
        @Override public void read() { }
        @Override public void stop() { stops++; if (cleanupThrows) throw new IllegalStateException("owned cleanup stop failure"); }
        @Override public void dispose() { disposals++; if (cleanupThrows) throw new IllegalStateException("owned cleanup disposal failure"); }
    }
    private static ScriptedMovie movie(TestApp app) throws Exception {
        Field unsafe = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); unsafe.setAccessible(true);
        ScriptedMovie movie = (ScriptedMovie) ((sun.misc.Unsafe) unsafe.get(null)).allocateInstance(ScriptedMovie.class);
        movie.owner = app; movie.width = 4; movie.height = 4;
        return movie;
    }
    private static void invokeStart(VideoGlitcher app, boolean full) throws Exception {
        try { invoke(app, full ? "startFullProcessExport" : "startInteractiveExport"); }
        catch (InvocationTargetException exception) {
            throw new AssertionError("Playback startup failure must roll back without escaping to the draw/UI caller", exception.getCause());
        }
    }
    private static void finish(VideoGlitcher app) throws Exception {
        @SuppressWarnings("unchecked") CompletableFuture<String> future = (CompletableFuture<String>) get(app, "pendingExportFinish");
        check(future != null && future.get(10, TimeUnit.SECONDS) == null, "Owned retry encoding must complete");
        invoke(app, "completePendingExport");
    }
    private static void cleanEncoders(TestApp app) throws Exception {
        FfmpegVideoExporter current = (FfmpegVideoExporter) get(app, "videoExporter");
        if (current != null) { current.abort(); current.awaitCleanup(10, TimeUnit.SECONDS); }
        for (FfmpegVideoExporter encoder : app.failedEncoders) {
            if (encoder != null) { encoder.abort(); encoder.awaitCleanup(10, TimeUnit.SECONDS); }
        }
    }
    @SuppressWarnings("unchecked") private static void drain(VideoGlitcher app) throws Exception {
        var queue = (ConcurrentLinkedQueue<Runnable>) get(app, "pendingGuiActions");
        Runnable action; while ((action = queue.poll()) != null) action.run();
    }
    private static void invoke(VideoGlitcher app, String name) throws Exception {
        Method method = VideoGlitcher.class.getDeclaredMethod(name); method.setAccessible(true); method.invoke(app);
    }
    private static Object get(VideoGlitcher app, String name) throws Exception {
        Field field = VideoGlitcher.class.getDeclaredField(name); field.setAccessible(true); return field.get(app);
    }
    private static void set(VideoGlitcher app, String name, Object value) throws Exception {
        Field field = VideoGlitcher.class.getDeclaredField(name); field.setAccessible(true); field.set(app, value);
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); checks++; }
}
