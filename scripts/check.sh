#!/usr/bin/env bash
# Build + deterministic tests. Add --with-ffmpeg for real encoded-output regression tests.
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ $# -gt 1 || ( $# -eq 1 && "$1" != "--with-ffmpeg" ) ]]; then
  echo "Usage: bash scripts/check.sh [--with-ffmpeg]" >&2; exit 2
fi
separator=:
case "${OSTYPE:-}" in msys*|cygwin*) separator=';' ;; esac
classpath="lib/core.jar${separator}lib/controlP5/library/*${separator}lib/processing-opengl/library/*${separator}lib/video/library/*"
compiler=(javac)
if ! command -v javac >/dev/null; then
  # Some development runtimes contain jdk.compiler but omit the javac launcher.
  compiler=(java com.sun.tools.javac.Main)
  classpath=$(find lib -maxdepth 4 -name '*.jar' -print | paste -sd "$separator" -)
fi
mkdir -p bin test-bin
"${compiler[@]}" -cp "$classpath" -d bin src/tom/videoGlitcher/*.java
"${compiler[@]}" -cp "bin${separator}${classpath}" -d test-bin src/tom/videoGlitcher/VideoGlitcherLogic.java src/tom/videoGlitcher/FfmpegVideoExporter.java test/tom/videoGlitcher/*.java
java -cp test-bin tom.videoGlitcher.VideoGlitcherLogicTest
python3 -m unittest discover -s test -p 'test_*.py'
if [[ "${1:-}" == "--with-ffmpeg" ]]; then
  command -v ffmpeg >/dev/null
  command -v ffprobe >/dev/null
  java -cp test-bin tom.videoGlitcher.FfmpegVideoExporterTest
  java -Djava.awt.headless=true -cp "bin${separator}test-bin${separator}${classpath}" tom.videoGlitcher.VideoGlitcherWorkflowTest
fi
