package tom.videoGlitcher;

public final class VideoGlitcherLogicTest {

    private static final float EPSILON = 0.0001f;

    private VideoGlitcherLogicTest() {
    }

    public static void main(String[] args) {
        testMakeExportFilename();
        testComputeVideoFitDoesNotUpscale();
        testComputeVideoFitLetterboxesWideVideo();
        testNormalizeRangesClampsMaximums();
        testAdvanceGlitchStateWhenActive();
        testAdvanceGlitchStateTransitionsToCalm();
        testAdvanceGlitchStateStartsEpisodeOnTimeout();
        testAdvanceGlitchStateStartsEpisodeSpontaneously();
        testFrameSpecRoundsDownToEvenDimensions();
        testFfmpegCommandIncludesExpectedArguments();
        testPresetValues();
        testUnknownPresetReturnsNull();

        System.out.println("All VideoGlitcherLogic tests passed.");
    }

    private static void testMakeExportFilename() {
        assertEquals("clip_glitched.mp4", VideoGlitcherLogic.makeExportFilename("clip.mov"));
        assertEquals("archive.final_glitched.mp4", VideoGlitcherLogic.makeExportFilename("archive.final.mp4"));
        assertEquals("README_glitched.mp4", VideoGlitcherLogic.makeExportFilename("README"));
        assertEquals(".hidden_glitched.mp4", VideoGlitcherLogic.makeExportFilename(".hidden"));
    }

    private static void testComputeVideoFitDoesNotUpscale() {
        VideoGlitcherLogic.VideoFit fit = VideoGlitcherLogic.computeVideoFit(1920, 1080, 1280, 720);

        assertFloatEquals(320.0f, fit.drawX());
        assertFloatEquals(180.0f, fit.drawY());
        assertFloatEquals(1280.0f, fit.drawW());
        assertFloatEquals(720.0f, fit.drawH());
    }

    private static void testComputeVideoFitLetterboxesWideVideo() {
        VideoGlitcherLogic.VideoFit fit = VideoGlitcherLogic.computeVideoFit(800, 600, 1920, 1080);

        assertFloatEquals(0.0f, fit.drawX());
        assertFloatEquals(75.0f, fit.drawY());
        assertFloatEquals(800.0f, fit.drawW());
        assertFloatEquals(450.0f, fit.drawH());
    }

    private static void testNormalizeRangesClampsMaximums() {
        VideoGlitcherLogic.RangeValues ranges = VideoGlitcherLogic.normalizeRanges(8, 4, 10, 6);

        assertFloatEquals(8.0f, ranges.episodeMinFrames());
        assertFloatEquals(8.0f, ranges.episodeMaxFrames());
        assertFloatEquals(10.0f, ranges.calmMinFrames());
        assertFloatEquals(10.0f, ranges.calmMaxFrames());
    }

    private static void testAdvanceGlitchStateWhenActive() {
        VideoGlitcherLogic.GlitchState next = VideoGlitcherLogic.advanceGlitchState(
                new VideoGlitcherLogic.GlitchState(true, 3, 0),
                0.55f,
                0.9f,
                12,
                7);

        assertTrue(next.glitchActive(), "Glitch should remain active while frames remain");
        assertEquals(2, next.glitchFramesLeft());
        assertEquals(0, next.calmFramesLeft());
    }

    private static void testAdvanceGlitchStateTransitionsToCalm() {
        VideoGlitcherLogic.GlitchState next = VideoGlitcherLogic.advanceGlitchState(
                new VideoGlitcherLogic.GlitchState(true, 1, 0),
                0.55f,
                0.9f,
                14,
                7);

        assertFalse(next.glitchActive(), "Glitch should end when the last active frame is consumed");
        assertEquals(0, next.glitchFramesLeft());
        assertEquals(14, next.calmFramesLeft());
    }

    private static void testAdvanceGlitchStateStartsEpisodeOnTimeout() {
        VideoGlitcherLogic.GlitchState next = VideoGlitcherLogic.advanceGlitchState(
                new VideoGlitcherLogic.GlitchState(false, 0, 1),
                0.10f,
                0.9f,
                12,
                5);

        assertTrue(next.glitchActive(), "Glitch should start when calm frames run out");
        assertEquals(5, next.glitchFramesLeft());
        assertEquals(0, next.calmFramesLeft());
    }

    private static void testAdvanceGlitchStateStartsEpisodeSpontaneously() {
        VideoGlitcherLogic.GlitchState next = VideoGlitcherLogic.advanceGlitchState(
                new VideoGlitcherLogic.GlitchState(false, 0, 20),
                0.92f,
                0.01f,
                12,
                9);

        assertTrue(next.glitchActive(), "Glitch should start when the spontaneous roll falls below the threshold");
        assertEquals(9, next.glitchFramesLeft());
        assertEquals(0, next.calmFramesLeft());
    }

    private static void testFrameSpecRoundsDownToEvenDimensions() {
        FfmpegVideoExporter.FrameSpec frameSpec = FfmpegVideoExporter.frameSpecFor(1921, 1081);

        assertEquals(1921, frameSpec.sourceWidth());
        assertEquals(1081, frameSpec.sourceHeight());
        assertEquals(1920, frameSpec.exportWidth());
        assertEquals(1080, frameSpec.exportHeight());
    }

    private static void testFfmpegCommandIncludesExpectedArguments() {
        java.util.List<String> command = FfmpegVideoExporter.buildCommand("ffmpeg", 1280, 720, 24, "out.mp4");

        assertEquals("ffmpeg", command.get(0));
        assertEquals("1280x720", command.get(7));
        assertEquals("24", command.get(9));
        assertEquals("libx264", command.get(14));
        assertTrue(command.get(command.size() - 1).endsWith("out.mp4"), "Output filename should be preserved in ffmpeg command");
    }

    private static void testPresetValues() {
        VideoGlitcherLogic.PresetValues cinematic = VideoGlitcherLogic.presetForName("Cinematic");
        VideoGlitcherLogic.PresetValues extreme = VideoGlitcherLogic.presetForName("Extreme");

        assertNotNull(cinematic, "Cinematic preset should exist");
        assertNotNull(extreme, "Extreme preset should exist");
        assertFloatEquals(0.85f, cinematic.glitchIntensity());
        assertFloatEquals(0.55f, cinematic.glitchFrequency());
        assertFloatEquals(1.55f, extreme.glitchIntensity());
        assertFloatEquals(0.38f, extreme.burstChance());
    }

    private static void testUnknownPresetReturnsNull() {
        if (VideoGlitcherLogic.presetForName("Unknown") != null) {
            throw new AssertionError("Unknown presets should return null");
        }
    }

    private static void assertEquals(String expected, String actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError("Expected '" + expected + "' but got '" + actual + "'");
        }
    }

    private static void assertFloatEquals(float expected, float actual) {
        if (Math.abs(expected - actual) > EPSILON) {
            throw new AssertionError("Expected " + expected + " but got " + actual);
        }
    }

    private static void assertEquals(int expected, int actual) {
        if (expected != actual) {
            throw new AssertionError("Expected " + expected + " but got " + actual);
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void assertFalse(boolean condition, String message) {
        if (condition) {
            throw new AssertionError(message);
        }
    }

    private static void assertNotNull(Object value, String message) {
        if (value == null) {
            throw new AssertionError(message);
        }
    }
}