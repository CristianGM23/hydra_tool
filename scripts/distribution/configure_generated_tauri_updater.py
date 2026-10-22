#!/usr/bin/env python3
"""Configure Tauri v2's *own* signed updater for a trusted HYDRA generated editor.

This deliberately does not sign updates, download any release, install updates,
or issue HYDRA native-certification receipts. The signing private key stays outside
source control and is provided only to the official Tauri bundler on trusted CI.
"""
from __future__ import annotations
import argparse
import base64
import hashlib
import json
import os
from pathlib import Path
import re
import tempfile
from urllib.parse import urlsplit

MAX_FILE = 128 * 1024


def plain_file(root: Path, name: str) -> Path:
    if not root.is_dir() or root.is_symlink():
        raise ValueError('Generated editor must be a real directory')
    path = root
    for part in Path(name).parts:
        path = path / part
        if path.is_symlink():
            raise ValueError('Symbolic updater configuration path rejected: ' + name)
    if not path.is_file() or path.stat().st_size > MAX_FILE:
        raise ValueError('Missing or oversized generated file: ' + name)
    return path


def validate_public_key(value: str) -> str:
    value = value.strip()
    if not 60 <= len(value) <= 2048 or not re.fullmatch(r'[A-Za-z0-9+/=]+', value):
        raise ValueError('Expected a nonempty base64 Tauri minisign public key')
    try:
        decoded = base64.b64decode(value, validate=True)
    except (ValueError, base64.binascii.Error) as exc:
        raise ValueError('Invalid updater public key encoding') from exc
    # Tauri's pubkey is the base64 encoding of a minisign .pub text file.
    # Minisign's second line decodes to a 42-byte Ed25519 key record: Ed + 8-byte key id + 32-byte key.
    lines = decoded.splitlines()
    if len(lines) != 2 or not lines[0].startswith(b'untrusted comment: minisign public key: '):
        raise ValueError('Expected an official Tauri minisign public key, not a HYDRA receipt key')
    try:
        record = base64.b64decode(lines[1], validate=True)
    except (ValueError, base64.binascii.Error) as exc:
        raise ValueError('Invalid minisign public key record') from exc
    if len(record) != 42 or record[:2] != b'Ed':
        raise ValueError('Malformed Tauri minisign public key record')
    return value


def validate_endpoint(value: str) -> str:
    if len(value) > 2048 or any(c.isspace() or ord(c) < 32 for c in value):
        raise ValueError('Unsafe updater endpoint')
    parsed = urlsplit(value)
    if parsed.scheme != 'https' or not parsed.hostname or parsed.username or parsed.password or parsed.fragment:
        raise ValueError('Updater endpoint must be HTTPS without credentials or fragment')
    if parsed.port is not None and parsed.port != 443:
        raise ValueError('Nonstandard HTTPS update endpoint port prohibited by release policy')
    if parsed.hostname in ('localhost', '127.0.0.1', '::1') or parsed.hostname.endswith('.localhost'):
        raise ValueError('Local updater endpoint prohibited in production')
    return value


def configure(root: Path, pubkey: str, endpoint: str, *, apply: bool = False) -> dict:
    root = root.absolute()
    pubkey, endpoint = validate_public_key(pubkey), validate_endpoint(endpoint)
    paths = {name: plain_file(root, name) for name in (
        'src-tauri/tauri.conf.json', 'src-tauri/Cargo.toml',
        'src-tauri/src/main.rs', 'src-tauri/capabilities/default.json',
    )}
    config = json.loads(paths['src-tauri/tauri.conf.json'].read_text(encoding='utf-8'))
    capabilities = json.loads(paths['src-tauri/capabilities/default.json'].read_text(encoding='utf-8'))
    if not isinstance(config, dict) or not isinstance(capabilities, dict):
        raise ValueError('Invalid generated Tauri configuration')
    main = paths['src-tauri/src/main.rs'].read_text(encoding='utf-8')
    cargo = paths['src-tauri/Cargo.toml'].read_text(encoding='utf-8')
    if not config.get('identifier', '').startswith('org.hydra.') or not config.get('build', {}).get('frontendDist') == '../dist':
        raise ValueError('Not a generated HYDRA Tauri v2 editor')
    if 'tauri::Builder::default()' not in main or 'tauri = { version = "2"' not in cargo:
        raise ValueError('Unknown generated Tauri main.rs or Cargo.toml')
    if 'tauri_plugin_updater::Builder::new().build()' not in main:
        main = main.replace('tauri::Builder::default()',
                            'tauri::Builder::default()\n        .plugin(tauri_plugin_updater::Builder::new().build())', 1)
    package_line = 'tauri-plugin-updater = "2"'
    if package_line not in cargo:
        if 'tauri-plugin-updater' in cargo:
            raise ValueError('Existing updater dependency requires manual review')
        if '\n[dependencies]\n' not in cargo:
            raise ValueError('Missing generated Cargo dependencies')
        cargo = cargo.replace('\n[dependencies]\n', '\n[dependencies]\n' + package_line + '\n', 1)
    installed = config.get('plugins', {}).get('updater')
    expected = {'pubkey': pubkey, 'endpoints': [endpoint], 'windows': {'installMode': 'passive'}}
    if installed is not None and installed != expected:
        raise ValueError('Existing updater trust settings differ; refusing silent key or endpoint rotation')
    config.setdefault('plugins', {})['updater'] = expected
    config.setdefault('bundle', {})['createUpdaterArtifacts'] = True
    permissions = capabilities.get('permissions')
    if not isinstance(permissions, list) or not all(isinstance(p, str) for p in permissions):
        raise ValueError('Invalid generated Tauri capabilities')
    for perm in ('updater:allow-check', 'updater:allow-download-and-install'):
        if perm not in permissions:
            permissions.append(perm)
    proposed = {
        'src-tauri/tauri.conf.json': json.dumps(config, ensure_ascii=False, sort_keys=True, indent=2) + '\n',
        'src-tauri/Cargo.toml': cargo,
        'src-tauri/src/main.rs': main,
        'src-tauri/capabilities/default.json': json.dumps(capabilities, ensure_ascii=False, sort_keys=True, indent=2) + '\n',
    }
    changed = sorted(name for name, content in proposed.items() if paths[name].read_text(encoding='utf-8') != content)
    if apply:
        # Original generated files are backed up before mutation; recovery is explicit.
        backup = root / 'src-tauri' / '.hydra-updater-originals'
        if changed and backup.exists():
            raise ValueError('Previous updater backup already exists: manual review required')
        if changed:
            backup.mkdir(mode=0o700)
            original = {name: paths[name].read_bytes() for name in changed}
            try:
                for name, content in original.items():
                    copy = backup / Path(name).name
                    with copy.open('xb') as handle:
                        handle.write(content)
                for name in changed:
                    fd, tempname = tempfile.mkstemp(prefix='.hydra-updater-', dir=paths[name].parent)
                    try:
                        with os.fdopen(fd, 'wb') as handle:
                            handle.write(proposed[name].encode('utf-8'))
                            handle.flush()
                            os.fsync(handle.fileno())
                        os.replace(tempname, paths[name])
                    finally:
                        if os.path.exists(tempname):
                            os.unlink(tempname)
            except Exception:
                # Only local generated metadata, never user models.
                for name, content in original.items():
                    paths[name].write_bytes(content)
                raise
    return {
        'schemaVersion': 1,
        'status': 'CONFIGURED' if apply else 'PREVIEW_ONLY',
        'changedFiles': changed,
        'trustedEndpoint': endpoint,
        'publicKeySha256': hashlib.sha256(pubkey.encode('ascii')).hexdigest(),
        'tauriUpdaterArtifactGeneration': True,
        'signedUpdateExecutionCertified': False,
        'osInstalledModelRoundTripCertified': False,
        'requires': ['TAURI_SIGNING_PRIVATE_KEY on trusted CI', 'signed updater execution test',
                     'migration/rollback tests on each OS'],
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--editor-root', type=Path, required=True)
    parser.add_argument('--public-key-file', type=Path, required=True)
    parser.add_argument('--endpoint', required=True)
    parser.add_argument('--apply', action='store_true')
    parser.add_argument('--report', type=Path)
    args = parser.parse_args()
    try:
        if args.public_key_file.is_symlink() or not args.public_key_file.is_file() or args.public_key_file.stat().st_size > 4096:
            raise ValueError('Missing, linked, or oversized trusted Tauri public key file')
        result = configure(args.editor_root, args.public_key_file.read_text(encoding='utf-8'), args.endpoint, apply=args.apply)
        result_json = json.dumps(result, ensure_ascii=False, indent=2, sort_keys=True) + '\n'
        if args.report:
            if args.report.is_symlink():
                raise ValueError('Symbolic report is forbidden')
            args.report.parent.mkdir(parents=True, exist_ok=True)
            args.report.write_text(result_json, encoding='utf-8')
        print(result_json, end='')
        return 0
    except (ValueError, OSError, UnicodeError, json.JSONDecodeError) as exc:
        print('TAURI_UPDATER_CONFIGURATION=BLOCKED ' + str(exc))
        return 2

if __name__ == '__main__':
    raise SystemExit(main())
