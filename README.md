# Video Glitcher

Video Glitcher is a Java app built with Processing as a library. It extends `PApplet`, loads a video file, previews it fullscreen, applies glitch effects in real time, and can export the result as an MP4.

The repository is self-contained and includes the Processing OpenGL jars needed for the `P2D` renderer, plus platform-specific video natives for macOS, Linux, and Windows.

## Project Layout

- `src/tom/videoGlitcher/VideoGlitcher.java`: main application source
- `video_glitcher.pde`: original Processing sketch version
- `.vscode/launch.json`: debug launch configs for macOS, Windows, and Linux
- `.vscode/tasks.json`: build and run tasks for VS Code
- `lib/`: bundled Processing, video, ControlP5, VideoExport, and Processing OpenGL libraries
- `bin/`: compiled class output
- `packaging/portable/`: launcher scripts for the portable cross-platform bundle
- `dist/`: generated packaging output, ignored by git

## Requirements

- Java 17 or newer
- VS Code with Java support
- Native video runtime files are already vendored for `macos-aarch64`, `macos-x86_64`, `linux-amd64`, and `windows-amd64`

## Build

Build from the project root with:

```sh
javac -cp "lib/core.jar:lib/controlP5/library/*:lib/processing-opengl/library/*:lib/video/library/*:lib/videoExport/library/*" -d bin src/tom/videoGlitcher/VideoGlitcher.java
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

### Distribution package

Use:

- `Terminal` -> `Run Task...`
- Choose `Package VideoGlitcher (macOS app)`

This creates a distributable app bundle at `dist/VideoGlitcher.app`.

### Portable cross-platform package

Use:

- `Terminal` -> `Run Task...`
- Choose `Package VideoGlitcher (portable zip)`

This creates `dist/VideoGlitcher-portable.zip` containing:

- `run-macos.sh`
- `run-linux.sh`
- `run-windows.bat`
- the app jar
- all required libraries
- bundled video natives for macOS, Linux, and Windows

This is the practical multiplatform distribution format for this repo. Native app bundles such as `.app`, `.exe`, or Linux app images still need to be built on their target OS.

### Debug launch

Use:

- `Run and Debug`
- Select `Run VideoGlitcher (macOS Apple Silicon)`
- Press `F5`

The debug launch is configured to run the `Build VideoGlitcher` task before startup.

## Run From Terminal

```sh
java -cp "bin:lib/core.jar:lib/controlP5/library/*:lib/processing-opengl/library/*:lib/video/library/*:lib/videoExport/library/*" \
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
- The macOS packaging task builds an `.app` image with the required jars and bundled Apple Silicon video natives.
- The portable ZIP package is the repo's cross-platform distribution artifact. It includes per-platform launcher scripts rather than a single universal native executable.
