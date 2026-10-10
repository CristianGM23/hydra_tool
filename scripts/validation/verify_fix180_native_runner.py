#!/usr/bin/env python3
"""D8 native OS launcher fail-closed regression; not actual OS certification."""
import pathlib,sys,tempfile,unittest
ROOT=pathlib.Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'scripts/distribution'))
from run_native_installer_smoke import locate_bundle,run

class ActualBundleRunnerContract(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory();self.addCleanup(self.tmp.cleanup)
        self.root=pathlib.Path(self.tmp.name)
    def test_dry_run_never_certifies(self):
        import platform
        system={'Windows':'windows','Linux':'linux','Darwin':'macos'}[platform.system()]
        self.assertEqual(run(self.root,system,execute=False)['status'],'NOT_EXECUTED')
    def test_missing_bundle_rejected(self):
        with self.assertRaisesRegex(ValueError,'exactly one'):
            locate_bundle(self.root,'linux')
    def test_multiple_bundles_rejected(self):
        (self.root/'a.AppImage').write_bytes(b'foo')
        (self.root/'b.AppImage').write_bytes(b'bar')
        with self.assertRaisesRegex(ValueError,'exactly one'):
            locate_bundle(self.root,'linux')
    def test_wrong_operating_system_blocked(self):
        import platform
        other={'Windows':'linux','Linux':'macos','Darwin':'windows'}[platform.system()]
        with self.assertRaisesRegex(ValueError,'not the executing'):
            run(self.root,other,execute=True)
    def test_symlink_bundle_denied(self):
        outside=self.root.parent/'not-real-bundle.AppImage'
        link=self.root/'virtual.AppImage'
        try:link.symlink_to(outside)
        except OSError:self.skipTest('symlinks unavailable')
        with self.assertRaisesRegex(ValueError,'exactly one'):
            locate_bundle(self.root,'linux')

if __name__=='__main__':unittest.main(verbosity=2)
