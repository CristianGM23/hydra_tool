#!/usr/bin/env python3
"""Prepara el frontend del editor generado en dist para Tauri, sin compilar Studio.

Solo acepta el ZIP extraído de un editor HYDRA con frontend y descriptor reales.
No sobrescribe un dist diferente ni sigue enlaces simbólicos.
"""
from __future__ import annotations
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import tempfile

MAX_FILES=2048
MAX_BYTES=64*1024*1024


def inventory(root:Path)->dict[str,bytes]:
    if not root.is_dir() or root.is_symlink():
        raise ValueError('Editor frontend must be a real directory')
    files={}
    total=0
    for entry in sorted(root.rglob('*')):
        if entry.is_symlink():
            raise ValueError('Symbolic link in generated frontend: '+str(entry))
        if entry.is_dir():
            continue
        if not entry.is_file():
            raise ValueError('Unsupported frontend entry: '+str(entry))
        relative=entry.relative_to(root).as_posix()
        if any(part.startswith('.') for part in Path(relative).parts):
            raise ValueError('Hidden file or directory not allowed: '+relative)
        if relative in files or len(files)>=MAX_FILES:
            raise ValueError('Duplicate or excessive generated frontend entries')
        size=entry.stat().st_size
        total+=size
        if total>MAX_BYTES:
            raise ValueError('Generated frontend exceeds size limit')
        files[relative]=entry.read_bytes()
    return files


def prepare(editor:Path)->dict:
    editor=editor.absolute()
    if not editor.is_dir() or editor.is_symlink():
        raise ValueError('Editor root must be a real directory')
    descriptor=editor/'standalone-editor.json'
    if not descriptor.is_file() or descriptor.is_symlink():
        raise ValueError('Missing or linked generated HYDRA descriptor')
    raw=descriptor.read_bytes()
    if len(raw)>1024*1024:
        raise ValueError('Descriptor exceeds limit')
    config=json.loads(raw)
    if not isinstance(config,dict) or not config.get('applicationId') or not config.get('editorId'):
        raise ValueError('Expected generated HYDRA editor descriptor')
    files=inventory(editor/'frontend')
    for required in ('index.html','app.js','native-transport.js','runtime.js','styles.css','config/capabilities.json'):
        if required not in files:
            raise ValueError('Generated frontend incomplete: '+required)
    files['standalone-editor.json']=raw
    destination=editor/'dist'
    if destination.exists() or destination.is_symlink():
        current=inventory(destination)
        if current != files:
            raise ValueError('Refusing to overwrite different dist assets; prepare in a clean editor directory')
        return {'status':'UNCHANGED','files':len(files),'descriptorSha256':hashlib.sha256(raw).hexdigest()}
    with tempfile.TemporaryDirectory(prefix='.hydra-tauri-',dir=editor) as scratch:
        staging=Path(scratch)/'dist'
        for name, content in files.items():
            target=staging/name
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(content)
        os.rename(staging,destination)
    return {'status':'PREPARED','files':len(files),'descriptorSha256':hashlib.sha256(raw).hexdigest()}


def main()->int:
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--editor-root',type=Path,required=True)
    args=parser.parse_args()
    try:
        print(json.dumps(prepare(args.editor_root),sort_keys=True))
        return 0
    except (ValueError,OSError,json.JSONDecodeError) as problem:
        print('TAURI_FRONTEND_PREPARATION=FAIL '+str(problem))
        return 2

if __name__=='__main__':
    raise SystemExit(main())
