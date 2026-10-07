#!/usr/bin/env bash
# Source-checkout launcher. No system libraries are changed or downloaded.
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ ! -f bin/tom/videoGlitcher/VideoGlitcher.class ]]; then
  echo 'Build first: bash scripts/check.sh' >&2
  exit 1
fi
if [[ -n "${VIDEO_GLITCHER_NATIVE_DIR:-}" ]]; then
  GST_DIR=$(cd "$VIDEO_GLITCHER_NATIVE_DIR" && pwd)
else
  RUNTIME_TMP=$(mktemp -d)
  trap 'rm -rf -- "$RUNTIME_TMP"' EXIT
  python3 scripts/materialize_linux_runtime.py --destination "$RUNTIME_TMP/runtime"
  GST_DIR="$RUNTIME_TMP/runtime"
fi
# Scope lookup to this process tree, not the shell/system. Avoid text link placeholders.
NATIVE_PATH="$GST_DIR${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}"
missing=$(LD_LIBRARY_PATH="$NATIVE_PATH" ldd "$GST_DIR/libgstreamer-1.0.so" "$GST_DIR/gstreamer-1.0/libgstlibav.so" | awk '/not found/ {print $1}' | sort -u)
if [[ -n "$missing" ]]; then
  printf 'Missing Linux video runtime dependencies:\n%s\n' "$missing" >&2
  echo 'Use a compatible, provenance-reviewed runtime. See docs/PRODUCT-CANDIDATE-2026-10-06.md. No system libraries were changed.' >&2
  exit 1
fi
VIDEO_GLITCHER_FFMPEG_LD_LIBRARY_PATH="${LD_LIBRARY_PATH:-}" FONTCONFIG_PATH="${FONTCONFIG_PATH:-/etc/fonts}" LD_LIBRARY_PATH="$NATIVE_PATH" java -cp 'bin:lib/core.jar:lib/controlP5/library/*:lib/processing-opengl/library/*:lib/video/library/*' \
  -Dgstreamer.library.path="$GST_DIR" \
  -Dgstreamer.plugin.path="$GST_DIR/gstreamer-1.0" \
  tom.videoGlitcher.VideoGlitcher "$@"
