#!/usr/bin/env python3
"""Run real B2 native compilation AND test stages on pre-generated projects.

No uploaded shell commands, no automatic dependency installation beyond the
explicit trusted npm/maven/pytest test profile; no simulated native PASS.
This local/CI developer tool complements the existing HYDRA native-parity API.
"""
from __future__ import annotations

import argparse
from dataclasses import dataclass
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]


@dataclass(frozen=True)
class Profile:
    required: tuple[str, ...]
    stages: tuple[tuple[str, tuple[str, ...]], ...]
    capability_scope: tuple[str, ...]


PROFILES: dict[str, Profile] = {
    'java-standalone': Profile(('src/HydraJavaEditorSmoke.java', 'tests/hydra-parity/scenarios.json'), (
        ('java-compile', ('javac', '-encoding', 'UTF-8', '-d', '.', 'src/HydraJavaEditorSmoke.java')),
        ('java-semantic-tests', ('java', '-cp', '.', 'HydraJavaEditorSmoke'))),
        ('NATIVE_BUILD', 'TEST_SUITE_EXECUTED')),
    'generated-web': Profile(('package.json', 'package-lock.json'), (
        ('npm-install', ('npm', 'ci', '--no-audit', '--no-fund')),
        ('editor-build', ('npm', 'run', 'build')),
        ('editor-tests', ('npm', 'test'))), ('NATIVE_BUILD', 'TEST_SUITE_EXECUTED')),
    'emf': Profile(('pom.xml',), (('maven-tests', ('mvn', '-B', '--no-transfer-progress', 'verify')),), ('NATIVE_BUILD',)),
    'sirius': Profile(('pom.xml',), (('tycho-tests', ('mvn', '-B', '--no-transfer-progress', 'verify')),), ('NATIVE_BUILD',)),
    'xtext': Profile(('pom.xml',), (('xtext-tests', ('mvn', '-B', '--no-transfer-progress', 'verify')),), ('NATIVE_BUILD',)),
    'langium': Profile(('package.json', 'package-lock.json'), (
        ('npm-install', ('npm', 'ci', '--no-audit', '--no-fund')),
        ('langium-build', ('npm', 'run', 'build')),
        ('langium-tests', ('npm', 'test', '--', '--run'))), ('NATIVE_BUILD', 'TEST_SUITE_EXECUTED')),
    'glsp': Profile(('package.json', 'package-lock.json'), (
        ('npm-install', ('npm', 'ci', '--no-audit', '--no-fund')),
        ('glsp-build', ('npm', 'run', 'build')),
        ('glsp-tests', ('npm', 'test', '--', '--run'))), ('NATIVE_BUILD', 'TEST_SUITE_EXECUTED')),
    'besser': Profile(('pyproject.toml',), (
        ('besser-tests', ('python', '-m', 'pytest', '-q')),), ('TEST_SUITE_EXECUTED',)),
    'antlr': Profile(('pom.xml',), (
        ('antlr-tests', ('mvn', '-B', '--no-transfer-progress', 'verify')),), ('NATIVE_BUILD',)),
}


def locate(root: Path, relative: str) -> Path:
    if not relative or '\\' in relative or re.match(r'^[A-Za-z]:', relative):
        raise ValueError('Project directory must be a portable relative path')
    value = Path(relative)
    if value.is_absolute() or any(part in ('.', '..') for part in value.parts):
        raise ValueError('Project escapes the generated-project root')
    root = root.resolve()
    path = root / value
    current = root
    for part in value.parts:
        current /= part
        if current.is_symlink():
            raise ValueError('Native project directory traverses symlink')
    if not path.is_dir():
        raise ValueError('Native generated project not present: ' + str(path))
    return path


def run(profile: str, directory: Path, timeout: int, execute: bool, challenge: str | None = None) -> dict:
    plan = PROFILES[profile]
    for required in plan.required:
        file = directory / required
        if file.is_symlink() or not file.is_file():
            return {'profile': profile, 'status': 'UNAVAILABLE', 'reason': 'Missing native fixture build file: ' + required,
                    'stages': [], 'provenCapabilities': []}
    stages = []
    for label, argv in plan.stages:
        command = list(argv)
        if os.name == 'nt' and command[0] in ('npm', 'mvn'):
            command[0] += '.cmd'
        if not execute:
            stages.append({'id': label, 'status': 'NOT_EXECUTED', 'command': command})
            continue
        if shutil.which(command[0]) is None:
            stages.append({'id': label, 'status': 'TOOL_UNAVAILABLE', 'tool': command[0]})
            break
        try:
            process = subprocess.run(command, cwd=directory, shell=False, timeout=timeout,
                                     stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, errors='replace',
                                     env={**os.environ, 'HYDRA_PARITY_CHALLENGE': challenge} if challenge else None)
            stages.append({'id': label, 'status': 'PASSED' if process.returncode == 0 else 'FAILED',
                           'exitCode': process.returncode, 'outputTail': process.stdout[-6000:]})
            if process.returncode:
                break
        except subprocess.TimeoutExpired:
            stages.append({'id': label, 'status': 'TIMED_OUT', 'timeoutSeconds': timeout})
            break
        except OSError as error:
            stages.append({'id': label, 'status': 'FAILED', 'error': str(error)})
            break
    passed = bool(stages) and len(stages) == len(plan.stages) and all(s['status'] == 'PASSED' for s in stages)
    return {'profile': profile, 'status': 'PASSED' if passed else 'UNVERIFIED', 'stages': stages,
            'provenCapabilities': list(plan.capability_scope) if passed else [],
            'scope': 'NATIVE_BUILD_AND_PROJECT_TESTS_ONLY',
            'functionalParityCertified': False,
            'limitations': 'Passing native tests does not prove parity for untested HYDRA functions'}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', type=Path, default=ROOT / 'data' / 'generated-projects')
    parser.add_argument('--project', required=True, help='Relative project path below root')
    parser.add_argument('--profile', choices=sorted(PROFILES), required=True)
    parser.add_argument('--execute', action='store_true', help='Explicit opt-in to native build/test commands')
    parser.add_argument('--timeout-seconds', type=int, default=1800)
    parser.add_argument('--report', type=Path)
    args = parser.parse_args()
    if not 1 <= args.timeout_seconds <= 3600:
        parser.error('timeout must be between 1 and 3600 seconds')
    try:
        result = run(args.profile, locate(args.root, args.project), args.timeout_seconds, args.execute)
    except (ValueError, OSError) as error:
        print('NATIVE_E2E=FAIL ' + str(error), file=sys.stderr)
        return 2
    print(json.dumps(result, indent=2, sort_keys=True))
    if args.report:
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(json.dumps(result, indent=2, sort_keys=True) + '\n', encoding='utf-8')
    return 0 if result['status'] == 'PASSED' else 1


if __name__ == '__main__':
    raise SystemExit(main())
