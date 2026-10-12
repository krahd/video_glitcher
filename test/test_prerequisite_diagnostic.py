"""Prerequisite metadata and bounded subprocess checks, never native app acceptance."""
import importlib.util
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import time
import unittest
from unittest import mock

SCRIPT = Path(__file__).resolve().parents[1] / "scripts/diagnose_prerequisites.py"
spec = importlib.util.spec_from_file_location("prerequisites", SCRIPT)
diagnostic = importlib.util.module_from_spec(spec)
sys.modules[spec.name] = diagnostic
spec.loader.exec_module(diagnostic)


class PrerequisiteTests(unittest.TestCase):
    def inspect(self, version='openjdk version "21.0.8"', modules='java.base@21\njava.desktop@21',
                encoders='Encoders:\n V....D libx264 H.264 encoder', missing=(), overrides=None):
        calls = []
        outputs = {'-version': version, '--list-modules': modules, '-encoders': encoders}
        def run(command):
            calls.append(command)
            return (overrides or {}).get(command[-1], diagnostic.Probe('ok', outputs[command[-1]]))
        findings = diagnostic.inspect_prerequisites(
            locate=lambda name: None if name in missing else '/owned tools/' + name,
            run=run)
        return findings, calls

    def test_reported_prerequisites_and_literal_paths(self):
        findings, calls = self.inspect()
        self.assertEqual([f.state for f in findings], ['FOUND'] * 3)
        self.assertEqual(calls, [['/owned tools/java', '-version'],
                                 ['/owned tools/java', '--list-modules'],
                                 ['/owned tools/ffmpeg', '-hide_banner', '-encoders']])
        self.assertEqual(diagnostic.exit_code(findings), 0)
        report = diagnostic.format_report(findings)
        self.assertIn('/owned tools/java', report)
        self.assertIn('does not establish that the app will work', report)
        self.assertIn('Killed/137', report)

    def test_supported_version_spellings(self):
        for version in ['java version "21"', 'openjdk 21.0.1 2023-10-17', 'openjdk version "25-ea"']:
            with self.subTest(version=version):
                findings, _ = self.inspect(version=version)
                self.assertEqual(findings[0].state, 'FOUND')

    def test_old_or_unrecognised_versions(self):
        for version in ['java version "1.8.0_402"', 'openjdk version "17.0.1"']:
            with self.subTest(version=version):
                findings, _ = self.inspect(version=version)
                self.assertEqual(findings[0].state, 'MISSING')
                self.assertEqual(diagnostic.exit_code(findings), 1)
        for version in ['', 'wrapper 21', 'openjdk 21oops', 'error: requires openjdk version "21"']:
            with self.subTest(version=version):
                findings, _ = self.inspect(version=version)
                self.assertEqual(findings[0].state, 'UNKNOWN')
                self.assertEqual(diagnostic.exit_code(findings), 2)

    def test_oversized_version_components_are_unknown(self):
        for token in ['9' * 5000, '1.' + '9' * 5000, '21.' + '9' * 5000,
                      '9' * 10, '21' + '.1' * 100]:
            with self.subTest(length=len(token), prefix=token[:10]):
                findings, _ = self.inspect(version='openjdk version "' + token + '"')
                self.assertEqual(findings[0].state, 'UNKNOWN')
                self.assertEqual(diagnostic.exit_code(findings), 2)

    def test_missing_tools_do_not_execute(self):
        findings, calls = self.inspect(missing=('java', 'ffmpeg'))
        self.assertEqual(calls, [])
        self.assertEqual([f.state for f in findings], ['MISSING', 'UNKNOWN', 'MISSING'])
        self.assertEqual(diagnostic.exit_code(findings), 1)
        self.assertIn('PATH', diagnostic.format_report(findings))

    def test_missing_desktop_and_exact_encoder_name(self):
        for modules in ['java.base@21', 'java.base@21\njava.desktop.extra@21', 'java.base@21\nwarning: java.desktop@21']:
            findings, _ = self.inspect(modules=modules)
            self.assertEqual(findings[1].state, 'MISSING')
        for encoders in [' V....D libx264rgb RGB encoder', ' A....D libx264 audio',
                         'Please install libx264', ' V....D libx264-extra other']:
            findings, _ = self.inspect(encoders='Encoders:\n' + encoders)
            self.assertEqual(findings[2].state, 'MISSING')

    def test_malformed_successful_output_is_unknown(self):
        for modules in ['', 'warning: java.desktop@21', 'java.desktop@21']:
            findings, _ = self.inspect(modules=modules)
            self.assertEqual(findings[1].state, 'UNKNOWN')
        for encoders in ['', 'Please install libx264', ' V....D libx264 without a list header']:
            findings, _ = self.inspect(encoders=encoders)
            self.assertEqual(findings[2].state, 'UNKNOWN')

    def test_failed_probes_are_unknown_not_missing_capability(self):
        for key in ['-version', '--list-modules', '-encoders']:
            for state in ['timeout', 'output-limit', 'failed', 'unavailable']:
                with self.subTest(key=key, state=state):
                    findings, _ = self.inspect(overrides={key: diagnostic.Probe(state, '\x1b[31mowned error\x00')})
                    finding = findings[{'-version': 0, '--list-modules': 1, '-encoders': 2}[key]]
                    self.assertEqual(finding.state, 'UNKNOWN')
                    self.assertIn(state, finding.detail)
                    self.assertNotIn('\x1b', diagnostic.format_report(findings))
                    self.assertNotIn('\x00', diagnostic.format_report(findings))

    def test_metadata_commands_capture_stderr_and_nonzero(self):
        for exit_value, expected in [(0, 'ok'), (17, 'failed')]:
            probe = diagnostic.run_probe([sys.executable, '-c',
                f'import sys; print("owned output", file=sys.stderr); sys.exit({exit_value})'])
            self.assertEqual(probe.state, expected)
            self.assertIn('owned output', probe.output)

    def test_missing_executable_is_bounded(self):
        probe = diagnostic.run_probe(['/definitely-missing-owned-probe/java'])
        self.assertEqual(probe.state, 'unavailable')

    def test_literal_subprocess_argument(self):
        literal = 'owned path with spaces; $(must-not-run) & unicode-é'
        for encoding in ('utf-8', 'cp1252'):
            with self.subTest(child_console_encoding=encoding), mock.patch.dict(os.environ, PYTHONIOENCODING=encoding):
                # This checks argument transport, not the child's platform console codec.
                probe = diagnostic.run_probe([sys.executable, '-c',
                    'import sys; sys.stdout.buffer.write(sys.argv[1].encode("utf-8"))', literal])
                self.assertEqual(probe.state, 'ok')
                self.assertEqual(probe.output.strip(), literal)

    def test_non_utf8_output_is_replaced_without_crashing(self):
        probe = diagnostic.run_probe([sys.executable, '-c', 'import sys; sys.stdout.buffer.write(bytes([233]))'])
        self.assertEqual(probe.state, 'ok')
        self.assertEqual(probe.output, '\ufffd')

    def test_interruption_reaps_owned_child(self):
        with tempfile.TemporaryDirectory() as directory:
            marker = Path(directory) / 'child-survived'
            child = 'import time; from pathlib import Path; time.sleep(.7); Path(' + repr(str(marker)) + ').touch()'
            real_sleep = time.sleep
            interrupted = False
            def interrupt_once(seconds):
                nonlocal interrupted
                if not interrupted:
                    interrupted = True
                    raise KeyboardInterrupt
                real_sleep(seconds)
            with mock.patch.object(diagnostic.time, 'sleep', side_effect=interrupt_once):
                with self.assertRaises(KeyboardInterrupt):
                    diagnostic.run_probe([sys.executable, '-c', child])
            time.sleep(.8)
            self.assertFalse(marker.exists())

    def assert_setup_failure_reaps_child(self, target, error, start_first=False):
        with tempfile.TemporaryDirectory() as directory:
            marker = Path(directory) / 'child-survived'
            child = 'import time; from pathlib import Path; time.sleep(.7); Path(' + repr(str(marker)) + ').touch()'
            processes = []
            real_popen = subprocess.Popen
            real_start = diagnostic.threading.Thread.start
            def launch(*args, **kwargs):
                process = real_popen(*args, **kwargs)
                processes.append(process)
                return process
            def fail_start(reader):
                if start_first:
                    real_start(reader)
                raise error('owned setup failure')
            replacement = (mock.patch.object(diagnostic.threading.Thread, 'start', fail_start)
                           if target == 'start' else
                           mock.patch.object(diagnostic.threading, 'Event', side_effect=error('owned setup failure')))
            try:
                with mock.patch.object(diagnostic.subprocess, 'Popen', side_effect=launch), replacement:
                    with self.assertRaisesRegex(error, 'owned setup failure'):
                        diagnostic.run_probe([sys.executable, '-c', child])
                time.sleep(.8)
                self.assertFalse(marker.exists(), 'Setup failure left the owned child running')
                self.assertTrue(all(p.poll() is not None for p in processes))
            finally:
                for process in processes:
                    if process.poll() is None:
                        process.kill()
                    process.wait(timeout=2)
                    process.stdout.close()

    def test_reader_start_failure_reaps_child_without_joining_unstarted_thread(self):
        for error in (KeyboardInterrupt, RuntimeError):
            with self.subTest(error=error):
                self.assert_setup_failure_reaps_child('start', error)

    def test_interruption_after_reader_start_reaps_child(self):
        self.assert_setup_failure_reaps_child('start', KeyboardInterrupt, start_first=True)

    def test_post_spawn_event_setup_failure_reaps_child(self):
        self.assert_setup_failure_reaps_child('event', RuntimeError)

    def test_deadline_and_output_cap(self):
        start = time.monotonic()
        probe = diagnostic.run_probe([sys.executable, '-c', 'import time; time.sleep(30)'], timeout=.15)
        self.assertEqual(probe.state, 'timeout')
        self.assertLess(time.monotonic() - start, 3)
        probe = diagnostic.run_probe([sys.executable, '-c',
            'import os\nwhile True: os.write(1, b"x" * 4096)'], output_limit=1024)
        self.assertEqual(probe.state, 'output-limit')
        self.assertLessEqual(len(probe.output.encode()), 1024)

    @unittest.skipIf(os.name == 'nt', 'POSIX process-group cleanup contract')
    def test_inherited_pipe_cannot_hold_probe_open(self):
        with tempfile.TemporaryDirectory() as directory:
            marker = Path(directory) / 'child-survived'
            child = 'import time; from pathlib import Path; time.sleep(.7); Path(' + repr(str(marker)) + ').touch()'
            parent = 'import subprocess,sys; subprocess.Popen([sys.executable,"-c",' + repr(child) + '])'
            start = time.monotonic()
            probe = diagnostic.run_probe([sys.executable, '-c', parent], timeout=.2)
            self.assertEqual(probe.state, 'timeout')
            self.assertLess(time.monotonic() - start, 3)
            time.sleep(.8)
            self.assertFalse(marker.exists())

    def test_cli_missing_tools_is_actionable_and_does_not_start_app(self):
        with tempfile.TemporaryDirectory() as directory:
            result = subprocess.run([sys.executable, str(SCRIPT)], env=dict(os.environ, PATH=directory),
                                    capture_output=True, text=True, timeout=5)
        self.assertEqual(result.returncode, 1, result.stderr)
        self.assertIn('java was not found on PATH', result.stdout)
        self.assertIn('ffmpeg was not found on PATH', result.stdout)
        self.assertIn('No application, media or native video pipeline is opened', result.stdout)
        self.assertEqual(result.stderr, '')

    def test_cli_help_does_not_need_tools_and_rejects_unknown_options(self):
        with tempfile.TemporaryDirectory() as directory:
            for args, expected in [(['--help'], 0), (['--install'], 2)]:
                result = subprocess.run([sys.executable, str(SCRIPT), *args],
                                        env=dict(os.environ, PATH=directory),
                                        capture_output=True, text=True, timeout=5)
                self.assertEqual(result.returncode, expected)
                self.assertIn('usage:', result.stdout + result.stderr)
                self.assertNotIn('was not found on PATH', result.stdout)


if __name__ == '__main__':
    unittest.main()
