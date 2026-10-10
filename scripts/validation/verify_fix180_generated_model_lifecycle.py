#!/usr/bin/env python3
"""Fix180 D8 safety and persistence: run HYDRA's real generated Java server template.

Intentionally does NOT certify installers for Windows/macOS/Linux. Runs actual Java
HTTP implementation used in standalone editor generation, not a Python substitute.
"""
from __future__ import annotations
from concurrent.futures import ThreadPoolExecutor
import hashlib
import json
import os
from pathlib import Path
import shutil
import socket
import subprocess
import tempfile
import time
import unittest
from urllib.error import HTTPError
from urllib.request import Request, urlopen

ROOT = Path(__file__).resolve().parents[2]
TEMPLATE = ROOT/'src/main/resources/standalone-template/backend/src/HydraStandaloneServer.java.template'
FRONTEND = ROOT/'src/main/resources/standalone-template/frontend'

class RealStandaloneLifecycle(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        if not shutil.which('java') or not shutil.which('javac'):
            raise unittest.SkipTest('JDK 21+ required: cannot certify server without actual Java execution')
        cls.temporary = tempfile.TemporaryDirectory(prefix='hydra-fix180-native-editor-')
        cls.root = Path(cls.temporary.name)
        source = cls.root/'HydraStandaloneServer.java'
        java = TEMPLATE.read_text(encoding='utf-8').replace('__MAX_MODEL_BYTES__','8192').replace('__DEFAULT_PORT__','0').replace('__APPLICATION_ID_JSON__','test-editor')
        source.write_text(java,encoding='utf-8')
        built = subprocess.run(['javac','--release','21','-d',str(cls.root),str(source)],capture_output=True,text=True,timeout=60)
        if built.returncode:
            cls.temporary.cleanup()
            raise RuntimeError('Real standalone Java template failed compilation: '+built.stderr)
        (cls.root/'frontend').mkdir()
        (cls.root/'frontend'/'index.html').write_text('<!doctype html><title>Generated HYDRA editor</title>',encoding='utf-8')
        (cls.root/'frontend'/'app.js').write_bytes((FRONTEND/'app.js').read_bytes())
        (cls.root/'standalone-editor.json').write_text('{"applicationId":"test-editor"}')
        cls.data_root=cls.root/'durable-models'
        cls.start_server()

    @classmethod
    def start_server(cls):
        with socket.socket() as sock:
            sock.bind(('127.0.0.1',0))
            cls.port=sock.getsockname()[1]
        cls.server=subprocess.Popen(['java','-cp',str(cls.root),'HydraStandaloneServer',f'--root={cls.root}',f'--port={cls.port}',f'--data-root={cls.data_root}'],stdout=subprocess.DEVNULL,stderr=subprocess.PIPE)
        cls.url=f'http://127.0.0.1:{cls.port}'
        for attempt in range(90):
            if cls.server.poll() is not None:
                raise RuntimeError('Generated HYDRA server terminated before ready')
            try:
                if cls.fetch('/api/health')[0]==200: return
            except (OSError, HTTPError): pass
            time.sleep(.1)
        cls.stop_server()
        raise RuntimeError('Generated HYDRA server did not start')

    @classmethod
    def stop_server(cls):
        if cls.server.poll() is None:
            cls.server.terminate()
            try: cls.server.wait(timeout=6)
            except subprocess.TimeoutExpired: cls.server.kill();cls.server.wait(timeout=6)
        cls.server.stderr.close()

    @classmethod
    def tearDownClass(cls):
        cls.stop_server()
        cls.temporary.cleanup()

    @classmethod
    def fetch(cls, path, *, method='GET', body=None, headers=None):
        request=Request(cls.url+path,data=body,headers=headers or {},method=method)
        try:
            with urlopen(request,timeout=8) as response:
                return response.status,response.read(),{k.lower(): v for k, v in response.headers.items()}
        except HTTPError as err:
            with err:
                return err.code,err.read(),{k.lower(): v for k, v in err.headers.items()}

    def unique_id(self):
        return self.id().split('.')[-1].lower().replace('_','-')[:150]

    def create(self, id=None, payload=None):
        id=id or self.unique_id()
        model={'schemaVersion':'1.0','id':id,'elements':[{'id':'a','classId':'Book','attributes':{'title':'Alpha'}}],'relations':[]}
        value=json.dumps(model,ensure_ascii=False).encode() if payload is None else payload
        return id, self.fetch('/api/models/'+id,method='PUT',body=value,headers={'Content-Type':'application/json','If-None-Match':'*'})

    def test_actual_generated_editor_loads_html_and_js(self):
        self.assertIn(b'Generated HYDRA editor',self.fetch('/')[1])
        self.assertIn(b'If-None-Match',self.fetch('/app.js')[1])

    def test_actual_save_reload_and_model_integrity(self):
        id,(status,stored,headers)=self.create()
        self.assertEqual(status,200)
        self.assertEqual(json.loads(stored)['elements'][0]['attributes']['title'],'Alpha')
        initial_etag=headers['etag']
        self.assertEqual(self.fetch('/api/models/'+id)[2]['etag'],initial_etag)
        self.assertEqual(hashlib.sha256(stored).hexdigest(),initial_etag.strip('"'))
        self.assertTrue((self.data_root/f'{id}.model.json').is_file())
        self.assertIn(id,json.loads(self.fetch('/api/models')[1]))

    def test_edit_and_optimistic_concurrency(self):
        id,(status,original,headers)=self.create()
        self.assertEqual(status,200)
        changed=json.loads(original);changed['elements'][0]['attributes']['title']='Beta'
        changed_bytes=json.dumps(changed).encode()
        status,updated,new_headers=self.fetch('/api/models/'+id,method='PUT',body=changed_bytes,headers={'Content-Type':'application/json','If-Match':headers['etag']})
        self.assertEqual(status,200)
        self.assertEqual(json.loads(updated)['elements'][0]['attributes']['title'],'Beta')
        self.assertNotEqual(headers['etag'],new_headers['etag'])
        self.assertEqual(self.fetch('/api/models/'+id,method='PUT',body=original,headers={'Content-Type':'application/json','If-Match':headers['etag']})[0],412)
        self.assertEqual(self.fetch('/api/models/'+id,method='PUT',body=original,headers={'Content-Type':'application/json'})[0],428)
        self.assertEqual(self.fetch('/api/models/'+id)[1],updated)

    def test_parallel_editors_cannot_overwrite_same_revision(self):
        id,(status,initial,headers)=self.create()
        self.assertEqual(status,200)
        def replace(i):
            value=json.loads(initial)
            value['elements'][0]['attributes']['title']=f'parallel-{i}'
            return self.fetch('/api/models/'+id,method='PUT',body=json.dumps(value).encode(),
                headers={'Content-Type':'application/json','If-Match':headers['etag']})[0]
        with ThreadPoolExecutor(max_workers=8) as pool:
            outcomes=list(pool.map(replace,range(8)))
        self.assertEqual(outcomes.count(200),1)
        self.assertEqual(outcomes.count(412),7)

    def test_rejects_deep_json_nesting(self):
        id=self.unique_id()
        payload=b'{"id":"'+id.encode()+b'","nested":'+b'['*100+b'1'+b']'*100+b'}'
        self.assertEqual(self.fetch('/api/models/'+id,method='PUT',body=payload,
            headers={'Content-Type':'application/json','If-None-Match':'*'})[0],400)

    def test_failed_updates_do_not_damage_existing_model(self):
        id,(status,original,headers)=self.create()
        self.assertEqual(status,200)
        code,_,_=self.fetch('/api/models/'+id,method='PUT',body=b'{"id":"wrong"}',
            headers={'Content-Type':'application/json','If-Match':headers['etag']})
        self.assertEqual(code,400)
        self.assertEqual(self.fetch('/api/models/'+id)[1],original)
        self.assertEqual(self.fetch('/api/models/'+id)[2]['etag'],headers['etag'])

    def test_insert_requires_precondition(self):
        id=self.unique_id()
        payload=json.dumps({'id':id}).encode()
        self.assertEqual(self.fetch('/api/models/'+id,method='PUT',body=payload,headers={'Content-Type':'application/json'})[0],428)
        self.assertEqual(self.fetch('/api/models/'+id,method='GET')[0],404)

    def test_if_none_match_blocks_lost_create(self):
        id,(status,body,headers)=self.create()
        self.assertEqual(status,200)
        self.assertEqual(self.create(id)[1][0],412)
        self.assertEqual(self.fetch('/api/models/'+id)[1],body)

    def test_delete_checks_etag(self):
        id,(status,body,headers)=self.create()
        self.assertEqual(status,200)
        self.assertEqual(self.fetch('/api/models/'+id,method='DELETE')[0],428)
        self.assertEqual(self.fetch('/api/models/'+id,method='DELETE',headers={'If-Match':'"deadbeef"'})[0],412)
        self.assertEqual(self.fetch('/api/models/'+id,method='DELETE',headers={'If-Match':headers['etag']})[0],204)
        self.assertEqual(self.fetch('/api/models/'+id)[0],404)

    def test_rejects_non_json_type(self):
        id=self.unique_id()
        self.assertEqual(self.fetch('/api/models/'+id,method='PUT',body=b'{}',headers={'Content-Type':'text/plain','If-None-Match':'*'})[0],415)

    def test_rejects_windows_device_ids_and_colons(self):
        for id in ('CON', 'nul', 'COM1', 'lpt9.data', 'bad:name'):
            with self.subTest(id=id):
                code,_,_=self.fetch('/api/models/'+id)
                self.assertEqual(code,400)

    def test_rejects_invalid_utf8(self):
        self.assertEqual(self.create(payload=b'{"x":"\xff"}')[1][0],400)

    def test_rejects_invalid_json_values(self):
        id=self.unique_id()
        for data in (b'{',b'{"id":}',b'{"a":1,}',b'{"a":tru}',b'{"a":01}',b'{"a":"\\x"}',b'{"a":[1,]}',b'{"a":1} junk',b'[1,2]'):
            with self.subTest(data=data):
                self.assertEqual(self.fetch('/api/models/'+id,method='PUT',body=data,headers={'Content-Type':'application/json','If-None-Match':'*'})[0],400)

    def test_rejects_wrong_identity_and_duplicate_id(self):
        id=self.unique_id()
        for payload in (b'{"id":"other"}', b'{"id":"a","id":"b"}', b'{"elements":[]}'):
            with self.subTest(payload=payload):
                code,_,_=self.fetch('/api/models/'+id,method='PUT',body=payload,
                    headers={'Content-Type':'application/json','If-None-Match':'*'})
                self.assertEqual(code,400)
        self.assertEqual(self.fetch('/api/models/'+id)[0],404)

    def test_nested_model_and_json_literals_are_valid(self):
        id=self.unique_id()
        data=json.dumps({'id':id,'rootElementIds':['r'],'elements':[{'id':'r','attributes':{'count':-1.2e-4,'notes':'\\u2022','flag':False,'unset':None}}], 'relations': []}).encode()
        code,body,_=self.fetch('/api/models/'+id,method='PUT',body=data,
            headers={'Content-Type':'application/json; charset=utf-8','If-None-Match':'*'})
        self.assertEqual(code,200)
        self.assertEqual(body,data)

    def test_rejects_oversize(self):
        self.assertEqual(self.create(payload=b'{"large":"'+b'a'*8192+b'"}')[1][0],413)

    def test_path_traversal_rejected(self):
        for bad in ('../secret','%2e%2e%2fsecret','bad%2fsegment'):
            with self.subTest(bad=bad):
                self.assertIn(self.fetch('/api/models/'+bad)[0],(400,404))

    @unittest.skipUnless(os.name != 'nt' and hasattr(os,'symlink'),'Requires supported filesystem symlinks')
    def test_model_symlink_denied_and_not_enumerated(self):
        secret=self.root/'secret.txt';secret.write_text('secret')
        linked=self.data_root/(self.unique_id()+'.model.json')
        linked.symlink_to(secret)
        self.assertEqual(self.fetch('/api/models/'+self.unique_id())[0],400)
        self.assertNotIn(self.unique_id(),json.loads(self.fetch('/api/models')[1]))
        self.assertEqual(secret.read_text(),'secret')

    @unittest.skipUnless(os.name != 'nt' and hasattr(os,'symlink'),'Requires supported filesystem symlinks')
    def test_static_symlink_denied(self):
        secret=self.root/'secret-static.txt';secret.write_text('private')
        (self.root/'frontend'/'hidden.txt').symlink_to(secret)
        self.assertEqual(self.fetch('/hidden.txt')[0],404)

    def test_model_survives_application_asset_replacement(self):
        id,(status,stored,headers)=self.create()
        self.assertEqual(status,200)
        self.stop_server()
        page=self.root/'frontend'/'index.html'
        page.write_text('<!doctype html><title>HYDRA editor upgraded v2</title>',encoding='utf-8')
        self.start_server()
        self.assertIn(b'upgraded v2',self.fetch('/')[1])
        self.assertEqual(self.fetch('/api/models/'+id)[1],stored)
        self.assertEqual(self.fetch('/api/models/'+id)[2]['etag'],headers['etag'])

    def test_save_is_preserved_after_restart(self):
        id,(status,stored,headers)=self.create()
        self.assertEqual(status,200)
        self.stop_server()
        self.start_server()
        self.assertEqual(self.fetch('/api/models/'+id)[1],stored)
        self.assertEqual(self.fetch('/api/models/'+id)[2]['etag'],headers['etag'])

if __name__=='__main__':
    unittest.main(verbosity=2)
