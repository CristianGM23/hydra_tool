#!/usr/bin/env python3
"""Unit/regression cases for Fix182's pinned native-release evidence gate."""
import base64
import copy
import hashlib
import json
from pathlib import Path
import sys
import tempfile
import unittest
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'distribution'))
import fix182_native_release_gate as gate
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey
from cryptography.hazmat.primitives.serialization import Encoding,PublicFormat


class ReleaseGateTests(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory();self.addCleanup(self.tmp.cleanup)
        self.root=Path(self.tmp.name);self.pkg=self.root/'pkg.zip';self.pkg.write_bytes(b'original package')
        self.folder=self.root/'evidence';self.folder.mkdir()
        self.manifest=self.root/'MANIFEST_SHA256.txt';self.manifest.write_bytes((('a'*64)+'  README.md\n').encode())
        self.priv=[Ed25519PrivateKey.generate() for _ in range(2)]
        self.policy={'schemaVersion':1,'algorithm':'Ed25519','channels':['stable'], 'minimumSignatures':2,
                     'trustedKeys':[{'id':f'ci-{i}','publicKey':base64.b64encode(k.public_key().public_bytes(Encoding.Raw,PublicFormat.Raw)).decode()}
                                    for i,k in enumerate(self.priv)]}
        self.trust=self.root/'trust.json';self.trust.write_text(json.dumps(self.policy))
        self.make('ECLIPSE_GLSP');self.make('WINDOWS')
    def make(self,target):
        log=self.folder/(target.lower()+'.log');log.write_bytes(b'actual run log' +target.encode())
        checks={key:True for key in gate.TESTS['desktop' if target=='WINDOWS' else 'native']}
        doc={'schemaVersion':1,'fix':'Fix182','target':target,'scope':'desktop' if target=='WINDOWS' else 'native',
             'packageSha256':hashlib.sha256(self.pkg.read_bytes()).hexdigest(),'sourceSha256':hashlib.sha256(self.manifest.read_bytes()).hexdigest(),
             'runId':'trusted-run-56789','observations':checks,'executionLog':{'path':log.name,'sha256':hashlib.sha256(log.read_bytes()).hexdigest()}}
        path=self.folder/(target.lower()+'.json');path.write_text(json.dumps(doc))
        self.sign(target,doc)
    def sign(self,target,doc):
        raw=json.dumps(doc,sort_keys=True,separators=(',',':'),ensure_ascii=True).encode('ascii')
        bundle={'schemaVersion':1,'algorithm':'Ed25519','receiptSha256':hashlib.sha256(raw).hexdigest(),
                'signatures':[{'keyId':f'ci-{i}','signature':base64.b64encode(k.sign(gate.DOMAIN+raw)).decode()}
                              for i,k in enumerate(self.priv)]}
        (self.folder/(target.lower()+'.signatures.json')).write_text(json.dumps(bundle))
    def report(self,targets=['ECLIPSE_GLSP']):return gate.evaluate(self.folder,self.pkg,self.trust,targets,self.manifest)
    def test_missing_manifest_is_not_certification(self):
        with self.assertRaisesRegex(ValueError,'mandatory'):
            gate.evaluate(self.folder,self.pkg,self.trust,['WINDOWS'])
    def test_manifest_changed_after_attestation_blocks(self):
        self.manifest.write_bytes((('b'*64)+'  README.md\n').encode())
        self.assertFalse(self.report(['WINDOWS'])['nativeReleaseReady'])
    def test_symlinked_manifest_rejected(self):
        link=self.root/'linked-manifest'
        try:link.symlink_to(self.manifest)
        except (OSError,NotImplementedError):self.skipTest('symlinks unavailable')
        with self.assertRaisesRegex(ValueError,'linked'):
            gate.evaluate(self.folder,self.pkg,self.trust,['WINDOWS'],link)
    def test_wrong_signed_source_digest_blocks(self):
        p=self.folder/'windows.json';d=json.loads(p.read_text());d['sourceSha256']='b'*64
        p.write_text(json.dumps(d));self.sign('WINDOWS',d)
        self.assertFalse(self.report(['WINDOWS'])['nativeReleaseReady'])
    def test_proper_scoped_attestations(self):self.assertTrue(self.report(['ECLIPSE_GLSP','WINDOWS'])['nativeReleaseReady'])
    def test_unrequested_os_is_not_certified(self):self.assertEqual(len(self.report()['targets']),1)
    def test_missing_native_platform_blocks(self):self.assertFalse(self.report(['ECLIPSE_GLSP','MACOS'])['nativeReleaseReady'])
    def test_without_pinned_keys_blocks(self):self.assertFalse(gate.evaluate(self.folder,self.pkg,None,['WINDOWS'],self.manifest)['nativeReleaseReady'])
    def test_duplicate_required_targets_rejected(self):
        with self.assertRaisesRegex(ValueError,'Duplicate'):self.report(['WINDOWS','WINDOWS'])
    def test_package_tampering_blocks(self):self.pkg.write_bytes(b'different');self.assertFalse(self.report()['nativeReleaseReady'])
    def test_log_tampering_blocks(self):(self.folder/'eclipse_glsp.log').write_text('forged');self.assertFalse(self.report()['nativeReleaseReady'])
    def test_missing_signed_update_blocks(self):
        p=self.folder/'windows.json';d=json.loads(p.read_text());d['observations']['signedUpdaterVerified']=False
        p.write_text(json.dumps(d));self.sign('WINDOWS',d)
        self.assertFalse(self.report(['WINDOWS'])['nativeReleaseReady'])
    def test_editing_claim_without_resigning_blocks(self):
        p=self.folder/'windows.json';d=json.loads(p.read_text());d['runId']='another-trusted-run';p.write_text(json.dumps(d))
        self.assertFalse(self.report(['WINDOWS'])['nativeReleaseReady'])
    def test_only_one_signature_blocks_two_of_two(self):
        p=self.folder/'windows.signatures.json';d=json.loads(p.read_text());d['signatures']=d['signatures'][:1];p.write_text(json.dumps(d))
        self.assertFalse(self.report(['WINDOWS'])['nativeReleaseReady'])
    def test_duplicate_signature_blocks(self):
        p=self.folder/'windows.signatures.json';d=json.loads(p.read_text());d['signatures']=[d['signatures'][0]]*2;p.write_text(json.dumps(d))
        self.assertFalse(self.report(['WINDOWS'])['nativeReleaseReady'])
    def test_parent_escape_log_blocked(self):
        p=self.folder/'eclipse_glsp.json';d=json.loads(p.read_text());d['executionLog']['path']='../escape.log';p.write_text(json.dumps(d));self.sign('ECLIPSE_GLSP',d)
        self.assertFalse(self.report()['nativeReleaseReady'])
    def test_native_built_only_is_not_certification(self):
        p=self.folder/'eclipse_glsp.json';d=json.loads(p.read_text());d['observations']['observableBehaviorCompared']=False;p.write_text(json.dumps(d));self.sign('ECLIPSE_GLSP',d)
        self.assertFalse(self.report()['nativeReleaseReady'])
    def test_mismatched_target_signed_cannot_substitute(self):
        p=self.folder/'eclipse_glsp.json';d=json.loads(p.read_text());d['target']='WINDOWS';p.write_text(json.dumps(d));self.sign('ECLIPSE_GLSP',d)
        self.assertFalse(self.report()['nativeReleaseReady'])

if __name__=='__main__':unittest.main(verbosity=2)
