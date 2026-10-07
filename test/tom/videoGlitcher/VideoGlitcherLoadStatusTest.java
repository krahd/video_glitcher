package tom.videoGlitcher;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

/** Load/export status with owned encoder fixtures and Movie doubles, not native acceptance. */
public final class VideoGlitcherLoadStatusTest {
    private static int checks;
    private enum Load { CONSTRUCTION_FAILURE, PLAYBACK_FAILURE, PATH_FALLBACK, PLAYBACK_FALLBACK, HEALTHY }

    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("video-glitcher-load-status-");
        try { run(directory); }
        finally {
            try (var files = Files.walk(directory)) {
                for (Path file : files.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(file);
            }
        }
    }

    static void run(Path root) throws Exception {
        for (String outcome : new String[]{"saved", "cancelled", "collision"}) {
            for (Load load : Load.values()) {
                for (boolean cleanupThrows : new boolean[]{false, true}) testQueuedLoad(root, outcome, load, cleanupThrows);
            }
        }
        testPickerCancel(root);
        System.out.println("Load status checks passed (" + checks + " checks; owned outputs and injected load outcomes).");
    }

    private static void testQueuedLoad(Path root, String outcome, Load load, boolean cleanupThrows) throws Exception {
        Path directory = Files.createDirectory(root.resolve("load-status-" + outcome + "-" + load + "-" + cleanupThrows));
        TestApp app = app(); app.load = load;
        PreviewMovie previous = movie(); previous.cleanupThrows = cleanupThrows;
        Path source = directory.resolve("source.mp4"); Files.writeString(source, "owned source");
        Path next = directory.resolve("next.mp4"); Files.writeString(next, "owned next input");
        Path output = directory.resolve("saved.mp4");
        set(app, "video", previous); set(app, "movieReady", true); set(app, "currentVideoName", source.getFileName().toString());
        set(app, "currentVideoFile", source.toFile()); set(app, "exportFilename", output.toString());
        // Cancellation is gated by an owned subprocess that cannot publish before X wins.
        FfmpegVideoExporter encoder;
        if (outcome.equals("cancelled")) {
            Path gate = directory.resolve("owned-encoder.sh");
            Files.writeString(gate, "#!/bin/sh\ncat >/dev/null\nexec sleep 60\n");
            check(gate.toFile().setExecutable(true), "Owned cancellation gate must be executable");
            encoder = FfmpegVideoExporter.start(gate.toString(), output.toString(), 4, 4, 24);
        } else encoder = FfmpegVideoExporter.start(output.toString(), 4, 4, 24);
        try {
            set(app, "videoExporter", encoder); set(app, "exporting", true); setMode(app, "FULL_PROCESS");
            int[] frame = new int[16]; Arrays.fill(frame, 0xff00ff00);
            for (int i = 0; i < 8; i++) encoder.writeFrame(frame);
            if (outcome.equals("collision")) Files.writeString(output, "owned concurrent output");
            // Exercise real public callbacks. A newer accepted choice replaces the queued choice;
            // cancelling another picker must neither forget that choice nor load it early.
            select(app, directory.resolve("superseded.mp4"));
            check(get(app, "video") == previous && get(app, "pendingVideoFile") != null, "Pending save must retain the old movie");
            select(app, next);
            if (load == Load.HEALTHY) {
                select(app, null);
                check(get(app, "pendingVideoFile").equals(next.toFile()) && get(app, "video") == previous,
                        "Picker cancellation must retain the latest queued choice and outgoing movie");
                check(status(app).contains(next.getFileName().toString()) && status(app).contains("Finishing"),
                        "A cancelled picker must still explain the queued load/export handoff");
            }
            CompletableFuture<String> finish = finishing(app);
            if (outcome.equals("cancelled")) app.cancelExport();
            else {
                String error = finish.get(10, TimeUnit.SECONDS);
                check((error == null) == outcome.equals("saved"), "Status must use the actual encoder result");
                if (outcome.equals("saved")) {
                    app.cancelExport(); // The real file is already committed: cancellation cannot relabel it.
                    check(Files.exists(output), "Cancel after publication must keep output");
                }
                invoke(app, "completePendingExport");
            }
            check(encoder.awaitCleanup(10, TimeUnit.SECONDS), "Encoder terminal cleanup must finish");
            check(get(app, "pendingVideoFile") == null && get(app, "pendingExportFinish") == null
                            && get(app, "videoExporter") == null && !(boolean) get(app, "exporting"), "Handoff must clear pending/export ownership");
            String result = outcome.equals("saved") ? "Saved export: " + output : "Export cancelled. No output saved";
            boolean failedLoad = load == Load.CONSTRUCTION_FAILURE || load == Load.PLAYBACK_FAILURE;
            if (outcome.equals("collision")) {
                check(status(app).startsWith("Export failed:") && status(app).contains("New clip not loaded")
                                && !status(app).contains("Saved export:"), "Failed save must retain its error and not imply a queued load started");
                check(app.attempts == 0 && get(app, "video") == previous && previous.disposals == 0,
                        "Failed export must leave queued replacement unstarted");
                check(Files.readString(output).equals("owned concurrent output"), "Concurrent destination must be preserved");
            } else {
                check(status(app).startsWith(result), "Queued load must preserve the actual saved/cancelled export result");
                check(status(app).contains(failedLoad ? "failed to load next.mp4" : "loading next.mp4 via "
                                + (load == Load.HEALTHY ? "path" : "file URI")), "Queued load must describe actual failure or accepted fallback, never an invented loading state");
                check(previous.stops == 1 && previous.disposals == 1 && previous.plays == 0,
                        "Replacing the outgoing movie must not restart it, even when cleanup throws");
                check((get(app, "video") == null) == failedLoad, "Status and Movie ownership must agree");
                for (PreviewMovie retired : app.created) {
                    if (retired != get(app, "video")) check(retired.disposals == 1, "Failed playback construction must be retired once");
                }
                if (!failedLoad) {
                    select(app, null);
                    check(status(app).startsWith(result) && status(app).contains("File selection cancelled"),
                            "Picker cancellation during decode must not erase the preceding export result");
                    // Delayed decode failure and delayed first-frame readiness are both load outcomes.
                    if (cleanupThrows) {
                        set(app, "videoLoadStartedNanos", System.nanoTime() - TimeUnit.SECONDS.toNanos(16));
                        invoke(app, "checkVideoLoadTimeout");
                        check(status(app).startsWith(result) && status(app).contains("did not decode within 15 seconds"),
                                "Asynchronous timeout must retain the export result and tell the truth about decode");
                        check(get(app, "video") == null && !(boolean) get(app, "movieReady"), "Timed-out load must be retired");
                    } else {
                        update(app);
                        check(status(app).startsWith(result) && status(app).contains("previewing next.mp4"),
                                "First decoded frame must replace loading with previewing without erasing the export result");
                        select(app, null);
                        check(status(app).contains("File selection cancelled") && status(app).contains("next.mp4")
                                        && !status(app).contains("no video selected"), "Cancelling a picker after readiness must truthfully retain the loaded clip");
                    }
                }
                if (outcome.equals("saved")) verifyOutput(directory, output);
                else check(!Files.exists(output), "Cancelled handoff must not publish output");
            }
            String terminal = status(app); app.stopExport(); app.cancelExport();
            check(status(app).equals(terminal), "Repeated terminal Stop/Cancel must not erase load/export outcomes");
            byte[] originalOutput = Files.exists(output) ? Files.readAllBytes(output) : null;
            // An explicit new load is a new operation and must not attach stale export or load errors.
            app.load = Load.HEALTHY;
            for (int retry = 0; retry < 2; retry++) {
                app.attempts = 0; select(app, source);
                check(status(app).equals("Status: loading source.mp4 via path"), "Explicit retry must clear the old export-result prefix");
                update(app); check(status(app).equals("Status: previewing source.mp4"), "Explicit retry must reach truthful ready status");
            }
            check(Files.readString(source).equals("owned source") && Files.readString(next).equals("owned next input"), "Load/status recovery must not alter input bytes");
            check(originalOutput == null ? !Files.exists(output) : Arrays.equals(originalOutput, Files.readAllBytes(output)), "Later retries must not change saved/concurrent output");
            try (var files = Files.list(directory)) {
                check(files.noneMatch(path -> path.getFileName().toString().startsWith(".video-glitcher-")), "No terminal staging file may remain");
            }
        } finally { encoder.abort(); check(encoder.awaitCleanup(10, TimeUnit.SECONDS), "Owned subprocess must be cleaned"); }
    }

    private static void testPickerCancel(Path root) throws Exception {
        TestApp empty = app(); select(empty, null);
        check(status(empty).equals("Status: no video selected") && get(empty, "video") == null, "No-current-clip cancellation must not invent a retained movie");
        for (boolean paused : new boolean[]{false, true}) {
            for (boolean ready : new boolean[]{false, true}) {
                TestApp app = app(); PreviewMovie current = movie();
                set(app, "video", current); set(app, "movieReady", ready); set(app, "paused", paused); set(app, "currentVideoName", "retained.mp4");
                processing.core.PImage snapshot = new processing.core.PImage(4, 4); set(app, "pausedFrame", snapshot);
                for (int repeat = 0; repeat < 3; repeat++) {
                    select(app, null);
                    check(status(app).contains("File selection cancelled") && status(app).contains("retained.mp4")
                                    && !status(app).contains("no video selected"), "Retained movie cancellation must not claim no video exists");
                    check(get(app, "video") == current && (boolean) get(app, "paused") == paused
                                    && (boolean) get(app, "movieReady") == ready && get(app, "pausedFrame") == snapshot,
                            "Picker cancellation must leave preview pause/readiness/frame ownership untouched");
                    check(current.plays == 0 && current.stops == 0 && current.disposals == 0 && app.attempts == 0,
                            "Picker cancellation must not touch playback or create a replacement");
                }
            }
        }
    }

    private static void verifyOutput(Path directory, Path output) throws Exception {
        Path decoded = directory.resolve("owned-decoded.rgb");
        ProcessBuilder builder = new ProcessBuilder("ffmpeg", "-v", "error", "-i", output.toString(), "-f", "rawvideo", "-pix_fmt", "rgb24", "-")
                .redirectOutput(decoded.toFile()).redirectError(ProcessBuilder.Redirect.INHERIT);
        FfmpegVideoExporter.configureEncoderEnvironment(builder.environment()); Process process = builder.start();
        try {
            check(process.waitFor(10, TimeUnit.SECONDS) && process.exitValue() == 0, "Saved queued-load output must decode");
            byte[] pixels = Files.readAllBytes(decoded);
            check(pixels.length == 8 * 4 * 4 * 3, "Saved output must retain all eight encoded frames");
            for (int i = 0; i < pixels.length; i += 3) {
                if ((pixels[i] & 255) > 12 || (pixels[i + 1] & 255) < 240 || (pixels[i + 2] & 255) > 12) throw new AssertionError("Saved pixels changed during queued load");
            }
            checks++;
        } finally { if (process.isAlive()) { process.destroyForcibly(); process.waitFor(5, TimeUnit.SECONDS); } Files.deleteIfExists(decoded); }
    }
    private static TestApp app() { TestApp app = new TestApp(); app.width = 4; app.height = 4; return app; }
    private static final class TestApp extends VideoGlitcher {
        Load load = Load.HEALTHY; int attempts; final List<PreviewMovie> created = new ArrayList<>();
        @Override processing.video.Movie createMovie(String source) {
            attempts++;
            if (load == Load.CONSTRUCTION_FAILURE || (load == Load.PATH_FALLBACK && attempts == 1)) throw new IllegalStateException("owned constructor failure");
            try {
                PreviewMovie movie = movie(); movie.failPlayback = load == Load.PLAYBACK_FAILURE || (load == Load.PLAYBACK_FALLBACK && attempts == 1);
                created.add(movie); return movie;
            } catch (Exception exception) { throw new RuntimeException(exception); }
        }
    }
    public static final class PreviewMovie extends processing.video.Movie {
        boolean failPlayback, cleanupThrows; int plays, stops, disposals;
        private PreviewMovie() { super(null, "unused"); }
        @Override public void play() { plays++; if (failPlayback) throw new IllegalStateException("owned playback failure"); }
        @Override public void loop() { play(); }
        @Override public void read() { }
        @Override public float duration() { return 3; }
        @Override public float time() { return 0; }
        @Override public void stop() { stops++; if (cleanupThrows) throw new IllegalStateException("owned stop failure"); }
        @Override public void dispose() { disposals++; if (cleanupThrows) throw new IllegalStateException("owned dispose failure"); }
    }
    private static PreviewMovie movie() throws Exception {
        Field unsafe = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); unsafe.setAccessible(true);
        PreviewMovie movie = (PreviewMovie) ((sun.misc.Unsafe) unsafe.get(null)).allocateInstance(PreviewMovie.class);
        movie.width = 4; movie.height = 4; return movie;
    }
    private static void update(TestApp app) throws Exception {
        Method method = VideoGlitcher.class.getDeclaredMethod("updateMovieFrame", processing.video.Movie.class); method.setAccessible(true); method.invoke(app, get(app, "video"));
    }
    private static void select(TestApp app, Path path) throws Exception {
        set(app, "selectingVideo", true); app.videoSelected(path == null ? null : path.toFile());
        check((boolean) get(app, "selectingVideo"), "Public picker callback must defer state to draw");
        drain(app); check(!(boolean) get(app, "selectingVideo"), "Draw must consume picker selection");
    }
    @SuppressWarnings("unchecked") private static void drain(TestApp app) throws Exception {
        var queue = (ConcurrentLinkedQueue<Runnable>) get(app, "pendingGuiActions"); Runnable action; while ((action = queue.poll()) != null) action.run();
    }
    @SuppressWarnings("unchecked") private static CompletableFuture<String> finishing(TestApp app) throws Exception { return (CompletableFuture<String>) get(app, "pendingExportFinish"); }
    private static void setMode(TestApp app, String mode) throws Exception {
        for (Object value : Class.forName("tom.videoGlitcher.VideoGlitcher$ExportMode").getEnumConstants()) if (value.toString().equals(mode)) set(app, "exportMode", value);
    }
    private static String status(TestApp app) throws Exception { return (String) get(app, "statusMessage"); }
    private static void invoke(TestApp app, String name) throws Exception { Method method = VideoGlitcher.class.getDeclaredMethod(name); method.setAccessible(true); method.invoke(app); }
    private static Object get(VideoGlitcher app, String name) throws Exception { Field field = VideoGlitcher.class.getDeclaredField(name); field.setAccessible(true); return field.get(app); }
    private static void set(VideoGlitcher app, String name, Object value) throws Exception { Field field = VideoGlitcher.class.getDeclaredField(name); field.setAccessible(true); field.set(app, value); }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); checks++; }
}
