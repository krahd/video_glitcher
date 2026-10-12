package tom.videoGlitcher;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Owned draw-thread frame/completion failures and encoder outcomes. Does not load a native video pipeline. */
public final class VideoGlitcherFrameFailureTest {
    private static int checks;

    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("video-frame-failure-");
        try { run(directory); }
        finally { try (var paths = Files.walk(directory)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        } }
    }

    static void run(Path directory) throws Exception {
        String[] scenarios = {"end-isPlaying", "end-duration", "end-time", "end-snapshot", "frame-available", "frame-read"};
        for (String scenario : scenarios) {
            for (String phase : new String[]{"preview", "live", "full", "saving", "saving-full"}) testFailure(directory, scenario, phase, false);
        }
        testFailure(directory, "frame-read", "live", true);
        testFinishingOutcome(directory, false);
        testFinishingOutcome(directory, true);
        testGuards();
        testFatalErrorsPropagate();
        System.out.println("Draw-time frame failure checks passed (" + checks + " checks; no native acceptance).");
    }

    private static void testFailure(Path directory, String scenario, String phase, boolean cleanupThrows) throws Exception {
        TestApp app = new TestApp(); app.width = 4; app.height = 4;
        Path output = directory.resolve("frame-failure-" + scenario + "-" + phase + "-" + cleanupThrows + ".mp4");
        Path source = directory.resolve("frame-failure-source-" + scenario + "-" + phase + "-" + cleanupThrows + ".mp4");
        Files.writeString(source, "owned source marker");
        set(app, "currentVideoName", source.getFileName().toString());
        FfmpegVideoExporter encoder = phase.equals("preview") ? null : FfmpegVideoExporter.start(output.toString(), 4, 4, 24);
        CountDownLatch gate = new CountDownLatch(1);
        CompletableFuture<String> finishing = null;
        try {
            if (encoder != null) {
                encoder.writeFrame(new int[16]); set(app, "videoExporter", encoder); setMode(app, (phase.equals("full") || phase.equals("saving-full")) ? "FULL_PROCESS" : "INTERACTIVE");
                if (phase.startsWith("saving")) finishing = holdFinish(app, encoder, output, gate);
                else set(app, "exporting", true);
            }
            ScriptedMovie failed = configure(app, scenario); failed.cleanupThrows = cleanupThrows;
            perform(app, scenario);
            checkRetired(app, failed);
            check(((String) get(app, "statusMessage")).contains("owned " + failed.failure + " failure")
                            && ((String) get(app, "statusMessage")).contains("reload"), "Explain the actual failure and reload action");
            check(Files.readString(source).equals("owned source marker"), "Frame recovery must not alter the source");
            if (phase.startsWith("saving")) {
                check(get(app, "pendingExportFinish") == finishing && get(app, "videoExporter") == encoder,
                        "Frame failure must retain existing finish and encoder ownership");
                check(!finishing.isDone() && encoder.framesWritten() == 1, "Recovery must neither finish nor add frames");
                gate.countDown(); check(finishing.get(10, TimeUnit.SECONDS) == null, "Captured frames must still encode");
                invoke(app, "completePendingExport");
                check(Files.size(output) > 0 && get(app, "statusMessage").equals("Saved export: " + output),
                        "The authoritative saved result must replace the temporary preview warning");
                verifyDecoded(output);
            } else {
                check(get(app, "videoExporter") == null && !(boolean) get(app, "exporting") && get(app, "pendingExportFinish") == null,
                        "Preview/active-capture failure must leave no active or finishing encoder ownership");
                check(get(app, "exportMode").toString().equals("NONE") && get(app, "lockedRenderSettings") == null,
                        "Failed live/full capture must unlock export controls");
                if (encoder != null) check(encoder.awaitCleanup(10, TimeUnit.SECONDS), "Failed live/full capture must clean its encoder");
                check(!Files.exists(output), "Failed live/full capture must not publish partial output");
            }
            // Repeated controls after retirement must be safe. Reload must accept a fresh movie.
            app.pausePlay(); app.rewindToStart(); app.togglePlaybackMode();
            app.videoSelected(source.toFile()); drain(app);
            ScriptedMovie retry = (ScriptedMovie) get(app, "video");
            invoke(app, "readAvailableMovieFrame");
            app.pausePlay(); app.pausePlay(); app.rewindToStart();
            check(get(app, "video") == retry && (boolean) get(app, "movieReady") && !(boolean) get(app, "paused"),
                    "Immediate reload/retry must return to usable playback");
            check(Files.readString(source).equals("owned source marker"), "Reload/retry must preserve source bytes");
        } finally {
            gate.countDown();
            if (encoder != null) { encoder.abort(); check(encoder.awaitCleanup(10, TimeUnit.SECONDS), "Owned encoder cleanup must complete"); }
            if (finishing != null) finishing.get(10, TimeUnit.SECONDS);
            if (encoder != null) checkEncoderRetired(encoder);
        }
    }

    private static void testFinishingOutcome(Path directory, boolean cancel) throws Exception {
        TestApp app = new TestApp(); Path output = directory.resolve("frame-failure-final-" + cancel + ".mp4");
        FfmpegVideoExporter encoder = FfmpegVideoExporter.start(output.toString(), 4, 4, 24);
        CountDownLatch gate = new CountDownLatch(1); CompletableFuture<String> finishing = null;
        try {
            encoder.writeFrame(new int[16]); set(app, "videoExporter", encoder); setMode(app, "INTERACTIVE");
            finishing = holdFinish(app, encoder, output, gate);
            configure(app, "end-snapshot"); invoke(app, "updatePlaybackCompletion");
            if (cancel) {
                app.cancelExport();
                check(get(app, "pendingExportFinish") == null && ((String) get(app, "statusMessage")).startsWith("Export cancelled."),
                        "X must remain available after a failed preview control while saving");
            } else Files.writeString(output, "owned competing output");
            gate.countDown(); String error = finishing.get(10, TimeUnit.SECONDS);
            check(error != null, "Cancellation/collision must not be reported as successful output");
            if (cancel) check(!Files.exists(output), "Cancelled finish must publish nothing");
            else {
                invoke(app, "completePendingExport");
                check(get(app, "statusMessage").equals(error) && Files.readString(output).equals("owned competing output"),
                        "Encoder failure must stay authoritative and preserve competing destination bytes");
            }
        } finally {
            gate.countDown(); encoder.abort(); check(encoder.awaitCleanup(10, TimeUnit.SECONDS), "Finishing fixture cleanup");
            if (finishing != null) finishing.get(10, TimeUnit.SECONDS);
            if (encoder != null) checkEncoderRetired(encoder);
        }
    }

    private static CompletableFuture<String> holdFinish(TestApp app, FfmpegVideoExporter encoder, Path output, CountDownLatch gate) throws Exception {
        CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
            try { if (!gate.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("owned gate timed out"); encoder.finish(); return null; }
            catch (Exception exception) { return "Export failed: " + exception.getMessage(); }
        });
        set(app, "pendingExportFinish", future); set(app, "finishingExportFilename", output.toString());
        set(app, "finishingExportMode", get(app, "exportMode")); return future;
    }

    private static ScriptedMovie configure(TestApp app, String scenario) throws Exception {
        ScriptedMovie movie = movie(); movie.failure = scenario.substring(scenario.indexOf('-') + 1); movie.atEnd = true;
        set(app, "video", movie); set(app, "movieReady", true); set(app, "paused", false);
        set(app, "pausedFrame", new processing.core.PImage(4, 4)); set(app, "frozenFrame", new processing.core.PImage(4, 4));
        set(app, "previousFrame", new processing.core.PImage(4, 4)); set(app, "freezeManual", true); set(app, "freezeFramesLeft", 2);
        return movie;
    }
    private static void perform(TestApp app, String scenario) throws Exception {
        invoke(app, scenario.startsWith("end-") ? "updatePlaybackCompletion" : "readAvailableMovieFrame");
    }
    private static void checkRetired(TestApp app, ScriptedMovie movie) throws Exception {
        check(get(app, "video") == null && !(boolean) get(app, "movieReady") && !(boolean) get(app, "paused"), "Failed preview must be retired");
        check(get(app, "pausedFrame") == null && get(app, "frozenFrame") == null && get(app, "previousFrame") == null
                && !(boolean) get(app, "freezeManual") && (int) get(app, "freezeFramesLeft") == 0, "Retirement must clear stale frame state");
        check(movie.failures == 1 && movie.stops == 1 && movie.disposals == 1, "Do not repeat a failing frame/completion call; attempt both cleanup operations");
    }
    private static void testGuards() throws Exception {
        TestApp empty = new TestApp(); invoke(empty, "readAvailableMovieFrame"); invoke(empty, "updatePlaybackCompletion");
        TestApp app = new TestApp(); ScriptedMovie movie = configure(app, "frame-read");
        set(app, "paused", true); invoke(app, "readAvailableMovieFrame"); invoke(app, "updatePlaybackCompletion");
        check(movie.calls == 0 && get(app, "video") == movie, "Paused preview must not read/query the movie");
        set(app, "paused", false); set(app, "movieReady", false); invoke(app, "updatePlaybackCompletion");
        check(movie.calls == 0, "Loading preview must not query playback completion");
        set(app, "videoLoadExportOutcome", "Saved export: owned-before-load.mp4");
        invoke(app, "readAvailableMovieFrame"); checkRetired(app, movie);
        check(((String) get(app, "statusMessage")).startsWith("Saved export: owned-before-load.mp4; ")
                && get(app, "videoLoadExportOutcome").equals(""), "Failed decoding must retain the prior save and terminate loading context");
        check(((String) get(app, "statusMessage")).contains("reload"), "A failed first-frame read must be reloadable");
    }
    private static void checkEncoderRetired(FfmpegVideoExporter encoder) throws Exception {
        Field process = FfmpegVideoExporter.class.getDeclaredField("process"); process.setAccessible(true);
        Field temporary = FfmpegVideoExporter.class.getDeclaredField("temporaryPath"); temporary.setAccessible(true);
        check(!((Process) process.get(encoder)).isAlive(), "Frame recovery must not orphan an encoder process");
        check(!Files.exists((Path) temporary.get(encoder)), "Frame recovery must remove its private staging file");
    }
    private static void testFatalErrorsPropagate() throws Exception {
        for (String scenario : new String[]{"end-isPlaying", "frame-available", "frame-read"}) {
            TestApp app = new TestApp(); ScriptedMovie movie = configure(app, scenario); movie.fatal = true;
            try { perform(app, scenario); throw new AssertionError("Fatal failure must escape ordinary recovery"); }
            catch (java.lang.reflect.InvocationTargetException exception) {
                check(exception.getCause() instanceof AssertionError, "Do not classify JVM/fatal Errors as recoverable playback exceptions");
            }
            check(get(app, "video") == movie && movie.stops == 0 && movie.disposals == 0,
                    "Fatal errors must not enter the ordinary retirement/retry path");
        }
    }
    private static void verifyDecoded(Path output) throws Exception {
        Path pixels = output.resolveSibling(output.getFileName() + ".rgb");
        Process process = new ProcessBuilder("ffmpeg", "-v", "error", "-i", output.toString(), "-frames:v", "1", "-f", "rawvideo", "-pix_fmt", "rgb24", pixels.toString()).inheritIO().start();
        try {
            check(process.waitFor(10, TimeUnit.SECONDS) && process.exitValue() == 0 && Files.size(pixels) == 48,
                    "Retained finish must contain a decodable owned 4x4 frame");
        } finally { if (process.isAlive()) process.destroyForcibly(); }
    }
    private static final class TestApp extends VideoGlitcher {
        @Override processing.video.Movie createMovie(String source) { try { return movie(); } catch (Exception exception) { throw new RuntimeException(exception); } }
    }
    public static final class ScriptedMovie extends processing.video.Movie {
        String failure; boolean atEnd, cleanupThrows, fatal; int calls, failures, stops, disposals;
        private ScriptedMovie() { super(null, "unused"); }
        void call(String name) {
            calls++;
            if (name.equals(failure)) {
                failures++;
                if (fatal) throw new AssertionError("owned fatal " + name + " failure");
                throw new IllegalStateException("owned " + name + " failure");
            }
        }
        @Override public void pause() { call("pause"); }
        @Override public void play() { call("play"); }
        @Override public void loop() { call("loop"); }
        @Override public void noLoop() { call("noLoop"); }
        @Override public void jump(float value) { call("jump"); atEnd = false; }
        @Override public float duration() { call("duration"); return 3; }
        @Override public float time() { call("time"); return atEnd ? 3 : 0; }
        @Override public processing.core.PImage get() { call("snapshot"); return new processing.core.PImage(4, 4); }
        @Override public void read() { call("read"); }
        @Override public boolean available() { call("available"); return true; }
        @Override public boolean isPlaying() { call("isPlaying"); return false; }
        @Override public void stop() { stops++; if (cleanupThrows) throw new IllegalStateException("owned stop failure"); }
        @Override public void dispose() { disposals++; if (cleanupThrows) throw new IllegalStateException("owned disposal failure"); }
    }
    private static ScriptedMovie movie() throws Exception {
        Field unsafe = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); unsafe.setAccessible(true);
        ScriptedMovie movie = (ScriptedMovie) ((sun.misc.Unsafe) unsafe.get(null)).allocateInstance(ScriptedMovie.class);
        movie.width = 4; movie.height = 4; return movie;
    }
    private static void setMode(VideoGlitcher app, String mode) throws Exception {
        for (Object value : Class.forName("tom.videoGlitcher.VideoGlitcher$ExportMode").getEnumConstants()) if (value.toString().equals(mode)) set(app, "exportMode", value);
    }
    @SuppressWarnings("unchecked") private static void drain(VideoGlitcher app) throws Exception {
        var queue = (ConcurrentLinkedQueue<Runnable>) get(app, "pendingGuiActions"); Runnable action; while ((action = queue.poll()) != null) action.run();
    }
    private static void invoke(VideoGlitcher app, String name) throws Exception { Method method = VideoGlitcher.class.getDeclaredMethod(name); method.setAccessible(true); method.invoke(app); }
    private static Object get(VideoGlitcher app, String name) throws Exception { Field field = VideoGlitcher.class.getDeclaredField(name); field.setAccessible(true); return field.get(app); }
    private static void set(VideoGlitcher app, String name, Object value) throws Exception { Field field = VideoGlitcher.class.getDeclaredField(name); field.setAccessible(true); field.set(app, value); }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); checks++; }
}
