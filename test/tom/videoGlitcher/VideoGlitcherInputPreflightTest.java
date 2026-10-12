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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Owned input metadata and encoders; no native codec or dialog acceptance. */
public final class VideoGlitcherInputPreflightTest {
    private static int checks;
    private static final String[] PRESERVED = {"video", "movieReady", "paused", "pausedFrame", "frozenFrame", "previousFrame",
            "freezeManual", "freezeFramesLeft", "currentVideoFile", "currentVideoName", "currentVideoPath", "exportFilename",
            "videoExporter", "exporting", "exportMode", "pendingExportFinish", "pendingVideoFile", "lockedRenderSettings",
            "exportReachedPlaybackEnd", "videoLoadStartedNanos", "videoLoadExportOutcome"};

    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("video-input-preflight-");
        try { run(directory); }
        finally { try (var files = Files.walk(directory)) {
            for (Path path : files.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        } }
    }
    static void run(Path root) throws Exception {
        for (String kind : new String[]{"missing", "directory", "unreadable", "denied-stat", "denied-read", "dangling"}) {
            if (kind.equals("dangling") && System.getProperty("os.name").startsWith("Windows")) continue;
            for (String state : new String[]{"playing", "paused", "live", "full"}) rejectedSelection(root, kind, state);
        }
        for (boolean full : new boolean[]{false, true}) for (boolean cancel : new boolean[]{false, true}) queuedInputDisappears(root, full, cancel, false);
        for (boolean cancel : new boolean[]{false, true}) queuedInputDisappears(root, true, cancel, true);
        readableSymlink(root);
        emptyAndLoading(root);
        System.out.println("Input preflight checks passed (" + checks + " checks; no native acceptance).");
    }

    private static void rejectedSelection(Path root, String kind, String state) throws Exception {
        Path directory = Files.createDirectory(root.resolve("input-" + kind + "-" + state));
        TestApp app = configured(directory, state.equals("paused")); MovieDouble previous = (MovieDouble) get(app, "video");
        Path output = directory.resolve("recorded.mp4"); set(app, "exportFilename", output.toString());
        FfmpegVideoExporter encoder = (state.equals("live") || state.equals("full")) ? FfmpegVideoExporter.start(output.toString(), 4, 4, 24) : null;
        try {
            if (encoder != null) { encoder.writeFrame(new int[16]); set(app, "videoExporter", encoder); set(app, "exporting", true); mode(app, state.equals("full") ? "FULL_PROCESS" : "INTERACTIVE"); }
            File invalid = invalid(directory, kind); Map<String, Object> before = snapshot(app);
            select(app, invalid);
            check(get(app, "video") == previous && previous.stops == 0 && previous.disposals == 0,
                    "Invalid input must not retire the current preview");
            unchanged(app, before);
            check(app.creations == 0 && !(boolean) get(app, "selectingVideo"), "Reject before native construction and unlock Load");
            check(status(app).contains("readable") && status(app).contains("Press L"), "Explain readable-file requirement and retry action");
            if (encoder != null) {
                check(status(app).contains(state.equals("full") ? "Full-clip export continues" : "recording remains open"), "Explain that original capture continues");
                encoder.writeFrame(new int[16]); check(encoder.framesWritten() == 2, "Invalid selection must not close the encoder");
            }
            check(Files.readString(directory.resolve("source.mp4")).equals("owned source"), "Source bytes stay unchanged");
            Path retry = directory.resolve("retry owned ü.mp4"); Files.writeString(retry, "owned next source");
            select(app, retry.toFile());
            if (encoder != null) finish(app);
            MovieDouble replacement = (MovieDouble) get(app, "video");
            check(replacement != previous && app.creations == 1 && previous.disposals == 1 && replacement.starts == 1,
                    "A valid retry must replace and start the pipeline exactly once");
            check(!(boolean) get(app, "paused") && get(app, "currentVideoFile").equals(retry.toFile()), "Retry must select the exact readable source");
            if (encoder != null) { check(Files.size(output) > 0, "Valid retry must preserve the preceding recorded output"); decode(output, 2); }
        } finally { if (encoder != null) { encoder.abort(); check(encoder.awaitCleanup(10, TimeUnit.SECONDS), "Owned live encoder cleanup"); } }
    }

    private static void queuedInputDisappears(Path root, boolean full, boolean cancel, boolean resumeFails) throws Exception {
        Path directory = Files.createDirectory(root.resolve("input-queued-" + full + "-" + cancel + "-" + resumeFails));
        TestApp app = configured(directory, false); MovieDouble previous = (MovieDouble) get(app, "video");
        previous.failResume = resumeFails;
        Path output = directory.resolve("saved.mp4"); Path next = directory.resolve("next.mp4"); Files.writeString(next, "owned queued source");
        FfmpegVideoExporter encoder = FfmpegVideoExporter.start(output.toString(), 4, 4, 24);
        CountDownLatch gate = new CountDownLatch(1); CompletableFuture<String> future = null;
        try {
            encoder.writeFrame(new int[16]); set(app, "videoExporter", encoder); set(app, "exportFilename", output.toString()); mode(app, full ? "FULL_PROCESS" : "INTERACTIVE");
            // Hold a real finish so filesystem changes and cancellation are deterministic.
            future = CompletableFuture.supplyAsync(() -> {
                try { if (!gate.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("owned gate timed out"); encoder.finish(); return null; }
                catch (Exception exception) { return "Export failed: " + exception.getMessage(); }
            });
            set(app, "pendingExportFinish", future); set(app, "finishingExportFilename", output.toString()); set(app, "finishingExportMode", get(app, "exportMode"));
            select(app, next.toFile());
            check(get(app, "pendingVideoFile").equals(next.toFile()) && get(app, "video") == previous, "Readable selection must queue behind saving");
            Map<String, Object> before = snapshot(app); select(app, directory.resolve("later-missing.mp4").toFile()); unchanged(app, before);
            check(status(app).contains("next.mp4") && status(app).contains("Finishing"), "Invalid newer selection must retain and explain the earlier queued choice");
            Files.delete(next); if (full) Files.createDirectory(next); // Missing and directory replacements are both rejected.
            if (cancel) app.cancelExport();
            gate.countDown(); String error = future.get(10, TimeUnit.SECONDS);
            if (!cancel) { check(error == null, "Owned save must finish"); invoke(app, "completePendingExport"); }
            check(get(app, "video") == (resumeFails ? null : previous) && previous.disposals == (resumeFails ? 1 : 0) && app.creations == 0,
                    "Keep a usable outgoing preview; retire only if its own restoration fails");
            if (resumeFails) check(status(app).contains("Preview could not resume") && !status(app).contains("preview unchanged"),
                    "Restoration failure must remain explicit without claiming an unchanged preview");
            check(get(app, "pendingVideoFile") == null && get(app, "pendingExportFinish") == null && get(app, "videoExporter") == null,
                    "Rejected deferred load must clear finished export/selection ownership");
            check(status(app).startsWith(cancel ? "Export cancelled." : "Saved export: " + output) && status(app).contains("readable"),
                    "Keep the authoritative saved/cancelled result alongside rejected-input feedback");
            check(previous.starts == (full ? 1 : 0), "If the deferred input is lost, restore ordinary full-process playback exactly once");
            check(Files.exists(output) != cancel, "Preserve the actual save/discard result");
            if (!cancel) decode(output, 1);
        } finally { gate.countDown(); encoder.abort(); check(encoder.awaitCleanup(10, TimeUnit.SECONDS), "Owned finish cleanup"); if (future != null) future.get(10, TimeUnit.SECONDS); }
    }

    private static void readableSymlink(Path root) throws Exception {
        if (System.getProperty("os.name").startsWith("Windows")) return;
        TestApp app = configured(root, false); Path link = root.resolve("readable-alias.mp4");
        Files.createSymbolicLink(link, Path.of("source.mp4")); select(app, link.toFile());
        check(app.creations == 1 && get(app, "currentVideoFile").equals(link.toFile()) && Files.isSymbolicLink(link),
                "Readable regular-file symlinks must retain normal exact-selection behaviour");
    }

    private static void emptyAndLoading(Path root) throws Exception {
        TestApp empty = new TestApp(); select(empty, root.resolve("missing-first.mp4").toFile());
        check(empty.creations == 0 && get(empty, "video") == null && status(empty).contains("Press L"), "First invalid source must remain reloadable without native construction");
        TestApp loading = configured(root, false); set(loading, "movieReady", false); set(loading, "videoLoadStartedNanos", System.nanoTime());
        set(loading, "videoLoadExportOutcome", "Saved export: prior.mp4"); Map<String, Object> before = snapshot(loading);
        select(loading, root.resolve("missing-during-load.mp4").toFile()); unchanged(loading, before);
        check(status(loading).startsWith("Saved export: prior.mp4; "), "Invalid new selection must preserve the older loading/save context");
    }
    private static File invalid(Path directory, String kind) throws Exception {
        Path path = directory.resolve("invalid.mp4");
        if (kind.equals("directory")) Files.createDirectory(path);
        else if (kind.equals("dangling")) Files.createSymbolicLink(path, Path.of("missing-target"));
        else if (!kind.equals("missing")) Files.writeString(path, "owned unreadable fixture");
        if (kind.equals("unreadable") || kind.startsWith("denied")) return new File(path.toString()) {
            @Override public boolean isFile() { if (kind.equals("denied-stat")) throw new SecurityException("owned metadata denial"); return super.isFile(); }
            @Override public boolean canRead() { if (kind.equals("denied-read")) throw new SecurityException("owned read denial"); return false; }
        };
        return path.toFile();
    }
    private static TestApp configured(Path directory, boolean paused) throws Exception {
        TestApp app = new TestApp(); app.width = 4; app.height = 4; Path source = directory.resolve("source.mp4");
        if (!Files.exists(source)) Files.writeString(source, "owned source");
        set(app, "video", movie()); set(app, "movieReady", true); set(app, "paused", paused);
        set(app, "currentVideoFile", source.toFile()); set(app, "currentVideoPath", source.toString()); set(app, "currentVideoName", source.getFileName().toString());
        for (String field : new String[]{"pausedFrame", "frozenFrame", "previousFrame"}) set(app, field, new processing.core.PImage(4, 4));
        set(app, "freezeManual", true); set(app, "freezeFramesLeft", 3); return app;
    }
    private static final class TestApp extends VideoGlitcher {
        int creations;
        @Override processing.video.Movie createMovie(String source) { creations++; try { return movie(); } catch (Exception exception) { throw new RuntimeException(exception); } }
    }
    public static final class MovieDouble extends processing.video.Movie {
        int starts, stops, disposals; boolean failResume;
        private MovieDouble() { super(null, "unused"); }
        @Override public void loop() { starts++; if (failResume) throw new IllegalStateException("owned restore failure"); }
        @Override public void play() { starts++; }
        @Override public void stop() { stops++; }
        @Override public void dispose() { disposals++; }
    }
    private static MovieDouble movie() throws Exception {
        Field unsafe = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); unsafe.setAccessible(true);
        MovieDouble movie = (MovieDouble) ((sun.misc.Unsafe) unsafe.get(null)).allocateInstance(MovieDouble.class); movie.width = 4; movie.height = 4; return movie;
    }
    private static void select(TestApp app, File file) throws Exception { set(app, "selectingVideo", true); app.videoSelected(file); check((boolean) get(app, "selectingVideo"), "Selection must remain queued until draw"); drain(app); }
    private static Map<String, Object> snapshot(VideoGlitcher app) throws Exception { Map<String, Object> m = new LinkedHashMap<>(); for (String f : PRESERVED) m.put(f, get(app, f)); return m; }
    private static void unchanged(VideoGlitcher app, Map<String, Object> before) throws Exception { for (var f : before.entrySet()) check(java.util.Objects.equals(get(app, f.getKey()), f.getValue()), "Rejected input must preserve " + f.getKey()); }
    private static String status(VideoGlitcher app) throws Exception { return (String) get(app, "statusMessage"); }
    private static void finish(VideoGlitcher app) throws Exception { long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(10); while (get(app, "pendingExportFinish") != null && System.nanoTime() < until) { invoke(app, "completePendingExport"); Thread.sleep(5); } check(get(app, "pendingExportFinish") == null, "Owned finish deadline"); }
    private static void decode(Path output, int frames) throws Exception {
        Path pixels = output.resolveSibling(output.getFileName() + ".rgb"); Process p = new ProcessBuilder("ffmpeg", "-v", "error", "-i", output.toString(), "-f", "rawvideo", "-pix_fmt", "rgb24", pixels.toString()).inheritIO().start();
        try { check(p.waitFor(10, TimeUnit.SECONDS) && p.exitValue() == 0 && Files.size(pixels) == 48L * frames, "Preserved output must decode all owned 4x4 frames"); } finally { if (p.isAlive()) p.destroyForcibly(); }
    }
    private static void mode(VideoGlitcher app, String name) throws Exception { for (Object mode : Class.forName("tom.videoGlitcher.VideoGlitcher$ExportMode").getEnumConstants()) if (mode.toString().equals(name)) set(app, "exportMode", mode); }
    @SuppressWarnings("unchecked") private static void drain(VideoGlitcher app) throws Exception { var q = (ConcurrentLinkedQueue<Runnable>) get(app, "pendingGuiActions"); Runnable r; while ((r = q.poll()) != null) r.run(); }
    private static void invoke(VideoGlitcher app, String name) throws Exception { Method m = VideoGlitcher.class.getDeclaredMethod(name); m.setAccessible(true); m.invoke(app); }
    private static Object get(VideoGlitcher app, String name) throws Exception { Field f = VideoGlitcher.class.getDeclaredField(name); f.setAccessible(true); return f.get(app); }
    private static void set(VideoGlitcher app, String name, Object value) throws Exception { Field f = VideoGlitcher.class.getDeclaredField(name); f.setAccessible(true); f.set(app, value); }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); checks++; }
}
