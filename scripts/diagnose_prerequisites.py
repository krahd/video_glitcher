#!/usr/bin/env python3
"""Report installed Java/encoder metadata without launching Video Glitcher."""
from __future__ import annotations

import argparse
from dataclasses import dataclass
import os
import re
import shutil
import signal
import subprocess
import sys
import threading
import time


@dataclass(frozen=True)
class Probe:
    state: str
    output: str


@dataclass(frozen=True)
class Finding:
    state: str
    name: str
    detail: str


def run_probe(command: list[str], timeout: float = 5, output_limit: int = 65536) -> Probe:
    """Bound captured bytes and waiting after spawn; never invoke a shell.

    POSIX probes have a private process group for timeout/overflow cleanup.
    Windows terminates the direct child only; PATH wrappers' descendants are
    not guaranteed to stop. A daemon reader cannot extend the caller deadline.
    Executable discovery/process creation and filesystem access are not timed.
    """
    process = None
    reader = None
    captured = bytearray()
    state = 'interrupted'
    try:
        try:
            process = subprocess.Popen(command, stdin=subprocess.DEVNULL, stdout=subprocess.PIPE,
                                       stderr=subprocess.STDOUT, start_new_session=os.name != 'nt')
        except OSError as error:
            return Probe('unavailable', str(error))
        # Every operation after a successful spawn belongs to the cleanup guard,
        # including event/thread construction and an interrupted Thread.start().
        done = threading.Event()
        overflow = threading.Event()
        read_failed = threading.Event()

        def read_output():
            try:
                with process.stdout:
                    while True:
                        block = process.stdout.read(4096)
                        if not block:
                            break
                        remaining = output_limit - len(captured)
                        captured.extend(block[:remaining])
                        if len(block) > remaining:
                            overflow.set()
                            break
            except (OSError, ValueError):
                read_failed.set()
            finally:
                done.set()

        reader = threading.Thread(target=read_output, daemon=True)
        reader.start()
        deadline = time.monotonic() + timeout
        while True:
            if overflow.is_set():
                state = 'output-limit'
                break
            if process.poll() is not None and done.is_set():
                state = 'failed' if process.returncode != 0 or read_failed.is_set() else 'ok'
                break
            if time.monotonic() >= deadline:
                state = 'timeout'
                break
            time.sleep(min(.02, max(0, deadline - time.monotonic())))
    finally:
        if process is not None:
            if state in ('timeout', 'output-limit', 'interrupted'):
                try:
                    if os.name != 'nt':
                        os.killpg(process.pid, signal.SIGKILL)
                    else:
                        process.kill()
                except OSError:
                    # Metadata remains unknown even if OS cleanup cannot be confirmed.
                    pass
                try:
                    process.wait(timeout=.5)
                except subprocess.TimeoutExpired:
                    pass
            if reader is not None and reader.ident is not None:
                reader.join(timeout=.1)
            else:
                # No started reader owns the pipe; joining an unstarted thread
                # would mask the original setup failure with RuntimeError.
                process.stdout.close()
    return Probe(state, bytes(captured).decode('utf-8', errors='replace'))


def clean_detail(value: str, limit: int = 240) -> str:
    return ' '.join(''.join(c if c.isprintable() else ' ' for c in value).split())[:limit]


def inspect_prerequisites(locate=shutil.which, run=run_probe) -> list[Finding]:
    findings = []
    java = locate('java')
    ffmpeg = locate('ffmpeg')

    def unknown(name, command, probe):
        return Finding('UNKNOWN', name,
                       f'{command}: {probe.state}. {clean_detail(probe.output)} '
                       'Check this installed command manually; no capability result was established.')

    if java is None:
        findings.append(Finding('MISSING', 'Java 21+',
                                'java was not found on PATH. Select an existing Java 21+ runtime with desktop/AWT support.'))
        findings.append(Finding('UNKNOWN', 'Desktop module', 'Java is unavailable; java.desktop was not checked.'))
    else:
        version = run([java, '-version'])
        match = re.search(r'^(?:openjdk|java)(?: version)?\s+"?([0-9]+(?:\.[0-9]+)*(?:[-+_][\w.-]+)?)(?=["\s]|$)',
                          version.output, re.MULTILINE)
        major = None
        if version.state == 'ok' and match and len(match.group(1)) <= 128:
            components = re.split(r'[-+_]', match.group(1), maxsplit=1)[0].split('.')
            if all(len(component) <= 9 for component in components):
                try:
                    major = int(components[0])
                    if major == 1 and len(components) > 1:
                        major = int(components[1])
                except ValueError:
                    major = None
        if major is None:
            findings.append(unknown('Java 21+', java, version if version.state != 'ok'
                                    else Probe('unrecognised version', version.output)))
        else:
            findings.append(Finding('FOUND' if major >= 21 else 'MISSING', 'Java 21+',
                                    f'{java} reports Java {major}; the compiled app requires at least Java 21. '
                                    'Version metadata does not verify runtime compatibility.'))
        modules = run([java, '--list-modules'])
        if modules.state != 'ok':
            findings.append(unknown('Desktop module', java, modules))
        elif not re.search(r'^java\.base(?:@[^\s]+)?\s*$', modules.output, re.MULTILINE):
            findings.append(unknown('Desktop module', java, Probe('unrecognised module list', modules.output)))
        elif re.search(r'^java\.desktop(?:@[^\s]+)?\s*$', modules.output, re.MULTILINE):
            findings.append(Finding('FOUND', 'Desktop module',
                                    f'{java} lists java.desktop. This does not test AWT, a display or a GPU.'))
        else:
            findings.append(Finding('MISSING', 'Desktop module',
                                    f'{java} does not list java.desktop. Select a Java runtime including desktop/AWT support.'))
    if ffmpeg is None:
        findings.append(Finding('MISSING', 'H.264 export encoder',
                                'ffmpeg was not found on PATH. MP4 export needs an existing ffmpeg build with libx264.'))
    else:
        encoders = run([ffmpeg, '-hide_banner', '-encoders'])
        if encoders.state != 'ok':
            findings.append(unknown('H.264 export encoder', ffmpeg, encoders))
        elif not re.search(r'^Encoders:\s*$', encoders.output, re.MULTILINE):
            findings.append(unknown('H.264 export encoder', ffmpeg, Probe('unrecognised encoder list', encoders.output)))
        elif re.search(r'^\s*V[FSXBD.]{5}\s+libx264(?:\s|$)', encoders.output, re.MULTILINE):
            findings.append(Finding('FOUND', 'H.264 export encoder',
                                    f'{ffmpeg} advertises libx264 video encoding; no encode was attempted.'))
        else:
            findings.append(Finding('MISSING', 'H.264 export encoder',
                                    f'{ffmpeg} does not advertise libx264 video encoding. Select an ffmpeg build that includes it.'))
    return findings


def exit_code(findings: list[Finding]) -> int:
    if any(f.state == 'MISSING' for f in findings):
        return 1
    return 2 if any(f.state == 'UNKNOWN' for f in findings) else 0


def format_report(findings: list[Finding]) -> str:
    lines = ['Video Glitcher prerequisite metadata (no app launch)', '']
    lines.extend(f'{f.state}: {f.name}: {clean_detail(f.detail, 600)}' for f in findings)
    lines.extend(['', 'FOUND means advertised prerequisites only; it does not establish that the app will work.',
                  'No application, media or native video pipeline is opened. No software is installed or configured.',
                  'Not checked: display/AWT operation, GStreamer/native dependencies, GPU, decoding, output storage or native stability.',
                  'See docs/LOCAL-EVALUATION.md for platform gates. The Killed/137 native retry stop remains in force.',
                  'Exit codes: 0 = metadata found, 1 = prerequisite missing, 2 = unknown (with none missing).'])
    return '\n'.join(lines)


def main() -> int:
    argparse.ArgumentParser(description=__doc__).parse_args()
    try:
        findings = inspect_prerequisites()
    except KeyboardInterrupt:
        print('Prerequisite diagnostic interrupted; no application was launched.', file=sys.stderr)
        return 130
    print(format_report(findings))
    return exit_code(findings)


if __name__ == '__main__':
    raise SystemExit(main())
