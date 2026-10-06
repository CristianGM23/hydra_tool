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

files = source_files(ROOT, version)
with zipfile.ZipFile(archive, 'w', compression=zipfile.ZIP_DEFLATED, compresslevel=9) as output:
    for path in files:
        write_file(output, path, path.relative_to(ROOT).as_posix())
sha = sha256_file(archive)
(OUT / (archive.name + '.sha256')).write_text(f'{sha}  {archive.name}\n', encoding='utf-8')
print(archive)
print('files', len(files))
print('sha256', sha)
