#!/usr/bin/env python3
"""B3+D3: executable regression suite for semantic parity and immutable web releases."""
from __future__ import annotations

import hashlib
import importlib.util
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch
from zipfile import ZipFile, ZipInfo, ZIP_DEFLATED

ROOT = Path(__file__).resolve().parents[2]


def load(name: str, relative: str):
    spec = importlib.util.spec_from_file_location(name, ROOT / relative)
    module = importlib.util.module_from_spec(spec)
    sys.modules[name] = module
    assert spec.loader
    spec.loader.exec_module(module)
    return module


# Scripts designed for direct developer invocation rather than Python packages.
sys.path.insert(0, str(ROOT / 'scripts/validation'))
sys.path.insert(0, str(ROOT / 'scripts/distribution'))
parity = load('bd3_parity', 'scripts/validation/run_native_parity_suite.py')
registry = load('bd3_release', 'scripts/distribution/editor_release_registry.py')


class ParityTests(unittest.TestCase):
    def setUp(self):
        temp = tempfile.TemporaryDirectory(prefix='hydra-bd3-parity-')
        self.addCleanup(temp.cleanup)
        self.root = Path(temp.name)
        (self.root / 'tests').mkdir()
        (self.root / 'tests/input.json').write_text('{"id":"x","name":"old"}', encoding='utf-8')
        (self.root / 'tests/expected.json').write_text('{"id":"x","name":"new"}', encoding='utf-8')
        self.case = {'id':'rename','capability':'OPERATION','input':'tests/input.json',
                     'expected':'tests/expected.json','evidence':'target/rename.json'}
        self.write_suite([self.case])

    def write_suite(self, cases):
        (self.root / 'tests/scenarios.json').write_text(json.dumps({'schemaVersion':1,'scenarios':cases}))

    def test_scenario_schema(self):
        self.assertEqual(len(parity.load_cases(self.root, 'tests/scenarios.json')), 1)

    def test_scenario_unknown_capability_rejected(self):
        self.write_suite([{**self.case, 'capability':'MAGIC'}])
        with self.assertRaisesRegex(ValueError, 'capability'):
            parity.load_cases(self.root, 'tests/scenarios.json')

    def test_duplicate_scenario_rejected(self):
        self.write_suite([self.case, self.case])
        with self.assertRaisesRegex(ValueError, 'Duplicate'):
            parity.load_cases(self.root, 'tests/scenarios.json')

    def test_path_escape_rejected(self):
        for value in ('../escape', '/etc/passwd', 'tests/../../escape', 'C:/tmp', 'tests\\input.json'):
            with self.subTest(value=value), self.assertRaises(ValueError):
                parity.within(self.root, value, require_exists=False)

    def test_symlink_rejected(self):
        link = self.root / 'link.json'
        try:
            link.symlink_to(self.root / 'tests/input.json')
        except (OSError, NotImplementedError):
            self.skipTest('symlinks unsupported')
        with self.assertRaisesRegex(ValueError, 'symbolic'):
            parity.within(self.root, 'link.json', require_exists=True)

    def test_oversized_json_rejected(self):
        (self.root / 'tests/input.json').write_text(' ' * (parity.MAX_JSON_BYTES + 1))
        with self.assertRaisesRegex(ValueError, 'oversized'):
            parity.load_cases(self.root, 'tests/scenarios.json')

    def native(self, challenge: str | None, value=None, altered_digest=False):
        if challenge:
            evidence = self.root / 'target/rename.json'
            evidence.parent.mkdir(exist_ok=True)
            evidence.write_text(json.dumps({
                'schemaVersion':1,'scenarioId':'rename', 'challenge':challenge,
                'inputSHA256': '0'*64 if altered_digest else parity.digest(self.root/'tests/input.json'),
                'actual': {'id':'x','name':'new'} if value is None else value,
            }), encoding='utf-8')
        return {'status':'PASSED','stages':[{'status':'PASSED'}]}

    def test_valid_fresh_native_semantics_certified(self):
        with patch.object(parity,'run',side_effect=lambda profile,project,timeout,execute,challenge=None:self.native(challenge)):
            result=parity.certify('generated-web',self.root,'tests/scenarios.json',30,True)
        self.assertEqual(result['status'], 'PASSED')
        self.assertEqual(result['certifiedCapabilities'], ['OPERATION'])
        self.assertEqual(len(result['notCertifiedCapabilities']), len(parity.CAPABILITIES)-1)

    def test_dry_run_never_certified(self):
        with patch.object(parity,'run',return_value={'status':'PASSED'}):
            result=parity.certify('generated-web',self.root,'tests/scenarios.json',30,False)
        self.assertEqual(result['status'],'UNVERIFIED')

    def test_stale_proof_rejected(self):
        self.native('deadbeef'*6)
        with patch.object(parity,'run',return_value={'status':'PASSED'}):
            result=parity.certify('generated-web',self.root,'tests/scenarios.json',30,True)
        self.assertEqual(result['scenarios'][0]['status'], 'FAILED')

    def test_wrong_output_rejected(self):
        with patch.object(parity,'run',side_effect=lambda profile,project,timeout,execute,challenge=None:self.native(challenge,{'name':'incorrect'})):
            result=parity.certify('generated-web',self.root,'tests/scenarios.json',30,True)
        self.assertEqual(result['scenarios'][0]['status'], 'FAILED')

    def test_wrong_input_digest_rejected(self):
        with patch.object(parity,'run',side_effect=lambda profile,project,timeout,execute,challenge=None:self.native(challenge,altered_digest=True)):
            result=parity.certify('generated-web',self.root,'tests/scenarios.json',30,True)
        self.assertIn('digest',result['scenarios'][0]['reason'])

    def test_boolean_number_mismatch_rejected(self):
        (self.root/'tests/expected.json').write_text('{"passed":1}')
        with patch.object(parity,'run',side_effect=lambda profile,project,timeout,execute,challenge=None:self.native(challenge,{'passed':True})):
            result=parity.certify('generated-web',self.root,'tests/scenarios.json',30,True)
        self.assertEqual(result['scenarios'][0]['status'], 'FAILED')

    def test_expected_changed_during_native_execution_rejected(self):
        def mutated(profile,project,timeout,execute,challenge=None):
            self.native(challenge)
            (self.root/'tests/expected.json').write_text('{"id":"x","name":"new \u0021"}')
            return {'status':'PASSED'}
        with patch.object(parity,'run',side_effect=mutated):
            result=parity.certify('generated-web',self.root,'tests/scenarios.json',30,True)
        self.assertIn('modified declared', result['scenarios'][0]['reason'])

    def test_nan_in_canonical_json_rejected(self):
        (self.root/'tests/expected.json').write_text('{"result": NaN}')
        with self.assertRaisesRegex(ValueError, 'Non-finite'):
            parity.load_cases(self.root, 'tests/scenarios.json')

    def test_failure_in_native_tests_rejected(self):
        with patch.object(parity,'run',return_value={'status':'UNVERIFIED'}):
            result=parity.certify('generated-web',self.root,'tests/scenarios.json',30,True)
        self.assertEqual(result['status'],'UNVERIFIED')


class ReleaseRegistryTests(unittest.TestCase):
    def setUp(self):
        temp = tempfile.TemporaryDirectory(prefix='hydra-bd3-release-')
        self.addCleanup(temp.cleanup)
        self.root = Path(temp.name)
        self.registry = self.root/'registry'
        self.create_package('1.0.0')

    def create_package(self, version, content=b'<html><body>HYDRA editor</body></html>', out=None):
        output = out or self.root/('editor-'+version+'.zip')
        files={'index.html':content,'assets/editor.js':b'window.editor=true;'}
        hashes = ''.join(hashlib.sha256(v).hexdigest()+'  '+k+'\n' for k,v in sorted(files.items())).encode()
        metadata=json.dumps({'level':'DEPLOYABLE_WEB_ASSETS','version':version,'nativeInstallerCertified':False,
                             'files':len(files)}).encode()
        with ZipFile(output,'w',compression=ZIP_DEFLATED) as z:
            for name,data in {**files,'hydra-release.sha256':hashes,'hydra-deployment.json':metadata}.items():
                info=ZipInfo(name, (1980,1,1,0,0,0));info.compress_type=ZIP_DEFLATED
                z.writestr(info,data)
        return output

    def promote(self, version='1.0.0', channel='stable', package=None):
        return registry.promote(self.registry,'business-editor',channel,package or self.root/('editor-'+version+'.zip'))

    def test_promotion_and_verification(self):
        self.assertEqual(self.promote()['status'],'PROMOTED')
        report=registry.inspect(self.registry,'business-editor','stable')
        self.assertEqual(report['status'],'VERIFIED')
        self.assertEqual(report['integrity']['webFiles'],4)
        self.assertFalse(report['authenticityVerified'])

    def test_immutable_web_assets_detected(self):
        self.promote()
        target=self.registry/'products/business-editor/releases/1.0.0/www/index.html'
        target.write_text('tampered')
        with self.assertRaisesRegex(ValueError,'asset differs'):
            registry.inspect(self.registry,'business-editor','stable')

    def test_published_zip_modification_detected(self):
        self.promote()
        target=self.registry/'products/business-editor/releases/1.0.0/editor.zip'
        target.write_bytes(b'not a ZIP')
        with self.assertRaises(Exception):
            registry.inspect(self.registry,'business-editor','stable')

    def test_promote_update_then_rollback(self):
        self.promote()
        self.create_package('1.1.0',content=b'new version')
        self.promote('1.1.0')
        self.assertEqual(registry.inspect(self.registry,'business-editor','stable')['version'],'1.1.0')
        self.assertEqual(registry.rollback(self.registry,'business-editor','stable')['version'],'1.0.0')
        self.assertEqual(registry.inspect(self.registry,'business-editor','stable')['version'],'1.0.0')

    def test_windows_transient_release_directory_lock_retries_safely(self):
        self.promote()
        self.create_package('1.1.0', content=b'new version')
        original = Path.rename
        attempts = []
        def occasionally_locked(staging, destination):
            attempts.append((staging, destination))
            if len(attempts) <= 2:
                error = PermissionError(13, 'simulated Windows directory sharing lock')
                error.winerror = 5 if len(attempts) == 1 else 32
                raise error
            return original(staging, destination)
        with patch.object(Path, 'rename', new=occasionally_locked), patch.object(registry.time, 'sleep') as sleep:
            self.promote('1.1.0')
        self.assertEqual(len(attempts), 3)
        self.assertEqual(sleep.call_count, 2)
        self.assertEqual(registry.inspect(self.registry, 'business-editor', 'stable')['version'], '1.1.0')
        self.assertEqual(registry.verify_release(self.registry/'products/business-editor', '1.0.0')['status'], 'PASSED')

    def test_windows_persistent_release_lock_fails_closed_without_partial_channel(self):
        self.promote()
        self.create_package('1.1.0', content=b'new version')
        attempted = []
        def permanently_locked(staging, destination):
            attempted.append(destination)
            error = PermissionError(13, 'simulated Windows antivirus lock')
            error.winerror = 5
            raise error
        with patch.object(Path, 'rename', new=permanently_locked), patch.object(registry.time, 'sleep'):
            with self.assertRaisesRegex(PermissionError, 'bounded retries'):
                self.promote('1.1.0')
        self.assertEqual(len(attempted), 8)
        self.assertEqual(registry.inspect(self.registry, 'business-editor', 'stable')['version'], '1.0.0')
        releases = self.registry/'products/business-editor/releases'
        self.assertFalse((releases/'1.1.0').exists())
        self.assertFalse(any(path.name.endswith('.tmp') for path in releases.iterdir()))

    def test_non_windows_or_permanent_acl_failure_is_not_retried(self):
        self.create_package('1.1.0')
        calls = []
        def denied(staging, destination):
            calls.append(destination)
            raise PermissionError(13, 'permanent ACL denial')
        with patch.object(Path, 'rename', new=denied), patch.object(registry.time, 'sleep') as sleep:
            with self.assertRaisesRegex(PermissionError, 'permanent ACL denial'):
                self.promote('1.1.0')
        self.assertEqual(len(calls), 1)
        sleep.assert_not_called()

    def test_release_destination_race_fails_without_overwriting_existing_directory(self):
        self.create_package('1.1.0')
        def destination_appears(staging, destination):
            destination.mkdir()
            (destination/'other-data.txt').write_text('untrusted existing release')
            error = PermissionError(13, 'simulated Windows sharing violation')
            error.winerror = 32
            raise error
        with patch.object(Path, 'rename', new=destination_appears), patch.object(registry.time, 'sleep') as sleep:
            with self.assertRaisesRegex(ValueError, 'destination appeared'):
                self.promote('1.1.0')
        sleep.assert_not_called()
        self.assertEqual((self.registry/'products/business-editor/releases/1.1.0/other-data.txt').read_text(),
                         'untrusted existing release')
        self.assertFalse((self.registry/'products/business-editor/channels/stable.json').exists())

    def test_channel_pointers_independent(self):
        self.promote(channel='beta')
        self.assertEqual(registry.inspect(self.registry,'business-editor','stable')['status'],'UNAVAILABLE')
        self.promote()
        self.assertEqual(registry.inspect(self.registry,'business-editor','stable')['status'],'VERIFIED')

    def test_downgrade_without_rollback_denied(self):
        self.promote();self.create_package('1.1.0');self.promote('1.1.0')
        with self.assertRaisesRegex(ValueError,'higher'):
            self.promote('1.0.0')

    def test_same_version_mutation_rejected(self):
        self.promote()
        changed=self.create_package('1.0.0',content=b'changed',out=self.root/'changed.zip')
        with self.assertRaises(ValueError):
            self.promote(package=changed)

    def test_invalid_archive_rejected(self):
        path=self.root/'broken.zip';path.write_bytes(b'not zip')
        with self.assertRaises(Exception):
            self.promote(package=path)

    def test_channel_sha_mismatch_rejected(self):
        self.promote()
        pointer=self.registry/'products/business-editor/channels/stable.json'
        obj=json.loads(pointer.read_text());obj['sha256']='0'*64
        pointer.write_text(json.dumps(obj))
        with self.assertRaisesRegex(ValueError,'pointer SHA'):
            registry.inspect(self.registry,'business-editor','stable')

    def test_rollback_requires_history(self):
        self.promote()
        with self.assertRaisesRegex(ValueError,'previous'):
            registry.rollback(self.registry,'business-editor','stable')

    def test_invalid_names_rejected(self):
        for name in ('../other','ROOT','some_name','.hidden','foo/bar'):
            with self.subTest(name=name),self.assertRaises(ValueError):
                registry.product_path(self.registry,name)

    def test_invalid_versions_rejected(self):
        for value in ('1.0','01.0.0','1.0.0-beta','../../etc','x.y.z'):
            with self.subTest(version=value),self.assertRaises(ValueError):
                registry.version_tuple(value)

    def test_registry_symlink_root_denied(self):
        target=self.root/'real';target.mkdir()
        link=self.root/'linked'
        try:link.symlink_to(target,target_is_directory=True)
        except (OSError, NotImplementedError):self.skipTest('symlinks unavailable')
        with self.assertRaises(ValueError):
            registry.product_path(link,'business-editor')

    def test_embedded_channel_mismatch_rejected(self):
        package=self.root/'wrong-channel.zip'
        content=b'index'
        digest=hashlib.sha256(content).hexdigest()
        with ZipFile(package,'w') as zip:
            zip.writestr('index.html',content)
            zip.writestr('hydra-release.sha256',f'{digest}  index.html\n')
            zip.writestr('hydra-deployment.json',json.dumps({'level':'DEPLOYABLE_WEB_ASSETS',
                'version':'1.0.0','files':1,'nativeInstallerCertified':False,'updateChannel':'beta'}))
        with self.assertRaisesRegex(ValueError,'channel'):
            self.promote(package=package)

    def test_symlinked_products_directory_denied(self):
        outside=self.root/'outside';outside.mkdir()
        self.registry.mkdir()
        try:(self.registry/'products').symlink_to(outside,target_is_directory=True)
        except (OSError, NotImplementedError):self.skipTest('symlinks unsupported')
        with self.assertRaisesRegex(ValueError,'symbolic'):
            self.promote()

    def test_case_insensitive_portable_collision_rejected(self):
        package=self.root/'collision.zip'
        contents={'index.html':b'hello','assets/A.js':b'a','assets/a.js':b'b'}
        with ZipFile(package,'w') as z:
            for name,raw in contents.items():z.writestr(name,raw)
            z.writestr('hydra-release.sha256',''.join(hashlib.sha256(data).hexdigest()+'  '+name+'\n' for name,data in contents.items()))
            z.writestr('hydra-deployment.json',json.dumps({'level':'DEPLOYABLE_WEB_ASSETS',
                'version':'1.0.0','files':len(contents),'nativeInstallerCertified':False}))
        with self.assertRaisesRegex(ValueError,'Case-insensitive'):
            self.promote(package=package)

    def test_windows_reserved_device_name_rejected(self):
        package=self.root/'reserved.zip'
        contents={'index.html':b'hello','assets/con.txt':b'unportable'}
        with ZipFile(package,'w') as z:
            for name,raw in contents.items():z.writestr(name,raw)
            z.writestr('hydra-release.sha256',''.join(hashlib.sha256(data).hexdigest()+'  '+name+'\n' for name,data in contents.items()))
            z.writestr('hydra-deployment.json',json.dumps({'level':'DEPLOYABLE_WEB_ASSETS',
                'version':'1.0.0','files':len(contents),'nativeInstallerCertified':False}))
        with self.assertRaisesRegex(ValueError,'Not portable'):
            self.promote(package=package)

    def test_lock_prevents_concurrent_promotion(self):
        base=registry.product_path(self.registry,'business-editor')
        base.mkdir(parents=True)
        (base/'.hydra-promotion-lock').mkdir()
        with self.assertRaises(FileExistsError):
            self.promote()


if __name__=='__main__':
    result=unittest.main(verbosity=2,exit=False)
    if not result.result.wasSuccessful():
        sys.exit(1)
    print('HYDRA_BD3_TESTS=PASS total='+str(result.result.testsRun))
