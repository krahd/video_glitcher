"""Owned Bash/command doubles; never a native video, GUI or decoder launch."""
import json
import os
from pathlib import Path
import shlex
import shutil
import subprocess
import sys
import tempfile
import unittest

REPO = Path(__file__).resolve().parents[1]
CLASSPATH = "bin:lib/core.jar:lib/controlP5/library/*:lib/processing-opengl/library/*:lib/video/library/*"
MAIN = "tom.videoGlitcher.VideoGlitcher"


@unittest.skipIf(os.name == "nt", "Linux source Bash launcher contract")
class SourceLauncherHelpTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(prefix="glitcher source help ü ")
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name).resolve()
        self.bash = shutil.which("bash")
        if self.bash is None:
            self.skipTest("Bash unavailable")
        (self.root / "scripts").mkdir()
        self.launcher = self.root / "scripts/run-linux.sh"
        shutil.copyfile(REPO / "scripts/run-linux.sh", self.launcher)
        self.compiled = self.root / "bin/tom/videoGlitcher/VideoGlitcher.class"
        self.compiled.parent.mkdir(parents=True)
        self.compiled.write_bytes(b"owned class-presence marker, not Java bytecode")
        self.tools = self.root / "tools"
        self.tools.mkdir()
        self.probes = self.root / "probes.jsonl"
        self.capture = self.root / "java.json"
        self.runtime_tmp = self.root / "owned runtime temp"
        self.helper = self.root / "owned_command.py"
        self.helper.write_text('''import json, os, sys
from pathlib import Path
kind, args = sys.argv[1], sys.argv[2:]
entry = {"kind": kind, "argv": args, "loader": os.environ.get("LD_LIBRARY_PATH")}
with Path(os.environ["OWNED_PROBES"]).open("a", encoding="utf-8") as out:
    out.write(json.dumps(entry) + "\\n")
if kind == "java":
    data = {"argv": args, "cwd": os.getcwd(), "env": {key: os.environ.get(key) for key in
        ("LD_LIBRARY_PATH", "FONTCONFIG_PATH", "VIDEO_GLITCHER_FFMPEG_LD_LIBRARY_PATH")}}
    Path(os.environ["OWNED_JAVA_CAPTURE"]).write_text(json.dumps(data), encoding="utf-8")
    print("owned Java stub output; not application acceptance")
    raise SystemExit(int(os.environ.get("OWNED_JAVA_EXIT", "17")))
if os.environ.get("OWNED_ALLOW_NATIVE_ROUTE") != "1":
    raise SystemExit(42)
if kind == "mktemp":
    target = Path(os.environ["OWNED_RUNTIME_TMP"])
    target.mkdir()
    print(target)
elif kind == "python3":
    Path(args[args.index("--destination") + 1]).mkdir()
elif kind == "ldd" and os.environ.get("OWNED_MISSING_LIBRARY") == "1":
    print("libowned-missing.so => not found")
''', encoding="utf-8")
        for name in ("java", "mktemp", "python3", "ldd"):
            path = self.tools / name
            path.write_text("#!/bin/sh\nexec " + shlex.quote(sys.executable) + " " + shlex.quote(str(self.helper))
                            + " " + name + ' "$@"\n', encoding="utf-8")
            path.chmod(0o755)
        self.env = dict(os.environ, PATH=str(self.tools) + os.pathsep + os.environ.get("PATH", ""),
                        OWNED_PROBES=str(self.probes), OWNED_JAVA_CAPTURE=str(self.capture),
                        OWNED_RUNTIME_TMP=str(self.runtime_tmp), OWNED_JAVA_EXIT="17",
                        LD_LIBRARY_PATH="/owned/original library path", FONTCONFIG_PATH="/owned/font config",
                        VIDEO_GLITCHER_FFMPEG_LD_LIBRARY_PATH="/owned/previous marker")
        self.env.pop("VIDEO_GLITCHER_NATIVE_DIR", None)
        self.env.pop("OWNED_ALLOW_NATIVE_ROUTE", None)
        self.env.pop("OWNED_MISSING_LIBRARY", None)

    def run_launcher(self, args, **changes):
        self.probes.unlink(missing_ok=True)
        self.capture.unlink(missing_ok=True)
        env = dict(self.env, **changes)
        return subprocess.run([self.bash, str(self.launcher), *args], cwd="/", env=env,
                              capture_output=True, text=True, timeout=15)

    def entries(self):
        return [json.loads(line) for line in self.probes.read_text(encoding="utf-8").splitlines()] if self.probes.exists() else []

    def test_exact_help_skips_native_preflights_and_preserves_arguments_environment(self):
        for args in (["--help"], ["--help", "--help"], ["--windowed", "--help", "--preset=VHS Decay"],
                     ["--video=/owned/clip with spaces ü; $(literal).mp4", "--smoke-test", "--auto-process", "--help"],
                     ["--", "--help"]):
            for override in ({}, {"VIDEO_GLITCHER_NATIVE_DIR": "/owned/absent-native-runtime"}):
                with self.subTest(args=args, override=override):
                    result = self.run_launcher(args, **override)
                    self.assertEqual(result.returncode, 17, result.stderr)
                    self.assertEqual([entry["kind"] for entry in self.entries()], ["java"])
                    data = json.loads(self.capture.read_text(encoding="utf-8"))
                    self.assertEqual(data["argv"], ["-cp", CLASSPATH, MAIN, *args])
                    self.assertEqual(Path(data["cwd"]), self.root)
                    self.assertEqual(data["env"], {key: self.env[key] for key in data["env"]})
                    self.assertFalse(self.runtime_tmp.exists())

    def test_normal_and_lookalike_flags_keep_managed_preflights_environment_cleanup(self):
        for args in ([], ["--windowed"], ["--help=true"], ["--helpful"], ["--HELP"], ["-h"], ["--video=--help"]):
            with self.subTest(args=args):
                result = self.run_launcher(args, OWNED_ALLOW_NATIVE_ROUTE="1")
                self.assertEqual(result.returncode, 17, result.stderr)
                self.assertEqual([entry["kind"] for entry in self.entries()], ["mktemp", "python3", "ldd", "java"])
                runtime = self.runtime_tmp / "runtime"
                data = json.loads(self.capture.read_text(encoding="utf-8"))
                self.assertEqual(data["argv"], ["-cp", CLASSPATH, f"-Dgstreamer.library.path={runtime}",
                                              f"-Dgstreamer.plugin.path={runtime}/gstreamer-1.0", MAIN, *args])
                self.assertEqual(data["env"]["LD_LIBRARY_PATH"], str(runtime) + ":" + self.env["LD_LIBRARY_PATH"])
                self.assertEqual(data["env"]["VIDEO_GLITCHER_FFMPEG_LD_LIBRARY_PATH"], self.env["LD_LIBRARY_PATH"])
                self.assertEqual(data["env"]["FONTCONFIG_PATH"], self.env["FONTCONFIG_PATH"])
                self.assertFalse(self.runtime_tmp.exists(), "Normal nonzero exit still cleans the owned managed runtime")

    def test_normal_override_keeps_preflight_without_materialisation(self):
        override = self.root / "owned native override"
        override.mkdir()
        result = self.run_launcher(["--video=/owned/clip.mp4"], VIDEO_GLITCHER_NATIVE_DIR=str(override), OWNED_ALLOW_NATIVE_ROUTE="1")
        self.assertEqual(result.returncode, 17, result.stderr)
        self.assertEqual([entry["kind"] for entry in self.entries()], ["ldd", "java"])
        self.assertTrue(override.is_dir())
        self.assertFalse(self.runtime_tmp.exists())

    def test_missing_native_dependency_still_blocks_normal_launch(self):
        result = self.run_launcher(["--windowed"], OWNED_ALLOW_NATIVE_ROUTE="1", OWNED_MISSING_LIBRARY="1")
        self.assertEqual(result.returncode, 1, result.stderr)
        self.assertIn("libowned-missing.so", result.stderr)
        self.assertEqual([entry["kind"] for entry in self.entries()], ["mktemp", "python3", "ldd"])
        self.assertFalse(self.capture.exists())
        self.assertFalse(self.runtime_tmp.exists())

    def test_missing_build_still_blocks_help_before_any_command(self):
        self.compiled.unlink()
        result = self.run_launcher(["--help"])
        self.assertEqual(result.returncode, 1)
        self.assertIn("Build first", result.stderr)
        self.assertEqual(self.entries(), [])

    def test_help_propagates_java_success_and_failure(self):
        for status in (0, 23):
            result = self.run_launcher(["--help"], OWNED_JAVA_EXIT=str(status))
            self.assertEqual(result.returncode, status, result.stderr)
            self.assertIn("owned Java stub output", result.stdout)
            self.assertEqual([entry["kind"] for entry in self.entries()], ["java"])

    def test_help_still_requires_java(self):
        (self.tools / "java").unlink()
        dirname = shutil.which("dirname")
        self.assertIsNotNone(dirname)
        helper = self.tools / "dirname"
        helper.write_text("#!/bin/sh\nexec " + shlex.quote(dirname) + ' "$@"\n', encoding="utf-8")
        helper.chmod(0o755)
        result = self.run_launcher(["--help"], PATH=str(self.tools))
        self.assertEqual(result.returncode, 127, result.stderr)
        self.assertIn("java", result.stderr)
        self.assertEqual(self.entries(), [])


if __name__ == "__main__":
    unittest.main()
