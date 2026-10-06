from __future__ import annotations

import argparse
import json
import os
import subprocess
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'scripts/release'))
from qualification_engine import workspace_fingerprint  # noqa: E402
parser = argparse.ArgumentParser(description='Valida el árbol actual de HYDRA por capacidades, sin snapshots históricos.')
parser.add_argument('--ci', action='store_true')
parser.add_argument('--structural-only', action='store_true')
args = parser.parse_args()
steps: list[dict[str, object]] = []


def run(name: str, cmd: list[str], *, cwd: Path = ROOT, required: bool = True,
        timeout: int = 1200, facet: str | None = None) -> bool:
    started = time.time()
    print('START', name, f'timeout={timeout}s', flush=True)
    try:
        environment = os.environ.copy()
        environment.setdefault('PYTHONUTF8', '1')
        environment.setdefault('PYTHONIOENCODING', 'utf-8')
        environment.setdefault('PYTHONUNBUFFERED', '1')
        result = subprocess.run(cmd, cwd=cwd, text=True, capture_output=True, timeout=timeout, env=environment)
        passed = result.returncode == 0
        output = (result.stdout + '\n' + result.stderr).strip()
    except Exception as exc:  # gate boundary: preserve diagnostic in readiness report
        passed = False
        output = str(exc)
    steps.append({
        'name': name,
        'facet': facet or name,
        'passed': passed,
        'required': required,
        'seconds': round(time.time() - started, 3),
        'outputTail': '\n'.join(output.splitlines()[-80:]),
    })
    print(('PASS' if passed else 'FAIL'), name)
    return passed or not required


def structural() -> bool:
    gates: list[tuple[str, list[str], str, Path]] = [
        ('Pre-GA source hygiene', [sys.executable, 'scripts/validation/verify_pre_ga_hygiene.py'], 'readiness', ROOT),
        ('Fix82-85 lifecycle/persistence/jobs consolidation', [sys.executable, 'scripts/validation/verify_r5c13_fix82_85_consolidation.py'], 'readiness', ROOT),
        ('Fix85T project picker context recovery', [sys.executable, 'scripts/validation/verify_r5c13_fix85t_project_path_picker_context.py'], 'readiness', ROOT),
        ('Fix85U project picker bootstrap hardening', [sys.executable, 'scripts/validation/verify_r5c13_fix85u_project_path_picker_bootstrap.py'], 'readiness', ROOT),
        ('Fix86-91 platform evolution', [sys.executable, 'scripts/validation/verify_r5c13_fix86_91_platform_evolution.py'], 'readiness', ROOT),
        ('Fix92-98 platform hardening', [sys.executable, 'scripts/validation/verify_r5c13_fix92_98_platform_hardening.py'], 'readiness', ROOT),
        ('Fix99-100 release closure', [sys.executable, 'scripts/validation/verify_r5c13_fix99_100_release_closure.py'], 'readiness', ROOT),
        ('Fix101-107 RC1 product qualification', [sys.executable, 'scripts/validation/verify_r5c13_fix101_107_rc1.py'], 'readiness', ROOT),
        ('Fix108 qualification bootstrap', [sys.executable, 'scripts/validation/verify_r5c13_fix108_qualification_bootstrap.py'], 'readiness', ROOT),
        ('Fix109 Windows toolchain resolution', [sys.executable, 'scripts/validation/verify_r5c13_fix109_windows_toolchain_resolution.py'], 'readiness', ROOT),
        ('Fix110 Java 24/25 qualification', [sys.executable, 'scripts/validation/verify_r5c13_fix110_java24_25_qualification.py'], 'readiness', ROOT),
        ('Fix111-117 RC2 stabilization', [sys.executable, 'scripts/validation/verify_r5c13_fix111_117_rc2_stabilization.py'], 'readiness', ROOT),
        ('No visual release/schema versions', [sys.executable, 'scripts/validation/verify_no_visual_versions.py'], 'frontend', ROOT),
        ('P0/P1 product contracts', [sys.executable, 'scripts/validation/verify_p01_contracts.py'], 'backend', ROOT),
        ('Core metamodel contracts', [sys.executable, 'scripts/validation/verify_block0_contracts.py'], 'backend', ROOT),
        ('Expression capability', [sys.executable, 'scripts/validation/verify_block1_expressions.py'], 'backend', ROOT),
        ('Validation capability', [sys.executable, 'scripts/validation/verify_block2_validations.py'], 'backend', ROOT),
        ('Action and Quick Fix capability', [sys.executable, 'scripts/validation/verify_block3_actions.py'], 'backend', ROOT),
        ('Validation/Quick Fix UI', [sys.executable, 'scripts/validation/verify_blocks4_6_ui.py'], 'frontend', ROOT),
        ('Validation help and accessibility', [sys.executable, 'scripts/validation/verify_validation_help_ui.py'], 'a11y', ROOT),
        ('Operations core', [sys.executable, 'scripts/validation/verify_operations_o0_o2.py'], 'backend', ROOT),
        ('Operations persistence/runtime', [sys.executable, 'scripts/validation/verify_operations_o3_o5.py'], 'backend', ROOT),
        ('Integration capability', [sys.executable, 'scripts/validation/verify_integration_i0.py'], 'backend', ROOT),
        ('GLSP/Eclipse transactional runtime lifecycle', [sys.executable, 'scripts/validation/verify_glsp_eclipse_runtime_lifecycle.py'], 'backend', ROOT),
        ('Xtext/Langium transactional runtime lifecycle', [sys.executable, 'scripts/validation/verify_language_host_runtime_lifecycle.py'], 'backend', ROOT),
        ('Sirius transactional runtime lifecycle', [sys.executable, 'scripts/validation/verify_sirius_runtime_lifecycle.py'], 'backend', ROOT),
        ('EMF transactional runtime lifecycle', [sys.executable, 'scripts/validation/verify_emf_runtime_lifecycle.py'], 'backend', ROOT),
        ('Portable host transactional runtime lifecycle', [sys.executable, 'scripts/validation/verify_portable_host_runtime_lifecycle.py'], 'backend', ROOT),
        ('Generated portable host project lifecycle suites', [sys.executable, 'scripts/validation/verify_generated_portable_host_project_tests.py'], 'backend', ROOT),
        ('mxGraph/draw.io injectable', [sys.executable, 'scripts/validation/verify_mxgraph_drawio_injectable.py'], 'backend', ROOT),
        ('Editor runtime capability', [sys.executable, 'scripts/validation/verify_editor_runtime_b2_phase1.py'], 'frontend', ROOT),
        ('Editor runtime and grammar semantic contexts', [sys.executable, 'scripts/validation/verify_c5_runtime_semantic_context.py'], 'frontend', ROOT),
        ('Grammar-first semantic layers', [sys.executable, 'scripts/validation/verify_c6_grammar_first_semantic_layers.py'], 'backend', ROOT),
        ('Reported semantic/frontend test contracts', [sys.executable, 'scripts/validation/verify_c7_semantic_context_test_contracts.py'], 'backend', ROOT),
        ('Model editor React Flow selection-loop closure', [sys.executable, 'scripts/validation/verify_c8_model_editor_selection_loop.py'], 'frontend', ROOT),
        ('Editor definition Vitest timeout budget', [sys.executable, 'scripts/validation/verify_c9_editor_definition_timeout_budget.py'], 'frontend', ROOT),
        ('Semantic runtime execution closure', [sys.executable, 'scripts/validation/verify_c10_semantic_runtime_execution.py'], 'backend', ROOT),
        ('Semantic export and audit closure', [sys.executable, 'scripts/validation/verify_c11_semantic_export_audit.py'], 'backend', ROOT),
        ('Events and editor definitions', [sys.executable, 'scripts/validation/verify_events_editors_b0_b1.py'], 'frontend', ROOT),
        ('Editor definition classpath resources', [sys.executable, 'scripts/validation/verify_editor_definition_contract_resources.py'], 'backend', ROOT),
        ('Unified designer UI', [sys.executable, 'scripts/validation/verify_unified_designer_ui.py'], 'frontend', ROOT),
        ('External project recovery', [sys.executable, 'scripts/validation/verify_external_project_recovery.py'], 'recovery', ROOT),
        ('External multilayer import', [sys.executable, 'scripts/validation/verify_external_multilayer_import.py'], 'recovery', ROOT),
        ('Navigation and repository security', [sys.executable, 'scripts/validation/verify_navigation_repository_security.py'], 'security', ROOT),
        ('Frontend architecture', [sys.executable, 'scripts/validation/verify_frontend_architecture.py'], 'frontend', ROOT),
        ('Frontend control convergence', [sys.executable, 'scripts/validation/verify_frontend_control_convergence.py'], 'frontend', ROOT),
        ('Spanish/i18n complete tree', [sys.executable, 'scripts/validation/verify_hydra_i18n_full.py'], 'i18n', ROOT),
        ('Spanish visible/runtime closure', ['node', 'scripts/validation/verify_frontend_spanish_coverage.cjs'], 'i18n', ROOT),
        ('Concurrency/state budgets', [sys.executable, 'scripts/validation/verify_concurrency_state_budgets.py'], 'backend', ROOT),
        ('HTTP route uniqueness', [sys.executable, 'scripts/validation/verify_update_site_route_uniqueness.py'], 'api', ROOT),
        ('Java core smoke', [sys.executable, 'scripts/validation/verify_java_p01_core.py'], 'backend', ROOT),
        ('Backend Spanish method documentation', [sys.executable, 'scripts/validation/verify_backend_spanish_method_docs.py'], 'backend', ROOT),
        ('Backend warning/debt audit', [sys.executable, 'scripts/validation/verify_backend_warning_debt.py'], 'backend', ROOT),
        ('Backend contract convergence', [sys.executable, 'scripts/validation/verify_backend_contract_convergence.py'], 'backend', ROOT),
        ('Reported JDK24 contract closure', [sys.executable, 'scripts/validation/verify_reported_jdk24_contract_closure.py'], 'backend', ROOT),
        ('Backend compiler hotfix', [sys.executable, 'scripts/validation/verify_backend_compiler_hotfix.py'], 'backend', ROOT),
        ('Maven/IDE output isolation', [sys.executable, 'scripts/validation/verify_maven_output_isolation.py'], 'toolchain', ROOT),
        ('Frontend index benchmark', ['node', 'scripts/validation/benchmark_frontend_indexes.mjs'], 'performance', ROOT),
        ('Frontend accessibility', ['node', 'scripts/check-accessibility.mjs'], 'a11y', ROOT / 'frontend'),
        ('CSS budgets', ['node', 'scripts/check-css-budgets.mjs'], 'a11y', ROOT / 'frontend'),
        ('OpenAPI/client convergence', [sys.executable, 'scripts/api/verify_openapi.py'], 'api', ROOT),
        ('Exception boundaries', [sys.executable, 'scripts/validation/audit_exception_boundaries.py'], 'observability', ROOT),
        ('External process security', [sys.executable, 'scripts/security/verify_external_process_security.py'], 'security', ROOT),
        ('Current invariants', [sys.executable, 'scripts/validation/verify_invariants.py'], 'readiness', ROOT),
        ('Release metadata', [sys.executable, 'scripts/release/verify_release_metadata.py'], 'license', ROOT),
    ]
    passed = True
    for name, cmd, facet, cwd in gates:
        passed &= run(name, cmd, cwd=cwd, facet=facet)
    return bool(passed)


structural_ok = structural()
full_ok = structural_ok
if not args.structural_only:
    full_ok &= run('Toolchain/Java 24-25 prerequisites', [sys.executable, 'scripts/release/verify_toolchain.py', '--strict', '--full'], facet='toolchain')
    full_ok &= run('TypeScript full compile', ['node', './node_modules/typescript/bin/tsc', '-b', '--pretty', 'false'], cwd=ROOT / 'frontend', facet='build')
    full_ok &= run('ESLint full tree', ['node', './node_modules/eslint/bin/eslint.js', 'src', '--max-warnings=0'], cwd=ROOT / 'frontend', facet='lint')
    full_ok &= run('Frontend production build', ['npm.cmd', 'run', 'build'] if os.name == 'nt' else ['npm', 'run', 'build'], cwd=ROOT / 'frontend', facet='bundle')
    full_ok &= run('Vitest full suite', ['node', 'scripts/hydra-test-runner.mjs', 'full'], cwd=ROOT / 'frontend', timeout=7200, facet='tests')
    full_ok &= run('Axe component suite', ['node', './node_modules/vitest/vitest.mjs', 'run', 'src/accessibility/axeAccessibility.test.tsx'], cwd=ROOT / 'frontend', facet='a11y')
    full_ok &= run('Playwright keyboard/focus/zoom suite', ['npm.cmd', 'run', 'test:a11y:e2e'] if os.name == 'nt' else ['npm', 'run', 'test:a11y:e2e'], cwd=ROOT / 'frontend', timeout=1800, facet='a11y')
    full_ok &= run('Maven full verify', ['mvnw.cmd', '-B', 'clean', 'verify'] if os.name == 'nt' else ['./mvnw', '-B', 'clean', 'verify'], facet='backend')

status = 'STRUCTURAL_PASS' if args.structural_only and structural_ok else ('PASS' if full_ok else 'FAIL')
facets: dict[str, dict[str, object]] = {}
for step in steps:
    facet = str(step['facet'])
    entry = facets.setdefault(facet, {'passed': True, 'executed': 0, 'failedSteps': []})
    entry['executed'] = int(entry['executed']) + 1
    if bool(step['required']) and not bool(step['passed']):
        entry['passed'] = False
        cast_failed = entry['failedSteps']
        assert isinstance(cast_failed, list)
        cast_failed.append(step['name'])
for name in ['build', 'tests', 'lint', 'bundle', 'backend', 'i18n', 'a11y', 'api', 'security', 'license', 'readiness', 'performance', 'recovery', 'observability', 'toolchain']:
    facets.setdefault(name, {'passed': None, 'executed': 0, 'failedSteps': []})

out = ROOT / 'target/release/release-readiness.json'
out.parent.mkdir(parents=True, exist_ok=True)
version = json.loads((ROOT / 'config/hydra-version.json').read_text(encoding='utf-8'))['version']
fingerprint = workspace_fingerprint({'gate': 'final-tree', 'structuralOnly': args.structural_only, 'version': version})
out.write_text(json.dumps({
    'schemaVersion': '2.1',
    'version': version,
    'status': status,
    'structuralOnly': args.structural_only,
    'fingerprint': fingerprint,
    'fullReleasePass': status == 'PASS',
    'facets': facets,
    'steps': steps,
}, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')
print('FINAL_TREE_GATE=' + status)
print(out)
sys.exit(0 if (structural_ok if args.structural_only else full_ok) else 1)
