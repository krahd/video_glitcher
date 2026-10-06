#!/usr/bin/env python3
"""Inspect and stage existing ZIPs for INTERNAL provenance work, never publication.

The final manifest is the completion marker. Existing output paths are always
refused; a failed attempt may leave a partial directory for inspection. Nothing
in an existing destination is deleted, replaced or treated as a ready build.
"""
from __future__ import annotations

import argparse
from contextlib import ExitStack
import hashlib
import io
import json
import os
import re
import shutil
import stat
import subprocess
import zipfile
from datetime import datetime, timezone
from pathlib import Path, PurePosixPath

PLATFORMS = {
    "macos-aarch64": "run-macos.sh",
    "linux-amd64": "run-linux.sh",
    "windows-amd64": "run-windows.bat",
}
ASSETS = tuple(f"video_glitcher-{platform}.zip" for platform in PLATFORMS)
MAX_ARCHIVE_BYTES = 2 * 1024**3
MAX_APP_JAR_BYTES = 16 * 1024**2
MAX_ENTRIES = 5000


def sha256(path: Path) -> str:
    with path.open("rb") as handle:
        return hashlib.file_digest(handle, "sha256").hexdigest()


def git_head(repo: Path) -> str:
    result = subprocess.run(["git", "-C", str(repo), "rev-parse", "HEAD"],
                            check=True, text=True, capture_output=True)
    return result.stdout.strip()


def tracked_paths(repo: Path) -> list[str]:
    result = subprocess.run(["git", "-C", str(repo), "ls-files", "-z"],
                            check=True, capture_output=True)
    return result.stdout.decode("utf-8").rstrip("\0").split("\0")


def expected_payloads(repo: Path, platform: str) -> dict[str, Path]:
    """Match the existing release recipe, using only tracked regular files.

    No broad directory-copy rule: untracked files, fixtures and personal media
    cannot become an accepted archive member. The app JAR is checked separately.
    """
    launcher = PLATFORMS[platform]
    payloads = {}
    jars = {"lib/controlP5/library", "lib/processing-opengl/library", "lib/video/library"}
    native = f"lib/video/library/{platform}/"
    for name in tracked_paths(repo):
        path = PurePosixPath(name)
        if name == "lib/core.jar" or (str(path.parent) in jars and path.suffix == ".jar"):
            destination = "video_glitcher/lib/" + path.name
        elif name.startswith(native):
            # The inspected native trees contain only these library kinds. New
            # notice/resource kinds need an explicit reviewed exception, not a
            # recursive-copy wildcard (or removal of their required notices).
            relative = name.removeprefix(native)
            suffix = {"linux-amd64": r"\.so(?:\.[0-9]+)*", "macos-aarch64": r"\.dylib",
                      "windows-amd64": r"\.dll"}[platform]
            if not re.fullmatch(r"(?:[A-Za-z0-9_.+-]+/)*[A-Za-z0-9_.+-]+" + suffix, relative):
                raise ValueError(f"Unreviewed native payload kind: {name}")
            destination = "video_glitcher/video/" + name.removeprefix("lib/video/library/")
        elif name == "packaging/portable/" + launcher:
            destination = "video_glitcher/" + launcher
        else:
            continue
        source = repo / name
        if source.is_symlink() or not source.is_file() or not source.resolve().is_relative_to(repo.resolve()):
            raise ValueError(f"Expected payload is not a contained regular file: {name}")
        if destination in payloads:
            raise ValueError(f"Flattened dependency filename collision: {destination}")
        payloads[destination] = source
    required = {"video_glitcher/lib/core.jar", "video_glitcher/lib/controlP5.jar",
                "video_glitcher/lib/video.jar", "video_glitcher/" + launcher}
    if not required.issubset(payloads) or not any(p.startswith(f"video_glitcher/video/{platform}/") for p in payloads):
        raise ValueError(f"Checkout lacks required {platform} payloads; use the complete matching checkout")
    return payloads


def checked_entries(archive: zipfile.ZipFile, allowed: set[str], max_bytes: int) -> dict[str, zipfile.ZipInfo]:
    """Validate without extracting or executing archive members."""
    infos = archive.infolist()
    if len(infos) > MAX_ENTRIES or sum(i.file_size for i in infos) > max_bytes:
        raise ValueError("Archive exceeds the internal inspection size/entry limit")
    directories = {str(parent) for name in allowed for parent in PurePosixPath(name).parents if str(parent) != "."}
    seen = set()
    nodes = {}
    files = {}
    for info in infos:
        name = info.filename.rstrip("/")
        parts = name.split("/")
        if (not name or any(p in ("", ".", "..") for p in parts) or "\\" in name
                or ":" in name or any(ord(c) < 32 for c in name)
                or name != info.orig_filename.rstrip("/")
                or info.filename != name + ("/" if info.is_dir() else "")):
            raise ValueError(f"Unsafe archive path: {info.orig_filename!r}")
        key = name.casefold()
        if key in seen:
            raise ValueError(f"Duplicate/case-colliding archive path: {name}")
        seen.add(key)
        # ZIP writers may omit directory entries. Check every implied prefix,
        # including file-versus-directory identity on case-insensitive targets.
        for length in range(1, len(parts) + 1):
            prefix = "/".join(parts[:length])
            kind = "directory" if length < len(parts) or info.is_dir() else "file"
            identity = (prefix, kind)
            old = nodes.setdefault(prefix.casefold(), identity)
            if old != identity:
                raise ValueError(f"Conflicting archive path component: {prefix}")
        mode = stat.S_IFMT(info.external_attr >> 16)
        if mode not in (0, stat.S_IFREG, stat.S_IFDIR) or (mode == stat.S_IFDIR and not info.is_dir()):
            raise ValueError(f"Archive links/special files are not accepted: {name}")
        if info.flag_bits & 1:
            raise ValueError(f"Encrypted archive entry: {name}")
        if info.is_dir():
            if name not in directories or info.file_size:
                raise ValueError(f"Unexpected archive directory: {name}")
        else:
            if name not in allowed:
                raise ValueError(f"Unexpected archive file: {name}")
            files[name] = info
    if set(files) != allowed:
        raise ValueError("Missing archive files: " + ", ".join(sorted(allowed - set(files))))
    return files


def inspect_app_jar(payload: bytes) -> int:
    """Check a narrow class-only app JAR; this does NOT prove its source commit."""
    with zipfile.ZipFile(io.BytesIO(payload)) as jar:
        files = {i.filename for i in jar.infolist() if not i.is_dir()}
        required = {f"tom/videoGlitcher/{name}.class" for name in
                    ("VideoGlitcher", "VideoGlitcherLogic", "FfmpegVideoExporter")}
        for name in files - {"META-INF/MANIFEST.MF"}:
            if not re.fullmatch(r"tom/videoGlitcher/(VideoGlitcher|VideoGlitcherLogic|FfmpegVideoExporter)(\$[A-Za-z0-9_$]+)?\.class", name):
                raise ValueError(f"Unexpected app JAR payload: {name}")
        if not required.issubset(files):
            raise ValueError("App JAR is missing an application entry class")
        entries = checked_entries(jar, files, MAX_APP_JAR_BYTES)
        majors = []
        for name, info in entries.items():
            # Read all bytes so CRC corruption cannot masquerade as valid header data.
            data = jar.read(info)
            if name.endswith(".class"):
                if len(data) < 8 or data[:4] != b"\xca\xfe\xba\xbe":
                    raise ValueError(f"Invalid class header: {name}")
                major = int.from_bytes(data[6:8], "big")
                if not 45 <= major <= 65:
                    raise ValueError(f"Class target outside the documented Java 21 contract: {major}")
                majors.append(major)
        return max(majors) - 44


def inspect_archive(path_or_handle, payloads: dict[str, Path]) -> dict:
    # Staging supplies an already-open regular-file handle anchored to its source
    # directory. Path use is a read-only inspection convenience for tests/tools.
    if isinstance(path_or_handle, Path):
        path = path_or_handle
        if path.is_symlink() or not path.is_file():
            raise ValueError(f"Archive must be a regular file, not a link: {path.name}")
        flags = os.O_RDONLY | getattr(os, "O_NOFOLLOW", 0)
        with os.fdopen(os.open(path, flags), "rb") as handle:
            return inspect_archive(handle, payloads)
    handle = path_or_handle
    metadata = os.fstat(handle.fileno())
    if not stat.S_ISREG(metadata.st_mode) or metadata.st_size > MAX_ARCHIVE_BYTES:
        raise ValueError("Archive must be a regular file within the inspection size limit")
    handle.seek(0)
    app_name = "video_glitcher/video_glitcher.jar"
    expected = set(payloads) | {app_name}
    with zipfile.ZipFile(handle) as archive:
        entries = checked_entries(archive, expected, MAX_ARCHIVE_BYTES)
        for name, source in payloads.items():
            if entries[name].file_size != source.stat().st_size:
                raise ValueError(f"Payload size differs from selected checkout: {name}")
            with archive.open(entries[name]) as entry:
                digest = hashlib.file_digest(entry, "sha256").hexdigest()
            if digest != sha256(source):
                raise ValueError(f"Payload bytes differ from selected checkout: {name}")
        if entries[app_name].file_size > MAX_APP_JAR_BYTES:
            raise ValueError("App JAR exceeds inspection limit")
        minimum_java = inspect_app_jar(archive.read(entries[app_name]))
    return {"entryCount": len(expected), "payloadComparison": "matches-selected-checkout",
            "appJarCheck": "class-allowlist-and-headers-only", "minimumJavaFromAppClasses": minimum_java}


def supports_safe_staging() -> bool:
    return (hasattr(os, "O_DIRECTORY") and hasattr(os, "O_NOFOLLOW")
            and all(function in os.supports_dir_fd for function in (os.open, os.mkdir, os.stat))
            and os.stat in os.supports_follow_symlinks)


def open_directory(stack: ExitStack, path, *, parent_fd=None) -> int:
    fd = os.open(path, os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW, dir_fd=parent_fd)
    stack.callback(os.close, fd)
    return fd


def open_regular_at(directory_fd: int, name: str, *, create: bool = False):
    flags = os.O_NOFOLLOW | (os.O_RDWR | os.O_CREAT | os.O_EXCL if create else os.O_RDONLY)
    fd = os.open(name, flags, 0o600, dir_fd=directory_fd)
    if not stat.S_ISREG(os.fstat(fd).st_mode):
        os.close(fd)
        raise ValueError(f"Expected regular file: {name}")
    return os.fdopen(fd, "w+b" if create else "rb")


def assert_directory_identity(parent_fd: int, name: str, directory_fd: int) -> None:
    current = os.stat(name, dir_fd=parent_fd, follow_symlinks=False)
    opened = os.fstat(directory_fd)
    if not stat.S_ISDIR(current.st_mode) or (current.st_dev, current.st_ino) != (opened.st_dev, opened.st_ino):
        raise ValueError("Staging directory identity changed; partial output retained, no completion manifest")


def write_text_at(directory_fd: int, name: str, text: str) -> None:
    with open_regular_at(directory_fd, name, create=True) as handle:
        handle.write(text.encode("utf-8"))


def stage_supported_build(repo: Path, source: Path, output: Path, version: str, *, internal_provenance_only: bool = False) -> dict:
    if not internal_provenance_only:
        raise RuntimeError("Release archives are not cleared for paid redistribution; see docs/REDISTRIBUTION-AUDIT.md")
    if not supports_safe_staging():
        raise RuntimeError("Internal staging requires POSIX directory-fd/no-follow capabilities; staging is unsupported on this platform")
    repo = repo.resolve(strict=True)
    source = source.resolve(strict=True)
    output = output.absolute()
    generated_root = repo / "dist"
    # A single new child is enough for internal inspection. No recursive parent
    # creation and no symlink-bearing intermediate path are supported.
    if output.parent != generated_root or output.name in ("", ".", ".."):
        raise ValueError("Output must be a NEW immediate child directory of this checkout's existing dist/ directory")
    if not version or any(ord(c) < 32 for c in version):
        raise ValueError("Version must be a non-empty single-line label")

    with ExitStack() as stack:
        repo_fd = open_directory(stack, repo)
        dist_fd = open_directory(stack, "dist", parent_fd=repo_fd)
        source_fd = open_directory(stack, source)
        try:
            os.stat(output.name, dir_fd=dist_fd, follow_symlinks=False)
        except FileNotFoundError:
            pass
        else:
            raise FileExistsError("Output already exists; choose a NEW generated directory. Nothing was removed")

        # Hold the original source/destination directories throughout preflight.
        # Later path replacement cannot redirect mkdir/open/write operations.
        staging_commit = git_head(repo)
        assets = []
        payloads_by_asset = {}
        for platform, name in zip(PLATFORMS, ASSETS):
            payloads = expected_payloads(repo, platform)
            payloads_by_asset[name] = payloads
            with open_regular_at(source_fd, name) as src:
                inspection = inspect_archive(src, payloads)
                src.seek(0)
                digest = hashlib.file_digest(src, "sha256").hexdigest()
                assets.append({"file": name, "sha256": digest, "bytes": os.fstat(src.fileno()).st_size,
                               "inspection": inspection})

        assert_directory_identity(repo_fd, "dist", dist_fd)
        # mkdir is exclusive even if somebody claims the leaf after preflight.
        os.mkdir(output.name, mode=0o700, dir_fd=dist_fd)
        output_fd = open_directory(stack, output.name, parent_fd=dist_fd)
        for asset in assets:
            with open_regular_at(source_fd, asset["file"]) as src, open_regular_at(output_fd, asset["file"], create=True) as dst:
                shutil.copyfileobj(src, dst)
                dst.flush()
                dst.seek(0)
                if hashlib.file_digest(dst, "sha256").hexdigest() != asset["sha256"]:
                    raise ValueError("Source archive changed during copy; partial output retained, no completion manifest")
                inspect_archive(dst, payloads_by_asset[asset["file"]])

        manifest = {
            "schema": 2, "product": "video_glitcher", "versionLabel": version,
            "stagingCheckoutCommit": staging_commit,
            "stagingCheckoutScope": "HEAD identifier only; working-tree cleanliness is not attested",
            "archiveSourceCommit": None,
            "archiveSourceVerification": "unverified; local HEAD and payload matching are not build provenance",
            "generatedAt": datetime.now(timezone.utc).isoformat(),
            "distribution": "internal-provenance-staging", "redistributionClearance": "not-cleared",
            "nativeRuntimeAcceptance": "not-established-by-packaging", "assets": assets,
        }
        write_text_at(output_fd, "SHA256SUMS", "".join(f"{asset['sha256']}  {asset['file']}\n" for asset in assets))
        write_text_at(output_fd, "README-SUPPORTED-BUILD.txt",
            "INTERNAL PROVENANCE STAGING ONLY - NOT CLEARED FOR DISTRIBUTION OR SALE\n\n"
            f"Requested version label (not verified): {version}\n"
            f"Staging checkout (not archive source): {staging_commit}\n\n"
            "Archive source commits are UNVERIFIED. Checksums identify these bytes only.\n"
            "Tracked dependency/launcher bytes match this checkout. App JAR inspection checks only\n"
            "class names/headers; it does not prove source, safe execution or native compatibility.\n"
            "Java 21 with desktop/AWT support and system ffmpeg with libx264 are external requirements.\n"
            "Linux native-link/dependency gaps and all platform acceptance/licensing gates remain.\n"
            "See docs/LOCAL-EVALUATION.md and docs/REDISTRIBUTION-AUDIT.md in the source checkout.\n"
            "Only a valid, complete support-manifest.json with matching checksums marks successful internal staging.\n")
        # Writes remain anchored even after a rename. Detect changed public names
        # before reporting success; never clean up somebody else's replacement.
        assert_directory_identity(repo_fd, "dist", dist_fd)
        assert_directory_identity(dist_fd, output.name, output_fd)
        write_text_at(output_fd, "support-manifest.json", json.dumps(manifest, indent=2, sort_keys=True) + "\n")
        return manifest


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--version", required=True, help="Unverified human version label, e.g. v1.1.3")
    parser.add_argument("--source", type=Path, default=Path("dist"), help="Directory containing the three release ZIPs")
    parser.add_argument("--output", type=Path, default=Path("dist/supported-build"), help="NEW immediate child directory of this checkout's existing dist/ (POSIX only)")
    parser.add_argument("--internal-provenance-only", action="store_true", help="Internal inspection only; does not clear redistribution")
    args = parser.parse_args()
    repo = Path(__file__).resolve().parents[1]
    try:
        manifest = stage_supported_build(repo, args.source, args.output, args.version, internal_provenance_only=args.internal_provenance_only)
    except (OSError, ValueError, RuntimeError, zipfile.BadZipFile, subprocess.CalledProcessError) as error:
        parser.exit(1, f"Staging refused: {error}\nA failed attempt may leave partial files; choose a new output path.\n")
    print(json.dumps({"status": "internal-staging-complete", "output": str(args.output), "assets": len(manifest["assets"]), "redistributionClearance": "not-cleared"}, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
