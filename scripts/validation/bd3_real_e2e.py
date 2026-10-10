#!/usr/bin/env python3
"""B3+D3 integration: actual Node native semantics -> offline -> Java ZIP -> immutable channel.

Runs only a trusted, bundled generated-editor fixture in a temporary copy.
Does not certify third-party native adapters or fabricate installer binaries.
"""
from __future__ import annotations

import importlib.util
import json
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'scripts/validation'))
sys.path.insert(0, str(ROOT / 'scripts/distribution'))
from run_native_parity_suite import certify
from editor_release_registry import promote, inspect
from build_generated_editor import precache
from verify_web_package import verify

JAVA = ROOT / 'src/main/java/com/example/editor'
JAVA_SOURCES = ['io/AtomicFiles.java', *['standalone/distribution/' + name + '.java' for name in (
    'StandaloneDistributionPackageResult', 'StandaloneDistributionProfile',
    'StandaloneDistributionTarget', 'StandaloneDistributionDiagnostic',
    'StandaloneDistributionDiagnosticSeverity', 'StandaloneDistributionValidationReport',
    'StandaloneWebDeploymentPackager', 'StandaloneProductionReadinessService')]]


def must_run(command: list[str], *, cwd: Path, timeout: int = 180) -> str:
    run = subprocess.run(command, cwd=cwd, capture_output=True, text=True, errors='replace', timeout=timeout)
    if run.returncode:
        raise RuntimeError('Failed: ' + ' '.join(command[:3]) + '\n' + (run.stdout + run.stderr)[-5000:])
    return run.stdout.strip()


def main() -> int:
    if not shutil.which('node') or not shutil.which('npm') or not shutil.which('javac') or not shutil.which('java'):
        print('BD3_REAL_E2E=UNAVAILABLE Node/npm and JDK21+ are required', file=sys.stderr)
        return 2
    with tempfile.TemporaryDirectory(prefix='hydra-bd3-real-') as location:
        temp = Path(location)
        editor = temp / 'reference-editor'
        shutil.copytree(ROOT / 'reference-projects/bd2-standalone-smoke', editor,
                        ignore=shutil.ignore_patterns('node_modules', 'dist', 'target'))
        native = certify('generated-web', editor, 'tests/hydra-parity/scenarios.json', 150, True)
        if native['status'] != 'PASSED' or len(native['scenarios']) != 7:
            raise RuntimeError('Generated-editor semantic parity evidence is incomplete: ' + json.dumps(native)[-2000:])
        _, assets = precache(editor / 'dist', '1.0.0', 'HYDRA Editor Fixture')
        if 'index.html' not in assets or not (editor / 'dist/service-worker.js').is_file():
            raise RuntimeError('Offline shell build did not include generated editor entry point')
        classes = temp / 'classes'
        classes.mkdir()
        must_run(['javac', '--release', '21', '-d', str(classes),
                  *[str(JAVA / file) for file in JAVA_SOURCES],
                  str(ROOT / 'scripts/testing/bd-block2/BD2RealBundleHarness.java')], cwd=ROOT)
        package = temp / 'independent-editor.zip'
        print(must_run(['java', '-cp', str(classes), 'BD2RealBundleHarness', str(editor), str(package)], cwd=ROOT))
        if verify(package)['status'] != 'PASSED':
            raise RuntimeError('Real Java web bundle failed independent verification')
        promoted = promote(temp / 'registry', 'hydra-editor-fixture', 'stable', package)
        published = inspect(temp / 'registry', 'hydra-editor-fixture', 'stable')
        if promoted['version'] != '1.0.0' or published['status'] != 'VERIFIED':
            raise RuntimeError('Immutable release registry did not verify the actual web bundle')
        print('HYDRA_BD3_REAL_E2E=PASS scenarios=7 native_runtime=NODE '
              'offline=PASS java_bundle=PASS sha256=PASS release=VERIFIED')
    return 0


if __name__ == '__main__':
    try:
        raise SystemExit(main())
    except (OSError, RuntimeError, ValueError, subprocess.TimeoutExpired) as error:
        print('HYDRA_BD3_REAL_E2E=FAIL ' + str(error), file=sys.stderr)
        raise SystemExit(1)
