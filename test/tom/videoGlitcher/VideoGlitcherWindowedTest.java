package tom.videoGlitcher;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import controlP5.ControlP5;
import controlP5.Controller;
import processing.awt.PGraphicsJava2D;
import processing.core.PSurface;
import processing.event.MouseEvent;

/** Owned headless layout/settings checks, never a native window or GPU acceptance test. */
public final class VideoGlitcherWindowedTest {
    private static int checks;

    public static void main(String[] args) throws Exception {
        Field options = field("launchOptions");
        Object original = options.get(null);
        try {
            options.set(null, parse("--windowed"));
            SettingsApp app = new SettingsApp();
            app.settings();
            check(app.sized && !app.fullscreen, "--windowed must select size rather than fullscreen");
            check(app.width == 960 && app.height == 540 && "processing.opengl.PGraphics2D".equals(app.renderer),
                    "Windowed canvas must be fixed 960x540 P2D");
            check(app.density == 1 && app.unsmoothed, "Existing pixel density and smoothing must remain");
            testArguments();
            testLayout(false);
            testLayout(true);
            testSmoke();
        } finally {
            options.set(null, original);
        }
        System.out.println("Windowed settings/headless layout passed: " + checks + " assertion increments; no native window/GPU acceptance.");
    }

    private static void testArguments() throws Exception {
        for (String[] args : new String[][]{ {}, {"--unknown"}, {"--windowed=false"}, {"--windowed=960x540"},
                {"--Windowed"}, {"--windowed "}, {"--windowed", "--windowed"}, {"--windowed"},
                {"--smoke-test"}, {"--smoke-test", "--windowed"}, {"--windowed", "--smoke-test"} }) {
            Object parsed = parse(args);
            boolean smoke = Arrays.asList(args).contains("--smoke-test");
            boolean windowed = smoke || Arrays.asList(args).contains("--windowed");
            check((boolean) option(parsed, "windowed") == windowed, "Exact flags select windowed presentation");
            check((boolean) option(parsed, "smokeTest") == smoke, "Windowed must not enable smoke behaviour");
            String[] expected = windowed ? new String[]{VideoGlitcher.class.getName()}
                    : new String[]{"--present", VideoGlitcher.class.getName()};
            check(Arrays.equals((String[]) option(parsed, "processingArgs"), expected), "Processing args must match selected presentation");
            check(option(parsed, "videoPath").equals("") && option(parsed, "presetName").equals("Cinematic"), "Default input/preset unchanged");
            check(!(boolean) option(parsed, "autoExport") && !(boolean) option(parsed, "autoProcess"), "Automation unchanged");
            check((int) option(parsed, "smokeFrames") == 60 && (int) option(parsed, "exportFrames") == 48, "Frame defaults unchanged");
            if (windowed) {
                field("launchOptions").set(null, parsed);
                SettingsApp app = new SettingsApp(); app.settings();
                check(app.sized && !app.fullscreen && app.width == 960 && app.height == 540, "Smoke and window flag combinations share fixed canvas");
            }
        }
        for (String suffix : new String[]{"", "--windowed", "--smoke-test"}) {
            Object parsed = parse("--windowed", "--video=/tmp/my clip ü.mp4", "--preset=vhs-decay", "--auto-export", "--auto-process",
                    "--smoke-frames=91", "--export-frames=37", suffix);
            check(option(parsed, "videoPath").equals("/tmp/my clip ü.mp4"), "Video path is preserved verbatim");
            check(option(parsed, "presetName").equals("VHS Decay"), "Preset aliases remain");
            check((boolean) option(parsed, "autoExport") && (boolean) option(parsed, "autoProcess"), "Existing automation flags remain");
            check((int) option(parsed, "smokeFrames") == 91 && (int) option(parsed, "exportFrames") == 37, "Existing frame flags remain");
        }
        Object invalid = parse("--windowed", "--smoke-frames=-1", "--export-frames=999999999999999999999", "--preset=invalid");
        check((int) option(invalid, "smokeFrames") == 60 && (int) option(invalid, "exportFrames") == 48
                && option(invalid, "presetName").equals("Cinematic"), "Existing invalid option fallbacks remain");
        check(!(boolean) option(parse(), "windowed"), "Windowed parsing must not leak into later launches");
    }

    private static void testLayout(boolean advanced) throws Exception {
        field("launchOptions").set(null, parse("--windowed"));
        LayoutApp app = new LayoutApp();
        try {
            app.setup();
            check(app.title.equals("video_glitcher"), "Normal window keeps normal title");
            check((boolean) field("showGUI").get(app) && (boolean) field("showHUD").get(app), "Normal window keeps controls/HUD visible");
            ControlP5 cp5 = (ControlP5) field("cp5").get(app);
            if (advanced) cp5.getController("advancedGui").setValue(1);
            check((boolean) field("compactGuiMode").get(app) != advanced, "Advanced toggle must reach normal control handler");
            int panelBottom = (int) field("panelY").get(app) + (int) field("panelH").get(app);
            check(panelBottom <= app.height - 54, "Control panel must stay above existing HUD");
            for (String name : new String[]{"loadVideo", "rewindToStart", "pausePlay", "togglePlaybackMode", "toggleGlitching",
                    "startExport", "stopExport", "processVideo", "advancedGui", "preset_0", "preset_1", "preset_2", "preset_3",
                    "preset_4", "preset_5", "preset_6", "preset_7"}) {
                Controller<?> control = cp5.getController(name);
                check(control != null && control.isVisible(), "Fixed control remains visible: " + name);
                checkFits(control, app, panelBottom);
            }
            String[] controls = advanced ? new String[]{"glitchIntensity", "glitchFrequency", "retroAmount", "subtleDamageChance",
                    "episodeMinFrames", "episodeMaxFrames", "calmMinFrames", "calmMaxFrames", "burstChance", "useRGBSplit",
                    "useSlices", "useBlocks", "useBars", "useDropouts", "useGhosts", "useFreeze", "useScanBursts", "useFlash",
                    "useMicroJitter", "useZoomWobble", "retroJitter", "trackingDrift", "headSwitchHeight", "chromaOffset",
                    "smearStrength", "columnDriftAmount", "useTrackingTear", "useHeadSwitchBand", "useChromaDrift",
                    "useScanlineWobble", "useVerticalSmear", "useColumnDrift"}
                    : new String[]{"glitchIntensity", "glitchFrequency", "retroAmount", "subtleDamageChance"};
            Set<String> fullySeen = new HashSet<>();
            int maxScroll = (int) invoke(app, "maxGuiScroll");
            app.mouseX = 40; app.mouseY = 200;
            for (int step = 0; step <= (maxScroll + 33) / 34; step++) {
                if (step > 0) wheel(app, 1);
                int top = (int) field("guiViewportTop").get(app), bottom = (int) field("guiViewportBottom").get(app);
                for (String name : controls) {
                    Controller<?> control = cp5.getController(name); float y = control.getPosition()[1];
                    if (control.isVisible() && y >= top && y + control.getHeight() <= bottom) fullySeen.add(name);
                }
            }
            check(fullySeen.equals(Set.of(controls)), "All selected controls must become fully visible via ordinary wheel steps");
            check((int) field("guiScrollOffset").get(app) == maxScroll, "Wheel must clamp at panel end");
            app.mouseX = 900; wheel(app, -1);
            check((int) field("guiScrollOffset").get(app) == maxScroll, "Outside wheel must not move controls");
            app.mouseX = 40;
            if (advanced) {
                cp5.getController("advancedGui").setValue(0);
                check((boolean) field("compactGuiMode").get(app) && (int) field("guiScrollOffset").get(app) == 0,
                        "Compact toggle resets advanced scroll");
                cp5.getController("advancedGui").setValue(1);
                check(!(boolean) field("compactGuiMode").get(app) && (int) field("guiScrollOffset").get(app) == 0,
                        "Advanced panel can reopen at its start");
            }
            app.key = 'u'; app.keyPressed(); check(!(boolean) field("showGUI").get(app), "U hides controls");
            int beforeHiddenWheel = (int) field("guiScrollOffset").get(app); wheel(app, 1);
            check((int) field("guiScrollOffset").get(app) == beforeHiddenWheel, "Hidden panel ignores wheel");
            app.key = 'u'; app.keyPressed(); check((boolean) field("showGUI").get(app), "U restores controls");
            app.key = 'h'; app.keyPressed(); check(!(boolean) field("showHUD").get(app), "H hides HUD");
            app.key = 'h'; app.keyPressed(); check((boolean) field("showHUD").get(app), "H restores HUD");
            app.key = '?'; app.keyPressed(); check((boolean) field("showGuide").get(app), "Guide opens");
            wheel(app, 1);
            check((int) field("guiScrollOffset").get(app) == beforeHiddenWheel, "Guide ignores panel wheel");
            app.key = 27; app.keyPressed(); check(!(boolean) field("showGuide").get(app) && app.key == 0, "Escape closes guide without exiting");
        } finally { app.g.endDraw(); app.g.dispose(); }
    }

    private static void testSmoke() throws Exception {
        for (String[] args : new String[][]{{"--smoke-test"}, {"--windowed", "--smoke-test"}}) {
            field("launchOptions").set(null, parse(args)); LayoutApp app = new LayoutApp();
            try {
                app.setup();
                check(app.title.equals("video_glitcher Smoke Test"), "Smoke title remains");
                check(!(boolean) field("showGUI").get(app) && !(boolean) field("showHUD").get(app), "Smoke hides GUI/HUD with either flag combination");
            } finally { app.g.endDraw(); app.g.dispose(); }
        }
    }

    private static void wheel(LayoutApp app, int amount) {
        app.mouseWheel(new MouseEvent(null, 0, MouseEvent.WHEEL, 0, app.mouseX, app.mouseY, 0, amount));
    }

    private static void checkFits(Controller<?> control, LayoutApp app, int bottom) {
        float[] p = control.getPosition();
        check(p[0] >= 0 && p[1] >= 0 && p[0] + control.getWidth() <= app.width && p[1] + control.getHeight() <= bottom,
                "Fixed control bounds must fit window and panel: " + control.getName());
    }
    private static Object parse(String... args) throws Exception {
        Method parse = field("launchOptions").getType().getDeclaredMethod("parse", String[].class); parse.setAccessible(true);
        return parse.invoke(null, (Object) args);
    }
    private static Object option(Object options, String name) throws Exception {
        Method m = options.getClass().getDeclaredMethod(name); m.setAccessible(true); return m.invoke(options);
    }
    private static Object invoke(Object app, String name) throws Exception {
        Method m = VideoGlitcher.class.getDeclaredMethod(name); m.setAccessible(true); return m.invoke(app);
    }
    private static Field field(String name) throws Exception {
        Field f = VideoGlitcher.class.getDeclaredField(name); f.setAccessible(true); return f;
    }
    private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }

    private static class SettingsApp extends VideoGlitcher {
        boolean sized, fullscreen, unsmoothed; int density; String renderer;
        @Override public void size(int w, int h, String renderer) { sized = true; width = w; height = h; this.renderer = renderer; }
        @Override public void fullScreen(String renderer) { fullscreen = true; }
        @Override public void pixelDensity(int value) { density = value; }
        @Override public void noSmooth() { unsmoothed = true; }
    }
    private static final class LayoutApp extends SettingsApp {
        String title;
        LayoutApp() {
            width = 960; height = 540;
            PGraphicsJava2D graphics = new PGraphicsJava2D(); graphics.setParent(this); graphics.setPrimary(false);
            graphics.setSize(width, height); g = graphics; g.beginDraw();
            surface = (PSurface) Proxy.newProxyInstance(PSurface.class.getClassLoader(), new Class[]{PSurface.class},
                    (proxy, method, args) -> { if (method.getName().equals("setTitle")) title = (String) args[0]; return null; });
        }
        @Override public void frameRate(float value) { /* No animation thread/surface. */ }
    }
}
