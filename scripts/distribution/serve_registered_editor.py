#!/usr/bin/env python3
"""D5: secure localhost runtime for a previously published HYDRA web editor.

Serves only immutable, individually SHA-256-verified generated-editor assets.
Never serves the HYDRA authoring UI, secrets, arbitrary directories or APIs.
This is a LOCAL standalone preview/runtime, not a signed OS installer or remote CDN.
"""
from __future__ import annotations

import argparse
import hashlib
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
import mimetypes
import os
from pathlib import Path
import re
from urllib.parse import unquote, urlsplit
from zipfile import ZipFile
import sys

from editor_release_registry import inspect, product_path

MAX_ASSET = 64 * 1024 * 1024
MAX_URL = 2048
CSP = "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; connect-src 'self'; object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'"


def runtime(root: Path, product: str, channel: str, trust_policy: Path | None = None) -> dict:
    registered = inspect(root, product, channel, trust_policy)
    if registered['status'] != 'VERIFIED':
        raise ValueError('No integrity-verified release in selected channel')
    base = product_path(root, product) / 'releases' / registered['version']
    package = base / 'editor.zip'
    with ZipFile(package) as archive:
        expected = {}
        for line in archive.read('hydra-release.sha256').decode('utf-8').splitlines():
            sha, name = line.split('  ', 1)
            expected[name] = sha
        for name in ('hydra-release.sha256', 'hydra-deployment.json'):
            expected[name] = hashlib.sha256(archive.read(name)).hexdigest()
    if set(expected) != {path.relative_to(base / 'www').as_posix() for path in (base/'www').rglob('*') if path.is_file()}:
        raise ValueError('Published web root differs from immutable release archive')
    return {'webRoot':base/'www', 'version':registered['version'], 'product':product, 'channel':channel,
            'sha256':registered['integrity']['sha256'], 'authenticityVerified':registered['authenticityVerified'],
            'files':expected}


def request_file(url: str, accept: str, available: dict[str,str]) -> str | None:
    if not isinstance(url, str) or len(url) > MAX_URL or urlsplit(url).scheme or urlsplit(url).netloc:
        raise ValueError('Invalid local request target')
    original=urlsplit(url).path
    if not original.startswith('/') or re.search(r'%(?:2f|5c|25|00)',original,re.I):
        raise ValueError('Unsafe URL encoding')
    path=unquote(original, encoding='utf-8', errors='strict')
    if any(x in path for x in ('\\','\x00','%','?','#',':')):
        raise ValueError('Unsafe encoded local asset path')
    if path == '/':
        return 'index.html'
    segments=path[1:].split('/')
    if any(not x or x.startswith('.') or x in ('..','.') or any(ord(c)<32 for c in x) for x in segments):
        raise ValueError('Unsafe asset name')
    relative='/'.join(segments)
    if relative in available and relative not in ('hydra-release.sha256','hydra-deployment.json'):
        return relative
    # SPA navigation fallback, never for missing scripts, styles, metadata or API paths.
    if 'text/html' in accept and '.' not in segments[-1] and segments[0] not in ('api','__hydra'):
        return 'index.html'
    return None


def load_checked(state: dict, relative: str) -> bytes:
    if relative not in state['files']:
        raise ValueError('Unlisted web asset')
    root = state['webRoot']
    current = root
    for segment in relative.split('/'):
        current = current / segment
        if current.is_symlink():
            raise ValueError('Web asset contains symlink')
    if not current.is_file() or current.stat().st_size > MAX_ASSET:
        raise ValueError('Web asset missing or oversized')
    # os.O_NOFOLLOW closes the final-component symlink race on POSIX.
    flags = os.O_RDONLY | getattr(os, 'O_NOFOLLOW', 0)
    fd=os.open(current,flags)
    try:
        with os.fdopen(fd,'rb') as stream:
            data=stream.read(MAX_ASSET+1)
    except BaseException:
        raise
    if len(data)>MAX_ASSET or hashlib.sha256(data).hexdigest()!=state['files'][relative]:
        raise ValueError('Immutable web asset changed on disk')
    return data


def make_handler(state: dict):
    class EditorHandler(BaseHTTPRequestHandler):
        server_version='HYDRA-Generated-Editor'
        sys_version=''

        def log_message(self, fmt, *args):
            # Do not leak path, cookies, host or query values to stdout.
            pass

        def do_POST(self):
            self.send_error(HTTPStatus.METHOD_NOT_ALLOWED)

        def do_GET(self):
            self.respond(head=False)

        def do_HEAD(self):
            self.respond(head=True)

        def respond(self, head: bool):
            port=self.server.server_address[1]
            if self.headers.get('Host','').lower() not in ('127.0.0.1:'+str(port),'localhost:'+str(port)):
                self.send_error(HTTPStatus.BAD_REQUEST, 'Invalid local Host header')
                return
            if self.path.startswith('/__hydra/status'):
                if urlsplit(self.path).path != '/__hydra/status':
                    self.send_error(HTTPStatus.NOT_FOUND)
                    return
                body=json.dumps({'schemaVersion':1,'status':'VERIFIED','product':state['product'],
                                 'channel':state['channel'],'version':state['version'],
                                 'packageSHA256':state['sha256'],
                                 'authenticityVerified':state['authenticityVerified'],
                                 'nativeInstallerCertified':False},sort_keys=True).encode('utf-8')
                self.send_payload(body,'application/json; charset=utf-8',head)
                return
            try:
                relative=request_file(self.path,self.headers.get('Accept',''),state['files'])
                if relative is None:
                    self.send_error(HTTPStatus.NOT_FOUND)
                    return
                body=load_checked(state,relative)
            except (ValueError,OSError,UnicodeError):
                self.send_error(HTTPStatus.NOT_FOUND)
                return
            typ=mimetypes.guess_type(relative)[0] or 'application/octet-stream'
            if relative.endswith('.js') or relative.endswith('.mjs'):
                typ='text/javascript'
            if relative.endswith(('.json','.webmanifest')):
                typ='application/json'
            if typ.startswith('text/') or typ=='application/json':
                typ+='; charset=utf-8'
            self.send_payload(body,typ,head)

        def send_payload(self, content: bytes, mime: str, head: bool):
            self.send_response(HTTPStatus.OK)
            self.send_header('Content-Type',mime)
            self.send_header('Content-Length',str(len(content)))
            self.send_header('Cache-Control','no-store')
            self.send_header('Content-Security-Policy',CSP)
            self.send_header('X-Content-Type-Options','nosniff')
            self.send_header('X-Frame-Options','DENY')
            self.send_header('Referrer-Policy','no-referrer')
            self.send_header('Cross-Origin-Resource-Policy','same-origin')
            self.send_header('Cross-Origin-Opener-Policy','same-origin')
            self.end_headers()
            if not head:
                self.wfile.write(content)

    return EditorHandler


class LocalServer(ThreadingHTTPServer):
    daemon_threads=True
    request_queue_size=16


def open_server(state: dict, port: int = 0) -> LocalServer:
    if type(port) is not int or not 0 <= port <= 65535:
        raise ValueError('Invalid local port')
    return LocalServer(('127.0.0.1',port),make_handler(state))


def main() -> int:
    ap=argparse.ArgumentParser(description=__doc__)
    ap.add_argument('--registry',type=Path,required=True)
    ap.add_argument('--product',required=True)
    ap.add_argument('--channel',choices=('stable','beta','nightly'),default='stable')
    ap.add_argument('--trust-policy',type=Path)
    ap.add_argument('--port',type=int,default=0)
    args=ap.parse_args()
    try:
        state=runtime(args.registry,args.product,args.channel,args.trust_policy)
        with open_server(state,args.port) as server:
            print('HYDRA_STANDALONE_LOCAL=READY http://127.0.0.1:'+str(server.server_address[1]) + '/'
                  ' version='+state['version'],flush=True)
            server.serve_forever(poll_interval=.25)
        return 0
    except KeyboardInterrupt:
        return 0
    except (OSError,ValueError,KeyError,UnicodeError) as err:
        print('HYDRA_STANDALONE_LOCAL=FAIL '+str(err),file=sys.stderr)
        return 2

if __name__=='__main__':
    raise SystemExit(main())
