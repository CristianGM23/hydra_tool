from __future__ import annotations

import hashlib
import json
import zipfile
from pathlib import Path

from source_inventory import source_files

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'target/release'
OUT.mkdir(parents=True, exist_ok=True)
version = json.loads((ROOT / 'config/hydra-version.json').read_text(encoding='utf-8'))['version']
archive = OUT / f'HYDRA_V{version}_source.zip'
BUFFER_SIZE = 1024 * 1024


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open('rb') as source:
        for chunk in iter(lambda: source.read(BUFFER_SIZE), b''):
            digest.update(chunk)
    return digest.hexdigest()


def write_file(output: zipfile.ZipFile, path: Path, relative: str) -> None:
    mode = 0o100755 if relative == 'mvnw' else 0o100644
    info = zipfile.ZipInfo(relative, date_time=(2026, 1, 1, 0, 0, 0))
    info.compress_type = zipfile.ZIP_DEFLATED
    info.external_attr = mode << 16
    with path.open('rb') as source, output.open(info, 'w') as destination:
        while chunk := source.read(BUFFER_SIZE):
            destination.write(chunk)


for stale in (Path('config/config'), Path('docs/docs'), Path('scripts/validation/validation')):
    if (ROOT / stale).exists():
        raise SystemExit(f'Refusing to package stale duplicated tree: {stale.as_posix()}')

# The workspace may contain versioned Archify documentation and temporary
# outputs that are intentionally *not* in the canonical signed source manifest.
# Reproducible release archives contain only the reviewed manifest members.
manifest = ROOT / 'MANIFEST_SHA256.txt'
if not manifest.is_file():
    raise SystemExit('Missing MANIFEST_SHA256.txt')
expected: dict[str, str] = {}
for number, line in enumerate(manifest.read_text(encoding='utf-8').splitlines(), 1):
    if not line.strip():
        continue
    if '  ' not in line:
        raise SystemExit(f'Malformed manifest line {number}')
    digest, relative = line.split('  ', 1)
    if (len(digest) != 64 or any(c not in '0123456789abcdef' for c in digest.lower())
            or relative in expected or relative == 'MANIFEST_SHA256.txt'
            or Path(relative).is_absolute() or '..' in Path(relative).parts):
        raise SystemExit(f'Invalid canonical manifest entry on line {number}')
    expected[relative] = digest.lower()

inventory = {path.relative_to(ROOT).as_posix(): path for path in source_files(ROOT, version)}
missing = sorted(set(expected) - set(inventory))
if missing:
    raise SystemExit(f'Missing canonical source files: {missing[:10]}')
for relative, digest in expected.items():
    if sha256_file(inventory[relative]) != digest:
        raise SystemExit(f'Canonical source hash mismatch: {relative}')
files = [inventory[relative] for relative in sorted(expected)] + [manifest]
files.sort(key=lambda path: path.relative_to(ROOT).as_posix())
with zipfile.ZipFile(archive, 'w', compression=zipfile.ZIP_DEFLATED, compresslevel=9) as output:
    for path in files:
        write_file(output, path, path.relative_to(ROOT).as_posix())
sha = sha256_file(archive)
(OUT / (archive.name + '.sha256')).write_text(f'{sha}  {archive.name}\n', encoding='utf-8')
print(archive)
print('files', len(files))
print('sha256', sha)
