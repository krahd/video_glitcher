package tom.videoGlitcher;

import processing.core.*;
import processing.data.IntList;
import processing.event.MouseEvent;
import processing.video.*;
import controlP5.*;


import java.io.File;
import java.io.IOException;
import java.util.HashMap;

public class VideoGlitcher extends PApplet {
    
    private static LaunchOptions launchOptions = LaunchOptions.defaults();
    private static final float PLAYBACK_END_EPSILON_SECONDS = 0.05f;
    private static final String PLAY_LABEL = "PLAY";
    private static final String PAUSE_LABEL = "PAUSE";
    private static final String REWIND_TO_START_LABEL = "REWIND";
    private static final String LOOPING_LABEL = "LOOP";
    private static final String PLAY_ONCE_LABEL = "ONCE";
    private static final String PROCESS_FULL_LABEL = "Process Full";
    private static final String PROCESSING_LABEL = "Processing...";
    private static final String RANDOM_PRESET_NAME = "Random";
    private static final String[] PRESET_NAMES = {
        "Subtle",
        "Cinematic",
        "Corrupted File",
        "Broken Codec",
        "Extreme",
        "VHS Decay",
        "Old Digicam",
        RANDOM_PRESET_NAME
    };
    private static final String[] PRESET_DISPLAY_NAMES = {
        "Subtle",
        "Cinematic",
        "Corrupted",
        "Broken Codec",
        "Extreme",
        "VHS Decay",
        "Old Digicam",
        "Random"
    };
    private static final int GUI_SCROLL_STEP = 34;
    private static final int GUI_HEADER_HEIGHT = 34;
    private static final int GUI_FOOTER_HEIGHT = 124;
    private static final int GUI_TOGGLE_GAP = 26;
    private static final int GUI_SECTION_GAP = 18;
    private static final int PRESET_GRID_COLUMNS = 4;
    private static final int PRESET_BUTTON_W = 112;
    private static final int PRESET_BUTTON_H = 30;
    private static final int PRESET_GRID_GAP = 8;
    private static final int BUTTON_LABEL_SIZE = 12;
    private static final int HUD_MARGIN = 12;
    private static final int HUD_HEIGHT = 42;
    private static final int FOOTER_BUTTON_H = 30;
    private static final int FOOTER_BUTTON_GAP = 8;
    private static final int FOOTER_ROW_STEP = 38;
    // All button rows: total width 464px (4x112 + 3x8)
    private static final int FOOTER_ROW_TOTAL_W = 464;
    private static final int FOOTER_LOAD_BUTTON_W = 112;
    private static final int FOOTER_REWIND_BUTTON_W = 112;
    private static final int FOOTER_PLAY_BUTTON_W = 112;
    private static final int FOOTER_PLAYBACK_MODE_BUTTON_W = 112;
    // Middle row: 3 buttons, expand Export/Stop to fill row
    private static final int FOOTER_GLITCH_BUTTON_W = 112;
    private static final int FOOTER_EXPORT_BUTTON_W = 168;
    private static final int FOOTER_STOP_EXPORT_BUTTON_W = 168;
    // Bottom row: 1 button, expand to fill row
    private static final int FOOTER_PROCESS_BUTTON_W = 464;
    private static final int ADVANCED_TOGGLE_X_OFFSET = 324;
    private static final int ADVANCED_LABEL_X_OFFSET = 350;
    private static final int ADVANCED_TOGGLE_Y_OFFSET = 0;
    private static final int ADVANCED_LABEL_Y_OFFSET = 1;
    
    public static void main(String[] args) {
        launchOptions = LaunchOptions.parse(args);
        if (launchOptions.smokeTest()) {
            PApplet.main(new String[] { VideoGlitcher.class.getName() });
            return;
        }
        
        PApplet.hideMenuBar();
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
    private boolean selectingProcessOutput = false;
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
    
    private float retroAmount = 0.28f;
    private float retroJitter = 0.24f;
    private float trackingDrift = 0.32f;
    private float headSwitchHeight = 0.10f;
    private float chromaOffset = 0.22f;
    private float smearStrength = 0.20f;
    private float columnDriftAmount = 0.24f;
    
    private boolean useTrackingTear = false;
    private boolean useHeadSwitchBand = false;
    private boolean useChromaDrift = false;
    private boolean useScanlineWobble = false;
    private boolean useVerticalSmear = false;
    private boolean useColumnDrift = false;
    private boolean compactGuiMode = true;
    
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
    private Toggle advancedGuiToggle;
    private Textlabel guiTitleLabel;
    private Textlabel presetSectionLabel;
    private Textlabel retroSectionLabel;
    private final HashMap<String, Textlabel> guiLabels = new HashMap<>();
    private final HashMap<String, Button> presetButtons = new HashMap<>();
    private PFont guiFont;
    private PFont guiTitleFont;
    private String activePresetName = PRESET_NAMES[1];
    private String statusMessage = "Status: no video loaded";
    
    private boolean triedVideoUriFallback = false;
    private boolean smokeExportStarted = false;
    private int smokeExportFramesSaved = 0;
    
    private int panelX = 12;
    private int panelY = 12;
    private int panelW = 520;
    private int panelH = 904;
    
    private int guiX = panelX + 14;
    private int guiY = panelY + 14;
    private int sliderW = 220;
    private int sliderH = 18;
    private int labelX = guiX + sliderW + 82;
    private int rowGap = 30;
    private int guiScrollOffset = 0;
    private int guiContentHeight = 0;
    private int guiViewportTop = 0;
    private int guiViewportBottom = 0;
    
    @Override
    public void settings() {
        pixelDensity(1);
        noSmooth();
        if (launchOptions.smokeTest()) {
            size(960, 540, P2D);
        } else {
            ensureMenuBarHidden();
            fullScreen(P2D);
        }
    }
    
    @Override
    public void setup() {
        frameRate(FPS);
        ensureMenuBarHidden();
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
        ensureMenuBarHidden();
        
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
            showGUI = showHUD;
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
        // Toggle advanced checkbox if label is clicked
        Textlabel advancedLabel = guiLabels.get("lbl_advancedGui");
        if (advancedLabel != null) {
            float ax = advancedLabel.getPosition()[0], ay = advancedLabel.getPosition()[1];
            float aw = advancedLabel.getWidth(), ah = advancedLabel.getHeight();
            if (mouseX >= ax && mouseX <= ax + aw && mouseY >= ay && mouseY <= ay + ah) {
                if (advancedGuiToggle != null) {
                    advancedGuiToggle.setValue(advancedGuiToggle.getValue() < 0.5f ? 1.0f : 0.0f);
                }
            }
        }
    }
    
    @Override
    public void mouseWheel(MouseEvent event) {
        if (!showGUI || !isPointerOverGui()) {
            return;
        }
        
        if (maxGuiScroll() <= 0) {
            return;
        }
        
        guiScrollOffset += Math.round(event.getCount() * GUI_SCROLL_STEP);
        clampGuiScroll();
        refreshGuiLayout();
    }
    
    public void movieEvent(Movie m) {
        if (!paused) {
            updateMovieFrame(m);
        }
    }
    
    private void ensureMenuBarHidden() {
        if (!launchOptions.smokeTest()) {
            PApplet.hideMenuBar();
        }
    }
    
    private void updateMovieFrame(Movie movie) {
        movie.read();
        
        if (!movieReady && movie.width > 0 && movie.height > 0) {
            movieReady = true;
            computeVideoFit();
            setStatusMessage("Status: previewing " + currentVideoName);
            
            if (!hasLoadedFirstVideo) {
                hasLoadedFirstVideo = true;
                glitchEnabled = true;
                updateGlitchButton();
            }
            
            if (paused) {
                pausedFrame = movie.get();
                movie.pause();
                setStatusMessage("Status: paused " + currentVideoName);
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
        guiLabels.clear();
        presetButtons.clear();
        guiFont = createFont("SansSerif", 14, true);
        guiTitleFont = createFont("SansSerif", 16, true);
        cp5.setFont(guiFont);
        
        int x = guiX;
        // Move all UI elements down to give space for DIGITAL DISTORTIONS label
        int y = guiY + 18;
        
        guiTitleLabel = addGuiLabel("guiTitle", x, y, "video_glitcher");
        guiTitleLabel.setFont(guiTitleFont);
        guiTitleLabel.setColor(color(255, 220, 40)); // yellow
        
        advancedGuiToggle = cp5.addToggle("advancedGui")
        .setPosition(x + ADVANCED_TOGGLE_X_OFFSET, y + ADVANCED_TOGGLE_Y_OFFSET)
        .setSize(18, 18)
        .setValue(!compactGuiMode);
        advancedGuiToggle.getCaptionLabel().setVisible(false);
        addGuiLabel("lbl_advancedGui", x + ADVANCED_LABEL_X_OFFSET, y + ADVANCED_LABEL_Y_OFFSET, "Advanced");
        
        y += GUI_HEADER_HEIGHT;
        
        
        presetSectionLabel = addGuiLabel("presetSection", x, y, "PRESETS");
        presetSectionLabel.setFont(guiTitleFont);
        presetSectionLabel.getValueLabel().align(ControlP5.LEFT, ControlP5.CENTER);
        addPresetButtons(x, y + 24);
        
        // Add DIGITAL DISTORTIONS label below preset buttons
        int digitalDistortionsY = y - 10 + ((PRESET_NAMES.length + PRESET_GRID_COLUMNS - 1) / PRESET_GRID_COLUMNS) * (PRESET_BUTTON_H + PRESET_GRID_GAP) + 24;
        Textlabel digitalDistortionsLabel = addGuiLabel("digitalDistortionsSection", x, digitalDistortionsY, "DIGITAL DISTORTIONS");
        digitalDistortionsLabel.setFont(guiTitleFont);
        digitalDistortionsLabel.getValueLabel().align(ControlP5.LEFT, ControlP5.CENTER);
        
        
        
        addSliderRow("glitchIntensity", x, y, sliderW, sliderH, 0.1f, 2.0f, glitchIntensity, "Digital Intensity");
        addSliderRow("glitchFrequency", x, y, sliderW, sliderH, 0.0f, 1.0f, glitchFrequency, "Glitch Activity");
        addSliderRow("episodeMinFrames", x, y, sliderW, sliderH, 1, 30, episodeMinFrames, "Episode Min");
        addSliderRow("episodeMaxFrames", x, y, sliderW, sliderH, 1, 40, episodeMaxFrames, "Episode Max");
        addSliderRow("calmMinFrames", x, y, sliderW, sliderH, 1, 80, calmMinFrames, "Calm Min");
        addSliderRow("calmMaxFrames", x, y, sliderW, sliderH, 1, 120, calmMaxFrames, "Calm Max");
        addSliderRow("subtleDamageChance", x, y, sliderW, sliderH, 0.0f, 1.0f, subtleDamageChance, "Damage Texture");
        addSliderRow("burstChance", x, y, sliderW, sliderH, 0.0f, 1.0f, burstChance, "Burst Chance");
        
        addToggleRow("useRGBSplit", "RGB Split", x, y);
        addToggleRow("useSlices", "Slices", x, y);
        addToggleRow("useBlocks", "Blocks", x, y);
        addToggleRow("useBars", "Bars", x, y);
        addToggleRow("useDropouts", "Dropouts", x, y);
        addToggleRow("useGhosts", "Ghosts", x, y);
        addToggleRow("useFreeze", "Freeze", x, y);
        addToggleRow("useScanBursts", "Scan Bursts", x, y);
        addToggleRow("useFlash", "Flash", x, y);
        addToggleRow("useMicroJitter", "Micro Jitter", x, y);
        addToggleRow("useZoomWobble", "Zoom Wobble", x, y);
        
        retroSectionLabel = addGuiLabel("retroSection", x, y, "RETRO DISTORTIONS");
        retroSectionLabel.setFont(guiTitleFont);
        retroSectionLabel.getValueLabel().align(ControlP5.LEFT, ControlP5.CENTER);
        
        addSliderRow("retroAmount", x, y, sliderW, sliderH, 0.0f, 1.0f, retroAmount, "Analogue Intensity");
        addSliderRow("retroJitter", x, y, sliderW, sliderH, 0.0f, 1.0f, retroJitter, "Analogue Jitter");
        addToggleRow("useTrackingTear", "Tracking Tear", x, y);
        addToggleRow("useHeadSwitchBand", "Head Switch", x, y);
        addToggleRow("useChromaDrift", "Chroma Drift", x, y);
        addToggleRow("useScanlineWobble", "Scanline Wobble", x, y);
        addToggleRow("useVerticalSmear", "Vertical Smear", x, y);
        addToggleRow("useColumnDrift", "Column Drift", x, y);
        addSliderRow("trackingDrift", x, y, sliderW, sliderH, 0.0f, 1.0f, trackingDrift, "Tracking Drift");
        addSliderRow("headSwitchHeight", x, y, sliderW, sliderH, 0.0f, 1.0f, headSwitchHeight, "Head Switch Height");
        addSliderRow("chromaOffset", x, y, sliderW, sliderH, 0.0f, 1.0f, chromaOffset, "Chroma Offset");
        addSliderRow("smearStrength", x, y, sliderW, sliderH, 0.0f, 1.0f, smearStrength, "Smear Strength");
        addSliderRow("columnDriftAmount", x, y, sliderW, sliderH, 0.0f, 1.0f, columnDriftAmount, "Column Drift");
        
        int firstRowY = y;
        int loadButtonX = x;
        int rewindButtonX = loadButtonX + FOOTER_LOAD_BUTTON_W + FOOTER_BUTTON_GAP;
        int pausePlayButtonX = rewindButtonX + FOOTER_REWIND_BUTTON_W + FOOTER_BUTTON_GAP;
        int playbackModeButtonX = pausePlayButtonX + FOOTER_PLAY_BUTTON_W + FOOTER_BUTTON_GAP;
        
        Button bLoad = cp5.addButton("loadVideo")
        .setPosition(loadButtonX, firstRowY)
        .setSize(FOOTER_LOAD_BUTTON_W, FOOTER_BUTTON_H)
        .setLabel("Load");
        styleFooterButton(bLoad);
        
        Button rewindButton = cp5.addButton("rewindToStart")
        .setPosition(rewindButtonX, firstRowY)
        .setSize(FOOTER_REWIND_BUTTON_W, FOOTER_BUTTON_H)
        .setLabel(REWIND_TO_START_LABEL);
        styleFooterButton(rewindButton);
        
        pausePlayButton = cp5.addButton("pausePlay")
        .setPosition(pausePlayButtonX, firstRowY)
        .setSize(FOOTER_PLAY_BUTTON_W, FOOTER_BUTTON_H)
        .setLabel(PLAY_LABEL);
        styleFooterButton(pausePlayButton);
        
        playbackModeButton = cp5.addButton("togglePlaybackMode")
        .setPosition(playbackModeButtonX, firstRowY)
        .setSize(FOOTER_PLAYBACK_MODE_BUTTON_W, FOOTER_BUTTON_H)
        .setLabel(LOOPING_LABEL);
        styleFooterButton(playbackModeButton);
        
        y += FOOTER_ROW_STEP;
        
        int secondRowY = y;
        int exportButtonX = x + FOOTER_GLITCH_BUTTON_W + FOOTER_BUTTON_GAP;
        int stopExportButtonX = exportButtonX + FOOTER_EXPORT_BUTTON_W + FOOTER_BUTTON_GAP;
        
        glitchToggleButton = cp5.addButton("toggleGlitching")
        .setPosition(x, secondRowY)
        .setSize(FOOTER_GLITCH_BUTTON_W, FOOTER_BUTTON_H)
        .setLabel("Glitch");
        styleFooterButton(glitchToggleButton);
        
        interactiveExportButton = cp5.addButton("startExport")
        .setPosition(exportButtonX, secondRowY)
        .setSize(FOOTER_EXPORT_BUTTON_W, FOOTER_BUTTON_H)
        .setLabel("Export Start");
        styleFooterButton(interactiveExportButton);
        
        stopExportButton = cp5.addButton("stopExport")
        .setPosition(stopExportButtonX, secondRowY)
        .setSize(FOOTER_STOP_EXPORT_BUTTON_W, FOOTER_BUTTON_H)
        .setLabel("Export Stop");
        styleFooterButton(stopExportButton);
        
        y += FOOTER_ROW_STEP;
        
        processVideoButton = cp5.addButton("processVideo")
        .setPosition(x, y)
        .setSize(FOOTER_PROCESS_BUTTON_W, FOOTER_BUTTON_H)
        .setLabel(PROCESS_FULL_LABEL);
        styleFooterButton(processVideoButton);
        
        refreshGuiLayout();
        updatePausePlayButton();
        updatePlaybackModeButton();
        updateGlitchButton();
        updateExportButtons();
        updatePresetButtons();
    }
    
    private void addPresetButtons(int x, int y) {
        for (int i = 0; i < PRESET_NAMES.length; i++) {
            int column = i % PRESET_GRID_COLUMNS;
            int row = i / PRESET_GRID_COLUMNS;
            int buttonX = x + column * (PRESET_BUTTON_W + PRESET_GRID_GAP);
            int buttonY = y + row * (PRESET_BUTTON_H + PRESET_GRID_GAP);
            
            Button button = cp5.addButton("preset_" + i)
            .setPosition(buttonX, buttonY)
            .setSize(PRESET_BUTTON_W, PRESET_BUTTON_H)
            .setLabel(PRESET_DISPLAY_NAMES[i]);
            styleButton(button);
            presetButtons.put(PRESET_NAMES[i], button);
        }
    }
    
    private void updatePresetButtons() {
        for (int i = 0; i < PRESET_NAMES.length; i++) {
            Button button = presetButtons.get(PRESET_NAMES[i]);
            if (button != null) {
                stylePresetButton(button, PRESET_NAMES[i].equals(activePresetName));
            }
        }
    }
    
    private void stylePresetButton(Button button, boolean active) {
        if (active) {
            button.setColorBackground(color(208, 102, 71));
            button.setColorForeground(color(230, 120, 86));
            button.setColorActive(color(247, 141, 101));
        } else {
            button.setColorBackground(color(40, 40, 40));
            button.setColorForeground(color(66, 66, 66));
            button.setColorActive(color(92, 92, 92));
        }
    }
    
    private void styleButton(Button button) {
        button.getCaptionLabel().align(ControlP5.CENTER, ControlP5.CENTER);
        button.getCaptionLabel().setFont(guiFont).setSize(BUTTON_LABEL_SIZE);
    }
    
    private void styleFooterButton(Button button) {
        styleButton(button);
        button.setColorBackground(color(26, 67, 51));
        button.setColorForeground(color(38, 96, 72));
        button.setColorActive(color(55, 128, 95));
    }
    
    private Textlabel addGuiLabel(String name, int x, int y, String text) {
        Textlabel label = cp5.addTextlabel(name)
        .setPosition(x, y)
        .setText(text);
        label.setFont(guiFont);
        guiLabels.put(name, label);
        return label;
    }
    
    private void addSliderRow(String name, int x, int y, int w, int h, float minV, float maxV, float value,
        String label) {
            Slider s = cp5.addSlider(name)
            .setPosition(x, y)
            .setSize(w, h)
            .setRange(minV, maxV)
            .setValue(value);
            
            s.getCaptionLabel().setVisible(false);
            s.getValueLabel().align(ControlP5.RIGHT_OUTSIDE, ControlP5.CENTER).setPaddingX(8).setFont(guiFont).setSize(12);
            
            addGuiLabel("lbl_" + name, labelX, y + 1, label);
        }
        
        private void addToggleRow(String name, String label, int x, int y) {
            Toggle t = cp5.addToggle(name)
            .setPosition(x, y)
            .setSize(20, 20)
            .setValue(getToggleValue(name));
            
            t.getCaptionLabel().setVisible(false);
            
            addGuiLabel("lbl_" + name, x + 28, y + 1, label);
        }
        
        private void setStandaloneControllerVisible(Controller<?> controller, boolean visible) {
            if (controller != null) {
                controller.setVisible(visible);
            }
        }
        
        private void setControlVisible(String name, boolean visible) {
            Controller<?> controller = cp5.getController(name);
            if (controller != null) {
                controller.setVisible(visible);
            }
            
            Textlabel label = guiLabels.get("lbl_" + name);
            if (label != null) {
                label.setVisible(visible);
            }
        }
        
        private void setRowPosition(String name, int x, int y, boolean toggleRow) {
            Controller<?> controller = cp5.getController(name);
            if (controller != null) {
                controller.setPosition(x, y);
            }
            
            Textlabel label = guiLabels.get("lbl_" + name);
            if (label != null) {
                label.setPosition(toggleRow ? x + 28 : labelX, y + 1);
            }
        }
        
        private boolean isScrollableRowVisible(int screenY, int rowHeight) {
            return screenY + rowHeight >= guiViewportTop && screenY <= guiViewportBottom;
        }
        
        private int layoutScrollableSliderRow(String name, int x, int logicalY, boolean visible) {
            if (!visible) {
                setControlVisible(name, false);
                return logicalY;
            }
            
            int screenY = guiViewportTop + logicalY - guiScrollOffset;
            boolean rowVisible = isScrollableRowVisible(screenY, sliderH);
            setControlVisible(name, rowVisible);
            if (rowVisible) {
                setRowPosition(name, x, screenY, false);
            }
            return logicalY + rowGap;
        }
        
        private int layoutScrollableToggleRow(String name, int x, int logicalY, boolean visible) {
            if (!visible) {
                setControlVisible(name, false);
                return logicalY;
            }
            
            int screenY = guiViewportTop + logicalY - guiScrollOffset;
            boolean rowVisible = isScrollableRowVisible(screenY, 20);
            setControlVisible(name, rowVisible);
            if (rowVisible) {
                setRowPosition(name, x, screenY, true);
            }
            return logicalY + GUI_TOGGLE_GAP;
        }
        
        private int layoutScrollableLabel(Textlabel label, int x, int logicalY, int rowHeight, boolean visible) {
            if (label == null) {
                return logicalY;
            }
            
            if (!visible) {
                label.setVisible(false);
                return logicalY;
            }
            
            int screenY = guiViewportTop + logicalY - guiScrollOffset;
            boolean rowVisible = isScrollableRowVisible(screenY, rowHeight);
            label.setVisible(rowVisible);
            if (rowVisible) {
                label.setPosition(x, screenY);
            }
            return logicalY + rowHeight;
        }
        
        private int measureGuiContentHeight() {
            int logicalY = 0;
            
            if (compactGuiMode) {
                logicalY += rowGap;
                logicalY += rowGap;
                logicalY += rowGap;
                logicalY += rowGap;
                return logicalY + 8;
            }
            
            logicalY += rowGap;
            logicalY += rowGap;
            logicalY += rowGap;
            logicalY += rowGap;
            logicalY += rowGap;
            logicalY += rowGap;
            logicalY += rowGap;
            logicalY += rowGap;
            logicalY += GUI_SECTION_GAP;
            
            logicalY += GUI_TOGGLE_GAP * 11;
            logicalY += GUI_SECTION_GAP;
            
            logicalY += 24;
            logicalY += rowGap;
            logicalY += rowGap;
            logicalY += GUI_TOGGLE_GAP * 6;
            logicalY += GUI_SECTION_GAP;
            
            logicalY += rowGap;
            logicalY += rowGap;
            logicalY += rowGap;
            logicalY += rowGap;
            logicalY += rowGap;
            
            return logicalY + 8;
        }
        
        private int maxGuiScroll() {
            return max(0, guiContentHeight - max(0, guiViewportBottom - guiViewportTop));
        }
        
        private void clampGuiScroll() {
            guiScrollOffset = constrain(guiScrollOffset, 0, maxGuiScroll());
        }
        
        private float hudTopY() {
            return height - HUD_HEIGHT - HUD_MARGIN;
        }
        
        private void refreshGuiLayout() {
            if (cp5 == null) {
                return;
            }
            
            int x = guiX;
            int headerY = guiY;
            int presetY = headerY + GUI_HEADER_HEIGHT;
            int presetButtonsY = presetY + 24;
            int presetGridBottom = presetButtonsY + PRESET_BUTTON_H * 2 + PRESET_GRID_GAP;
            
            if (guiTitleLabel != null) {
                guiTitleLabel.setPosition(x, headerY).setVisible(true);
            }
            
            if (advancedGuiToggle != null) {
                advancedGuiToggle.setVisible(true);
                advancedGuiToggle.setPosition(x + ADVANCED_TOGGLE_X_OFFSET, headerY + ADVANCED_TOGGLE_Y_OFFSET);
            }
            Textlabel advancedLabel = guiLabels.get("lbl_advancedGui");
            if (advancedLabel != null) {
                advancedLabel.setVisible(true);
                advancedLabel.setPosition(x + ADVANCED_LABEL_X_OFFSET, headerY + ADVANCED_LABEL_Y_OFFSET);
            }
            
            if (presetSectionLabel != null) {
                presetSectionLabel.setVisible(true);
                presetSectionLabel.setPosition(x, presetY);
            }
            
            for (int i = 0; i < PRESET_NAMES.length; i++) {
                Button button = presetButtons.get(PRESET_NAMES[i]);
                if (button != null) {
                    int column = i % PRESET_GRID_COLUMNS;
                    int row = i / PRESET_GRID_COLUMNS;
                    button.setVisible(true);
                    button.setPosition(x + column * (PRESET_BUTTON_W + PRESET_GRID_GAP),
                    presetButtonsY + row * (PRESET_BUTTON_H + PRESET_GRID_GAP));
                }
            }
            
            updateAdvancedToggle();
            updatePresetButtons();
            
            guiContentHeight = measureGuiContentHeight();
            
            int maxVisiblePanelHeight = max(420, round(hudTopY() - panelY));
            int basePanelHeight = (presetGridBottom - panelY) + GUI_FOOTER_HEIGHT + 34;
            int desiredPanelHeight = basePanelHeight + guiContentHeight;
            if (compactGuiMode) {
                panelH = min(maxVisiblePanelHeight, desiredPanelHeight);
                panelH = max(panelH, 428);
                guiScrollOffset = 0;
            } else {
                panelH = maxVisiblePanelHeight;
            }
            
            int footerY = panelY + panelH - GUI_FOOTER_HEIGHT + 8;
            guiViewportTop = presetGridBottom + 24;
            guiViewportBottom = footerY - 18;
            clampGuiScroll();
            
            int logicalY = 0;
            
            logicalY = layoutScrollableSliderRow("glitchIntensity", x, logicalY, true);
            logicalY = layoutScrollableSliderRow("glitchFrequency", x, logicalY, true);
            logicalY = layoutScrollableSliderRow("retroAmount", x, logicalY, true);
            logicalY = layoutScrollableSliderRow("subtleDamageChance", x, logicalY, true);
            
            if (!compactGuiMode) {
                logicalY = layoutScrollableSliderRow("episodeMinFrames", x, logicalY, true);
                logicalY = layoutScrollableSliderRow("episodeMaxFrames", x, logicalY, true);
                logicalY = layoutScrollableSliderRow("calmMinFrames", x, logicalY, true);
                logicalY = layoutScrollableSliderRow("calmMaxFrames", x, logicalY, true);
                logicalY = layoutScrollableSliderRow("burstChance", x, logicalY, true);
                logicalY += GUI_SECTION_GAP;
                
                logicalY = layoutScrollableToggleRow("useRGBSplit", x, logicalY, true);
                logicalY = layoutScrollableToggleRow("useSlices", x, logicalY, true);
                logicalY = layoutScrollableToggleRow("useBlocks", x, logicalY, true);
                logicalY = layoutScrollableToggleRow("useBars", x, logicalY, true);
                logicalY = layoutScrollableToggleRow("useDropouts", x, logicalY, true);
                logicalY = layoutScrollableToggleRow("useGhosts", x, logicalY, true);
                logicalY = layoutScrollableToggleRow("useFreeze", x, logicalY, true);
                logicalY = layoutScrollableToggleRow("useScanBursts", x, logicalY, true);
                logicalY = layoutScrollableToggleRow("useFlash", x, logicalY, true);
                logicalY = layoutScrollableToggleRow("useMicroJitter", x, logicalY, true);
                logicalY = layoutScrollableToggleRow("useZoomWobble", x, logicalY, true);
                logicalY += GUI_SECTION_GAP;
            } else {
                setControlVisible("episodeMinFrames", false);
                setControlVisible("episodeMaxFrames", false);
                setControlVisible("calmMinFrames", false);
                setControlVisible("calmMaxFrames", false);
                setControlVisible("burstChance", false);
                setControlVisible("useRGBSplit", false);
                setControlVisible("useSlices", false);
                setControlVisible("useBlocks", false);
                setControlVisible("useBars", false);
                setControlVisible("useDropouts", false);
                setControlVisible("useGhosts", false);
                setControlVisible("useFreeze", false);
                setControlVisible("useScanBursts", false);
                setControlVisible("useFlash", false);
                setControlVisible("useMicroJitter", false);
                setControlVisible("useZoomWobble", false);
            }
            
            if (!compactGuiMode) {
                logicalY = layoutScrollableLabel(retroSectionLabel, x, logicalY, 24, true);
                logicalY = layoutScrollableSliderRow("retroJitter", x, logicalY, true);
                logicalY = layoutScrollableToggleRow("useTrackingTear", x, logicalY, true);
                logicalY = layoutScrollableToggleRow("useHeadSwitchBand", x, logicalY, true);
                logicalY = layoutScrollableToggleRow("useChromaDrift", x, logicalY, true);
                logicalY = layoutScrollableToggleRow("useScanlineWobble", x, logicalY, true);
                logicalY = layoutScrollableToggleRow("useVerticalSmear", x, logicalY, true);
                logicalY = layoutScrollableToggleRow("useColumnDrift", x, logicalY, true);
                logicalY += GUI_SECTION_GAP;
                
                logicalY = layoutScrollableSliderRow("trackingDrift", x, logicalY, true);
                logicalY = layoutScrollableSliderRow("headSwitchHeight", x, logicalY, true);
                logicalY = layoutScrollableSliderRow("chromaOffset", x, logicalY, true);
                logicalY = layoutScrollableSliderRow("smearStrength", x, logicalY, true);
                logicalY = layoutScrollableSliderRow("columnDriftAmount", x, logicalY, true);
            } else {
                if (retroSectionLabel != null) {
                    retroSectionLabel.setVisible(false);
                }
                setControlVisible("retroJitter", false);
                setControlVisible("useTrackingTear", false);
                setControlVisible("useHeadSwitchBand", false);
                setControlVisible("useChromaDrift", false);
                setControlVisible("useScanlineWobble", false);
                setControlVisible("useVerticalSmear", false);
                setControlVisible("useColumnDrift", false);
                setControlVisible("trackingDrift", false);
                setControlVisible("headSwitchHeight", false);
                setControlVisible("chromaOffset", false);
                setControlVisible("smearStrength", false);
                setControlVisible("columnDriftAmount", false);
            }
            
            Controller<?> loadVideoButton = cp5.getController("loadVideo");
            setStandaloneControllerVisible(loadVideoButton, true);
            int loadButtonX = x;
            int rewindButtonX = loadButtonX + FOOTER_LOAD_BUTTON_W + FOOTER_BUTTON_GAP;
            int pausePlayButtonX = rewindButtonX + FOOTER_REWIND_BUTTON_W + FOOTER_BUTTON_GAP;
            int playbackModeButtonX = pausePlayButtonX + FOOTER_PLAY_BUTTON_W + FOOTER_BUTTON_GAP;
            if (loadVideoButton != null) {
                loadVideoButton.setPosition(loadButtonX, footerY);
            }
            
            Controller<?> rewindButton = cp5.getController("rewindToStart");
            setStandaloneControllerVisible(rewindButton, true);
            if (rewindButton != null) {
                rewindButton.setPosition(rewindButtonX, footerY);
            }
            
            if (pausePlayButton != null) {
                pausePlayButton.setVisible(true);
                pausePlayButton.setPosition(pausePlayButtonX, footerY);
            }
            
            if (playbackModeButton != null) {
                playbackModeButton.setVisible(true);
                playbackModeButton.setPosition(playbackModeButtonX, footerY);
            }
            
            footerY += FOOTER_ROW_STEP;
            
            int exportButtonX = x + FOOTER_GLITCH_BUTTON_W + FOOTER_BUTTON_GAP;
            int stopExportButtonX = exportButtonX + FOOTER_EXPORT_BUTTON_W + FOOTER_BUTTON_GAP;
            
            if (glitchToggleButton != null) {
                glitchToggleButton.setVisible(true);
                glitchToggleButton.setPosition(x, footerY);
            }
            if (interactiveExportButton != null) {
                interactiveExportButton.setVisible(true);
                interactiveExportButton.setPosition(exportButtonX, footerY);
            }
            if (stopExportButton != null) {
                stopExportButton.setVisible(true);
                stopExportButton.setPosition(stopExportButtonX, footerY);
            }
            
            footerY += FOOTER_ROW_STEP;
            
            if (processVideoButton != null) {
                processVideoButton.setVisible(true);
                processVideoButton.setPosition(x, footerY);
            }
        }
        
        private void updateAdvancedToggle() {
            if (advancedGuiToggle != null) {
                advancedGuiToggle.setBroadcast(false);
                advancedGuiToggle.setValue(!compactGuiMode);
                advancedGuiToggle.setBroadcast(true);
            }
        }
        
        private void setStatusMessage(String message) {
            if (message == null || message.isBlank()) {
                statusMessage = "Status: ready";
                return;
            }
            
            statusMessage = message;
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
            if (name.equals("useTrackingTear"))
                return useTrackingTear;
            if (name.equals("useHeadSwitchBand"))
                return useHeadSwitchBand;
            if (name.equals("useChromaDrift"))
                return useChromaDrift;
            if (name.equals("useScanlineWobble"))
                return useScanlineWobble;
            if (name.equals("useVerticalSmear"))
                return useVerticalSmear;
            if (name.equals("useColumnDrift"))
                return useColumnDrift;
            return false;
        }
        
        public void controlEvent(ControlEvent e) {
            if (isFullProcessExportActive()) {
                return;
            }
            
            String controllerName = e.getController().getName();
            if (controllerName.startsWith("preset_")) {
                int index = Integer.parseInt(controllerName.substring("preset_".length()));
                if (index >= 0 && index < PRESET_NAMES.length) {
                    applyPreset(PRESET_NAMES[index]);
                }
                return;
            }
            
            if ("advancedGui".equals(controllerName)) {
                boolean nextAdvanced = e.getController().getValue() > 0.5f;
                if (compactGuiMode == nextAdvanced) {
                    compactGuiMode = !nextAdvanced;
                    guiScrollOffset = 0;
                    refreshGuiLayout();
                }
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
                setStatusMessage("Status: rewound " + currentVideoName);
            } else {
                startPlayback();
                setStatusMessage("Status: previewing " + currentVideoName);
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
            
            setStatusMessage("Status: playback mode " + (loopPlayback ? "looping" : "play once"));
            updatePlaybackModeButton();
        }
        
        public void toggleGlitching() {
            if (isFullProcessExportActive()) {
                return;
            }
            
            glitchEnabled = !glitchEnabled;
            setStatusMessage("Status: glitching " + (glitchEnabled ? "enabled" : "disabled"));
            updateGlitchButton();
        }
        
        public void processVideo() {
            promptForProcessOutput();
        }
        
        private void promptForVideo() {
            if (selectingVideo || selectingProcessOutput || isFullProcessExportActive())
                return;
            selectingVideo = true;
            selectInput("Select a video file:", "videoSelected");
        }
        
        private void promptForProcessOutput() {
            if (exporting || !movieReady || video == null || selectingProcessOutput) {
                return;
            }
            
            if (launchOptions.autoProcess()) {
                startFullProcessExport();
                return;
            }
            
            selectingProcessOutput = true;
            setStatusMessage("Status: choose where to save the processed video");
            selectOutput("Save processed video as:", "processOutputSelected", new File(exportFilename));
        }
        
        public void processOutputSelected(File selection) {
            selectingProcessOutput = false;
            
            if (selection == null) {
                setStatusMessage("Status: full-process export cancelled");
                return;
            }
            
            exportFilename = normalizeExportOutputPath(selection);
            startFullProcessExport();
        }
        
        public void videoSelected(File selection) {
            selectingVideo = false;
            
            if (selection == null) {
                setStatusMessage("Status: no video selected");
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
                setStatusMessage("Status: loading " + currentVideoName + " via " + sourceLabel);
            } catch (RuntimeException exception) {
                video = null;
                println("Failed to load video via " + sourceLabel + ": " + exception.getMessage());
                if (!triedVideoUriFallback && currentVideoFile != null && "path".equals(sourceLabel)) {
                    triedVideoUriFallback = true;
                    startMovie(currentVideoFile.toURI().toString(), "file URI");
                } else {
                    setStatusMessage("Status: failed to load " + currentVideoName);
                }
            }
        }
        
        private String makeExportFilename(String sourceName) {
            return VideoGlitcherLogic.makeExportFilename(sourceName);
        }
        
        private String normalizeExportOutputPath(File selection) {
            return VideoGlitcherLogic.ensureMp4Extension(selection.getAbsolutePath());
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
            
            int maxScroll = maxGuiScroll();
            if (maxScroll > 0) {
                float trackX = panelX + panelW - 10;
                float trackY = guiViewportTop;
                float trackH = max(0, guiViewportBottom - guiViewportTop);
                float thumbH = max(32, trackH * trackH / max(1, guiContentHeight));
                float thumbY = trackY + map(guiScrollOffset, 0, maxScroll, 0, max(0, trackH - thumbH));
                
                noStroke();
                fill(255, 20);
                rect(trackX, trackY, 4, trackH, 2);
                
                fill(255, 90);
                rect(trackX, thumbY, 4, thumbH, 2);
            }
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
        
        private String fitTextToWidth(String text, float maxWidth) {
            if (text == null || text.isEmpty()) {
                return "";
            }
            
            if (textWidth(text) <= maxWidth) {
                return text;
            }
            
            String ellipsis = "...";
            int end = text.length();
            while (end > 0 && textWidth(text.substring(0, end) + ellipsis) > maxWidth) {
                end--;
            }
            
            return end <= 0 ? ellipsis : text.substring(0, end) + ellipsis;
        }
        
        private void drawHUD() {
            float hudX = HUD_MARGIN;
            float hudY = hudTopY();
            float hudW = width - HUD_MARGIN * 2.0f;
            
            fill(0, 192);
            noStroke();
            rect(hudX, hudY, hudW, HUD_HEIGHT, 8);
            
            fill(0);
            textSize(13);
            textAlign(LEFT, TOP);
            
            String mode = glitchActive ? "GLITCH" : "CALM";
            String exp = exportMode == ExportMode.INTERACTIVE ? "INTERACTIVE EXPORT"
            : exportMode == ExportMode.FULL_PROCESS ? "FULL PROCESS"
            : "PREVIEW";
            String gui = showGUI ? "GUI ON" : "GUI OFF";
            String playback = paused ? "PAUSED" : "PLAYING";
            String playbackMode = loopPlayback ? "LOOP" : "ONCE";
            String summary = "Video: " + currentVideoName
            + " | Mode: " + mode
            + " | Playback: " + playback
            + " | Repeat: " + playbackMode
            + " | Output: " + exp
            + " | " + gui
            + " | SPACE play/pause | G glitch | L load | F freeze | H hud | U gui | E export | P process";
            
            text(fitTextToWidth(statusMessage, hudW - 16), hudX + 8, hudY + 6);
            text(fitTextToWidth(summary, hudW - 16), hudX + 8, hudY + 23);
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
                    int layers = (int) random(2, 7 + settings.retroAmount() * 2.0f);
                    
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
                        else if (effect == 10)
                            trackingTear();
                        else if (effect == 11)
                            headSwitchBand();
                        else if (effect == 12)
                            chromaDrift();
                        else if (effect == 13)
                            scanlineWobble();
                        else if (effect == 14)
                            verticalSmear();
                        else if (effect == 15)
                            columnDrift();
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
                    if (settings.useTrackingTear())
                        choices.append(10);
                    if (settings.useHeadSwitchBand())
                        choices.append(11);
                    if (settings.useChromaDrift())
                        choices.append(12);
                    if (settings.useScanlineWobble())
                        choices.append(13);
                    if (settings.useVerticalSmear())
                        choices.append(14);
                    if (settings.useColumnDrift())
                        choices.append(15);
                    
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
                
                private void trackingTear() {
                    RenderSettings settings = activeRenderSettings();
                    int x0 = (int) drawX;
                    int y0 = (int) drawY;
                    int w0 = (int) drawW;
                    int h0 = (int) drawH;
                    float amplitude = (28.0f + 220.0f * settings.trackingDrift()) * (0.35f + settings.retroAmount());
                    int bands = max(1, 1 + (int) (settings.retroAmount() * 4.0f));
                    
                    for (int i = 0; i < bands; i++) {
                        int bandH = max(4, (int) (h0 * random(0.018f, 0.090f) * (0.60f + settings.retroAmount())));
                        int y = y0 + (int) random(max(1, h0 - bandH));
                        int dx = (int) random(-amplitude, amplitude);
                        copy(x0, y, w0, bandH, x0 + dx, y, w0, bandH);
                        
                        stroke(255, random(26, 84));
                        line(x0, y, x0 + w0, y);
                        if (random(1) < 0.55f) {
                            stroke(0, random(18, 56));
                            line(x0, y + bandH, x0 + w0, y + bandH);
                        }
                    }
                }
                
                private void headSwitchBand() {
                    RenderSettings settings = activeRenderSettings();
                    int x0 = (int) drawX;
                    int y0 = (int) drawY;
                    int w0 = (int) drawW;
                    int h0 = (int) drawH;
                    int bandH = max(10, (int) (h0 * (0.04f + settings.headSwitchHeight() * 0.22f)));
                    int bandY = y0 + h0 - bandH;
                    float offsetRange = 24.0f + 180.0f * settings.retroJitter();
                    
                    noStroke();
                    fill(0, 24 + settings.retroAmount() * 70.0f);
                    rect(x0, bandY, w0, bandH);
                    
                    for (int y = bandY; y < bandY + bandH; y += 2) {
                        int stripH = min(2, bandY + bandH - y);
                        int dx = (int) random(-offsetRange, offsetRange);
                        copy(x0, y, w0, stripH, x0 + dx, y, w0, stripH);
                        
                        if (random(1) < 0.38f) {
                            stroke(255, random(20, 70));
                            line(x0, y, x0 + w0, y);
                        }
                    }
                    
                    for (int i = 0; i < max(1, (int) (settings.retroAmount() * 6.0f)); i++) {
                        noStroke();
                        fill(random(255), random(255), random(255), random(12, 36));
                        rect(x0 + random(w0), bandY + random(bandH), random(18, 72), random(1, 4));
                    }
                }
                
                private void chromaDrift() {
                    RenderSettings settings = activeRenderSettings();
                    float drift = (3.0f + 24.0f * settings.chromaOffset()) * (0.50f + settings.retroAmount());
                    float verticalJitter = 1.0f + settings.retroJitter() * 5.0f;
                    
                    blendMode(ADD);
                    
                    tint(255, 70, 40, random(26, 82));
                    image(video, drawX - drift, drawY + random(-verticalJitter, verticalJitter), drawW, drawH);
                    
                    tint(40, 170, 255, random(28, 92));
                    image(video, drawX + drift * 0.75f, drawY + random(-verticalJitter, verticalJitter), drawW, drawH);
                    
                    noTint();
                    blendMode(BLEND);
                }
                
                private void scanlineWobble() {
                    RenderSettings settings = activeRenderSettings();
                    int x0 = (int) drawX;
                    int y0 = (int) drawY;
                    int w0 = (int) drawW;
                    int h0 = (int) drawH;
                    float amplitude = 1.5f + settings.retroAmount() * 14.0f;
                    float phaseScale = 0.035f + settings.chromaOffset() * 0.090f;
                    float jitter = 1.0f + settings.retroJitter() * 5.0f;
                    
                    for (int y = y0; y < y0 + h0; y += 2) {
                        float phase = frameCount * 0.22f + (y - y0) * phaseScale;
                        int dx = (int) (sin(phase) * amplitude + random(-jitter, jitter));
                        int stripH = min(2, y0 + h0 - y);
                        copy(x0, y, w0, stripH, x0 + dx, y, w0, stripH);
                    }
                }
                
                private void verticalSmear() {
                    RenderSettings settings = activeRenderSettings();
                    int x0 = (int) drawX;
                    int y0 = (int) drawY;
                    int w0 = (int) drawW;
                    int h0 = (int) drawH;
                    int columns = max(1, 1 + (int) (settings.smearStrength() * 7.0f));
                    
                    for (int i = 0; i < columns; i++) {
                        int colW = max(2, (int) random(2, 10 + settings.smearStrength() * 18.0f));
                        int sx = x0 + (int) random(max(1, w0 - colW));
                        int sy = y0 + (int) random(max(1.0f, h0 * 0.35f));
                        int sampleH = max(8, (int) random(8, max(9.0f, h0 * 0.18f)));
                        sampleH = min(sampleH, y0 + h0 - sy);
                        int stretchedH = min(y0 + h0 - sy, max(sampleH, (int) (sampleH * (2.0f + settings.smearStrength() * 4.0f))));
                        
                        copy(sx, sy, colW, sampleH, sx, sy, colW, stretchedH);
                        
                        noStroke();
                        fill(255, random(8, 28));
                        rect(sx, sy, colW, stretchedH);
                    }
                }
                
                private void columnDrift() {
                    RenderSettings settings = activeRenderSettings();
                    int x0 = (int) drawX;
                    int y0 = (int) drawY;
                    int w0 = (int) drawW;
                    int h0 = (int) drawH;
                    int columns = max(2, 2 + (int) (settings.columnDriftAmount() * 10.0f));
                    float offsetRange = (10.0f + 90.0f * settings.columnDriftAmount()) * (0.45f + settings.retroAmount());
                    
                    for (int i = 0; i < columns; i++) {
                        int colW = max(3, (int) random(4, 12 + settings.columnDriftAmount() * 20.0f));
                        int sx = x0 + (int) random(max(1, w0 - colW));
                        int dy = (int) random(-offsetRange, offsetRange);
                        
                        copy(sx, y0, colW, h0, sx, y0 + dy, colW, h0);
                        
                        if (random(1) < 0.35f) {
                            noStroke();
                            fill(0, random(8, 24));
                            rect(sx, y0, colW, h0);
                        }
                    }
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
                    if (RANDOM_PRESET_NAME.equals(name)) {
                        applyRandomPreset();
                        return;
                    }
                    
                    VideoGlitcherLogic.PresetValues preset = VideoGlitcherLogic.presetForName(name);
                    if (preset == null) {
                        return;
                    }
                    
                    activePresetName = name;
                    
                    glitchIntensity = preset.glitchIntensity();
                    glitchFrequency = preset.glitchFrequency();
                    episodeMinFrames = preset.episodeMinFrames();
                    episodeMaxFrames = preset.episodeMaxFrames();
                    calmMinFrames = preset.calmMinFrames();
                    calmMaxFrames = preset.calmMaxFrames();
                    subtleDamageChance = preset.subtleDamageChance();
                    burstChance = preset.burstChance();
                    retroAmount = preset.retroAmount();
                    retroJitter = preset.retroJitter();
                    trackingDrift = preset.trackingDrift();
                    headSwitchHeight = preset.headSwitchHeight();
                    chromaOffset = preset.chromaOffset();
                    smearStrength = preset.smearStrength();
                    columnDriftAmount = preset.columnDriftAmount();
                    
                    useTrackingTear = preset.useTrackingTear();
                    useHeadSwitchBand = preset.useHeadSwitchBand();
                    useChromaDrift = preset.useChromaDrift();
                    useScanlineWobble = preset.useScanlineWobble();
                    useVerticalSmear = preset.useVerticalSmear();
                    useColumnDrift = preset.useColumnDrift();
                    
                    applyPresetEffectDefaults(name);
                    
                    syncGuiValues();
                }
                
                private void applyRandomPreset() {
                    activePresetName = RANDOM_PRESET_NAME;
                    
                    glitchIntensity = random(0.1f, 2.0f);
                    glitchFrequency = random(0.0f, 1.0f);
                    episodeMinFrames = floor(random(1, 31));
                    episodeMaxFrames = floor(random(episodeMinFrames, 41));
                    calmMinFrames = floor(random(1, 81));
                    calmMaxFrames = floor(random(calmMinFrames, 121));
                    subtleDamageChance = random(0.0f, 1.0f);
                    burstChance = random(0.0f, 1.0f);
                    retroAmount = random(0.0f, 1.0f);
                    retroJitter = random(0.0f, 1.0f);
                    trackingDrift = random(0.0f, 1.0f);
                    headSwitchHeight = random(0.0f, 1.0f);
                    chromaOffset = random(0.0f, 1.0f);
                    smearStrength = random(0.0f, 1.0f);
                    columnDriftAmount = random(0.0f, 1.0f);
                    
                    useRGBSplit = randomToggle();
                    useSlices = randomToggle();
                    useBlocks = randomToggle();
                    useBars = randomToggle();
                    useDropouts = randomToggle();
                    useGhosts = randomToggle();
                    useFreeze = randomToggle();
                    useScanBursts = randomToggle();
                    useFlash = randomToggle();
                    useMicroJitter = randomToggle();
                    useZoomWobble = randomToggle();
                    useTrackingTear = randomToggle();
                    useHeadSwitchBand = randomToggle();
                    useChromaDrift = randomToggle();
                    useScanlineWobble = randomToggle();
                    useVerticalSmear = randomToggle();
                    useColumnDrift = randomToggle();
                    
                    if (!hasAnyEffectEnabled()) {
                        enableRandomEffect();
                    }
                    
                    syncGuiValues();
                    setStatusMessage("Status: random preset generated");
                }
                
                private boolean randomToggle() {
                    return random(1) < 0.5f;
                }
                
                private boolean hasAnyEffectEnabled() {
                    return useRGBSplit
                    || useSlices
                    || useBlocks
                    || useBars
                    || useDropouts
                    || useGhosts
                    || useFreeze
                    || useScanBursts
                    || useFlash
                    || useMicroJitter
                    || useZoomWobble
                    || useTrackingTear
                    || useHeadSwitchBand
                    || useChromaDrift
                    || useScanlineWobble
                    || useVerticalSmear
                    || useColumnDrift;
                }
                
                private void enableRandomEffect() {
                    int effectIndex = (int) random(17);
                    
                    switch (effectIndex) {
                        case 0:
                        useRGBSplit = true;
                        break;
                        case 1:
                        useSlices = true;
                        break;
                        case 2:
                        useBlocks = true;
                        break;
                        case 3:
                        useBars = true;
                        break;
                        case 4:
                        useDropouts = true;
                        break;
                        case 5:
                        useGhosts = true;
                        break;
                        case 6:
                        useFreeze = true;
                        break;
                        case 7:
                        useScanBursts = true;
                        break;
                        case 8:
                        useFlash = true;
                        break;
                        case 9:
                        useMicroJitter = true;
                        break;
                        case 10:
                        useZoomWobble = true;
                        break;
                        case 11:
                        useTrackingTear = true;
                        break;
                        case 12:
                        useHeadSwitchBand = true;
                        break;
                        case 13:
                        useChromaDrift = true;
                        break;
                        case 14:
                        useScanlineWobble = true;
                        break;
                        case 15:
                        useVerticalSmear = true;
                        break;
                        default:
                        useColumnDrift = true;
                        break;
                    }
                }
                
                private void applyPresetEffectDefaults(String name) {
                    useRGBSplit = true;
                    useSlices = true;
                    useBlocks = true;
                    useBars = true;
                    useDropouts = true;
                    useGhosts = true;
                    useFreeze = true;
                    useScanBursts = true;
                    useFlash = true;
                    useMicroJitter = true;
                    useZoomWobble = true;
                    
                    if ("VHS Decay".equals(name)) {
                        useBlocks = false;
                        useBars = false;
                        useFreeze = false;
                        useFlash = false;
                        return;
                    }
                    
                    if ("Old Digicam".equals(name)) {
                        useSlices = false;
                        useBlocks = false;
                        useBars = false;
                        useDropouts = false;
                        useGhosts = false;
                        useFreeze = false;
                        useScanBursts = false;
                        useFlash = false;
                        useZoomWobble = false;
                    }
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
                    cp5.getController("retroAmount").setValue(retroAmount);
                    cp5.getController("retroJitter").setValue(retroJitter);
                    cp5.getController("trackingDrift").setValue(trackingDrift);
                    cp5.getController("headSwitchHeight").setValue(headSwitchHeight);
                    cp5.getController("chromaOffset").setValue(chromaOffset);
                    cp5.getController("smearStrength").setValue(smearStrength);
                    cp5.getController("columnDriftAmount").setValue(columnDriftAmount);
                    
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
                    cp5.getController("useTrackingTear").setValue(useTrackingTear ? 1 : 0);
                    cp5.getController("useHeadSwitchBand").setValue(useHeadSwitchBand ? 1 : 0);
                    cp5.getController("useChromaDrift").setValue(useChromaDrift ? 1 : 0);
                    cp5.getController("useScanlineWobble").setValue(useScanlineWobble ? 1 : 0);
                    cp5.getController("useVerticalSmear").setValue(useVerticalSmear ? 1 : 0);
                    cp5.getController("useColumnDrift").setValue(useColumnDrift ? 1 : 0);
                    updateAdvancedToggle();
                    updatePresetButtons();
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
                    setStatusMessage("Status: interactive export to " + exportFilename);
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
                    setStatusMessage("Status: processing full video to " + exportFilename);
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
                        setStatusMessage("Status: full-process export finished");
                        println("Full-process export finished");
                    } else {
                        setStatusMessage("Status: interactive export finished");
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
                    setStatusMessage("Status: export failed");
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
                        setStatusMessage("Status: paused " + currentVideoName);
                    } else {
                        pausedFrame = null;
                        if (isAtPlaybackEnd()) {
                            video.jump(0);
                        }
                        startPlayback();
                        setStatusMessage("Status: previewing " + currentVideoName);
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
                            setStatusMessage("Status: interactive export reached end, click Export Stop");
                        }
                        return;
                    }
                    
                    setStatusMessage("Status: finished " + currentVideoName);
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
                    paused = true;
                    movieReady = false;
                    pausedFrame = null;
                    frozenFrame = null;
                    previousFrame = null;
                    releaseVideo();
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
                        useZoomWobble,
                        retroAmount,
                        retroJitter,
                        trackingDrift,
                        headSwitchHeight,
                        chromaOffset,
                        smearStrength,
                        columnDriftAmount,
                        useTrackingTear,
                        useHeadSwitchBand,
                        useChromaDrift,
                        useScanlineWobble,
                        useVerticalSmear,
                        useColumnDrift);
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
                        boolean useZoomWobble,
                        float retroAmount,
                        float retroJitter,
                        float trackingDrift,
                        float headSwitchHeight,
                        float chromaOffset,
                        float smearStrength,
                        float columnDriftAmount,
                        boolean useTrackingTear,
                        boolean useHeadSwitchBand,
                        boolean useChromaDrift,
                        boolean useScanlineWobble,
                        boolean useVerticalSmear,
                        boolean useColumnDrift) {
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
                                            case "vhs decay":
                                            case "vhs-decay":
                                            case "vhs_decay":
                                            return "VHS Decay";
                                            case "old digicam":
                                            case "old-digicam":
                                            case "old_digicam":
                                            return "Old Digicam";
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
                            