import processing.video.*;
import controlP5.*;
import com.hamoid.*;
import java.io.File;

Movie video;
ControlP5 cp5;
VideoExport videoExport;

// ---------- playback / export ----------
boolean movieReady = false;
boolean exporting = false;
String exportFilename = "glitched_export.mp4";
String currentVideoPath = "";
String currentVideoName = "No video loaded";

// ---------- display ----------
int FPS = 24;
float drawX = 0;
float drawY = 0;
float drawW = 0;
float drawH = 0;

// ---------- general ----------
boolean showHUD = true;
boolean showGUI = true;
boolean freezeManual = false;
boolean selectingVideo = false;

// ---------- preset / behavior ----------
float glitchIntensity = 0.85;
float glitchFrequency = 0.55;
float episodeMinFrames = 4;
float episodeMaxFrames = 14;
float calmMinFrames = 10;
float calmMaxFrames = 40;

float subtleDamageChance = 0.25;
float burstChance = 0.16;

boolean useRGBSplit = true;
boolean useSlices = true;
boolean useBlocks = true;
boolean useBars = true;
boolean useDropouts = true;
boolean useGhosts = true;
boolean useFreeze = true;
boolean useScanBursts = true;
boolean useFlash = true;
boolean useMicroJitter = true;
boolean useZoomWobble = true;

// ---------- runtime glitch episode ----------
boolean glitchActive = false;
int glitchFramesLeft = 0;
int calmFramesLeft = 0;

// ---------- frame memory ----------
PImage frozenFrame = null;
int freezeFramesLeft = 0;
PImage previousFrame = null;

// ---------- camera wobble ----------
float zoomJitter = 1.0;
float offsetX = 0;
float offsetY = 0;

// ---------- GUI ----------
DropdownList presetList;
Textlabel statusLabel;

int panelX = 12;
int panelY = 12;
int panelW = 420;
int panelH = 860;

int guiX = panelX + 14;
int guiY = panelY + 14;
int sliderW = 220;
int sliderH = 16;
int labelX = guiX + sliderW + 16;
int rowGap = 24;

void setup() {
  fullScreen(P2D);
  pixelDensity(1);
  frameRate(FPS);
  noSmooth();
  surface.setTitle("video_glitcher");

  setupGui();
  applyPreset("Cinematic");
  calmFramesLeft = int(random(calmMinFrames, calmMaxFrames + 1));

  promptForVideo();
}

void setupGui() {
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

  addSliderRow("glitchIntensity", x, y, sliderW, sliderH, 0.1, 2.0, glitchIntensity, "Intensity"); y += rowGap;
  addSliderRow("glitchFrequency", x, y, sliderW, sliderH, 0.0, 1.0, glitchFrequency, "Frequency"); y += rowGap;
  addSliderRow("episodeMinFrames", x, y, sliderW, sliderH, 1, 30, episodeMinFrames, "Episode Min"); y += rowGap;
  addSliderRow("episodeMaxFrames", x, y, sliderW, sliderH, 1, 40, episodeMaxFrames, "Episode Max"); y += rowGap;
  addSliderRow("calmMinFrames", x, y, sliderW, sliderH, 1, 80, calmMinFrames, "Calm Min"); y += rowGap;
  addSliderRow("calmMaxFrames", x, y, sliderW, sliderH, 1, 120, calmMaxFrames, "Calm Max"); y += rowGap;
  addSliderRow("subtleDamageChance", x, y, sliderW, sliderH, 0.0, 1.0, subtleDamageChance, "Subtle Damage"); y += rowGap;
  addSliderRow("burstChance", x, y, sliderW, sliderH, 0.0, 1.0, burstChance, "Burst Chance"); y += 30;

  addToggleRow("useRGBSplit",    "RGB Split",    x, y); y += 22;
  addToggleRow("useSlices",      "Slices",       x, y); y += 22;
  addToggleRow("useBlocks",      "Blocks",       x, y); y += 22;
  addToggleRow("useBars",        "Bars",         x, y); y += 22;
  addToggleRow("useDropouts",    "Dropouts",     x, y); y += 22;
  addToggleRow("useGhosts",      "Ghosts",       x, y); y += 22;
  addToggleRow("useFreeze",      "Freeze",       x, y); y += 22;
  addToggleRow("useScanBursts",  "Scan Bursts",  x, y); y += 22;
  addToggleRow("useFlash",       "Flash",        x, y); y += 22;
  addToggleRow("useMicroJitter", "Micro Jitter", x, y); y += 22;
  addToggleRow("useZoomWobble",  "Zoom Wobble",  x, y); y += 30;

  Button bLoad = cp5.addButton("loadVideo")
    .setPosition(x, y)
    .setSize(120, 30)
    .setLabel("Load Video");
  bLoad.getCaptionLabel().align(ControlP5.CENTER, ControlP5.CENTER);

  Button b1 = cp5.addButton("startExport")
    .setPosition(x + 132, y)
    .setSize(120, 30)
    .setLabel("Export Start");
  b1.getCaptionLabel().align(ControlP5.CENTER, ControlP5.CENTER);

  Button b2 = cp5.addButton("stopExport")
    .setPosition(x + 264, y)
    .setSize(120, 30)
    .setLabel("Export Stop");
  b2.getCaptionLabel().align(ControlP5.CENTER, ControlP5.CENTER);

  y += 42;

  statusLabel = cp5.addTextlabel("status")
    .setPosition(x, y)
    .setText("Status: no video loaded");
}

void addSliderRow(String name, int x, int y, int w, int h, float minV, float maxV, float value, String label) {
  Slider s = cp5.addSlider(name)
    .setPosition(x, y)
    .setSize(w, h)
    .setRange(minV, maxV)
    .setValue(value);

  s.getCaptionLabel().setVisible(false);
  s.getValueLabel().align(ControlP5.RIGHT_OUTSIDE, ControlP5.CENTER).setPaddingX(8);

  cp5.addTextlabel("lbl_" + name)
    .setPosition(labelX, y + 1)
    .setText(label);
}

void addToggleRow(String name, String label, int x, int y) {
  Toggle t = cp5.addToggle(name)
    .setPosition(x, y)
    .setSize(18, 18)
    .setValue(getToggleValue(name));

  t.getCaptionLabel().setVisible(false);

  cp5.addTextlabel("lbl_" + name)
    .setPosition(x + 28, y + 1)
    .setText(label);
}

boolean getToggleValue(String name) {
  if (name.equals("useRGBSplit")) return useRGBSplit;
  if (name.equals("useSlices")) return useSlices;
  if (name.equals("useBlocks")) return useBlocks;
  if (name.equals("useBars")) return useBars;
  if (name.equals("useDropouts")) return useDropouts;
  if (name.equals("useGhosts")) return useGhosts;
  if (name.equals("useFreeze")) return useFreeze;
  if (name.equals("useScanBursts")) return useScanBursts;
  if (name.equals("useFlash")) return useFlash;
  if (name.equals("useMicroJitter")) return useMicroJitter;
  if (name.equals("useZoomWobble")) return useZoomWobble;
  return false;
}

void promptForVideo() {
  if (selectingVideo) return;
  selectingVideo = true;
  selectInput("Select a video file:", "videoSelected");
}

void videoSelected(File selection) {
  selectingVideo = false;

  if (selection == null) {
    statusLabel.setText("Status: no video selected");
    return;
  }

  loadVideoFile(selection.getAbsolutePath());
}

void loadVideoFile(String path) {
  stopExport();

  if (video != null) {
    try {
      video.stop();
    } catch (Exception e) {
    }
    video = null;
  }

  movieReady = false;
  frozenFrame = null;
  previousFrame = null;
  freezeFramesLeft = 0;
  freezeManual = false;

  currentVideoPath = path;
  currentVideoName = new File(path).getName();
  exportFilename = makeExportFilename(currentVideoName);

  println("Loading video: " + currentVideoPath);

  video = new Movie(this, currentVideoPath);
  video.loop();

  statusLabel.setText("Status: loading " + currentVideoName);
}

String makeExportFilename(String sourceName) {
  int dot = sourceName.lastIndexOf('.');
  String base = (dot > 0) ? sourceName.substring(0, dot) : sourceName;
  return base + "_glitched.mp4";
}

void movieEvent(Movie m) {
  m.read();

  if (!movieReady) {
    movieReady = true;
    computeVideoFit();
    statusLabel.setText("Status: previewing " + currentVideoName);
  }
}

void computeVideoFit() {
  if (video == null || video.width <= 0 || video.height <= 0) return;

  float sx = (float) width / (float) video.width;
  float sy = (float) height / (float) video.height;
  float scaleFactor = min(sx, sy);

  if (scaleFactor > 1.0) {
    scaleFactor = 1.0;
  }

  drawW = video.width * scaleFactor;
  drawH = video.height * scaleFactor;
  drawX = (width - drawW) * 0.5;
  drawY = (height - drawH) * 0.5;
}

void draw() {
  background(0);

  normalizeRanges();
  updateGlitchState();
  updateCameraJitter();

  pushMatrix();
  translate(offsetX, offsetY);
  scale(zoomJitter);

  if (!movieReady || video == null) {
    drawNoVideoScreen();
  } else {
    if ((freezeFramesLeft > 0 || freezeManual) && frozenFrame != null) {
      image(frozenFrame, drawX, drawY, drawW, drawH);
      if (!freezeManual && freezeFramesLeft > 0) freezeFramesLeft--;
    } else {
      image(video, drawX, drawY, drawW, drawH);
    }

    if (glitchActive) {
      applyRandomGlitchStack();
    } else {
      applySubtleFilmDamage();
    }
  }

  popMatrix();

  previousFrame = get();

  if (exporting && videoExport != null && movieReady) {
    videoExport.saveFrame();
  }

  if (showHUD) drawHUD();
  if (showGUI) drawGui();
}

void drawNoVideoScreen() {
  fill(255);
  textAlign(CENTER, CENTER);
  textSize(28);
  text("No video loaded", width * 0.5, height * 0.5 - 20);

  textSize(18);
  text("Click 'Load Video' or press L", width * 0.5, height * 0.5 + 20);

  if (selectingVideo) {
    text("File dialog open...", width * 0.5, height * 0.5 + 50);
  }
}

void drawGuiPanel() {
  noStroke();
  fill(0, 175);
  rect(panelX, panelY, panelW, panelH, 14);

  stroke(255, 35);
  noFill();
  rect(panelX, panelY, panelW, panelH, 14);
}

void drawGui() {
  hint(DISABLE_DEPTH_TEST);
  drawGuiPanel();
  cp5.draw();
  hint(ENABLE_DEPTH_TEST);
}

void normalizeRanges() {
  if (episodeMaxFrames < episodeMinFrames) episodeMaxFrames = episodeMinFrames;
  if (calmMaxFrames < calmMinFrames) calmMaxFrames = calmMinFrames;
}

void updateGlitchState() {
  if (glitchActive) {
    glitchFramesLeft--;
    if (glitchFramesLeft <= 0) {
      glitchActive = false;
      calmFramesLeft = int(random(calmMinFrames, calmMaxFrames + 1));
    }
  } else {
    calmFramesLeft--;
    float spontaneousChance = 0.01 + glitchFrequency * 0.08;
    if (calmFramesLeft <= 0 || random(1) < spontaneousChance) {
      glitchActive = true;
      glitchFramesLeft = int(random(episodeMinFrames, episodeMaxFrames + 1));
    }
  }
}

void updateCameraJitter() {
  if (glitchActive && useZoomWobble && random(1) < 0.35) {
    zoomJitter = random(0.985, 1.02);
    offsetX = random(-8, 8) * glitchIntensity;
    offsetY = random(-5, 5) * glitchIntensity;
  } else {
    zoomJitter = 1.0;
    offsetX = 0;
    offsetY = 0;
  }
}

void applyRandomGlitchStack() {
  int layers = int(random(2, 6));

  for (int i = 0; i < layers; i++) {
    int effect = pickEnabledEffect();
    if (effect == -1) break;

    if (effect == 0) heavyRGBSplit();
    else if (effect == 1) horizontalSlices();
    else if (effect == 2) blockGlitch();
    else if (effect == 3) digitalBars();
    else if (effect == 4) dropoutBands();
    else if (effect == 5) ghostFrame();
    else if (effect == 6) microJitterCopies();
    else if (effect == 7) scanlineBurst();
    else if (effect == 8) whiteFlashOrBlackout();
    else if (effect == 9) freezeStutter();
  }

  if (random(1) < 0.80) scanlines();
  if (random(1) < 0.60) flickerNoise();
  if (random(1) < burstChance) burstGlitch();
}

int pickEnabledEffect() {
  IntList choices = new IntList();

  if (useRGBSplit) choices.append(0);
  if (useSlices) choices.append(1);
  if (useBlocks) choices.append(2);
  if (useBars) choices.append(3);
  if (useDropouts) choices.append(4);
  if (useGhosts) choices.append(5);
  if (useMicroJitter) choices.append(6);
  if (useScanBursts) choices.append(7);
  if (useFlash) choices.append(8);
  if (useFreeze) choices.append(9);

  if (choices.size() == 0) return -1;
  return choices.get(int(random(choices.size())));
}

void applySubtleFilmDamage() {
  if (random(1) < subtleDamageChance && useRGBSplit) subtleRGBMisalign();
  if (random(1) < subtleDamageChance * 0.7 && useDropouts) faintBanding();

  if (random(1) < subtleDamageChance) {
    stroke(0, 25);
    for (int y = 0; y < height; y += 3) {
      line(0, y, width, y);
    }
  }
}

void heavyRGBSplit() {
  blendMode(ADD);

  float amt = map(glitchIntensity, 0, 2.0, 0, 26);

  tint(255, 0, 0, random(90, 170));
  image(video, drawX + random(-amt, amt), drawY + random(-amt * 0.4, amt * 0.4), drawW, drawH);

  tint(0, 255, 80, random(40, 110));
  image(video, drawX + random(-amt * 0.5, amt * 0.5), drawY + random(-amt * 0.2, amt * 0.2), drawW, drawH);

  tint(0, 120, 255, random(90, 170));
  image(video, drawX + random(-amt, amt), drawY + random(-amt * 0.4, amt * 0.4), drawW, drawH);

  noTint();
  blendMode(BLEND);
}

void subtleRGBMisalign() {
  blendMode(ADD);

  tint(255, 0, 0, 28);
  image(video, drawX + random(-4, 4), drawY, drawW, drawH);

  tint(0, 180, 255, 28);
  image(video, drawX + random(-4, 4), drawY, drawW, drawH);

  noTint();
  blendMode(BLEND);
}

void horizontalSlices() {
  int slices = int(random(5, 16) * glitchIntensity);

  int x0 = int(drawX);
  int y0 = int(drawY);
  int w0 = int(drawW);
  int h0 = int(drawH);

  for (int i = 0; i < max(1, slices); i++) {
    int y = y0 + int(random(h0));
    int h = int(random(2, 26));
    h = min(h, y0 + h0 - y);

    int dx = int(random(-180, 180) * glitchIntensity);
    copy(x0, y, w0, h, x0 + dx, y, w0, h);

    if (random(1) < 0.45) {
      stroke(255, random(40, 120));
      line(x0, y, x0 + w0, y);
    }
  }
}

void blockGlitch() {
  int blocks = int(random(8, 26) * glitchIntensity);

  int x0 = int(drawX);
  int y0 = int(drawY);
  int w0 = int(drawW);
  int h0 = int(drawH);

  for (int i = 0; i < max(1, blocks); i++) {
    int bw = int(random(18, min(170, w0)));
    int bh = int(random(8, min(100, h0)));

    int sx = x0 + int(random(w0 - bw));
    int sy = y0 + int(random(h0 - bh));
    int dx = constrain(sx + int(random(-130, 130)), x0, x0 + w0 - bw);
    int dy = constrain(sy + int(random(-45, 45)), y0, y0 + h0 - bh);

    copy(sx, sy, bw, bh, dx, dy, bw, bh);

    if (random(1) < 0.25) {
      noStroke();
      fill(random(255), random(255), random(255), random(12, 65));
      rect(dx, dy, bw, bh);
    }
  }
}

void digitalBars() {
  blendMode(ADD);
  noStroke();

  int bars = int(random(3, 12) * glitchIntensity);

  for (int i = 0; i < max(1, bars); i++) {
    float y = drawY + random(drawH);
    float h = random(2, 18);

    fill(255, 0, 0, random(20, 80));
    rect(drawX + random(-40, 40), y, drawW, h);

    fill(0, 255, 255, random(20, 80));
    rect(drawX + random(-40, 40), y + random(-3, 3), drawW, h);

    if (random(1) < 0.4) {
      fill(0, 255, 0, random(15, 60));
      rect(drawX + random(-25, 25), y, drawW, random(1, 8));
    }
  }

  blendMode(BLEND);
}

void dropoutBands() {
  int bands = int(random(1, 5));

  for (int i = 0; i < bands; i++) {
    float y = drawY + random(drawH);
    float h = random(10, 70);

    noStroke();
    if (random(1) < 0.5) fill(0, random(40, 130));
    else fill(255, random(25, 80));
    rect(drawX, y, drawW, h);
  }
}

void faintBanding() {
  noStroke();
  for (int i = 0; i < 2; i++) {
    fill(255, random(5, 12));
    rect(drawX, drawY + random(drawH), drawW, random(8, 24));
  }
}

void ghostFrame() {
  if (previousFrame == null) return;

  blendMode(ADD);
  tint(255, random(20, 60));
  image(previousFrame, random(-8, 8), random(-4, 4), width, height);
  noTint();
  blendMode(BLEND);
}

void microJitterCopies() {
  int n = int(random(2, 5));

  blendMode(ADD);
  tint(255, random(12, 40));

  for (int i = 0; i < n; i++) {
    image(video, drawX + random(-10, 10), drawY + random(-4, 4), drawW, drawH);
  }

  noTint();
  blendMode(BLEND);
}

void scanlines() {
  int x0 = int(drawX);
  int y0 = int(drawY);
  int w0 = int(drawW);
  int h0 = int(drawH);

  stroke(0, 45);
  for (int y = y0; y < y0 + h0; y += 2) {
    line(x0, y, x0 + w0, y);
  }
}

void scanlineBurst() {
  int x0 = int(drawX);
  int y0 = int(drawY);
  int w0 = int(drawW);
  int h0 = int(drawH);

  stroke(0, random(55, 100));
  int step = int(random(2, 4));
  for (int y = y0; y < y0 + h0; y += step) {
    line(x0, y, x0 + w0, y);
  }
}

void whiteFlashOrBlackout() {
  noStroke();
  if (random(1) < 0.55) fill(255, random(20, 70));
  else fill(0, random(20, 90));
  rect(drawX, drawY, drawW, drawH);
}

void freezeStutter() {
  if (frozenFrame == null || random(1) < 0.45) {
    frozenFrame = get(int(drawX), int(drawY), max(1, int(drawW)), max(1, int(drawH)));
  }
  freezeFramesLeft = int(random(1, 3));
}

void flickerNoise() {
  noStroke();

  if (random(1) < 0.32) {
    fill(255, random(6, 28));
    rect(drawX, drawY, drawW, drawH);
  }

  if (random(1) < 0.20) {
    fill(0, random(6, 22));
    rect(drawX, drawY, drawW, drawH);
  }

  int dots = int(2200 * glitchIntensity);
  for (int i = 0; i < dots; i++) {
    if (random(1) < 0.06) {
      fill(random(255), random(10, 90));
      rect(drawX + random(drawW), drawY + random(drawH), 1, 1);
    }
  }
}

void burstGlitch() {
  int x0 = int(drawX);
  int y0 = int(drawY);
  int w0 = int(drawW);
  int h0 = int(drawH);

  for (int i = 0; i < int(random(10, 26)); i++) {
    int y = y0 + int(random(h0));
    int h = int(random(2, 20));
    h = min(h, y0 + h0 - y);
    int dx = int(random(-260, 260));
    copy(x0, y, w0, h, x0 + dx, y, w0, h);
  }

  for (int i = 0; i < int(random(3, 9)); i++) {
    noStroke();
    if (random(1) < 0.5) fill(255, random(25, 100));
    else fill(random(255), random(255), random(255), random(25, 100));
    rect(drawX, drawY + random(drawH), drawW, random(2, 14));
  }
}

void drawHUD() {
  fill(255, 220);
  noStroke();
  rect(12, height - 36, width - 24, 24, 8);

  fill(0);
  textSize(14);
  textAlign(LEFT, BASELINE);

  String mode = glitchActive ? "GLITCH" : "CALM";
  String exp = exporting ? "EXPORTING" : "PREVIEW";
  String gui = showGUI ? "GUI ON" : "GUI OFF";

  text(
    "Video: " + currentVideoName +
    "   Mode: " + mode +
    "   Output: " + exp +
    "   " + gui +
    "   SPACE gui   L load   F freeze   H HUD   E export",
    20, height - 18
  );
}

void controlEvent(ControlEvent e) {
  if (e.isFrom(presetList)) {
    int v = int(e.getValue());
    if (v == 0) applyPreset("Subtle");
    else if (v == 1) applyPreset("Cinematic");
    else if (v == 2) applyPreset("Corrupted File");
    else if (v == 3) applyPreset("Broken Codec");
    else if (v == 4) applyPreset("Extreme");
  }
}

void applyPreset(String name) {
  if (name.equals("Subtle")) {
    glitchIntensity = 0.45;
    glitchFrequency = 0.22;
    episodeMinFrames = 2;
    episodeMaxFrames = 6;
    calmMinFrames = 18;
    calmMaxFrames = 55;
    subtleDamageChance = 0.18;
    burstChance = 0.04;
  } else if (name.equals("Cinematic")) {
    glitchIntensity = 0.85;
    glitchFrequency = 0.55;
    episodeMinFrames = 4;
    episodeMaxFrames = 14;
    calmMinFrames = 10;
    calmMaxFrames = 40;
    subtleDamageChance = 0.25;
    burstChance = 0.16;
  } else if (name.equals("Corrupted File")) {
    glitchIntensity = 1.05;
    glitchFrequency = 0.72;
    episodeMinFrames = 5;
    episodeMaxFrames = 18;
    calmMinFrames = 6;
    calmMaxFrames = 22;
    subtleDamageChance = 0.32;
    burstChance = 0.24;
  } else if (name.equals("Broken Codec")) {
    glitchIntensity = 1.20;
    glitchFrequency = 0.82;
    episodeMinFrames = 6;
    episodeMaxFrames = 20;
    calmMinFrames = 4;
    calmMaxFrames = 16;
    subtleDamageChance = 0.36;
    burstChance = 0.28;
  } else if (name.equals("Extreme")) {
    glitchIntensity = 1.55;
    glitchFrequency = 0.92;
    episodeMinFrames = 8;
    episodeMaxFrames = 26;
    calmMinFrames = 2;
    calmMaxFrames = 10;
    subtleDamageChance = 0.42;
    burstChance = 0.38;
  }

  syncGuiValues();
}

void syncGuiValues() {
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

void loadVideo() {
  promptForVideo();
}

void startExport() {
  if (exporting || !movieReady || video == null) return;

  video.jump(0);
  video.play();

  videoExport = new VideoExport(this, exportFilename);
  videoExport.setFrameRate(FPS);
  videoExport.startMovie();

  exporting = true;
  statusLabel.setText("Status: exporting to " + exportFilename);
  println("Export started: " + exportFilename);
}

void stopExport() {
  if (!exporting) return;

  exporting = false;
  if (videoExport != null) {
    videoExport.endMovie();
    videoExport = null;
  }

  statusLabel.setText("Status: export finished");
  println("Export finished");
}

void keyPressed() {
  if (keyCode == UP) {
    glitchIntensity = min(2.0, glitchIntensity + 0.1);
    syncGuiValues();
  } else if (keyCode == DOWN) {
    glitchIntensity = max(0.1, glitchIntensity - 0.1);
    syncGuiValues();
  } else if (key == ' ') {
    showGUI = !showGUI;
  } else if (key == 'l' || key == 'L') {
    promptForVideo();
  } else if (key == 'f' || key == 'F') {
    freezeManual = !freezeManual;
    if (freezeManual && frozenFrame == null) {
      frozenFrame = get(int(drawX), int(drawY), max(1, int(drawW)), max(1, int(drawH)));
    }
  } else if (key == 'h' || key == 'H') {
    showHUD = !showHUD;
  } else if (key == 'e' || key == 'E') {
    if (exporting) stopExport();
    else startExport();
  } else if (key == 's' || key == 'S') {
    saveFrame("glitch-######.png");
  }
}
