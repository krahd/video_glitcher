# Video Glitcher preview-to-export candidate

Date: 6 October 2026. Base: `7d4eae357a4b19755d0e0aa66971e6d42ffede13`.

## What this candidate improves

- Preset descriptions and an in-app workflow guide explain how to get from a clip to a result and disclose actual output limits.
- Hold-to-compare source preview leaves the effect settings, ghost cache and exported frame untouched.
- Both export modes have a destination picker. Existing files and symlink destinations are rejected, including the loaded source. Cancelling the picker does not start/restart playback.
- Encoding uses a private temporary file in the destination directory. Publication creates a same-directory hard link in one no-replace operation, so a file created by another application during encoding cannot be replaced. No error path deletes the destination, avoiding deletion of a replacement owned by another process. Filesystems without hard-link support (for example some removable/network volumes) fail clearly; use a supported local output folder.
- `X` cancels and discards; the existing `E` / `Stop Output` retains a partial recording. Normal application disposal aborts an unfinished export.
- Bounded stderr capture gives recovery information in the status bar. A four-frame queue moves pipe writes/close off the UI thread and fails rather than silently dropping frames when the encoder cannot keep up. Background finalisation has a 30-second deadline including the writer, pipe close and process exit. Cancellation is non-blocking; it is no longer accepted once the completed file starts its single publication operation. Invalid/empty frames and zero-frame exports fail rather than report success.
- HUD text is readable on its dark background; `U` now works in ordinary preview as already documented.

## Verification actually performed

Linux x86_64, OpenJDK 21.0.12.1, system ffmpeg/ffprobe 7.1.5:

- `bash scripts/check.sh --with-ffmpeg`: app compilation, Java logic tests, three Python packaging tests and 89 real-encoder integration assertions pass.
- Output is decoded and checked for red pixels, correct row stride/odd-edge cropping, H.264, even dimensions, 24 frames, 24 fps and one-second duration.
- Failure checks cover existing/source files, a destination appearing during encoding, symlinks, cancellation/retry, missing binary/folder, invalid frame/dimensions/fps, empty output, encoder error text and finalisation timeout.
- `git diff --check` passes.

This is headless build/export evidence, not desktop acceptance. The available cloud desktop has a separate filesystem; an attempted launch confirmed the executor script was absent there. A separate cloud desktop checkout was subsequently cloned and built. Its installed Java was headless, so a disposable official Temurin graphical JRE was downloaded without changing the system runtime. Normal-launch startup, guide readability/open-close and U hide/show were visually checked on Linux at code revision `8961eee`. This exposed a compact-panel height calculation that hid the fourth slider; the candidate now includes its fix, pending repeat visual validation. Native video load/export and macOS/Windows interaction acceptance remain pending. The new CI workflow checks builds/logic/packaging on three platforms and real encoding on Linux; its results must be verified for the final remote SHA.

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
