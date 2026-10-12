package tom.videoGlitcher;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

/** Synchronous picker-opening failures only; no native dialog or video pipeline is launched. */
public final class VideoGlitcherPickerFailureTest {
    private static int checks;
    private static final String[] PRESERVED = {"video", "movieReady", "paused", "pausedFrame", "frozenFrame",
            "previousFrame", "freezeManual", "freezeFramesLeft", "currentVideoFile", "currentVideoPath",
            "currentVideoName", "exportFilename", "videoExporter", "exporting", "exportMode",
            "pendingExportFinish", "videoLoadStartedNanos", "videoLoadExportOutcome", "lockedRenderSettings",
            "exportReachedPlaybackEnd", "finishingExportFilename", "finishingExportMode", "pendingVideoFile"};

    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("video-picker-failure-");
        try { run(directory); }
        finally { try (var paths = Files.walk(directory)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        } }
    }

    static void run(Path directory) throws Exception {
        for (String route : new String[]{"load", "live", "full"}) {
            for (boolean paused : new boolean[]{false, true}) testFailureRetry(directory, route, paused);
        }
        testEmptyAndLoading();
        testLoadDuringRecording(directory, false);
        testLoadDuringRecording(directory, true);
        testInterlocks();
        System.out.println("Picker opening failure checks passed (" + checks + " checks; no native chooser acceptance).");
    }

    private static void testFailureRetry(Path directory, String route, boolean paused) throws Exception {
        PickerApp app = configured(paused);
        Path output = directory.resolve("picker-" + route + "-" + paused + ".mp4");
        Path source = directory.resolve("picker-source-" + route + "-" + paused + ".mp4");
        Files.writeString(source, "owned source bytes");
        set(app, "currentVideoFile", source.toFile()); set(app, "currentVideoPath", source.toString());
        set(app, "exportFilename", output.toString());
        Map<String, Object> before = snapshot(app);
        for (int attempt = 1; attempt <= 2; attempt++) {
            open(app, route);
            check(app.opens == attempt, "A failed picker must allow another opening attempt");
            check(!(boolean) get(app, flag(route)), "Opening failure must clear its selection lock");
            unchanged(app, before);
            String status = (String) get(app, "statusMessage");
            check(status.contains("owned picker failure") && status.contains("Press " + shortcut(route)),
                    "Opening failure must explain the error and a usable retry shortcut");
            check(!Files.exists(output) && Files.readString(source).equals("owned source bytes"),
                    "Failed opening must neither create output nor modify source bytes");
        }
        app.fail = false;
        open(app, route);
        check(app.opens == 3 && (boolean) get(app, flag(route)), "Retry must really reopen the requested chooser");
        for (String blocked : new String[]{"load", "live", "full"}) open(app, blocked);
        check(app.opens == 3, "While the chooser is open, repeated/other opening requests must remain blocked");
        selected(app, route, null);
        check((boolean) get(app, flag(route)), "Picker callbacks must still queue for the draw thread");
        drain(app);
        check(!(boolean) get(app, flag(route)), "Cancellation must clear the successfully reopened chooser");
        unchanged(app, before);
        open(app, route);
        selected(app, route, route.equals("load") ? source.toFile() : output.toFile()); drain(app);
        if (route.equals("load")) {
            OwnedMovie old = (OwnedMovie) before.get("video");
            OwnedMovie replacement = (OwnedMovie) get(app, "video");
            check(replacement != old && replacement.starts == 1 && old.stops == 1 && old.disposals == 1,
                    "Accepted retry must replace and start the video only after selection");
            Method update = VideoGlitcher.class.getDeclaredMethod("updateMovieFrame", processing.video.Movie.class);
            update.setAccessible(true); update.invoke(app, replacement);
            check((boolean) get(app, "movieReady") && !(boolean) get(app, "paused"), "Accepted retry must become a usable preview");
        } else {
            FfmpegVideoExporter encoder = (FfmpegVideoExporter) get(app, "videoExporter");
            check(encoder != null && (boolean) get(app, "exporting"), "Accepted retry must start the requested export");
            try {
                encoder.writeFrame(new int[16]); app.stopExport(); finish(app);
                check(get(app, "statusMessage").equals("Saved export: " + output), "Retry success must name the exact accepted destination");
                decoded(output, 1);
            } finally { encoder.abort(); check(encoder.awaitCleanup(10, TimeUnit.SECONDS), "Retry encoder cleanup"); }
        }
        check(Files.readString(source).equals("owned source bytes"), "Retry must preserve source bytes");
    }

    private static void testEmptyAndLoading() throws Exception {
        PickerApp empty = new PickerApp(); open(empty, "load");
        check(!(boolean) get(empty, "selectingVideo") && get(empty, "video") == null, "An empty app must recover its Load control");
        PickerApp loading = configured(false); set(loading, "movieReady", false);
        set(loading, "videoLoadStartedNanos", System.nanoTime()); set(loading, "videoLoadExportOutcome", "Saved export: owned.mp4");
        Map<String, Object> before = snapshot(loading); open(loading, "load"); unchanged(loading, before);
        check(((String) get(loading, "statusMessage")).startsWith("Saved export: owned.mp4; "),
                "A failed load picker must preserve the preceding saved-result context during loading");
    }

    private static void testLoadDuringRecording(Path directory, boolean cancel) throws Exception {
        PickerApp app = configured(false); app.fail = false;
        Path output = directory.resolve("picker-recording-" + cancel + ".mp4"); set(app, "exportFilename", output.toString());
        app.startExport(); app.interactiveOutputSelected(output.toFile()); drain(app);
        FfmpegVideoExporter encoder = (FfmpegVideoExporter) get(app, "videoExporter");
        try {
            encoder.writeFrame(new int[16]); app.fail = true;
            Map<String, Object> before = snapshot(app); open(app, "load"); unchanged(app, before);
            check(!(boolean) get(app, "selectingVideo") && ((String) get(app, "statusMessage")).contains("recording continues"),
                    "Failed Load chooser must leave live capture usable and explain that it continues");
            encoder.writeFrame(new int[16]);
            app.key = cancel ? 'x' : 'e'; app.keyPressed();
            if (cancel) {
                check(encoder.awaitCleanup(10, TimeUnit.SECONDS) && !Files.exists(output), "X must discard after failed Load opening");
            } else {
                finish(app); check(Files.exists(output), "E must save after failed Load opening"); decoded(output, 2);
            }
        } finally { encoder.abort(); check(encoder.awaitCleanup(10, TimeUnit.SECONDS), "Live fixture encoder cleanup"); }
    }

    private static void testInterlocks() throws Exception {
        for (String state : new String[]{"full", "saving", "selectingVideo", "selectingProcessOutput", "selectingInteractiveOutput"}) {
            PickerApp app = configured(false);
            if (state.equals("full")) { set(app, "exporting", true); mode(app, "FULL_PROCESS"); }
            else if (state.equals("saving")) set(app, "pendingExportFinish", new CompletableFuture<String>());
            else set(app, state, true);
            for (String route : new String[]{"load", "live", "full"}) open(app, route);
            check(app.opens == 0, "Existing chooser/full-process/finishing interlocks must be preserved");
        }
    }

    private static PickerApp configured(boolean paused) throws Exception {
        PickerApp app = new PickerApp(); app.width = 4; app.height = 4;
        set(app, "video", movie()); set(app, "movieReady", true); set(app, "paused", paused);
        set(app, "currentVideoName", "owned.mp4");
        for (String field : new String[]{"pausedFrame", "frozenFrame", "previousFrame"}) set(app, field, new processing.core.PImage(4, 4));
        set(app, "freezeManual", true); set(app, "freezeFramesLeft", 3); return app;
    }
    private static void open(PickerApp app, String route) throws Exception {
        if (route.equals("load")) invoke(app, "promptForVideo");
        else if (route.equals("live")) app.startExport(); else app.processVideo();
    }
    private static String flag(String route) { return route.equals("load") ? "selectingVideo" : route.equals("live") ? "selectingInteractiveOutput" : "selectingProcessOutput"; }
    private static String shortcut(String route) { return route.equals("load") ? "L" : route.equals("live") ? "E" : "P"; }
    private static void selected(PickerApp app, String route, File file) {
        if (route.equals("load")) app.videoSelected(file); else if (route.equals("live")) app.interactiveOutputSelected(file); else app.processOutputSelected(file);
    }
    private static Map<String, Object> snapshot(VideoGlitcher app) throws Exception {
        Map<String, Object> result = new LinkedHashMap<>(); for (String field : PRESERVED) result.put(field, get(app, field)); return result;
    }
    private static void unchanged(VideoGlitcher app, Map<String, Object> before) throws Exception {
        for (var field : before.entrySet()) check(java.util.Objects.equals(get(app, field.getKey()), field.getValue()), "Opening/cancellation must preserve " + field.getKey());
    }
    private static final class PickerApp extends VideoGlitcher {
        boolean fail = true; int opens;
        private void opened() { opens++; if (fail) throw new IllegalStateException("owned picker failure"); }
        @Override public void selectInput(String prompt, String callback) { opened(); }
        @Override public void selectOutput(String prompt, String callback, File file) { opened(); }
        @Override processing.video.Movie createMovie(String source) { try { return movie(); } catch (Exception exception) { throw new RuntimeException(exception); } }
    }
    public static final class OwnedMovie extends processing.video.Movie {
        int starts, stops, disposals;
        private OwnedMovie() { super(null, "unused"); }
        @Override public void loop() { starts++; }
        @Override public void play() { starts++; }
        @Override public void noLoop() { }
        @Override public float duration() { return 3; }
        @Override public float time() { return 0; }
        @Override public void jump(float time) { }
        @Override public void read() { }
        @Override public void stop() { stops++; }
        @Override public void dispose() { disposals++; }
    }
    private static OwnedMovie movie() throws Exception {
        Field unsafe = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); unsafe.setAccessible(true);
        OwnedMovie movie = (OwnedMovie) ((sun.misc.Unsafe) unsafe.get(null)).allocateInstance(OwnedMovie.class);
        movie.width = 4; movie.height = 4; return movie;
    }
    private static void decoded(Path output, int frames) throws Exception {
        Path raw = output.resolveSibling(output.getFileName() + ".rgb");
        Process process = new ProcessBuilder("ffmpeg", "-v", "error", "-i", output.toString(), "-f", "rawvideo", "-pix_fmt", "rgb24", raw.toString()).inheritIO().start();
        try { check(process.waitFor(10, TimeUnit.SECONDS) && process.exitValue() == 0 && Files.size(raw) == 48L * frames, "Retry output must contain all owned 4x4 frames"); }
        finally { if (process.isAlive()) process.destroyForcibly(); }
    }
    private static void finish(VideoGlitcher app) throws Exception {
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (get(app, "pendingExportFinish") != null && System.nanoTime() < until) { invoke(app, "completePendingExport"); Thread.sleep(5); }
        check(get(app, "pendingExportFinish") == null, "Owned export must finish within its test bound");
    }
    private static void mode(VideoGlitcher app, String name) throws Exception { for (Object mode : Class.forName("tom.videoGlitcher.VideoGlitcher$ExportMode").getEnumConstants()) if (mode.toString().equals(name)) set(app, "exportMode", mode); }
    @SuppressWarnings("unchecked") private static void drain(VideoGlitcher app) throws Exception { var actions = (ConcurrentLinkedQueue<Runnable>) get(app, "pendingGuiActions"); Runnable action; while ((action = actions.poll()) != null) action.run(); }
    private static void invoke(VideoGlitcher app, String name) throws Exception { Method method = VideoGlitcher.class.getDeclaredMethod(name); method.setAccessible(true); method.invoke(app); }
    private static Object get(VideoGlitcher app, String name) throws Exception { Field field = VideoGlitcher.class.getDeclaredField(name); field.setAccessible(true); return field.get(app); }
    private static void set(VideoGlitcher app, String name, Object value) throws Exception { Field field = VideoGlitcher.class.getDeclaredField(name); field.setAccessible(true); field.set(app, value); }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); checks++; }
}
