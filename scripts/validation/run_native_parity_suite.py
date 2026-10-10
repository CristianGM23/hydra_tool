#!/usr/bin/env python3
"""Execute native project tests and verify challenge-bound HYDRA semantic parity evidence.

This is an opt-in local/CI developer utility, never a REST command runner.
Passing a generic native build cannot prove semantic parity. Generated-project
native tests must emit fresh, challenge-bound proofs of explicitly listed cases.
"""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import re
import secrets
import sys

from run_native_e2e_matrix import PROFILES, locate, run

CAPABILITIES = frozenset({
    'METAMODEL', 'GRAPHICAL_EDITOR', 'TEXTUAL_EDITOR', 'VALIDATION', 'QUICK_FIX',
    'LANGUAGE_SERVICE_SMOKE', 'GRAPHICAL_BRIDGE_SMOKE', 'NATIVE_BUILD',
    'RUNTIME_LIFECYCLE', 'OPERATION', 'EVENT_FLOW', 'GAMIFICATION',
    'TRANSFORMATION', 'ROUND_TRIP', 'PERSISTENCE',
})
MAX_SCENARIOS = 80
MAX_JSON_BYTES = 128 * 1024
SAFE_ID = re.compile(r'[a-z][a-z0-9_-]{0,63}\Z')
SAFE_SHA = re.compile(r'[0-9a-f]{64}\Z')


def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def json_file(path: Path):
    if not path.is_file() or path.is_symlink() or path.stat().st_size > MAX_JSON_BYTES:
        raise ValueError('Missing, linked, or oversized parity JSON: ' + str(path))
    def reject_constant(value: str):
        raise ValueError('Non-finite JSON numbers are forbidden: ' + value)
    return json.loads(path.read_text(encoding='utf-8'), parse_constant=reject_constant)


def within(root: Path, relative: str, *, require_exists: bool) -> Path:
    if not isinstance(relative, str) or not relative or '\\' in relative or ':' in relative:
        raise ValueError('Parity path must be a portable relative path')
    parts = relative.split('/')
    if any(p in ('', '.', '..') or p.startswith('.') for p in parts):
        raise ValueError('Parity path contains an unsafe segment')
    current = root.resolve()
    for part in parts:
        current = current / part
        if current.is_symlink():
            raise ValueError('Parity path contains a symbolic link')
    if require_exists and not current.is_file():
        raise ValueError('Parity artifact is missing: ' + relative)
    return current


def load_cases(project: Path, relative: str) -> list[dict]:
    document = json_file(within(project, relative, require_exists=True))
    if not isinstance(document, dict) or document.get('schemaVersion') != 1:
        raise ValueError('Unsupported parity scenario schema version')
    cases = document.get('scenarios')
    if not isinstance(cases, list) or not 1 <= len(cases) <= MAX_SCENARIOS:
        raise ValueError('Parity suite must contain 1..80 scenarios')
    seen = set()
    for case in cases:
        if not isinstance(case, dict) or not isinstance(case.get('id'), str) or not SAFE_ID.fullmatch(case['id']):
            raise ValueError('Invalid parity scenario identifier')
        if case['id'] in seen:
            raise ValueError('Duplicate parity scenario id: ' + case['id'])
        seen.add(case['id'])
        if case.get('capability') not in CAPABILITIES:
            raise ValueError('Unsupported parity capability: ' + str(case.get('capability')))
        for name in ('input', 'expected', 'evidence'):
            if not isinstance(case.get(name), str):
                raise ValueError('Missing parity ' + name)
            within(project, case[name], require_exists=name != 'evidence')
        if case['evidence'] in (case['input'], case['expected']):
            raise ValueError('Parity evidence cannot overwrite input or expected result')
        json_file(within(project, case['input'], require_exists=True))
        json_file(within(project, case['expected'], require_exists=True))
    return cases


def strict_json_equal(actual, expected) -> bool:
    """JSON equality preserving number/bool/container types, not Python coercion."""
    if type(actual) is not type(expected):
        return False
    if isinstance(actual, dict):
        return actual.keys() == expected.keys() and all(strict_json_equal(actual[k], expected[k]) for k in actual)
    if isinstance(actual, list):
        return len(actual) == len(expected) and all(strict_json_equal(a, b) for a, b in zip(actual, expected))
    return actual == expected


def certify(profile: str, project: Path, manifest: str, timeout: int, execute: bool) -> dict:
    cases = load_cases(project, manifest)
    nonce = secrets.token_hex(24)
    before = {}
    declared = {case['id']: {label: digest(within(project, case[label], require_exists=True))
                           for label in ('input', 'expected')} for case in cases}
    for case in cases:
        proof = within(project, case['evidence'], require_exists=False)
        if proof.is_file():
            before[case['id']] = (proof.stat().st_mtime_ns, digest(proof))
    native = run(profile, project, timeout, execute, challenge=nonce if execute else None)
    passed_native = native['status'] == 'PASSED' and execute
    results = []
    for case in cases:
        item = {'id': case['id'], 'capability': case['capability'], 'status': 'UNVERIFIED'}
        if not passed_native:
            item['reason'] = 'Native build/tests did not pass in this invocation'
        else:
            path = within(project, case['evidence'], require_exists=False)
            try:
                proof = json_file(path)
                # Prevent old evidence passing a later run; a stale on-disk proof
                # cannot know the fresh challenge generated for this invocation.
                if not isinstance(proof, dict) or proof.get('schemaVersion') != 1 or proof.get('scenarioId') != case['id']:
                    raise ValueError('Evidence schema or scenario mismatch')
                if proof.get('challenge') != nonce:
                    raise ValueError('Stale evidence: challenge does not match this run')
                if not SAFE_SHA.fullmatch(str(proof.get('inputSHA256', ''))):
                    raise ValueError('Missing semantic input binding')
                if proof['inputSHA256'] != digest(within(project, case['input'], require_exists=True)):
                    raise ValueError('Evidence input digest mismatch')
                if case['id'] in before and (path.stat().st_mtime_ns, digest(path)) == before[case['id']]:
                    raise ValueError('Native tests did not refresh evidence')
                for label in ('input', 'expected'):
                    if declared[case['id']][label] != digest(within(project, case[label], require_exists=True)):
                        raise ValueError('Native execution modified declared parity ' + label)
                expected = json_file(within(project, case['expected'], require_exists=True))
                if not strict_json_equal(proof.get('actual'), expected):
                    raise ValueError('Native result differs from canonical expected behavior')
                item['status'] = 'PASSED'
                item['evidenceSHA256'] = digest(path)
            except (ValueError, OSError, UnicodeError, json.JSONDecodeError) as error:
                item['status'] = 'FAILED'
                item['reason'] = str(error)
        results.append(item)
    proven = sorted({r['capability'] for r in results if r['status'] == 'PASSED'})
    all_pass = passed_native and all(r['status'] == 'PASSED' for r in results)
    return {
        'schemaVersion': 1, 'profile': profile, 'native': native,
        'status': 'PASSED' if all_pass else 'UNVERIFIED',
        'certificationScope': 'DECLARED_CHALLENGE_BOUND_SCENARIOS_ONLY',
        'functionalParityCertified': all_pass,
        'certifiedCapabilities': proven,
        'notCertifiedCapabilities': sorted(CAPABILITIES - set(proven)),
        'scenarios': results,
        'limits': 'Only validated scenarios are evidenced; never universal technology parity',
    }


def main() -> int:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--root', type=Path, required=True)
    p.add_argument('--project', required=True)
    p.add_argument('--profile', choices=sorted(PROFILES), required=True)
    p.add_argument('--suite', default='tests/hydra-parity/scenarios.json')
    p.add_argument('--timeout-seconds', type=int, default=900)
    p.add_argument('--execute', action='store_true')
    p.add_argument('--report', type=Path)
    args = p.parse_args()
    if not 1 <= args.timeout_seconds <= 3600:
        p.error('Timeout must be between 1 and 3600 seconds')
    try:
        report = certify(args.profile, locate(args.root, args.project), args.suite, args.timeout_seconds, args.execute)
        if args.report:
            args.report.parent.mkdir(parents=True, exist_ok=True)
            args.report.write_text(json.dumps(report, indent=2, sort_keys=True) + '\n', encoding='utf-8')
        print(json.dumps(report, indent=2, sort_keys=True))
        return 0 if report['status'] == 'PASSED' else 1
    except (ValueError, OSError, KeyError, UnicodeError, json.JSONDecodeError) as error:
        print('BD3_PARITY=FAIL ' + str(error), file=sys.stderr)
        return 2


if __name__ == '__main__':
    raise SystemExit(main())
