#!/usr/bin/env python3
"""Fail-closed acceptance gate for the entire B10-B13 + D10-D13 delivery.

Every requirement needs an administrator-signed Fix182 native runner receipt,
plus a specific executed scenario comparing observed bytes with a separate,
reviewed, immutable oracle file. A receipt alone is NEVER sufficient.
The verifier runs no external programs and does not itself issue certificates.
"""
from __future__ import annotations
import argparse
import hashlib
import json
from pathlib import Path
import re
import sys
from typing import Any

import fix182_native_release_gate as native_gate

SOURCE_ROOT = Path(__file__).resolve().parents[2]
MATRIX = SOURCE_ROOT/'scripts/validation/fix185_native_acceptance_matrix.json'
HEX = re.compile(r'[0-9a-f]{64}\Z')
MAX_FILE = 4*1024*1024
MAX_ORACLE = 256*1024
RUNNER = {
    'EMF': 'emf-official-eclipse-runtime',
    'LANGIUM': 'langium-official-runtime',
    'SIRIUS_DESKTOP': 'sirius-official-eclipse-runtime',
    'ECLIPSE_GLSP': 'glsp-official-server-and-client',
    'XTEXT': 'xtext-official-runtime',
    'ANTLR': 'antlr-official-runtime',
    'BESSER_SDK': 'besser-official-sdk-runtime',
    'BESSER_WEB': 'besser-official-web-runtime',
    'WINDOWS': 'tauri-installed-windows-gui',
    'LINUX': 'tauri-installed-linux-gui',
    'MACOS': 'tauri-installed-macos-gui',
    'DISTRIBUTION': 'hydra-trusted-distribution-e2e',
}


def file_at(root: Path, relative: str, limit: int = MAX_FILE) -> Path:
    if (not isinstance(relative, str) or not 1 <= len(relative) <= 180 or
        '\\' in relative or ':' in relative or relative.startswith('/') or
        any(p in ('', '..', '.') for p in relative.split('/'))):
        raise ValueError('Unsafe proof path')
    if root.is_symlink() or not root.is_dir():
        raise ValueError('Evidence/oracle root not a real directory')
    path = root
    for part in relative.split('/'):
        path = path/part
        if path.is_symlink():
            raise ValueError('Symlink in proof path')
    if not path.is_file() or path.stat().st_size > limit:
        raise ValueError('Missing or oversized proof file: '+relative)
    return path


def sha(raw: bytes) -> str:
    return hashlib.sha256(raw).hexdigest()


def matrix() -> list[dict]:
    data = json.loads(MATRIX.read_text(encoding='utf-8'))
    if data.get('schemaVersion') != 1 or data.get('specification') != 'B10_B11_B12_B13_D10_D11_D12_D13':
        raise ValueError('Wrong acceptance matrix version')
    cases = data['cases']
    if len(cases) != 63 or set(c['target'] for c in cases) != set(RUNNER):
        raise ValueError('Incomplete or changed HYDRA native acceptance matrix')
    if len(set(c['id'] for c in cases)) != len(cases):
        raise ValueError('Duplicate acceptance case ID')
    if any(c['id'] != f'NATIVE-{i:03d}' for i,c in enumerate(cases,1)):
        raise ValueError('Acceptance matrix identifiers must not drift')
    return cases


def validate_case(spec: dict, proof: Any, oracle_root: Path, output_root: Path) -> dict:
    name = spec['id']
    if not isinstance(proof, dict) or proof.get('caseId') != name or proof.get('scenario') != spec['scenario']:
        raise ValueError('Missing/mismatched signed case proof')
    if proof.get('status') != 'PASSED' or proof.get('nativeRuntimeExecuted') is not True:
        raise ValueError('Native behavior not executed and passed')
    if proof.get('officialRunner') != RUNNER[spec['target']]:
        raise ValueError('Unapproved runner or portable emulator substituted for native runtime')
    version=proof.get('runtimeVersion')
    if not isinstance(version,str) or not re.fullmatch(r'[a-zA-Z0-9][a-zA-Z0-9.+_ -]{0,119}',version):
        raise ValueError('Missing toolchain version')
    expected_name = name+'.expected.json'
    observed_name = name+'.observed.json'
    oracle = file_at(oracle_root, expected_name, MAX_ORACLE)
    output = file_at(output_root, observed_name, MAX_ORACLE)
    expected_bytes = oracle.read_bytes()
    observed_bytes = output.read_bytes()
    expected_digest = sha(expected_bytes)
    observed_digest = sha(observed_bytes)
    if proof.get('oracleSha256') != expected_digest or proof.get('observedSha256') != observed_digest:
        raise ValueError('Test outputs do not match the signed proof')
    # Semantically compare parsed JSON with the independent oracle. Do not accept
    # a string 'PASS' or a manually provided counter as observable behavior.
    expected = json.loads(expected_bytes)
    actual = json.loads(observed_bytes)
    if not isinstance(expected,dict) or not isinstance(actual,dict) or 'observations' not in expected:
        raise ValueError('Oracle must describe concrete observable state')
    if expected != actual:
        raise ValueError('Observed behavior differs from reviewed oracle')
    observations=actual['observations']
    if not isinstance(observations,dict) or not observations or any(v is None for v in observations.values()):
        raise ValueError('No observable model/toolchain outcomes')
    if proof.get('oracleIndependentFromRunner') is not True:
        raise ValueError('Independent oracle not attested')
    if proof.get('processExitCode') != 0:
        raise ValueError('Native program returned non-zero exit code')
    return {'caseId':name,'target':spec['target'],'status':'ATTESTED_ORACLE_MATCH','runtimeVersion':version}


def evaluate(package:Path, canonical_manifest:Path, evidence_dir:Path, trust_policy:Path|None,
             oracle_root:Path, output_root:Path) -> dict:
    cases = matrix()
    targets = list(RUNNER)
    attestation = native_gate.evaluate(evidence_dir,package,trust_policy,targets,canonical_manifest)
    trusted={v['target'] for v in attestation['targets'] if v['status']=='SIGNED_EXECUTION_ATTESTED'}
    results=[]
    for spec in cases:
        target=spec['target']
        try:
            if target not in trusted:
                raise ValueError('Missing trusted signed official-runner attestation')
            receipt=native_gate.read(evidence_dir/(target.lower()+'.json'))
            proofs=receipt.get('scenarioProofs')
            if not isinstance(proofs,list):
                raise ValueError('Missing signed scenario proofs')
            if any(not isinstance(v,dict) for v in proofs):
                raise ValueError('Invalid scenario proofs')
            if len(proofs)!=len({v.get('caseId') for v in proofs}):
                raise ValueError('Duplicate scenario proof')
            proof=next((v for v in proofs if v.get('caseId')==spec['id']),None)
            results.append(validate_case(spec,proof,oracle_root,output_root))
        except (OSError,ValueError,KeyError,TypeError,UnicodeError,json.JSONDecodeError) as error:
            results.append({'caseId':spec['id'],'target':target,'status':'BLOCKED','reason':str(error)})
    complete=sum(v['status']=='ATTESTED_ORACLE_MATCH' for v in results)
    return {'schemaVersion':1,'fix':'Fix185','nativeLineComplete':complete==len(cases),
            'status':'COMPLETE' if complete==len(cases) else 'BLOCKED',
            'casesRequired':len(cases),'casesPassed':complete,'casesBlocked':len(cases)-complete,
            'signedTargets':sorted(trusted),'requiredTargets':targets,'results':results,
            'certificationScope':'FULL_NATIVE_E2E_ORACLE_ATTESTATION',
            'limitations':'Requires trusted real CI evidence and independent, reviewed expected outputs; no receipts fabricated'}


def main() -> int:
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--package',type=Path,required=True)
    parser.add_argument('--canonical-manifest',type=Path,required=True)
    parser.add_argument('--evidence-dir',type=Path,required=True)
    parser.add_argument('--trust-policy',type=Path,required=True)
    parser.add_argument('--oracle-root',type=Path,required=True)
    parser.add_argument('--observed-root',type=Path,required=True)
    parser.add_argument('--report',type=Path)
    args=parser.parse_args()
    try:
        result=evaluate(args.package,args.canonical_manifest,args.evidence_dir,args.trust_policy,
                        args.oracle_root,args.observed_root)
        if args.report:
            if args.report.is_symlink():raise ValueError('Symlink report output')
            args.report.parent.mkdir(parents=True,exist_ok=True)
            args.report.write_text(json.dumps(result,indent=2,sort_keys=True,ensure_ascii=False)+'\n',encoding='utf-8')
        print(f"HYDRA_NATIVE_CLOSURE={result['status']} {result['casesPassed']}/{result['casesRequired']}")
        if result['casesBlocked']:
            for v in result['results']:
                if v['status']=='BLOCKED':print('  '+v['caseId']+' '+v['target']+' '+v['reason'])
        return 0 if result['nativeLineComplete'] else 2
    except (ValueError,OSError,KeyError,TypeError,UnicodeError,json.JSONDecodeError) as exc:
        print('HYDRA_NATIVE_CLOSURE=FAIL '+str(exc),file=sys.stderr)
        return 2

if __name__=='__main__':raise SystemExit(main())
