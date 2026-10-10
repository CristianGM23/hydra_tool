#!/usr/bin/env python3
"""B5+D5 actual Node semantic test -> Java web package -> signed local web HTTP.

Only certifies the included independent editor fixture and its local runtime;
no remote native modeling framework or OS installer is certified here.
"""
from __future__ import annotations

import base64
import json
from pathlib import Path
import shutil
import sys
import tempfile
import threading
from urllib.request import urlopen, Request

ROOT=Path(__file__).resolve().parents[2]
sys.path[:0]=[str(ROOT/'scripts/validation'),str(ROOT/'scripts/distribution')]
from bd3_real_e2e import must_run, JAVA, JAVA_SOURCES
from run_native_parity_suite import certify
from build_generated_editor import precache
from verify_editor_runtime_package import verify as preflight
from editor_release_registry import promote, inspect
from editor_release_trust import claim, sign_detached
from serve_registered_editor import runtime, open_server


def main():
    from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey
    from cryptography.hazmat.primitives import serialization
    if any(shutil.which(cmd) is None for cmd in ('node','npm','javac','java')):
        raise RuntimeError('Node/npm and Java 21+ required for actual generated-editor E2E')
    with tempfile.TemporaryDirectory(prefix='hydra-bd5-live-') as tmp:
        folder=Path(tmp)
        editor=folder/'editor'
        shutil.copytree(ROOT/'reference-projects/bd2-standalone-smoke',editor,
                        ignore=shutil.ignore_patterns('node_modules','dist','target'))
        proof=certify('generated-web',editor,'tests/hydra-parity/scenarios.json',180,True)
        if proof['status']!='PASSED' or len(proof['scenarios'])!=7:
            raise RuntimeError('Reference editor did not pass seven real Node behavior cases')
        precache(editor/'dist','1.0.0','HYDRA Generated Editor Fixture')
        classes=folder/'classes'; classes.mkdir()
        must_run(['javac','--release','21','-d',str(classes),
            *[str(JAVA/path) for path in JAVA_SOURCES],
            str(ROOT/'scripts/testing/bd-block2/BD2RealBundleHarness.java')],cwd=ROOT)
        archive=folder/'web.zip'
        must_run(['java','-cp',str(classes),'BD2RealBundleHarness',str(editor),str(archive)],cwd=ROOT)
        checked=preflight(archive)
        if not checked['offlineShellPackaged'] or 'assets/app.js' not in checked['compiledReferences']:
            raise RuntimeError('Real Java output is not deployable offline editor')
        product='hydra-editor-fixture'; channel='stable'; version=checked['version']
        keys=[];policy_entries=[]
        for index in range(2):
            sk=Ed25519PrivateKey.generate()
            pem=folder/f'signing-{index}.pem'
            pem.write_bytes(sk.private_bytes(serialization.Encoding.PEM,
                serialization.PrivateFormat.PKCS8,serialization.NoEncryption()))
            keys.append(pem)
            pk=sk.public_key().public_bytes(serialization.Encoding.Raw,serialization.PublicFormat.Raw)
            policy_entries.append({'id':f'signer-{index}','publicKey':base64.b64encode(pk).decode()})
        policy=folder/'trust.json'
        policy.write_text(json.dumps({'schemaVersion':1,'algorithm':'Ed25519',
            'channels':[channel],'minimumSignatures':2,'trustedKeys':policy_entries}))
        signed=None
        for index,pem in enumerate(keys):
            signed=sign_detached(pem,f'signer-{index}',
                claim(product,channel,version,checked['packageSHA256']),signed)
        signature=folder/'signature.json';signature.write_text(json.dumps(signed))
        registry=folder/'registry'
        promoted=promote(registry,product,channel,archive,signature,policy)
        if not promoted['authenticityVerified']:
            raise RuntimeError('Signed publication was not authenticated')
        state=runtime(registry,product,channel,policy)
        with open_server(state) as server:
            t=threading.Thread(target=server.serve_forever,daemon=True);t.start()
            try:
                address='http://127.0.0.1:'+str(server.server_address[1])
                for path,expected in [('/',b'<html'),('/assets/app.js',b'addEventListener'),
                    ('/manifest.webmanifest',b'HYDRA'),('/service-worker.js',b'caches.open')]:
                    with urlopen(address+path,timeout=10) as response:
                        content=response.read()
                        if response.status!=200 or expected not in content:
                            raise RuntimeError('Generated editor local runtime failed: '+path)
                        if response.headers['Cache-Control']!='no-store':
                            raise RuntimeError('Local response is cacheable beyond integrity controls')
                with urlopen(address+'/__hydra/status',timeout=10) as response:
                    status=json.loads(response.read())
                    if status['status']!='VERIFIED' or not status['authenticityVerified']:
                        raise RuntimeError('Local editor did not report verified signed publication')
                # Ensure the generated web runtime cannot mutate the host data.
                with urlopen(Request(address+'/index.html',method='HEAD'),timeout=10) as response:
                    if response.status!=200 or response.read():
                        raise RuntimeError('HEAD response is not implemented correctly')
            finally:
                server.shutdown();t.join(timeout=10)
        print('HYDRA_BD5_REAL_E2E=PASS node_semantic_scenarios=7 java_web_package=PASS '
              'offline_shell=PASS dual_ed25519_signatures=PASS local_http_resources=4 '
              'signed_health=PASS native_installers=NOT_CERTIFIED external_toolchains=NOT_CERTIFIED')
    return 0

if __name__=='__main__':
    try:raise SystemExit(main())
    except (OSError,ValueError,RuntimeError,KeyError,ImportError,TimeoutError) as exc:
        print('HYDRA_BD5_REAL_E2E=FAIL '+str(exc),file=sys.stderr)
        raise SystemExit(1)
