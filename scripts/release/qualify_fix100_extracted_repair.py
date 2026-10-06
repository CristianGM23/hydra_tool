from __future__ import annotations

import argparse
import hashlib
import json
import os
import shutil
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'target/release'
OUT.mkdir(parents=True, exist_ok=True)
sys.path.insert(0, str(ROOT / 'scripts/release'))
from qualification_engine import QualificationRunner, add_resume_arguments  # noqa:E402

parser = argparse.ArgumentParser(
    description='Revalida exclusivamente el tramo de paquete extraído que falló en Fix100, sin repetir los gates ya superados.'
)
add_resume_arguments(parser)
args = parser.parse_args()
version = json.loads((ROOT / 'config/hydra-version.json').read_text(encoding='utf-8'))['version']
runner = QualificationRunner('fix117-extracted-repair', args, fingerprint_extra={'version': version, 'scope': 'extracted-repair'})
report_path = OUT / 'fix117-extracted-repair.json'


def sha(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open('rb') as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b''):
            digest.update(chunk)
    return digest.hexdigest()


def finish(status: str, extra: dict | None = None) -> int:
    payload = {
        'schemaVersion': '1.0',
        'qualification': 'Fix117 extracted package repair',
        'version': version,
        'status': status,
        'scope': 'ONLY_FAILED_EXTRACTED_PACKAGE_SEGMENT',
        'fingerprint': runner.fingerprint,
        'checkpoint': str(runner.state_path.relative_to(ROOT)),
        'steps': runner.stage_payloads(),
    }
    if extra:
        payload.update(extra)
    report_path.write_text(json.dumps(payload, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')
    print(report_path)
    return 0 if status == 'PASS' else 1


def run(name: str, cmd: list[str], *, cwd: Path = ROOT, timeout: int = 3600, outputs: list[Path] | tuple[Path, ...] = ()) -> None:
    if not runner.run(name, cmd, cwd=cwd, timeout=timeout, outputs=outputs):
        raise SystemExit(finish('FAIL'))


# No se ejecutan primary-full-tree, Vitest, Playwright, Maven, Axe ni ningún gate
# que ya quedó en PASS. Solo se reconstruye el paquete y se reproduce el tramo que falló.
archive = OUT / f'HYDRA_V{version}_source.zip'
run('source-package', [sys.executable, 'scripts/release/package_source.py'], timeout=1800, outputs=[archive])
archive_sha = sha(archive)

work = OUT / 'work/fix117-extracted-repair'
marker = work / '.hydra-source-sha256'
if not marker.is_file() or marker.read_text(encoding='utf-8').strip() != archive_sha:
    if work.exists():
        shutil.rmtree(work)
    work.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(archive) as package:
        for info in package.infolist():
            relative = Path(info.filename)
            if relative.is_absolute() or '..' in relative.parts:
                raise SystemExit(finish('FAIL', {'reason': 'unsafe package path'}))
        package.extractall(work)
    marker.write_text(archive_sha + '\n', encoding='utf-8')

run('extracted-package-integrity', [sys.executable, 'scripts/validation/verify_manifest.py'], cwd=work, timeout=600)
frontend = work / 'frontend'
npm = ['npm.cmd', 'ci', '--include=dev'] if os.name == 'nt' else ['npm', 'ci', '--include=dev']
typescript_bin = frontend / 'node_modules/typescript/bin/tsc'
run('extracted-npm-ci', npm, cwd=frontend, timeout=3600, outputs=[typescript_bin])

# ÚNICAMENTE los cuatro gates que fallaron en la ejecución del 04/10/2026.
run('xtext-langium-runtime', [sys.executable, 'scripts/validation/verify_language_host_runtime_lifecycle.py'], cwd=work, timeout=1200)
run('portable-host-runtime', [sys.executable, 'scripts/validation/verify_portable_host_runtime_lifecycle.py'], cwd=work, timeout=1200)
run('generated-portable-hosts', [sys.executable, 'scripts/validation/verify_generated_portable_host_project_tests.py'], cwd=work, timeout=1200)
run('spanish-visible-runtime', ['node', 'scripts/validation/verify_frontend_spanish_coverage.cjs'], cwd=work, timeout=1200)

# Comprobación de empaquetado, sin repetir tests funcionales.
rebuilt = work / 'target/release' / archive.name
run('extracted-repackage', [sys.executable, 'scripts/release/package_source.py'], cwd=work, timeout=1800, outputs=[rebuilt])
reproducible = rebuilt.is_file() and sha(rebuilt) == archive_sha
runner.record(
    'package-byte-reproducibility',
    reproducible,
    f'original={archive_sha} rebuilt={sha(rebuilt) if rebuilt.is_file() else "MISSING"}',
)
if not reproducible:
    raise SystemExit(finish('FAIL'))

raise SystemExit(finish('PASS', {
    'sourceArchive': archive.name,
    'sourceSha256': archive_sha,
    'message': 'Solo se ha revalidado el segmento de paquete extraído que había fallado; no se han repetido los tests ya superados.',
}))
