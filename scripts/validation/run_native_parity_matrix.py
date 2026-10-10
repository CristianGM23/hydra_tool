#!/usr/bin/env python3
"""B4: execute independently declared native parity suites and compare their semantics.

A PASSED matrix is limited to declared scenarios and configured platforms. It
never asserts universal equivalence or executes arbitrary user supplied commands.
"""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import re
import sys

from run_native_e2e_matrix import PROFILES, locate
from run_native_parity_suite import CAPABILITIES, certify, json_file, load_cases, within

SAFE_ID = re.compile(r'[a-z][a-z0-9_-]{0,63}\Z')
MAX_PROFILES = 16
MAX_MATRIX_BYTES = 128 * 1024


def clean_id(value, label):
    if not isinstance(value, str) or not SAFE_ID.fullmatch(value):
        raise ValueError('Invalid ' + label)
    return value


def load_matrix(path: Path) -> dict:
    if path.is_symlink() or not path.is_file() or path.stat().st_size > MAX_MATRIX_BYTES:
        raise ValueError('Matrix specification missing, linked or oversized')
    document = json.loads(path.read_text(encoding='utf-8'))
    if not isinstance(document, dict) or document.get('schemaVersion') != 1:
        raise ValueError('Unknown B4 native parity matrix schema')
    groups = document.get('groups')
    if not isinstance(groups, list) or not 1 <= len(groups) <= 10:
        raise ValueError('Matrix requires 1..10 groups')
    seen_groups = set()
    for group in groups:
        if not isinstance(group, dict):
            raise ValueError('Group must be an object')
        gid = clean_id(group.get('id'), 'group id')
        if gid in seen_groups:
            raise ValueError('Duplicate matrix group id')
        seen_groups.add(gid)
        members = group.get('members')
        if not isinstance(members, list) or not 1 <= len(members) <= MAX_PROFILES:
            raise ValueError('Group must include 1..16 explicitly selected native projects')
        minimum = group.get('minimumPlatforms', 2)
        if type(minimum) is not int or not 2 <= minimum <= MAX_PROFILES or minimum > len(members):
            raise ValueError('Cross-platform parity requires at least two distinct members')
        required = group.get('requiredCapabilities')
        if not isinstance(required, list) or not required or len(required) != len(set(map(str, required))):
            raise ValueError('Required capabilities must be unique and nonempty')
        if any(not isinstance(c, str) or c not in CAPABILITIES for c in required):
            raise ValueError('Unknown required HYDRA capability')
        seen_members = set()
        seen_profiles = set()
        for m in members:
            if not isinstance(m, dict):
                raise ValueError('Native member must be an object')
            mid = clean_id(m.get('id'), 'member id')
            if mid in seen_members:
                raise ValueError('Duplicate platform member id')
            seen_members.add(mid)
            if m.get('profile') not in PROFILES:
                raise ValueError('Unsupported native execution profile')
            if m['profile'] in seen_profiles:
                raise ValueError('Cross-technology matrix requires distinct native profiles')
            seen_profiles.add(m['profile'])
            if any(not isinstance(m.get(k), str) or not m[k] for k in ('project', 'suite')):
                raise ValueError('Native member requires project and suite paths')
    return document


def suite_semantics(project: Path, suite: str) -> tuple[dict, set]:
    cases = load_cases(project, suite)
    baseline = {}
    for case in cases:
        canonical = json_file(within(project, case['expected'], require_exists=True))
        # Parsed canonical JSON equality is intentional: irrelevant whitespace
        # and key ordering in expected fixtures must not reduce parity.
        encoded = json.dumps(canonical, sort_keys=True, separators=(',', ':'), ensure_ascii=False, allow_nan=False)
        baseline[case['id']] = (case['capability'], hashlib.sha256(encoded.encode('utf-8')).hexdigest())
    return baseline, {c['capability'] for c in cases}


def run_matrix(root: Path, specification: dict, timeout: int, execute: bool) -> dict:
    if not 1 <= timeout <= 3600:
        raise ValueError('Timeout outside 1..3600 seconds')
    results = []
    for group in specification['groups']:
        required = set(group['requiredCapabilities'])
        baseline: dict | None = None
        members = []
        disagreement = []
        count_validated = 0
        for m in group['members']:
            project = locate(root, m['project'])
            semantics, declared = suite_semantics(project, m['suite'])
            if baseline is None:
                baseline = semantics
            else:
                for scenario in sorted(set(baseline) | set(semantics)):
                    if scenario not in baseline or scenario not in semantics:
                        disagreement.append(f'{m["id"]}: missing/extra scenario {scenario}')
                    elif baseline[scenario] != semantics[scenario]:
                        disagreement.append(f'{m["id"]}: semantic expectation differs for {scenario}')
            try:
                report = certify(m['profile'], project, m['suite'], timeout, execute)
                passed = (execute and report['status'] == 'PASSED' and report.get('functionalParityCertified') is True)
                proven = set(report.get('certifiedCapabilities', [])) if passed else set()
                status = 'PASSED' if passed and required <= proven else 'UNVERIFIED'
                failures = [c['id'] for c in report.get('scenarios', []) if c['status'] != 'PASSED']
                # Include only cryptographic digests of challenge-bound native proofs.
                # They are used by B5 to enforce per-capability coverage and do
                # not expose untrusted subprocess output or confidential models.
                scenarios = report.get('scenarios', []) if passed else []
                proofs = []
                for scenario in scenarios:
                    if not isinstance(scenario, dict) or scenario.get('status') != 'PASSED':
                        continue
                    case = semantics.get(scenario.get('id'))
                    proof_hash = scenario.get('evidenceSHA256')
                    if case and isinstance(proof_hash, str) and re.fullmatch(r'[0-9a-f]{64}', proof_hash):
                        proofs.append({'id': scenario['id'], 'capability': case[0], 'evidenceSHA256': proof_hash})
                # Deliberately omit raw subprocess stdout, possibly sensitive.
                member = {'id': m['id'], 'profile': m['profile'], 'status': status,
                          'declaredCapabilities': sorted(declared), 'certifiedCapabilities': sorted(proven),
                          'missingCapabilities': sorted(required - proven),
                          'failedScenarios': failures, 'nativeStatus': report['native']['status'],
                          'scenarioEvidence': sorted(proofs, key=lambda p: p['id'])}
            except (OSError, ValueError, KeyError) as err:
                member = {'id': m['id'], 'profile': m['profile'], 'status': 'UNVERIFIED',
                          'declaredCapabilities': sorted(declared), 'certifiedCapabilities': [],
                          'missingCapabilities': sorted(required), 'failedScenarios': [],
                          'nativeStatus': 'UNVERIFIED', 'scenarioEvidence': [], 'reason': type(err).__name__}
            members.append(member)
            if member['status'] == 'PASSED':
                count_validated += 1
        complete = execute and not disagreement and count_validated == len(members) and count_validated >= group['minimumPlatforms']
        results.append({'id': group['id'], 'status': 'PASSED' if complete else 'UNVERIFIED',
                        'scope': 'DECLARED_SHARED_SCENARIOS_ONLY', 'requiredCapabilities': sorted(required),
                        'platformsCertified': count_validated, 'minimumPlatforms': group['minimumPlatforms'],
                        'semanticDisagreements': sorted(set(disagreement)),
                        'scenarioIds': sorted(baseline or {}), 'members': members})
    good = bool(results) and all(g['status'] == 'PASSED' for g in results)
    return {'schemaVersion': 1, 'status': 'PASSED' if good else 'UNVERIFIED',
            'certificationScope': 'DECLARED_NATIVE_PROJECTS_AND_SCENARIOS_ONLY',
            'universalTechnologyParityCertified': False, 'groups': results}


def main() -> int:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--root', type=Path, required=True)
    p.add_argument('--matrix', type=Path, required=True)
    p.add_argument('--timeout-seconds', type=int, default=900)
    p.add_argument('--execute', action='store_true', help='Explicitly run allow-listed build/test tools')
    p.add_argument('--report', type=Path)
    args = p.parse_args()
    try:
        report = run_matrix(args.root, load_matrix(args.matrix), args.timeout_seconds, args.execute)
        if args.report:
            args.report.parent.mkdir(parents=True, exist_ok=True)
            args.report.write_text(json.dumps(report, indent=2, sort_keys=True) + '\n', encoding='utf-8')
        print(json.dumps(report, indent=2, sort_keys=True))
        return 0 if report['status'] == 'PASSED' else 1
    except (ValueError, OSError, KeyError, UnicodeError, json.JSONDecodeError) as exc:
        print('BD4_NATIVE_PARITY_MATRIX=FAIL ' + str(exc), file=sys.stderr)
        return 2


if __name__ == '__main__':
    raise SystemExit(main())
