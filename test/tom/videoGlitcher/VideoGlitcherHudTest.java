package tom.videoGlitcher;

import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import processing.awt.PGraphicsJava2D;
import processing.core.PApplet;

/** Actual HUD draw calls on an owned Java2D image; injected counters are not encoded media. */
public final class VideoGlitcherHudTest {
    private static int checks;
    private static final String LONG_PATH = "/owned/" + "project with a very long name/".repeat(12) + "clip.mp4";
    private static final String UNICODE_PATH = "/owned/" + "café e\u0301 😀 日本語/".repeat(18) + "result 🎞.mp4";

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && args[0].equals("--case")) {
            run(args.length > 1 ? Path.of(args[1]) : null);
            return;
        }
        for (String locale : new String[]{"en-US", "de-DE", "ar-EG"}) {
            String[] parts = locale.split("-");
            List<String> command = new ArrayList<>(List.of(
                    Path.of(System.getProperty("java.home"), "bin", "java").toString(), "-Djava.awt.headless=true",
                    "-Duser.language=" + parts[0], "-Duser.country=" + parts[1], "-cp", System.getProperty("java.class.path"),
                    VideoGlitcherHudTest.class.getName(), "--case"));
            if (args.length == 1) command.add(Path.of(args[0], locale).toString());
            Process child = new ProcessBuilder(command).inheritIO().start();
            try {
                if (!child.waitFor(45, TimeUnit.SECONDS)) throw new AssertionError("Owned HUD fixture timed out: " + locale);
                if (child.exitValue() != 0) throw new AssertionError("Owned HUD fixture failed: " + locale);
            } finally {
                if (child.isAlive()) { child.destroyForcibly(); child.waitFor(10, TimeUnit.SECONDS); }
            }
        }
    }

    private static void run(Path renders) throws Exception {
        Path directory = Files.createTempDirectory("glitcher-hud-fixture-");
        Path output = directory.resolve("never-published.mp4"), temporary = Files.createTempFile(directory, ".owned-", ".part");
        Constructor<FfmpegVideoExporter> constructor = FfmpegVideoExporter.class.getDeclaredConstructor(
                Process.class, FfmpegVideoExporter.FrameSpec.class, Path.class, Path.class);
        constructor.setAccessible(true);
        FfmpegVideoExporter exporter = constructor.newInstance(new OwnedProcess(), FfmpegVideoExporter.frameSpecFor(4, 4), output, temporary);
        try {
            renderCase(exporter, "Exporting: " + LONG_PATH, 120, true, 960, true, renders, "long-path");
            renderCase(exporter, "Exporting: " + UNICODE_PATH, 120, true, 960, true, renders, "unicode-path");
            renderCase(exporter, "Exporting: clip.mp4", 120, true, 960, true, renders, "short-status");
            renderCase(exporter, "Exporting: " + UNICODE_PATH, Long.MAX_VALUE, true, 960, true, renders, "large-counter");
            renderCase(exporter, "Exporting: " + LONG_PATH, 0, true, 1920, true, null, "zero-counter");
            renderCase(exporter, "Could not open video chooser: " + LONG_PATH, 120, true, 960, true, renders, "active-warning");
            renderCase(exporter, "Export failed; no output saved: " + LONG_PATH, 120, false, 960, false, renders, "error");
            renderCase(exporter, "Saving export: " + LONG_PATH, 120, false, 960, false, renders, "saving");
            renderCase(exporter, "Export cancelled. No output saved; source and previous exports unchanged.", 120, false, 960, false, null, "cancelled");
            renderCase(exporter, "Saved export: " + LONG_PATH, 120, false, 960, false, null, "saved");
            renderCase(exporter, "Exporting: " + LONG_PATH, 120, true, 160, false, renders, "narrow-status-priority");
            renderCase(null, "Status: ready", 0, true, 960, false, null, "missing-encoder");
            renderCase(exporter, "Exporting: clip.mp4", 120, true, 960, true, null, "repeated-live");
            testTextBounds();
        } finally {
            exporter.abort();
            check(exporter.awaitCleanup(10, TimeUnit.SECONDS), "Owned counter fixture must clean up");
            check(!Files.exists(output) && !Files.exists(temporary), "HUD test must never publish its owned marker");
            Files.deleteIfExists(temporary); Files.deleteIfExists(output); Files.delete(directory);
        }
        System.out.println("HUD layout passed: " + checks + " assertion increments, locale " + Locale.getDefault()
                + "; measured Java2D text, no native window/GPU/media acceptance.");
    }

    private static void renderCase(FfmpegVideoExporter exporter, String status, long count, boolean active,
            int width, boolean expectProgress, Path renders, String name) throws Exception {
        if (exporter != null) {
            Field pool = FfmpegVideoExporter.class.getDeclaredField("frameBuffers"); pool.setAccessible(true);
            Field frames = FfmpegVideoExporter.class.getDeclaredField("framesWritten"); frames.setAccessible(true);
            synchronized (pool.get(exporter)) { frames.setLong(exporter, count); }
        }
        HudApp app = new HudApp(width);
        try {
            field("statusMessage").set(app, status); field("currentVideoName").set(app, UNICODE_PATH);
            field("exportFilename").set(app, LONG_PATH); field("videoExporter").set(app, exporter); field("exporting").set(app, active);
            Field mode = field("exportMode");
            for (Object value : mode.getType().getEnumConstants()) {
                if (((Enum<?>) value).name().equals(active ? "INTERACTIVE" : "NONE")) mode.set(app, value);
            }
            Method draw = VideoGlitcher.class.getDeclaredMethod("drawHUD"); draw.setAccessible(true); draw.invoke(app);
            if (renders != null) { Files.createDirectories(renders); app.g.get(0, 470, width, 70).save(renders.resolve(name + ".png").toString()); }
            check(app.lines.size() == 2, "HUD retains its two existing lines");
            String first = app.lines.get(0);
            String suffix = " [" + count + " frames / " + PApplet.nf(count / 24f, 0, 1) + "s output]";
            if (expectProgress) check(first.endsWith(suffix), "Live progress must remain visible after long status/path: " + name);
            else check(!first.contains("s output]"), "Inactive/missing encoder or narrow status priority must not show live counts: " + name);
            if (name.equals("short-status")) check(first.equals(status + suffix), "Short status presentation remains unchanged");
            if (name.equals("active-warning")) check(first.startsWith("Could not open video chooser:"), "Active warning keeps its actionable prefix");
            if (name.equals("error")) check(first.startsWith("Export failed; no output saved:"), "Error prefix has priority");
            if (name.equals("saving")) check(first.startsWith("Saving export:"), "Saving does not imply completed frames/file");
            for (String line : app.lines) {
                check(app.textWidth(line) <= width - 40 + 0.1f, "Measured HUD text stays inside horizontal padding: " + name);
                check(wellFormedUtf16(line), "Displayed Unicode must not contain a broken surrogate: " + name);
            }
            check(field("statusMessage").get(app).equals(status), "Full status/path must stay unchanged");
            check(field("exportFilename").get(app).equals(LONG_PATH), "Full output filename must stay unchanged");
            if (name.equals("long-path")) {
                for (String outcome : new String[]{"Saving export: " + LONG_PATH, "Export failed; no output saved: " + LONG_PATH,
                        "Export cancelled. No output saved.", "Saved export: " + LONG_PATH}) {
                    field("exporting").set(app, false); field("statusMessage").set(app, outcome); app.lines.clear(); draw.invoke(app);
                    check(!app.lines.get(0).contains("s output]"), "A live-to-terminal transition must remove stale counts");
                    check(field("statusMessage").get(app).equals(outcome), "Terminal outcome remains exact after drawing");
                }
                field("exporting").set(app, true); field("statusMessage").set(app, status); app.lines.clear(); draw.invoke(app);
                check(app.lines.get(0).endsWith(suffix), "A later active draw restores live progress");
            }
        } finally { app.g.endDraw(); app.g.dispose(); }
    }

    private static void testTextBounds() throws Exception {
        HudApp app = new HudApp(960);
        try {
            Method fit = VideoGlitcher.class.getDeclaredMethod("fitTextToWidth", String.class, float.class); fit.setAccessible(true);
            app.textSize(13);
            for (String text : new String[]{null, "", "Short", "😀".repeat(40), "e\u0301".repeat(40), "日本語".repeat(40)}) {
                for (float width : new float[]{-1, 0, 1, 10, 20, 41, 99, 450}) {
                    String actual = (String) fit.invoke(app, text, width);
                    check(wellFormedUtf16(actual), "Fitting must preserve Unicode code points");
                    check(app.textWidth(actual) <= Math.max(0, width) + 0.1f, "Fitting must respect even sub-ellipsis width");
                    if (text != null && width > 0 && app.textWidth(text) <= width) check(actual.equals(text), "A fitting string stays exact");
                }
            }
            VideoGlitcher measured = new VideoGlitcher() {
                @Override public float textWidth(String value) { return value.codePointCount(0, value.length()) * 10; }
            };
            for (int width = 0; width <= 120; width++) {
                String actual = (String) fit.invoke(measured, "A😀B😀C😀D😀E😀F", (float) width);
                check(wellFormedUtf16(actual), "Code-point regression cannot depend on platform font glyph coverage");
                check(measured.textWidth(actual) <= width, "Owned text metrics remain bounded");
            }
        } finally { app.g.endDraw(); app.g.dispose(); }
    }

    private static boolean wellFormedUtf16(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isHighSurrogate(c)) { if (++i >= text.length() || !Character.isLowSurrogate(text.charAt(i))) return false; }
            else if (Character.isLowSurrogate(c)) return false;
        }
        return true;
    }
    private static Field field(String name) throws Exception { Field f = VideoGlitcher.class.getDeclaredField(name); f.setAccessible(true); return f; }
    private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
    private static final class HudApp extends VideoGlitcher {
        final List<String> lines = new ArrayList<>();
        HudApp(int width) {
            this.width = width; height = 540;
            PGraphicsJava2D graphics = new PGraphicsJava2D(); graphics.setParent(this); graphics.setPrimary(false); graphics.setSize(width, height);
            g = graphics; g.beginDraw(); background(35); textFont(createFont("SansSerif", 13, true));
        }
        @Override public void text(String text, float x, float y) { lines.add(text); super.text(text, x, y); }
    }
    private static final class OwnedProcess extends Process {
        volatile boolean alive = true;
        public OutputStream getOutputStream() { return OutputStream.nullOutputStream(); }
        public InputStream getInputStream() { return InputStream.nullInputStream(); }
        public InputStream getErrorStream() { return InputStream.nullInputStream(); }
        public int waitFor() { alive = false; return 0; }
        public boolean waitFor(long time, TimeUnit unit) { alive = false; return true; }
        public int exitValue() { if (alive) throw new IllegalThreadStateException(); return 0; }
        public void destroy() { alive = false; }
        public Process destroyForcibly() { destroy(); return this; }
        public boolean isAlive() { return alive; }
        public java.util.stream.Stream<ProcessHandle> descendants() { return java.util.stream.Stream.empty(); }
    }
}
