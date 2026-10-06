from __future__ import annotations

import argparse
import hashlib
import json
import shutil
import subprocess
import tempfile
from pathlib import Path

from source_inventory import source_inventory, source_root

ROOT = Path(__file__).resolve().parents[2]
parser = argparse.ArgumentParser()
parser.add_argument('--baseline', required=True)
parser.add_argument('--baseline-version', required=True)
args = parser.parse_args()
BASE = Path(args.baseline).resolve()
OUT = ROOT / 'target/release'
OUT.mkdir(parents=True, exist_ok=True)
version = json.loads((ROOT / 'config/hydra-version.json').read_text(encoding='utf-8'))['version']


def copy_inventory(source: Path, source_version: str, destination: Path) -> None:
    for relative, path in source_inventory(source, source_version).items():
        target = destination / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(path, target)


with tempfile.TemporaryDirectory(prefix='hydra-patch-') as temporary, source_root(BASE) as base_root:
    repo = Path(temporary)
    copy_inventory(base_root, args.baseline_version, repo)
    subprocess.run(['git', 'init', '-q'], cwd=repo, check=True)
    subprocess.run(['git', 'config', 'user.email', 'hydra-release@local.invalid'], cwd=repo, check=True)
    subprocess.run(['git', 'config', 'user.name', 'HYDRA release'], cwd=repo, check=True)
    subprocess.run(['git', 'config', 'core.autocrlf', 'false'], cwd=repo, check=True)
    subprocess.run(['git', 'config', 'core.safecrlf', 'false'], cwd=repo, check=True)
    # Highest-precedence repository-local attributes force every modified source file into
    # a raw-byte GIT binary patch. Disabling text normalization is essential because the
    # distributed baseline intentionally contains both LF and CRLF files.
    info_attributes = repo / '.git/info/attributes'
    info_attributes.parent.mkdir(parents=True, exist_ok=True)
    info_attributes.write_text('* -diff -text\n', encoding='utf-8')
    subprocess.run(['git', 'add', '-f', '-A'], cwd=repo, check=True)
    subprocess.run(['git', 'commit', '-q', '-m', f'baseline {args.baseline_version}'], cwd=repo, check=True)

    for path in sorted(repo.rglob('*'), reverse=True):
        if '.git' in path.parts:
            continue
        if path.is_file() or path.is_symlink():
            path.unlink()
    copy_inventory(ROOT, version, repo)
    # Stage the complete target state. A cached diff against the baseline commit is the
    # only reliable way to include added, modified and deleted files together, including
    # ignored allowlisted paths and empty files.
    subprocess.run(['git', 'add', '-f', '-A'], cwd=repo, check=True)
    diff = subprocess.run(
        ['git', 'diff', '--cached', '--binary', '--no-ext-diff', '--no-renames', 'HEAD'],
        cwd=repo,
        check=True,
        stdout=subprocess.PIPE,
    ).stdout

patch = OUT / f'HYDRA_V{version}_over_V{args.baseline_version}.patch'
patch.write_bytes(diff)
sha = hashlib.sha256(diff).hexdigest()
(OUT / (patch.name + '.sha256')).write_text(f'{sha}  {patch.name}\n', encoding='utf-8')
print(patch)
print('sha256=' + sha)
