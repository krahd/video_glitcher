#!/usr/bin/env python3
"""Recreate validated in-tree Linux library links in a NEW generated runtime copy.

Some imported Processing runtime link entries are regular files containing their
relative targets. This preserves payload bytes and refuses unsafe or ambiguous
links; it does not establish binary provenance or redistribution permission.
"""
from __future__ import annotations
import argparse
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import shutil
import tempfile


def inspect_runtime(source: Path) -> dict[str, str]:
    source = source.resolve(strict=True)
    links: dict[str, str] = {}
    for path in source.rglob('*'):
        if path.is_symlink():
            links[path.relative_to(source).as_posix()] = os.readlink(path)
        elif path.is_file() and '.so' in path.name and path.stat().st_size < 256:
            if path.read_bytes().startswith(b'\x7fELF'):
                continue
            try:
                target = path.read_text(encoding='ascii').strip()
            except UnicodeDecodeError as error:
                raise ValueError(f'Unrecognised small library: {path.name}') from error
            if not target or any(c.isspace() for c in target):
                raise ValueError(f'Ambiguous library link: {path.name}')
            links[path.relative_to(source).as_posix()] = target
    resolved: dict[str, str] = {}
    for name in links:
        seen: set[str] = set()
        current = name
        while current in links:
            if current in seen:
                raise ValueError(f'Cyclic library link: {name}')
            seen.add(current)
            target = links[current]
            if PurePosixPath(target).is_absolute() or '\\' in target or '\x00' in target:
                raise ValueError(f'Absolute or invalid library link: {name}')
            current = os.path.normpath(str(PurePosixPath(current).parent / target))
            if current == '..' or current.startswith('../'):
                raise ValueError(f'Escaping library link: {name}')
        endpoint = source / current
        # Resolve real source symlinks too, and never follow them outside the source tree.
        if not endpoint.resolve().is_relative_to(source):
            raise ValueError(f'Escaping library target: {name}')
        if not endpoint.is_file():
            raise ValueError(f'Dangling library link: {name} -> {current}')
        with endpoint.open('rb') as payload:
            if payload.read(4) != b'\x7fELF':
                raise ValueError(f'Library link does not terminate at an ELF payload: {name}')
        resolved[name] = current
    return links


def materialize(source: Path, destination: Path) -> dict:
    source = source.resolve(strict=True)
    destination = destination.absolute()
    if destination.exists() or destination.is_symlink():
        raise ValueError('Destination already exists; choose a new generated directory')
    if destination.resolve().is_relative_to(source) or source.is_relative_to(destination.resolve()):
        raise ValueError('Source and destination must be separate trees')
    links = inspect_runtime(source)
    destination.parent.mkdir(parents=True, exist_ok=True)
    staging = Path(tempfile.mkdtemp(prefix='.video-glitcher-runtime-', dir=destination.parent))
    try:
        tree = staging / 'runtime'
        shutil.copytree(source, tree, symlinks=True)
        for name, target in links.items():
            path = tree / name
            path.unlink()
            path.symlink_to(target)
        # Resolve again after materialisation, not just against placeholder text.
        inspect_runtime(tree)
        payloads = {}
        for path in source.rglob('*'):
            relative = path.relative_to(source).as_posix()
            if path.is_file() and not path.is_symlink() and relative not in links:
                source_hash = hashlib.sha256(path.read_bytes()).hexdigest()
                if hashlib.sha256((tree / relative).read_bytes()).hexdigest() != source_hash:
                    raise ValueError(f'Payload changed during runtime copy: {relative}')
                payloads[relative] = source_hash
        manifest = {'schema': 1, 'links': links, 'sha256': payloads,
                    'redistribution_cleared': False}
        (tree / 'materialization-manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
        tree.rename(destination)
        return manifest
    finally:
        shutil.rmtree(staging)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', type=Path, default=Path(__file__).resolve().parents[1] / 'lib/video/library/linux-amd64')
    parser.add_argument('--destination', required=True, type=Path)
    args = parser.parse_args()
    try:
        manifest = materialize(args.source, args.destination)
    except (OSError, ValueError) as error:
        parser.exit(1, f'Runtime materialisation refused: {error}\n')
    print(f'Validated {len(manifest["links"])} relative library links; payload bytes unchanged. Redistribution hold remains.')

if __name__ == '__main__':
    main()
