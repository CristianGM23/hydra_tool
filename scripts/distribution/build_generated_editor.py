#!/usr/bin/env python3
"""B+D macroblock 2: opt-in native builds and verifiable offline web assets.

HYDRA remains a web authoring app. This utility operates on a separately
GENERATED editor; it is never invoked on untrusted projects by a REST endpoint.
Without --execute-project-build it cannot run npm/Cargo or install dependencies.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile

REPOSITORY = Path(__file__).resolve().parents[2]
DEFAULT_ALLOWED = REPOSITORY / 'data' / 'standalone-editors'
MAX_FILES = 2048
MAX_FILE_BYTES = 64 * 1024 * 1024
MAX_TOTAL_BYTES = 192 * 1024 * 1024


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def checked_root(allowed: Path, relative: str) -> Path:
    if not relative or '\\' in relative or re.match(r'^[A-Za-z]:', relative):
        raise ValueError('Editor directory must be a portable relative path')
    path = Path(relative)
    if path.is_absolute() or any(p in ('', '.', '..') for p in path.parts):
        raise ValueError('Editor directory escapes allowed root')
    allowed = allowed.resolve()
    target = allowed.joinpath(*path.parts)
    current = allowed
    for part in path.parts:
        current = current / part
        if current.is_symlink():
            raise ValueError('Symbolic editor directory segment is forbidden')
    if not target.is_dir():
        raise ValueError('Generated editor does not exist: ' + str(target))
    return target


def inventory(dist: Path) -> dict[str, str]:
    if not dist.is_dir() or dist.is_symlink():
        raise ValueError('Compiled dist directory missing')
    found: dict[str, str] = {}
    total = 0
    for path in sorted(dist.rglob('*')):
        if path.is_symlink():
            raise ValueError('Distribution cannot contain symbolic links: ' + str(path))
        if not path.is_file():
            continue
        name = path.relative_to(dist).as_posix()
        base = path.name.lower()
        if base.startswith('.env') or base.endswith(('.map', '.key', '.pem', '.p12', '.pfx')) or base in ('credentials.json', 'hydra-release.sha256', 'hydra-deployment.json'):
            raise ValueError('Sensitive/reserved distribution file: ' + name)
        if len(found) >= MAX_FILES or path.stat().st_size > MAX_FILE_BYTES:
            raise ValueError('Distribution count or file size exceeded')
        data = path.read_bytes()
        total += len(data)
        if total > MAX_TOTAL_BYTES:
            raise ValueError('Distribution size exceeded')
        found[name] = sha256(data)
    if 'index.html' not in found:
        raise ValueError('Compiled entry dist/index.html missing')
    return found


def precache(dist: Path, version: str, product_name: str = "Generated editor") -> tuple[str, list[str]]:
    paths = inventory(dist)
    if 'manifest.webmanifest' not in paths:
        raise ValueError('PWA manifest must be present in compiled dist')
    assets = sorted(name for name in paths if name != 'service-worker.js')
    fingerprint = sha256(('\n'.join(name + ':' + paths[name] for name in assets)).encode())[:20]
    prefix = 'hydra-editor-' + sha256(product_name.encode('utf-8'))[:12] + '-'
    cache = re.sub(r'[^a-z0-9._-]+', '-', version.lower()) + '-' + fingerprint
    # Only same-origin GET, no API caching, no cross-origin behavior.
    worker = f'''/* HYDRA generated offline shell; content-addressed and versioned. */
const CACHE_PREFIX = {json.dumps(prefix)} + encodeURIComponent(self.registration.scope) + '-';
const CACHE_NAME = CACHE_PREFIX + {json.dumps(cache)};
const SHELL = {json.dumps(['./' + name for name in assets], ensure_ascii=True)};
self.addEventListener('install', event => event.waitUntil(
  caches.open(CACHE_NAME).then(cache => cache.addAll(SHELL)).then(() => self.skipWaiting())
));
self.addEventListener('activate', event => event.waitUntil(
  caches.keys().then(names => Promise.all(names.filter(name => name.startsWith(CACHE_PREFIX) && name !== CACHE_NAME)
    .map(name => caches.delete(name)))).then(() => self.clients.claim())
));
self.addEventListener('fetch', event => {{
  const req = event.request;
  const url = new URL(req.url);
  if (req.method !== 'GET' || url.origin !== self.location.origin || url.pathname.startsWith('/api/')) return;
  if (req.mode === 'navigate') {{
    event.respondWith(fetch(req).catch(() => caches.match('./index.html')));
    return;
  }}
  event.respondWith(caches.match(req).then(hit => hit || fetch(req)));
}});
'''
    (dist / 'service-worker.js').write_text(worker, encoding='utf-8', newline='\n')
    return cache, assets


def bounded(command: list[str], cwd: Path, timeout: int) -> dict:
    if shutil.which(command[0]) is None:
        return {'status': 'TOOL_UNAVAILABLE', 'command': command[0]}
    try:
        result = subprocess.run(command, cwd=cwd, timeout=timeout, stdout=subprocess.PIPE,
                                stderr=subprocess.STDOUT, text=True, errors='replace', shell=False)
        return {'status': 'PASSED' if result.returncode == 0 else 'FAILED', 'exitCode': result.returncode,
                'outputTail': result.stdout[-6000:]}
    except subprocess.TimeoutExpired:
        return {'status': 'TIMED_OUT', 'timeoutSeconds': timeout}
    except OSError as error:
        return {'status': 'FAILED', 'error': str(error)}


def build(allowed: Path, relative: str, execute: bool, web: bool, native: bool, offline: bool,
          timeout: int, prepare_assets: bool) -> dict:
    editor = checked_root(allowed, relative)
    descriptor = editor / 'distribution' / 'hydra-distribution.json'
    if not descriptor.is_file():
        raise ValueError('Generated editor distribution descriptor is missing')
    metadata = json.loads(descriptor.read_text(encoding='utf-8'))
    version = metadata['version']
    steps: dict[str, dict] = {}
    if execute and not (web or native):
        raise ValueError('Select --web or --native to execute project builds')
    if web:
        if execute:
            if not (editor / 'package.json').is_file() or not (editor / 'package-lock.json').is_file():
                raise ValueError('Reproducible npm build requires package.json and package-lock.json')
            runner = 'npm.cmd' if os.name == 'nt' else 'npm'
            steps['npmCi'] = bounded([runner, 'ci', '--no-audit', '--no-fund'], editor, timeout)
            if steps['npmCi']['status'] != 'PASSED':
                return {'version': version, 'steps': steps, 'qualified': False}
            steps['webBuild'] = bounded([runner, 'run', 'build'], editor, timeout)
            if steps['webBuild']['status'] != 'PASSED':
                return {'version': version, 'steps': steps, 'qualified': False}
        else:
            steps['webBuild'] = {'status': 'NOT_EXECUTED'}
    if prepare_assets:
        if not web:
            raise ValueError('--prepare-offline-shell requires --web')
        if not offline:
            raise ValueError('Offline shell generation requires the offline flag')
        public = editor / 'public'
        dist = editor / 'dist'
        if not dist.is_dir() or dist.is_symlink():
            raise ValueError('Cannot prepare offline shell without built assets')
        for name in ('manifest.webmanifest',):
            target = dist / name
            if not target.is_file():
                source = public / name
                if not source.is_file() or source.is_symlink():
                    raise ValueError('Required PWA descriptor missing: ' + name)
                shutil.copyfile(source, target)
        cache, assets = precache(dist, version, metadata.get('productName', 'Generated editor'))
        steps['offlineShell'] = {'status': 'GENERATED', 'cache': cache, 'precacheEntries': len(assets)}
    if web:
        try:
            files = inventory(editor / 'dist')
            if offline and 'service-worker.js' not in files:
                raise ValueError('Offline PWA requires built service-worker.js')
            steps['webAssets'] = {'status': 'PASSED', 'fileCount': len(files),
                                  'sha256': sha256(json.dumps(files, sort_keys=True).encode())}
        except ValueError as error:
            steps['webAssets'] = {'status': 'FAILED', 'reason': str(error)}
    if native:
        if not (editor / 'src-tauri' / 'Cargo.toml').is_file():
            raise ValueError('Native packaging requires generated src-tauri/Cargo.toml')
        if execute:
            # Only explicitly invoked locally or on trusted CI artifacts; never REST triggered.
            steps['nativeBuild'] = bounded(['cargo', 'tauri', 'build'], editor / 'src-tauri', timeout)
            if steps['nativeBuild']['status'] == 'PASSED':
                bundles = editor / 'src-tauri' / 'target' / 'release' / 'bundle'
                found = sorted(path.relative_to(bundles).as_posix() for path in bundles.rglob('*')
                               if path.is_file() and not path.is_symlink()
                               and path.suffix.lower() in ('.msi', '.exe', '.deb', '.rpm', '.appimage', '.dmg')) if bundles.is_dir() else []
                # A macOS .app bundle is a directory; require its executable payload.
                apps = sorted(path.relative_to(bundles).as_posix() for path in bundles.rglob('*.app')
                              if path.is_dir() and (path / 'Contents' / 'MacOS').is_dir()) if bundles.is_dir() else []
                if not found and not apps:
                    steps['nativeBuild'] = {'status': 'FAILED', 'reason': 'No actual native bundle was produced'}
                else:
                    steps['nativeBuild']['bundlePaths'] = (found + apps)[:24]
        else:
            steps['nativeBuild'] = {'status': 'NOT_EXECUTED'}
    qualified = bool(steps) and all(entry['status'] == 'PASSED' for key, entry in steps.items()
                                    if key not in ('offlineShell',)) and (not native or steps['nativeBuild']['status'] == 'PASSED')
    return {'schemaVersion': '1.0', 'version': version, 'steps': steps, 'qualified': qualified,
            'scope': 'WEB_BUILD_AND_NATIVE_COMPILATION_ONLY', 'nativeInstallerSigned': False}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--allowed-root', type=Path, default=DEFAULT_ALLOWED)
    parser.add_argument('--editor', required=True, help='Relative path of generated editor')
    parser.add_argument('--web', action='store_true')
    parser.add_argument('--native', action='store_true')
    parser.add_argument('--offline', action='store_true')
    parser.add_argument('--prepare-offline-shell', action='store_true')
    parser.add_argument('--execute-project-build', action='store_true', help='Explicit opt-in: executes npm or Cargo scripts; use only trusted generated projects')
    parser.add_argument('--timeout-seconds', type=int, default=1200)
    parser.add_argument('--report', type=Path)
    args = parser.parse_args()
    if not 1 <= args.timeout_seconds <= 3600:
        parser.error('timeout must be between 1 and 3600 seconds')
    try:
        result = build(args.allowed_root, args.editor, args.execute_project_build, args.web,
                       args.native, args.offline, args.timeout_seconds, args.prepare_offline_shell)
    except (ValueError, OSError, KeyError, json.JSONDecodeError) as error:
        print('BD2_BUILD=FAIL', str(error), file=sys.stderr)
        return 2
    print(json.dumps(result, indent=2, sort_keys=True))
    if args.report:
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(json.dumps(result, indent=2, sort_keys=True) + '\n', encoding='utf-8')
    return 0 if result['qualified'] else 1


if __name__ == '__main__':
    raise SystemExit(main())
