package tom.videoGlitcher;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/** Real Processing callback registration with owned Movie doubles; no native pipeline is constructed. */
public final class VideoGlitcherLifecycleTest {
    private static int checks;

    static void run(Path directory) throws Exception {
        testReplacementAndCancellation(directory);
        testDecodeTimeoutAndRetry(directory);
        testPlaybackFailureAndFallback(directory);
        testRepeatedReleaseAndStopFailure(directory);
        testExportDefersReplacement(directory, false);
        testExportDefersReplacement(directory, true);
        testFullProcessKeepsItsPipeline(directory);
        System.out.println("Video pipeline lifecycle checks passed (" + checks + " checks; native cleanup is not measured).");
    }

    private static void testReplacementAndCancellation(Path directory) throws Exception {
        LifecycleApp app = new LifecycleApp();
        select(app, directory.resolve("first-owned.mp4"));
        TrackingMovie first = app.created.get(0);
        app.firePost();
        check(first.posts == 1 && first.disposals == 0, "Current movie must retain its Processing callbacks");
        set(app, "selectingVideo", true); app.videoSelected(null); drain(app);
        check(get(app, "video") == first && first.disposals == 0, "Picker cancellation must keep the current pipeline");
        select(app, directory.resolve("second-owned.mp4"));
        TrackingMovie second = app.created.get(1);
        check(first.stops == 1 && first.disposals == 1 && first.detachedBeforeDispose,
                "Replacement must detach, stop and dispose the retired pipeline exactly once");
        app.firePost();
        check(first.posts == 1 && second.posts == 1, "Retired movie must unregister its post callback");
        app.fireDisposal();
        check(first.disposals == 1 && second.disposals == 1, "Framework disposal must not revisit a retired pipeline");
    }

    private static void testDecodeTimeoutAndRetry(Path directory) throws Exception {
        LifecycleApp app = new LifecycleApp();
        select(app, directory.resolve("timeout-owned.mp4"));
        TrackingMovie timedOut = app.created.get(0);
        set(app, "videoLoadStartedNanos", System.nanoTime() - 16_000_000_000L);
        invoke(app, "checkVideoLoadTimeout");
        check(get(app, "video") == null && timedOut.stops == 1 && timedOut.disposals == 1,
                "A decode timeout must dispose the abandoned pipeline before a retry");
        app.firePost();
        check(timedOut.posts == 0, "Timed-out movie must no longer receive Processing callbacks");
        select(app, directory.resolve("retry-owned.mp4"));
        TrackingMovie retry = app.created.get(1);
        check(get(app, "video") == retry && retry.plays == 1 && retry.disposals == 0,
                "Retry must own a new live pipeline");
        invoke(app, "releaseVideo");
        check(retry.disposals == 1, "Retry cleanup must dispose its own pipeline");
    }

    private static void testPlaybackFailureAndFallback(Path directory) throws Exception {
        LifecycleApp app = new LifecycleApp();
        app.playFailures.add(true); app.playFailures.add(false);
        select(app, directory.resolve("fallback-owned.mp4"));
        TrackingMovie failed = app.created.get(0), fallback = app.created.get(1);
        check(failed.stops == 1 && failed.disposals == 1 && failed.detachedBeforeDispose,
                "Playback-start failure must dispose the created pipeline before URI fallback");
        check(get(app, "video") == fallback && fallback.plays == 1 && fallback.disposals == 0,
                "URI fallback must preserve only its successful pipeline");
        app.firePost();
        check(failed.posts == 0 && fallback.posts == 1, "Failed pipeline callbacks must be removed before fallback");
        invoke(app, "releaseVideo");

        app = new LifecycleApp();
        app.playFailures.add(true); app.playFailures.add(true);
        select(app, directory.resolve("failed-owned.mp4"));
        check(get(app, "video") == null && app.created.size() == 2,
                "Failed path and URI starts must leave no selected pipeline");
        for (TrackingMovie movie : app.created) check(movie.disposals == 1, "Every constructed failed pipeline must be disposed");
        app.firePost(); app.fireDisposal();
        for (TrackingMovie movie : app.created) check(movie.posts == 0 && movie.disposals == 1,
                "Failed pipelines must not receive later post/dispose callbacks");
    }

    private static void testRepeatedReleaseAndStopFailure(Path directory) throws Exception {
        LifecycleApp app = new LifecycleApp();
        select(app, directory.resolve("stop-failure-owned.mp4"));
        TrackingMovie failedStop = app.created.get(0);
        failedStop.failStop = true;
        invoke(app, "releaseVideo"); invoke(app, "releaseVideo");
        check(get(app, "video") == null && failedStop.stops == 1 && failedStop.disposals == 1,
                "A stop exception must not skip disposal, and repeated release must not double-dispose");
        for (int i = 0; i < 20; i++) select(app, directory.resolve("cycle-" + i + ".mp4"));
        app.firePost();
        for (int i = 0; i < app.created.size() - 1; i++) {
            TrackingMovie movie = app.created.get(i);
            check(movie.disposals == 1 && movie.posts == 0, "Repeated replacement must leave no retired callbacks");
        }
        TrackingMovie current = app.created.get(app.created.size() - 1);
        check(current.disposals == 0 && current.posts == 1, "The latest movie remains owned and registered");
        invoke(app, "releaseVideo");
        app.fireDisposal();
        check(current.disposals == 1, "Final release must unregister framework disposal");
    }

    private static void testExportDefersReplacement(Path directory, boolean cancel) throws Exception {
        LifecycleApp app = new LifecycleApp();
        select(app, directory.resolve("before-export-" + cancel + ".mp4"));
        TrackingMovie previous = app.created.get(0);
        set(app, "movieReady", true);
        Path output = directory.resolve("lifecycle-export-" + cancel + ".mp4");
        FfmpegVideoExporter encoder;
        if (cancel) {
            Path stalled = directory.resolve("lifecycle-stalled-encoder.sh");
            Files.writeString(stalled, "#!/bin/sh\ncat >/dev/null\nexec sleep 60\n");
            check(stalled.toFile().setExecutable(true), "Owned stalled encoder must be executable");
            encoder = FfmpegVideoExporter.start(stalled.toString(), output.toString(), 4, 4, 24);
        } else {
            encoder = FfmpegVideoExporter.start(output.toString(), 4, 4, 24);
        }
        try {
            encoder.writeFrame(new int[16]);
            set(app, "videoExporter", encoder); set(app, "exporting", true); set(app, "exportFilename", output.toString());
            setMode(app, "INTERACTIVE");
            select(app, directory.resolve("after-export-" + cancel + ".mp4"));
            check(get(app, "video") == previous && previous.disposals == 0 && get(app, "pendingVideoFile") != null,
                    "A queued next clip must not dispose the movie while current output is being finalised");
            app.firePost();
            check(previous.posts == 1, "Deferred replacement must leave the current callback registered");
            @SuppressWarnings("unchecked")
            CompletableFuture<String> finishing = (CompletableFuture<String>) get(app, "pendingExportFinish");
            check(finishing != null, "Selecting a next clip must finalise the active recording first");
            if (cancel) {
                app.cancelExport();
                check(encoder.awaitCleanup(10, TimeUnit.SECONDS), "Cancellation must clean its owned encoder");
                finishing.get(10, TimeUnit.SECONDS);
                check(!Files.exists(output), "Cancelling deferred output must not publish it");
            } else {
                check(finishing.get(10, TimeUnit.SECONDS) == null, "Output must finish successfully before replacement");
                check(previous.disposals == 0, "Worker completion alone must not dispose draw-thread ownership");
                invoke(app, "completePendingExport");
                check(Files.exists(output), "Completed output must survive the following pipeline release");
            }
            check(previous.disposals == 1 && previous.detachedBeforeDispose && app.created.size() == 2,
                    "Finish/cancel must release the previous movie once and accept the deferred clip");
            TrackingMovie next = app.created.get(1);
            app.firePost();
            check(previous.posts == 1 && next.posts == 1 && get(app, "video") == next,
                    "Only the replacement pipeline may receive subsequent frame callbacks");
            invoke(app, "releaseVideo");
        } finally {
            encoder.abort();
            check(encoder.awaitCleanup(10, TimeUnit.SECONDS), "Lifecycle fixture encoder must be cleaned");
        }
    }

    private static void testFullProcessKeepsItsPipeline(Path directory) throws Exception {
        LifecycleApp app = new LifecycleApp();
        select(app, directory.resolve("full-process-owned.mp4"));
        TrackingMovie current = app.created.get(0);
        set(app, "movieReady", true); set(app, "exporting", true); setMode(app, "FULL_PROCESS");
        app.loadVideo(); app.rewindToStart(); app.pausePlay();
        check(get(app, "video") == current && current.disposals == 0 && !((boolean) get(app, "selectingVideo")),
                "Full-process controls must not replace or release the pipeline they own");
        set(app, "exporting", false); setMode(app, "NONE");
        invoke(app, "releaseVideo");
    }

    private static void setMode(VideoGlitcher app, String selected) throws Exception {
        for (Object mode : Class.forName("tom.videoGlitcher.VideoGlitcher$ExportMode").getEnumConstants()) {
            if (mode.toString().equals(selected)) set(app, "exportMode", mode);
        }
    }

    private static final class LifecycleApp extends VideoGlitcher {
        final List<TrackingMovie> created = new ArrayList<>();
        final ArrayDeque<Boolean> playFailures = new ArrayDeque<>();
        @Override processing.video.Movie createMovie(String source) {
            try {
                Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); field.setAccessible(true);
                TrackingMovie movie = (TrackingMovie) ((sun.misc.Unsafe) field.get(null)).allocateInstance(TrackingMovie.class);
                movie.owner = this; movie.width = 4; movie.height = 4;
                movie.failPlay = !playFailures.isEmpty() && playFailures.remove();
                created.add(movie);
                registerMethod("post", movie); registerMethod("dispose", movie);
                return movie;
            } catch (Exception exception) { throw new RuntimeException(exception); }
        }
        void firePost() { handleMethods("post"); }
        void fireDisposal() { handleMethods("dispose"); }
    }

    public static final class TrackingMovie extends processing.video.Movie {
        LifecycleApp owner;
        int stops, disposals, posts, plays;
        boolean failPlay, failStop, detachedBeforeDispose;
        private TrackingMovie() { super(null, "unused"); }
        @Override public void stop() { stops++; if (failStop) throw new IllegalStateException("owned stop failure"); }
        @Override public void play() { plays++; if (failPlay) throw new IllegalStateException("owned playback failure"); }
        @Override public void loop() { play(); }
        @Override public void post() { posts++; }
        @Override public void dispose() {
            disposals++;
            try { detachedBeforeDispose = VideoGlitcherLifecycleTest.get(owner, "video") != this; }
            catch (Exception exception) { throw new RuntimeException(exception); }
            owner.unregisterMethod("post", this); owner.unregisterMethod("dispose", this);
        }
    }

    private static void select(LifecycleApp app, Path file) throws Exception {
        set(app, "selectingVideo", true); app.videoSelected(file.toFile()); drain(app);
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
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message); checks++;
    }
}
