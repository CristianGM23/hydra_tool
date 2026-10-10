#!/usr/bin/env python3
"""B4+D4 signed E2E: actual generated-editor runtime -> Java ZIP -> Ed25519 registry.

Keeps signing keys solely in a disposable temporary workspace. No OS bundle or
third-party technology is declared certified by this reference-editor scenario.
"""
from __future__ import annotations

import base64
import json
from pathlib import Path
import shutil
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[2]
sys.path[:0] = [str(ROOT/'scripts/validation'), str(ROOT/'scripts/distribution')]
from bd3_real_e2e import must_run, JAVA, JAVA_SOURCES
from run_native_parity_suite import certify
from build_generated_editor import precache
from verify_web_package import verify
from editor_release_registry import promote, inspect
from editor_release_trust import claim, sign_detached


def main() -> int:
    from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey
    from cryptography.hazmat.primitives import serialization
    if any(not shutil.which(x) for x in ('node','npm','javac','java')):
        raise RuntimeError('Node/npm and Java 21+ are required for genuine B4 E2E execution')
    with tempfile.TemporaryDirectory(prefix='hydra-bd4-real-') as location:
        temp=Path(location)
        editor=temp/'generated-editor'
        shutil.copytree(ROOT/'reference-projects/bd2-standalone-smoke', editor,
                        ignore=shutil.ignore_patterns('node_modules','dist','target'))
        result=certify('generated-web',editor,'tests/hydra-parity/scenarios.json',180,True)
        if result['status']!='PASSED' or len(result['scenarios']) != 7:
            raise RuntimeError('Generated editor did not produce seven genuine parity proofs')
        precache(editor/'dist','1.0.0','HYDRA Editor Fixture')
        classes=temp/'classes';classes.mkdir()
        must_run(['javac','--release','21','-d',str(classes),
                 *[str(JAVA/f) for f in JAVA_SOURCES],
                 str(ROOT/'scripts/testing/bd-block2/BD2RealBundleHarness.java')],cwd=ROOT)
        package=temp/'generated-editor.zip'
        must_run(['java','-cp',str(classes),'BD2RealBundleHarness',str(editor),str(package)],cwd=ROOT)
        verified=verify(package)
        product='hydra-editor-fixture';channel='stable';version=verified['version']
        binding=claim(product,channel,version,verified['packageSHA256'])
        keys=[];policy_keys=[]
        for index in range(2):
            private=Ed25519PrivateKey.generate()
            file=temp/f'key-{index}.pem'
            file.write_bytes(private.private_bytes(serialization.Encoding.PEM,
                serialization.PrivateFormat.PKCS8,serialization.NoEncryption()))
            public=private.public_key().public_bytes(serialization.Encoding.Raw,serialization.PublicFormat.Raw)
            keys.append(file)
            policy_keys.append({'id':f'fixture-{index}', 'publicKey':base64.b64encode(public).decode('ascii')})
        policy=temp/'trusted.json';policy.write_text(json.dumps({'schemaVersion':1,'algorithm':'Ed25519',
            'channels':['stable'],'minimumSignatures':2,'trustedKeys':policy_keys}))
        signatures=None
        for index,file in enumerate(keys):
            signatures=sign_detached(file,f'fixture-{index}',binding,signatures)
        detached=temp/'release-signature.json';detached.write_text(json.dumps(signatures))
        promoted=promote(temp/'registry',product,channel,package,detached,policy)
        published=inspect(temp/'registry',product,channel,policy)
        if not promoted['authenticityVerified'] or not published['authenticityVerified']:
            raise RuntimeError('Authenticity must be verified against two pinned keys')
        if (temp/'registry').joinpath('products',product,'releases',version,'editor.zip').read_bytes()!=package.read_bytes():
            raise RuntimeError('Published source ZIP bytes were mutated')
        print('BD4_REAL_E2E=PASS native_web=NODE seven_parity_scenarios=7 '
              'offline=PASS java_web_zip=PASS trusted_signatures=2 immutable_publish=PASS')
    return 0


if __name__=='__main__':
    try:
        raise SystemExit(main())
    except (OSError, ValueError, RuntimeError, KeyError, ImportError) as err:
        print('BD4_REAL_E2E=FAIL ' + str(err),file=sys.stderr)
        raise SystemExit(1)
