---
name: video-glitcher-ui-iteration
description: 'Run the video_glitcher desktop app, capture the current macOS interface as a screenshot, inspect the fullscreen Processing UI, and iterate on visual changes. Use when: reviewing the GUI, HUD, layout, spacing, fullscreen presentation, preset panel, or any interface change in this repo.'
argument-hint: 'Describe the part of the interface to inspect or change, optionally naming a preset to review.'
user-invocable: true
---

# Video Glitcher UI Iteration

Use this skill when the task depends on seeing the actual desktop interface before editing code.

## Important Constraints

- Visual review must use the normal app launch path. Do not use smoke mode for UI review because smoke mode hides both the GUI and the HUD during setup.
- The bundled helper is macOS Apple Silicon focused and uses the repo's vendored `macos-aarch64` video runtime.
- The helper launches the app with `--video=` and `--preset=` so screenshots show the real playback interface instead of the empty state.

## Procedure

1. Read the current UI code in `src/tom/videoGlitcher/VideoGlitcher.java` and the interface notes in `README.md`.
2. Capture the current UI with the helper script:

   ```sh
   bash ./.github/skills/video-glitcher-ui-iteration/scripts/capture-ui-macos.sh --preset "VHS Decay"
   ```

3. Optional helper flags:

   ```text
   --video /absolute/or/repo-relative/path/to/clip.mp4
   --output dist/ui-review/my-capture.png
   --delay 8
   --skip-build
   --keep-running
   ```

4. For named before/after comparisons, use the compare helper:

   ```sh
   bash ./.github/skills/video-glitcher-ui-iteration/scripts/capture-ui-compare-macos.sh --session panel-density-pass --shot before --preset "VHS Decay"
   ```

   After editing, re-run it with the same session name and `--shot after`. The helper keeps both PNGs together and refreshes a small `compare.html` page for side-by-side review.

5. Open the generated PNG from `dist/ui-review/` or the session `compare.html` page from `dist/ui-review/compare/` and inspect layout density, spacing, control balance, HUD legibility, and empty-state readability.
6. Edit only the relevant UI areas, usually the layout constants, `setupGui()`, `drawGui()`, `drawHUD()`, or preset/button sections in `src/tom/videoGlitcher/VideoGlitcher.java`.
7. Re-run the capture script after each meaningful visual change and compare the new screenshot against the prior one.
8. After visual edits, run the build and logic tests. Use smoke tasks only for runtime regression checks, not for screenshot review.

## Outputs

- Screenshot output lands in `dist/ui-review/`.
- The helper also refreshes `dist/ui-review/latest.png` so the newest capture is easy to inspect.
- The compare helper stores named shots in `dist/ui-review/compare/<session>/` and updates `compare.html` for side-by-side review.
- If the app exits early, inspect `dist/ui-review/capture.log` for launch failures.

## References

- [macOS capture helper](./scripts/capture-ui-macos.sh)
- [macOS compare helper](./scripts/capture-ui-compare-macos.sh)