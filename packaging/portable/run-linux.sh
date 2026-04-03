#!/usr/bin/env sh
set -eu

BASE_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
GST_DIR="$BASE_DIR/video/linux-amd64"

exec java -cp "$BASE_DIR/video_glitcher.jar:$BASE_DIR/lib/*" \
  -Dgstreamer.library.path="$GST_DIR" \
  -Dgstreamer.plugin.path="$GST_DIR/gstreamer-1.0" \
  tom.videoGlitcher.VideoGlitcher
