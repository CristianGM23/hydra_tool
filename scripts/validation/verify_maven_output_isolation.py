#!/usr/bin/env python3
"""Regression gate for CLI Maven/IDE output isolation on Windows workspaces."""
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[2]
checks: list[tuple[str, bool]] = []

def check(label: str, ok: bool) -> None:
    checks.append((label, bool(ok)))
    print(('PASS' if ok else 'FAIL') + ' - ' + label)

pom = (ROOT / 'pom.xml').read_text(encoding='utf-8')
config = (ROOT / '.mvn' / 'maven.config').read_text(encoding='utf-8')
clean = (ROOT / 'scripts' / 'clean-target-windows.ps1').read_text(encoding='utf-8')
runner = (ROOT / 'scripts' / 'testing' / 'print_surefire_failures.py').read_text(encoding='utf-8')
validator = (ROOT / 'VALIDAR_HYDRA_V4.32.107_WINDOWS.cmd').read_text(encoding='utf-8')
final_tree = (ROOT / 'scripts' / 'validation' / 'verify_final_tree.py').read_text(encoding='utf-8')

check('POM exposes configurable Maven build directory', '<hydra.build.directory>target</hydra.build.directory>' in pom)
check('POM binds project build directory to HYDRA property', '<directory>${hydra.build.directory}</directory>' in pom)
check('CLI Maven output is isolated from target/classes', '-Dhydra.build.directory=build/hydra-maven' in config)
check('CLI output does not live below target', 'target/' not in config.replace('target/classes',''))
check('Windows cleanup knows isolated CLI output', 'build/hydra-maven' in clean)
check('Surefire failure helper checks isolated reports', 'build/hydra-maven/surefire-reports' in runner)
check('Windows validator performs Maven output preflight', 'verify_maven_output_isolation.py' in validator)
check('final structural tree retains Maven output isolation gate', "('Maven/IDE output isolation', [sys.executable, 'scripts/validation/verify_maven_output_isolation.py']" in final_tree)
# Hardened Windows (NoDefaultCurrentDirectoryInExePath=1) never resolves a bare 'mvnw.cmd' from the working directory.
launchers = ['scripts/validation/verify_final_tree.py', 'scripts/release/qualify_release.py',
             'scripts/benchmarks/run_rc1_scale.py', 'scripts/release/verify_installation_matrix.py']
bare = [rel for rel in launchers if re.search(r"\[\s*'mvnw\.cmd'", (ROOT / rel).read_text(encoding='utf-8'))]
check('release gates launch the Maven wrapper by absolute path', not bare)
# The full backend suite takes over 20 minutes on supported hardware; it must not inherit the 1200 s default step timeout.
check('final-tree Maven full verify has its own time budget', re.search(r"run\('Maven full verify',[^\n]*timeout=\d+", final_tree) is not None)

# Every class reported by the 2026-09-18 disappearing-class incident must still be source-backed.
incident = [
    'com/example/editor/metamodel/lifecycle/MetamodelDeletionImpact.java',
    'com/example/editor/metamodel/lifecycle/MetamodelDeletionResult.java',
    'com/example/editor/metamodel/projection/MetamodelProjectionException.java',
    'com/example/editor/metamodel/projection/MetamodelProjectionProperties.java',
    'com/example/editor/metamodel/validation/MetamodelValidationProperties.java',
    'com/example/editor/metamodel/validation/MetamodelValidationAdvisor.java',
    'com/example/editor/model/ModelDocumentController.java',
    'com/example/editor/model/ModelSessionProperties.java',
    'com/example/editor/performance/CapabilityPerformanceProperties.java',
    'com/example/editor/persistence/CanonicalDocumentStore.java',
    'com/example/editor/metamodel/importing/updatesite/UpdateSiteRemoteSnapshotService.java',
    'com/example/editor/metamodel/layout/routing/MetamodelPortAssignmentEngine.java',
    'com/example/editor/metamodel/layout/routing/MetamodelEdgeRoutingEngine.java',
    'com/example/editor/metamodel/layout/routing/MetamodelEdgeLabelPlacementEngine.java',
]
check('representative disappearing classes remain present as Java sources', all((ROOT/'src/main/java'/item).is_file() for item in incident))

# The apparent generic-bound errors are cascading if these implementation relationships remain intact.
routing = {
    'DynamicMetamodelPortAssignmentEngine.java': 'implements MetamodelPortAssignmentEngine',
    'OrthogonalMetamodelEdgeRoutingEngine.java': 'implements MetamodelEdgeRoutingEngine',
    'LongestSegmentMetamodelEdgeLabelPlacementEngine.java': 'implements MetamodelEdgeLabelPlacementEngine',
}
check('routing implementations still satisfy their interfaces', all(
    expected in (ROOT/'src/main/java/com/example/editor/metamodel/layout/routing'/name).read_text(encoding='utf-8')
    for name, expected in routing.items()
))

passed = sum(ok for _, ok in checks)
print(f'MAVEN_OUTPUT_ISOLATION={passed}/{len(checks)}')
raise SystemExit(0 if passed == len(checks) else 1)
