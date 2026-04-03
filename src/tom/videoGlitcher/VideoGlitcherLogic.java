package tom.videoGlitcher;

final class VideoGlitcherLogic {

    private VideoGlitcherLogic() {
    }

    static String makeExportFilename(String sourceName) {
        int dot = sourceName.lastIndexOf('.');
        String base = (dot > 0) ? sourceName.substring(0, dot) : sourceName;
        return base + "_glitched.mp4";
    }

    static VideoFit computeVideoFit(int viewportWidth, int viewportHeight, int videoWidth, int videoHeight) {
        if (viewportWidth <= 0 || viewportHeight <= 0 || videoWidth <= 0 || videoHeight <= 0) {
            return new VideoFit(0, 0, 0, 0);
        }

        float sx = (float) viewportWidth / (float) videoWidth;
        float sy = (float) viewportHeight / (float) videoHeight;
        float scaleFactor = Math.min(sx, sy);

        if (scaleFactor > 1.0f) {
            scaleFactor = 1.0f;
        }

        float drawW = videoWidth * scaleFactor;
        float drawH = videoHeight * scaleFactor;
        float drawX = (viewportWidth - drawW) * 0.5f;
        float drawY = (viewportHeight - drawH) * 0.5f;
        return new VideoFit(drawX, drawY, drawW, drawH);
    }

    static RangeValues normalizeRanges(float episodeMinFrames, float episodeMaxFrames, float calmMinFrames, float calmMaxFrames) {
        float normalizedEpisodeMax = Math.max(episodeMinFrames, episodeMaxFrames);
        float normalizedCalmMax = Math.max(calmMinFrames, calmMaxFrames);
        return new RangeValues(episodeMinFrames, normalizedEpisodeMax, calmMinFrames, normalizedCalmMax);
    }

    static GlitchState advanceGlitchState(
            GlitchState state,
            float glitchFrequency,
            float spontaneousRoll,
            int nextCalmFrames,
            int nextGlitchFrames) {
        if (state.glitchActive()) {
            int remainingGlitchFrames = state.glitchFramesLeft() - 1;
            if (remainingGlitchFrames <= 0) {
                return new GlitchState(false, 0, nextCalmFrames);
            }
            return new GlitchState(true, remainingGlitchFrames, state.calmFramesLeft());
        }

        int remainingCalmFrames = state.calmFramesLeft() - 1;
        float spontaneousChance = 0.01f + glitchFrequency * 0.08f;
        if (remainingCalmFrames <= 0 || spontaneousRoll < spontaneousChance) {
            return new GlitchState(true, nextGlitchFrames, 0);
        }

        return new GlitchState(false, 0, remainingCalmFrames);
    }

    static PresetValues presetForName(String name) {
        switch (name) {
            case "Subtle":
                return new PresetValues(0.45f, 0.22f, 2, 6, 18, 55, 0.18f, 0.04f);
            case "Cinematic":
                return new PresetValues(0.85f, 0.55f, 4, 14, 10, 40, 0.25f, 0.16f);
            case "Corrupted File":
                return new PresetValues(1.05f, 0.72f, 5, 18, 6, 22, 0.32f, 0.24f);
            case "Broken Codec":
                return new PresetValues(1.20f, 0.82f, 6, 20, 4, 16, 0.36f, 0.28f);
            case "Extreme":
                return new PresetValues(1.55f, 0.92f, 8, 26, 2, 10, 0.42f, 0.38f);
            default:
                return null;
        }
    }

    static record VideoFit(float drawX, float drawY, float drawW, float drawH) {
    }

    static record RangeValues(float episodeMinFrames, float episodeMaxFrames, float calmMinFrames, float calmMaxFrames) {
    }

    static record PresetValues(
            float glitchIntensity,
            float glitchFrequency,
            float episodeMinFrames,
            float episodeMaxFrames,
            float calmMinFrames,
            float calmMaxFrames,
            float subtleDamageChance,
            float burstChance) {
    }

    static record GlitchState(boolean glitchActive, int glitchFramesLeft, int calmFramesLeft) {
    }
}