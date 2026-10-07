#!/usr/bin/env bash
# Run only on a real desktop with a graphical JRE; build tests first with scripts/check.sh.
set -euo pipefail
cd "$(dirname "$0")/.."
exec java -cp 'bin:test-bin:lib/core.jar:lib/controlP5/library/*:lib/processing-opengl/library/*:lib/video/library/*' \
  tom.videoGlitcher.VideoGlitcherGpuSnapshotTest
