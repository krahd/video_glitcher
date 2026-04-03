# Video Glitcher

Video Glitcher is a Java app built with Processing as a library. It extends `PApplet`, loads a video file, previews it fullscreen, applies glitch effects in real time, and can export the result as an MP4.

The repository is self-contained and includes the Processing OpenGL jars needed for the `P2D` renderer, plus platform-specific video natives for macOS, Linux, and Windows.

## Releases

Latest release downloads:

- [macOS Apple Silicon](https://github.com/krahd/video_glitcher/releases/latest/download/VideoGlitcher-macos-aarch64.zip)
- [Linux x64](https://github.com/krahd/video_glitcher/releases/latest/download/VideoGlitcher-linux-amd64.zip)
- [Windows x64](https://github.com/krahd/video_glitcher/releases/latest/download/VideoGlitcher-windows-amd64.zip)

All releases:

- [GitHub Releases](https://github.com/krahd/video_glitcher/releases)

## Project Layout

- `src/tom/videoGlitcher/VideoGlitcher.java`: main application source
- `video_glitcher.pde`: original Processing sketch version
- `.vscode/launch.json`: debug launch configs for macOS, Windows, and Linux
- `.vscode/tasks.json`: build and run tasks for VS Code
- `.github/workflows/release.yml`: automated cross-platform release bundles
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

Additional platform tasks:

- `Build VideoGlitcher (Linux x64)`
- `Run VideoGlitcher (Linux x64)`
- `Build VideoGlitcher (Windows x64)`
- `Run VideoGlitcher (Windows x64)`

### Distribution package

Use:

- `Terminal` -> `Run Task...`
- Choose `Package VideoGlitcher (macOS app)`

This creates a distributable app bundle at `dist/VideoGlitcher.app`.

### Release bundles

Use:

- `Terminal` -> `Run Task...`
- Choose one of:
- `Package VideoGlitcher Release (macOS Apple Silicon)`
- `Package VideoGlitcher Release (Linux x64)`
- `Package VideoGlitcher Release (Windows x64)`

These create release archives in `dist/` for each platform, for example:

- `dist/VideoGlitcher-macos-aarch64.zip`
- `dist/VideoGlitcher-linux-amd64.zip`
- `dist/VideoGlitcher-windows-amd64.zip`

Each bundle contains the application jar, the required libraries, the platform-specific video natives, and the matching launcher script.

### Debug launch

Use:

- `Run and Debug`
- Select one of:
- `Run VideoGlitcher (macOS Apple Silicon)`
- `Run VideoGlitcher (macOS Intel)`
- `Run VideoGlitcher (Linux x64 bundled)`
- `Run VideoGlitcher (Windows x64)`
- Press `F5`

Each debug launch is configured with the correct bundled libraries and native video paths for that platform.

## Run From Terminal

```sh
java -cp "bin:lib/core.jar:lib/controlP5/library/*:lib/processing-opengl/library/*:lib/video/library/*:lib/videoExport/library/*" \
  -Dgstreamer.library.path="$PWD/lib/video/library/macos-aarch64" \
  -Dgstreamer.plugin.path="$PWD/lib/video/library/macos-aarch64/gstreamer-1.0" \
  tom.videoGlitcher.VideoGlitcher
```

## Controls

- Click background: open the file picker when no video is loaded
- `L`: load a video
- `Space`: pause or play
- `G`: turn glitching on or off
- `F`: toggle freeze mode
- `H`: show or hide the HUD
- `U`: show or hide the GUI
- `E`: start or stop export
- `S`: save a frame as PNG
- `Up`: increase glitch intensity
- `Down`: decrease glitch intensity

## Interface Notes

- Clicking inside the GUI does not trigger the background file picker.
- Glitching starts disabled before the first video is loaded so the empty-state text stays readable.
- After the first successful video load, glitching turns on automatically and then follows the user's chosen on/off state.
- If the fitted video does not fill the screen, black mattes are drawn around it so glitches stay confined to the visible video area.
- The sketch runs in Processing present mode so fullscreen covers the macOS menu bar.

## Notes

- The app is written as plain Java, so Processing types that are auto-imported in `.pde` sketches must be imported explicitly in the Java source.
- Video playback depends on the bundled Processing video library and native GStreamer files matching your platform.
- The macOS packaging task builds an `.app` image with the required jars and bundled Apple Silicon video natives.
- Tagging a version like `v1.0.0` triggers the GitHub Actions workflow to publish downloadable release bundles for macOS, Linux, and Windows.
