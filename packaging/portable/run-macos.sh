#!/usr/bin/env sh
set -eu

BASE_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)

case "$(uname -m)" in
  arm64|aarch64)
    GST_DIR="$BASE_DIR/video/macos-aarch64"
    ;;
  x86_64)
    GST_DIR="$BASE_DIR/video/macos-x86_64"
    ;;
  *)
    echo "Unsupported macOS architecture: $(uname -m)" >&2
    exit 1
    ;;
esac

exec java -cp "$BASE_DIR/VideoGlitcher.jar:$BASE_DIR/lib/*" \
  -Dgstreamer.library.path="$GST_DIR" \
  -Dgstreamer.plugin.path="$GST_DIR/gstreamer-1.0" \
  tom.videoGlitcher.VideoGlitcher