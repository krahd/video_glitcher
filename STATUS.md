# video_glitcher – Project Status

Last updated: 2026-10-07 07:20

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

Current focus is the preview-to-export product candidate and bounded local-evaluation packaging audit: safe internal staging, truthful archive provenance, argument-preserving launchers and a concrete first-session/platform checklist. Existing release assets and release/Homebrew workflows remain unchanged. See `docs/LOCAL-EVALUATION.md`. The paid redistribution hold remains in force; this is not a cleared or validated paid product. See `docs/PRODUCT-CANDIDATE-2026-10-06.md`.

Documentation publication through `.github/workflows/pages.yml` is manual-only (`workflow_dispatch`). The main-branch push trigger has been removed; the deployment job, permissions and concurrency settings are preserved. Main pushes continue to run build/export regression CI. Release-bundle and Homebrew workflows remain tag/manual-only and unchanged. Source integration does not establish native desktop acceptance, licensing clearance or permission to release or publish the documentation site.

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

- Java 21 with desktop/AWT support is the documented current build/release contract; source compilation uses JDK 21. Release CI does not target Java 17. Python 3.11+ is required for internal inspection/tests.
- System `ffmpeg` with the `libx264` encoder must be available on `PATH` for MP4 export. Neither Java nor ffmpeg is bundled in the ZIP release recipe.
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

- Paused Rewind now reports a request and explains that the preview updates on Play, rather than implying the old displayed frame has already changed. This is a wording correction only: seek/pause calls, snapshot handling, artistic frozen-frame live capture and ONCE-end gating remain unchanged. A new 211-check headless harness verifies repeated mid/end Rewind/Play, LOOP/ONCE, no-video/full-process interlocks and owned encoder counts/cleanup. The preceding source fails the wording assertion; full aggregate tests pass. Fresh paused seek-frame support remains an open native bridge task, not an implemented capability.

- Queued clip loading now keeps the actual saved/cancelled export result alongside the new clip's loading, fallback, immediate failure, first-frame readiness or decode-timeout status. A failed next load no longer claims it is still loading, and cancellation feedback is not erased by starting that load. The load-scoped context ends at readiness/failure/timeout; later explicit loads and ordinary controls use their own status. Cancelling a file picker distinguishes no current movie, a retained movie and an already queued replacement without changing playback or export ownership. A new 961-check owned-fixture suite covers both constructor/playback failure and successful fallback, real saved MP4 decoding, publication collision, deterministic cancellation, delayed readiness/timeout, repeated queued choices, cleanup exceptions and explicit retry. Source and previous output markers remain unchanged. The native checkpoint below remains pinned to `841c74d`; this status repair has headless state/encoded-output evidence only, with no new native UI, disposal or licensing acceptance.

- A preview-restoration exception after full-process finish, cancellation or failure no longer escapes before the export result is reported. Saved output retains its saved filename; cancelled/failed exports keep their distinct outcomes and original error, with a separate preview/reload warning. Failed preview state is retired, and a queued next clip is loaded without first restarting the outgoing pipeline. The 417-check owned-fixture suite covers real decoded output bytes, cancellation, encoder/publication failure, destinations appearing during encoding, repeated reload/retry, cleanup exceptions, paused/live controls, queued replacements and smoke exit/results. File success and smoke export status remain based on the encoder/publication result; a preview warning does not change saved bytes into an export failure. Native cleanup remains best-effort and no native GUI/GStreamer acceptance is added.

- Export startup now rolls back an acquired encoder when a native playback operation throws before recording becomes active. The error is contained, private encoder staging is aborted, export mode/settings are cleared, and the failed movie is retired into a reloadable state with an actionable status. Full-process rollback does not retry the failing playback call. Existing-destination validation failures preserve the usable preview. The new 298-check regression uses injected duration/time/seek/play/loop failures with real ffmpeg, three failed starts followed by a successful immediate retry, stop/dispose exceptions, existing-output markers and a smoke child that must exit 1 with no encoder/staging orphan. Source and previous output files are never removed. Native pipeline teardown remains best-effort if library disposal itself throws; these tests do not add native GUI/GStreamer acceptance.

- Retired video pipelines now receive explicit `Movie.dispose()` after being detached and stopped when replacing a clip or abandoning a decode timeout. A movie constructed before a playback-start failure is released before the file-URI retry. This uses the existing library lifecycle API to remove Processing callbacks and release native resources; it changes no dependency. Owned lifecycle doubles with real Processing callback registration cover replacement, picker cancellation, timeout/retry, failed playback/fallback, 20 repeated loads, stop exceptions and deferred export finish/cancellation. All 59 checks pass alongside the existing aggregate suite. These tests verify application ownership and callback removal; native memory, GPU and GStreamer disposal behaviour has not been measured or newly accepted.

- Fixed live recording after play-once reaches the clip end: resuming preview with Play/Space now reopens frame capture for the same output. Rewind while paused continues to wait for Play. Regression tests cover direct restart and Rewind then Play, with play-once/loop playback on resume, and decode owned red/blue synthetic segments to verify that resumed frames are actually saved. This is headless state/encoded-output evidence; it adds no native desktop or runtime acceptance.

- Added a source comparison hold key (`C`), in-app workflow/preset guide (`?`) and contextual preset descriptions.
- Fixed repeated loading after a manual pause or completed full export: only an accepted clip that begins loading resets pause/frame/readiness state. Cancelling selection keeps the previous paused clip unchanged. Both paths have workflow regressions and passed native repeats at `f199d6b`, including picker cancellation preservation. A separately reproduced black pause snapshot now uses GPU texture readback. Desktop GPU regression and actual colour-bar pause/end/freeze/cancel/reload checks passed at `eb7ea12`.
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

A later native investigation at `da183177` reproduced unchanged paused mid/end images for approximately 58/66 seconds after Rewind, with subsequent Play near zero. **Its live-recording investigation did not pass:** the original process ended Killed/137 with a disposed-buffer callback warning and a retained private partial output; causal attribution remains unresolved. A single controlled retry ended deliberately under its memory guard (143) before output selection, with no recording result. Neither manual-paused-live nor ONCE-end export-gate behaviour was accepted in that pass. See `docs/NATIVE-REWIND-CHECKPOINT-2026-10-07.md`. Further native retries are stopped pending a better bounded resource/diagnostic opportunity. The wording correction does not fix those unresolved runtime limits and has not been re-observed through the GUI.

The wording candidate passes full Java compilation/logic, 49 Python tests (one expected Linux skip), the 93/59/298/417/961 existing encoder/lifecycle/startup/outcome/load checks, 211 new Rewind checks and four decoded live-resume cases. Automated state/encoder evidence is distinct from the incomplete native recording investigation.

On 7 October 2026, the load-status candidate passed `bash scripts/check.sh --with-ffmpeg`: full Java compilation, pure logic, 49 Python tests (one expected Linux skip), 93 encoder checks, 59 lifecycle checks, 298 startup-rollback checks, 417 export-outcome checks, 961 load-status checks and four decoded live-resume cases. Its new regression fails against the preceding source at the expected misleading-load assertion. This automated pass does not extend the native checkpoint to the new source.

A bounded normal-GUI Linux recovery pass on 7 October 2026 verified source `841c74d04009bb0ac34591ea4127722179495a0f`: coloured pause/picker cancellation, repeated accepted loading, full export, existing-output protection, guide-time cancellation and one live recording resumed twice to 586 decoded frames. Source and prior output hashes stayed unchanged; cancelled files and private staging were absent after cleanup. The graphics snapshot regression also passed. See `docs/NATIVE-QA-2026-10-07.md` and its evidence identities for the tested environment, precise outputs and limits. This is one observed cloud-desktop run with an existing temporary graphical JRE/libffi dependency, not a shippable runtime or physical-GPU/memory-leak acceptance.

Optional GStreamer plugin warnings (libcrypto/libsoup/libnice) and JOGL shutdown diagnostics for three open X11 display connections remain. Paused Rewind retained the last displayed frame until Play; Rewind+Play resumed capture correctly. Finishing-race and deliberately injected preview-restoration failure coverage remains automated-only. macOS/Windows native, runtime provenance, licensing/redistribution and buyer-value gates remain open.

Automated coverage targets pure Java logic extracted from the fullscreen Processing sketch, including export filename generation, video-fit calculations, range normalisation, glitch state transitions, and ffmpeg export setup.

On 6 October 2026, `bash scripts/check.sh --with-ffmpeg` passed on Linux x86_64 with OpenJDK 21 and ffmpeg 7.1.5: full Java app compilation, Java logic tests, 12 Python checks (packaging and Linux link safety) and 93 real-encoder integration checks. `git diff --check` passed. Output checks decode a generated MP4 and verify pixels/cropping, codec, frame count, frame rate and duration. Failure tests exercise no-clobber publication, cancellation/retry and encoder failures. New PR CI covers Linux/macOS/Windows build/logic and Linux real encoding; exact-head remote results remain pending. Workflow tests also cover native-picker handoff, asynchronous completion filenames, guide-time cancel and failing smoke timeouts. Normal-launch Linux screenshots at `83d9cd5` verified all four compact sliders, opaque guide, guide open/close, U hide/show and clean exit. The original Linux runtime failed playback; an isolated validated runtime subsequently passed generated-H.264 playback, full export, existing-output rejection and live cancellation at `b87671b`. This depended on a temporary graphical JRE and libffi7 compatibility package, not a shipped/cleared bundle. macOS/Windows native acceptance remains pending. Independent review and exact-head CI at `83d9cd5` pass.

Manual/runtime validation remains important because fullscreen Processing behaviour, native video library compatibility, GUI interaction, and ffmpeg export depend on the local platform/runtime environment.

## Known issues, risks, and limitations

- Native recording stability remains unestablished after an unresolved Killed/137 event with a disposed-buffer callback warning at `da183177`. The memory-guarded retry was intentionally incomplete, not a pass. A fresh paused Rewind preview also remains unimplemented; the corrected status explains the current Play requirement.
- Paid binary redistribution remains blocked by the codec/runtime provenance hold in `docs/REDISTRIBUTION-AUDIT.md`; project-level licence clarification remains separate. Existing rights/notices are unchanged.
- Full-process capture remains real-time, random and preview-canvas-sized at 24 fps without audio; it is not frame-accurate/source-resolution offline export.
- The original unmaterialised Linux runtime failed generated-clip playback. The bundle contains 155 small library-link placeholder files; examples `libgstreamer-1.0.so` and `libharfbuzz.so.0` are Git mode 100644 text rather than symlinks. See the candidate report for loader diagnostics. The source launcher now materialises and preflights the runtime; its loader override is scoped to Java and removed/restored for system ffmpeg.
- A 15-second first-frame decode timeout is now implemented. Isolated runtime link repair enabled generated-H.264 playback with a temporary official libffi7 compatibility package. The default Debian 13 host lacks libffi.so.7; a reviewed compatible runtime remains a delivery gate. Linux Swing picker and scoped ffmpeg environment fixes passed full-export, existing-output rejection and guide-time live cancellation at `b87671b`. Optional native-plugin and internal callback warnings remain; all macOS/Windows native interaction checks and deliverable-runtime acceptance remain pending.
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

1. Preserve the accepted application/export safeguards and bounded packaging contract. Code/test head `c598964b0b299535acb75862c10ef5d3d8f65104` has green exact-head CI and independent packaging review; neither supplies new native desktop or licensing acceptance.
2. Test normal-launch GUI interactions and native load/live/full-process exports on each supported desktop.
3. Resolve redistribution/project-licence gates and establish editor-valued workflow benefit before any paid offer. No release or store upload is authorised in this work.

## Longer-term steps

1. Improve automated coverage for export/process flows where feasible without relying on fullscreen GUI interaction.
2. Keep release packaging reliable across macOS Apple Silicon, Linux x64, and Windows x64.
3. Maintain clear separation between testable logic and Processing runtime/UI code.

## Decisions and rationale

- The project remains a plain Java Processing application rather than a `.pde`-only sketch.
- Pure logic should stay extracted where practical so it can be tested without launching Processing.
- Historical distribution uses platform ZIPs plus a Homebrew formula. ZIPs require external Java and ffmpeg; native acceptance and redistribution clearance remain separate gates.

## Internal packaging/evaluation audit

- Read-only follow-up inventories the 13 shipped dependency JARs in `docs/JAR-PROVENANCE-2026-10-06.json`: ten repository-byte identities verified, three tool-limited, zero upstream artifact hashes verified. Official JogAmp tag targets agree with retrieved manifest claims; two macOS JAR listing sizes differ, with reason unknown. LGPL metadata/notice observations are review evidence, not a legal conclusion. No dependency is changed or executed.

- `scripts/package_supported_build.py` requires `--internal-provenance-only`, a complete matching checkout and a new immediate-child destination under an existing non-symlink `dist/`. Staging requires POSIX directory-fd/no-follow capabilities and fails closed on Windows/unsupported runtimes. It never recursively deletes or replaces an existing destination. Concurrent destination claims, symlinks, untracked/archive-extra payloads, path traversal, duplicate paths, payload mismatches and malformed class JARs are refused. Failed copies remain partial and cannot be reused as success.
- Independent packaging review reproduced output-parent TOCTOU redirection, tracked media/config inclusion and implicit directory case collisions. The repair holds original directory handles and uses exclusive no-follow directory-relative writes; it performs point-in-time identity checks, rejects nested/symlink output parents, restricts native file kinds and validates all implicit archive path components. New owned-fixture regressions exercise replacement before creation and during copying, with outside directories untouched. The contract requires a trusted checkout/dist and cooperative writers; it does not promise protection against arbitrary same-user directory substitution or renames after a check. macOS CI exposed lexical `/var` versus canonical `/private/var` root spelling; the next repair recognises only trusted selected-root spellings without resolving output components. Alias API and actual CLI-from-alias regressions now pass locally. Independent re-review accepted the bounded packaging/claims contract at `c598964b0b299535acb75862c10ef5d3d8f65104`. This is not native desktop, rights or release acceptance.
- Schema 2 records `stagingCheckoutCommit`; `archiveSourceCommit` remains null/unverified. It no longer misrepresents local HEAD or a requested version label as archive build provenance. The app JAR receives structural checks only; native acceptance and redistribution clearance remain unestablished.
- Portable launchers now forward CLI/smoke arguments unchanged. Stub process tests verify literal paths/presets with spaces and non-zero exit propagation; they do not exercise Java/GStreamer/GPU startup.
- README states Java 21, desktop/AWT and external libx264-capable ffmpeg requirements. `docs/LOCAL-EVALUATION.md` covers load, effects, compare, preview, safe full/live export, finding output, discard, overwrite refusal and repeat loading with synthetic media only.
- `docs/REDISTRIBUTION-AUDIT.md` now inventories native trees, exact recipe scope, media/owner decisions and official upstream licensing sources. No new project licence is chosen, and inherited binaries/notices remain unchanged.
- Local verification for this audit: 49 Python tests ran, 47 passed and two skipped (Windows command processor and absent full compiled app). Pure Java logic and 93 real-encoder assertions passed. This workspace recovered text source through GitHub; binary dependency recovery is unavailable through its text-only connector, so full compilation/workflow/GPU/native checks were not repeated locally. Before this review repair, exact-head CI passed at `e6a67051af74e8cd58c5d2f5fc83098f5d070f11` in runs `37542960852` and `37542955166`, including full application compilation on Linux/macOS/Windows, 93 encoder checks and workflow tests. The first descriptor repair at `f762bb3` passed Linux, Windows and encoder jobs but failed macOS on the root-spelling bug above; those failed runs are `37543905099` and `37543899732`. The alias correction passed the original macOS cases; its additional canonical test fixture required `self.repo.resolve()` rather than a third unselected spelling. Final code/test head `c598964b0b299535acb75862c10ef5d3d8f65104` passed both exact-head CI runs `37544725979` (PR) and `37544721395` (push). Actual logs confirm full compilation and 49 Python tests on Linux/macOS/Windows: expected skips 1/10/32 respectively. Windows staging fails closed; its supported archive/launcher checks pass. Linux also passes 93 real-encoder assertions, headless workflow tests and 155-link validation. Fresh independent review accepted that exact source under the stated trusted/cooperative contract. No release artifact, installer or external upload was produced.
- Existing release asset names, release-bundle/Homebrew publication workflows, Homebrew formula/rendering and VS Code packaging commands remain unchanged. The documentation-site workflow is now manual-only as described above. Their runtime/rights gaps are documented, not declared solved by these tests.

Last updated: 2026-10-07 07:20
