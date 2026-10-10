#!/usr/bin/env python3
"""Immutable local channel registry for HYDRA-generated deployable web editors.

Promotes independently verified B2 web archives into versioned deployments.
Supports inspection, integrity verification, monotonic channel upgrades and
explicit rollback. It does not fetch remote files or claim artifact authenticity;
release signing and native installer attestation are separate concerns.
"""
from __future__ import annotations

import argparse
from contextlib import contextmanager
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import stat
import sys
import time
import uuid
from zipfile import ZipFile

from verify_web_package import verify as verify_package
from editor_release_trust import claim as release_claim, read_document as trust_document, verify_detached

SLUG = re.compile(r'[a-z][a-z0-9-]{0,47}\Z')
VERSION = re.compile(r'(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\Z')
CHANNELS = ('stable', 'beta', 'nightly')


def normalized_root(root: Path) -> Path:
    if root.is_symlink():
        raise ValueError('Registry root cannot be symbolic link')
    root = root.absolute()
    current = Path(root.anchor)
    for part in root.parts[1:]:
        current /= part
        if current.is_symlink():
            raise ValueError('Registry root traverses a symbolic link')
    return root


def key(value: str, what: str) -> str:
    if not isinstance(value, str) or not SLUG.fullmatch(value):
        raise ValueError(f'{what} must be a safe lowercase slug')
    return value


def version_tuple(value: str) -> tuple[int, int, int]:
    match = VERSION.fullmatch(str(value))
    if not match or len(value) > 48:
        raise ValueError('Release version must use major.minor.patch with no suffix')
    return tuple(map(int, match.groups()))


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open('rb') as file:
        while part := file.read(64 * 1024):
            h.update(part)
    return h.hexdigest()


def read_json(path: Path) -> dict:
    if path.is_symlink() or not path.is_file() or path.stat().st_size > 1024 * 1024:
        raise ValueError('Registry record missing, linked or too large: ' + str(path))
    result = json.loads(path.read_text(encoding='utf-8'))
    if not isinstance(result, dict):
        raise ValueError('Invalid registry record')
    return result


def atomic_json(path: Path, document: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name('.' + path.name + '.' + uuid.uuid4().hex + '.tmp')
    try:
        with temporary.open('x', encoding='utf-8', newline='\n') as out:
            json.dump(document, out, sort_keys=True, indent=2, ensure_ascii=False)
            out.write('\n')
            out.flush()
            os.fsync(out.fileno())
        os.replace(temporary, path)
    finally:
        temporary.unlink(missing_ok=True)


@contextmanager
def locked(product_root: Path):
    product_root.mkdir(parents=True, exist_ok=True)
    lock = product_root / '.hydra-promotion-lock'
    # Atomic mkdir, fail closed on concurrent promotions; never steal locks.
    lock.mkdir()
    try:
        yield
    finally:
        lock.rmdir()


def product_path(root: Path, product: str) -> Path:
    base = normalized_root(root)
    for part in ('products', key(product, 'Product')):
        base = base / part
        if base.is_symlink():
            raise ValueError('Registry product path crosses a symbolic link')
    return base


def channel_path(product_root: Path, channel: str) -> Path:
    if channel not in CHANNELS:
        raise ValueError('Unsupported publication channel')
    folder = product_root / 'channels'
    if folder.is_symlink():
        raise ValueError('Channel directory is a symbolic link')
    return folder / (channel + '.json')


def release_path(product_root: Path, version: str) -> Path:
    version_tuple(version)
    folder = product_root / 'releases'
    if folder.is_symlink():
        raise ValueError('Releases directory is a symbolic link')
    return folder / version


def active(product_root: Path, channel: str) -> dict | None:
    path = channel_path(product_root, channel)
    return read_json(path) if path.is_file() else None


def safe_extract(archive_path: Path, target: Path) -> None:
    target.mkdir(parents=True, exist_ok=False)
    with ZipFile(archive_path) as archive:
        for item in archive.infolist():
            # verify_package has already rejected unsafe member names,
            # symlinks, duplicates and excessive extracted sizes.
            dest = target.joinpath(*item.filename.split('/'))
            dest.parent.mkdir(parents=True, exist_ok=True)
            with archive.open(item) as inp, dest.open('xb') as out:
                shutil.copyfileobj(inp, out, 64 * 1024)
            dest.chmod(0o644)


def publish_staged_release(staging: Path, destination: Path) -> None:
    """Commit a prepared immutable release with bounded Windows sharing-lock retries.

    Windows scanners and indexers can briefly hold file/directory handles after
    extraction.  Only known Windows sharing/access errors are retried; existing
    destinations, symlinks, and persistent ACL errors still fail closed.  Never
    replace or remove an existing immutable published version.
    """
    for attempt in range(8):
        # Check before every attempt, including retries: immutable paths must
        # never be overwritten, even if another process created the version.
        if destination.exists() or destination.is_symlink():
            raise ValueError('Immutable release destination already exists: ' + destination.name)
        try:
            staging.rename(destination)
            return
        except OSError as error:
            # winerror is present on Python's genuine Windows OSErrors.
            if getattr(error, 'winerror', None) not in (5, 32, 33):
                raise
            if destination.exists() or destination.is_symlink():
                raise ValueError('Immutable release destination appeared during promotion') from error
            if attempt == 7:
                raise PermissionError(
                    'Windows could not publish an immutable release after bounded '
                    'retries; check antivirus/indexer locks or directory permissions'
                ) from error
            time.sleep(min(0.05 * (2 ** attempt), 1.0))


def verify_release(product_root: Path, version: str, trust_policy: Path | None = None) -> dict:
    folder = release_path(product_root, version)
    if folder.is_symlink() or not folder.is_dir():
        raise ValueError('Immutable release folder missing or linked')
    record = read_json(folder / 'release.json')
    package = folder / 'editor.zip'
    if package.is_symlink() or not package.is_file():
        raise ValueError('Release archive missing or linked')
    report = verify_package(package)
    if record.get('schemaVersion') != 1 or record.get('version') != version or record.get('sha256') != report['packageSHA256']:
        raise ValueError('Published release SHA/version differs from immutable manifest')
    if report['version'] != version:
        raise ValueError('Embedded editor version differs from published release')
    trusted = record.get('trustedPublication', False)
    if type(trusted) is not bool:
        raise ValueError('Invalid release trust marker')
    signature_path = folder / 'release-signature.json'
    if trusted:
        if trust_policy is None:
            raise ValueError('Signed publication requires an explicit trust policy')
        if not signature_path.is_file() or signature_path.is_symlink():
            raise ValueError('Immutable signed release lost its signature')
        if not isinstance(record.get('product'), str) or record.get('channel') not in CHANNELS:
            raise ValueError('Signed release identity is missing')
        authenticity = verify_detached(trust_document(signature_path), trust_document(trust_policy),
            release_claim(record['product'], record['channel'], version, report['packageSHA256']))
    else:
        if trust_policy is not None or signature_path.exists() or signature_path.is_symlink():
            raise ValueError('Signed trust mode forbids unsigned/ambiguous releases')
        authenticity = {'authenticityVerified': False}
    # Verify every deployed byte against the independently verified archive.
    www = folder / 'www'
    if www.is_symlink() or not www.is_dir():
        raise ValueError('Deployed web directory missing or linked')
    with ZipFile(package) as archive:
        expected = {entry.filename for entry in archive.infolist()}
        checksum_lines = archive.read('hydra-release.sha256').decode('utf-8').splitlines()
        checked = dict(line.split('  ', 1)[::-1] for line in checksum_lines)
        checked['hydra-release.sha256'] = hashlib.sha256(archive.read('hydra-release.sha256')).hexdigest()
        checked['hydra-deployment.json'] = hashlib.sha256(archive.read('hydra-deployment.json')).hexdigest()
        actual = set()
        for path in www.rglob('*'):
            if path.is_symlink():
                raise ValueError('Deployment contains a symlink')
            if not path.is_file():
                continue
            name = path.relative_to(www).as_posix()
            actual.add(name)
            if name not in checked or sha256(path) != checked[name]:
                raise ValueError('Deployed web asset differs from published archive: ' + name)
        if expected != actual:
            raise ValueError('Deployed web asset inventory differs from archive')
    return {'status': 'PASSED', 'version': version, 'sha256': record['sha256'], 'webFiles': len(actual),
            'nativeInstallerCertified': False, 'authenticityVerified': authenticity['authenticityVerified']}


def promote(root: Path, product: str, channel: str, package: Path,
            signature: Path | None = None, trust_policy: Path | None = None) -> dict:
    base = product_path(root, product)
    pointer = channel_path(base, channel)
    if not package.is_file() or package.is_symlink():
        raise ValueError('Local release archive missing or linked')
    report = verify_package(package)
    with ZipFile(package) as published_zip:
        embedded_channel = json.loads(published_zip.read('hydra-deployment.json')).get('updateChannel')
    if embedded_channel is not None and embedded_channel != channel:
        raise ValueError('Embedded release channel differs from selected promotion channel')
    version = report['version']
    version_tuple(version)
    if (signature is None) != (trust_policy is None):
        raise ValueError('Signed promotion requires both signature and pinned trust policy')
    if trust_policy is not None:
        verification = verify_detached(trust_document(signature), trust_document(trust_policy),
            release_claim(product, channel, version, report['packageSHA256']))
    else:
        verification = {'authenticityVerified': False}
    with locked(base):
        previous = active(base, channel)
        if previous is not None:
            prior_version = previous['version']
            verify_release(base, prior_version, trust_policy)
            if version_tuple(version) <= version_tuple(prior_version):
                raise ValueError('New channel release must have a higher semantic version; use rollback explicitly')
        folder = release_path(base, version)
        staging = folder.with_name('.' + version + '-' + uuid.uuid4().hex + '.tmp')
        if folder.exists() or folder.is_symlink():
            existing = verify_release(base, version, trust_policy)
            if existing['sha256'] != report['packageSHA256']:
                raise ValueError('Conflicting immutable release with same version')
        else:
            try:
                staging.mkdir(parents=True)
                shutil.copyfile(package, staging / 'editor.zip')
                if sha256(staging / 'editor.zip') != report['packageSHA256']:
                    raise ValueError('Copied package digest mismatch')
                safe_extract(staging / 'editor.zip', staging / 'www')
                if signature is not None:
                    if signature.is_symlink():
                        raise ValueError('Detached release signature cannot be symlinked')
                    shutil.copyfile(signature, staging / 'release-signature.json')
                    verify_detached(trust_document(staging / 'release-signature.json'), trust_document(trust_policy),
                        release_claim(product, channel, version, report['packageSHA256']))
                atomic_json(staging / 'release.json', {'schemaVersion': 1, 'version': version,
                    'sha256': report['packageSHA256'], 'format': report['level'],
                    'product': product, 'channel': channel,
                    'trustedPublication': verification['authenticityVerified'],
                    'nativeInstallerCertified': False, 'authenticityVerified': verification['authenticityVerified']})
                publish_staged_release(staging, folder)
            finally:
                if staging.exists():
                    shutil.rmtree(staging)
        verify_release(base, version, trust_policy)
        history = list(previous.get('history', [])) if previous else []
        if previous:
            history.append(previous['version'])
        record = {'schemaVersion': 1, 'product': product, 'channel': channel, 'version': version,
                  'sha256': report['packageSHA256'], 'history': history[-50:],
                  'lastAction': 'promote', 'updatedUtc': datetime.now(timezone.utc).isoformat()}
        atomic_json(pointer, record)
        return {'status': 'PROMOTED', 'product': product, 'channel': channel,
                'version': version, 'sha256': report['packageSHA256'], 'authenticityVerified': verification['authenticityVerified']}


def rollback(root: Path, product: str, channel: str, trust_policy: Path | None = None) -> dict:
    base = product_path(root, product)
    with locked(base):
        previous = active(base, channel)
        if not previous or not previous.get('history'):
            raise ValueError('No previous release recorded for rollback')
        verify_release(base, previous['version'], trust_policy)
        history = list(previous['history'])
        target = history.pop()
        release = verify_release(base, target, trust_policy)
        record = {**previous, 'version': target, 'sha256': release['sha256'], 'history': history,
                  'lastAction': 'rollback', 'updatedUtc': datetime.now(timezone.utc).isoformat()}
        atomic_json(channel_path(base, channel), record)
        return {'status': 'ROLLED_BACK', 'product': product, 'channel': channel, 'version': target,
                'sha256': release['sha256'], 'authenticityVerified': release['authenticityVerified']}


def inspect(root: Path, product: str, channel: str, trust_policy: Path | None = None) -> dict:
    base = product_path(root, product)
    pointer = active(base, channel)
    if pointer is None:
        return {'status': 'UNAVAILABLE', 'product': product, 'channel': channel}
    if pointer.get('schemaVersion') != 1 or pointer.get('product') != product or pointer.get('channel') != channel:
        raise ValueError('Channel record identity mismatch')
    evidence = verify_release(base, pointer['version'], trust_policy)
    if pointer.get('sha256') != evidence['sha256']:
        raise ValueError('Channel pointer SHA differs from release')
    return {'status': 'VERIFIED', 'product': product, 'channel': channel, 'version': pointer['version'],
            'history': pointer.get('history', []), 'webRoot': str(base / 'releases' / pointer['version'] / 'www'),
            'integrity': evidence, 'authenticityVerified': evidence['authenticityVerified']}


def main() -> int:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('action', choices=('promote', 'verify', 'rollback'))
    p.add_argument('--registry', type=Path, required=True)
    p.add_argument('--product', required=True)
    p.add_argument('--channel', choices=CHANNELS, default='stable')
    p.add_argument('--package', type=Path)
    p.add_argument('--signature', type=Path, help='Detached release signature (Ed25519)')
    p.add_argument('--trust-policy', type=Path, help='Administrator-pinned signing trust policy')
    args = p.parse_args()
    try:
        if args.action == 'promote':
            if args.package is None:
                p.error('--package is required for promotion')
            result = promote(args.registry, args.product, args.channel, args.package, args.signature, args.trust_policy)
        elif args.action == 'rollback':
            result = rollback(args.registry, args.product, args.channel, args.trust_policy)
        else:
            result = inspect(args.registry, args.product, args.channel, args.trust_policy)
        print(json.dumps(result, indent=2, sort_keys=True))
        return 0 if result['status'] != 'UNAVAILABLE' else 1
    except (OSError, ValueError, KeyError, json.JSONDecodeError) as error:
        print('BD3_EDITOR_RELEASE=FAIL ' + str(error), file=sys.stderr)
        return 2


if __name__ == '__main__':
    raise SystemExit(main())
