# video_glitcher

| Before | After |
| --- | --- |
| ![Source clip](docs/assets/video_glitcher-source.gif) | ![Clip exported from video_glitcher](docs/assets/video_glitcher-glitched.gif) |

video_glitcher is a Java app built with [Processing](http://processing.org) as a library. It extends `PApplet`, loads a video file, previews it fullscreen or in an optional fixed-size window, applies glitch effects in real time, and exports the result as an MP4 in either live interactive mode or full-process mode.

Project site: [krahd.github.io/video_glitcher](https://krahd.github.io/video_glitcher/)

Current version: `v1.1.3`

The repository includes the Processing OpenGL jars needed for the `P2D` renderer, plus platform-specific video natives for macOS, Linux, and Windows. Java and `ffmpeg` are external prerequisites; they are not included in the release ZIPs. Export uses system `ffmpeg` with the `libx264` encoder.

The current source candidate is for local evaluation only. Existing v1.1.3 downloads do not include its workflow repairs. See the [local evaluation checklist](docs/LOCAL-EVALUATION.md) for first-session steps and platform gates; current binaries remain [uncleared for paid redistribution](docs/REDISTRIBUTION-AUDIT.md).

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

- Java 21 with desktop/AWT support for the current build/release recipe (JDK 21 for source compilation). Older runtime compatibility is not established.
- VS Code with Java support is optional; terminal build/run commands are available.
- Homebrew installs `ffmpeg` and `openjdk` automatically when you use `brew tap krahd/tap && brew install krahd/tap/video_glitcher`
- `ffmpeg` on your `PATH`, built with `libx264`, for MP4 export
- Native video runtime files are already vendored for `macos-aarch64`, `macos-x86_64`, `linux-amd64`, and `windows-amd64`

Before building or opening the app, check the Java and encoder commands selected by your terminal:

```sh
python3 scripts/diagnose_prerequisites.py
```

On Windows, use `py -3 scripts/diagnose_prerequisites.py` with Python 3.11+ installed. The diagnostic reports the executable paths, Java 21+ version metadata, `java.desktop` module and advertised `libx264` encoder. It launches only installed Java/ffmpeg metadata commands, never the app or a video. `FOUND` is not a working-app guarantee: AWT/display, native GStreamer/GPU, decoding and output storage remain untested. Missing prerequisites return exit 1; inconclusive checks return exit 2 unless another prerequisite is missing; exit 0 means only that the advertised prerequisites were found. No software is installed or configured.

Each metadata process has a five-second wait limit after startup and a 64 KiB captured-output cap; timeouts, oversized/malformed output and command failures remain `UNKNOWN`. Executable discovery and process creation are not timed. POSIX cleanup targets the probe's process group; Windows cleanup covers only the direct child, so wrapper descendants are not guaranteed to stop. Use normal installed Java/ffmpeg commands rather than custom wrappers. The existing native-retry stop remains in force; see the [local evaluation checklist](docs/LOCAL-EVALUATION.md).

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

The current archives are not cleared for paid distribution. The [internal packaging inspection](docs/SUPPORTED_BUILD.md) leaves free release and Homebrew workflows unchanged.

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

### Optional windowed preview (current source)

Append the exact `--windowed` flag after `tom.videoGlitcher.VideoGlitcher` in your platform's source run command. The Linux source launcher forwards it with `bash scripts/run-linux.sh --windowed`. Existing portable launchers also forward arguments, but historical v1.1.3 binaries do not implement this new flag.

This selects a fixed 960×540 preview canvas with the normal controls, HUD, presets and keyboard shortcuts. The Advanced panel uses its existing mouse-wheel scrolling; `U` hides/restores controls when you want the unobstructed preview. Omitting the flag retains fullscreen. No custom dimensions or runtime mode switch are added. `--video=` and `--preset=` still work; adding `--smoke-test` retains smoke mode's hidden controls, automation and exit behaviour rather than starting an interactive session.

Export still uses the preview canvas, so windowed exports are 960×540 with any fitted black mattes and no audio. This makes a smaller-canvas choice available; it does not guarantee an overloaded export will recover or establish frame rate, memory usage or native stability. The desktop must have room for the canvas plus OS window decorations. Headless layout checks do not prove DPI, window-manager, GPU/video or chooser behaviour; the native-retry stop in the [evaluation checklist](docs/LOCAL-EVALUATION.md) remains active.

## Preview-to-export workflow

Press `?` in the app for a short preset and workflow guide. Start with `Subtle` or `Cinematic`, or choose `VHS Decay` for tracking/colour drift and `Old Digicam` for smear/column drift. Choosing a preset shows its description in the status bar. Hold `C` to compare the source without changing your settings or the recorded output.

Choosing a missing, unreadable or non-file input leaves the current preview and recording intact. A queued input is checked again after saving or discarding; if it disappeared, the saved/cancelled result remains visible and the previous preview is retained where it can resume. Readable files still go to the native decoder: this metadata check is not codec validation or protection against later filesystem changes.

Both `E` (live performance) and `P` (full clip) now ask for an output destination. Save dialogs suggest numbered names for repeated exports in the same folder, such as `clip (2).mp4`; you can choose another name or folder. Suggestions do not reserve a file, and the path you accept still receives the same no-overwrite checks. If a load or save dialog raises an ordinary exception while opening, the status bar offers L/E/P to retry without changing the preview or stopping a live recording. Choose a **new filename**: the app refuses to replace existing files, including the source and previous exports. Encoding happens in a temporary file in the selected folder. A successful background finish saves the MP4; failures and `X` cancellation discard the temporary output. Choose a local output drive with hard-link support; unsupported destinations fail without replacing files. Cancellation remains available until the final publication operation begins. `E` / `Stop Output` still finalises the frames recorded so far, so use `X` when you want to discard them instead. The status bar reports failures with the encoder diagnostic and shows the saved destination on success. In play-once mode, reaching the end pauses frame capture but keeps a live recording open. Press Play or Space to restart the clip and continue the same recording; Rewind while paused waits for Play. Stop Output finalises both segments.

Ordinary exceptions while checking/reading a video frame or completing playback now leave a reloadable preview error. An active recording fails without saving partial output; an export already finishing keeps its own saved/failed result and cancellation. This application-level recovery does not resolve native callback, GPU or runtime stability limits.

Output remains **silent H.264 MP4 at 24 fps, at the preview canvas size including black mattes**. It is not source-resolution export. Full-clip mode follows real-time playback and can skip source frames under load; it is not a frame-accurate offline renderer. Effects are random, so it does not reproduce the exact random sequence seen during preview. These limits are shown in the in-app guide and remain product gates.

Run the complete local checks (JDK 21, Python 3.11+, ffmpeg and ffprobe):

```sh
bash scripts/check.sh --with-ffmpeg
```

The exporter reuses a lazy, bounded pool of RGB frame buffers instead of allocating a full clone for every accepted frame. A buffer stays exclusively owned until its pipe write finishes or fails; it is never reused while queued or being written. The array bound is the existing pending-queue capacity plus one producer and one consumer; warmed arrays remain reusable while the export is open. The 64 MiB queue target is not a total-memory limit: active buffers, the JVM, native playback and ffmpeg are additional. Concurrent producers are explicitly rejected, queue overload still fails without silently dropping accepted frames, and terminal cleanup drops free/queued references while discarding in-flight buffers when their owners return. A stuck or timed-out owner is never recycled. This reduces measured array allocation in owned tests; it does not establish higher frame rate or fix the unresolved native recording failure.

The real-encoder regression suite decodes generated output and checks colour, odd-dimension cropping, H.264 codec, 24 fps, frame count, duration, paths with spaces/Unicode, source/existing-file protection, concurrent destination creation, cancellation/retry, missing encoder/folder, invalid frames, empty exports, failed encoders and bounded finalisation, including a live non-reading encoder that fills the input pipe. It does not substitute for Processing/GStreamer GUI tests. CI runs build/logic/packaging checks on Linux, macOS and Windows and real encoder tests on Linux; it does not publish a release.

Pause and completed-export snapshots use GPU texture readback so the source remains visible after native CPU buffers have been consumed. Manual freeze uses the pre-GUI video frame. The repeated-load regression covers loading another clip after pause or completed export, while cancellation preserves the current clip. On a graphical Linux/macOS development desktop, `bash scripts/check-gpu-snapshot.sh` runs the separate OpenGL snapshot regression after the normal build; headless CI does not claim that check.

For a built Linux source checkout, run `bash scripts/run-linux.sh` (accepts existing CLI/smoke flags; requires Python 3). It validates/materialises imported runtime links in a temporary tree and preflights dependencies without changing system libraries. The existing bundle requires `libffi.so.7`, which is absent on some current distributions; the launcher then fails with a specific message. Use a compatible, provenance-reviewed runtime rather than aliasing an incompatible ABI. `VIDEO_GLITCHER_NATIVE_DIR` can point to a separately prepared runtime. The native-runtime and redistribution gates remain open; see the candidate report.

See [candidate validation and remaining product gates](docs/PRODUCT-CANDIDATE-2026-10-06.md). The [paid redistribution hold](docs/REDISTRIBUTION-AUDIT.md) remains in force.

## Controls

- Click background: open the file picker when no video is loaded
- `L`: load a video
- `Space`: pause or play
- `G`: turn glitching on or off
- `F`: toggle freeze mode
- `H`: show or hide the HUD
- `U`: show or hide the GUI
- `C` (hold): compare the source; release to return to effects
- `?`: show or close the workflow/preset guide (`Escape` also closes the guide)
- `X`: cancel the current export and discard its output
- `E`: choose a new filename and start, or stop/finalise live interactive export
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
- During capture, the HUD reserves measured space for its existing frame/output-seconds counter so a long path cannot push it off the 960px canvas. Displayed paths/status may be abbreviated; full stored status, destination and existing console messages are unchanged. An unusually narrow canvas prioritises the status line. Saving, failure and cancellation do not show stale live counts or a guessed completion percentage.
- When the full panel grows beyond the visible space, use the mouse wheel while the pointer is over the GUI to scroll the control list.
- If the fitted video does not fill the screen, black mattes are drawn around it so glitches stay confined to the visible video area.
- The app now calls `PApplet.hideMenuBar()` before entering Processing present mode so fullscreen explicitly covers the macOS menu bar. Windowed and smoke modes skip menu-bar hiding.

## Notes

- The app is written as plain Java, so Processing types that are auto-imported in `.pde` sketches must be imported explicitly in the Java source.
- Video playback depends on the bundled Processing video library and native GStreamer files matching your platform.
- MP4 export depends on `ffmpeg` being installed and available on your `PATH`.
- The macOS packaging task builds an `.app` image with the required jars and bundled Apple Silicon video natives.
- Pushing a release tag like `v1.1.2` triggers the GitHub Actions workflow to build and publish downloadable release bundles for macOS, Linux, and Windows.
- The `Publish Homebrew Tap` workflow waits for the macOS Apple Silicon and Linux x86_64 release ZIPs, renders `Formula/video_glitcher.rb`, syncs it into `krahd/homebrew-tap`, and removes the previous hyphenated formula file when the `HOMEBREW_TAP_TOKEN` repository secret is configured.

## Contributing

Pull requests are welcome.
