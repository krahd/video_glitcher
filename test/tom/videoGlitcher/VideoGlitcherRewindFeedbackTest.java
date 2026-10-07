package tom.videoGlitcher;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/** Paused-seek wording and existing transport/capture policy; no fresh native frame claim. */
public final class VideoGlitcherRewindFeedbackTest {
    private static int checks;
    private static final String REQUESTED = "Status: rewind requested; preview updates on Play";

    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("video-glitcher-rewind-feedback-");
        try { run(directory); }
        finally {
            try (var files = Files.walk(directory)) {
                for (Path file : files.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(file);
            }
        }
    }

    static void run(Path directory) throws Exception {
        for (boolean live : new boolean[]{false, true}) {
            for (boolean ended : new boolean[]{false, true}) {
                for (boolean loop : new boolean[]{false, true}) testPausedRewind(directory, live, ended, loop);
            }
        }
        for (boolean loop : new boolean[]{false, true}) testPlayingRewind(loop);
        testInterlocks();
        System.out.println("Rewind feedback checks passed (" + checks + " checks; existing pause/capture policy preserved).");
    }

    private static void testPausedRewind(Path directory, boolean live, boolean ended, boolean loop) throws Exception {
        VideoGlitcher app = new VideoGlitcher(); RewindMovie movie = movie(ended);
        set(app, "video", movie); set(app, "movieReady", true); set(app, "paused", true); set(app, "loopPlayback", loop);
        set(app, "currentVideoName", "owned.mp4"); set(app, "pausedFrame", movie.snapshot);
        set(app, "exportReachedPlaybackEnd", ended && live);
        Path output = directory.resolve("rewind-feedback-" + live + "-" + ended + "-" + loop + ".mp4");
        FfmpegVideoExporter encoder = live ? FfmpegVideoExporter.start(output.toString(), 4, 4, 24) : null;
        try {
            if (live) {
                encoder.writeFrame(new int[16]); set(app, "videoExporter", encoder); set(app, "exporting", true); setMode(app, "INTERACTIVE");
            }
            boolean captureBefore = (boolean) invoke(app, "shouldWriteExportFrame");
            check(captureBefore == (live && !ended), "Manual paused live capture and ONCE-end gating must remain distinct");
            for (int repeat = 1; repeat <= 3; repeat++) {
                app.rewindToStart();
                check(get(app, "statusMessage").equals(REQUESTED), "Paused Rewind must report a request and Play instruction, not a completed preview update");
                check((boolean) get(app, "paused") && get(app, "video") == movie && get(app, "pausedFrame") == movie.snapshot,
                        "Paused Rewind must retain paused preview ownership");
                check(movie.jumps == repeat && movie.lastJump == 0 && movie.pauses == repeat,
                        "Each paused Rewind must keep the existing seek-to-zero and pause calls");
                check(movie.plays == 0 && movie.loops == 0 && movie.reads == 0,
                        "Paused Rewind must not start playback or force a Movie read");
                check((boolean) get(app, "exportReachedPlaybackEnd") == (live && ended)
                                && (boolean) invoke(app, "shouldWriteExportFrame") == captureBefore,
                        "Paused Rewind must neither clear the end gate nor change artistic frozen-frame capture policy");
                if (live) check(encoder.framesWritten() == 1, "Rewind must not itself add encoder frames");
            }
            app.pausePlay();
            check(!(boolean) get(app, "paused") && get(app, "pausedFrame") == null, "Explicit Play must release the paused snapshot normally");
            check(loop ? movie.loops == 1 && movie.plays == 0 : movie.plays == 1 && movie.loops == 0,
                    "Only explicit Play must resume the selected playback mode");
            check(get(app, "statusMessage").equals("Status: previewing owned.mp4"), "Play must replace the request hint with current preview feedback");
            if (live) {
                check(!(boolean) get(app, "exportReachedPlaybackEnd") && (boolean) invoke(app, "shouldWriteExportFrame"),
                        "Explicit Play must reopen the existing live-capture end gate");
                check(encoder.framesWritten() == 1, "The transport action must not directly write frames");
            }
            app.pausePlay(); app.rewindToStart();
            check((boolean) get(app, "paused") && get(app, "statusMessage").equals(REQUESTED), "Repeated Play/Pause/Rewind must keep the same truthful message");
            check(movie.reads == 0 && movie.stops == 0 && movie.disposals == 0, "Feedback changes must not read or retire a live movie");
        } finally {
            if (encoder != null) { encoder.abort(); check(encoder.awaitCleanup(10, TimeUnit.SECONDS), "Owned encoder must clean up"); }
            check(!Files.exists(output), "Transport-only fixture must not publish an output");
        }
    }

    private static void testPlayingRewind(boolean loop) throws Exception {
        VideoGlitcher app = new VideoGlitcher(); RewindMovie movie = movie(false);
        set(app, "video", movie); set(app, "movieReady", true); set(app, "loopPlayback", loop); set(app, "currentVideoName", "playing.mp4");
        app.rewindToStart();
        check(!(boolean) get(app, "paused") && movie.jumps == 1 && movie.lastJump == 0, "Playing Rewind must stay playing and seek normally");
        check(loop ? movie.loops == 1 && movie.plays == 0 : movie.plays == 1 && movie.loops == 0, "Playing Rewind must keep LOOP/ONCE API selection");
        check(movie.pauses == 0 && movie.reads == 0 && get(app, "statusMessage").equals("Status: previewing playing.mp4"),
                "Playing Rewind must not show the paused-preview instruction");
    }

    private static void testInterlocks() throws Exception {
        VideoGlitcher empty = new VideoGlitcher(); Object initial = get(empty, "statusMessage"); empty.rewindToStart();
        check(get(empty, "statusMessage").equals(initial), "No-current-video Rewind must remain a no-op");
        for (boolean paused : new boolean[]{false, true}) {
            VideoGlitcher app = new VideoGlitcher(); RewindMovie movie = movie(true);
            set(app, "video", movie); set(app, "movieReady", true); set(app, "paused", paused); set(app, "exporting", true); setMode(app, "FULL_PROCESS");
            set(app, "statusMessage", "owned full-process status"); app.rewindToStart(); app.pausePlay();
            check(movie.jumps == 0 && movie.pauses == 0 && movie.plays == 0 && movie.loops == 0 && movie.reads == 0,
                    "Full-process controls must not touch playback");
            check((boolean) get(app, "paused") == paused && get(app, "statusMessage").equals("owned full-process status"),
                    "Full-process interlock must keep pause and status unchanged");
        }
    }

    public static final class RewindMovie extends processing.video.Movie {
        int jumps, pauses, plays, loops, reads, stops, disposals; float lastJump, position;
        processing.core.PImage snapshot;
        private RewindMovie() { super(null, "unused"); }
        @Override public void jump(float value) { jumps++; lastJump = value; } // Model asynchronous seek: old image remains until later playback.
        @Override public void pause() { pauses++; }
        @Override public void play() { plays++; }
        @Override public void loop() { loops++; }
        @Override public void read() { reads++; }
        @Override public float time() { return position; }
        @Override public float duration() { return 8; }
        @Override public processing.core.PImage get() { return snapshot; }
        @Override public void stop() { stops++; }
        @Override public void dispose() { disposals++; }
    }
    private static RewindMovie movie(boolean ended) throws Exception {
        Field unsafe = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); unsafe.setAccessible(true);
        RewindMovie movie = (RewindMovie) ((sun.misc.Unsafe) unsafe.get(null)).allocateInstance(RewindMovie.class);
        movie.width = 4; movie.height = 4; movie.position = ended ? 8 : 4; movie.snapshot = new processing.core.PImage(4, 4); return movie;
    }
    private static void setMode(VideoGlitcher app, String mode) throws Exception {
        for (Object value : Class.forName("tom.videoGlitcher.VideoGlitcher$ExportMode").getEnumConstants()) if (value.toString().equals(mode)) set(app, "exportMode", value);
    }
    private static Object invoke(VideoGlitcher app, String name) throws Exception { Method method = VideoGlitcher.class.getDeclaredMethod(name); method.setAccessible(true); return method.invoke(app); }
    private static Object get(VideoGlitcher app, String name) throws Exception { Field field = VideoGlitcher.class.getDeclaredField(name); field.setAccessible(true); return field.get(app); }
    private static void set(VideoGlitcher app, String name, Object value) throws Exception { Field field = VideoGlitcher.class.getDeclaredField(name); field.setAccessible(true); field.set(app, value); }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); checks++; }
}
