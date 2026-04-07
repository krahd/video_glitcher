#!/usr/bin/env python3

import argparse
import re
import sys
from pathlib import Path


FORMULA_TEMPLATE = """class VideoGlitcher < Formula
  desc \"Realtime desktop app for glitching video and exporting corrupted MP4 files\"
  homepage \"https://krahd.github.io/video_glitcher/\"
  version \"__VERSION__\"

  on_macos do
    url \"__MACOS_URL__\"
    sha256 \"__MACOS_SHA256__\"
  end

  on_linux do
    url \"__LINUX_URL__\"
    sha256 \"__LINUX_SHA256__\"
  end

  depends_on \"ffmpeg\"
  depends_on \"openjdk\"

  def install
    if OS.mac? && !Hardware::CPU.arm?
      odie \"video-glitcher only supports macOS Apple Silicon via Homebrew\"
    end

    if OS.linux? && !Hardware::CPU.intel?
      odie \"video-glitcher only supports Linux x86_64 via Homebrew\"
    end

    pkgshare.install \"video_glitcher\"
    release_dir = pkgshare/\"video_glitcher\"

    chmod 0755, release_dir/\"run-macos.sh\" if OS.mac?
    chmod 0755, release_dir/\"run-linux.sh\" if OS.linux?

    (bin/\"video_glitcher\").write <<~SH
      #!/bin/sh
      set -eu

      export PATH=\"#{Formula[\"ffmpeg\"].opt_bin}:#{Formula[\"openjdk\"].opt_bin}:$PATH\"
      BASE_DIR=\"#{release_dir}\"

      case \"$(uname -s)\" in
        Darwin)
          exec \"$BASE_DIR/run-macos.sh\" \"$@\"
          ;;
        Linux)
          exec \"$BASE_DIR/run-linux.sh\" \"$@\"
          ;;
        *)
          echo \"Unsupported operating system: $(uname -s)\" >&2
          exit 1
          ;;
      esac
    SH

    chmod 0755, bin/\"video_glitcher\"
  end

  test do
    assert_predicate pkgshare/\"video_glitcher/video_glitcher.jar\", :exist?
  end
end
"""


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Render the asset-based Homebrew formula for video_glitcher."
    )
    parser.add_argument("--release-tag", required=True)
    parser.add_argument("--macos-url", required=True)
    parser.add_argument("--macos-sha256", required=True)
    parser.add_argument("--linux-url", required=True)
    parser.add_argument("--linux-sha256", required=True)
    parser.add_argument("--output")
    return parser.parse_args()


def normalize_sha256(value: str, label: str) -> str:
    candidate = value.strip()
    if candidate.startswith("sha256:"):
        candidate = candidate.split(":", 1)[1]

    if not re.fullmatch(r"[0-9a-fA-F]{64}", candidate):
        raise SystemExit(f"Invalid {label} SHA-256 digest: {value}")

    return candidate.lower()


def render_formula(args: argparse.Namespace) -> str:
    version = args.release_tag[1:] if args.release_tag.startswith("v") else args.release_tag

    replacements = {
        "__VERSION__": version,
        "__MACOS_URL__": args.macos_url.strip(),
        "__MACOS_SHA256__": normalize_sha256(args.macos_sha256, "macOS"),
        "__LINUX_URL__": args.linux_url.strip(),
        "__LINUX_SHA256__": normalize_sha256(args.linux_sha256, "Linux"),
    }

    formula = FORMULA_TEMPLATE
    for key, value in replacements.items():
        formula = formula.replace(key, value)

    return formula


def main() -> int:
    args = parse_args()
    formula = render_formula(args)

    if args.output:
        output_path = Path(args.output)
        output_path.parent.mkdir(parents=True, exist_ok=True)
        output_path.write_text(formula, encoding="utf-8")
    else:
        sys.stdout.write(formula)

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
