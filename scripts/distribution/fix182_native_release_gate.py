#!/usr/bin/env python3
"""Fix182 D12+D13: pinned native-evidence release gate, NOT a substitute for native tests.

Only administrator-signed reports from the official test runners can satisfy the
release gate. Source-integrity checks, a process launch, or unsigned JSON alone
cannot authorize a native release. No commands are executed from evidence files.
"""
from __future__ import annotations
import argparse
import base64
import hashlib
import json
from pathlib import Path
import re
import sys

from editor_release_trust import policy_keys, read_document, b64, require_crypto

DOMAIN=b'HYDRA-FIX182-EXECUTED-EVIDENCE-V1\x00'
HEX=re.compile(r'[0-9a-f]{64}\Z')
TARGETS=('EMF','LANGIUM','SIRIUS_DESKTOP','ECLIPSE_GLSP','XTEXT','ANTLR','BESSER_SDK','BESSER_WEB','WINDOWS','LINUX','MACOS','DISTRIBUTION')
TESTS={
 'native':('generatedByHydra','officialToolchainExecuted','observableBehaviorCompared','nativeModelRoundTrip'),
 'desktop':('osInstalled','guiLaunched','modelOpened','modelEdited','modelSaved','modelReopened','signedUpdaterVerified','migrationRollbackVerified')
}
MAX=65536


def read(path:Path)->dict:
    if path.is_symlink() or not path.is_file() or path.stat().st_size>MAX:
        raise ValueError('Evidence document missing, linked, or oversized')
    data=json.loads(path.read_text(encoding='utf-8'))
    if not isinstance(data,dict):raise ValueError('Evidence document must be an object')
    return data


def sha(raw:bytes)->str:return hashlib.sha256(raw).hexdigest()


def signed_receipt(receipt:dict, signature:dict, policy:dict)->bool:
    Ed25519PublicKey,_,InvalidSignature=require_crypto()
    keys,minimum=policy_keys(policy,'stable')
    if signature.get('schemaVersion')!=1 or signature.get('algorithm')!='Ed25519':
        raise ValueError('Unsupported evidence signature')
    raw=json.dumps(receipt,sort_keys=True,separators=(',',':'),ensure_ascii=True).encode('ascii')
    if signature.get('receiptSha256')!=sha(raw):raise ValueError('Evidence signature bound to a different receipt')
    entries=signature.get('signatures')
    if not isinstance(entries,list) or not 1<=len(entries)<=16:raise ValueError('Missing evidence signatures')
    seen=set();valid=set()
    for entry in entries:
        if not isinstance(entry,dict) or not isinstance(entry.get('keyId'),str):raise ValueError('Invalid signer')
        key=entry['keyId']
        if key in seen:raise ValueError('Duplicate evidence signer')
        seen.add(key)
        proof=b64(entry.get('signature'),64)
        if key in keys:
            try:Ed25519PublicKey.from_public_bytes(keys[key]).verify(proof,DOMAIN+raw)
            except InvalidSignature:raise ValueError('Invalid trusted evidence signature') from None
            valid.add(key)
    if len(valid)<minimum:raise ValueError('Insufficient trusted evidence signatures')
    return True


def validate(receipt:dict,expected_target:str,evidence_root:Path,package_sha:str,expected_source_sha:str)->dict:
    if receipt.get('schemaVersion')!=1 or receipt.get('fix')!='Fix182':raise ValueError('Unknown evidence schema/fix')
    if receipt.get('target')!=expected_target:raise ValueError('Evidence target mismatch')
    expected_scope='desktop' if expected_target in ('WINDOWS','LINUX','MACOS') else 'native'
    if receipt.get('scope')!=expected_scope:raise ValueError('Evidence scope mismatch')
    if receipt.get('packageSha256')!=package_sha:raise ValueError('Evidence release hash mismatch')
    if not isinstance(receipt.get('runId'),str) or not re.fullmatch(r'[a-zA-Z0-9._-]{8,128}',receipt['runId']):
        raise ValueError('Missing trusted run identifier')
    if not isinstance(receipt.get('sourceSha256'),str) or not HEX.fullmatch(receipt['sourceSha256']):
        raise ValueError('Missing real canonical source fingerprint')
    if receipt['sourceSha256'] != expected_source_sha:
        raise ValueError('Attested source does not match the local canonical manifest')
    observed=receipt.get('observations')
    if not isinstance(observed,dict) or any(observed.get(name) is not True for name in TESTS[expected_scope]):
        raise ValueError('Required native observations missing or not passed')
    evidence=receipt.get('executionLog')
    if not isinstance(evidence,dict) or not isinstance(evidence.get('path'),str):
        raise ValueError('Missing executable test log')
    name=evidence['path']
    if not name or '\\' in name or ':' in name or name.startswith('/') or len(name)>180 or any(
            part in ('','.','..') for part in name.split('/')):raise ValueError('Unsafe evidence log path')
    if evidence_root.is_symlink() or not evidence_root.is_dir():
        raise ValueError('Evidence root missing or linked')
    path=evidence_root.joinpath(name)
    if not path.resolve().is_relative_to(evidence_root.resolve()) or any(
            ancestor.is_symlink() for ancestor in [path, *path.parents] if ancestor == evidence_root or evidence_root in ancestor.parents):
        raise ValueError('Evidence log escapes its trusted root through a link')
    if not path.is_file() or path.stat().st_size>20*1024*1024:
        raise ValueError('Missing or linked test log')
    if not isinstance(evidence.get('sha256'),str) or sha(path.read_bytes())!=evidence['sha256']:
        raise ValueError('Test log digest mismatch')
    return {'scope':expected_scope,'target':expected_target,'status':'SIGNED_EXECUTION_ATTESTED',
            'runId':receipt['runId'],'sourceSha256':receipt['sourceSha256']}


def evaluate(evidence_dir:Path, package:Path, policy_file:Path|None, required:list[str], canonical_manifest:Path|None=None)->dict:
    if not package.is_file() or package.is_symlink():raise ValueError('Release package not found or linked')
    h=hashlib.sha256()
    with package.open('rb') as f:
        for block in iter(lambda:f.read(65536),b''):h.update(block)
    pkg=h.hexdigest()
    if canonical_manifest is None:
        raise ValueError('A local canonical source manifest is mandatory for release attestation')
    if canonical_manifest.is_symlink() or not canonical_manifest.is_file():
        raise ValueError('Canonical source manifest missing or linked')
    if canonical_manifest.stat().st_size > 1024*1024:
        raise ValueError('Canonical source manifest is oversized')
    manifest_bytes=canonical_manifest.read_bytes()
    if not manifest_bytes or not manifest_bytes.endswith(b'\n'):
        raise ValueError('Canonical source manifest is missing or malformed')
    source_digest=sha(manifest_bytes)
    policy=read(policy_file) if policy_file else None
    items=[]
    for target in required:
        if target not in TARGETS:raise ValueError('Unknown required target: '+target)
        if required.count(target)!=1:raise ValueError('Duplicate release target: '+target)
        path=evidence_dir/(target.lower()+'.json')
        sign=evidence_dir/(target.lower()+'.signatures.json')
        try:
            if policy is None:raise ValueError('Administrator-pinned trust policy is required')
            receipt=read(path)
            validated=validate(receipt,target,evidence_dir,pkg,source_digest)
            signed_receipt(receipt,read(sign),policy)
            items.append(validated)
        except (ValueError,OSError,KeyError,UnicodeError,json.JSONDecodeError) as error:
            items.append({'target':target,'status':'BLOCKED','reason':str(error)})
    return {'schemaVersion':1,'fix':'Fix182','releasePackageSha256':pkg,
            'nativeReleaseReady':bool(items) and all(item['status']=='SIGNED_EXECUTION_ATTESTED' for item in items),
            'requiredTargets':required,'targets':items,
            'limitation':'Trusted runner attestation; not proof for unrequested technologies or OSs'}


def main()->int:
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--package',type=Path,required=True)
    p.add_argument('--evidence-dir',type=Path,required=True)
    p.add_argument('--trust-policy',type=Path)
    p.add_argument('--canonical-manifest',type=Path,required=True,help='Local MANIFEST_SHA256.txt whose bytes must match the signed sourceSha256')
    p.add_argument('--require',action='append',required=True,choices=TARGETS)
    p.add_argument('--report',type=Path)
    args=p.parse_args()
    try:
        result=evaluate(args.evidence_dir,args.package,args.trust_policy,args.require,args.canonical_manifest)
        if args.report:
            if args.report.is_symlink():raise ValueError('Refusing symbolic report path')
            args.report.parent.mkdir(parents=True,exist_ok=True)
            args.report.write_text(json.dumps(result,sort_keys=True,indent=2)+'\n',encoding='utf-8')
        print(json.dumps(result,sort_keys=True,indent=2))
        return 0 if result['nativeReleaseReady'] else 2
    except (OSError,ValueError) as exc:
        print('FIX182_NATIVE_RELEASE_GATE=FAIL '+str(exc),file=sys.stderr)
        return 2

if __name__=='__main__':raise SystemExit(main())
