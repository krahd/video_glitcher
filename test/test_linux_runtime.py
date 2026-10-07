import importlib.util
from pathlib import Path
import tempfile
import sys
import unittest

spec = importlib.util.spec_from_file_location('linux_runtime', Path(__file__).resolve().parents[1] / 'scripts/materialize_linux_runtime.py')
runtime = importlib.util.module_from_spec(spec)
spec.loader.exec_module(runtime)

@unittest.skipUnless(sys.platform.startswith("linux"), "Linux-only runtime materialisation")
class RuntimeMaterialisationTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.source = self.root / 'source'
        self.source.mkdir()
        (self.source / 'libvalid.so.1.2').write_bytes(b'\x7fELFpayload')
    def tearDown(self): self.temp.cleanup()
    def link(self, name, target): (self.source / name).write_text(target)
    def refused(self):
        with self.assertRaises((ValueError, OSError)): runtime.materialize(self.source, self.root / 'output')
        self.assertFalse((self.root / 'output').exists())
    def test_relative_chain_preserves_payload(self):
        self.link('libvalid.so', 'libvalid.so.1')
        self.link('libvalid.so.1', 'libvalid.so.1.2')
        manifest = runtime.materialize(self.source, self.root / 'output')
        self.assertEqual(len(manifest['links']), 2)
        self.assertTrue((self.root / 'output/libvalid.so').is_symlink())
        self.assertEqual((self.root / 'output/libvalid.so').read_bytes(), b'\x7fELFpayload')
        self.assertFalse((self.source / 'libvalid.so').is_symlink())
        self.assertFalse(manifest['redistribution_cleared'])
    def test_absolute_rejected(self): self.link('libbad.so', '/etc/passwd'); self.refused()
    def test_escape_rejected(self): self.link('libbad.so', '../../other.so'); self.refused()
    def test_cycle_rejected(self):
        self.link('liba.so', 'libb.so'); self.link('libb.so', 'liba.so'); self.refused()
    def test_dangling_rejected(self): self.link('libbad.so', 'missing.so'); self.refused()
    def test_non_library_rejected(self):
        (self.source / 'payload').write_bytes(b'not an ELF'); self.link('libbad.so', 'payload'); self.refused()
    def test_actual_escaping_symlink_rejected(self):
        (self.source / 'libbad.so').symlink_to('../outside'); self.refused()
    def test_existing_destination_preserved(self):
        target = self.root / 'output'; target.mkdir(); (target / 'keep').write_text('original')
        with self.assertRaises(ValueError): runtime.materialize(self.source, target)
        self.assertEqual((target / 'keep').read_text(), 'original')
    def test_nested_destination_rejected(self):
        with self.assertRaises(ValueError): runtime.materialize(self.source, self.source / 'generated')

if __name__ == '__main__': unittest.main()
