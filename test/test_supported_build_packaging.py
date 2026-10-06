from __future__ import annotations

import importlib.util
import io
import json
import os
import shutil
import stat
import subprocess
import sys
import tempfile
import unittest
import warnings
import zipfile
from pathlib import Path
from unittest import mock

REPO = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("package_supported_build", REPO / "scripts/package_supported_build.py")
assert spec and spec.loader
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


def app_jar(extra=None, major=65):
    stream = io.BytesIO()
    with zipfile.ZipFile(stream, "w") as jar:
        jar.writestr("META-INF/", b"")
        jar.writestr("META-INF/MANIFEST.MF", b"Manifest-Version: 1.0\n")
        jar.writestr("tom/", b"")
        jar.writestr("tom/videoGlitcher/", b"")
        for name in ("VideoGlitcher", "VideoGlitcherLogic", "FfmpegVideoExporter"):
            jar.writestr(f"tom/videoGlitcher/{name}.class", b"\xca\xfe\xba\xbe\0\0" + major.to_bytes(2, "big") + b"synthetic header fixture")
        if extra:
            jar.writestr(*extra)
    return stream.getvalue()


class SupportedBuildPackagingTests(unittest.TestCase):
    """Only agent-owned synthetic payloads; no real media, binaries or user files."""
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="glitcher-packaging-test-")
        self.root = Path(self.temp.name)
        self.repo = self.root / "repo"
        self.repo.mkdir()
        self.source = self.repo / "dist" / "inputs"
        self.source.mkdir(parents=True)
        self.output = self.repo / "dist" / "new-output"
        self.tracked = []
        for name in ("lib/core.jar", "lib/controlP5/library/controlP5.jar", "lib/video/library/video.jar",
                     "lib/video/library/jna.jar", "lib/processing-opengl/library/jogl-all-2.6.0.jar"):
            self.add_payload(name)
        for platform, launcher in module.PLATFORMS.items():
            self.add_payload("packaging/portable/" + launcher)
            suffix = {"linux-amd64": ".so.1", "macos-aarch64": ".dylib", "windows-amd64": ".dll"}[platform]
            self.add_payload(f"lib/video/library/{platform}/runtime{suffix}")
        self.paths_patch = mock.patch.object(module, "tracked_paths", side_effect=lambda repo: list(self.tracked))
        self.paths_patch.start()
        self.head_patch = mock.patch.object(module, "git_head", return_value="a" * 40)
        self.head_patch.start()
        for platform, name in zip(module.PLATFORMS, module.ASSETS):
            self.write_archive(platform, name)

    def tearDown(self):
        self.head_patch.stop()
        self.paths_patch.stop()
        self.temp.cleanup()

    def add_payload(self, name):
        path = self.repo / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(("synthetic fixture: " + name).encode())
        self.tracked.append(name)

    def write_archive(self, platform, name, *, extra=None, omit=None, jar=None):
        with zipfile.ZipFile(self.source / name, "w") as archive:
            for destination, path in module.expected_payloads(self.repo, platform).items():
                if destination != omit:
                    archive.writestr(destination, path.read_bytes())
            archive.writestr("video_glitcher/video_glitcher.jar", jar or app_jar())
            if extra:
                archive.writestr(*extra)

    def stage(self, **kwargs):
        if kwargs.get("internal_provenance_only", True) and not module.supports_safe_staging():
            self.skipTest("Internal staging requires POSIX directory-fd/no-follow capabilities")
        return module.stage_supported_build(self.repo, self.source, kwargs.pop("output", self.output), "v1.1.3",
                                            internal_provenance_only=kwargs.pop("internal_provenance_only", True), **kwargs)

    def refused_before_output(self, error=ValueError):
        with self.assertRaises(error):
            for platform, name in zip(module.PLATFORMS, module.ASSETS):
                module.inspect_archive(self.source / name, module.expected_payloads(self.repo, platform))
        self.assertFalse(self.output.exists())

    def test_copies_exact_bytes_and_never_invents_archive_provenance(self):
        manifest = self.stage()
        self.assertEqual(manifest["schema"], 2)
        self.assertEqual(manifest["stagingCheckoutCommit"], "a" * 40)
        self.assertIsNone(manifest["archiveSourceCommit"])
        self.assertNotIn("sourceCommit", manifest)
        self.assertEqual(manifest["redistributionClearance"], "not-cleared")
        self.assertEqual(manifest["nativeRuntimeAcceptance"], "not-established-by-packaging")
        self.assertEqual(len((self.output / "SHA256SUMS").read_text().splitlines()), 3)
        self.assertEqual(json.loads((self.output / "support-manifest.json").read_text()), manifest)
        for asset in manifest["assets"]:
            self.assertEqual((self.source / asset["file"]).read_bytes(), (self.output / asset["file"]).read_bytes())
            self.assertEqual(module.sha256(self.output / asset["file"]), asset["sha256"])
            self.assertEqual(asset["inspection"]["minimumJavaFromAppClasses"], 21)
        self.assertIn("NOT CLEARED", (self.output / "README-SUPPORTED-BUILD.txt").read_text())

    def test_paid_redistribution_is_refused_before_io(self):
        with mock.patch.object(module, "expected_payloads", side_effect=AssertionError("must not inspect")):
            with self.assertRaisesRegex(RuntimeError, "not cleared"):
                self.stage(internal_provenance_only=False)
        self.assertFalse(self.output.exists())

    def test_missing_input_refused_before_output(self):
        (self.source / module.ASSETS[-1]).unlink()
        with self.assertRaises(FileNotFoundError):
            self.stage()
        self.assertFalse(self.output.exists())

    def test_existing_output_preserves_all_bytes(self):
        self.output.mkdir()
        keep = self.output / "previous.mp4"
        keep.write_bytes(b"owned synthetic existing output")
        with mock.patch.object(shutil, "rmtree", side_effect=AssertionError("must never delete")):
            with self.assertRaises(FileExistsError):
                self.stage()
        self.assertEqual(keep.read_bytes(), b"owned synthetic existing output")
        self.assertEqual(list(self.output.iterdir()), [keep])

    def test_existing_empty_directory_is_also_preserved(self):
        self.output.mkdir()
        with self.assertRaises(FileExistsError):
            self.stage()
        self.assertEqual(list(self.output.iterdir()), [])

    def test_existing_file_preserved(self):
        self.output.write_bytes(b"keep")
        with self.assertRaises(FileExistsError):
            self.stage()
        self.assertEqual(self.output.read_bytes(), b"keep")

    def test_output_must_be_strictly_inside_generated_directory(self):
        for output in (self.repo, self.repo / "dist", self.source, self.root / "outside", self.source.parent.parent):
            with self.subTest(output=output), self.assertRaises((ValueError, FileExistsError)):
                self.stage(output=output)
        self.assertEqual(len(list(self.source.glob("*.zip"))), 3)

    def test_destination_appearing_during_preflight_is_preserved(self):
        real_inspect = module.inspect_archive
        def race(*args):
            result = real_inspect(*args)
            self.output.mkdir(exist_ok=True)
            (self.output / "keep").write_bytes(b"concurrent writer")
            return result
        with mock.patch.object(module, "inspect_archive", side_effect=race), self.assertRaises(FileExistsError):
            self.stage()
        self.assertEqual((self.output / "keep").read_bytes(), b"concurrent writer")
        self.assertEqual(len(list(self.output.iterdir())), 1)

    def test_failed_copy_retains_partial_without_success_manifest(self):
        with mock.patch.object(module.shutil, "copyfileobj", side_effect=OSError("fixture disk failure")):
            with self.assertRaisesRegex(OSError, "fixture disk failure"):
                self.stage()
        self.assertFalse((self.output / "support-manifest.json").exists())
        self.assertTrue(self.output.exists())
        with self.assertRaises(FileExistsError):
            self.stage()

    def test_source_change_during_copy_is_not_success(self):
        def corrupt(src, dst):
            dst.write(b"fixture changed during copy")
        with mock.patch.object(module.shutil, "copyfileobj", side_effect=corrupt), self.assertRaisesRegex(ValueError, "changed during copy"):
            self.stage()
        self.assertFalse((self.output / "support-manifest.json").exists())

    def test_archive_must_be_zip(self):
        (self.source / module.ASSETS[0]).write_bytes(b"not a zip")
        self.refused_before_output(zipfile.BadZipFile)

    def test_archive_members_must_match_tracked_allowlist(self):
        for name in ("video_glitcher/private.mp4", "video_glitcher/.env", "video_glitcher/lib/untracked.jar",
                     "video_glitcher/video/windows-amd64/extra.dll", "outside", "/absolute", "../escape", "video_glitcher/../escape",
                     "video_glitcher//extra", "video_glitcher\\escape", "C:/escape", "video_glitcher/secret:stream"):
            with self.subTest(name=name):
                self.write_archive("macos-aarch64", module.ASSETS[0], extra=(name, b"synthetic"))
                self.refused_before_output()

    def test_untracked_checkout_file_does_not_join_allowlist(self):
        extra = self.repo / "lib/video/library/secret.jar"
        extra.write_bytes(b"do not include")
        self.write_archive("macos-aarch64", module.ASSETS[0], extra=("video_glitcher/lib/secret.jar", extra.read_bytes()))
        self.refused_before_output()

    def test_missing_payload_refused(self):
        self.write_archive("macos-aarch64", module.ASSETS[0], omit="video_glitcher/lib/core.jar")
        self.refused_before_output()

    def test_changed_payload_refused(self):
        (self.repo / "lib/core.jar").write_bytes(b"different checkout bytes")
        self.refused_before_output()

    def test_duplicate_and_case_colliding_entries_refused(self):
        for name in ("video_glitcher/lib/core.jar", "video_glitcher/lib/CORE.JAR"):
            with self.subTest(name=name), warnings.catch_warnings():
                warnings.simplefilter("ignore", UserWarning)
                self.write_archive("macos-aarch64", module.ASSETS[0], extra=(name, b"duplicate"))
                self.refused_before_output()

    def test_archive_link_and_special_file_refused(self):
        for mode in (stat.S_IFLNK, stat.S_IFIFO, stat.S_IFCHR):
            info = zipfile.ZipInfo("video_glitcher/linked")
            info.create_system = 3
            info.external_attr = (mode | 0o777) << 16
            self.write_archive("macos-aarch64", module.ASSETS[0], extra=(info, b"target"))
            self.refused_before_output()

    def test_archive_size_and_entry_count_limits(self):
        with mock.patch.object(module, "MAX_ARCHIVE_BYTES", 10):
            self.refused_before_output()
        with mock.patch.object(module, "MAX_ENTRIES", 1):
            self.refused_before_output()

    def test_class_jar_excludes_non_class_files_and_unknown_classes(self):
        for name in ("private.mp4", "tom/videoGlitcher/Secret.class", "META-INF/services/unknown"):
            self.write_archive("macos-aarch64", module.ASSETS[0], jar=app_jar((name, b"synthetic")))
            self.refused_before_output()

    def test_newer_java_target_refused(self):
        self.write_archive("macos-aarch64", module.ASSETS[0], jar=app_jar(major=66))
        self.refused_before_output()

    def test_class_jar_duplicate_refused(self):
        with warnings.catch_warnings():
            warnings.simplefilter("ignore", UserWarning)
            jar = app_jar(("tom/videoGlitcher/VideoGlitcher.class", b"bad"))
        self.write_archive("macos-aarch64", module.ASSETS[0], jar=jar)
        self.refused_before_output()

    def test_dependency_flattening_collision_refused(self):
        self.add_payload("lib/controlP5/library/jna.jar")
        self.refused_before_output()

    def test_symlink_destination_and_parent_refused(self):
        target = self.root / "other"
        target.mkdir()
        try:
            self.output.symlink_to(target, target_is_directory=True)
        except OSError:
            self.skipTest("Symlink creation not available on this runner")
        with self.assertRaises((ValueError, FileExistsError)):
            self.stage()
        with self.assertRaises(ValueError):
            self.stage(output=self.output / "child")
        self.assertEqual(list(target.iterdir()), [])

    def test_linked_input_refused(self):
        archive = self.source / module.ASSETS[0]
        original = self.root / "fixture.zip"
        archive.rename(original)
        try:
            archive.symlink_to(original)
        except OSError:
            self.skipTest("Symlink creation not available on this runner")
        self.refused_before_output()

    @unittest.skipUnless(module.supports_safe_staging(), "POSIX-only staging")
    def test_control_char_in_label_refused(self):
        with self.assertRaises(ValueError):
            module.stage_supported_build(self.repo, self.source, self.output, "fake\nclaim", internal_provenance_only=True)
        self.assertFalse(self.output.exists())

    def test_missing_safe_directory_capability_fails_closed(self):
        with mock.patch.object(module, "supports_safe_staging", return_value=False):
            with self.assertRaisesRegex(RuntimeError, "unsupported on this platform"):
                module.stage_supported_build(self.repo, self.source, self.output, "v1", internal_provenance_only=True)
        self.assertFalse(self.output.exists())

    @unittest.skipUnless(module.supports_safe_staging(), "POSIX-only staging")
    def test_nested_output_and_inward_symlink_parent_are_refused(self):
        inside = self.repo / "dist/existing-parent"
        inside.mkdir()
        alias = self.repo / "dist/alias"
        alias.symlink_to(inside, target_is_directory=True)
        for output in (alias / "new-output", inside / "new-output"):
            with self.subTest(output=output), self.assertRaisesRegex(ValueError, "immediate child"):
                self.stage(output=output)
        self.assertEqual(list(inside.iterdir()), [])

    @unittest.skipUnless(module.supports_safe_staging(), "POSIX-only staging")
    def test_symlinked_dist_is_refused_even_when_target_is_inside_repo(self):
        old_dist = self.repo / "dist"
        moved = self.repo / "held-dist"
        old_dist.rename(moved)
        old_dist.symlink_to(moved, target_is_directory=True)
        self.source = moved / "inputs"
        with self.assertRaises(OSError):
            self.stage()
        self.assertFalse((moved / self.output.name).exists())

    @unittest.skipUnless(module.supports_safe_staging(), "POSIX-only staging")
    def test_dist_replaced_by_outside_symlink_during_inspection_is_refused(self):
        outside = self.root / "outside"
        outside.mkdir()
        original_dist = self.repo / "held-dist"
        real_inspect = module.inspect_archive
        switched = False
        def race(*args):
            nonlocal switched
            result = real_inspect(*args)
            if not switched:
                (self.repo / "dist").rename(original_dist)
                (self.repo / "dist").symlink_to(outside, target_is_directory=True)
                switched = True
            return result
        with mock.patch.object(module, "inspect_archive", side_effect=race), self.assertRaisesRegex(ValueError, "identity changed"):
            self.stage()
        self.assertEqual(list(outside.iterdir()), [])
        self.assertFalse((original_dist / self.output.name).exists())

    @unittest.skipUnless(module.supports_safe_staging(), "POSIX-only staging")
    def test_creation_boundary_uses_held_parent_even_after_path_replacement(self):
        outside = self.root / "outside"
        outside.mkdir()
        original_dist = self.repo / "held-dist"
        real_mkdir = os.mkdir
        switched = False
        def race(name, *args, **kwargs):
            nonlocal switched
            if name == self.output.name and kwargs.get("dir_fd") is not None and not switched:
                (self.repo / "dist").rename(original_dist)
                (self.repo / "dist").symlink_to(outside, target_is_directory=True)
                switched = True
            return real_mkdir(name, *args, **kwargs)
        # Replacing os.mkdir changes its Python identity in supports_dir_fd;
        # retain the already-verified capability while instrumenting the call.
        with mock.patch.object(module, "supports_safe_staging", return_value=True), mock.patch.object(os, "mkdir", side_effect=race):
            with self.assertRaisesRegex(ValueError, "identity changed"):
                self.stage()
        self.assertTrue(switched)
        self.assertEqual(list(outside.iterdir()), [])
        held_output = original_dist / self.output.name
        self.assertTrue(held_output.is_dir())
        self.assertFalse((held_output / "support-manifest.json").exists())
        self.assertTrue((held_output / module.ASSETS[0]).is_file())

    @unittest.skipUnless(module.supports_safe_staging(), "POSIX-only staging")
    def test_output_leaf_replaced_by_symlink_never_redirects_file_writes(self):
        outside = self.root / "outside"
        outside.mkdir()
        held_output = self.repo / "dist/held-output"
        original_copy = module.shutil.copyfileobj
        switched = False
        def race(src, dst):
            nonlocal switched
            if not switched:
                self.output.rename(held_output)
                self.output.symlink_to(outside, target_is_directory=True)
                switched = True
            return original_copy(src, dst)
        with mock.patch.object(module.shutil, "copyfileobj", side_effect=race), self.assertRaisesRegex(ValueError, "identity changed"):
            self.stage()
        self.assertEqual(list(outside.iterdir()), [])
        self.assertFalse((held_output / "support-manifest.json").exists())
        self.assertEqual(len(list(held_output.glob("*.zip"))), 3)

    def test_tracked_media_config_and_unreviewed_notice_kinds_fail_closed(self):
        for leaf in ("owned-sample.mp4", "private.env", "LICENSE.txt", "notes.json", "installer.exe"):
            with self.subTest(leaf=leaf):
                name = "lib/video/library/macos-aarch64/" + leaf
                self.add_payload(name)
                with self.assertRaisesRegex(ValueError, "Unreviewed native payload kind"):
                    module.expected_payloads(self.repo, "macos-aarch64")
                self.tracked.remove(name)
        self.assertFalse(self.output.exists())

    def test_implicit_directory_case_collision_refused_without_directory_entries(self):
        self.add_payload("lib/video/library/macos-aarch64/Case/a.dylib")
        self.add_payload("lib/video/library/macos-aarch64/case/b.dylib")
        self.write_archive("macos-aarch64", module.ASSETS[0])
        self.refused_before_output()

    def test_file_ancestor_collision_in_both_orders(self):
        for names in (("root/a", "root/a/b"), ("root/a/b", "root/a")):
            for case_variant in (False, True):
                actual = (names[0], names[1].replace("root/a", "root/A") if case_variant else names[1])
                stream = io.BytesIO()
                with zipfile.ZipFile(stream, "w") as archive:
                    for name in actual:
                        archive.writestr(name, b"fixture")
                with zipfile.ZipFile(stream) as archive, self.assertRaisesRegex(ValueError, "path component"):
                    module.checked_entries(archive, set(actual), 100)


    @unittest.skipUnless(module.supports_safe_staging(), "POSIX-only staging")
    def test_selected_checkout_root_alias_accepts_lexical_and_canonical_parent(self):
        alias = self.root / "checkout-alias"
        alias.symlink_to(self.repo, target_is_directory=True)
        for output in (alias / "dist/alias-output", self.repo / "dist/canonical-output"):
            with self.subTest(output=output):
                result = module.stage_supported_build(alias, self.source, output, "fixture", internal_provenance_only=True)
                self.assertEqual(result["redistributionClearance"], "not-cleared")
                self.assertTrue((self.repo / "dist" / output.name / "support-manifest.json").is_file())
        # This allowance is for the selected root only, never dist aliases.
        nested = self.repo / "dist/alias"
        nested.symlink_to(self.repo / "dist", target_is_directory=True)
        with self.assertRaises(ValueError):
            module.stage_supported_build(alias, self.source, alias / "dist/alias/forbidden", "fixture", internal_provenance_only=True)

    @unittest.skipUnless(module.supports_safe_staging() and shutil.which("git"), "POSIX staging with git")
    def test_normal_cli_from_symlink_spelled_checkout(self):
        scripts = self.repo / "scripts"
        scripts.mkdir()
        shutil.copyfile(REPO / "scripts/package_supported_build.py", scripts / "package_supported_build.py")
        subprocess.run(["git", "init", "-q", str(self.repo)], check=True, capture_output=True)
        subprocess.run(["git", "-C", str(self.repo), "add", "lib", "packaging", "scripts"], check=True, capture_output=True)
        subprocess.run(["git", "-C", str(self.repo), "-c", "user.name=Synthetic Fixture", "-c", "user.email=fixture@example.invalid",
                        "commit", "-q", "-m", "Owned synthetic packaging fixture"], check=True, capture_output=True)
        alias = self.root / "checkout-alias"
        alias.symlink_to(self.repo, target_is_directory=True)
        result = subprocess.run([sys.executable, str(alias / "scripts/package_supported_build.py"), "--version", "fixture",
                                 "--source", "dist/inputs", "--output", "dist/cli-output", "--internal-provenance-only"],
                                cwd=alias, capture_output=True, text=True)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(json.loads(result.stdout)["status"], "internal-staging-complete")
        manifest = json.loads((self.repo / "dist/cli-output/support-manifest.json").read_text())
        self.assertIsNone(manifest["archiveSourceCommit"])



if __name__ == "__main__":
    unittest.main()
