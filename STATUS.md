# video_glitcher – Project Status

Last updated: 2026-10-06 16:27

## Project purpose

video_glitcher is a Java application built with Processing as a library. It loads video files, previews them fullscreen, applies digital and analogue-style glitch effects in real time, and exports the result as MP4 in live interactive mode or full-process mode.

## Current implementation state

The repository currently presents version `v1.1.3` in the README.

Implemented surfaces include:

- Java/Processing application entry point in `src/tom/videoGlitcher/VideoGlitcher.java`
- extracted pure Java logic in `VideoGlitcherLogic.java`
- ffmpeg-backed MP4 export support in `FfmpegVideoExporter.java`
- original Processing sketch in `video_glitcher.pde`
- bundled Processing, ControlP5, Processing OpenGL, Processing video, and platform-specific GStreamer/native video libraries under `lib/`
- logic test harness under `test/`
- VS Code build, run, smoke-test, and packaging tasks
- portable launchers and release-bundle packaging support
- Homebrew formula rendering and tap publication workflow
- release workflows for macOS Apple Silicon, Linux x64, and Windows x64 bundles

The GUI includes compact/full modes, preset buttons, high-level digital/analogue controls, deeper timing/effect controls, scrollable full panel behaviour, and retro effects such as VHS decay, old digicam, tracking tear, head switch, chroma drift, scanline wobble, vertical smear, and column drift.

## Active focus

Current focus is the draft preview-to-export product candidate: safer new-file-only export, cancellation/recovery, source comparison and preset/workflow guidance. Release bundles and Homebrew remain unchanged. The paid redistribution hold remains in force; this is not a cleared or validated paid product. See `docs/PRODUCT-CANDIDATE-2026-10-06.md`.

## Architecture overview

The main Java `PApplet` coordinates the Processing window, video playback, GUI controls, glitch rendering, smoke-test modes, and export workflows. Extracted Java logic covers deterministic calculations and state transitions that can be tested without launching the GUI. The exporter delegates MP4 writing to system `ffmpeg`. Packaging scripts assemble platform-specific launcher bundles with bundled jars and video natives.

### Architecture diagram

<svg xmlns="http://www.w3.org/2000/svg" width="1060" height="520" viewBox="0 0 1060 520" role="img" aria-labelledby="vg-arch-title vg-arch-desc">
  <title id="vg-arch-title">video_glitcher architecture</title>
  <desc id="vg-arch-desc">The Processing application uses bundled libraries and platform video natives, calls logic and ffmpeg exporter modules, and is packaged into release bundles and Homebrew formulae.</desc>
  <defs><marker id="arrow" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="8" markerHeight="8" orient="auto"><path d="M0 0 L10 5 L0 10 z" /></marker></defs>
  <rect x="40" y="200" width="190" height="90" rx="10" fill="none" stroke="black" /><text x="135" y="232" text-anchor="middle" font-size="14">VideoGlitcher.java</text><text x="135" y="254" text-anchor="middle" font-size="12">Processing PApplet,</text><text x="135" y="272" text-anchor="middle" font-size="12">GUI and runtime</text>
  <rect x="315" y="55" width="200" height="80" rx="10" fill="none" stroke="black" /><text x="415" y="87" text-anchor="middle" font-size="14">lib/</text><text x="415" y="109" text-anchor="middle" font-size="12">Processing, ControlP5,</text><text x="415" y="127" text-anchor="middle" font-size="12">video natives</text>
  <rect x="315" y="190" width="200" height="90" rx="10" fill="none" stroke="black" /><text x="415" y="222" text-anchor="middle" font-size="14">VideoGlitcherLogic</text><text x="415" y="244" text-anchor="middle" font-size="12">testable state and</text><text x="415" y="262" text-anchor="middle" font-size="12">math helpers</text>
  <rect x="315" y="335" width="200" height="80" rx="10" fill="none" stroke="black" /><text x="415" y="367" text-anchor="middle" font-size="14">FfmpegVideoExporter</text><text x="415" y="389" text-anchor="middle" font-size="12">MP4 export process</text>
  <rect x="595" y="105" width="210" height="90" rx="10" fill="none" stroke="black" /><text x="700" y="137" text-anchor="middle" font-size="14">Tests and smoke modes</text><text x="700" y="159" text-anchor="middle" font-size="12">logic, startup, load,</text><text x="700" y="177" text-anchor="middle" font-size="12">export, process</text>
  <rect x="595" y="290" width="210" height="90" rx="10" fill="none" stroke="black" /><text x="700" y="322" text-anchor="middle" font-size="14">Packaging</text><text x="700" y="344" text-anchor="middle" font-size="12">portable launchers,</text><text x="700" y="362" text-anchor="middle" font-size="12">release ZIPs</text>
  <rect x="850" y="290" width="175" height="90" rx="10" fill="none" stroke="black" /><text x="937" y="322" text-anchor="middle" font-size="14">Distribution</text><text x="937" y="344" text-anchor="middle" font-size="12">GitHub Releases</text><text x="937" y="362" text-anchor="middle" font-size="12">and Homebrew tap</text>
  <line x1="230" y1="225" x2="315" y2="95" stroke="black" marker-end="url(#arrow)" /><line x1="230" y1="245" x2="315" y2="235" stroke="black" marker-end="url(#arrow)" /><line x1="230" y1="268" x2="315" y2="375" stroke="black" marker-end="url(#arrow)" /><line x1="515" y1="235" x2="595" y2="150" stroke="black" marker-end="url(#arrow)" /><line x1="515" y1="375" x2="595" y2="335" stroke="black" marker-end="url(#arrow)" /><line x1="805" y1="335" x2="850" y2="335" stroke="black" marker-end="url(#arrow)" />
</svg>

### Flow chart

<svg xmlns="http://www.w3.org/2000/svg" width="1080" height="360" viewBox="0 0 1080 360" role="img" aria-labelledby="vg-flow-title vg-flow-desc">
  <title id="vg-flow-title">video_glitcher runtime and export flow</title>
  <desc id="vg-flow-desc">A user loads a video, chooses presets and controls, previews glitched frames, and either records live export or processes the full clip through ffmpeg.</desc>
  <defs><marker id="flowarrow" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="8" markerHeight="8" orient="auto"><path d="M0 0 L10 5 L0 10 z" /></marker></defs>
  <rect x="25" y="145" width="120" height="65" rx="10" fill="none" stroke="black" /><text x="85" y="173" text-anchor="middle" font-size="12">Load</text><text x="85" y="191" text-anchor="middle" font-size="12">video</text>
  <rect x="185" y="145" width="130" height="65" rx="10" fill="none" stroke="black" /><text x="250" y="173" text-anchor="middle" font-size="12">Choose preset</text><text x="250" y="191" text-anchor="middle" font-size="12">and controls</text>
  <rect x="355" y="145" width="130" height="65" rx="10" fill="none" stroke="black" /><text x="420" y="173" text-anchor="middle" font-size="12">Render glitch</text><text x="420" y="191" text-anchor="middle" font-size="12">preview</text>
  <rect x="545" y="65" width="135" height="65" rx="10" fill="none" stroke="black" /><text x="612" y="93" text-anchor="middle" font-size="12">Live export</text><text x="612" y="111" text-anchor="middle" font-size="12">start/stop</text>
  <rect x="545" y="225" width="135" height="65" rx="10" fill="none" stroke="black" /><text x="612" y="253" text-anchor="middle" font-size="12">Process full</text><text x="612" y="271" text-anchor="middle" font-size="12">clip</text>
  <rect x="745" y="145" width="135" height="65" rx="10" fill="none" stroke="black" /><text x="812" y="173" text-anchor="middle" font-size="12">ffmpeg</text><text x="812" y="191" text-anchor="middle" font-size="12">MP4 output</text>
  <rect x="930" y="145" width="120" height="65" rx="10" fill="none" stroke="black" /><text x="990" y="173" text-anchor="middle" font-size="12">Saved</text><text x="990" y="191" text-anchor="middle" font-size="12">video file</text>
  <line x1="145" y1="177" x2="185" y2="177" stroke="black" marker-end="url(#flowarrow)" /><line x1="315" y1="177" x2="355" y2="177" stroke="black" marker-end="url(#flowarrow)" />
  <path d="M 485 160 L 545 98" fill="none" stroke="black" marker-end="url(#flowarrow)" /><path d="M 485 195 L 545 258" fill="none" stroke="black" marker-end="url(#flowarrow)" />
  <path d="M 680 98 L 745 160" fill="none" stroke="black" marker-end="url(#flowarrow)" /><path d="M 680 258 L 745 195" fill="none" stroke="black" marker-end="url(#flowarrow)" />
  <line x1="880" y1="177" x2="930" y2="177" stroke="black" marker-end="url(#flowarrow)" />
</svg>

## Setup and run instructions

Build from the project root:

```sh
javac -cp "lib/core.jar:lib/controlP5/library/*:lib/processing-opengl/library/*:lib/video/library/*" -d bin src/tom/videoGlitcher/VideoGlitcher.java src/tom/videoGlitcher/VideoGlitcherLogic.java src/tom/videoGlitcher/FfmpegVideoExporter.java
```

Run logic tests:

```sh
mkdir -p test-bin && javac -d test-bin src/tom/videoGlitcher/VideoGlitcherLogic.java src/tom/videoGlitcher/FfmpegVideoExporter.java test/tom/videoGlitcher/VideoGlitcherLogicTest.java && java -cp test-bin tom.videoGlitcher.VideoGlitcherLogicTest
```

Run macOS Apple Silicon smoke startup directly:

```sh
java -cp "bin:lib/core.jar:lib/controlP5/library/*:lib/processing-opengl/library/*:lib/video/library/*" \
  -Dgstreamer.library.path="$PWD/lib/video/library/macos-aarch64" \
  -Dgstreamer.plugin.path="$PWD/lib/video/library/macos-aarch64/gstreamer-1.0" \
  tom.videoGlitcher.VideoGlitcher --smoke-test --smoke-frames=45
```

VS Code tasks also provide build, run, logic-test, smoke-test, macOS app packaging, and release-bundle packaging commands.

## Configuration and environment variables

- Java 17 or newer is required.
- `ffmpeg` must be available on `PATH` for MP4 export.
- GStreamer native paths are supplied through Java system properties, e.g. `gstreamer.library.path` and `gstreamer.plugin.path`.
- Homebrew installs `ffmpeg` and `openjdk` automatically when using the tap formula.

## Important files and directories

- `src/tom/videoGlitcher/VideoGlitcher.java`: main application source.
- `src/tom/videoGlitcher/VideoGlitcherLogic.java`: pure logic under automated test.
- `src/tom/videoGlitcher/FfmpegVideoExporter.java`: ffmpeg export integration.
- `test/tom/videoGlitcher/VideoGlitcherLogicTest.java`: logic test harness.
- `video_glitcher.pde`: original Processing sketch.
- `lib/`: bundled Processing and video libraries.
- `.vscode/tasks.json`: build/run/test/smoke/package tasks.
- `.vscode/launch.json`: debug launch profiles.
- `packaging/portable/`: release-bundle launchers.
- `packaging/homebrew/render_homebrew_formula.py`: formula generator.
- `Formula/video_glitcher.rb`: formula snapshot.
- `.github/workflows/release.yml`: release-bundle workflow.
- `.github/workflows/publish-homebrew-tap.yml`: Homebrew tap sync workflow.

## Recent changes

- Added a source comparison hold key (`C`), in-app workflow/preset guide (`?`) and contextual preset descriptions.
- Added save destination selection to live export; both modes refuse existing files. Temporary encoding plus no-replace hard-link publication protects previous outputs and source files, including a destination created during encoding.
- Added discard/cancel (`X`), frame counts, bounded encoder diagnostics/finalisation, actionable errors and normal-exit cleanup.
- Added isolated Linux library-link materialisation with safety/payload-hash validation and dependency preflight. No vendored payloads, system libraries or redistribution clearance are changed. Linux uses Swing pickers; ffmpeg receives the original loader environment.
- Fixed HUD contrast and the documented `U` preview shortcut; added real decoded-output and failure regression tests plus non-publishing CI.

- README currently documents version `v1.1.3`.
- Homebrew install path uses `krahd/tap` and formula name `video_glitcher`.
- The Homebrew formula snapshot has been renamed to `Formula/video_glitcher.rb`.
- Retro GUI controls include VHS/old-digicam presets and analogue-style toggles/sliders.
- Smoke tasks cover startup, load, live export, and full-process export paths on macOS Apple Silicon.

## Tests and verification status

Automated coverage targets pure Java logic extracted from the fullscreen Processing sketch, including export filename generation, video-fit calculations, range normalisation, glitch state transitions, and ffmpeg export setup.

On 6 October 2026, `bash scripts/check.sh --with-ffmpeg` passed on Linux x86_64 with OpenJDK 21 and ffmpeg 7.1.5: full Java app compilation, Java logic tests, three Python packaging tests and 89 real-encoder integration checks. `git diff --check` passed. Output checks decode a generated MP4 and verify pixels/cropping, codec, frame count, frame rate and duration. Failure tests exercise no-clobber publication, cancellation/retry and encoder failures. New PR CI covers Linux/macOS/Windows build/logic and Linux real encoding; exact-head remote results remain pending. Workflow tests also cover native-picker handoff, asynchronous completion filenames, guide-time cancel and failing smoke timeouts. Normal-launch Linux screenshots at `83d9cd5` verified all four compact sliders, opaque guide, guide open/close, U hide/show and clean exit. Generated-H.264 playback failed due to the existing bundled native runtime; full native video/export and macOS/Windows acceptance remain pending. Independent review and exact-head CI at `83d9cd5` pass.

Manual/runtime validation remains important because fullscreen Processing behaviour, native video library compatibility, GUI interaction, and ffmpeg export depend on the local platform/runtime environment.

## Known issues, risks, and limitations

- Paid binary redistribution remains blocked by the codec/runtime provenance hold in `docs/REDISTRIBUTION-AUDIT.md`; project-level licence clarification remains separate. Existing rights/notices are unchanged.
- Full-process capture remains real-time, random and preview-canvas-sized at 24 fps without audio; it is not frame-accurate/source-resolution offline export.
- Native Linux generated-clip playback failed. The bundle contains 155 small library-link placeholder files; examples `libgstreamer-1.0.so` and `libharfbuzz.so.0` are Git mode 100644 text rather than symlinks. See the candidate report for loader diagnostics. No untested LD_LIBRARY_PATH workaround was committed.
- A 15-second first-frame decode timeout is now implemented. Isolated runtime link repair enabled generated-H.264 playback with a temporary official libffi7 compatibility package. The default Debian 13 host lacks libffi.so.7; a reviewed compatible runtime remains a delivery gate. Linux Swing picker and scoped ffmpeg environment fixes await final native export-dialog QA; all macOS/Windows native interaction checks remain pending.
- Encoder writes/close and finalisation now run off the UI thread behind a bounded queue/deadline. Queue overload fails explicitly. Publication requires hard-link-capable storage; unresponsive filesystem operations can still delay background save completion.

- Runtime video playback depends on bundled platform-specific Processing video/GStreamer native files.
- MP4 export depends on system `ffmpeg` availability.
- Fullscreen `PApplet` behaviour and GUI interactions require manual or smoke validation beyond pure logic tests.
- Cross-platform release bundles need platform-specific smoke checks before release.
- Generated `dist/` bundles, compiled outputs, and exported media should not be committed.

## Pending tasks

- Keep smoke tests aligned with runtime flags and presets.
- Confirm all release asset names remain aligned with GitHub Actions and Homebrew formula rendering.
- Continue expanding deterministic logic coverage when GUI/runtime behaviour can be isolated.

## Next steps

1. Complete independent review and exact-head CI for the candidate draft PR.
2. Test normal-launch GUI interactions and native load/live/full-process exports on each supported desktop.
3. Resolve redistribution/project-licence gates and establish editor-valued workflow benefit before any paid offer. No release or store upload is authorised in this work.

## Longer-term steps

1. Improve automated coverage for export/process flows where feasible without relying on fullscreen GUI interaction.
2. Keep release packaging reliable across macOS Apple Silicon, Linux x64, and Windows x64.
3. Maintain clear separation between testable logic and Processing runtime/UI code.

## Decisions and rationale

- The project remains a plain Java Processing application rather than a `.pde`-only sketch.
- Pure logic should stay extracted where practical so it can be tested without launching Processing.
- Distribution relies on self-contained release bundles plus a Homebrew formula for supported platforms.

## Supported-build distribution layer

- `scripts/package_supported_build.py` stages the three release ZIPs for internal provenance/checksum use only via `--internal-provenance-only`, without modifying the free release artefacts. Paid redistribution remains held.
- The packager fails closed when a platform archive is missing and emits `SHA256SUMS`, `support-manifest.json`, and a concise supported-build README.
- No DRM, licence key, release tag, GitHub asset publication, or Homebrew behaviour is introduced or changed.

Last updated: 2026-10-06 16:27
