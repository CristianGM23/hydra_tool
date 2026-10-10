#!/usr/bin/env python3
"""D9: transactional, reversible, declarative standalone model migration.

Operate ONLY on an offline generated editor's dedicated user-data directory.
No arbitrary scripts are executed, source files are never blindly overwritten,
and interrupted transactions are recoverable using the checksummed journal.
Native OS installer/update certification is a separate requirement.
"""
from __future__ import annotations
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import sys
import uuid

MAX_FILES=1024
MAX_BYTES=8*1024*1024
IDENT=re.compile(r'^[A-Za-z0-9][A-Za-z0-9._:-]{0,160}$')

def sha(body:bytes)->str:return hashlib.sha256(body).hexdigest()

def atomic(path:Path,body:bytes):
    tmp=path.with_name('.'+path.name+'.'+uuid.uuid4().hex+'.tmp')
    try:
        with tmp.open('xb') as stream:
            stream.write(body);stream.flush();os.fsync(stream.fileno())
        os.replace(tmp,path)
    finally:
        if tmp.exists():tmp.unlink()

def guarded(path:Path, label:str, *, directory=True)->Path:
    absolute=path.absolute()
    for parent in [absolute,*absolute.parents]:
        if parent.is_symlink():raise ValueError(label+' traverses symbolic link')
    if directory and not absolute.is_dir():raise ValueError(label+' must be an existing directory')
    return absolute

def read_json(path:Path)->dict:
    if path.is_symlink() or not path.is_file() or path.stat().st_size>MAX_BYTES:raise ValueError('Missing, linked or oversized '+path.name)
    doc=json.loads(path.read_text(encoding='utf-8'))
    if not isinstance(doc,dict):raise ValueError('Expected JSON object in '+path.name)
    return doc

def files(data:Path)->list[Path]:
    if any(child.is_symlink() for child in data.iterdir()):raise ValueError('Symbolic files in model data root forbidden')
    found=sorted(data.glob('*.model.json'))
    if not found or len(found)>MAX_FILES:raise ValueError('No models, or too many model files')
    for f in found:
        if not f.is_file() or f.is_symlink() or f.stat().st_size>MAX_BYTES:
            raise ValueError('Invalid model data file')
    return found

def plan_valid(plan:dict)->dict:
    if plan.get('schemaVersion')!=1 or not all(isinstance(plan.get(k),str) and IDENT.fullmatch(plan[k]) for k in ('fromVersion','toVersion','metamodelId')):
        raise ValueError('Invalid migration identity')
    if plan['fromVersion']==plan['toVersion']:raise ValueError('No-op migration forbidden')
    mappings=plan.get('renameAttributes',{})
    if not isinstance(mappings,dict) or len(mappings)>128:raise ValueError('Invalid attribute mapping')
    if any(not isinstance(a,str) or not isinstance(b,str) or not a or not b or a==b for a,b in mappings.items()):
        raise ValueError('Invalid attribute rename')
    if len(set(mappings.values()))!=len(mappings):raise ValueError('Two attributes map to one destination')
    return mappings

def transform(source:bytes, plan:dict, mappings:dict)->bytes:
    doc=json.loads(source.decode('utf-8'))
    if not isinstance(doc,dict) or doc.get('metamodelId')!=plan['metamodelId'] or doc.get('schemaVersion')!=plan['fromVersion']:
        raise ValueError('Model schema/metamodel mismatch')
    for obj in doc.get('elements',[]):
        values=obj.get('attributes',{})
        if not isinstance(values,dict):raise ValueError('Invalid canonical attributes')
        for old,new in mappings.items():
            if old in values:
                if new in values:raise ValueError('Attribute migration conflict: '+new)
                values[new]=values.pop(old)
    doc['schemaVersion']=plan['toVersion']
    return (json.dumps(doc,sort_keys=True,ensure_ascii=False,separators=(',',':'))+'\n').encode('utf-8')

def journal_dir(data:Path)->Path:return data.parent/('.hydra-migration-'+data.name)

def safe_model_filename(name):
    return isinstance(name,str) and name.endswith('.model.json') and IDENT.fullmatch(name) is not None and '/' not in name and '\\' not in name

def load_journal(data:Path)->tuple[Path,dict]:
    base=guarded(journal_dir(data),'Migration journal')
    return base,read_json(base/'journal.json')

def recover(data:Path)->dict:
    data=guarded(data,'Model data root')
    base,record=load_journal(data)
    if record.get('state')!='PENDING':raise ValueError('No interrupted migration')
    for entry in record['files']:
        if not safe_model_filename(entry.get('file')):raise ValueError('Unsafe migration journal filename')
        file=data/entry['file'];old=(base/'backup'/entry['file']).read_bytes()
        if sha(old)!=entry['before']:raise ValueError('Recovery backup checksum mismatch')
        if file.is_symlink() or not file.is_file():raise ValueError('Recovery target missing or linked')
        if sha(file.read_bytes()) not in (entry['before'],entry['after']):
            raise ValueError('Recovery conflicts with a concurrent user edit')
    for entry in record['files']:
        atomic(data/entry['file'],(base/'backup'/entry['file']).read_bytes())
    record['state']='RECOVERED'
    atomic(base/'journal.json',json.dumps(record,sort_keys=True).encode())
    return {'status':'RECOVERED','files':len(record['files']),'dataRoot':str(data)}

def migrate(data:Path,plan_path:Path, *, simulate_failure_after:int|None=None)->dict:
    data=guarded(data,'Model data root')
    plan=read_json(plan_path);mappings=plan_valid(plan)
    base=journal_dir(data)
    if base.exists() or base.is_symlink():
        raise ValueError('Migration journal exists: recover/rollback before a new migration')
    model_files=files(data)
    old_files={file.name:file.read_bytes() for file in model_files}
    new_files={name:transform(content,plan,mappings) for name,content in old_files.items()}
    entries=[{'file':name,'before':sha(content),'after':sha(new_files[name])} for name,content in old_files.items()]
    base.mkdir(mode=0o700)
    try:
        backup=base/'backup';backup.mkdir(mode=0o700)
        for name,content in old_files.items():atomic(backup/name,content)
        record={'schemaVersion':1,'state':'PENDING','planSHA256':sha(plan_path.read_bytes()),
                'fromVersion':plan['fromVersion'],'toVersion':plan['toVersion'],'files':entries}
        atomic(base/'journal.json',json.dumps(record,sort_keys=True).encode())
        for index,(name,body) in enumerate(new_files.items(),1):
            if sha((data/name).read_bytes())!=sha(old_files[name]):raise ValueError('Concurrent model change during migration')
            atomic(data/name,body)
            if index==simulate_failure_after:raise RuntimeError('INJECTED: Interrupted model migration')
        record['state']='COMMITTED'
        atomic(base/'journal.json',json.dumps(record,sort_keys=True).encode())
        return {'status':'MIGRATED','files':len(entries),'fromVersion':plan['fromVersion'],
                'toVersion':plan['toVersion'],'backupRoot':str(base),'nativeInstallerCertified':False}
    except Exception:
        if (base/'journal.json').exists():
            recover(data)
        else:shutil.rmtree(base)
        raise

def rollback_data(data:Path)->dict:
    data=guarded(data,'Model data root')
    base,record=load_journal(data)
    if record['state']!='COMMITTED':raise ValueError('Migration is not committed')
    for entry in record['files']:
        if not safe_model_filename(entry.get('file')):raise ValueError('Unsafe migration journal filename')
        file=data/entry['file']
        if file.is_symlink() or not file.is_file() or sha(file.read_bytes())!=entry['after']:
            raise ValueError('Rollback blocked: user modified a migrated model')
        original=base/'backup'/entry['file']
        if original.is_symlink() or sha(original.read_bytes())!=entry['before']:
            raise ValueError('Rollback backup was modified')
    for entry in record['files']:
        atomic(data/entry['file'],(base/'backup'/entry['file']).read_bytes())
    record['state']='ROLLED_BACK';atomic(base/'journal.json',json.dumps(record,sort_keys=True).encode())
    return {'status':'ROLLED_BACK','files':len(record['files'])}

def main()->int:
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('action',choices=('migrate','rollback','recover'))
    p.add_argument('--data-root',type=Path,required=True);p.add_argument('--plan',type=Path)
    p.add_argument('--editor-stopped',action='store_true',help='Explicit assertion: no process can write models')
    args=p.parse_args()
    try:
        if not args.editor_stopped:raise ValueError('Stop the editor first and pass --editor-stopped')
        if args.action=='migrate':
            if args.plan is None:raise ValueError('Migration requires plan')
            result=migrate(args.data_root,args.plan)
        elif args.action=='rollback':result=rollback_data(args.data_root)
        else:result=recover(args.data_root)
        print(json.dumps(result,sort_keys=True));return 0
    except (OSError,ValueError,KeyError,TypeError,RuntimeError,json.JSONDecodeError) as exc:
        print('FIX180_MODEL_MIGRATION=FAIL '+str(exc),file=sys.stderr);return 2

if __name__=='__main__':raise SystemExit(main())
