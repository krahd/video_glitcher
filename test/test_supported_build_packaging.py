from __future__ import annotations

import importlib.util
import json
import shutil
import tempfile
import unittest
from pathlib import Path
from unittest import mock

REPO = Path(__file__).resolve().parents[1]
SCRIPT = REPO / "scripts" / "package_supported_build.py"
spec = importlib.util.spec_from_file_location("package_supported_build", SCRIPT)
assert spec and spec.loader
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class SupportedBuildPackagingTests(unittest.TestCase):
    def setUp(self) -> None:
        (REPO / "dist").mkdir(exist_ok=True)
        self.temp_dir = Path(tempfile.mkdtemp(prefix="supported-build-test-", dir=REPO / "dist"))

    def tearDown(self) -> None:
        shutil.rmtree(self.temp_dir, ignore_errors=True)

    def test_copies_exact_assets_and_emits_checksums(self) -> None:
        source = self.temp_dir / "source"
        output = self.temp_dir / "out"
        source.mkdir()
        for index, name in enumerate(module.ASSETS):
            (source / name).write_bytes(f"asset-{index}".encode())
        with mock.patch.object(module, "git_head", return_value="abc123"):
            manifest = module.stage_supported_build(REPO, source, output, "v1.1.3")
        self.assertEqual([entry["file"] for entry in manifest["assets"]], list(module.ASSETS))
        self.assertEqual(manifest["sourceCommit"], "abc123")
        self.assertEqual(manifest["distribution"], "supported-build")
        self.assertIn("No DRM", manifest["licensing"])
        sums = (output / "SHA256SUMS").read_text().splitlines()
        self.assertEqual(len(sums), 3)
        self.assertTrue(all((output / name).is_file() for name in module.ASSETS))
        stored = json.loads((output / "support-manifest.json").read_text())
        self.assertEqual(stored["version"], "v1.1.3")
        self.assertEqual(stored["thirdPartyCompliance"]["notices"], "third-party/THIRD-PARTY-NOTICES.md")
        self.assertTrue((output / "third-party" / "THIRD-PARTY-NOTICES.md").is_file())
        self.assertTrue((output / "third-party" / "SOURCE-ACCESS.md").is_file())
        source_manifest = json.loads((output / "third-party" / "source-manifest.json").read_text())
        self.assertTrue(any(item["name"] == "x264" for item in source_manifest["components"]))
        self.assertTrue((output / "third-party" / "licenses" / "GPL-2.0.txt").is_file())

    def test_fails_closed_when_release_asset_missing(self) -> None:
        source = self.temp_dir / "source"
        source.mkdir()
        for name in module.ASSETS[:-1]:
            (source / name).write_bytes(b"asset")
        with self.assertRaises(FileNotFoundError) as caught:
            module.stage_supported_build(REPO, source, self.temp_dir / "out", "v1")
        self.assertIn(module.ASSETS[-1], str(caught.exception))

    def test_fails_closed_when_compliance_package_missing(self) -> None:
        source = self.temp_dir / "source-compliance"
        source.mkdir()
        for name in module.ASSETS:
            (source / name).write_bytes(b"asset")
        with mock.patch.object(module, "COMPLIANCE_ROOT", self.temp_dir / "missing-compliance"):
            with self.assertRaises(FileNotFoundError) as caught:
                module.stage_supported_build(REPO, source, self.temp_dir / "out-compliance", "v1")
        self.assertIn("third-party compliance", str(caught.exception))


if __name__ == "__main__":
    unittest.main()
