#!/usr/bin/env bash

set -euo pipefail

usage() {
  cat <<'EOF'
Usage: capture-ui-macos.sh [options]

Build video_glitcher, launch the real fullscreen app on macOS Apple Silicon,
capture a screenshot, and stop the app unless --keep-running is set.

Options:
  --preset NAME       Preset to apply on launch. Default: Cinematic
  --video PATH        Absolute or repo-relative path to a video file
  --output PATH       Absolute or repo-relative PNG path
  --delay SECONDS     Seconds to wait before capturing. Default: 6
  --skip-build        Reuse the current bin/ output
  --keep-running      Leave the app running after capture
  -h, --help          Show this help text
EOF
}

script_dir="$(cd "$(dirname "$0")" && pwd)"
repo_root="$(cd "$script_dir/../../../.." && pwd)"

preset="Cinematic"
video_path=""
output_path=""
delay_seconds="6"
skip_build="0"
keep_running="0"

resolve_path() {
  local path_value="$1"

  if [[ "$path_value" = /* ]]; then
    printf '%s\n' "$path_value"
    return
  fi

  printf '%s\n' "$repo_root/$path_value"
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --preset)
      preset="${2:-}"
      shift 2
      ;;
    --video)
      video_path="${2:-}"
      shift 2
      ;;
    --output)
      output_path="${2:-}"
      shift 2
      ;;
    --delay)
      delay_seconds="${2:-}"
      shift 2
      ;;
    --skip-build)
      skip_build="1"
      shift
      ;;
    --keep-running)
      keep_running="1"
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      usage >&2
      exit 1
      ;;
  esac
done

if [[ "$(uname -s)" != "Darwin" ]]; then
  echo "This helper only supports macOS." >&2
  exit 1
fi

if [[ "$(uname -m)" != "arm64" ]]; then
  echo "This helper is configured for macOS Apple Silicon (arm64)." >&2
  exit 1
fi

if ! command -v java >/dev/null 2>&1 || ! command -v javac >/dev/null 2>&1; then
  echo "java and javac must be available on PATH." >&2
  exit 1
fi

if ! command -v ffmpeg >/dev/null 2>&1; then
  echo "ffmpeg must be available on PATH." >&2
  exit 1
fi

if ! command -v screencapture >/dev/null 2>&1; then
  echo "screencapture is required for macOS UI capture." >&2
  exit 1
fi

if ! [[ "$delay_seconds" =~ ^[0-9]+([.][0-9]+)?$ ]]; then
  echo "--delay must be a positive number." >&2
  exit 1
fi

cd "$repo_root"
mkdir -p dist/ui-review

if [[ -n "$video_path" ]]; then
  video_path="$(resolve_path "$video_path")"
else
  video_path="$repo_root/dist/ui-review/ui-sample.mp4"
  if [[ ! -f "$video_path" ]]; then
    ffmpeg -y -f lavfi -i testsrc=size=1280x720:rate=24 -t 6 -pix_fmt yuv420p "$video_path" >/dev/null 2>&1
  fi
fi

if [[ ! -f "$video_path" ]]; then
  echo "Video file not found: $video_path" >&2
  exit 1
fi

if [[ -n "$output_path" ]]; then
  output_path="$(resolve_path "$output_path")"
else
  timestamp="$(date +%Y%m%d-%H%M%S)"
  output_path="$repo_root/dist/ui-review/video-glitcher-ui-$timestamp.png"
fi

mkdir -p "$(dirname "$output_path")"

classpath="bin:lib/core.jar:lib/controlP5/library/*:lib/processing-opengl/library/*:lib/video/library/*"
gstreamer_path="$repo_root/lib/video/library/macos-aarch64"
plugin_path="$gstreamer_path/gstreamer-1.0"
capture_log="$repo_root/dist/ui-review/capture.log"
latest_output="$repo_root/dist/ui-review/latest.png"

if [[ "$skip_build" = "0" ]]; then
  javac -cp "$classpath" -d bin \
    src/tom/videoGlitcher/VideoGlitcher.java \
    src/tom/videoGlitcher/VideoGlitcherLogic.java \
    src/tom/videoGlitcher/FfmpegVideoExporter.java
fi

app_pid=""

cleanup() {
  if [[ -n "$app_pid" ]] && kill -0 "$app_pid" >/dev/null 2>&1; then
    kill "$app_pid" >/dev/null 2>&1 || true
    for _ in 1 2 3 4 5; do
      if ! kill -0 "$app_pid" >/dev/null 2>&1; then
        break
      fi
      sleep 1
    done
    if kill -0 "$app_pid" >/dev/null 2>&1; then
      kill -9 "$app_pid" >/dev/null 2>&1 || true
    fi
  fi
}

if [[ "$keep_running" = "0" ]]; then
  trap cleanup EXIT INT TERM
fi

java -cp "$classpath" \
  "-Dgstreamer.library.path=$gstreamer_path" \
  "-Dgstreamer.plugin.path=$plugin_path" \
  tom.videoGlitcher.VideoGlitcher \
  "--video=$video_path" \
  "--preset=$preset" \
  >"$capture_log" 2>&1 &
app_pid="$!"

osascript <<EOF >/dev/null 2>&1 || true
tell application "System Events"
  repeat 20 times
    try
      set frontmost of first process whose unix id is $app_pid to true
      exit repeat
    end try
    delay 0.25
  end repeat
end tell
EOF

sleep "$delay_seconds"

if ! kill -0 "$app_pid" >/dev/null 2>&1; then
  echo "video_glitcher exited before capture. See $capture_log" >&2
  exit 1
fi

screencapture -x "$output_path"
cp "$output_path" "$latest_output"

if [[ "$keep_running" = "1" ]]; then
  echo "Captured $output_path"
  echo "App is still running with PID $app_pid"
else
  echo "Captured $output_path"
fi