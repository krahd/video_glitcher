"""Argument/exit propagation, not GUI or native decoder acceptance."""
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest

REPO = Path(__file__).resolve().parents[1]


class PortableLauncherTests(unittest.TestCase):
    @unittest.skipIf(os.name == "nt", "POSIX launcher contract")
    def test_posix_launchers_forward_literal_arguments_and_failure(self):
        with tempfile.TemporaryDirectory(prefix="glitcher launcher ") as tmp:
            root = Path(tmp)
            tools = root / "fake-tools"
            tools.mkdir()
            capture = root / "arguments"
            java = tools / "java"
            java.write_text('#!/bin/sh\nprintf "%s\\0" "$@" > "$JAVA_ARGS_CAPTURE"\nexit 17\n')
            java.chmod(0o755)
            uname = tools / "uname"
            uname.write_text('#!/bin/sh\nprintf "arm64\\n"\n')
            uname.chmod(0o755)
            env = dict(os.environ, PATH=str(tools) + os.pathsep + os.environ.get("PATH", ""), JAVA_ARGS_CAPTURE=str(capture))
            args = ["--smoke-test", "--video=/fixture path/owned clip.mp4", "--preset=VHS Decay", "--export-frames=48"]
            for launcher, platform in (("run-linux.sh", "linux-amd64"), ("run-macos.sh", "macos-aarch64")):
                with self.subTest(launcher=launcher):
                    target = root / launcher
                    shutil.copyfile(REPO / "packaging/portable" / launcher, target)
                    result = subprocess.run(["sh", str(target), *args], env=env, cwd="/", capture_output=True)
                    self.assertEqual(result.returncode, 17, result.stderr)
                    actual = capture.read_bytes().decode().split("\0")[:-1]
                    self.assertEqual(actual[-len(args):], args)
                    self.assertIn(f"-Dgstreamer.library.path={root}/video/{platform}", actual)
                    self.assertIn(f"{root}/video_glitcher.jar:{root}/lib/*", actual)
                    self.assertEqual(actual[-len(args)-1], "tom.videoGlitcher.VideoGlitcher")

    @unittest.skipUnless(os.name == "nt", "Native Windows command processor contract")
    def test_windows_launcher_forwards_spaced_arguments_and_failure(self):
        with tempfile.TemporaryDirectory(prefix="glitcher launcher ") as tmp:
            root = Path(tmp)
            tools = root / "fake-tools"
            tools.mkdir()
            capture = root / "arguments.json"
            helper = root / "capture.py"
            helper.write_text('import json, os, sys\nfrom pathlib import Path\nPath(os.environ["JAVA_ARGS_CAPTURE"]).write_text(json.dumps(sys.argv[1:]))\nraise SystemExit(17)\n')
            (tools / "java.bat").write_text(f'@echo off\n"{sys.executable}" "{helper}" %*\nexit /b %errorlevel%\n')
            launcher = root / "run-windows.bat"
            shutil.copyfile(REPO / "packaging/portable/run-windows.bat", launcher)
            env = dict(os.environ, PATH=str(tools) + os.pathsep + os.environ.get("PATH", ""), JAVA_ARGS_CAPTURE=str(capture))
            args = ["--smoke-test", r"--video=C:\fixture path\owned clip.mp4", "--preset=VHS Decay", "--export-frames=48"]
            command = f'""{launcher}" --smoke-test "{args[1]}" "{args[2]}" --export-frames=48"'
            result = subprocess.run("cmd.exe /d /s /v:off /c " + command, env=env, capture_output=True)
            self.assertEqual(result.returncode, 17, result.stderr)
            actual = json.loads(capture.read_text())
            self.assertEqual(actual[-len(args):], args)
            self.assertIn(f"-Dgstreamer.library.path={root}\\video\\windows-amd64", actual)

    def test_documented_java_target_matches_ci_and_existing_release_recipe(self):
        for workflow in ("check.yml", "release.yml"):
            self.assertIn("java-version: '21'", (REPO / ".github/workflows" / workflow).read_text())
        self.assertIn("Java 21", (REPO / "README.md").read_text())
        self.assertNotIn("Java 17 or newer", (REPO / "README.md").read_text())

    def test_actual_compiled_application_class_target(self):
        path = REPO / "bin/tom/videoGlitcher/VideoGlitcher.class"
        if not path.is_file():
            self.skipTest("Full app build absent; CI runs this test after the full compile")
        header = path.read_bytes()[:8]
        self.assertEqual(header[:4], b"\xca\xfe\xba\xbe")
        self.assertEqual(int.from_bytes(header[6:8], "big"), 65, "Expected the documented Java 21 class target")


if __name__ == "__main__":
    unittest.main()
