#!/usr/bin/env python3
"""Verify a HYDRA generated-editor deployable web ZIP without extracting it.

Separate from HYDRA's source manifest. Rejects spurious/missing members, unsafe
paths, symlinks, inconsistent hashes and resource exhaustion before deploy.
"""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path, PurePosixPath
import re
import stat
import sys
from zipfile import ZipFile, BadZipFile

MAX_MEMBERS = 2050
MAX_SIZE = 192 * 1024 * 1024
MAX_FILE = 64 * 1024 * 1024
RESERVED = {'hydra-release.sha256', 'hydra-deployment.json'}
WINDOWS_RESERVED = {'con', 'prn', 'aux', 'nul', *(f'com{i}' for i in range(1, 10)), *(f'lpt{i}' for i in range(1, 10))}



def verify(package: Path) -> dict:
    if not package.is_file() or package.stat().st_size > MAX_SIZE:
        raise ValueError('Web archive missing or exceeds size limit')
    with ZipFile(package) as archive:
        names = [info.filename for info in archive.infolist()]
        if len(names) > MAX_MEMBERS or len(names) != len(set(names)):
            raise ValueError('Web archive has too many or duplicate entries')
        if not RESERVED <= set(names) or 'index.html' not in names:
            raise ValueError('Deployment metadata or index.html is missing')
        total = 0
        portable_names: set[str] = set()
        for info in archive.infolist():
            name = info.filename
            path = PurePosixPath(name)
            if not name or name.startswith('/') or '\\' in name or ':' in name or any(part in ('', '.', '..') for part in path.parts):
                raise ValueError('Unsafe archive path: ' + name)
            segments = name.split('/')
            if any(not segment or segment in ('.', '..') or segment.endswith((' ', '.'))
                   or any(ord(ch) < 32 or ch in '<>\"|?*' for ch in segment)
                   or segment.split('.')[0].lower() in WINDOWS_RESERVED for segment in segments):
                raise ValueError('Not portable across Windows/Linux/macOS: ' + name)
            folded = name.casefold()
            if folded in portable_names:
                raise ValueError('Case-insensitive filename collision: ' + name)
            portable_names.add(folded)
            mode = (info.external_attr >> 16) & 0o170000
            if info.is_dir() or mode == stat.S_IFLNK:
                raise ValueError('Archive directories and symbolic links are forbidden')
            if info.file_size > MAX_FILE:
                raise ValueError('Archive entry exceeds size limit')
            total += info.file_size
            if total > MAX_SIZE:
                raise ValueError('Decompressed content exceeds size limit')
        metadata = json.loads(archive.read('hydra-deployment.json'))
        if metadata.get('level') != 'DEPLOYABLE_WEB_ASSETS' or metadata.get('nativeInstallerCertified') is not False:
            raise ValueError('Unsupported/misleading deployment qualification metadata')
        hashes = archive.read('hydra-release.sha256').decode('utf-8').splitlines()
        expected: dict[str, str] = {}
        for line in hashes:
            match = re.fullmatch(r'([0-9a-f]{64})  (.+)', line)
            if match is None or match[2] in expected:
                raise ValueError('Malformed or duplicated deployment checksum')
            expected[match[2]] = match[1]
        files = set(names) - RESERVED
        if files != set(expected) or len(files) != metadata.get('files'):
            raise ValueError('Web package inventory differs from deployment manifest')
        for name in sorted(files):
            digest = hashlib.sha256()
            with archive.open(name) as stream:
                while chunk := stream.read(65536):
                    digest.update(chunk)
            if digest.hexdigest() != expected[name]:
                raise ValueError('Web asset checksum differs from deployment manifest: ' + name)
    return {'status': 'PASSED', 'level': 'DEPLOYABLE_WEB_ASSETS', 'fileCount': len(files),
            'packageSHA256': hashlib.sha256(package.read_bytes()).hexdigest(),
            'version': metadata['version'], 'nativeInstallerCertified': False}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('package', type=Path)
    args = parser.parse_args()
    try:
        print(json.dumps(verify(args.package), sort_keys=True))
        return 0
    except (ValueError, OSError, KeyError, BadZipFile, UnicodeError, json.JSONDecodeError) as error:
        print('BD2_DEPLOYABLE_WEB_VERIFY=FAIL ' + str(error), file=sys.stderr)
        return 1


if __name__ == '__main__':
    raise SystemExit(main())
