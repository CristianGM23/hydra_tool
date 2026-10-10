#!/usr/bin/env python3
"""D4: independently inventory real native installer artifacts from a trusted build.

This is an integrity/provenance inventory, not an OS code-signing or installation
certificate. True installer readiness still needs runner-level install/launch tests.
"""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path, PurePosixPath
import re
import sys

MAX_BYTES = 2 * 1024 * 1024 * 1024
MAX_BUNDLES = 24
SUFFIXES = {
    'windows': ('.msi', '.exe'),
    'linux': ('.deb', '.rpm', '.appimage'),
    'macos': ('.dmg', '.app'),
}


def digest(path: Path) -> str:
    value = hashlib.sha256()
    with path.open('rb') as f:
        while block := f.read(1024 * 1024):
            value.update(block)
    return value.hexdigest()


def validated_bundle(bundle_dir: Path, relative: str, platform: str) -> dict:
    if not isinstance(relative, str) or not relative or '\\' in relative or ':' in relative:
        raise ValueError('Invalid native bundle relative path')
    parts = PurePosixPath(relative).parts
    if relative.startswith('/') or any(part in ('.', '..') or part.startswith('.') for part in parts):
        raise ValueError('Native bundle escapes trusted output directory')
    current = bundle_dir.resolve()
    for part in parts:
        current = current / part
        if current.is_symlink():
            raise ValueError('Native bundle contains symlink path')
    lower = current.name.lower()
    if not lower.endswith(SUFFIXES[platform]):
        raise ValueError('Unsupported installer format for target platform')
    if current.is_file():
        size = current.stat().st_size
        if size < 1024 or size > MAX_BYTES:
            raise ValueError('Native installer missing, empty or too large')
        return {'path': relative, 'kind': 'INSTALLER_FILE', 'sha256': digest(current), 'bytes': size}
    if platform != 'macos' or not lower.endswith('.app') or not current.is_dir():
        raise ValueError('Native installer does not exist')
    binary_root = current / 'Contents' / 'MacOS'
    if binary_root.is_symlink() or not binary_root.is_dir():
        raise ValueError('macOS app has no executable directory')
    files = sorted(path for path in current.rglob('*') if path.is_file())
    if not files or len(files) > 2000 or any(f.is_symlink() for f in files):
        raise ValueError('Invalid native application inventory')
    if not any(f.is_file() and f.stat().st_size > 0 for f in binary_root.iterdir()):
        raise ValueError('macOS app has no executable payload')
    entries = [(f.relative_to(current).as_posix(), digest(f), f.stat().st_size) for f in files]
    if sum(sz for _,_,sz in entries) > MAX_BYTES:
        raise ValueError('Native bundle exceeds configured size budget')
    root_digest = hashlib.sha256(json.dumps(entries, sort_keys=True, separators=(',', ':')).encode()).hexdigest()
    return {'path': relative, 'kind': 'MACOS_APP_DIRECTORY', 'sha256': root_digest,
            'bytes': sum(sz for _,_,sz in entries), 'files': len(entries)}


def verify(editor: Path, result: dict, platform: str) -> dict:
    if platform not in SUFFIXES:
        raise ValueError('Unknown target operating system')
    native = result.get('steps', {}).get('nativeBuild', {})
    if result.get('qualified') is not True or native.get('status') != 'PASSED':
        raise ValueError('No successful, executed native build to attest')
    paths = native.get('bundlePaths')
    if not isinstance(paths, list) or not 1 <= len(paths) <= MAX_BUNDLES or len(paths) != len(set(paths)):
        raise ValueError('Native build must list unique real bundle paths')
    bundle_dir = editor / 'src-tauri' / 'target' / 'release' / 'bundle'
    if bundle_dir.is_symlink() or not bundle_dir.is_dir():
        raise ValueError('Native bundle root not present')
    members = [validated_bundle(bundle_dir, path, platform) for path in paths]
    return {'schemaVersion': 1, 'status': 'PASSED', 'platform': platform,
            'editorVersion': result.get('version'), 'bundleCount': len(members),
            'installerFilesVerified': True, 'nativeInstallLaunchCertified': False,
            'osCodeSignatureVerified': False, 'artifacts': members}


def main() -> int:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--editor', type=Path, required=True)
    p.add_argument('--build-report', type=Path, required=True)
    p.add_argument('--platform', choices=sorted(SUFFIXES), required=True)
    p.add_argument('--report', type=Path)
    args = p.parse_args()
    try:
        if args.build_report.is_symlink() or args.build_report.stat().st_size > 256 * 1024:
            raise ValueError('Native build report invalid or oversized')
        result = verify(args.editor, json.loads(args.build_report.read_text(encoding='utf-8')), args.platform)
        if args.report:
            args.report.parent.mkdir(parents=True, exist_ok=True)
            args.report.write_text(json.dumps(result, sort_keys=True, indent=2) + '\n', encoding='utf-8')
        print(json.dumps(result, sort_keys=True))
        return 0
    except (ValueError, OSError, KeyError, json.JSONDecodeError) as exc:
        print('BD4_NATIVE_INSTALLER_VERIFY=FAIL ' + str(exc), file=sys.stderr)
        return 2


if __name__ == '__main__':
    raise SystemExit(main())
