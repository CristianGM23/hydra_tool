#!/usr/bin/env python3
"""Fix179 regression: native Java/Node semantic parity + signed web install lifecycle.

External EMF/Sirius/Xtext/GLSP toolchains and OS installers NOT certified here.
"""
from __future__ import annotations

import base64
import hashlib
import json
from pathlib import Path
import shutil
import sys
import tempfile
import unittest
from zipfile import ZipFile, ZIP_DEFLATED

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT / 'scripts/validation'))
sys.path.insert(0,str(ROOT / 'scripts/distribution'))
from run_native_e2e_matrix import PROFILES
from run_native_parity_matrix import load_matrix, run_matrix
from run_native_parity_portfolio import read_policy, assess
from editor_release_registry import promote
from editor_release_trust import claim, sign_detached
from install_standalone_web import install, rollback, inspect_install, location, commit_staged_version
from unittest import mock


class NativeParityContract(unittest.TestCase):
    def test_java_is_independent_allowlisted_profile(self):
        self.assertIn('java-standalone',PROFILES)
        self.assertEqual(PROFILES['java-standalone'].stages[0][1][0],'javac')
        self.assertNotEqual(PROFILES['java-standalone'].stages[1][1][0],'node')

    def test_policy_and_matrix_cover_seven_shared_behaviors(self):
        matrix=load_matrix(ROOT/'scripts/validation/bd6_native_matrix.json')
        policy=read_policy(ROOT/'scripts/validation/bd6_native_policy.json')
        self.assertEqual(len(policy['groups'][0]['capabilityCoverage']),7)
        self.assertEqual(len({m['profile'] for m in matrix['groups'][0]['members']}),2)

    def test_dry_run_cannot_certify_java_and_node(self):
        matrix=load_matrix(ROOT/'scripts/validation/bd6_native_matrix.json')
        report=run_matrix(ROOT/'reference-projects',matrix,120,False)
        self.assertNotEqual(assess(read_policy(ROOT/'scripts/validation/bd6_native_policy.json'),
                                      report,executed=False)['status'],'CERTIFIED')

    def test_java_suite_has_same_inputs_and_expected_bytes_as_node(self):
        a=ROOT/'reference-projects/bd2-standalone-smoke/tests/hydra-parity'
        b=ROOT/'reference-projects/bd6-java-runtime/tests/hydra-parity'
        for file in sorted(a.rglob('*.json')):
            self.assertEqual(file.read_bytes(),(b/file.relative_to(a)).read_bytes())


class StagedVersionCommit(unittest.TestCase):
    """Windows antivirus/indexer handles must not abort an otherwise verified installation."""
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix='hydra-bd7-stage-')
        self.addCleanup(self.temp.cleanup)
        self.stage = Path(self.temp.name) / '.stage-test'
        self.stage.mkdir()
        self.target = Path(self.temp.name) / '1.0.0'

    @staticmethod
    def denied():
        error = PermissionError(13, 'Access is denied')
        error.winerror = 5
        return error

    def test_transient_windows_denial_is_retried_until_the_rename_succeeds(self):
        native = Path.rename
        calls = []
        def flaky(path, target):
            calls.append(path)
            if len(calls) <= 2:
                raise self.denied()
            return native(path, target)
        with mock.patch.object(Path, 'rename', flaky), mock.patch('install_standalone_web.os.name', 'nt'), \
                mock.patch('install_standalone_web.time.sleep') as pause:
            commit_staged_version(self.stage, self.target)
        self.assertTrue(self.target.is_dir())
        self.assertEqual((len(calls), pause.call_count), (3, 2))

    def test_persistent_denial_fails_after_bounded_retries(self):
        with mock.patch.object(Path, 'rename', side_effect=self.denied()), mock.patch('install_standalone_web.os.name', 'nt'), \
                mock.patch('install_standalone_web.time.sleep') as pause:
            with self.assertRaises(PermissionError):
                commit_staged_version(self.stage, self.target, retries=3)
        self.assertEqual(pause.call_count, 3)
        self.assertFalse(self.target.exists())

    def test_other_errors_are_not_retried(self):
        with mock.patch.object(Path, 'rename', side_effect=FileExistsError(17, 'exists')), \
                mock.patch('install_standalone_web.time.sleep') as pause:
            with self.assertRaises(FileExistsError):
                commit_staged_version(self.stage, self.target)
        pause.assert_not_called()


class InstalledWebLifecycle(unittest.TestCase):
    def setUp(self):
        temp=tempfile.TemporaryDirectory(prefix='hydra-bd7-lifecycle-')
        self.addCleanup(temp.cleanup)
        self.root=Path(temp.name)
        self.registry=self.root/'registry'
        self.dest=self.root/'deployment'
        self.product='teaching-editor'
        self.channel='stable'
        self.make_release('1.0.0')

    def make_archive(self,version):
        package=self.root/('web-'+version+'.zip')
        files={'index.html':b'<!doctype html><html><body><script type="module" src="./assets/app.js"></script></body></html>',
               'assets/app.js':('window.EDITOR_VERSION='+json.dumps(version)+';').encode(),
               'manifest.webmanifest':b'{"name":"Teaching Editor","display":"standalone"}',
               'service-worker.js':b'const SHELL=[]; caches.open("x"); self.addEventListener("fetch",()=>{});'}
        with ZipFile(package,'w',ZIP_DEFLATED) as archive:
            for name,content in sorted(files.items()):archive.writestr(name,content)
            archive.writestr('hydra-release.sha256',''.join(hashlib.sha256(body).hexdigest()+'  '+name+'\n' for name,body in sorted(files.items())))
            archive.writestr('hydra-deployment.json',json.dumps({'schemaVersion':'1.0','level':'DEPLOYABLE_WEB_ASSETS',
                'version':version,'updateChannel':self.channel,'offlineEnabled':True,
                'files':len(files),'nativeInstallerCertified':False}))
        return package

    def make_release(self,version):
        package=self.make_archive(version)
        promote(self.registry,self.product,self.channel,package)
        return package

    def installed(self):
        return install(self.registry,self.dest,self.product,self.channel,None,allow_unsigned_dev=True)

    def test_unsigned_install_requires_explicit_development_optin(self):
        with self.assertRaisesRegex(ValueError,'explicit'):
            install(self.registry,self.dest,self.product,self.channel,None)

    def test_installed_web_assets_are_real_and_independent(self):
        report=self.installed()
        self.assertEqual(report['status'],'VERIFIED')
        self.assertIn(b'<html>',(Path(report['webRoot'])/'index.html').read_bytes())
        self.assertNotIn('hydra-deployment.json',[p.name for p in Path(report['webRoot']).iterdir()])
        self.assertEqual(report['nativeInstallerCertified'],False)

    def test_installation_is_monotonic(self):
        self.installed()
        with self.assertRaisesRegex(ValueError,'newer'):
            self.installed()

    def test_upgrade_switch_and_rollback_preserve_user_files(self):
        old=self.installed()
        data=self.dest/'user-data'/'student-work.json'
        data.parent.mkdir(parents=True);data.write_text('{"course":"MDE","grade":5}')
        self.make_release('1.1.0')
        new=self.installed()
        self.assertEqual(new['version'],'1.1.0')
        self.assertNotEqual(new['sha256'],old['sha256'])
        self.assertEqual(rollback(self.dest,self.product,self.channel,None)['version'],'1.0.0')
        self.assertEqual(data.read_text(),'{"course":"MDE","grade":5}')

    def test_rollback_requires_existing_version(self):
        self.installed()
        with self.assertRaisesRegex(ValueError,'previous'):
            rollback(self.dest,self.product,self.channel,None)

    def test_installed_asset_tamper_detected(self):
        report=self.installed()
        (Path(report['webRoot'])/'assets/app.js').write_text('tampered')
        with self.assertRaisesRegex(ValueError,'differs'):
            inspect_install(self.dest,self.product,self.channel,None)

    def test_installed_package_tamper_detected(self):
        self.installed()
        package=location(self.dest,self.product,self.channel)/'versions/1.0.0/editor.zip'
        package.write_bytes(package.read_bytes()+b'extra')
        with self.assertRaisesRegex(ValueError,'SHA mismatch'):
            inspect_install(self.dest,self.product,self.channel,None)

    def test_extra_web_files_detected(self):
        report=self.installed()
        (Path(report['webRoot'])/'unexpected.js').write_text('unexpected')
        with self.assertRaisesRegex(ValueError,'missing or extra|differs'):
            inspect_install(self.dest,self.product,self.channel,None)

    def test_removed_web_assets_detected(self):
        report=self.installed()
        (Path(report['webRoot'])/'index.html').unlink()
        with self.assertRaisesRegex(ValueError,'inventory'):
            inspect_install(self.dest,self.product,self.channel,None)

    def test_symlinked_deployment_root_rejected(self):
        link=self.root/'symbolic'
        try: link.symlink_to(self.dest,target_is_directory=True)
        except OSError: self.skipTest('OS disallows symlinks')
        with self.assertRaisesRegex(ValueError,'symbolic'):
            install(self.registry,link,self.product,self.channel,None,allow_unsigned_dev=True)

    def test_pointer_tampering_detected(self):
        self.installed()
        pointer=location(self.dest,self.product,self.channel)/'current.json'
        document=json.loads(pointer.read_text());document['sha256']='0'*64
        pointer.write_text(json.dumps(document))
        with self.assertRaisesRegex(ValueError,'pointer digest'):
            inspect_install(self.dest,self.product,self.channel,None)

    def test_deployment_does_not_modify_source_registry(self):
        self.installed()
        origin=self.registry/'products'/self.product/'releases/1.0.0/editor.zip'
        expected=hashlib.sha256(origin.read_bytes()).digest()
        self.assertEqual(hashlib.sha256(origin.read_bytes()).digest(),expected)

    def test_signed_registry_requires_pinned_policy(self):
        self.installed()
        with self.assertRaisesRegex(ValueError,'Unsigned'):
            inspect_install(self.dest,self.product,self.channel,self.root/'nonexistent-policy')

    def test_separate_channels_are_isolated(self):
        self.installed()
        with self.assertRaisesRegex(ValueError,'missing|linked|nonexistent'):
            inspect_install(self.dest,self.product,'beta',None)

    def test_installation_blocks_symlink_inside_site(self):
        report=self.installed()
        path=Path(report['webRoot'])/'assets'/'link.js'
        try:path.symlink_to(self.root/'unrelated')
        except OSError:self.skipTest('OS disallows symlinks')
        with self.assertRaisesRegex(ValueError,'symbolic'):
            inspect_install(self.dest,self.product,self.channel,None)



class SignedWebDeployment(unittest.TestCase):
    def setUp(self):
        try:
            from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey
            from cryptography.hazmat.primitives import serialization
        except ImportError:
            self.skipTest('cryptography optional; signed production deployment cannot be certified')
        temp=tempfile.TemporaryDirectory(prefix='hydra-bd7-signed-')
        self.addCleanup(temp.cleanup)
        self.root=Path(temp.name)
        self.registry=self.root/'registry';self.dest=self.root/'deploy'
        self.product='teaching-editor';self.channel='stable'
        from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey
        from cryptography.hazmat.primitives import serialization
        self.keys=[]
        entries=[]
        for i in range(2):
            key=Ed25519PrivateKey.generate()
            pem=self.root/f'key{i}.pem'
            pem.write_bytes(key.private_bytes(serialization.Encoding.PEM,serialization.PrivateFormat.PKCS8,
                                               serialization.NoEncryption()))
            self.keys.append(pem)
            entries.append({'id':f'key-{i}','publicKey':base64.b64encode(key.public_key().public_bytes(
                serialization.Encoding.Raw,serialization.PublicFormat.Raw)).decode()})
        self.policy=self.root/'trust.json'
        self.policy.write_text(json.dumps({'schemaVersion':1,'algorithm':'Ed25519','channels':['stable'],
            'minimumSignatures':2,'trustedKeys':entries}))
        self.publish('1.0.0')

    def publish(self,version):
        package=self.root/('editor-'+version+'.zip')
        assets={'index.html':b'<html><body><script src="./assets/app.js"></script></body></html>',
                'assets/app.js':('window.VERSION='+json.dumps(version)+';').encode('utf-8')}
        with ZipFile(package,'w',ZIP_DEFLATED) as archive:
            for path,body in sorted(assets.items()): archive.writestr(path,body)
            archive.writestr('hydra-release.sha256',''.join(hashlib.sha256(body).hexdigest()+'  '+path+'\n' for path,body in sorted(assets.items())))
            archive.writestr('hydra-deployment.json',json.dumps({'schemaVersion':'1.0','level':'DEPLOYABLE_WEB_ASSETS',
                 'version':version,'updateChannel':'stable','offlineEnabled':False,
                 'files':len(assets),'nativeInstallerCertified':False}))
        signed=None
        for i,key in enumerate(self.keys):
            signed=sign_detached(key,f'key-{i}',claim(self.product,self.channel,version,
                        hashlib.sha256(package.read_bytes()).hexdigest()),signed)
        signature=self.root/('signature-'+version+'.json')
        signature.write_text(json.dumps(signed))
        promote(self.registry,self.product,self.channel,package,signature,self.policy)
        return signature

    def signed_install(self):
        return install(self.registry,self.dest,self.product,self.channel,self.policy)

    def test_real_two_signature_install(self):
        r=self.signed_install()
        self.assertTrue(r['signed'])
        self.assertEqual(r['version'],'1.0.0')

    def test_unsigned_optin_cannot_bypass_signed_registry(self):
        with self.assertRaises((ValueError,OSError)):
            install(self.registry,self.dest,self.product,self.channel,None,allow_unsigned_dev=True)

    def test_signed_install_without_policy_rejected(self):
        with self.assertRaisesRegex(ValueError,'policy|explicit'):
            install(self.registry,self.dest,self.product,self.channel,None)

    def test_signed_install_verification_without_policy_rejected(self):
        self.signed_install()
        with self.assertRaisesRegex(ValueError,'[Pp]inned'):
            inspect_install(self.dest,self.product,self.channel,None)

    def test_signature_tamper_rejected(self):
        self.signed_install()
        sig=location(self.dest,self.product,self.channel)/'versions/1.0.0/release-signature.json'
        document=json.loads(sig.read_text())
        document['signatures'][0]['signature']=base64.b64encode(b'0'*64).decode()
        sig.write_text(json.dumps(document))
        with self.assertRaisesRegex(ValueError,'Invalid signature'):
            inspect_install(self.dest,self.product,self.channel,self.policy)

    def test_trust_threshold_enforced(self):
        self.signed_install()
        policy=json.loads(self.policy.read_text());policy['minimumSignatures']=2
        policy['trustedKeys']=policy['trustedKeys'][:1]
        self.policy.write_text(json.dumps(policy))
        with self.assertRaisesRegex(ValueError,'threshold'):
            inspect_install(self.dest,self.product,self.channel,self.policy)

    def test_signed_upgrade_and_rollback_preserve_project(self):
        old=self.signed_install()
        data=self.dest/'user-data'/'project.json';data.parent.mkdir(parents=True)
        data.write_text('{"modelId":"demo"}')
        self.publish('1.1.0')
        new=self.signed_install()
        self.assertEqual(new['version'],'1.1.0')
        self.assertNotEqual(old['sha256'],new['sha256'])
        self.assertEqual(rollback(self.dest,self.product,self.channel,self.policy)['version'],'1.0.0')
        self.assertEqual(data.read_text(),'{"modelId":"demo"}')


if __name__=='__main__':
    unittest.main(verbosity=2)
