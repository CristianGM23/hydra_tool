#!/usr/bin/env python3
"""Verify a real HYDRA complete-project ZIP, canonical inputs and optional native build.

No project is treated as natively certified by inspecting its ZIP or running a
canonical oracle. A genuine official project build is opt-in, and functional
parity needs additional destination-side observable behavior and comparisons.
"""
from __future__ import annotations
import argparse
import hashlib
import json
from pathlib import Path, PurePosixPath
import re
import sys
import tempfile
from zipfile import ZipFile

ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'scripts/validation'))
from run_native_e2e_matrix import PROFILES,run
MAX_ZIP=192*1024*1024
MAX_ENTRY=32*1024*1024
MAX_MEMBERS=4096
HEX=re.compile(r'^[0-9a-f]{64}$')

def digest(value:bytes)->str:return hashlib.sha256(value).hexdigest()

def fingerprint(snapshot:dict)->str:
    hasher=hashlib.sha256()
    def update(key,value):
        hasher.update(key.encode());hasher.update(b'\x00');hasher.update(str(value if value is not None else '').encode());hasher.update(b'\n')
    for key,value in [('schemaVersion','1.0'),('metamodelId',snapshot['metamodelId']),
                      ('metamodelFingerprint',snapshot['metamodelFingerprint']),
                      ('projectExport',snapshot['projectExport']),('selectionMode',snapshot['selectionMode']),
                      ('editorId',snapshot.get('editorId',''))]:update(key,value)
    for key,value in sorted(snapshot.get('settings',{}).items()):update('setting:'+key,value)
    for item in snapshot['artifacts']:
        for key,value in [('artifact-kind',item['kind']),('artifact-id',item['documentId']),
                          ('artifact-sha256',item['sha256'])]:update(key,value)
    return hasher.hexdigest()

def safe_member(name:str)->bool:
    if not isinstance(name,str) or not name or '\\' in name or ':' in name or name.startswith('/') or '\x00' in name:return False
    parts=name.split('/')
    return all(part and part not in ('.','..') for part in parts) and len(name)<=240

def read_export(path:Path)->tuple[dict,dict,dict,dict[str,bytes]]:
    if path.is_symlink() or not path.is_file() or path.stat().st_size>MAX_ZIP:
        raise ValueError('Export ZIP unavailable, symbolic or oversized')
    members={}
    with ZipFile(path) as z:
        infos=z.infolist()
        if len(infos)>MAX_MEMBERS:raise ValueError('Export ZIP too many files')
        names=[];total=0
        for info in infos:
            if info.is_dir():continue
            if not safe_member(info.filename):raise ValueError('Unsafe export ZIP path: '+info.filename)
            if info.filename in members or info.filename.casefold() in names:
                raise ValueError('Duplicate or portable-colliding path: '+info.filename)
            names.append(info.filename.casefold())
            if (info.external_attr>>16)&0o170000==0o120000:raise ValueError('Symbolic ZIP entry forbidden')
            total+=info.file_size
            if info.file_size>MAX_ENTRY or total>MAX_ZIP:raise ValueError('Export ZIP exceeds uncompressed size limit')
            raw=z.read(info)
            if len(raw)!=info.file_size:raise ValueError('Export ZIP size mismatch')
            members[info.filename]=raw
    metadata=members.get('META-INF/hydra-technology-compilation.json')
    provenance=members.get('META-INF/hydra-project-generation-snapshot.json')
    gate=members.get('META-INF/hydra-metamodel-generation-gate.json')
    if metadata is None or provenance is None or gate is None:
        raise ValueError('Missing authentic HYDRA complete-project evidence records')
    manifest=json.loads(metadata)
    snapshot=json.loads(provenance)
    validation=json.loads(gate)
    expected={}
    for member in manifest.get('files',[]):
        name=member['path']
        if name in expected or not safe_member(name) or not HEX.fullmatch(member['sha256']):
            raise ValueError('Invalid or duplicate file declaration')
        expected[name]=member['sha256']
    if set(expected)!=(set(members)-{'META-INF/hydra-technology-compilation.json'}):
        raise ValueError('Actual artifact inventory differs from compiler manifest')
    for name,expected_hash in expected.items():
        if digest(members[name])!=expected_hash:raise ValueError('Exported file modified: '+name)
    if snapshot.get('schemaVersion')!='1.0' or snapshot.get('fingerprint')!=fingerprint(snapshot):
        raise ValueError('HYDRA generation fingerprint mismatch')
    if validation.get('generationSnapshotFingerprint')!=snapshot['fingerprint'] or validation.get('metamodelId')!=snapshot['metamodelId'] or validation.get('projectExport')!=snapshot['projectExport']:
        raise ValueError('HYDRA validation gate and source snapshot disagree')
    if validation.get('generatedProjectStructuralValidation')!='PASSED':
        raise ValueError('Generated project has no successful structural gate')
    return manifest,snapshot,validation,members

def bind_sources(snapshot:dict,inventory:Path|None)->dict:
    if inventory is None:return {'status':'UNBOUND','reason':'Provide --source-inventory for canonical inputs'}
    if inventory.is_symlink() or not inventory.is_file():raise ValueError('Source inventory not regular file')
    declaration=json.loads(inventory.read_text(encoding='utf-8'))
    if declaration.get('metamodelId')!=snapshot['metamodelId']:
        raise ValueError('Source inventory metamodel mismatch')
    declared={}
    for entry in declaration['artifacts']:
        key=(entry['kind'],entry['documentId'])
        if key in declared:raise ValueError('Duplicate canonical source inventory identity')
        relative=entry['relativePath']
        if not safe_member(relative):raise ValueError('Canonical source inventory path is unsafe')
        source=inventory.parent.joinpath(*relative.split('/'))
        if inventory.parent.resolve() not in source.resolve().parents:
            raise ValueError('Canonical input path escapes inventory directory')
        if any(piece.is_symlink() for piece in [source,*list(source.parents)] if piece!=inventory.parent.parent and inventory.parent in piece.parents):
            raise ValueError('Canonical input traverses symbolic path')
        if source.is_symlink() or not source.is_file() or source.stat().st_size>MAX_ENTRY:
            raise ValueError('Canonical input not available or not regular')
        if source.resolve().parent != inventory.parent.resolve() and inventory.parent.resolve() not in source.resolve().parents:
            raise ValueError('Canonical input path escapes inventory directory')
        declared[key]={'kind':entry['kind'],'documentId':entry['documentId'],
                       'sha256':digest(source.read_bytes()),'bytes':source.stat().st_size}
    actual={(value['kind'],value['documentId']):value for value in snapshot['artifacts']}
    if declared!=actual:
        raise ValueError('Canonical inputs diverge from generated project snapshot')
    return {'status':'BOUND','artifacts':len(actual),'fingerprint':snapshot['fingerprint']}

def verify(package:Path,inventory:Path|None=None, *, native_profile:str|None=None,execute:bool=False,
           trusted_project:bool=False,timeout:int=900)->dict:
    manifest,snapshot,gate,members=read_export(package)
    sources=bind_sources(snapshot,inventory)
    result={'schemaVersion':1,'originCryptographicallyVerified':False,'packageSHA256':digest(package.read_bytes()),
            'technology':manifest.get('technology'),'projectExport':snapshot['projectExport'],
            'generationFingerprint':snapshot['fingerprint'],'verifiedFiles':len(members)-1,
            'sourceBinding':sources,'structuralExportVerified':True,
            'nativeBuildExecuted':False,'nativeTestsPassed':False,
            'functionalParityCertified':False,'status':'STRUCTURAL_ONLY'}
    if native_profile:
        if native_profile not in PROFILES:raise ValueError('Unknown allowlisted native profile')
        if execute and (not trusted_project or sources['status']!='BOUND'):
            raise ValueError('Native execution requires explicitly trusted export and bound canonical sources')
        if execute:
            with tempfile.TemporaryDirectory(prefix='hydra-fix180-native-') as directory:
                root=Path(directory)
                for name,value in members.items():
                    if name.startswith('META-INF/'):continue
                    target=root.joinpath(*PurePosixPath(name).parts)
                    target.parent.mkdir(parents=True,exist_ok=True)
                    target.write_bytes(value)
                native=run(native_profile,root,timeout,True)
                result['nativeReport']=native
                result['nativeBuildExecuted']=True
                result['nativeTestsPassed']=native['status']=='PASSED'
                result['status']='NATIVE_BUILD_AND_TESTS' if native['status']=='PASSED' else 'NATIVE_BUILD_FAILED'
        else:
            result['nativeReport']={'status':'NOT_EXECUTED'}
    return result

def main()->int:
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--zip',type=Path,required=True)
    p.add_argument('--source-inventory',type=Path)
    p.add_argument('--native-profile',choices=sorted(PROFILES))
    p.add_argument('--execute-native',action='store_true')
    p.add_argument('--trusted-export',action='store_true')
    p.add_argument('--timeout-seconds',type=int,default=900)
    p.add_argument('--report',type=Path)
    a=p.parse_args()
    try:
        if not 1<=a.timeout_seconds<=3600:raise ValueError('Invalid timeout')
        r=verify(a.zip,a.source_inventory,native_profile=a.native_profile,execute=a.execute_native,
                 trusted_project=a.trusted_export,timeout=a.timeout_seconds)
        output=json.dumps(r,sort_keys=True,indent=2)+'\n'
        if a.report:a.report.parent.mkdir(parents=True,exist_ok=True);a.report.write_text(output,encoding='utf-8')
        print(output,end='')
        return 1 if r['status']=='NATIVE_BUILD_FAILED' else 0
    except (OSError,ValueError,KeyError,TypeError,IndexError,json.JSONDecodeError) as e:
        print('FIX180_EXPORT_PROVENANCE=FAIL '+str(e),file=sys.stderr);return 2

if __name__=='__main__':raise SystemExit(main())
