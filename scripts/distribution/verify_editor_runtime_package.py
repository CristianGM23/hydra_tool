#!/usr/bin/env python3
"""D5 preflight: verify launchable relative web assets in a HYDRA editor ZIP.

This static check complements (does not replace) actual browser tests, runtime
verification, Ed25519 trust policy or OS installer qualification.
"""
from __future__ import annotations

import argparse
from html.parser import HTMLParser
import json
from pathlib import PurePosixPath, Path
import sys
from urllib.parse import unquote, urlsplit
from zipfile import ZipFile

from verify_web_package import verify as verify_archive


class References(HTMLParser):
    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.refs=[]
        self.scripts=0
    def handle_starttag(self,tag,attrs):
        attributes=dict(attrs)
        if tag=='script':
            self.scripts+=1
            if attributes.get('src'):
                self.refs.append(('script',attributes['src']))
        if tag=='link' and attributes.get('rel','').lower() in ('stylesheet','manifest','modulepreload','preload','icon'):
            if attributes.get('href'):
                self.refs.append(('link',attributes['href']))


def resolve_reference(reference: str) -> str:
    if len(reference)>2048 or '\\' in reference or '%' in reference and any(
            code in reference.lower() for code in ('%2f','%5c','%25','%00')):
        raise ValueError('Unsafe compiled asset reference')
    url=urlsplit(reference)
    if url.scheme or url.netloc or reference.startswith('//'):
        raise ValueError('Remote executable/asset referenced by generated editor')
    raw=unquote(url.path,encoding='utf-8',errors='strict')
    if not raw:
        raise ValueError('Empty compiled asset URL')
    value=raw.lstrip('/')
    if value.startswith('./'):
        value=value[2:]
    parts=value.split('/')
    if any(not x or x in ('.','..') or x.startswith('.') or ':' in x or '\\' in x for x in parts):
        raise ValueError('Unsafe compiled asset reference')
    return '/'.join(parts)


def verify(package: Path) -> dict:
    integrity=verify_archive(package)
    with ZipFile(package) as archive:
        names=set(archive.namelist())
        parser=References()
        raw=archive.read('index.html')
        if len(raw)>2*1024*1024:
            raise ValueError('Oversized HTML entry point')
        parser.feed(raw.decode('utf-8'))
        if parser.scripts == 0:
            raise ValueError('Editor has no executable application script')
        resolved=[]
        for kind,reference in parser.refs:
            target=resolve_reference(reference)
            if target not in names:
                raise ValueError('Missing compiled reference: '+target)
            resolved.append(target)
        if not any(ref.endswith(('.js','.mjs')) for ref in resolved):
            raise ValueError('Compiled JavaScript entry not referenced in HTML')
        metadata=json.loads(archive.read('hydra-deployment.json'))
        # A manifest may be absent for a plain web editor; a declared offline
        # editor must actually package a manifest and cacheable service worker.
        offline=metadata.get('offlineEnabled',False)
        if type(offline) is not bool:
            raise ValueError('Invalid offline capability declaration')
        if offline:
            if 'manifest.webmanifest' not in names or 'service-worker.js' not in names:
                raise ValueError('Offline editor lacks manifest or service worker')
            worker=archive.read('service-worker.js').decode('utf-8')
            if not all(item in worker for item in ('caches.open','SHELL','fetch')):
                raise ValueError('Offline service worker is incomplete')
        if 'manifest.webmanifest' in names:
            webmanifest=json.loads(archive.read('manifest.webmanifest'))
            if not isinstance(webmanifest,dict) or not webmanifest.get('name'):
                raise ValueError('Invalid PWA webmanifest')
    return {'status':'PASSED','packageSHA256':integrity['packageSHA256'],
            'version':integrity['version'],'compiledReferences':sorted(set(resolved)),
            'offlineShellPackaged':offline and 'service-worker.js' in names,
            'nativeInstallerCertified':False,
            'validationScope':'STATIC_WEB_REFERENCE_AND_DEPLOYMENT_PREVIEW_ONLY'}


def main() -> int:
    ap=argparse.ArgumentParser(description=__doc__)
    ap.add_argument('package',type=Path)
    args=ap.parse_args()
    try:
        print(json.dumps(verify(args.package),sort_keys=True,indent=2))
        return 0
    except (ValueError,KeyError,OSError,UnicodeError,TypeError,LookupError) as exc:
        print('BD5_WEB_PREFLIGHT=FAIL '+str(exc),file=sys.stderr)
        return 1

if __name__=='__main__':
    raise SystemExit(main())
