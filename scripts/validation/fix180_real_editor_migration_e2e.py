#!/usr/bin/env python3
"""D8+D9 actual generated Java HTTP editor -> migration -> restart -> rollback.

Uses HYDRA's production standalone server Java template; not a fake REST handler.
Does not substitute for MSI/AppImage/DMG installation or OS-native Tauri updates.
"""
from __future__ import annotations
import json
from pathlib import Path
import sys
ROOT=Path(__file__).resolve().parents[2]
sys.path[:0]=[str(ROOT/'scripts/validation'),str(ROOT/'scripts/distribution')]
from verify_fix180_generated_model_lifecycle import RealStandaloneLifecycle
from migrate_standalone_models import migrate,rollback_data

def main():
    RealStandaloneLifecycle.setUpClass()
    server=RealStandaloneLifecycle
    try:
        original={}
        for n in range(2):
            name=f'fix180-integration-{n}'
            model={'id':name,'schemaVersion':'1.0','metamodelId':'library',
                   'rootElementIds':['book-'+str(n)],
                   'elements':[{'id':'book-'+str(n),'classId':'class:Book',
                                'attributes':{'attribute:class:Book:pages':n+1}}],
                   'relations':[]}
            raw=json.dumps(model).encode()
            code,body,headers=server.fetch('/api/models/'+name,method='PUT',body=raw,
                headers={'Content-Type':'application/json','If-None-Match':'*'})
            if code!=200:raise ValueError(f'Real generated Java server PUT failed: {code}')
            original[name]=body
        server.stop_server()
        plan=server.root/'fix180-migration-plan.json'
        plan.write_text(json.dumps({'schemaVersion':1,'metamodelId':'library',
            'fromVersion':'1.0','toVersion':'1.1',
            'renameAttributes':{'attribute:class:Book:pages':'attribute:class:Book:pageCount'}}))
        result=migrate(server.data_root,plan)
        if result['files']!=2:raise ValueError('Did not migrate every persisted model')
        server.start_server()
        for name in original:
            code,body,_=server.fetch('/api/models/'+name)
            model=json.loads(body)
            if code!=200 or model['schemaVersion']!='1.1' or 'attribute:class:Book:pageCount' not in model['elements'][0]['attributes']:
                raise ValueError('Migrated model not visible after actual Java process restart')
        server.stop_server()
        rollback_data(server.data_root)
        server.start_server()
        for name,old in original.items():
            code,body,_=server.fetch('/api/models/'+name)
            if code!=200 or body!=old:
                raise ValueError('Rollback did not recover byte-identical persisted model over HTTP')
        print('FIX180_REAL_EDITOR_MIGRATION_E2E=PASS actual_java_server=PASS '
              'persisted_models=2 migrate_1.0_to_1.1=PASS restart=PASS '
              'rollback=PASS original_bytes=RESTORED os_tauri_installer=NOT_EXECUTED')
    finally:
        server.tearDownClass()

if __name__=='__main__':
    try:main()
    except (OSError,ValueError,RuntimeError,TimeoutError,KeyError) as exc:
        print('FIX180_REAL_EDITOR_MIGRATION_E2E=FAIL '+str(exc),file=sys.stderr)
        raise SystemExit(1)
