package tom.videoGlitcher;

import java.util.Arrays;
import processing.core.PApplet;
import processing.core.PImage;
import processing.opengl.PGraphicsOpenGL;

/** Explicit desktop/OpenGL check; never counted as a headless test. */
public final class VideoGlitcherGpuSnapshotTest extends PApplet {
    private PImage source;
    private int[] expected;
    public static void main(String[] args) { PApplet.main(VideoGlitcherGpuSnapshotTest.class.getName()); }
    @Override public void settings() { size(320, 240, P2D); pixelDensity(1); }
    @Override public void setup() {
        source = createImage(16, 12, ARGB);
        for (int y = 0; y < source.height; y++) {
            for (int x = 0; x < source.width; x++) {
                source.pixels[y * source.width + x] = y < 6 ? (x < 8 ? 0xffff0000 : 0xff00ff00)
                        : (x < 8 ? 0xff0000ff : 0xffffffff);
            }
        }
        source.updatePixels();
        expected = source.pixels.clone();
        noSmooth();
    }
    @Override public void draw() {
        image(source, 0, 0, width, height);
        if (frameCount < 3) return;
        try {
            // Model the observed Movie path: GPU is correct, while CPU/native snapshot storage is stale.
            Arrays.fill(source.pixels, 0xff000000);
            PImage first = VideoGlitcher.snapshotTexture((PGraphicsOpenGL) g, source);
            if (first.width != 16 || first.height != 12 || !Arrays.equals(first.pixels, expected))
                throw new AssertionError("GPU snapshot lost pixels, dimensions, colour or orientation");
            Arrays.fill(first.pixels, 0xff000000);
            PImage second = VideoGlitcher.snapshotTexture((PGraphicsOpenGL) g, source);
            if (!Arrays.equals(second.pixels, expected)) throw new AssertionError("Repeated snapshot must be an independent copy");
            System.out.println("GPU snapshot regression passed: stale CPU data, all four colours, orientation and repeated capture.");
            dispose(); System.exit(0);
        } catch (Throwable failure) {
            failure.printStackTrace(); dispose(); System.exit(1);
        }
    }
}
