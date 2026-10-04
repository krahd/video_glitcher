#!/usr/bin/env python3
"""Stage verified video_glitcher release ZIPs for a supported-download offering.

This does not publish releases, create tags, alter GitHub assets, or modify the
Homebrew tap. It copies existing release ZIPs into a separate ignored output
directory and adds deterministic checksums plus support/provenance metadata.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import shutil
import subprocess
from datetime import datetime, timezone
from pathlib import Path

ASSETS = (
    "video_glitcher-macos-aarch64.zip",
    "video_glitcher-linux-amd64.zip",
    "video_glitcher-windows-amd64.zip",
)


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def git_head(repo: Path) -> str:
    result = subprocess.run(
        ["git", "-C", str(repo), "rev-parse", "HEAD"],
        check=True,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )
    return result.stdout.strip()


def stage_supported_build(repo: Path, source: Path, output: Path, version: str, *, internal_provenance_only: bool = False) -> dict:
    if not internal_provenance_only:
        raise RuntimeError("v1.1.3 release archives are not cleared for paid redistribution; see docs/REDISTRIBUTION-AUDIT.md")
    missing = [name for name in ASSETS if not (source / name).is_file()]
    if missing:
        raise FileNotFoundError("Missing required release asset(s): " + ", ".join(missing))

    if output.exists():
        shutil.rmtree(output)
    output.mkdir(parents=True)

    assets = []
    checksum_lines = []
    for name in ASSETS:
        src = source / name
        dst = output / name
        shutil.copy2(src, dst)
        digest = sha256(dst)
        assets.append({"file": name, "sha256": digest, "bytes": dst.stat().st_size})
        checksum_lines.append(f"{digest}  {name}")

    (output / "SHA256SUMS").write_text("\n".join(checksum_lines) + "\n")
    manifest = {
        "schema": 1,
        "product": "video_glitcher",
        "version": version,
        "sourceCommit": git_head(repo),
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "distribution": "internal-provenance-staging",
        "redistributionClearance": "not-cleared",
        "licensing": "No DRM or licence key is added by this packaging step; upstream project licensing remains unchanged.",
        "supportScope": "Supported download packaging and installation/runtime troubleshooting for the packaged build; no guarantee of future feature work.",
        "assets": assets,
    }
    (output / "support-manifest.json").write_text(json.dumps(manifest, indent=2, sort_keys=True) + "\n")
    (output / "README-SUPPORTED-BUILD.txt").write_text(
        "video_glitcher supported build\n\n"
        f"Version: {version}\n"
        f"Source commit: {manifest['sourceCommit']}\n\n"
        "This directory contains the same platform release bundles prepared for a supported-download offering. "
        "No DRM or licence key has been added. Verify downloads against SHA256SUMS.\n\n"
        "Included platforms:\n- macOS Apple Silicon\n- Linux x86_64\n- Windows x86_64\n\n"
        "MP4 export requires ffmpeg on PATH. Native video playback depends on the bundled platform-specific Processing/GStreamer runtime.\n"
    )
    return manifest


def main() -> int:
    parser = argparse.ArgumentParser(description="Stage existing release ZIPs as a verified supported-build delivery set.")
    parser.add_argument("--version", required=True, help="Release version shown in the support manifest, e.g. v1.1.3")
    parser.add_argument("--source", type=Path, default=Path("dist"), help="Directory containing the three release ZIPs")
    parser.add_argument("--output", type=Path, default=Path("dist/supported-build"), help="Ignored staging directory")
    parser.add_argument("--internal-provenance-only", action="store_true", help="Allow internal checksum/provenance staging only; does not clear paid redistribution")
    args = parser.parse_args()
    repo = Path(__file__).resolve().parents[1]
    manifest = stage_supported_build(repo, args.source.resolve(), args.output.resolve(), args.version, internal_provenance_only=args.internal_provenance_only)
    print(json.dumps({"status": "ok", "output": str(args.output), "assets": len(manifest["assets"]), "version": args.version}, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
