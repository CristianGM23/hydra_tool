#!/usr/bin/env python3
"""Real Fix179 journey: Java + Node semantic parity, Java ZIP, signed static install/upgrade/rollback.

The scope is two independent *reference runtimes* and deployable static web.
Not a certification of EMF/Sirius/Xtext/GLSP or OS-native installers.
"""
from __future__ import annotations
import base64
import hashlib
from http.server import ThreadingHTTPServer, SimpleHTTPRequestHandler
import json
from pathlib import Path
import shutil
import sys
import tempfile
import threading
from urllib.request import urlopen
from zipfile import ZipFile, ZIP_DEFLATED

ROOT=Path(__file__).resolve().parents[2]
sys.path[:0]=[str(ROOT/'scripts/validation'),str(ROOT/'scripts/distribution')]
from bd3_real_e2e import must_run, JAVA, JAVA_SOURCES
from run_native_parity_matrix import load_matrix, run_matrix
from run_native_parity_portfolio import assess, read_policy
from build_generated_editor import precache
from verify_editor_runtime_package import verify as preflight
from editor_release_registry import promote
from editor_release_trust import sign_detached, claim
from install_standalone_web import install, inspect_install, rollback


def extend_archive(original: Path, later: Path) -> None:
    # Write a second actual distribution with changed JS, regenerated offline
    # version marker and new independent per-file hashes.
    with ZipFile(original) as source:
        members={info.filename:source.read(info.filename) for info in source.infolist()}
    members.pop('hydra-release.sha256');meta=json.loads(members.pop('hydra-deployment.json'))
    meta['version']='1.1.0'
    members['assets/app.js']+=b'\nwindow.HYDRA_UPGRADE="1.1.0";\n'
    worker=members.get('service-worker.js')
    if worker is not None:
        members['service-worker.js']=worker.replace(b'1.0.0',b'1.1.0')
    checksum=''.join(hashlib.sha256(body).hexdigest()+'  '+path+'\n' for path,body in sorted(members.items()))
    members['hydra-release.sha256']=checksum.encode()
    members['hydra-deployment.json']=json.dumps(meta,sort_keys=True).encode()
    with ZipFile(later,'w',ZIP_DEFLATED,compresslevel=9) as package:
        for path,body in sorted(members.items()):package.writestr(path,body)


def main() -> int:
    from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey
    from cryptography.hazmat.primitives import serialization
    if any(shutil.which(tool) is None for tool in ('node','npm','java','javac')):
        raise ValueError('Node/npm and JDK 21+ required for real Fix179 E2E')
    with tempfile.TemporaryDirectory(prefix='hydra-bd67-e2e-') as tmp:
        folder=Path(tmp); generated=folder/'generated';generated.mkdir()
        for name in ('bd2-standalone-smoke','bd6-java-runtime'):
            shutil.copytree(ROOT/'reference-projects'/name,generated/name,
                ignore=shutil.ignore_patterns('node_modules','dist','target','*.class'))
        matrix=load_matrix(ROOT/'scripts/validation/bd6_native_matrix.json')
        proof=run_matrix(generated,matrix,180,True)
        verdict=assess(read_policy(ROOT/'scripts/validation/bd6_native_policy.json'),proof,executed=True)
        if verdict['status']!='CERTIFIED':
            raise ValueError('Real Java/Node cross-runtime behavior diverged')
        editor=generated/'bd2-standalone-smoke'
        precache(editor/'dist','1.0.0','HYDRA Generated Editor Fixture')
        classes=folder/'classes';classes.mkdir()
        must_run(['javac','--release','21','-d',str(classes),
            *[str(JAVA/path) for path in JAVA_SOURCES],
            str(ROOT/'scripts/testing/bd-block2/BD2RealBundleHarness.java')],cwd=ROOT)
        package=folder/'web-1.0.0.zip'
        must_run(['java','-cp',str(classes),'BD2RealBundleHarness',str(editor),str(package)],cwd=ROOT)
        result=preflight(package)
        if not result['offlineShellPackaged']:
            raise ValueError('Generated web artifact is not a complete offline PWA')
        later=folder/'web-1.1.0.zip';extend_archive(package,later)
        if preflight(later)['version']!='1.1.0':
            raise ValueError('Versioned update web archive invalid')
        product='hydra-generated-editor';channel='stable'
        keys=[];trust=[]
        for i in range(2):
            sk=Ed25519PrivateKey.generate()
            private=folder/f'key-{i}.pem'
            private.write_bytes(sk.private_bytes(serialization.Encoding.PEM,
                serialization.PrivateFormat.PKCS8,serialization.NoEncryption()))
            keys.append(private)
            trust.append({'id':f'signer-{i}','publicKey':base64.b64encode(sk.public_key().public_bytes(
                serialization.Encoding.Raw,serialization.PublicFormat.Raw)).decode()})
        policy=folder/'trust.json'
        policy.write_text(json.dumps({'schemaVersion':1,'algorithm':'Ed25519','channels':[channel],
                                      'minimumSignatures':2,'trustedKeys':trust}))
        registry=folder/'registry';installation=folder/'installed'
        def publish(version: str, archive: Path) -> dict:
            digest=hashlib.sha256(archive.read_bytes()).hexdigest()
            signature=None
            for i,key in enumerate(keys):
                signature=sign_detached(key,f'signer-{i}',claim(product,channel,version,digest),signature)
            signfile=folder/f'signature-{version}.json';signfile.write_text(json.dumps(signature))
            result=promote(registry,product,channel,archive,signfile,policy)
            if not result['authenticityVerified']:raise ValueError('Signature trust threshold not met')
            return install(registry,installation,product,channel,policy)
        installed=publish('1.0.0',package)
        original=installed['sha256']
        project=installation/'user-data'/'my-model.json';project.parent.mkdir(parents=True)
        project.write_text('{"id":"persisted-project"}')
        upgraded=publish('1.1.0',later)
        if upgraded['sha256']==original or upgraded['version']!='1.1.0':
            raise ValueError('Real application update did not switch versions')
        # Exercise independently installed static assets over actual HTTP.
        class QuietHandler(SimpleHTTPRequestHandler):
            def __init__(self,*args,**kwargs):super().__init__(*args,directory=upgraded['webRoot'],**kwargs)
            def log_message(self,*args):pass
        with ThreadingHTTPServer(('127.0.0.1',0),QuietHandler) as server:
            task=threading.Thread(target=server.serve_forever,daemon=True);task.start()
            try:
                uri='http://127.0.0.1:'+str(server.server_address[1])
                for asset,marker in [('/index.html',b'<html'),('/assets/app.js',b'HYDRA_UPGRADE'),
                                      ('/service-worker.js',b'caches.open')]:
                    with urlopen(uri+asset,timeout=8) as response:
                        if response.status!=200 or marker not in response.read():
                            raise ValueError('Installed generated editor HTTP asset invalid: '+asset)
            finally:
                server.shutdown();task.join(8)
        previous=rollback(installation,product,channel,policy)
        if previous['sha256']!=original or previous['version']!='1.0.0':
            raise ValueError('Rollback did not restore verified prior version')
        if project.read_text()!='{"id":"persisted-project"}':
            raise ValueError('Standalone update/rollback modified user project data')
        if inspect_install(installation,product,channel,policy)['version']!='1.0.0':
            raise ValueError('Restored installation did not independently verify')
        print('HYDRA_BD67_REAL_E2E=PASS independent_runtimes=java21,node22 '
              'semantic_scenarios=7 java_web_zip=PASS web_offline=PASS '
              'trusted_signers=2 install=PASS upgrade=PASS http=PASS rollback=PASS user_data=UNCHANGED '
              'emf_sirius_xtext_glsp=NOT_CERTIFIED os_native_installers=NOT_CERTIFIED')
    return 0

if __name__=='__main__':
    try:raise SystemExit(main())
    except (OSError,ValueError,KeyError,RuntimeError,ImportError,TimeoutError) as error:
        print('HYDRA_BD67_REAL_E2E=FAIL '+str(error),file=sys.stderr)
        raise SystemExit(1)
