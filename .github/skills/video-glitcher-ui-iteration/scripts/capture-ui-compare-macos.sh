#!/usr/bin/env bash

set -euo pipefail

usage() {
  cat <<'EOF'
Usage: capture-ui-compare-macos.sh [options]

Capture a named UI shot into a comparison session folder. Run once with
--shot before, make your edits, then run again with the same --session and
--shot after for direct comparison.

Options:
  --session NAME      Session folder name. Default: timestamp
  --shot NAME         Shot label. Default: before
  --preset NAME       Preset to apply on launch. Default: Cinematic
  --video PATH        Absolute or repo-relative path to a video file
  --delay SECONDS     Seconds to wait before capturing. Default: 6
  --skip-build        Reuse the current bin/ output
  --keep-running      Leave the app running after capture
  -h, --help          Show this help text
EOF
}

script_dir="$(cd "$(dirname "$0")" && pwd)"
repo_root="$(cd "$script_dir/../../../.." && pwd)"
capture_script="$script_dir/capture-ui-macos.sh"

session_name="$(date +%Y%m%d-%H%M%S)"
shot_name="before"
preset="Cinematic"
video_path=""
delay_seconds="6"
skip_build="0"
keep_running="0"

slugify() {
  printf '%s' "$1" | tr '[:upper:]' '[:lower:]' | sed -E 's/[^a-z0-9]+/-/g; s/^-+//; s/-+$//'
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --session)
      session_name="${2:-}"
      shift 2
      ;;
    --shot)
      shot_name="${2:-}"
      shift 2
      ;;
    --preset)
      preset="${2:-}"
      shift 2
      ;;
    --video)
      video_path="${2:-}"
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

if [[ -z "$session_name" ]]; then
  echo "--session must not be empty." >&2
  exit 1
fi

if [[ -z "$shot_name" ]]; then
  echo "--shot must not be empty." >&2
  exit 1
fi

session_slug="$(slugify "$session_name")"
shot_slug="$(slugify "$shot_name")"

if [[ -z "$session_slug" || -z "$shot_slug" ]]; then
  echo "Session and shot names must contain letters or numbers." >&2
  exit 1
fi

session_dir="$repo_root/dist/ui-review/compare/$session_slug"
output_path="$session_dir/$shot_slug.png"
compare_html="$session_dir/compare.html"
notes_file="$session_dir/README.txt"

mkdir -p "$session_dir"

capture_args=(--preset "$preset" --output "$output_path" --delay "$delay_seconds")

if [[ -n "$video_path" ]]; then
  capture_args+=(--video "$video_path")
fi

if [[ "$skip_build" = "1" ]]; then
  capture_args+=(--skip-build)
fi

if [[ "$keep_running" = "1" ]]; then
  capture_args+=(--keep-running)
fi

bash "$capture_script" "${capture_args[@]}"

python3 - <<'PY' "$compare_html" "$session_dir" "$session_name" "$shot_name" "$preset"
from html import escape
from pathlib import Path
import sys

compare_html = Path(sys.argv[1])
session_dir = Path(sys.argv[2])
session_name = sys.argv[3]
shot_name = sys.argv[4]
preset = sys.argv[5]

shots = sorted(path.name for path in session_dir.glob('*.png'))

def card(label, filename):
    image_path = filename if filename else ''
    body = f'<img src="{escape(image_path)}" alt="{escape(label)}">' if filename else '<div class="empty">Not captured yet</div>'
    return f'''<section class="card"><h2>{escape(label)}</h2>{body}</section>'''

before_name = 'before.png' if (session_dir / 'before.png').exists() else ''
after_name = 'after.png' if (session_dir / 'after.png').exists() else ''
other_cards = [card(path.stem, path.name) for path in sorted(session_dir.glob('*.png')) if path.name not in {'before.png', 'after.png'}]

html = f'''<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>video_glitcher UI compare: {escape(session_name)}</title>
  <style>
    :root {{ color-scheme: dark; --bg: #111; --panel: #1b1b1b; --text: #f2f2f2; --muted: #aaa; --accent: #d06647; }}
    * {{ box-sizing: border-box; }}
    body {{ margin: 0; font-family: -apple-system, BlinkMacSystemFont, sans-serif; background: var(--bg); color: var(--text); }}
    main {{ padding: 24px; }}
    h1 {{ margin: 0 0 8px; font-size: 28px; }}
    p {{ margin: 0 0 18px; color: var(--muted); }}
    .meta {{ margin-bottom: 20px; font-size: 14px; color: var(--muted); }}
    .grid {{ display: grid; grid-template-columns: repeat(auto-fit, minmax(420px, 1fr)); gap: 18px; }}
    .card {{ background: var(--panel); border: 1px solid rgba(255,255,255,0.08); border-radius: 14px; padding: 14px; }}
    h2 {{ margin: 0 0 10px; font-size: 18px; text-transform: capitalize; }}
    img {{ width: 100%; height: auto; display: block; border-radius: 10px; background: #000; }}
    .empty {{ min-height: 280px; display: grid; place-items: center; border-radius: 10px; background: rgba(255,255,255,0.03); color: var(--muted); }}
    .shots {{ margin-top: 20px; color: var(--muted); font-size: 14px; }}
    .accent {{ color: var(--accent); }}
  </style>
</head>
<body>
  <main>
    <h1>UI Compare</h1>
    <p>Session <span class="accent">{escape(session_name)}</span>. Most recent capture: {escape(shot_name)}. Preset: {escape(preset)}.</p>
    <div class="meta">Open this file in the browser for side-by-side review.</div>
    <div class="grid">
      {card('before', before_name)}
      {card('after', after_name)}
      {''.join(other_cards)}
    </div>
    <div class="shots">Available shots: {escape(', '.join(shots) if shots else 'none')}</div>
  </main>
</body>
</html>
'''

compare_html.write_text(html, encoding='utf-8')
PY

python3 - <<'PY' "$notes_file" "$session_name" "$shot_name" "$preset" "$output_path" "$compare_html"
from pathlib import Path
import sys

notes_file = Path(sys.argv[1])
session_name = sys.argv[2]
shot_name = sys.argv[3]
preset = sys.argv[4]
output_path = sys.argv[5]
compare_html = sys.argv[6]

notes_file.write_text(
    "\n".join([
        f"Session: {session_name}",
        f"Latest shot: {shot_name}",
        f"Preset: {preset}",
        f"Latest image: {output_path}",
        f"Compare page: {compare_html}",
    ]) + "\n",
    encoding='utf-8',
)
PY

echo "Updated comparison session: $session_dir"
echo "Shot saved to: $output_path"
echo "Compare page: $compare_html"