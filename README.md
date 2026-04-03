# Video Glitcher

Video Glitcher is a Java app built with Processing as a library. It extends `PApplet`, loads a video file, previews it fullscreen, applies glitch effects in real time, and can export the result as an MP4.

## Project Layout

- `src/tom/videoGlitcher/VideoGlitcher.java`: main application source
- `video_glitcher.pde`: original Processing sketch version
- `.vscode/launch.json`: debug launch configs for macOS, Windows, and Linux
- `.vscode/tasks.json`: build and run tasks for VS Code
- `lib/`: bundled Processing, video, ControlP5, and VideoExport libraries
- `bin/`: compiled class output

## Requirements

- Java 17 or newer
- VS Code with Java support
- macOS Apple Silicon uses the bundled native video libraries in `lib/video/library/macos-aarch64`

## Build

Build from the project root with:

```sh
javac -cp "lib/core.jar:lib/controlP5/library/*:lib/video/library/*:lib/videoExport/library/*" -d bin src/tom/videoGlitcher/VideoGlitcher.java
```

In VS Code, run the default build task:

- `Terminal` -> `Run Build Task...`
- Choose `Build VideoGlitcher`

## Run In VS Code

### Task-based run

Use:

- `Terminal` -> `Run Task...`
- Choose `Run VideoGlitcher (macOS Apple Silicon)`

This task builds the app first, then runs it with the correct bundled GStreamer paths.

### Debug launch

Use:

- `Run and Debug`
- Select `Run VideoGlitcher (macOS Apple Silicon)`
- Press `F5`

The debug launch is configured to run the `Build VideoGlitcher` task before startup.

## Run From Terminal

```sh
java -cp "bin:lib/core.jar:lib/controlP5/library/*:lib/video/library/*:lib/videoExport/library/*" \
	-Dgstreamer.library.path="$PWD/lib/video/library/macos-aarch64" \
	-Dgstreamer.plugin.path="$PWD/lib/video/library/macos-aarch64/gstreamer-1.0" \
	tom.videoGlitcher.VideoGlitcher
```

## Controls

- `L`: load a video
- `Space`: show or hide the GUI
- `F`: toggle freeze mode
- `H`: show or hide the HUD
- `E`: start or stop export
- `S`: save a frame as PNG
- `Up`: increase glitch intensity
- `Down`: decrease glitch intensity

## Notes

- The app is written as plain Java, so Processing types that are auto-imported in `.pde` sketches must be imported explicitly in the Java source.
- Video playback depends on the bundled Processing video library and native GStreamer files matching your platform.
