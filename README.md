# video_glitcher

| Before | After |
| --- | --- |
| ![Source clip](docs/assets/video_glitcher-source.gif) | ![Clip exported from video_glitcher](docs/assets/video_glitcher-glitched.gif) |

video_glitcher is a Java app built with [Processing](http://processing.org) as a library. It extends `PApplet`, loads a video file, previews it fullscreen, applies glitch effects in real time, and exports the result as an MP4 in either live interactive mode or full-process mode.

Project site: [krahd.github.io/video_glitcher](https://krahd.github.io/video_glitcher/)

Current version: `v1.1.3`

The repository is self-contained and includes the Processing OpenGL jars needed for the `P2D` renderer, plus platform-specific video natives for macOS, Linux, and Windows. Export uses `ffmpeg` from your system `PATH`.

## Install

### Homebrew

On macOS Apple Silicon and Linux x86_64, install `video_glitcher` from the `krahd/tap` Homebrew tap:

```sh
brew tap krahd/tap && brew install krahd/tap/video_glitcher
video_glitcher
```

The Homebrew formula installs the matching prebuilt release bundle, adds `ffmpeg` plus `openjdk` automatically, and exposes the `video_glitcher` command. Windows should use the release ZIP instead.

### Direct downloads

Latest release page:

- [GitHub Releases](https://github.com/krahd/video_glitcher/releases)

Latest release downloads:

- [macOS Apple Silicon](https://github.com/krahd/video_glitcher/releases/latest/download/video_glitcher-macos-aarch64.zip)
- [Linux x64](https://github.com/krahd/video_glitcher/releases/latest/download/video_glitcher-linux-amd64.zip)
- [Windows x64](https://github.com/krahd/video_glitcher/releases/latest/download/video_glitcher-windows-amd64.zip)

For version-pinned downloads and release-specific details, open the release page for the tag you want.

## Project Layout

- `Formula/video_glitcher.rb`: Homebrew formula snapshot synced to `krahd/homebrew-tap`
- `src/tom/videoGlitcher/VideoGlitcher.java`: main application source
- `video_glitcher.pde`: original Processing sketch version
- `.vscode/launch.json`: debug launch configs for `video_glitcher` on macOS, Windows, and Linux
- `.vscode/tasks.json`: build and run tasks for `video_glitcher` in VS Code
- `.github/workflows/publish-homebrew-tap.yml`: waits for release assets and updates the Homebrew tap formula from tagged releases or manual dispatch
- `.github/workflows/release.yml`: automated cross-platform release bundles
- `lib/`: bundled Processing, video, ControlP5, and Processing OpenGL libraries
- `bin/`: compiled class output
- `packaging/homebrew/render_homebrew_formula.py`: renders the asset-based Homebrew formula from release asset URLs and SHA-256 digests
- `packaging/portable/`: launcher scripts for the portable cross-platform bundle
- `dist/`: generated packaging output, ignored by git

## Requirements

- Java 17 or newer
- VS Code with Java support
- Homebrew installs `ffmpeg` and `openjdk` automatically when you use `brew tap krahd/tap && brew install krahd/tap/video_glitcher`
- `ffmpeg` on your `PATH` if you want MP4 export
- Native video runtime files are already vendored for `macos-aarch64`, `macos-x86_64`, `linux-amd64`, and `windows-amd64`

## Build

Build from the project root with:

```sh
javac -cp "lib/core.jar:lib/controlP5/library/*:lib/processing-opengl/library/*:lib/video/library/*" -d bin src/tom/videoGlitcher/VideoGlitcher.java src/tom/videoGlitcher/VideoGlitcherLogic.java src/tom/videoGlitcher/FfmpegVideoExporter.java
```

In VS Code, run the default build task:

- `Terminal` -> `Run Build Task...`
- Choose `Build video_glitcher`

## Test

Current automated coverage targets the pure Java logic that was extracted from the fullscreen Processing sketch. It covers export filename generation, video-fit calculations, range normalization, glitch state transitions, and ffmpeg export setup.

Run the logic tests from the project root with:

```sh
mkdir -p test-bin && javac -d test-bin src/tom/videoGlitcher/VideoGlitcherLogic.java src/tom/videoGlitcher/FfmpegVideoExporter.java test/tom/videoGlitcher/VideoGlitcherLogicTest.java && java -cp test-bin tom.videoGlitcher.VideoGlitcherLogicTest
```

In VS Code, you can also run:

- `Terminal` -> `Run Task...`
- Choose `Test video_glitcher logic`

### Smoke validation

The app also supports a non-interactive smoke mode for local runtime validation on macOS. Smoke mode runs in a normal window instead of Processing present mode and exits on its own.

Available tasks:

- `Smoke Test video_glitcher Startup (macOS Apple Silicon)`
- `Smoke Test video_glitcher Load (macOS Apple Silicon)`
- `Smoke Test video_glitcher Export (macOS Apple Silicon)`
- `Smoke Test video_glitcher Process (macOS Apple Silicon)`

The load, export, and process smoke tasks generate a short sample clip with `ffmpeg` and launch the app with `--smoke-test`. The load task validates the video pipeline without requiring interaction. The export task exercises the live interactive ffmpeg-backed export path. The process task runs the full-clip end-to-end mode and exits non-zero if the automatic export does not finish cleanly. The shipped command uses a longer smoke timeout because a heavy preset can render slower than realtime.

The smoke commands also accept the new retro presets, including `--preset=vhs-decay` and `--preset=old-digicam`, so you can validate the analogue-style control paths without touching the GUI.

You can also invoke smoke mode directly from the terminal:

```sh
java -cp "bin:lib/core.jar:lib/controlP5/library/*:lib/processing-opengl/library/*:lib/video/library/*" \
  -Dgstreamer.library.path="$PWD/lib/video/library/macos-aarch64" \
  -Dgstreamer.plugin.path="$PWD/lib/video/library/macos-aarch64/gstreamer-1.0" \
  tom.videoGlitcher.VideoGlitcher --smoke-test --smoke-frames=45
```

To auto-load a file and exercise live export:

```sh
java -cp "bin:lib/core.jar:lib/controlP5/library/*:lib/processing-opengl/library/*:lib/video/library/*" \
  -Dgstreamer.library.path="$PWD/lib/video/library/macos-aarch64" \
  -Dgstreamer.plugin.path="$PWD/lib/video/library/macos-aarch64/gstreamer-1.0" \
  tom.videoGlitcher.VideoGlitcher --smoke-test --video=/absolute/path/to/sample.mp4 --auto-export --smoke-frames=180 --export-frames=48
```

To auto-load a file and exercise full-process export:

```sh
java -cp "bin:lib/core.jar:lib/controlP5/library/*:lib/processing-opengl/library/*:lib/video/library/*" \
  -Dgstreamer.library.path="$PWD/lib/video/library/macos-aarch64" \
  -Dgstreamer.plugin.path="$PWD/lib/video/library/macos-aarch64/gstreamer-1.0" \
  tom.videoGlitcher.VideoGlitcher --smoke-test --video=/absolute/path/to/sample.mp4 --preset=Extreme --auto-process --smoke-frames=360
```

The rest of the app still requires manual validation because runtime behavior depends on a fullscreen `PApplet`, native video libraries, and live GUI interaction.

## Run In VS Code

### Task-based run

Use:

- `Terminal` -> `Run Task...`
- Choose `Run video_glitcher (macOS Apple Silicon)`

This task builds the app first, then runs it with the correct bundled GStreamer paths.

Additional platform tasks:

- `Build video_glitcher (Linux x64)`
- `Run video_glitcher (Linux x64)`
- `Build video_glitcher (Windows x64)`
- `Run video_glitcher (Windows x64)`

### Distribution package

Use:

- `Terminal` -> `Run Task...`
- Choose `Package video_glitcher (macOS app)`

This creates a distributable app bundle at `dist/video_glitcher.app`.

### Release bundles

Use:

- `Terminal` -> `Run Task...`
- Choose one of:
- `Package video_glitcher Release (macOS Apple Silicon)`
- `Package video_glitcher Release (Linux x64)`
- `Package video_glitcher Release (Windows x64)`

These create release archives in `dist/` for each platform, for example:

- `dist/video_glitcher-macos-aarch64.zip`
- `dist/video_glitcher-linux-amd64.zip`
- `dist/video_glitcher-windows-amd64.zip`

Each bundle contains the application jar, the required libraries, the platform-specific video natives, and the matching launcher script.

### Debug launch

Use:

- `Run and Debug`
- Select one of:
- `Run video_glitcher (macOS Apple Silicon)`
- `Run video_glitcher (macOS Intel)`
- `Run video_glitcher (Linux x64 bundled)`
- `Run video_glitcher (Windows x64)`
- Press `F5`

Each debug launch is configured with the correct bundled libraries and native video paths for that platform.

## Run From Terminal

```sh
java -cp "bin:lib/core.jar:lib/controlP5/library/*:lib/processing-opengl/library/*:lib/video/library/*" \
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
- `E`: start or stop live interactive export
- `P`: open a save dialog, then process the full clip from start to finish with the current settings
- `S`: save a frame as PNG
- `Up`: increase glitch intensity
- `Down`: decrease glitch intensity

## Retro Controls

- The preset controls now use a button grid that includes the original digital presets, `VHS Decay`, `Old Digicam`, and `Random`.
- The GUI includes a dedicated retro section with six toggles: `Tracking Tear`, `Head Switch`, `Chroma Drift`, `Scanline Wobble`, `Vertical Smear`, and `Column Drift`.
- The top-right `Advanced` toggle switches the whole control surface between `compact` and `full`.
- `GUI: Compact` keeps the panel short and only exposes the presets plus four high-level sliders: `Digital Intensity`, `Glitch Activity`, `Analogue Intensity`, and `Damage Texture`.
- `GUI: Full` stretches the panel vertically, reveals the advanced timing sliders, the full digital toggle bank, the per-effect retro sliders, and supports mouse-wheel scrolling inside the panel.
- Full-process export snapshots the active retro settings the same way it snapshots the rest of the glitch configuration, so preview and export stay aligned.

## Interface Notes

- Clicking inside the GUI does not trigger the background file picker.
- Glitching starts disabled before the first video is loaded so the empty-state text stays readable.
- After the first successful video load, glitching turns on automatically and then follows the user's chosen on/off state.
- `Export Start` / `Export Stop` stay in the interactive lane: you can let the preview run, change settings on the fly, and manually finish the MP4.
- `Process Full` opens a save dialog, snapshots the current glitch settings, rewinds to frame 0, renders the entire clip once, and finishes the MP4 automatically at the playback end.
- The GUI is layout-aware: compact mode keeps the panel shorter for quick preset-driven use, while full mode exposes the deeper digital and analogue tuning controls.
- The full-height panel now stops at the HUD/status bar instead of running behind it, and the footer transport row uses clearer labels such as `REWIND`.
- When the full panel grows beyond the visible space, use the mouse wheel while the pointer is over the GUI to scroll the control list.
- If the fitted video does not fill the screen, black mattes are drawn around it so glitches stay confined to the visible video area.
- The app now calls `PApplet.hideMenuBar()` before entering Processing present mode so fullscreen explicitly covers the macOS menu bar.

## Notes

- The app is written as plain Java, so Processing types that are auto-imported in `.pde` sketches must be imported explicitly in the Java source.
- Video playback depends on the bundled Processing video library and native GStreamer files matching your platform.
- MP4 export depends on `ffmpeg` being installed and available on your `PATH`.
- The macOS packaging task builds an `.app` image with the required jars and bundled Apple Silicon video natives.
- Pushing a release tag like `v1.1.2` triggers the GitHub Actions workflow to build and publish downloadable release bundles for macOS, Linux, and Windows.
- The `Publish Homebrew Tap` workflow waits for the macOS Apple Silicon and Linux x86_64 release ZIPs, renders `Formula/video_glitcher.rb`, syncs it into `krahd/homebrew-tap`, and removes the previous hyphenated formula file when the `HOMEBREW_TAP_TOKEN` repository secret is configured.

## Contributing

Pull requests are welcome.
