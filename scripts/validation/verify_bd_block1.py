#!/usr/bin/env python3
"""Portable B+D block-1 functional evidence and distribution smoke test.

Compiles isolated production Java 21 types (compatible with HYDRA JDK 24/25) and
runs a meaningful executable harness, rather than inferring PASS from source text.
"""
from __future__ import annotations

import subprocess
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / 'src/main/java/com/example/editor'
SOURCES = [
    'integration/IntegrationTechnology.java',
    'integration/IntegrationVerificationStep.java',
    'integration/verification/TechnologyValidationStatus.java',
    'integration/verification/TechnologyValidationStepStatus.java',
    'integration/verification/TechnologyValidationStepResult.java',
    'integration/verification/TechnologyProjectValidationReport.java',
    *[str(path.relative_to(JAVA).as_posix()) for path in sorted((JAVA / 'integration/parity').glob('*.java'))],
    *['standalone/distribution/' + name + '.java' for name in (
        'StandaloneDistributionArtifact', 'StandaloneDistributionPlanner',
        'StandaloneDistributionProfile', 'StandaloneDistributionTarget',
        'StandaloneDistributionDiagnostic', 'StandaloneDistributionDiagnosticSeverity',
        'StandaloneDistributionValidationReport', 'StandaloneProductionReadinessService',
    )],
]


def command(arguments: list[str], label: str) -> None:
    """Execute one bounded subprocess and fail with its actionable output."""
    try:
        run = subprocess.run(arguments, cwd=ROOT, capture_output=True, text=True, timeout=90)
    except (OSError, subprocess.TimeoutExpired) as exc:
        raise SystemExit(f'FAIL {label}: {exc}') from exc
    if run.stdout:
        print(run.stdout.strip())
    if run.returncode != 0:
        if run.stderr:
            print(run.stderr.strip())
        raise SystemExit(f'FAIL {label}: exit={run.returncode}')


def main() -> int:
    with tempfile.TemporaryDirectory(prefix='hydra-bd1-java-') as destination:
        compiled = str(Path(destination))
        command(['javac', '--release', '21', '-d', compiled, *[str(JAVA / name) for name in SOURCES]],
                'isolated Java source compilation')
        command(['javac', '--release', '21', '-cp', compiled, '-d', compiled,
                 str(ROOT / 'scripts/testing/bd-block1/BD1Harness.java')],
                'B+D executable harness compilation')
        command(['java', '-cp', compiled, 'BD1Harness'], 'B+D real Java behavior harness')
    print('BD_BLOCK1=PASS isolated_compilation=PASS runtime_cases=12')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
