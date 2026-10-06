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

        Path directory = Files.createTempDirectory("video-glitcher-workflow-");
        try {
            testSmokeTimeout(directory);
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
