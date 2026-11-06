#!/usr/bin/env python3
"""B6/B7/D6/D7: independent, verified installation of generated web editors.

This command operates on a locally pinned HYDRA signed-release registry; it
copies only verified built web assets to a self-contained, versioned static
site. It never installs system packages, invokes shells or modifies user data.
A static web host can serve the returned webRoot without running HYDRA.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import sys
import time
import uuid
from zipfile import ZipFile

from editor_release_registry import (
    normalized_root, product_path, channel_path, active, inspect, verify_release,
    version_tuple, locked, atomic_json, sha256, read_json,
)
from editor_release_trust import verify_detached, read_document as trust_document, claim
from verify_web_package import verify as verify_package

RESERVED = {'hydra-release.sha256', 'hydra-deployment.json'}
_WINDOWS_SHARING_ERRORS = frozenset({5, 32, 33})


def commit_staged_version(temp: Path, target: Path, *, retries: int = 7) -> None:
    """Rename the verified staging directory to its immutable version directory.

    On Windows, antivirus or indexer handles on the freshly written files make a
    directory rename fail transiently (access denied / sharing violation). Those
    are retried a bounded number of times; any other error, a persistent one or
    an already existing target is raised and the caller removes the stage.
    """
    for attempt in range(retries + 1):
        try:
            temp.rename(target)
            return
        except OSError as exc:
            transient = os.name == 'nt' and getattr(exc, 'winerror', None) in _WINDOWS_SHARING_ERRORS
            if attempt == retries or not transient or target.exists():
                raise
            time.sleep(min(0.05 * (attempt + 1), 0.4))


def location(destination: Path, product: str, channel: str) -> Path:
    # Keep product and channel validation identical to the release registry.
    if channel not in ('stable', 'beta', 'nightly'):
        raise ValueError('Invalid distribution channel')
    from editor_release_registry import key
    root = normalized_root(destination)
    for segment in ('sites', key(product, 'Product'), channel):
        root = root / segment
        if root.is_symlink():
            raise ValueError('Installation traverses a symbolic link')
    return root


def verify_version(site: Path, version: str, product: str, channel: str, policy: Path | None) -> dict:
    version_tuple(version)
    folder = site / 'versions' / version
    if folder.is_symlink() or not folder.is_dir():
        raise ValueError('Installed immutable version absent or linked')
    record = read_json(folder / 'installation.json')
    package = folder / 'editor.zip'
    if package.is_symlink() or not package.is_file():
        raise ValueError('Installed package missing or linked')
    report = verify_package(package)
    if record.get('schemaVersion') != 1 or record.get('product') != product or record.get('channel') != channel \
       or record.get('version') != version or record.get('sha256') != report['packageSHA256']:
        raise ValueError('Installed release identity or content SHA mismatch')
    if record.get('signed') is True:
        if policy is None:
            raise ValueError('Pinned trust policy is required for signed installation')
        signature = folder / 'release-signature.json'
        if signature.is_symlink() or not signature.is_file():
            raise ValueError('Installed detached signature is missing')
        auth = verify_detached(trust_document(signature), trust_document(policy),
                               claim(product, channel, version, report['packageSHA256']))
        if not auth.get('authenticityVerified'):
            raise ValueError('Installed detached signature was not verified')
    elif record.get('signed') is False:
        if policy is not None or (folder / 'release-signature.json').exists():
            raise ValueError('Unsigned publication forbidden by trust policy')
    else:
        raise ValueError('Invalid installed trust state')
    web = folder / 'www'
    if web.is_symlink() or not web.is_dir():
        raise ValueError('Installed web root absent or linked')
    with ZipFile(package) as archive:
        # verify_package already validated every archive member against this
        # checksum file; use it without keeping up to 64 MiB per entry in RAM.
        expected = dict(line.split('  ', 1)[::-1] for line in
                        archive.read('hydra-release.sha256').decode('utf-8').splitlines())
    found = set()
    for file in web.rglob('*'):
        if file.is_symlink():
            raise ValueError('Installed site contains a symbolic link')
        if file.is_file():
            name = file.relative_to(web).as_posix()
            found.add(name)
            if name not in expected or sha256(file) != expected[name]:
                raise ValueError('Installed asset differs from verified package: ' + name)
    if found != set(expected):
        raise ValueError('Installed asset inventory has missing or extra files')
    return {'version': version, 'sha256': report['packageSHA256'],
            'files': len(found), 'signed': record['signed'], 'webRoot': str(web)}


def inspect_install(destination: Path, product: str, channel: str, policy: Path | None) -> dict:
    site = location(destination, product, channel)
    pointer = read_json(site / 'current.json')
    if pointer.get('schemaVersion') != 1 or pointer.get('product') != product or pointer.get('channel') != channel:
        raise ValueError('Installation pointer identity mismatch')
    checked = verify_version(site, pointer['version'], product, channel, policy)
    if checked['sha256'] != pointer.get('sha256'):
        raise ValueError('Active installation pointer digest mismatch')
    return {'status':'VERIFIED', 'product':product,'channel':channel,
            'version':pointer['version'],'history':pointer.get('history',[]), **checked,
            'userDataPreserved':True, 'nativeInstallerCertified':False}


def install(registry: Path, destination: Path, product: str, channel: str,
            policy: Path | None, *, allow_unsigned_dev: bool = False) -> dict:
    if policy is None and not allow_unsigned_dev:
        raise ValueError('Unsigned installs require explicit --allow-unsigned-dev')
    origin = inspect(registry, product, channel, policy)
    if origin['status'] != 'VERIFIED':
        raise ValueError('Selected registry release is not verified')
    version = origin['version']
    base = product_path(registry, product)
    source = base / 'releases' / version
    verified = verify_release(base, version, policy)
    if bool(verified['authenticityVerified']) != bool(policy):
        raise ValueError('Registry trust policy does not match published release')
    site = location(destination, product, channel)
    with locked(site):
        pointer_file = site / 'current.json'
        previous = read_json(pointer_file) if pointer_file.exists() else None
        if previous:
            inspect_install(destination, product, channel, policy)
            if version_tuple(version) <= version_tuple(previous['version']):
                raise ValueError('Only monotonically newer installations allowed; use rollback')
        versions = site / 'versions'
        if versions.is_symlink():
            raise ValueError('Installation versions root is a link')
        target = versions / version
        temp = versions / ('.stage-' + uuid.uuid4().hex)
        if target.exists() or target.is_symlink():
            checked = verify_version(site, version, product, channel, policy)
            if checked['sha256'] != verified['sha256']:
                raise ValueError('Conflicting immutable installation version')
        else:
            try:
                temp.mkdir(parents=True)
                shutil.copyfile(source / 'editor.zip', temp / 'editor.zip')
                if sha256(temp / 'editor.zip') != verified['sha256']:
                    raise ValueError('Transfer checksum mismatch')
                with ZipFile(temp / 'editor.zip') as archive:
                    www = temp / 'www'
                    www.mkdir()
                    # Verified archive paths are safe and portable. Do not serve
                    # internal checksum manifests or release metadata as web assets.
                    for item in archive.infolist():
                        if item.filename in RESERVED:
                            continue
                        path = www.joinpath(*item.filename.split('/'))
                        path.parent.mkdir(parents=True, exist_ok=True)
                        with archive.open(item) as src, path.open('xb') as dest:
                            shutil.copyfileobj(src, dest, length=1024*1024)
                if policy is not None:
                    shutil.copyfile(source / 'release-signature.json', temp / 'release-signature.json')
                atomic_json(temp / 'installation.json', {'schemaVersion':1,'product':product,
                    'channel':channel,'version':version,'sha256':verified['sha256'],'signed':bool(policy)})
                commit_staged_version(temp, target)
            finally:
                if temp.exists():
                    shutil.rmtree(temp)
        verify_version(site, version, product, channel, policy)
        history = list(previous.get('history',[])) if previous else []
        if previous:
            history.append(previous['version'])
        atomic_json(pointer_file, {'schemaVersion':1,'product':product,'channel':channel,
                  'version':version,'sha256':verified['sha256'],'history':history[-50:]})
    return inspect_install(destination, product, channel, policy)


def rollback(destination: Path, product: str, channel: str, policy: Path | None) -> dict:
    site = location(destination, product, channel)
    with locked(site):
        previous = read_json(site / 'current.json')
        inspect_install(destination, product, channel, policy)
        history = previous.get('history',[])
        if not isinstance(history, list) or not history:
            raise ValueError('No previous verified installation available')
        target = history[-1]
        verified = verify_version(site, target, product, channel, policy)
        atomic_json(site / 'current.json', {**previous,'version':target,
                                             'sha256':verified['sha256'],'history':history[:-1]})
    return inspect_install(destination, product, channel, policy)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action', choices=('install','verify','rollback'))
    parser.add_argument('--registry', type=Path)
    parser.add_argument('--destination', type=Path, required=True)
    parser.add_argument('--product', required=True)
    parser.add_argument('--channel', choices=('stable','beta','nightly'), default='stable')
    parser.add_argument('--trust-policy',type=Path)
    parser.add_argument('--allow-unsigned-dev',action='store_true')
    args = parser.parse_args()
    try:
        if args.action == 'install':
            if args.registry is None:
                parser.error('install requires --registry')
            result = install(args.registry,args.destination,args.product,args.channel,
                             args.trust_policy,allow_unsigned_dev=args.allow_unsigned_dev)
        elif args.action == 'rollback':
            result = rollback(args.destination,args.product,args.channel,args.trust_policy)
        else:
            result = inspect_install(args.destination,args.product,args.channel,args.trust_policy)
        print(json.dumps(result,indent=2,sort_keys=True))
        return 0
    except (ValueError,OSError,KeyError,TypeError,json.JSONDecodeError) as exc:
        print('BD7_STANDALONE_INSTALL=FAIL '+str(exc),file=sys.stderr)
        return 2


if __name__ == '__main__':
    raise SystemExit(main())
