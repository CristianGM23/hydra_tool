#!/usr/bin/env python3
"""Synthetic negative/positive regressions for Fix185's STRICT real-native gate.

IMPORTANT: Synthetic fixtures exercise the verifier; they are not official MDE
runs, independent native parity proof, signed commercial updates, or OS GUI E2E.
"""
from __future__ import annotations
import base64
from copy import deepcopy
import hashlib
import json
from pathlib import Path
import sys
import tempfile
import unittest

ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'scripts/distribution'))
import fix182_native_release_gate as older
import fix185_complete_line_gate as gate
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey
from cryptography.hazmat.primitives.serialization import Encoding,PublicFormat


def sha(raw):return hashlib.sha256(raw).hexdigest()

class StrictNativeGateTest(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory(prefix='hydra-fix185-synthetic-');self.addCleanup(self.tmp.cleanup)
        self.root=Path(self.tmp.name)
        self.pkg=self.root/'pkg.zip';self.pkg.write_bytes(b'not a real package')
        self.manifest=self.root/'MANIFEST_SHA256.txt';self.manifest.write_text('a'*64+'  TEST_SOURCE.txt\n',encoding='utf-8')
        self.ev=self.root/'evidence';self.ev.mkdir()
        self.oracles=self.root/'oracles';self.oracles.mkdir()
        self.outputs=self.root/'outputs';self.outputs.mkdir()
        self.keys=[Ed25519PrivateKey.generate() for _ in range(2)]
        policy={'schemaVersion':1,'algorithm':'Ed25519','channels':['stable'],'minimumSignatures':2,
                'trustedKeys':[{'id':f'ci-{i}', 'publicKey':base64.b64encode(k.public_key().public_bytes(Encoding.Raw,PublicFormat.Raw)).decode('ascii')}
                               for i,k in enumerate(self.keys)]}
        self.trust=self.root/'trust.json';self.trust.write_text(json.dumps(policy),encoding='utf-8')
        for target in gate.RUNNER:self.make(target)

    def make(self,target):
        log=self.ev/(target.lower()+'.log');log.write_bytes(b'SYNTHETIC TEST ONLY:'+target.encode())
        scope='desktop' if target in ('WINDOWS','LINUX','MACOS') else 'native'
        checks={key:True for key in older.TESTS[scope]}
        proofs=[]
        for case in gate.matrix():
            if case['target']!=target:continue
            expected={'observations':{'operations':[case['scenario']],
                       'result':{'elementId':'fixture-'+case['id'],'state':'persisted'}}}
            raw=json.dumps(expected,sort_keys=True).encode()
            (self.oracles/(case['id']+'.expected.json')).write_bytes(raw)
            (self.outputs/(case['id']+'.observed.json')).write_bytes(raw)
            proofs.append({'caseId':case['id'],'scenario':case['scenario'],'status':'PASSED',
                           'officialRunner':gate.RUNNER[target],'runtimeVersion':'synthetic-fixture-0',
                           'nativeRuntimeExecuted':True,'oracleIndependentFromRunner':True,'processExitCode':0,
                           'oracleSha256':sha(raw),'observedSha256':sha(raw)})
        receipt={'schemaVersion':1,'fix':'Fix182','target':target,'scope':scope,
                 'packageSha256':sha(self.pkg.read_bytes()),'sourceSha256':sha(self.manifest.read_bytes()),
                 'runId':'synthetic-run-123','observations':checks,
                 'executionLog':{'path':log.name,'sha256':sha(log.read_bytes())},'scenarioProofs':proofs}
        (self.ev/(target.lower()+'.json')).write_text(json.dumps(receipt))
        self.sign(target,receipt)

    def sign(self,target,receipt):
        raw=json.dumps(receipt,sort_keys=True,separators=(',',':'),ensure_ascii=True).encode('ascii')
        bundle={'schemaVersion':1,'algorithm':'Ed25519','receiptSha256':sha(raw),'signatures':[
                {'keyId':f'ci-{i}','signature':base64.b64encode(k.sign(older.DOMAIN+raw)).decode('ascii')}
                for i,k in enumerate(self.keys)]}
        (self.ev/(target.lower()+'.signatures.json')).write_text(json.dumps(bundle))

    def report(self):
        return gate.evaluate(self.pkg,self.manifest,self.ev,self.trust,self.oracles,self.outputs)

    def change_receipt(self,target,modify,resign=True):
        path=self.ev/(target.lower()+'.json');receipt=json.loads(path.read_text())
        modify(receipt);path.write_text(json.dumps(receipt))
        if resign:self.sign(target,receipt)

    def test_strict_gate_has_all_63_obligations(self):
        cases=gate.matrix()
        self.assertEqual(len(cases),63)
        self.assertEqual(set(c['target'] for c in cases),set(gate.RUNNER))

    def test_all_synthetic_fixtures_only_show_mechanics(self):
        result=self.report()
        self.assertTrue(result['nativeLineComplete'])
        self.assertEqual(result['casesPassed'],63)
        # This is NOT a field reported as real native execution by the synthetic test.

    def test_missing_one_native_target_blocks_whole_line(self):
        (self.ev/'emf.signatures.json').unlink()
        r=self.report();self.assertFalse(r['nativeLineComplete']);self.assertEqual(r['casesPassed'],54)

    def test_missing_one_real_scenario_blocks_whole_line(self):
        self.change_receipt('LANGIUM',lambda d:d['scenarioProofs'].pop())
        self.assertEqual(self.report()['casesPassed'],62)

    def test_wrong_official_runner_rejected(self):
        self.change_receipt('WINDOWS',lambda d:d['scenarioProofs'][0].update(officialRunner='portable-js-simulator'))
        self.assertEqual(self.report()['casesPassed'],62)

    def test_wrong_runtime_execution_flag_rejected(self):
        self.change_receipt('SIRIUS_DESKTOP',lambda d:d['scenarioProofs'][0].update(nativeRuntimeExecuted=False))
        self.assertEqual(self.report()['casesPassed'],62)

    def test_invalid_exit_code_rejected(self):
        self.change_receipt('MACOS',lambda d:d['scenarioProofs'][0].update(processExitCode=1))
        self.assertEqual(self.report()['casesPassed'],62)

    def test_attested_runtime_receipt_tampering_fails_all_target_cases(self):
        self.change_receipt('ECLIPSE_GLSP',lambda d:d.update(runId='hacked-run-456'),resign=False)
        self.assertEqual(self.report()['casesPassed'],56)

    def test_oracle_mismatch_blocks_case(self):
        name='NATIVE-001';(self.outputs/(name+'.observed.json')).write_text('{"observations":{"malicious":true}}')
        self.assertEqual(self.report()['casesPassed'],62)

    def test_oracle_mutation_blocks_case(self):
        name='NATIVE-001';(self.oracles/(name+'.expected.json')).write_text('{"observations":{"malicious":true}}')
        self.assertEqual(self.report()['casesPassed'],62)

    def test_loss_of_trust_policy_blocks_every_case(self):
        self.trust.write_text('{}')
        self.assertEqual(self.report()['casesPassed'],0)

    def test_signed_case_duplicate_blocks_target(self):
        self.change_receipt('EMF',lambda d:d['scenarioProofs'].append(deepcopy(d['scenarioProofs'][0])))
        self.assertEqual(self.report()['casesPassed'],54)

    def test_symlinked_observed_results_block(self):
        name='NATIVE-001';file=self.outputs/(name+'.observed.json')
        dest=self.root/'other';file.replace(dest)
        try:file.symlink_to(dest)
        except (OSError,NotImplementedError):self.skipTest('Symlinks unsupported')
        self.assertEqual(self.report()['casesPassed'],62)

    def test_mismatched_package_blocks_all(self):
        self.pkg.write_bytes(b'tamper')
        self.assertEqual(self.report()['casesPassed'],0)

    def test_source_manifest_mutation_blocks_all(self):
        self.manifest.write_text('b'*64+'  TEST_SOURCE.txt\n')
        self.assertEqual(self.report()['casesPassed'],0)

    def test_unsigned_legacy_build_is_not_closure(self):
        self.assertFalse(gate.evaluate(self.pkg,self.manifest,self.ev,None,self.oracles,self.outputs)['nativeLineComplete'])

if __name__=='__main__':
    unittest.main(verbosity=2)
