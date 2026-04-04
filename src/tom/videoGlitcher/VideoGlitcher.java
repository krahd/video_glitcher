package tom.videoGlitcher;

import processing.core.*;
import processing.data.IntList;
import processing.video.*;
import controlP5.*;


import java.io.File;
import java.io.IOException;

public class VideoGlitcher extends PApplet {

    private static LaunchOptions launchOptions = LaunchOptions.defaults();
    private static final float PLAYBACK_END_EPSILON_SECONDS = 0.05f;
    private static final String PLAY_LABEL = "PLAY";
    private static final String PAUSE_LABEL = "PAUSE";
    private static final String REWIND_TO_START_LABEL = "<<";
    private static final String LOOPING_LABEL = "LOOP";
    private static final String PLAY_ONCE_LABEL = "ONCE";
    private static final String PROCESS_FULL_LABEL = "Process Full";
    private static final String PROCESSING_LABEL = "Processing...";

    public static void main(String[] args) {
        launchOptions = LaunchOptions.parse(args);
        if (launchOptions.smokeTest()) {
            PApplet.main(new String[] { VideoGlitcher.class.getName() });
            return;
        }

        PApplet.main(new String[] { "--present", VideoGlitcher.class.getName() });
    }

    private Movie video;
    private ControlP5 cp5;
    private FfmpegVideoExporter videoExporter;
    private File currentVideoFile;

    private boolean movieReady = false;
    private boolean exporting = false;
    private boolean paused = false;
    private boolean loopPlayback = true;
    private boolean glitchEnabled = false;
    private boolean hasLoadedFirstVideo = false;
    private String exportFilename = "glitched_export.mp4";
    private String currentVideoPath = "";
    private String currentVideoName = "No video loaded";

    private final int FPS = 24;
    private float drawX = 0;
    private float drawY = 0;
    private float drawW = 0;
    private float drawH = 0;

    private boolean showHUD = true;
    private boolean showGUI = true;
    private boolean freezeManual = false;
    private boolean selectingVideo = false;
    private ExportMode exportMode = ExportMode.NONE;
    private boolean exportReachedPlaybackEnd = false;

    private float glitchIntensity = 0.85f;
    private float glitchFrequency = 0.55f;
    private float episodeMinFrames = 4;
    private float episodeMaxFrames = 14;
    private float calmMinFrames = 10;
    private float calmMaxFrames = 40;

    private float subtleDamageChance = 0.25f;
    private float burstChance = 0.16f;

    private boolean useRGBSplit = true;
    private boolean useSlices = true;
    private boolean useBlocks = true;
    private boolean useBars = true;
    private boolean useDropouts = true;
    private boolean useGhosts = true;
    private boolean useFreeze = true;
    private boolean useScanBursts = true;
    private boolean useFlash = true;
    private boolean useMicroJitter = true;
    private boolean useZoomWobble = true;

    private boolean glitchActive = false;
    private int glitchFramesLeft = 0;
    private int calmFramesLeft = 0;

    private PImage frozenFrame = null;
    private PImage pausedFrame = null;
    private int freezeFramesLeft = 0;
    private PImage previousFrame = null;

    private float zoomJitter = 1.0f;
    private float offsetX = 0;
    private float offsetY = 0;
    private RenderSettings liveRenderSettings = captureCurrentRenderSettings();
    private RenderSettings lockedRenderSettings = null;

    private Button pausePlayButton;
    private Button playbackModeButton;
    private Button glitchToggleButton;
    private Button interactiveExportButton;
    private Button stopExportButton;
    private Button processVideoButton;
    private DropdownList presetList;
    private Textlabel statusLabel;

    private boolean triedVideoUriFallback = false;
    private boolean smokeExportStarted = false;
    private int smokeExportFramesSaved = 0;

    private int panelX = 12;
    private int panelY = 12;
    private int panelW = 420;
    private int panelH = 904;

    private int guiX = panelX + 14;
    private int guiY = panelY + 14;
    private int sliderW = 180;
    private int sliderH = 16;
    private int labelX = guiX + sliderW + 72;
    private int rowGap = 24;

    @Override
    public void settings() {
        pixelDensity(1);
        noSmooth();
        if (launchOptions.smokeTest()) {
            size(960, 540, P2D);
        } else {
            fullScreen(P2D);
        }
    }

    @Override
    public void setup() {
        frameRate(FPS);
        surface.setTitle(launchOptions.smokeTest() ? "video_glitcher Smoke Test" : "video_glitcher");

        setupGui();
        applyPreset(launchOptions.presetName());
        calmFramesLeft = (int) random(calmMinFrames, calmMaxFrames + 1);

        if (launchOptions.smokeTest()) {
            showGUI = false;
            showHUD = false;
        }

        if (!launchOptions.videoPath().isEmpty()) {
            loadVideoFile(new File(launchOptions.videoPath()));
        }
    }

    @Override
    public void draw() {
        background(0);

        if (video != null && !paused && video.available()) {
            updateMovieFrame(video);
        }

        normalizeRanges();
        liveRenderSettings = captureCurrentRenderSettings();
        updatePlaybackEffects();
        boolean activeGlitchEnabled = activeRenderSettings().glitchEnabled();

        pushMatrix();
        translate(offsetX, offsetY);
        scale(zoomJitter);

        if (!movieReady || video == null) {
            drawNoVideoScreen();
        } else {
            if (paused && pausedFrame != null) {
                image(pausedFrame, drawX, drawY, drawW, drawH);
            } else if ((freezeFramesLeft > 0 || freezeManual) && frozenFrame != null) {
                image(frozenFrame, drawX, drawY, drawW, drawH);
                if (!freezeManual && freezeFramesLeft > 0) {
                    freezeFramesLeft--;
                }
            } else {
                image(video, drawX, drawY, drawW, drawH);
            }

            if (activeGlitchEnabled && !paused && glitchActive) {
                applyRandomGlitchStack();
            } else if (activeGlitchEnabled && !paused) {
                applySubtleFilmDamage();
            }
        }

        popMatrix();

        if (movieReady && video != null) {
            drawVideoMask();
        }

        previousFrame = get();

        if (shouldWriteExportFrame()) {
            saveExportFrame();
        }

        if (showHUD)
            drawHUD();
        if (showGUI)
            drawGui();

        if (launchOptions.smokeTest()) {
            runSmokeCycle();
        }

        updatePlaybackCompletion();
    }

    @Override
    public void keyPressed() {
        if (isFullProcessExportActive()) {
            if (key == 'e' || key == 'E') {
                stopExport();
            } else if (key == 'h' || key == 'H') {
                showHUD = !showHUD;
            } else if (key == 'u' || key == 'U') {
                showGUI = !showGUI;
            }
            return;
        }

        if (keyCode == UP) {
            glitchIntensity = min(2.0f, glitchIntensity + 0.1f);
            syncGuiValues();
        } else if (keyCode == DOWN) {
            glitchIntensity = max(0.1f, glitchIntensity - 0.1f);
            syncGuiValues();
        } else if (key == ' ') {
            togglePausePlay();
        } else if (key == 'l' || key == 'L') {
            promptForVideo();
        } else if (key == 'f' || key == 'F') {
            freezeManual = !freezeManual;
            if (freezeManual && frozenFrame == null && movieReady) {
                frozenFrame = get((int) drawX, (int) drawY, max(1, (int) drawW), max(1, (int) drawH));
            }
        } else if (key == 'h' || key == 'H') {
            showHUD = !showHUD;
        } else if (key == 'u' || key == 'U') {
            showGUI = !showGUI;
        } else if (key == 'g' || key == 'G') {
            toggleGlitching();
        } else if (key == 'e' || key == 'E') {
            if (exporting)
                stopExport();
            else
                startExport();
        } else if (key == 'p' || key == 'P') {
            processVideo();
        } else if (key == 's' || key == 'S') {
            saveFrame("glitch-######.png");
        }
    }

    @Override
    public void mousePressed() {
        if (video == null && !movieReady && !selectingVideo && !isPointerOverGui()) {
            promptForVideo();
        }
    }

    public void movieEvent(Movie m) {
        if (!paused) {
            updateMovieFrame(m);
        }
    }

    private void updateMovieFrame(Movie movie) {
        movie.read();

        if (!movieReady && movie.width > 0 && movie.height > 0) {
            movieReady = true;
            computeVideoFit();
            statusLabel.setText("Status: previewing " + currentVideoName);

            if (!hasLoadedFirstVideo) {
                hasLoadedFirstVideo = true;
                glitchEnabled = true;
                updateGlitchButton();
            }

            if (paused) {
                pausedFrame = movie.get();
                movie.pause();
                statusLabel.setText("Status: paused " + currentVideoName);
                updatePausePlayButton();
            }
        }
    }

    private void updatePlaybackEffects() {
        if (!movieReady || video == null || paused || !activeRenderSettings().glitchEnabled()) {
            glitchActive = false;
            zoomJitter = 1.0f;
            offsetX = 0;
            offsetY = 0;
            return;
        }

        updateGlitchState();
        updateCameraJitter();
    }

    private void setupGui() {
        cp5 = new ControlP5(this);
        cp5.setAutoDraw(false);

        int x = guiX;
        int y = guiY;

        cp5.addTextlabel("guiTitle")
                .setPosition(x, y)
                .setText("GLITCH CONTROLS");
        y += 24;

        presetList = cp5.addDropdownList("preset")
                .setPosition(x, y)
                .setSize(240, 160)
                .setBarHeight(24)
                .setItemHeight(22);

        presetList.addItem("Subtle", 0);
        presetList.addItem("Cinematic", 1);
        presetList.addItem("Corrupted File", 2);
        presetList.addItem("Broken Codec", 3);
        presetList.addItem("Extreme", 4);

        y += 196;

        addSliderRow("glitchIntensity", x, y, sliderW, sliderH, 0.1f, 2.0f, glitchIntensity, "Intensity");
        y += rowGap;
        addSliderRow("glitchFrequency", x, y, sliderW, sliderH, 0.0f, 1.0f, glitchFrequency, "Frequency");
        y += rowGap;
        addSliderRow("episodeMinFrames", x, y, sliderW, sliderH, 1, 30, episodeMinFrames, "Episode Min");
        y += rowGap;
        addSliderRow("episodeMaxFrames", x, y, sliderW, sliderH, 1, 40, episodeMaxFrames, "Episode Max");
        y += rowGap;
        addSliderRow("calmMinFrames", x, y, sliderW, sliderH, 1, 80, calmMinFrames, "Calm Min");
        y += rowGap;
        addSliderRow("calmMaxFrames", x, y, sliderW, sliderH, 1, 120, calmMaxFrames, "Calm Max");
        y += rowGap;
        addSliderRow("subtleDamageChance", x, y, sliderW, sliderH, 0.0f, 1.0f, subtleDamageChance, "Subtle Damage");
        y += rowGap;
        addSliderRow("burstChance", x, y, sliderW, sliderH, 0.0f, 1.0f, burstChance, "Burst Chance");
        y += 30;

        addToggleRow("useRGBSplit", "RGB Split", x, y);
        y += 22;
        addToggleRow("useSlices", "Slices", x, y);
        y += 22;
        addToggleRow("useBlocks", "Blocks", x, y);
        y += 22;
        addToggleRow("useBars", "Bars", x, y);
        y += 22;
        addToggleRow("useDropouts", "Dropouts", x, y);
        y += 22;
        addToggleRow("useGhosts", "Ghosts", x, y);
        y += 22;
        addToggleRow("useFreeze", "Freeze", x, y);
        y += 22;
        addToggleRow("useScanBursts", "Scan Bursts", x, y);
        y += 22;
        addToggleRow("useFlash", "Flash", x, y);
        y += 22;
        addToggleRow("useMicroJitter", "Micro Jitter", x, y);
        y += 22;
        addToggleRow("useZoomWobble", "Zoom Wobble", x, y);
        y += 30;

        Button bLoad = cp5.addButton("loadVideo")
                .setPosition(x, y)
                .setSize(72, 30)
                .setLabel("Load Video");
        bLoad.getCaptionLabel().align(ControlP5.CENTER, ControlP5.CENTER);

        Button rewindButton = cp5.addButton("rewindToStart")
                .setPosition(x + 80, y)
                .setSize(44, 30)
                .setLabel(REWIND_TO_START_LABEL);
        rewindButton.getCaptionLabel().align(ControlP5.CENTER, ControlP5.CENTER);

        pausePlayButton = cp5.addButton("pausePlay")
                .setPosition(x + 132, y)
                .setSize(72, 30)
                .setLabel(PLAY_LABEL);
        pausePlayButton.getCaptionLabel().align(ControlP5.CENTER, ControlP5.CENTER);

        playbackModeButton = cp5.addButton("togglePlaybackMode")
                .setPosition(x + 212, y)
                .setSize(56, 30)
                .setLabel(LOOPING_LABEL);
        playbackModeButton.getCaptionLabel().align(ControlP5.CENTER, ControlP5.CENTER);

        y += 38;

        glitchToggleButton = cp5.addButton("toggleGlitching")
                .setPosition(x, y)
                .setSize(88, 30)
                .setLabel("Glitch");
        glitchToggleButton.getCaptionLabel().align(ControlP5.CENTER, ControlP5.CENTER);

        interactiveExportButton = cp5.addButton("startExport")
                .setPosition(x + 96, y)
                .setSize(96, 30)
                .setLabel("Export Start");
        interactiveExportButton.getCaptionLabel().align(ControlP5.CENTER, ControlP5.CENTER);

        stopExportButton = cp5.addButton("stopExport")
                .setPosition(x + 200, y)
                .setSize(96, 30)
                .setLabel("Export Stop");
        stopExportButton.getCaptionLabel().align(ControlP5.CENTER, ControlP5.CENTER);

        y += 38;

        processVideoButton = cp5.addButton("processVideo")
                .setPosition(x, y)
                .setSize(296, 30)
                .setLabel(PROCESS_FULL_LABEL);
        processVideoButton.getCaptionLabel().align(ControlP5.CENTER, ControlP5.CENTER);

        y += 42;

        statusLabel = cp5.addTextlabel("status")
                .setPosition(x, y)
                .setText("Status: no video loaded");

        updatePausePlayButton();
        updatePlaybackModeButton();
        updateGlitchButton();
        updateExportButtons();
    }

    private void addSliderRow(String name, int x, int y, int w, int h, float minV, float maxV, float value,
            String label) {
        Slider s = cp5.addSlider(name)
                .setPosition(x, y)
                .setSize(w, h)
                .setRange(minV, maxV)
                .setValue(value);

        s.getCaptionLabel().setVisible(false);
        s.getValueLabel().align(ControlP5.RIGHT_OUTSIDE, ControlP5.CENTER).setPaddingX(6);

        cp5.addTextlabel("lbl_" + name)
                .setPosition(labelX, y + 1)
                .setText(label);
    }

    private void addToggleRow(String name, String label, int x, int y) {
        Toggle t = cp5.addToggle(name)
                .setPosition(x, y)
                .setSize(18, 18)
                .setValue(getToggleValue(name));

        t.getCaptionLabel().setVisible(false);

        cp5.addTextlabel("lbl_" + name)
                .setPosition(x + 28, y + 1)
                .setText(label);
    }

    private boolean getToggleValue(String name) {
        if (name.equals("useRGBSplit"))
            return useRGBSplit;
        if (name.equals("useSlices"))
            return useSlices;
        if (name.equals("useBlocks"))
            return useBlocks;
        if (name.equals("useBars"))
            return useBars;
        if (name.equals("useDropouts"))
            return useDropouts;
        if (name.equals("useGhosts"))
            return useGhosts;
        if (name.equals("useFreeze"))
            return useFreeze;
        if (name.equals("useScanBursts"))
            return useScanBursts;
        if (name.equals("useFlash"))
            return useFlash;
        if (name.equals("useMicroJitter"))
            return useMicroJitter;
        if (name.equals("useZoomWobble"))
            return useZoomWobble;
        return false;
    }

    public void controlEvent(ControlEvent e) {
        if (isFullProcessExportActive()) {
            return;
        }

        if (e.isFrom(presetList)) {
            int v = (int) e.getValue();
            if (v == 0)
                applyPreset("Subtle");
            else if (v == 1)
                applyPreset("Cinematic");
            else if (v == 2)
                applyPreset("Corrupted File");
            else if (v == 3)
                applyPreset("Broken Codec");
            else if (v == 4)
                applyPreset("Extreme");
        }
    }

    public void loadVideo() {
        if (isFullProcessExportActive()) {
            return;
        }
        promptForVideo();
    }

    public void pausePlay() {
        if (isFullProcessExportActive()) {
            return;
        }
        togglePausePlay();
    }

    public void rewindToStart() {
        if (isFullProcessExportActive()) {
            return;
        }

        if (video == null) {
            return;
        }

        video.jump(0);
        if (paused) {
            pausedFrame = video.get();
            video.pause();
            statusLabel.setText("Status: rewound " + currentVideoName);
        } else {
            startPlayback();
            statusLabel.setText("Status: previewing " + currentVideoName);
        }

        updatePausePlayButton();
    }

    public void togglePlaybackMode() {
        if (isFullProcessExportActive()) {
            return;
        }

        loopPlayback = !loopPlayback;

        if (video != null && !paused) {
            if (loopPlayback) {
                video.loop();
            } else {
                video.noLoop();
            }
        }

        statusLabel.setText("Status: playback mode " + (loopPlayback ? "looping" : "play once"));
        updatePlaybackModeButton();
    }

    public void toggleGlitching() {
        if (isFullProcessExportActive()) {
            return;
        }

        glitchEnabled = !glitchEnabled;
        statusLabel.setText("Status: glitching " + (glitchEnabled ? "enabled" : "disabled"));
        updateGlitchButton();
    }

    public void processVideo() {
        startFullProcessExport();
    }

    private void promptForVideo() {
        if (selectingVideo || isFullProcessExportActive())
            return;
        selectingVideo = true;
        selectInput("Select a video file:", "videoSelected");
    }

    public void videoSelected(File selection) {
        selectingVideo = false;

        if (selection == null) {
            statusLabel.setText("Status: no video selected");
            return;
        }

        loadVideoFile(selection);
    }

    private void loadVideoFile(File file) {
        stopExport();
        releaseVideo();
        
        movieReady = false;
        frozenFrame = null;
        pausedFrame = null;
        previousFrame = null;
        freezeFramesLeft = 0;
        freezeManual = false;
        triedVideoUriFallback = false;
        
        currentVideoFile = file;
        currentVideoPath = file.getAbsolutePath();
        currentVideoName = file.getName();
        exportFilename = makeExportFilename(currentVideoName);
        
        startMovie(currentVideoPath, "path");
        updatePausePlayButton();
    }
    
    private void releaseVideo() {
        if (video != null) {
            try {
                video.stop();
            } catch (Exception ignored) {
            }
            video = null;
        }
    }

    private void startMovie(String source, String sourceLabel) {
        releaseVideo();
        movieReady = false;
        
        println("Loading video " + sourceLabel + ": " + source);
        try {
            video = new Movie(this, source);
            startPlayback();
            statusLabel.setText("Status: loading " + currentVideoName + " via " + sourceLabel);
        } catch (RuntimeException exception) {
            video = null;
            println("Failed to load video via " + sourceLabel + ": " + exception.getMessage());
            if (!triedVideoUriFallback && currentVideoFile != null && "path".equals(sourceLabel)) {
                triedVideoUriFallback = true;
                startMovie(currentVideoFile.toURI().toString(), "file URI");
            } else {
                statusLabel.setText("Status: failed to load " + currentVideoName);
            }
        }
    }

    private String makeExportFilename(String sourceName) {
        return VideoGlitcherLogic.makeExportFilename(sourceName);
    }

    private void computeVideoFit() {
        if (video == null || video.width <= 0 || video.height <= 0)
            return;

        VideoGlitcherLogic.VideoFit fit = VideoGlitcherLogic.computeVideoFit(width, height, video.width, video.height);
        drawX = fit.drawX();
        drawY = fit.drawY();
        drawW = fit.drawW();
        drawH = fit.drawH();
    }

    private void drawNoVideoScreen() {
        fill(255);
        textAlign(CENTER, CENTER);
        textSize(28);
        text("No video loaded", width * 0.5f, height * 0.5f - 20);

        textSize(18);
        text("Click anywhere or press L to load a video", width * 0.5f, height * 0.5f + 20);

        if (selectingVideo) {
            text("File dialog open...", width * 0.5f, height * 0.5f + 50);
        }
    }

    private void drawVideoMask() {
        noStroke();
        fill(0);

        if (drawY > 0) {
            rect(0, 0, width, drawY);
        }
        if (drawY + drawH < height) {
            rect(0, drawY + drawH, width, height - (drawY + drawH));
        }
        if (drawX > 0) {
            rect(0, drawY, drawX, drawH);
        }
        if (drawX + drawW < width) {
            rect(drawX + drawW, drawY, width - (drawX + drawW), drawH);
        }
    }

    private void drawGuiPanel() {
        noStroke();
        fill(0, 175);
        rect(panelX, panelY, panelW, panelH, 14);

        stroke(255, 35);
        noFill();
        rect(panelX, panelY, panelW, panelH, 14);
    }

    private void drawGui() {
        hint(DISABLE_DEPTH_TEST);
        drawGuiPanel();
        cp5.draw();
        hint(ENABLE_DEPTH_TEST);
    }

    private boolean isPointerOverGui() {
        if (!showGUI) {
            return false;
        }

        return mouseX >= panelX && mouseX <= panelX + panelW
                && mouseY >= panelY && mouseY <= panelY + panelH;
    }

    private void drawHUD() {
        fill(255, 220);
        noStroke();
        rect(12, height - 36, width - 24, 24, 8);

        fill(0);
        textSize(14);
        textAlign(LEFT, BASELINE);

        String mode = glitchActive ? "GLITCH" : "CALM";
        String exp = exportMode == ExportMode.INTERACTIVE ? "INTERACTIVE EXPORT"
                : exportMode == ExportMode.FULL_PROCESS ? "FULL PROCESS"
                : "PREVIEW";
        String gui = showGUI ? "GUI ON" : "GUI OFF";
        String playback = paused ? "PAUSED" : "PLAYING";
        String playbackMode = loopPlayback ? "LOOP" : "ONCE";

        text(
                "Video: " + currentVideoName +
                        "   Mode: " + mode +
                        "   Playback: " + playback +
                        "   Repeat: " + playbackMode +
                        "   Output: " + exp +
                        "   " + gui +
                        "   SPACE play/pause   G glitch   L load   F freeze   H hud   U gui   E export   P process",
                20, height - 18);
    }

    private void normalizeRanges() {
        VideoGlitcherLogic.RangeValues ranges = VideoGlitcherLogic.normalizeRanges(
                episodeMinFrames,
                episodeMaxFrames,
                calmMinFrames,
                calmMaxFrames);
        episodeMinFrames = ranges.episodeMinFrames();
        episodeMaxFrames = ranges.episodeMaxFrames();
        calmMinFrames = ranges.calmMinFrames();
        calmMaxFrames = ranges.calmMaxFrames();
    }

    private void updateGlitchState() {
        RenderSettings settings = activeRenderSettings();
        VideoGlitcherLogic.GlitchState next = VideoGlitcherLogic.advanceGlitchState(
                new VideoGlitcherLogic.GlitchState(glitchActive, glitchFramesLeft, calmFramesLeft),
                settings.glitchFrequency(),
                random(1),
                (int) random(settings.calmMinFrames(), settings.calmMaxFrames() + 1),
                (int) random(settings.episodeMinFrames(), settings.episodeMaxFrames() + 1));
        glitchActive = next.glitchActive();
        glitchFramesLeft = next.glitchFramesLeft();
        calmFramesLeft = next.calmFramesLeft();
    }

    private void updateCameraJitter() {
        RenderSettings settings = activeRenderSettings();
        if (glitchActive && settings.useZoomWobble() && random(1) < 0.35f) {
            zoomJitter = random(0.985f, 1.02f);
            offsetX = random(-8, 8) * settings.glitchIntensity();
            offsetY = random(-5, 5) * settings.glitchIntensity();
        } else {
            zoomJitter = 1.0f;
            offsetX = 0;
            offsetY = 0;
        }
    }

    private void applyRandomGlitchStack() {
        RenderSettings settings = activeRenderSettings();
        int layers = (int) random(2, 6);

        for (int i = 0; i < layers; i++) {
            int effect = pickEnabledEffect();
            if (effect == -1)
                break;

            if (effect == 0)
                heavyRGBSplit();
            else if (effect == 1)
                horizontalSlices();
            else if (effect == 2)
                blockGlitch();
            else if (effect == 3)
                digitalBars();
            else if (effect == 4)
                dropoutBands();
            else if (effect == 5)
                ghostFrame();
            else if (effect == 6)
                microJitterCopies();
            else if (effect == 7)
                scanlineBurst();
            else if (effect == 8)
                whiteFlashOrBlackout();
            else if (effect == 9)
                freezeStutter();
        }

        if (random(1) < 0.80f)
            scanlines();
        if (random(1) < 0.60f)
            flickerNoise();
        if (random(1) < settings.burstChance())
            burstGlitch();
    }

    private int pickEnabledEffect() {
        RenderSettings settings = activeRenderSettings();
        IntList choices = new IntList();

        if (settings.useRGBSplit())
            choices.append(0);
        if (settings.useSlices())
            choices.append(1);
        if (settings.useBlocks())
            choices.append(2);
        if (settings.useBars())
            choices.append(3);
        if (settings.useDropouts())
            choices.append(4);
        if (settings.useGhosts())
            choices.append(5);
        if (settings.useMicroJitter())
            choices.append(6);
        if (settings.useScanBursts())
            choices.append(7);
        if (settings.useFlash())
            choices.append(8);
        if (settings.useFreeze())
            choices.append(9);

        if (choices.size() == 0)
            return -1;
        return choices.get((int) random(choices.size()));
    }

    private void applySubtleFilmDamage() {
        RenderSettings settings = activeRenderSettings();
        if (random(1) < settings.subtleDamageChance() && settings.useRGBSplit())
            subtleRGBMisalign();
        if (random(1) < settings.subtleDamageChance() * 0.7f && settings.useDropouts())
            faintBanding();

        if (random(1) < settings.subtleDamageChance()) {
            stroke(0, 25);
            for (int y = (int) drawY; y < drawY + drawH; y += 3) {
                line(drawX, y, drawX + drawW, y);
            }
        }
    }

    private void heavyRGBSplit() {
        blendMode(ADD);

        float amt = map(activeRenderSettings().glitchIntensity(), 0, 2.0f, 0, 26);

        tint(255, 0, 0, random(90, 170));
        image(video, drawX + random(-amt, amt), drawY + random(-amt * 0.4f, amt * 0.4f), drawW, drawH);

        tint(0, 255, 80, random(40, 110));
        image(video, drawX + random(-amt * 0.5f, amt * 0.5f), drawY + random(-amt * 0.2f, amt * 0.2f), drawW, drawH);

        tint(0, 120, 255, random(90, 170));
        image(video, drawX + random(-amt, amt), drawY + random(-amt * 0.4f, amt * 0.4f), drawW, drawH);

        noTint();
        blendMode(BLEND);
    }

    private void subtleRGBMisalign() {
        blendMode(ADD);

        tint(255, 0, 0, 28);
        image(video, drawX + random(-4, 4), drawY, drawW, drawH);

        tint(0, 180, 255, 28);
        image(video, drawX + random(-4, 4), drawY, drawW, drawH);

        noTint();
        blendMode(BLEND);
    }

    private void horizontalSlices() {
        float activeGlitchIntensity = activeRenderSettings().glitchIntensity();
        int slices = (int) random(5, 16) * max(1, (int) activeGlitchIntensity);

        int x0 = (int) drawX;
        int y0 = (int) drawY;
        int w0 = (int) drawW;
        int h0 = (int) drawH;

        for (int i = 0; i < max(1, slices); i++) {
            int y = y0 + (int) random(h0);
            int h = (int) random(2, 26);
            h = min(h, y0 + h0 - y);

            int dx = (int) (random(-180, 180) * activeGlitchIntensity);
            copy(x0, y, w0, h, x0 + dx, y, w0, h);

            if (random(1) < 0.45f) {
                stroke(255, random(40, 120));
                line(x0, y, x0 + w0, y);
            }
        }
    }

    private void blockGlitch() {
        float activeGlitchIntensity = activeRenderSettings().glitchIntensity();
        int blocks = (int) random(8, 26) * max(1, (int) activeGlitchIntensity);

        int x0 = (int) drawX;
        int y0 = (int) drawY;
        int w0 = (int) drawW;
        int h0 = (int) drawH;

        for (int i = 0; i < max(1, blocks); i++) {
            int bw = (int) random(18, max(19, min(170, w0)));
            int bh = (int) random(8, max(9, min(100, h0)));

            int sx = x0 + (int) random(max(1, w0 - bw));
            int sy = y0 + (int) random(max(1, h0 - bh));
            int dx = constrain(sx + (int) random(-130, 130), x0, x0 + w0 - bw);
            int dy = constrain(sy + (int) random(-45, 45), y0, y0 + h0 - bh);

            copy(sx, sy, bw, bh, dx, dy, bw, bh);

            if (random(1) < 0.25f) {
                noStroke();
                fill(random(255), random(255), random(255), random(12, 65));
                rect(dx, dy, bw, bh);
            }
        }
    }

    private void digitalBars() {
        blendMode(ADD);
        noStroke();

        int bars = (int) random(3, 12) * max(1, (int) activeRenderSettings().glitchIntensity());

        for (int i = 0; i < max(1, bars); i++) {
            float y = drawY + random(drawH);
            float h = random(2, 18);

            fill(255, 0, 0, random(20, 80));
            rect(drawX + random(-40, 40), y, drawW, h);

            fill(0, 255, 255, random(20, 80));
            rect(drawX + random(-40, 40), y + random(-3, 3), drawW, h);

            if (random(1) < 0.4f) {
                fill(0, 255, 0, random(15, 60));
                rect(drawX + random(-25, 25), y, drawW, random(1, 8));
            }
        }

        blendMode(BLEND);
    }

    private void dropoutBands() {
        int bands = (int) random(1, 5);

        for (int i = 0; i < bands; i++) {
            float y = drawY + random(drawH);
            float h = random(10, 70);

            noStroke();
            if (random(1) < 0.5f)
                fill(0, random(40, 130));
            else
                fill(255, random(25, 80));
            rect(drawX, y, drawW, h);
        }
    }

    private void faintBanding() {
        noStroke();
        for (int i = 0; i < 2; i++) {
            fill(255, random(5, 12));
            rect(drawX, drawY + random(drawH), drawW, random(8, 24));
        }
    }

    private void ghostFrame() {
        if (previousFrame == null)
            return;

        blendMode(ADD);
        tint(255, random(20, 60));
        image(previousFrame, random(-8, 8), random(-4, 4), width, height);
        noTint();
        blendMode(BLEND);
    }

    private void microJitterCopies() {
        int n = (int) random(2, 5);

        blendMode(ADD);
        tint(255, random(12, 40));

        for (int i = 0; i < n; i++) {
            image(video, drawX + random(-10, 10), drawY + random(-4, 4), drawW, drawH);
        }

        noTint();
        blendMode(BLEND);
    }

    private void scanlines() {
        int x0 = (int) drawX;
        int y0 = (int) drawY;
        int w0 = (int) drawW;
        int h0 = (int) drawH;

        stroke(0, 45);
        for (int y = y0; y < y0 + h0; y += 2) {
            line(x0, y, x0 + w0, y);
        }
    }

    private void scanlineBurst() {
        int x0 = (int) drawX;
        int y0 = (int) drawY;
        int w0 = (int) drawW;
        int h0 = (int) drawH;

        stroke(0, random(55, 100));
        int step = (int) random(2, 4);
        for (int y = y0; y < y0 + h0; y += step) {
            line(x0, y, x0 + w0, y);
        }
    }

    private void whiteFlashOrBlackout() {
        noStroke();
        if (random(1) < 0.55f)
            fill(255, random(20, 70));
        else
            fill(0, random(20, 90));
        rect(drawX, drawY, drawW, drawH);
    }

    private void freezeStutter() {
        if (frozenFrame == null || random(1) < 0.45f) {
            frozenFrame = get((int) drawX, (int) drawY, max(1, (int) drawW), max(1, (int) drawH));
        }
        freezeFramesLeft = (int) random(1, 3);
    }

    private void flickerNoise() {
        float activeGlitchIntensity = activeRenderSettings().glitchIntensity();
        noStroke();

        if (random(1) < 0.32f) {
            fill(255, random(6, 28));
            rect(drawX, drawY, drawW, drawH);
        }

        if (random(1) < 0.20f) {
            fill(0, random(6, 22));
            rect(drawX, drawY, drawW, drawH);
        }

        int dots = (int) (2200 * activeGlitchIntensity);
        for (int i = 0; i < dots; i++) {
            if (random(1) < 0.06f) {
                fill(random(255), random(10, 90));
                rect(drawX + random(drawW), drawY + random(drawH), 1, 1);
            }
        }
    }

    private void burstGlitch() {
        int x0 = (int) drawX;
        int y0 = (int) drawY;
        int w0 = (int) drawW;
        int h0 = (int) drawH;

        for (int i = 0; i < (int) random(10, 26); i++) {
            int y = y0 + (int) random(h0);
            int h = (int) random(2, 20);
            h = min(h, y0 + h0 - y);
            int dx = (int) random(-260, 260);
            copy(x0, y, w0, h, x0 + dx, y, w0, h);
        }

        for (int i = 0; i < (int) random(3, 9); i++) {
            noStroke();
            if (random(1) < 0.5f)
                fill(255, random(25, 100));
            else
                fill(random(255), random(255), random(255), random(25, 100));
            rect(drawX, drawY + random(drawH), drawW, random(2, 14));
        }
    }

    private void applyPreset(String name) {
        VideoGlitcherLogic.PresetValues preset = VideoGlitcherLogic.presetForName(name);
        if (preset == null) {
            return;
        }

        glitchIntensity = preset.glitchIntensity();
        glitchFrequency = preset.glitchFrequency();
        episodeMinFrames = preset.episodeMinFrames();
        episodeMaxFrames = preset.episodeMaxFrames();
        calmMinFrames = preset.calmMinFrames();
        calmMaxFrames = preset.calmMaxFrames();
        subtleDamageChance = preset.subtleDamageChance();
        burstChance = preset.burstChance();

        syncGuiValues();
    }

    private void syncGuiValues() {
        cp5.getController("glitchIntensity").setValue(glitchIntensity);
        cp5.getController("glitchFrequency").setValue(glitchFrequency);
        cp5.getController("episodeMinFrames").setValue(episodeMinFrames);
        cp5.getController("episodeMaxFrames").setValue(episodeMaxFrames);
        cp5.getController("calmMinFrames").setValue(calmMinFrames);
        cp5.getController("calmMaxFrames").setValue(calmMaxFrames);
        cp5.getController("subtleDamageChance").setValue(subtleDamageChance);
        cp5.getController("burstChance").setValue(burstChance);

        cp5.getController("useRGBSplit").setValue(useRGBSplit ? 1 : 0);
        cp5.getController("useSlices").setValue(useSlices ? 1 : 0);
        cp5.getController("useBlocks").setValue(useBlocks ? 1 : 0);
        cp5.getController("useBars").setValue(useBars ? 1 : 0);
        cp5.getController("useDropouts").setValue(useDropouts ? 1 : 0);
        cp5.getController("useGhosts").setValue(useGhosts ? 1 : 0);
        cp5.getController("useFreeze").setValue(useFreeze ? 1 : 0);
        cp5.getController("useScanBursts").setValue(useScanBursts ? 1 : 0);
        cp5.getController("useFlash").setValue(useFlash ? 1 : 0);
        cp5.getController("useMicroJitter").setValue(useMicroJitter ? 1 : 0);
        cp5.getController("useZoomWobble").setValue(useZoomWobble ? 1 : 0);
    }

    public void startExport() {
        if (exporting || !movieReady || video == null)
            return;

        normalizeRanges();
        liveRenderSettings = captureCurrentRenderSettings();
        exportMode = ExportMode.INTERACTIVE;
        lockedRenderSettings = null;
        exportReachedPlaybackEnd = false;
        freezeManual = false;
        freezeFramesLeft = 0;
        paused = false;
        pausedFrame = null;
        if (isAtPlaybackEnd()) {
            video.jump(0);
        }
        startPlayback();
        updatePausePlayButton();

        try {
            videoExporter = FfmpegVideoExporter.start(exportFilename, width, height, FPS);
        } catch (IOException | RuntimeException exception) {
            failExport("Export failed: " + exception.getMessage());
            return;
        }

        exporting = true;
        updateExportButtons();
        statusLabel.setText("Status: interactive export to " + exportFilename);
        println("Interactive export started: " + exportFilename);
    }

    private void startFullProcessExport() {
        if (exporting || !movieReady || video == null) {
            return;
        }

        normalizeRanges();
        liveRenderSettings = captureCurrentRenderSettings();
        lockedRenderSettings = captureCurrentRenderSettings();
        exportMode = ExportMode.FULL_PROCESS;
        exportReachedPlaybackEnd = false;
        resetGlitchCycle(lockedRenderSettings);
        freezeManual = false;
        paused = false;
        pausedFrame = null;
        video.noLoop();
        video.jump(0);
        video.play();
        updatePausePlayButton();

        try {
            videoExporter = FfmpegVideoExporter.start(exportFilename, width, height, FPS);
        } catch (IOException | RuntimeException exception) {
            failExport("Export failed: " + exception.getMessage());
            return;
        }

        exporting = true;
        updateExportButtons();
        statusLabel.setText("Status: processing full video to " + exportFilename);
        println("Full-process export started: " + exportFilename);
    }

    public void stopExport() {
        if (!exporting)
            return;

        ExportMode completedMode = exportMode;
        exporting = false;
        if (videoExporter != null) {
            try {
                videoExporter.finish();
            } catch (IOException exception) {
                failExport("Export failed: " + exception.getMessage());
                return;
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                failExport("Export interrupted");
                return;
            }
            videoExporter = null;
        }

        exportMode = ExportMode.NONE;
        exportReachedPlaybackEnd = false;
        lockedRenderSettings = null;
        if (completedMode == ExportMode.FULL_PROCESS && video != null && !paused) {
            startPlayback();
        }
        updateExportButtons();

        if (completedMode == ExportMode.FULL_PROCESS) {
            statusLabel.setText("Status: full-process export finished");
            println("Full-process export finished");
        } else {
            statusLabel.setText("Status: interactive export finished");
            println("Interactive export finished");
        }
    }

    private void saveExportFrame() {
        loadPixels();
        try {
            videoExporter.writeFrame(pixels);
        } catch (IOException exception) {
            failExport("Export failed: " + exception.getMessage());
        }
    }

    private void failExport(String message) {
        ExportMode failedMode = exportMode;
        exporting = false;
        if (videoExporter != null) {
            videoExporter.abort();
            videoExporter = null;
        }

        exportMode = ExportMode.NONE;
        exportReachedPlaybackEnd = false;
        lockedRenderSettings = null;
        if (failedMode == ExportMode.FULL_PROCESS && video != null && !paused) {
            startPlayback();
        }
        updateExportButtons();
        statusLabel.setText("Status: export failed");
        println(message);
        if (launchOptions.smokeTest()) {
            finishSmokeRun(false, message);
        }
    }

    private void togglePausePlay() {
        if (video == null)
            return;

        paused = !paused;
        if (paused) {
            pausedFrame = video.get();
            video.pause();
            statusLabel.setText("Status: paused " + currentVideoName);
        } else {
            pausedFrame = null;
            if (isAtPlaybackEnd()) {
                video.jump(0);
            }
            startPlayback();
            statusLabel.setText("Status: previewing " + currentVideoName);
        }

        updatePausePlayButton();
    }

    private void startPlayback() {
        if (video == null) {
            return;
        }

        if (loopPlayback) {
            video.loop();
        } else {
            video.play();
        }
    }

    private boolean isAtPlaybackEnd() {
        if (!movieReady || video == null) {
            return false;
        }

        return video.duration() > 0
                && video.time() >= max(0, video.duration() - PLAYBACK_END_EPSILON_SECONDS);
    }

    private void updatePlaybackCompletion() {
        if (!movieReady || video == null || paused || video.isPlaying() || !isAtPlaybackEnd()) {
            return;
        }

        paused = true;
        pausedFrame = video.get();
        updatePausePlayButton();

        if (exporting) {
            exportReachedPlaybackEnd = true;
            if (exportMode == ExportMode.FULL_PROCESS) {
                stopExport();
                if (launchOptions.smokeTest() && launchOptions.autoProcess()) {
                    finishSmokeRun(true, "Smoke full-process export completed");
                }
            } else {
                statusLabel.setText("Status: interactive export reached end, click Export Stop");
            }
            return;
        }

        statusLabel.setText("Status: finished " + currentVideoName);
    }

    private void updatePausePlayButton() {
        if (pausePlayButton == null)
            return;

        pausePlayButton.setLabel(video == null || paused ? PLAY_LABEL : PAUSE_LABEL);
    }

    private void updatePlaybackModeButton() {
        if (playbackModeButton == null) {
            return;
        }

        playbackModeButton.setLabel(loopPlayback ? LOOPING_LABEL : PLAY_ONCE_LABEL);
    }

    private void updateGlitchButton() {
        if (glitchToggleButton == null)
            return;

        glitchToggleButton.setLabel(glitchEnabled ? "Glitch On" : "Glitch Off");
    }

    private void updateExportButtons() {
        if (interactiveExportButton != null) {
            interactiveExportButton.setLabel(exportMode == ExportMode.INTERACTIVE ? "Exporting..." : "Export Start");
        }
        if (processVideoButton != null) {
            processVideoButton.setLabel(exportMode == ExportMode.FULL_PROCESS ? PROCESSING_LABEL : PROCESS_FULL_LABEL);
        }
        if (stopExportButton != null) {
            stopExportButton.setLabel(exporting ? "Stop Output" : "Export Stop");
        }
    }

    private void runSmokeCycle() {
        if (launchOptions.autoProcess() && movieReady && video != null && !smokeExportStarted) {
            processVideo();
            smokeExportStarted = exporting;
        }

        if (launchOptions.autoExport() && movieReady && video != null && !smokeExportStarted) {
            startExport();
            smokeExportStarted = exporting;
        }

        if (launchOptions.autoExport() && exporting) {
            smokeExportFramesSaved++;
            if (smokeExportFramesSaved >= launchOptions.exportFrames()) {
                stopExport();
                finishSmokeRun(true, "Smoke export completed");
                return;
            }
        }

        if (frameCount >= launchOptions.smokeFrames()) {
            if (!launchOptions.videoPath().isEmpty() && !movieReady) {
                finishSmokeRun(false, "Smoke run timed out before video became ready");
                return;
            }

            if (launchOptions.autoExport() && !smokeExportStarted) {
                finishSmokeRun(false, "Smoke run timed out before export started");
                return;
            }

            if (launchOptions.autoProcess() && !smokeExportStarted) {
                finishSmokeRun(false, "Smoke run timed out before full-process export started");
                return;
            }

            if (launchOptions.autoProcess() && exporting) {
                finishSmokeRun(false, "Smoke run timed out before full-process export finished");
                return;
            }

            if (exporting) {
                stopExport();
            }

            finishSmokeRun(true, "Smoke startup completed");
        }
    }

    private void finishSmokeRun(boolean success, String message) {
        println(message);
        if (exporting) {
            stopExport();
        }
        dispose();
        System.exit(success ? 0 : 1);
    }

    private boolean shouldWriteExportFrame() {
        return exporting && videoExporter != null && movieReady
                && !(exportMode == ExportMode.INTERACTIVE && exportReachedPlaybackEnd);
    }

    private boolean isFullProcessExportActive() {
        return exporting && exportMode == ExportMode.FULL_PROCESS;
    }

    private void resetGlitchCycle(RenderSettings settings) {
        glitchActive = false;
        glitchFramesLeft = 0;
        calmFramesLeft = (int) random(settings.calmMinFrames(), settings.calmMaxFrames() + 1);
        frozenFrame = null;
        previousFrame = null;
        freezeFramesLeft = 0;
        zoomJitter = 1.0f;
        offsetX = 0;
        offsetY = 0;
    }

    private RenderSettings captureCurrentRenderSettings() {
        return new RenderSettings(
                glitchEnabled,
                glitchIntensity,
                glitchFrequency,
                episodeMinFrames,
                episodeMaxFrames,
                calmMinFrames,
                calmMaxFrames,
                subtleDamageChance,
                burstChance,
                useRGBSplit,
                useSlices,
                useBlocks,
                useBars,
                useDropouts,
                useGhosts,
                useFreeze,
                useScanBursts,
                useFlash,
                useMicroJitter,
                useZoomWobble);
    }

    private RenderSettings activeRenderSettings() {
        return lockedRenderSettings != null ? lockedRenderSettings : liveRenderSettings;
    }

    private record RenderSettings(
            boolean glitchEnabled,
            float glitchIntensity,
            float glitchFrequency,
            float episodeMinFrames,
            float episodeMaxFrames,
            float calmMinFrames,
            float calmMaxFrames,
            float subtleDamageChance,
            float burstChance,
            boolean useRGBSplit,
            boolean useSlices,
            boolean useBlocks,
            boolean useBars,
            boolean useDropouts,
            boolean useGhosts,
            boolean useFreeze,
            boolean useScanBursts,
            boolean useFlash,
            boolean useMicroJitter,
            boolean useZoomWobble) {
    }

    private enum ExportMode {
        NONE,
        INTERACTIVE,
        FULL_PROCESS
    }

    private static final class LaunchOptions {
        private final boolean smokeTest;
        private final String videoPath;
        private final String presetName;
        private final boolean autoExport;
        private final boolean autoProcess;
        private final int smokeFrames;
        private final int exportFrames;

        private LaunchOptions(boolean smokeTest, String videoPath, String presetName, boolean autoExport,
                boolean autoProcess, int smokeFrames, int exportFrames) {
            this.smokeTest = smokeTest;
            this.videoPath = videoPath;
            this.presetName = presetName;
            this.autoExport = autoExport;
            this.autoProcess = autoProcess;
            this.smokeFrames = smokeFrames;
            this.exportFrames = exportFrames;
        }

        static LaunchOptions defaults() {
            return new LaunchOptions(false, "", "Cinematic", false, false, 60, 48);
        }

        static LaunchOptions parse(String[] args) {
            boolean smokeTest = false;
            String videoPath = "";
            String presetName = "Cinematic";
            boolean autoExport = false;
            boolean autoProcess = false;
            int smokeFrames = 60;
            int exportFrames = 48;

            for (String arg : args) {
                if ("--smoke-test".equals(arg)) {
                    smokeTest = true;
                } else if (arg.startsWith("--video=")) {
                    videoPath = arg.substring("--video=".length());
                } else if (arg.startsWith("--preset=")) {
                    presetName = parsePresetName(arg.substring("--preset=".length()));
                } else if ("--auto-export".equals(arg)) {
                    autoExport = true;
                } else if ("--auto-process".equals(arg)) {
                    autoProcess = true;
                } else if (arg.startsWith("--smoke-frames=")) {
                    smokeFrames = parsePositiveInt(arg.substring("--smoke-frames=".length()), 60);
                } else if (arg.startsWith("--export-frames=")) {
                    exportFrames = parsePositiveInt(arg.substring("--export-frames=".length()), 48);
                }
            }

            return new LaunchOptions(smokeTest, videoPath, presetName, autoExport, autoProcess, smokeFrames,
                    exportFrames);
        }

        private static String parsePresetName(String value) {
            String trimmed = value == null ? "" : value.trim();
            if (trimmed.isEmpty()) {
                return "Cinematic";
            }

            switch (trimmed.toLowerCase()) {
                case "subtle":
                    return "Subtle";
                case "cinematic":
                    return "Cinematic";
                case "corrupted file":
                case "corrupted-file":
                case "corrupted_file":
                    return "Corrupted File";
                case "broken codec":
                case "broken-codec":
                case "broken_codec":
                    return "Broken Codec";
                case "extreme":
                    return "Extreme";
                default:
                    return "Cinematic";
            }
        }

        private static int parsePositiveInt(String value, int fallback) {
            try {
                int parsed = Integer.parseInt(value);
                return parsed > 0 ? parsed : fallback;
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }

        boolean smokeTest() {
            return smokeTest;
        }

        String videoPath() {
            return videoPath;
        }

        String presetName() {
            return presetName;
        }

        boolean autoExport() {
            return autoExport;
        }

        boolean autoProcess() {
            return autoProcess;
        }

        int smokeFrames() {
            return smokeFrames;
        }

        int exportFrames() {
            return exportFrames;
        }
    }
}
