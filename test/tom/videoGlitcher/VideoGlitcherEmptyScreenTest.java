package tom.videoGlitcher;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import controlP5.ControlP5;
import processing.awt.PGraphicsJava2D;
import processing.core.PSurface;

/** Complete owned Java2D canvas and real controls; no native window or video pipeline. */
public final class VideoGlitcherEmptyScreenTest {
    private static int checks;
    private static Path renders;

    public static void main(String[] args) throws Exception {
        renders = args.length == 1 ? Path.of(args[0]) : null;
        if (renders != null) Files.createDirectories(renders);
        Object original = field("launchOptions").get(null);
        Method parse = original.getClass().getDeclaredMethod("parse", String[].class); parse.setAccessible(true);
        // Suppress the platform menu-bar path; geometry cases do not assert native fullscreen behaviour.
        field("launchOptions").set(null, parse.invoke(null, (Object) new String[]{"--windowed"}));
        try {
            for (int[] size : new int[][]{{960, 540}, {800, 600}, {1280, 720}, {1920, 1080}}) {
                for (boolean advanced : new boolean[]{false, true}) {
                    try (EmptyApp app = new EmptyApp(size[0], size[1])) {
                        app.setup();
                        if (advanced) ((ControlP5) field("cp5").get(app)).getController("advancedGui").setValue(1);
                        String name = size[0] + "-" + (advanced ? "advanced" : "compact");
                        verify(app, name, true);
                        field("selectingVideo").setBoolean(app, true);
                        verify(app, name + "-dialog", true);
                        check(join(app).contains("File dialog open..."), "File-dialog feedback remains visible");
                        field("selectingVideo").setBoolean(app, false);
                        if (size[0] == 960) {
                            key(app, 'u'); verify(app, name + "-panel-hidden", true);
                            key(app, 'u'); verify(app, name + "-panel-restored", true);
                            key(app, 'h'); verify(app, name + "-both-hidden", true);
                            key(app, 'h'); verify(app, name + "-both-restored", true);
                            field("showHUD").setBoolean(app, false); verify(app, name + "-hud-hidden", true);
                            field("showHUD").setBoolean(app, true);
                            key(app, '?'); verifyGuide(app, name + "-guide");
                            key(app, (char) 27); verify(app, name + "-guide-closed", true);
                            check(app.key == 0, "Guide Escape must not exit");
                        }
                    }
                }
            }
            try (EmptyApp app = new EmptyApp(960, 540)) {
                app.setup();
                for (int[] size : new int[][]{{1280, 720}, {800, 600}, {960, 540}}) {
                    app.resizeOwned(size[0], size[1]); invoke(app, "refreshGuiLayout");
                    verify(app, "geometry-change-" + size[0], true);
                }
            }
            for (int[] size : new int[][]{{640, 540}, {540, 540}, {320, 240}}) {
                try (EmptyApp app = new EmptyApp(size[0], size[1])) {
                    app.setup(); verify(app, "unsupported-small-" + size[0], false);
                    // There may be no unoccluded space on a desktop too small for the existing panel.
                    if (size[0] == 640) check(join(app).contains("Press L"), "Narrow spare space prioritises Load");
                }
            }
        } finally { field("launchOptions").set(null, original); }
        System.out.println("Empty-screen layout passed: " + checks + " assertion increments; full owned canvases, no native acceptance.");
    }

    private static void verify(EmptyApp app, String name, boolean full) throws Exception {
        String before = (String) field("statusMessage").get(app);
        boolean selecting = field("selectingVideo").getBoolean(app);
        boolean gui = field("showGUI").getBoolean(app), hud = field("showHUD").getBoolean(app);
        int px = field("panelX").getInt(app), py = field("panelY").getInt(app);
        int pw = field("panelW").getInt(app), ph = field("panelH").getInt(app);
        app.emptyLines.clear(); app.draw();
        save(app, name);
        if (gui && full) {
            var load = ((ControlP5) field("cp5").get(app)).getController("loadVideo");
            check(load.isVisible() && (app.g.get((int) load.getPosition()[0] + 3, (int) load.getPosition()[1] + 3) & 0xffffff)
                    == (load.getColor().getBackground() & 0xffffff), "Actual Load control is drawn on this canvas: " + name);
        }
        if (full) {
            String text = join(app);
            for (String required : new String[]{"No video loaded", "Press L to load a video.", "Press ? for the workflow guide.",
                    "Choose a preset, hold C to compare, then P for a full-clip export.",
                    "Exports are silent MP4 at 24 fps, using the preview canvas size."}) {
                check(text.contains(required), "All initial guidance must remain visible: " + name + ": " + required);
            }
        }
        for (Line line : app.emptyLines) {
            check(line.x >= 0 && line.y >= 0 && line.right <= app.width && line.bottom <= app.height,
                    "Empty-state text stays inside complete canvas: " + name + ": " + line.text);
            if (gui) check(!line.intersects(px, py, px + pw, py + ph),
                    "Empty-state text must not be covered by actual later control panel: " + name + ": " + line.text);
            if (hud) check(!line.intersects(12, app.height - 54, app.width - 12, app.height - 12),
                    "Empty-state text must not be covered by later HUD: " + name);
        }
        check(before.equals(field("statusMessage").get(app)) && selecting == field("selectingVideo").getBoolean(app),
                "Rendering does not alter status or picker lock");
        check(field("video").get(app) == null && !field("movieReady").getBoolean(app)
                && !field("exporting").getBoolean(app) && field("videoExporter").get(app) == null,
                "Empty rendering cannot start loading, video or encoding");
        check(px == field("panelX").getInt(app) && py == field("panelY").getInt(app)
                && pw == field("panelW").getInt(app) && ph == field("panelH").getInt(app),
                "Instruction layout must not move or resize controls");
    }
    private static void verifyGuide(EmptyApp app, String name) throws Exception {
        app.emptyLines.clear(); app.draw(); save(app, name);
        check(field("showGuide").getBoolean(app), "Guide remains open");
        check(!((ControlP5) field("cp5").get(app)).isVisible(), "Guide hides real controls");
        check((app.g.get(80, 80) & 0xffffff) == 0x0c0f14, "Guide keeps its opaque background above the canvas");
    }
    private static void save(EmptyApp app, String name) { if (renders != null) app.g.get().save(renders.resolve(name + ".png").toString()); }
    private static String join(EmptyApp app) { return String.join(" ", app.emptyLines.stream().map(line -> line.text).toList()); }
    private static void key(EmptyApp app, char key) { app.key = key; app.keyPressed(); }
    private static Field field(String name) throws Exception { Field f = VideoGlitcher.class.getDeclaredField(name); f.setAccessible(true); return f; }
    private static void invoke(Object app, String name) throws Exception { Method m = VideoGlitcher.class.getDeclaredMethod(name); m.setAccessible(true); m.invoke(app); }
    private static void check(boolean condition, String message) { checks++; if (!condition) throw new AssertionError(message); }
    private record Line(String text, float x, float y, float right, float bottom) {
        boolean intersects(float left, float top, float right, float bottom) {
            return this.x < right && this.right > left && this.y < bottom && this.bottom > top;
        }
    }
    private static final class EmptyApp extends VideoGlitcher implements AutoCloseable {
        final List<Line> emptyLines = new ArrayList<>();
        EmptyApp(int w, int h) {
            resizeOwned(w, h);
            surface = (PSurface) Proxy.newProxyInstance(PSurface.class.getClassLoader(), new Class[]{PSurface.class}, (p, m, a) -> null);
        }
        void resizeOwned(int w, int h) {
            if (g != null) g.endDraw();
            width = w; height = h;
            if (g == null) {
                PGraphicsJava2D graphics = new PGraphicsJava2D(); graphics.setParent(this); graphics.setPrimary(false); g = graphics;
            }
            // Retain the actual graphics object also held by ControlP5 across geometry changes.
            g.setSize(w, h); g.beginDraw();
        }
        @Override public void frameRate(float value) { /* No animation thread or native surface. */ }
        @Override public void text(String text, float x, float y) {
            if (StackWalker.getInstance().walk(frames -> frames.anyMatch(frame -> frame.getMethodName().equals("drawNoVideoScreen")))) {
                float w = textWidth(text), h = textAscent() + textDescent();
                float left = g.textAlign == CENTER ? x - w / 2 : g.textAlign == RIGHT ? x - w : x;
                float top = g.textAlignY == CENTER ? y - h / 2 : g.textAlignY == TOP ? y : y - textAscent();
                emptyLines.add(new Line(text, left, top, left + w, top + h));
            }
            super.text(text, x, y);
        }
        @Override public void close() { g.endDraw(); g.dispose(); }
    }
}
