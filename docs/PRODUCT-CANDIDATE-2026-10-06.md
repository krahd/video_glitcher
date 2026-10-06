# Video Glitcher preview-to-export candidate

Date: 6 October 2026. Base: `7d4eae357a4b19755d0e0aa66971e6d42ffede13`.

## What this candidate improves

- Preset descriptions and an in-app workflow guide explain how to get from a clip to a result and disclose actual output limits.
- Hold-to-compare source preview leaves the effect settings, ghost cache and exported frame untouched.
- Both export modes have a destination picker. Existing files and symlink destinations are rejected, including the loaded source. Cancelling the picker does not start/restart playback.
- Encoding uses a private temporary file in the destination directory. Publication creates a same-directory hard link in one no-replace operation, so a file created by another application during encoding cannot be replaced. No error path deletes the destination, avoiding deletion of a replacement owned by another process. Filesystems without hard-link support (for example some removable/network volumes) fail clearly; use a supported local output folder.
- `X` cancels and discards; the existing `E` / `Stop Output` retains a partial recording. Normal application disposal aborts an unfinished export.
- Bounded stderr capture gives recovery information in the status bar. A byte-budgeted queue (up to 48 frames, targeting 64 MiB and at least one frame) moves pipe writes/close off the UI thread and fails rather than silently dropping frames when the encoder cannot keep up. Background finalisation has a 30-second deadline including the writer, pipe close and process exit. Cancellation is non-blocking; it is no longer accepted once the completed file starts its single publication operation. Invalid/empty frames and zero-frame exports fail rather than report success.
- HUD text is readable on its dark background; `U` now works in ordinary preview as already documented.

## Verification actually performed

Linux x86_64, OpenJDK 21.0.12.1, system ffmpeg/ffprobe 7.1.5:

- `bash scripts/check.sh --with-ffmpeg`: app compilation, Java logic tests, 12 Python checks (packaging and Linux link safety) and 93 real-encoder integration assertions pass.
- Output is decoded and checked for red pixels, correct row stride/odd-edge cropping, H.264, even dimensions, 24 frames, 24 fps and one-second duration.
- Failure checks cover existing/source files, a destination appearing during encoding, symlinks, cancellation/retry, missing binary/folder, invalid frame/dimensions/fps, empty output, encoder error text and finalisation timeout.
- `git diff --check` passes.

This is headless build/export evidence, not desktop acceptance. The available cloud desktop has a separate filesystem; an attempted launch confirmed the executor script was absent there. A separate cloud desktop checkout was subsequently cloned and built. Its installed Java was headless, so a disposable official Temurin graphical JRE was downloaded without changing the system runtime. Normal-launch startup, guide readability/open-close and U hide/show were visually checked on Linux at code revision `8961eee`. This exposed a compact-panel height calculation that hid the fourth slider. Repeat normal-launch screenshots at `83d9cd5` confirmed all four compact controls and the opaque guide fit correctly at 1364×1024. Guide open/close, U hide/show and clean application exit were exercised. Native generated-H.264 playback failed on this Linux runtime; therefore native video/export acceptance and all macOS/Windows interaction acceptance remain open. The new CI workflow checks builds/logic/packaging on three platforms and real encoding on Linux; its results must be verified for the final remote SHA.

## Remaining release and commercial gates

1. Run normal-launch screenshot/interaction QA on supported desktops: guide open/close, source compare/release/focus loss, repeated save clicks, Cancel, existing/source file selection, stop versus discard, missing ffmpeg, load failure/retry, advanced-panel scrolling and hidden controls. Smoke mode hides the GUI and is not visual QA.
2. Verify native load, live export and full-process smoke separately on macOS Apple Silicon, Linux x64 and Windows x64, including a slow/heavy preset.
3. Full-process remains a real-time playback capture, not a deterministic frame-accurate renderer. Output remains silent, 24 fps and preview-canvas-sized with mattes. Encoder I/O and finalisation run off the UI thread. Queue overload fails explicitly rather than producing a frame-dropped export. Final publication needs hard-link support in the destination filesystem; an unresponsive filesystem can still delay the background save operation. These are explicit remaining product/architecture limits.
4. Preserve `docs/REDISTRIBUTION-AUDIT.md`: the current multimedia binaries are not cleared for paid redistribution. No existing rights or notices were removed or changed. Project-level licence/provenance clarification is a separate prerequisite; this candidate does not grant or infer new rights.
5. Test workflow usefulness with editors using their permitted clips before defining a paid edition. No demand, willingness to pay, retention, customer revenue or recurring-revenue claim is established. The same source improvements do not alone establish a paid-product advantage over free distribution.
6. Keep this as a draft PR until independent review, GUI/platform acceptance and exact-head CI are resolved. No merge, tag, binary publication, deployment, store upload, billing activation or outreach belongs to this change.

The original Processing sketch is retained as a historical source; the maintained Java application is the implementation tested here.

## Adversarial review iteration

- Reproduced a non-reading encoder freezing the original candidate's UI-thread write. Added queued writing, background finish and blocked-pipe/cancellation/deadline tests.
- Independent review reproduced 20/20 normal Processing exits leaving staging files before the fix and 0/20 after the exporter shutdown hook. Ten subprocess exit cycles are retained in the regression suite.
- Snapshotted the actual finishing destination, deferred loading the next clip until save finishes, and transferred native file-picker callbacks onto the Processing draw thread.
- Added workflow state tests for picker cancellation, asynchronous completion filenames, guide-time X cancellation and a real smoke-timeout subprocess that must exit 1 rather than report success without output.
- Independent cancellation/publication stress tests reported consistent outcomes with no staging leaks, including cases where cancellation and publication each won.

## Native Linux QA result and blocker

Normal-launch screenshots were inspected at `83d9cd5` (compact controls, guide and hidden-control view). Native video testing used an ffmpeg-generated 640×360, 24 fps, three-second H.264 test clip and a disposable official Eclipse Temurin graphical JRE. The system-installed JRE was headless.

The bundled Linux video runtime failed to decode that clip. Plugin warnings identified missing loader dependencies, including `libavfilter.so.7`, despite corresponding files existing in the bundle. A scoped `LD_LIBRARY_PATH` diagnostic then failed on `libharfbuzz.so.0: file too short`. Repository inspection explains a material part of this: 155 small library entries are ordinary files containing intended link targets; `libgstreamer-1.0.so` and `libharfbuzz.so.0` are both committed with Git mode `100644`, not symlink mode. The video loader fell back to the system GStreamer before failing codec discovery. No blanket loader-path workaround was committed.

This is a verified native-runtime blocker, not an export-engine test failure or proof that every supported Linux machine fails. Do not claim the current Linux bundle is accepted. Repair and rebuild the native runtime reproducibly, then repeat startup/load/live/full-clip/native-dialog tests on all advertised platforms. Keep that work aligned with the existing redistribution/provenance hold; repairing links alone does not clear redistribution rights. The GUI also remained at a loading status after the native decoder error; a bounded first-frame timeout/actionable decode-error state is a remaining recovery improvement.

## Isolated native-runtime repair

`materialize_linux_runtime.py` validates the 155 imported relative link chains, rejects absolute/escaping/cyclic/dangling/non-ELF targets and creates a new runtime tree. It compares SHA-256 hashes of every non-link payload before and after copying and writes a manifest with redistribution clearance explicitly false. Nine added Linux-only tests cover the safety boundary. Existing vendored files are not edited. The source launcher uses a temporary materialised tree, scopes loader lookup to its Java process and preflights core/decoder dependencies. It stops with a specific missing-dependency message instead of silently falling back to a different GStreamer.

On this Debian 13 host, the repaired runtime requires `libffi.so.7`, absent from the host. For QA only, the official [Debian libffi7 3.3-6 package](https://packages.debian.org/bullseye/amd64/libffi7/download) was extracted into a temporary directory, without installing or replacing system libraries. This enabled native H.264 playback. It is not added to the repository/release or presented as a redistribution clearance. A compatible/provenance-reviewed runtime remains a delivery prerequisite.

Testing also exposed native GTK picker incompatibility under the older bundled libraries and contamination of the system ffmpeg subprocess loader. Linux now uses Processing's supported Swing file-picker path; the exporter restores the pre-launch loader environment for ffmpeg. Encoder regression tests pass both normally and under the scoped bundled-runtime environment. Only draw consumes video frames, avoiding concurrent reads from the GStreamer callback. Failed first-frame decoding is bounded to 15 seconds and returns an actionable retry state. The latest native-dialog changes passed the focused GUI checks below at `b87671b`.

## Final focused native result

Normal GUI testing at `b87671b` used the materialised runtime, bundled GStreamer 1.20.3, temporary libffi7 and temporary graphical Temurin JRE. Verified:

- Generated 640×360 / 24 fps / three-second H.264 input loads and previews.
- Full-clip Swing save dialog works and produces a saved result. System ffprobe confirms silent H.264/yuv420p, 1364×1024 preview-canvas output, 24 fps and three-second duration.
- Re-selecting the existing output is refused with an actionable visible error; the prior MP4 remains playable.
- Live export starts under a new filename. Opening the guide then pressing X cancels successfully; after normal app exit the cancelled path is absent.
- Screenshots were inspected for all four compact controls, the guide, successful save, overwrite refusal and guide-time cancellation.

These are focused Linux checks with temporary QA dependencies, not full supported-platform acceptance or proof of a shippable Linux bundle. Optional TLS/WebRTC plugin warnings (including missing OpenSSL 1.1) and internal GStreamer callback warnings remain. A clean, reproducible, provenance-reviewed native runtime is still required for delivery; all macOS/Windows native checks remain open.

At `b87671b`, PR CI passed while duplicate push CI exposed a scheduling-sensitive four-frame startup queue. The final revision uses a bounded queue targeting 64 MiB (at least one frame, at most 48) and includes a delayed-encoder-start regression that verifies all 24 frames are preserved. Overload remains an explicit failure, never silent dropping. Final exact-head CI and independent review of the runtime/startup-buffer delta must be checked before moving the draft forward.

## Repeated-load review repair

Independent review of `97e15a6` found that loading another clip after manual pause or completed full export inherited `paused=true`, preventing the draw loop from consuming its first frame and eventually producing a false decode timeout. The accepted-load transition now resets pause and starts a fresh first-frame window, only after any pending export completes. Opening/cancelling the picker leaves the old state unchanged.

Workflow tests invoke actual pause/full-export-completion and queued selection/load methods, replacing only native Movie construction. They verify cancellation preservation, old-pipeline stop, automatic next-clip playback, cleared paused/frozen caches, frame readiness and no false timeout after 16 seconds. Native repeated-use checks at `f199d6b` passed both manual-pause and completed-full-export → accepted next clip, with the replacement still previewing beyond 15 seconds. Cancelling the load picker preserved the old paused state.

The separate `Native object has been disposed` warning was inspected in the bundled library bytecode. Its stack terminates in `Movie$NewSampleListener` calling `Buffer.unmap` after the GL buffer-sink handoff. It was observed even with application frame reads confined to draw. That locates the failing library boundary but does not establish the root cause or prove a fix; it is tracked separately from the deterministic pause-state defect. No third-party binary or undocumented global video flag was changed to suppress it.

## GPU snapshot repair in progress

Native colour-bar testing separately reproduced black pause/end snapshots while GPU playback remained visible. Bundled `Movie.get()` reads transient native/CPU buffers through `Texture.getBufferPixels`; that method can return without copying when its pending/used buffer lists are empty. The application now reads the rendered source texture with `Texture.get` on the render thread instead of depending on that buffer lifetime. A dedicated desktop/OpenGL regression deliberately makes CPU pixels black after uploading a four-colour texture, then checks exact GPU snapshot colours, dimensions, orientation and repeated independent copies. This test is explicitly separate from headless CI and awaits desktop execution.

Manual freeze captures the last pre-GUI render rather than the decorated screen, so controls/HUD cannot enter its exported pixels. A headless crop regression verifies this path. Pause requests before first-frame readiness show a loading message instead of blocking decoding. The underlying native-object-disposed warning is not hidden or claimed fixed by this snapshot change.
