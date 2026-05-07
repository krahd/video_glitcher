# AGENTS.md

Repository instructions for AI coding agents working in this project.

This file is the durable source of truth for GitHub Copilot, OpenAI Codex, Claude Code, and compatible coding agents. Read it before making changes.

## 1: Non-negotiable rules

- Keep `STATUS.md` accurate at all times.
- `STATUS.md` must exist in the repository root.
- Do not finish a task that changes the project without reviewing and, when needed, updating `STATUS.md`.
- Do not invent project facts. Inspect the repository and record uncertainty explicitly.
- Do not overwrite user work or unrelated changes.
- Do not commit secrets, credentials, tokens, private keys, local environment files, generated build output, release archives, exported videos, or generated sensitive data.
- Prefer small, focused changes over broad rewrites.
- Preserve user-facing controls, CLI/smoke flags, release asset names, and Homebrew formula behaviour unless explicitly asked to change them.
- Verify meaningful changes with the narrowest reliable command available.
- Do not claim tests, builds, smoke tests, or release checks passed unless they were actually run.

## 2: Communication style

Use terse, factual, technical communication. Do not use playful, whimsical, cute, decorative, or filler progress phrases such as "combobulating", "cooking", "thinking...", "working on it", "let me dive in", "I'll get started", or "working my magic".

Allowed status-update style: "Reading files." "Found the issue." "Applying patch." "Tests passed." "Tests failed: <reason>."

No jokes, metaphors, fake enthusiasm, anthropomorphising, or decorative progress messages. Prefer concise present-tense technical updates. Use British English for prose documentation unless the repository consistently uses another variant.

## 3: Standard work loop

1. Read this file and `STATUS.md` before editing.
2. Inspect relevant files, README sections, VS Code tasks, Java sources, packaging scripts, release workflows, and tests.
3. Identify the smallest safe change.
4. Search call sites before changing controls, presets, smoke flags, exporter behaviour, packaging scripts, release workflow names, formula rendering, or launcher scripts.
5. Make focused edits.
6. Run relevant verification when possible.
7. Update documentation when behaviour, setup, architecture, commands, controls, packaging, or release state change.
8. Update `STATUS.md` if project state changed.
9. Report changed files, verification, and remaining issues.

## 4: Project-specific map

### 4.1: Project shape

- Purpose: Java/Processing video glitch application with real-time preview and MP4 export.
- Main runtime surface: `tom.videoGlitcher.VideoGlitcher`, a Java `PApplet` application.
- Primary language/framework: Java with Processing, Processing video/GStreamer, ControlP5, and ffmpeg export.
- Distribution: direct release ZIPs for macOS Apple Silicon, Linux x64, and Windows x64; Homebrew formula for supported tap installs.
- Current user-facing version line in README: `v1.1.3`.

### 4.2: Important paths

- `README.md`: human-facing overview, install, build, test, run, controls, smoke tests, and packaging notes.
- `STATUS.md`: complete current project status report; mandatory upkeep.
- `src/tom/videoGlitcher/VideoGlitcher.java`: main Processing application.
- `src/tom/videoGlitcher/VideoGlitcherLogic.java`: extracted pure Java logic covered by automated tests.
- `src/tom/videoGlitcher/FfmpegVideoExporter.java`: ffmpeg-backed export setup and process handling.
- `video_glitcher.pde`: original Processing sketch version.
- `test/tom/videoGlitcher/VideoGlitcherLogicTest.java`: logic test harness.
- `lib/`: bundled Processing, video, ControlP5, OpenGL jars, and platform-specific video natives.
- `bin/`: compiled class output.
- `dist/`: generated packaging output; should stay ignored.
- `.vscode/tasks.json`: build, run, test, smoke, and packaging tasks.
- `.vscode/launch.json`: debug launch configurations for supported platforms.
- `packaging/portable/`: portable launcher scripts.
- `packaging/homebrew/render_homebrew_formula.py`: Homebrew formula renderer.
- `Formula/video_glitcher.rb`: formula snapshot synced to the Homebrew tap.
- `.github/workflows/release.yml`: automated cross-platform release bundles.
- `.github/workflows/publish-homebrew-tap.yml`: tap publication workflow.

### 4.3: Safety invariants

- MP4 export depends on system `ffmpeg`; do not claim export works unless the path was verified.
- Processing video playback depends on bundled native GStreamer files matching the platform.
- Fullscreen/GUI behaviour often needs manual validation; logic tests do not prove interactive runtime behaviour.
- Release asset names and formula expectations must remain aligned with GitHub release workflows and Homebrew tap publishing.
- Smoke tests that load/export/process videos should use generated short sample clips or explicitly disposable files.
- Do not commit generated videos, `dist/` bundles, compiled outputs, or local exported media.

## 5: STATUS.md maintenance

`STATUS.md` is mandatory project state, not optional documentation.

Required timestamp line near the top:

```text
Last updated: YYYY-MM-DD HH:MM
```

Use 24-hour local time. If no other timezone is specified, use `America/Montevideo`. Duplicate the exact same line as the final line at the bottom of `STATUS.md`. Update both lines together.

`STATUS.md` must be a complete current snapshot, not a changelog. Include relevant sections for purpose, current implementation state, active focus, architecture, setup/run instructions, configuration, important files, recent changes, tests, risks, pending tasks, next steps, longer-term steps, and decisions.

## 6: Diagrams in STATUS.md

Include useful inline SVG architecture and flow diagrams when the structure is meaningful enough. Keep text inside boxes and canvas bounds. Keep arrows out of unrelated boxes and labels. Prefer generous spacing and simple SVG primitives. Update diagrams when architecture, module relationships, export flow, packaging shape, or release/deployment shape meaningfully changes.

## 7: Validation

Typical validation commands from the README:

```bash
javac -cp "lib/core.jar:lib/controlP5/library/*:lib/processing-opengl/library/*:lib/video/library/*" -d bin src/tom/videoGlitcher/VideoGlitcher.java src/tom/videoGlitcher/VideoGlitcherLogic.java src/tom/videoGlitcher/FfmpegVideoExporter.java
mkdir -p test-bin && javac -d test-bin src/tom/videoGlitcher/VideoGlitcherLogic.java src/tom/videoGlitcher/FfmpegVideoExporter.java test/tom/videoGlitcher/VideoGlitcherLogicTest.java && java -cp test-bin tom.videoGlitcher.VideoGlitcherLogicTest
```

macOS smoke command pattern:

```bash
java -cp "bin:lib/core.jar:lib/controlP5/library/*:lib/processing-opengl/library/*:lib/video/library/*" \
  -Dgstreamer.library.path="$PWD/lib/video/library/macos-aarch64" \
  -Dgstreamer.plugin.path="$PWD/lib/video/library/macos-aarch64/gstreamer-1.0" \
  tom.videoGlitcher.VideoGlitcher --smoke-test --smoke-frames=45
```

Rules:

- Run logic tests for logic/export filename/video-fit/state changes.
- Run build checks for Java source changes.
- Run smoke tests for runtime, video pipeline, export, process, GUI/control, or preset changes when environment allows.
- Record manual-validation gaps for fullscreen Processing, native video libraries, and ffmpeg export.

## 8: Versioning, release, and packaging

When changing version, packaging, or release behaviour, inspect and update all relevant files:

- `README.md`
- source/version constants if present
- `.github/workflows/release.yml`
- `.github/workflows/publish-homebrew-tap.yml`
- `Formula/video_glitcher.rb`
- `packaging/homebrew/render_homebrew_formula.py`
- `packaging/portable/`
- VS Code packaging tasks
- `STATUS.md`

Do not create release tags, publish release assets, or sync Homebrew taps unless explicitly requested.

## 9: Final response requirements

When finishing a task, report concisely: what changed, files changed, verification commands and results, whether `STATUS.md` was updated, and remaining issues or follow-up work.
