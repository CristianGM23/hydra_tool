#!/usr/bin/env python3
"""B5: policy-driven native parity portfolio with fail-closed capability coverage.

The only route that can return CERTIFIED is --execute with an actual B4 runner
invocation in the same process. Evaluating a saved report is always a DIAGNOSTIC,
never a substitute for executing trusted native projects afresh.
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path
import re
import sys

from run_native_parity_matrix import load_matrix, run_matrix
from run_native_parity_suite import CAPABILITIES
from run_native_e2e_matrix import PROFILES

SAFE = re.compile(r'[a-z][a-z0-9_-]{0,63}\Z')
MAX_BYTES = 128 * 1024


def read_policy(path: Path) -> dict:
    if path.is_symlink() or not path.is_file() or path.stat().st_size > MAX_BYTES:
        raise ValueError('Policy missing, linked or oversized')
    policy = json.loads(path.read_text(encoding='utf-8'))
    if not isinstance(policy, dict) or policy.get('schemaVersion') != 1:
        raise ValueError('Unsupported B5 policy schema')
    groups = policy.get('groups')
    if not isinstance(groups, list) or not 1 <= len(groups) <= 10:
        raise ValueError('Policy must describe 1..10 matrix groups')
    ids = set()
    for group in groups:
        if not isinstance(group, dict) or not isinstance(group.get('id'), str) or not SAFE.fullmatch(group['id']):
            raise ValueError('Invalid policy group id')
        if group['id'] in ids:
            raise ValueError('Duplicate policy group')
        ids.add(group['id'])
        profiles = group.get('requiredProfiles')
        if not isinstance(profiles, list) or not 2 <= len(profiles) <= 16 or any(not isinstance(p, str) or p not in PROFILES for p in profiles) or len(set(profiles)) != len(profiles):
            raise ValueError('Policy requires unique profiles for at least two technologies')
        coverage = group.get('capabilityCoverage')
        if not isinstance(coverage, dict) or not coverage or len(coverage) > len(CAPABILITIES):
            raise ValueError('Policy requires capability coverage rules')
        for cap, minimum in coverage.items():
            if cap not in CAPABILITIES or type(minimum) is not dict:
                raise ValueError('Unknown capability or invalid coverage rule')
            expected_keys = {'minProfiles', 'minScenarios'}
            if set(minimum) != expected_keys:
                raise ValueError('Coverage rule must have minProfiles and minScenarios')
            if type(minimum['minProfiles']) is not int or not 2 <= minimum['minProfiles'] <= len(profiles):
                raise ValueError('Coverage needs at least two distinct native technologies')
            if type(minimum['minScenarios']) is not int or not 1 <= minimum['minScenarios'] <= 80:
                raise ValueError('Coverage needs a bounded positive number of executed scenarios')
        if group.get('requireAllMembers', True) is not True:
            raise ValueError('Partial-member certification is forbidden in B5')
    return policy


def assess(policy: dict, native_report: dict, *, executed: bool) -> dict:
    """Check an already produced report without treating it as trusted evidence."""
    if native_report.get('schemaVersion') != 1 or not isinstance(native_report.get('groups'), list):
        raise ValueError('Unsupported B4 matrix report')
    indexed = {group.get('id'): group for group in native_report['groups'] if isinstance(group, dict)}
    result = []
    for requirement in policy['groups']:
        matrix = indexed.get(requirement['id'])
        reasons = []
        if matrix is None:
            result.append({'id':requirement['id'], 'status':'BLOCKED','reasons':['Native matrix group not found'], 'coverage':{}})
            continue
        members = matrix.get('members', [])
        by_profile = {m.get('profile'): m for m in members if isinstance(m, dict)}
        target_profiles = requirement['requiredProfiles']
        if len(by_profile) != len(members):
            reasons.append('Duplicate or malformed native members')
        if set(by_profile) != set(target_profiles):
            reasons.append('Native platforms differ from required technology profiles')
        if native_report.get('status') != 'PASSED' or matrix.get('status') != 'PASSED' or matrix.get('semanticDisagreements'):
            reasons.append('Native equivalence group has not passed')
        scenario_set = matrix.get('scenarioIds')
        if not isinstance(scenario_set, list) or any(not isinstance(sid, str) for sid in scenario_set) or len(set(scenario_set)) != len(scenario_set):
            reasons.append('Missing or duplicated scenario inventory in fresh parity report')
            scenario_set = []
        declared_ids = set(scenario_set)
        coverage = {}
        for cap, minimum in requirement['capabilityCoverage'].items():
            passing = 0
            counts = {}
            for profile in target_profiles:
                member = by_profile.get(profile, {})
                # Proofs are only exposed by B4 if the native execution really
                # ran and its challenge-bound per-scenario verification passed.
                proofs = member.get('scenarioEvidence', [])
                unique = set()
                if isinstance(proofs, list):
                    for proof in proofs:
                        if not isinstance(proof, dict) or proof.get('capability') != cap:
                            continue
                        sid, digest = proof.get('id'), proof.get('evidenceSHA256')
                        if isinstance(sid,str) and sid in declared_ids and isinstance(digest,str) and re.fullmatch(r'[0-9a-f]{64}',digest):
                            unique.add(sid)
                counts[profile] = len(unique)
                if member.get('status') == 'PASSED' and cap in member.get('certifiedCapabilities', []) and len(unique) >= minimum['minScenarios']:
                    passing += 1
            coverage[cap] = {'profilesPassing':passing,'requiredProfiles':minimum['minProfiles'],
                             'minimumScenariosPerProfile':minimum['minScenarios'],'provenScenariosByProfile':counts,
                             'status':'PASSED' if passing >= minimum['minProfiles'] else 'BLOCKED'}
            if passing < minimum['minProfiles']:
                reasons.append('Insufficient semantic evidence for capability '+cap)
        if any(by_profile.get(profile,{}).get('status')!='PASSED' for profile in target_profiles):
            reasons.append('At least one required native profile is not certified')
        ok = not reasons
        result.append({'id':requirement['id'], 'status':'PASSED' if ok else 'BLOCKED',
                       'reasons':sorted(set(reasons)), 'coverage':coverage})
    complete = bool(result) and all(item['status']=='PASSED' for item in result)
    return {'schemaVersion':1, 'status':'CERTIFIED' if (complete and executed) else 'NOT_CERTIFIED',
            'reportType':'EXECUTED_NATIVE_PARITY' if executed else 'UNTRUSTED_REPORT_DIAGNOSTIC',
            'universalTechnologyParityCertified':False,
            'qualificationScope':'ONLY_EXPLICITLY_DECLARED_TECHNOLOGIES_AND_SCENARIOS',
            'groups':result}


def main() -> int:
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--policy',type=Path,required=True)
    parser.add_argument('--matrix',type=Path)
    parser.add_argument('--root',type=Path)
    parser.add_argument('--execute',action='store_true')
    parser.add_argument('--from-report',type=Path,
                        help='Diagnostic only; report files are not trusted native evidence')
    parser.add_argument('--timeout-seconds',type=int,default=900)
    parser.add_argument('--report',type=Path)
    args=parser.parse_args()
    try:
        policy=read_policy(args.policy)
        if args.from_report:
            if args.execute or args.matrix or args.root:
                raise ValueError('Diagnostic from-report cannot be combined with executable matrix')
            path=args.from_report
            if path.is_symlink() or not path.is_file() or path.stat().st_size > 2*1024*1024:
                raise ValueError('Untrusted report missing, linked or oversized')
            native=json.loads(path.read_text(encoding='utf-8'))
            executed=False
        else:
            if not args.matrix or not args.root:
                raise ValueError('Supply --root and --matrix to run or inspect native profiles')
            native=run_matrix(args.root,load_matrix(args.matrix),args.timeout_seconds,args.execute)
            executed=args.execute
        report=assess(policy,native,executed=executed)
        if args.report:
            args.report.parent.mkdir(parents=True,exist_ok=True)
            args.report.write_text(json.dumps(report,indent=2,sort_keys=True)+'\n',encoding='utf-8')
        print(json.dumps(report,indent=2,sort_keys=True))
        return 0 if report['status']=='CERTIFIED' else 1
    except (OSError,ValueError,KeyError,TypeError,UnicodeError,json.JSONDecodeError) as exc:
        print('BD5_NATIVE_PORTFOLIO=FAIL '+str(exc),file=sys.stderr)
        return 2

if __name__=='__main__':
    raise SystemExit(main())
