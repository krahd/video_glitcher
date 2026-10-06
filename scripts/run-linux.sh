#!/usr/bin/env bash
# Run the maintained Java app from a built source checkout; arguments match the existing CLI.
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ ! -f bin/tom/videoGlitcher/VideoGlitcher.class ]]; then
  echo 'Build first: bash scripts/check.sh' >&2
  exit 1
fi
exec java -cp 'bin:lib/core.jar:lib/controlP5/library/*:lib/processing-opengl/library/*:lib/video/library/*' \
  -Dgstreamer.library.path="$PWD/lib/video/library/linux-amd64" \
  -Dgstreamer.plugin.path="$PWD/lib/video/library/linux-amd64/gstreamer-1.0" \
  tom.videoGlitcher.VideoGlitcher "$@"
