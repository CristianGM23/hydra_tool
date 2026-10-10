#!/usr/bin/env python3
"""Regresión de la separación entre workspace y paquete fuente firmado.

No usa Java/npm, no escribe fuera de un directorio temporal y no modifica
la fuente de producción ni los artefactos existentes del usuario.
"""
from __future__ import annotations

import contextlib
import hashlib
import importlib.util
import io
import json
import subprocess
import sys
import tempfile
import zipfile
from pathlib import Path
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]
CHECKS: list[tuple[str, bool]] = []


def check(name: str, ok: bool) -> None:
    CHECKS.append((name, ok))
    print(('PASS' if ok else 'FAIL') + ' ' + name)


def run(root: Path, relative: str, *args: str) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        [sys.executable, relative, *args], cwd=root, text=True,
        stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=45,
    )


def sha(raw: bytes) -> str:
    return hashlib.sha256(raw).hexdigest()


def main() -> int:
    with tempfile.TemporaryDirectory(prefix='hydra-fix173-boundary-') as td:
        test_root = Path(td)
        required = (
            'scripts/release/source_inventory.py',
            'scripts/release/package_source.py',
            'scripts/validation/verify_manifest.py',
        )
        for rel in required:
            destination = test_root / rel
            destination.parent.mkdir(parents=True, exist_ok=True)
            destination.write_bytes((ROOT / rel).read_bytes())
        config = test_root / 'config/hydra-version.json'
        config.parent.mkdir(parents=True, exist_ok=True)
        config.write_text(json.dumps({'version': '4.32.107'}) + '\n', encoding='utf-8')
        canonical_file = test_root / 'src/main/java/Canonical.java'
        canonical_file.parent.mkdir(parents=True, exist_ok=True)
        canonical_file.write_bytes(b'class Canonical {}\n')
        extra = test_root / 'docs/architecture/archify/internal/architecture.en.html'
        extra.parent.mkdir(parents=True, exist_ok=True)
        extra.write_bytes(b'Archify supplementary docs.\n')
        other = test_root / 'docs/grammar/unlisted-report.md'
        other.parent.mkdir(parents=True, exist_ok=True)
        other.write_bytes(b'Unlisted report.\n')
        manifest = test_root / 'MANIFEST_SHA256.txt'
        expected = sorted(set(required) | {'config/hydra-version.json', 'src/main/java/Canonical.java'})
        manifest.write_text(
            ''.join(f'{sha((test_root / rel).read_bytes())}  {rel}\n' for rel in expected),
            encoding='utf-8',
        )

        accepted = run(test_root, 'scripts/validation/verify_manifest.py', '--allow-extra')
        check('workspace preserves additional Archify and documentation files',
              accepted.returncode == 0 and 'workspace extras=2' in accepted.stdout)

        refused = run(test_root, 'scripts/validation/verify_manifest.py')
        check('strict verification rejects unlisted files in a workspace',
              refused.returncode != 0 and 'FAIL unlisted docs/architecture/archify' in refused.stdout)

        # A source generator can delete an *unlisted* artifact immediately after
        # the inventory is captured. The verifier must not open that artifact.
        module_path = test_root / 'scripts/validation/verify_manifest.py'
        spec = importlib.util.spec_from_file_location('fix173_verifier_under_test', module_path)
        assert spec and spec.loader
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        phantom = test_root / 'docs/architecture/archify/internal/.archify-delivery-removed/file.json'
        index = {rel: test_root / rel for rel in expected}
        index['docs/architecture/archify/internal/.archify-delivery-removed/file.json'] = phantom
        with (patch.object(module, 'source_inventory', return_value=index),
              patch.object(sys, 'argv', ['verify_manifest.py', '--allow-extra']),
              contextlib.redirect_stdout(io.StringIO())):
            phantom_result = module.main()
        check('vanished unlisted Archify file does not crash or fail workspace verification',
              phantom_result == 0 and not phantom.exists())

        mutated = canonical_file.read_bytes()
        canonical_file.write_bytes(b'class Changed {}\n')
        broken = run(test_root, 'scripts/validation/verify_manifest.py', '--allow-extra')
        package_broken = run(test_root, 'scripts/release/package_source.py')
        check('canonical drift fails even when workspace extras are allowed',
              broken.returncode != 0 and 'FAIL changed' in broken.stdout)
        check('source packaging refuses canonical drift',
              package_broken.returncode != 0 and 'Canonical source hash mismatch' in package_broken.stdout)
        canonical_file.write_bytes(mutated)

        packed = run(test_root, 'scripts/release/package_source.py')
        archive = test_root / 'target/release/HYDRA_V4.32.107_source.zip'
        check('source package can be produced from a workspace containing extra files',
              packed.returncode == 0 and archive.is_file())
        with zipfile.ZipFile(archive) as z:
            names = set(z.namelist())
            first_package_hash = sha(archive.read_bytes())
            check('signed source archive has exactly canonical files and manifest',
                  names == set(expected) | {'MANIFEST_SHA256.txt'})
            unpacked = test_root / 'extracted'
            z.extractall(unpacked)
        checked = run(unpacked, 'scripts/validation/verify_manifest.py')
        check('extracted source package passes strict integrity', checked.returncode == 0)
        repacked = run(unpacked, 'scripts/release/package_source.py')
        rebuilt = unpacked / 'target/release/HYDRA_V4.32.107_source.zip'
        check('extracted package rebuild is byte-reproducible',
              repacked.returncode == 0 and rebuilt.is_file() and sha(rebuilt.read_bytes()) == first_package_hash)

        for qualifier, stage in [
            ('scripts/release/qualify_release.py', 'primary-source-integrity'),
            ('scripts/release/qualify_rc1.py', 'rc1-workspace-integrity'),
            ('scripts/release/qualify_rc2.py', 'workspace-integrity'),
        ]:
            code = (ROOT / qualifier).read_text(encoding='utf-8')
            check(f'{stage} verifies canonical hashes while permitting workspace extras',
                  stage in code and "'scripts/validation/verify_manifest.py','--allow-extra'" in code)
        release = (ROOT / 'scripts/release/qualify_release.py').read_text(encoding='utf-8')
        check('extracted release remains strictly verified',
              "run('package-integrity',[sys.executable,'scripts/validation/verify_manifest.py'],cwd=work)" in release)
        rc2 = (ROOT / 'scripts/release/qualify_rc2.py').read_text(encoding='utf-8')
        check('extracted RC2 remains strictly verified',
              "run('package-requalification',[sys.executable,'scripts/validation/verify_manifest.py'],cwd=work" in rc2)

    failed = [name for name, ok in CHECKS if not ok]
    print(f'R6C4_FIX173_RELEASE_SOURCE_BOUNDARY={len(CHECKS)-len(failed)}/{len(CHECKS)} ' + ('PASS' if not failed else 'FAIL'))
    return 1 if failed else 0


if __name__ == '__main__':
    raise SystemExit(main())
