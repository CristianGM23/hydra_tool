#!/usr/bin/env python3
"""B4+D4 regression: cross-technology semantic parity and trusted native/web release."""
from __future__ import annotations

import base64
import hashlib
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch
from zipfile import ZIP_DEFLATED, ZipFile

BASE = Path(__file__).resolve().parents[2]
sys.path[:0] = [str(BASE/'scripts/validation'), str(BASE/'scripts/distribution')]
from run_native_parity_matrix import load_matrix, run_matrix, suite_semantics
import run_native_parity_matrix as matrix
import editor_release_registry as registry
import editor_release_trust as trust
import verify_native_installer as installer


class NativeCrossTechnologyMatrixTests(unittest.TestCase):
    def setUp(self):
        tmp = tempfile.TemporaryDirectory(prefix='hydra-bd4-parity-')
        self.addCleanup(tmp.cleanup)
        self.root = Path(tmp.name)
        self.members = []
        for name in ('editor-a', 'editor-b'):
            folder = self.root/name
            (folder/'tests').mkdir(parents=True)
            (folder/'package.json').write_text('{}')
            (folder/'package-lock.json').write_text('{}')
            (folder/'tests'/'input.json').write_text('{"value":1}')
            (folder/'tests'/'expected.json').write_text('{"result":true,"id":"x"}')
            self.write_suite(folder)
            self.members.append({'id': name, 'profile': 'generated-web' if name == 'editor-a' else 'langium',
                                 'project': name, 'suite':'tests/scenarios.json'})
        self.config = {'schemaVersion':1,'groups':[{'id':'paired-editors', 'minimumPlatforms':2,
            'requiredCapabilities':['VALIDATION'], 'members':self.members}]}
        self.file = self.root/'matrix.json'
        self.save()

    def write_suite(self, folder):
        (folder/'tests'/'scenarios.json').write_text(json.dumps({'schemaVersion':1,'scenarios':[
            {'id':'validate','capability':'VALIDATION', 'input':'tests/input.json',
             'expected':'tests/expected.json','evidence':'target/validation.json'}]}))

    def save(self):
        self.file.write_text(json.dumps(self.config))

    def certified(self, profile, project, suite, timeout, execute):
        return {'status':'PASSED','functionalParityCertified': True, 'native':{'status':'PASSED'},
                'certifiedCapabilities':['VALIDATION'],'scenarios':[{'id':'validate','status':'PASSED'}]}

    def test_two_matching_native_semantics(self):
        with patch.object(matrix,'certify',side_effect=self.certified):
            r=run_matrix(self.root,load_matrix(self.file),30,True)
        self.assertEqual(r['status'],'PASSED')
        self.assertFalse(r['universalTechnologyParityCertified'])
        self.assertEqual(r['groups'][0]['platformsCertified'],2)

    def test_structural_only_never_certified(self):
        with patch.object(matrix,'certify',side_effect=self.certified):
            self.assertEqual(run_matrix(self.root,load_matrix(self.file),30,False)['status'],'UNVERIFIED')

    def test_disagreement_fails_despite_green_native(self):
        (self.root/'editor-b/tests/expected.json').write_text('{"result":false,"id":"x"}')
        with patch.object(matrix,'certify',side_effect=self.certified):
            r=run_matrix(self.root,load_matrix(self.file),30,True)
        self.assertEqual(r['status'],'UNVERIFIED')
        self.assertIn('differs',r['groups'][0]['semanticDisagreements'][0])

    def test_semantically_identical_json_key_order_is_supported(self):
        (self.root/'editor-b/tests/expected.json').write_text('{"id":"x","result":true}')
        with patch.object(matrix,'certify',side_effect=self.certified):
            self.assertEqual(run_matrix(self.root,load_matrix(self.file),30,True)['status'],'PASSED')

    def test_missing_scenario_fails(self):
        folder=self.root/'editor-b'
        (folder/'tests/scenarios.json').write_text(json.dumps({'schemaVersion':1,'scenarios':[
            {'id':'different','capability':'VALIDATION','input':'tests/input.json',
             'expected':'tests/expected.json','evidence':'target/validation.json'}]}))
        with patch.object(matrix,'certify',side_effect=self.certified):
            self.assertEqual(run_matrix(self.root,load_matrix(self.file),30,True)['status'],'UNVERIFIED')

    def test_required_capability_missing_from_evidence(self):
        def limited(*args):
            result=self.certified(*args)
            result['certifiedCapabilities']=[]
            return result
        with patch.object(matrix,'certify',side_effect=limited):
            self.assertEqual(run_matrix(self.root,load_matrix(self.file),30,True)['status'],'UNVERIFIED')

    def test_boolean_and_integer_are_not_equivalent(self):
        (self.root/'editor-b/tests/expected.json').write_text('{"result":1,"id":"x"}')
        with patch.object(matrix,'certify',side_effect=self.certified):
            self.assertEqual(run_matrix(self.root,load_matrix(self.file),30,True)['status'],'UNVERIFIED')

    def test_one_member_never_proves_cross_platform_parity(self):
        self.config['groups'][0]['members']=self.members[:1]
        self.save()
        with self.assertRaisesRegex(ValueError,'at least two'):
            load_matrix(self.file)

    def test_same_toolchain_twice_does_not_count_as_technology_parity(self):
        self.config['groups'][0]['members'][1]['profile']='generated-web'
        self.save()
        with self.assertRaisesRegex(ValueError,'distinct native profiles'):
            load_matrix(self.file)

    def test_duplicate_platforms_rejected(self):
        self.config['groups'][0]['members'][1]['id']='editor-a'
        self.save()
        with self.assertRaisesRegex(ValueError,'Duplicate'):
            load_matrix(self.file)

    def test_unsafe_project_path_rejected(self):
        self.config['groups'][0]['members'][1]['project']='../secret'
        self.save()
        with self.assertRaises(ValueError):
            run_matrix(self.root,load_matrix(self.file),30,False)

    def test_unknown_capability_rejected(self):
        self.config['groups'][0]['requiredCapabilities']=['ROOT_ACCESS']
        self.save()
        with self.assertRaisesRegex(ValueError,'capability'):
            load_matrix(self.file)


class SignedDistributionTests(unittest.TestCase):
    def setUp(self):
        try:
            from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey
            from cryptography.hazmat.primitives import serialization
        except ImportError:
            self.skipTest('cryptography unavailable: optional signed distribution gate not installed')
        temp=tempfile.TemporaryDirectory(prefix='hydra-bd4-signed-')
        self.addCleanup(temp.cleanup)
        self.root=Path(temp.name)
        self.registry=self.root/'registry'
        self.policy_file=self.root/'trusted.json'
        self.signatures=self.root/'signatures.json'
        self.keys=[]
        trust_keys=[]
        for i in range(2):
            key=Ed25519PrivateKey.generate()
            private=self.root/f'private-{i}.pem'
            private.write_bytes(key.private_bytes(serialization.Encoding.PEM,
                                  serialization.PrivateFormat.PKCS8,serialization.NoEncryption()))
            pub=key.public_key().public_bytes(serialization.Encoding.Raw,serialization.PublicFormat.Raw)
            trust_keys.append({'id':f'build-{i}', 'publicKey':base64.b64encode(pub).decode('ascii')})
            self.keys.append(private)
        self.policy={'schemaVersion':1,'algorithm':'Ed25519','channels':['stable','beta'],
                     'minimumSignatures':2,'trustedKeys':trust_keys}
        self.save_policy()
        self.package=self.create_package('1.0.0')
        self.signed=self.sign_package(self.package,'1.0.0')

    def save_policy(self):
        self.policy_file.write_text(json.dumps(self.policy))

    def create_package(self,version,content=b'<html>editor</html>'):
        target=self.root/('editor-'+version+'.zip')
        files={'index.html':content,'assets/app.js':b'window.hydra = true;'}
        hashes=''.join(hashlib.sha256(v).hexdigest()+'  '+name+'\n' for name,v in sorted(files.items()))
        meta={'level':'DEPLOYABLE_WEB_ASSETS','version':version,'files':len(files),
              'nativeInstallerCertified':False,'updateChannel':'stable'}
        with ZipFile(target,'w',compression=ZIP_DEFLATED) as arc:
            for name,data in {**files,'hydra-release.sha256':hashes.encode(),
                              'hydra-deployment.json':json.dumps(meta).encode()}.items():
                arc.writestr(name,data)
        return target

    def sign_package(self,package,version):
        signed_claim=trust.claim('business-editor','stable',version,registry.sha256(package))
        signature=None
        for i,key in enumerate(self.keys):
            signature=trust.sign_detached(key,f'build-{i}',signed_claim,signature)
        target=self.root/('sig-'+version+'.json')
        target.write_text(json.dumps(signature))
        return target

    def promote(self,package=None,version='1.0.0',signature=None,policy=None):
        return registry.promote(self.registry,'business-editor','stable',package or self.package,
                                signature or self.signed,policy or self.policy_file)

    def test_threshold_two_signature_valid(self):
        result=self.promote()
        self.assertTrue(result['authenticityVerified'])
        report=registry.inspect(self.registry,'business-editor','stable',self.policy_file)
        self.assertTrue(report['integrity']['authenticityVerified'])

    def test_signed_release_cannot_be_inspected_without_policy(self):
        self.promote()
        with self.assertRaisesRegex(ValueError,'trust policy'):
            registry.inspect(self.registry,'business-editor','stable')

    def test_signature_tamper_is_rejected(self):
        altered=json.loads(self.signed.read_text())
        altered['signatures'][0]['signature']=base64.b64encode(b'\x00'*64).decode()
        self.signed.write_text(json.dumps(altered))
        with self.assertRaisesRegex(ValueError,'Invalid signature'):
            self.promote()

    def test_signature_tamper_after_promotion_rejected(self):
        self.promote()
        location=self.registry/'products/business-editor/releases/1.0.0/release-signature.json'
        record=json.loads(location.read_text());record['signatures'][0]['signature']=base64.b64encode(b'\x00'*64).decode()
        location.write_text(json.dumps(record))
        with self.assertRaisesRegex(ValueError,'signature'):
            registry.inspect(self.registry,'business-editor','stable',self.policy_file)

    def test_unknown_key_or_insufficient_quorum_denied(self):
        self.policy['trustedKeys']=self.policy['trustedKeys'][:1]
        self.policy['minimumSignatures']=1
        self.policy['trustedKeys'][0]['id']='different'
        self.save_policy()
        with self.assertRaisesRegex(ValueError,'Insufficient'):
            self.promote()

    def test_product_channel_and_version_are_bound(self):
        for value in (trust.claim('other-editor','stable','1.0.0',registry.sha256(self.package)),
                      trust.claim('business-editor','beta','1.0.0',registry.sha256(self.package)),
                      trust.claim('business-editor','stable','2.0.0',registry.sha256(self.package))):
            with self.subTest(value=value),self.assertRaisesRegex(ValueError,'identity'):
                trust.verify_detached(json.loads(self.signed.read_text()),self.policy,value)

    def test_changed_zip_cannot_reuse_signature(self):
        changed=self.create_package('1.0.0',b'altered editor')
        with self.assertRaisesRegex(ValueError,'identity'):
            registry.promote(self.registry,'business-editor','stable',changed,self.signed,self.policy_file)

    def test_rollback_preserves_trust(self):
        self.promote()
        upgraded=self.create_package('1.1.0')
        upgraded_sig=self.sign_package(upgraded,'1.1.0')
        registry.promote(self.registry,'business-editor','stable',upgraded,upgraded_sig,self.policy_file)
        result=registry.rollback(self.registry,'business-editor','stable',self.policy_file)
        self.assertTrue(result['authenticityVerified'])
        self.assertEqual(registry.inspect(self.registry,'business-editor','stable',self.policy_file)['version'],'1.0.0')

    def test_unsigned_to_signed_mode_fails_closed(self):
        registry.promote(self.registry,'business-editor','stable',self.package)
        with self.assertRaisesRegex(ValueError,'unsigned'):
            registry.inspect(self.registry,'business-editor','stable',self.policy_file)

    def test_signed_release_record_cannot_downgrade_to_unsigned(self):
        self.promote()
        path=self.registry/'products/business-editor/releases/1.0.0/release.json'
        document=json.loads(path.read_text());document['trustedPublication']=False
        path.write_text(json.dumps(document))
        with self.assertRaisesRegex(ValueError,'unsigned/ambiguous'):
            registry.inspect(self.registry,'business-editor','stable',self.policy_file)

    def test_wrong_trust_policy_on_rollback_is_rejected(self):
        self.promote()
        upgraded=self.create_package('1.1.0');upgraded_sig=self.sign_package(upgraded,'1.1.0')
        registry.promote(self.registry,'business-editor','stable',upgraded,upgraded_sig,self.policy_file)
        altered=json.loads(self.policy_file.read_text());altered['trustedKeys']=altered['trustedKeys'][:1]
        altered['minimumSignatures']=1
        altered['trustedKeys'][0]['id']='untrusted-key'
        self.policy_file.write_text(json.dumps(altered))
        with self.assertRaisesRegex(ValueError,'Insufficient'):
            registry.rollback(self.registry,'business-editor','stable',self.policy_file)

    def test_missing_optional_trust_in_legacy_remains_unsigned(self):
        result=registry.promote(self.registry,'business-editor','stable',self.package)
        self.assertFalse(result['authenticityVerified'])
        self.assertFalse(registry.inspect(self.registry,'business-editor','stable')['authenticityVerified'])

    def test_signing_private_key_not_embedded_in_release(self):
        self.promote()
        release=self.registry/'products/business-editor/releases/1.0.0'
        self.assertFalse(any(f.suffix == '.pem' for f in release.rglob('*')))


class InstallerInventoryTests(unittest.TestCase):
    def setUp(self):
        temp=tempfile.TemporaryDirectory(prefix='hydra-bd4-installer-')
        self.addCleanup(temp.cleanup)
        self.editor=Path(temp.name)
        self.bundle=self.editor/'src-tauri/target/release/bundle'
        self.bundle.mkdir(parents=True)
        self.report={'qualified':True, 'version':'1.2.3',
                     'steps':{'nativeBuild':{'status':'PASSED','bundlePaths':['nsis/product-1.2.3.exe']}}}
        (self.bundle/'nsis').mkdir()
        (self.bundle/'nsis/product-1.2.3.exe').write_bytes(b'MZ'+b'x'*2000)

    def test_windows_real_bundle_inventory(self):
        out=installer.verify(self.editor,self.report,'windows')
        self.assertEqual(out['status'],'PASSED')
        self.assertFalse(out['nativeInstallLaunchCertified'])
        self.assertFalse(out['osCodeSignatureVerified'])

    def test_native_build_unverified_rejected(self):
        self.report['steps']['nativeBuild']['status']='NOT_EXECUTED'
        with self.assertRaisesRegex(ValueError,'successful'):
            installer.verify(self.editor,self.report,'windows')

    def test_bundle_symlink_rejected(self):
        target=self.bundle/'real.exe';target.write_bytes(b'x'*2000)
        link=self.bundle/'nsis/link.exe'
        try:link.symlink_to(target)
        except (OSError,NotImplementedError):self.skipTest('no symlink privilege')
        self.report['steps']['nativeBuild']['bundlePaths']=['nsis/link.exe']
        with self.assertRaisesRegex(ValueError,'symlink'):
            installer.verify(self.editor,self.report,'windows')

    def test_path_traversal_rejected(self):
        self.report['steps']['nativeBuild']['bundlePaths']=['../other.exe']
        with self.assertRaisesRegex(ValueError,'escapes'):
            installer.verify(self.editor,self.report,'windows')

    def test_wrong_os_suffix_rejected(self):
        with self.assertRaisesRegex(ValueError,'Unsupported'):
            installer.verify(self.editor,self.report,'macos')

    def test_linux_deb_bundle_inventory(self):
        (self.bundle/'linux').mkdir()
        (self.bundle/'linux/editor.deb').write_bytes(b'DEB'+b'x'*2000)
        self.report['steps']['nativeBuild']['bundlePaths']=['linux/editor.deb']
        self.assertEqual(installer.verify(self.editor,self.report,'linux')['bundleCount'],1)

    def test_macos_app_requires_executable(self):
        app=self.bundle/'macos/Business.app'
        (app/'Contents/MacOS').mkdir(parents=True)
        self.report['steps']['nativeBuild']['bundlePaths']=['macos/Business.app']
        with self.assertRaisesRegex(ValueError,'inventory|executable'):
            installer.verify(self.editor,self.report,'macos')
        (app/'Contents/MacOS/editor').write_bytes(b'\x00'*2000)
        self.assertEqual(installer.verify(self.editor,self.report,'macos')['bundleCount'],1)

    def test_too_small_bundle_rejected(self):
        (self.bundle/'nsis/product-1.2.3.exe').write_bytes(b'MZ')
        with self.assertRaisesRegex(ValueError,'empty'):
            installer.verify(self.editor,self.report,'windows')


if __name__ == '__main__':
    result=unittest.main(verbosity=1,exit=False)
    if not result.result.wasSuccessful():
        raise SystemExit(1)
    print(f'BD_BLOCK4=PASS python_cases={result.result.testsRun}')
