package tom.videoGlitcher;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import processing.core.PApplet;

/** Only exact-help public main calls are allowed here; never run a non-help/native launch. */
public final class VideoGlitcherHelpTest {
    private static int checks;

    public static void main(String[] args) throws Exception {
        Field options = VideoGlitcher.class.getDeclaredField("launchOptions"); options.setAccessible(true);
        Object original = options.get(null);
        boolean nativeChooser = PApplet.useNativeSelect;
        Method parse = options.getType().getDeclaredMethod("parse", String[].class); parse.setAccessible(true);
        Object ignored = parse.invoke(null, (Object) new String[]{"--help"});
        check(!(boolean) option(ignored, "smokeTest") && !(boolean) option(ignored, "windowed"),
                "Help remains outside legacy launch parsing; it needs an earlier return");
        Method helper;
        try { helper = VideoGlitcher.class.getDeclaredMethod("printLaunchHelp", String[].class, PrintStream.class); }
        catch (NoSuchMethodException missing) { throw new AssertionError("Missing early help route; old parser ignores --help. Native main deliberately not invoked.", missing); }
        helper.setAccessible(true);
        String expected = null;
        for (String[] combination : new String[][]{
                {"--help"}, {"--help", "--help"}, {"--help", "--video=/owned/nonexistent.mp4", "--auto-export"},
                {"--windowed", "--help", "--video=/owned/clip with spaces ü.mp4"},
                {"--smoke-test", "--auto-process", "--video=/owned/nonexistent.mp4", "--help"},
                {"--auto-export", "--auto-process", "--smoke-frames=invalid", "--export-frames=-1", "--preset=Extreme", "--help"},
                {"--unknown", "--video=--help", "--help"} }) {
            String[] unchanged = combination.clone();
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (PrintStream out = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
                check((boolean) helper.invoke(null, combination, out), "Exact help anywhere must take precedence");
            }
            String text = bytes.toString(StandardCharsets.UTF_8);
            if (expected == null) expected = text;
            check(text.equals(expected), "Help prints once, independent of other arguments");
            check(Arrays.equals(combination, unchanged), "Help leaves input arguments unchanged");
            check(options.get(null) == original && PApplet.useNativeSelect == nativeChooser, "Helper must not mutate launch/chooser state");
            PrintStream previous = System.out;
            bytes.reset();
            try (PrintStream out = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
                System.setOut(out);
                // Every tested array contains an exact --help. The inspected first branch returns before launch.
                VideoGlitcher.main(combination);
            } finally { System.setOut(previous); }
            check(bytes.toString(StandardCharsets.UTF_8).equals(expected), "Public help route returns the same output");
            check(options.get(null) == original && PApplet.useNativeSelect == nativeChooser, "Public help must return before parsing or chooser mutation");
        }
        for (String[] noHelp : new String[][]{{}, {"--help=true"}, {"--helpful"}, {"-h"}, {"--HELP"}, {" --help"},
                {"--video=--help"}, {"--windowed", "--unknown"}, {"--smoke-test", "--help=no"}}) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (PrintStream out = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
                check(!(boolean) helper.invoke(null, noHelp, out) && bytes.size() == 0, "Lookalike/unknown flags must not become help");
            }
            Object parsed = parse.invoke(null, (Object) noHelp);
            check((boolean) option(parsed, "smokeTest") == Arrays.asList(noHelp).contains("--smoke-test"), "Smoke parsing unchanged");
            check((boolean) option(parsed, "windowed") == (Arrays.asList(noHelp).contains("--windowed") || Arrays.asList(noHelp).contains("--smoke-test")), "Presentation parsing unchanged");
        }
        check(expected != null && expected.startsWith("video_glitcher"), "Help must identify the application");
        for (String required : new String[]{"--help", "--windowed", "960x540", "--video=PATH", "--preset=NAME", "Cinematic",
                "--smoke-test", "--auto-export", "--auto-process", "--smoke-frames=N", "60", "--export-frames=N", "48",
                "Java 21", "desktop/AWT", "GStreamer", "ffmpeg", "libx264", "24 fps", "silent", "python3 scripts/diagnose_prerequisites.py",
                "py -3 scripts/diagnose_prerequisites.py", "Unknown options remain ignored", "does not validate"}) {
            check(expected.contains(required), "Help must describe current behaviour: " + required);
        }
        freshHelpProcess(expected);
        check(options.get(null) == original && PApplet.useNativeSelect == nativeChooser, "Repeated help preserves original launch state");
        System.out.println("Non-launching help passed: " + checks + " assertion increments; no native app/decoder/renderer acceptance.");
    }

    private static void freshHelpProcess(String expected) throws Exception {
        Path directory = Files.createTempDirectory("glitcher-help-check-");
        Path stdout = directory.resolve("help.txt"), trace = directory.resolve("initialisation.log");
        List<String> command = new ArrayList<>(List.of(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(), "-Djava.awt.headless=true",
                "-Dfile.encoding=UTF-8", "-Dgstreamer.library.path=/owned/missing-native-runtime",
                "-Xlog:class+init=info:stderr", "-cp", System.getProperty("java.class.path"), VideoGlitcher.class.getName(),
                "--windowed", "--video=/owned/not-read.mp4", "--auto-process", "--smoke-test", "--help"));
        Process child = null;
        try {
            child = new ProcessBuilder(command).redirectOutput(stdout.toFile()).redirectError(trace.toFile()).start();
            check(child.waitFor(15, TimeUnit.SECONDS), "Exact-help child must return promptly");
            check(child.exitValue() == 0, "Exact-help child must return success");
            check(Files.readString(stdout).equals(expected), "Fresh public help output remains exact");
            var initialising = Pattern.compile("Initializing '([^']+)'").matcher(Files.readString(trace));
            boolean sawApp = false, sawCore = false;
            List<String> forbidden = new ArrayList<>();
            while (initialising.find()) {
                String name = initialising.group(1);
                sawApp |= name.equals("tom/videoGlitcher/VideoGlitcher");
                sawCore |= name.equals("processing/core/PApplet");
                if (name.startsWith("processing/video/") || name.startsWith("processing/opengl/")
                        || name.startsWith("processing/awt/") || name.startsWith("controlP5/")
                        || name.startsWith("com/jogamp/") || name.startsWith("jogamp/")
                        || name.startsWith("org/freedesktop/gstreamer/") || name.startsWith("com/sun/jna/")
                        || name.equals("java/awt/Toolkit") || name.equals("java/awt/GraphicsEnvironment")
                        || name.startsWith("sun/awt/") || name.equals("java/lang/ProcessBuilder")
                        || name.equals("java/lang/ProcessImpl")) forbidden.add(name);
            }
            check(forbidden.isEmpty(), "Help must not initialise GUI/native subsystems: " + forbidden);
            check(sawApp && sawCore, "Initialisation trace must include real app/core metadata, not a substituted stub");
            // Trace size is diagnostic only; Java itself necessarily initialises standard classes.
            System.out.println("Help process returned without GUI/video/OpenGL/native bridge initialisers; app/core metadata initialised.");
        } finally {
            if (child != null && child.isAlive()) { child.destroyForcibly(); check(child.waitFor(10, TimeUnit.SECONDS), "Owned help child must reap"); }
            Files.deleteIfExists(stdout); Files.deleteIfExists(trace); Files.delete(directory);
        }
    }
    private static Object option(Object parsed, String name) throws Exception {
        Method method = parsed.getClass().getDeclaredMethod(name); method.setAccessible(true); return method.invoke(parsed);
    }
    private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
}
