package tom.videoGlitcher;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

/** Workflow state regressions without a Processing surface. Desktop acceptance remains separate. */
public final class VideoGlitcherWorkflowTest {
    public static void main(String[] args) throws Exception {
        if (args.length == 2 && args[0].equals("--timeout-child")) {
            runSmokeTimeoutChild(Path.of(args[1]));
            throw new AssertionError("Smoke timeout did not exit");
        }
        VideoGlitcher app = new VideoGlitcher();
        set(app, "selectingVideo", true);
        app.videoSelected(null);
        check((boolean) get(app, "selectingVideo"), "Native callback must not mutate draw-thread state");
        drainPickerActions(app);
        check(!(boolean) get(app, "selectingVideo"), "Queued selection must clear on draw");
        set(app, "selectingProcessOutput", true);
        app.processOutputSelected(null);
        check((boolean) get(app, "selectingProcessOutput"), "Process callback must queue");
        drainPickerActions(app);
        check(!(boolean) get(app, "selectingProcessOutput"), "Process cancel must clear");
        set(app, "selectingInteractiveOutput", true);
        app.interactiveOutputSelected(null);
        check((boolean) get(app, "selectingInteractiveOutput"), "Live callback must queue");
        drainPickerActions(app);
        check(!(boolean) get(app, "selectingInteractiveOutput"), "Live cancel must clear");

        testManualFreezeUsesPreGuiFrame();
        Path directory = Files.createTempDirectory("video-glitcher-workflow-");
        try {
            testSmokeTimeout(directory);
            testLoadAfterPauseOrExport(directory, false);
            testLoadAfterPauseOrExport(directory, true);
            Path actual = directory.resolve("actual.mp4");
            FfmpegVideoExporter exporter = FfmpegVideoExporter.start(actual.toString(), 4, 4, 24);
            exporter.writeFrame(new int[16]);
            set(app, "videoExporter", exporter);
            set(app, "exporting", true);
            set(app, "exportFilename", actual.toString());
            long started = System.nanoTime();
            app.stopExport();
            check(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) < 1000, "Stop must not synchronously finish");
            set(app, "exportFilename", "later-selection.mp4");
            waitForFinish(app);
            check(Files.exists(actual), "Recorded destination saved");
            check(((String) get(app, "statusMessage")).equals("Saved export: " + actual), "Success must name the recorded destination");

            exporter = FfmpegVideoExporter.start(directory.resolve("cancel.mp4").toString(), 4, 4, 24);
            exporter.writeFrame(new int[16]);
            set(app, "videoExporter", exporter);
            set(app, "exporting", true);
            set(app, "showGuide", true);
            app.key = 'x';
            app.keyPressed();
            check(!(boolean) get(app, "exporting"), "Guide must not swallow cancel");
            check(exporter.awaitCleanup(10, TimeUnit.SECONDS), "Guide cancellation cleaned up");
            check(!Files.exists(directory.resolve("cancel.mp4")), "Guide cancellation must not save output");
        } finally {
            try (var paths = Files.walk(directory)) {
                for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }
        System.out.println("All VideoGlitcher workflow state tests passed.");
    }
    private static void testManualFreezeUsesPreGuiFrame() throws Exception {
        VideoGlitcher app = new VideoGlitcher();
        processing.core.PImage preGui = new processing.core.PImage(4, 4);
        java.util.Arrays.fill(preGui.pixels, 0xff112233);
        preGui.pixels[5] = 0xffff0000; preGui.pixels[6] = 0xff00ff00;
        preGui.pixels[9] = 0xff0000ff; preGui.pixels[10] = 0xffffffff;
        set(app, "previousFrame", preGui); set(app, "movieReady", true);
        set(app, "drawX", 1f); set(app, "drawY", 1f); set(app, "drawW", 2f); set(app, "drawH", 2f);
        app.key = 'f'; app.keyPressed();
        processing.core.PImage frozen = (processing.core.PImage) get(app, "frozenFrame");
        check(frozen.width == 2 && frozen.height == 2 && java.util.Arrays.equals(frozen.pixels,
                new int[]{0xffff0000, 0xff00ff00, 0xff0000ff, 0xffffffff}), "Manual freeze must crop the pre-GUI render, never the decorated screen");
        app.keyPressed(); check(!(boolean) get(app, "freezeManual"), "Manual freeze toggle must release normally");
    }

    private static void testLoadAfterPauseOrExport(Path directory, boolean afterExport) throws Exception {
        TestVideoGlitcher app = new TestVideoGlitcher();
        ReadyMovie old = readyMovie();
        set(app, "video", old);
        set(app, "movieReady", true);
        if (afterExport) {
            Path output = directory.resolve("before-next-load.mp4");
            FfmpegVideoExporter exporter = FfmpegVideoExporter.start(output.toString(), 4, 4, 24);
            exporter.writeFrame(new int[16]);
            set(app, "videoExporter", exporter); set(app, "exporting", true); set(app, "exportFilename", output.toString());
            for (Object mode : Class.forName("tom.videoGlitcher.VideoGlitcher$ExportMode").getEnumConstants()) {
                if (mode.toString().equals("FULL_PROCESS")) set(app, "exportMode", mode);
            }
            invoke(app, "updatePlaybackCompletion");
            waitForFinish(app);
            check(Files.exists(output), "Full export must really complete before repeated-load test");
        } else {
            app.pausePlay();
        }
        check((boolean) get(app, "paused"), "Actual prior transition must pause the old clip");
        Object oldPausedFrame = get(app, "pausedFrame");
        set(app, "selectingVideo", true);
        app.videoSelected(null);
        drainPickerActions(app);
        check((boolean) get(app, "paused") && get(app, "pausedFrame") == oldPausedFrame,
                "Cancelling the picker must preserve the old pause state and frame");
        check(get(app, "video") == old && !old.stopped, "Picker cancellation must preserve the old pipeline");
        set(app, "freezeManual", true); set(app, "freezeFramesLeft", 5);
        set(app, "selectingVideo", true);
        app.videoSelected(directory.resolve(afterExport ? "after-export.mp4" : "after-pause.mp4").toFile());
        check((boolean) get(app, "paused"), "Queued selection must not reset playback before draw handles it");
        drainPickerActions(app); // Invokes the actual accepted-selection -> loadVideoFile -> startMovie path.
        ReadyMovie next = (ReadyMovie) get(app, "video");
        check(old.stopped && next != old && next.played, "Accepted next clip must replace and start its pipeline");
        check(!(boolean) get(app, "paused"), "New clip must not inherit manual/end-of-export pause");
        check(get(app, "pausedFrame") == null && !(boolean) get(app, "freezeManual") && (int) get(app, "freezeFramesLeft") == 0,
                "New clip must clear paused/frozen frame state");
        check(!(boolean) get(app, "movieReady") && (long) get(app, "videoLoadStartedNanos") != 0,
                "New clip gets its own first-frame load window");
        check(next.available() && !(boolean) get(app, "paused"), "draw must be allowed to read the ready native frame");
        Method update = VideoGlitcher.class.getDeclaredMethod("updateMovieFrame", processing.video.Movie.class); update.setAccessible(true); update.invoke(app, next);
        set(app, "videoLoadStartedNanos", System.nanoTime() - 16_000_000_000L);
        invoke(app, "checkVideoLoadTimeout");
        check(next.reads == 1 && (boolean) get(app, "movieReady") && !next.stopped && get(app, "video") == next,
                "A successfully decoded replacement clip must not be discarded by the timeout");
    }

    private static final class TestVideoGlitcher extends VideoGlitcher {
        @Override processing.video.Movie createMovie(String source) {
            try { return readyMovie(); } catch (Exception exception) { throw new RuntimeException(exception); }
        }
    }
    private static final class ReadyMovie extends processing.video.Movie {
        boolean stopped; boolean played; int reads;
        private ReadyMovie() { super(null, "unused"); }
        @Override public boolean available() { return true; }
        @Override public void read() { reads++; }
        @Override public void stop() { stopped = true; }
        @Override public void pause() { }
        @Override public void play() { played = true; }
        @Override public void loop() { played = true; }
        @Override public boolean isPlaying() { return false; }
        @Override public float time() { return 3; }
        @Override public float duration() { return 3; }
        @Override public processing.core.PImage get() { return new processing.core.PImage(4, 4); }
    }
    private static ReadyMovie readyMovie() throws Exception {
        // Bypass only native Movie construction; every application transition above is the real method.
        Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); field.setAccessible(true);
        ReadyMovie movie = (ReadyMovie) ((sun.misc.Unsafe) field.get(null)).allocateInstance(ReadyMovie.class);
        movie.width = 4; movie.height = 4;
        return movie;
    }
    private static void invoke(VideoGlitcher app, String method) throws Exception {
        Method m = VideoGlitcher.class.getDeclaredMethod(method); m.setAccessible(true); m.invoke(app);
    }

    private static void testSmokeTimeout(Path directory) throws Exception {
        String java = Path.of(System.getProperty("java.home"), "bin", System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java").toString();
        Process child = new ProcessBuilder(java, "-Djava.awt.headless=true", "-cp", System.getProperty("java.class.path"),
                VideoGlitcherWorkflowTest.class.getName(), "--timeout-child", directory.toString()).inheritIO().start();
        check(child.waitFor(15, TimeUnit.SECONDS) && child.exitValue() == 1, "Insufficient smoke export frames must fail, not claim success");
        check(!Files.exists(directory.resolve("timeout.mp4")), "Smoke timeout must not publish partial output");
        try (var paths = Files.list(directory)) { check(paths.noneMatch(p -> p.getFileName().toString().endsWith(".part.mp4")), "Smoke shutdown must clean staging files"); }
    }
    private static void runSmokeTimeoutChild(Path directory) throws Exception {
        VideoGlitcher app = new VideoGlitcher();
        Class<?> options = Class.forName("tom.videoGlitcher.VideoGlitcher$LaunchOptions");
        Method parse = options.getDeclaredMethod("parse", String[].class); parse.setAccessible(true);
        set(app, "launchOptions", parse.invoke(null, (Object) new String[]{"--smoke-test", "--auto-export", "--smoke-frames=1", "--export-frames=1000"}));
        FfmpegVideoExporter exporter = FfmpegVideoExporter.start(directory.resolve("timeout.mp4").toString(), 4, 4, 24);
        exporter.writeFrame(new int[16]);
        set(app, "videoExporter", exporter); set(app, "exporting", true); set(app, "smokeExportStarted", true);
        app.frameCount = 2;
        Field surface = processing.core.PApplet.class.getDeclaredField("surface"); surface.setAccessible(true);
        surface.set(app, java.lang.reflect.Proxy.newProxyInstance(processing.core.PSurface.class.getClassLoader(),
                new Class[]{processing.core.PSurface.class}, (proxy, method, values) -> method.getReturnType() == boolean.class ? false : null));
        Method cycle = VideoGlitcher.class.getDeclaredMethod("runSmokeCycle"); cycle.setAccessible(true); cycle.invoke(app);
    }

    @SuppressWarnings("unchecked") private static void drainPickerActions(VideoGlitcher app) throws Exception {
        var queue = (ConcurrentLinkedQueue<Runnable>) get(app, "pendingGuiActions");
        Runnable action;
        while ((action = queue.poll()) != null) action.run();
    }
    private static void waitForFinish(VideoGlitcher app) throws Exception {
        Method complete = VideoGlitcher.class.getDeclaredMethod("completePendingExport"); complete.setAccessible(true);
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (get(app, "pendingExportFinish") != null && System.nanoTime() < until) { complete.invoke(app); Thread.sleep(10); }
        check(get(app, "pendingExportFinish") == null, "Async finish must complete");
    }
    private static Object get(VideoGlitcher app, String name) throws Exception { Field f = VideoGlitcher.class.getDeclaredField(name); f.setAccessible(true); return f.get(app); }
    private static void set(VideoGlitcher app, String name, Object value) throws Exception { Field f = VideoGlitcher.class.getDeclaredField(name); f.setAccessible(true); f.set(app, value); }
    private static void check(boolean test, String message) { if (!test) throw new AssertionError(message); }
}
