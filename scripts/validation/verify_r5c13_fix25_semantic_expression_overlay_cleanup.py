#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
legacy = ROOT / 'src/main/java/com/example/editor/validation/expression'
legacy_tests = ROOT / 'src/test/java/com/example/editor/validation/expression'
semantic = ROOT / 'src/main/java/com/example/editor/semantic/expression'

allowed = {
    'ExpressionModel.java',
    'ExpressionModelApplicationException.java',
    'ExpressionModelApplicationService.java',
    'ExpressionModelCheckReport.java',
    'ExpressionModelLoadResult.java',
    'ExpressionModelProperties.java',
    'ExpressionModelSaveResponse.java',
    'ExpressionModelSummary.java',
}

checks = []
def check(label, condition):
    checks.append((label, bool(condition)))
    print(('PASS' if condition else 'FAIL') + ' - ' + label)

legacy_files = {p.name for p in legacy.glob('*.java')} if legacy.exists() else set()
unexpected = sorted(legacy_files - allowed)
missing = sorted(allowed - legacy_files)

check('validation.expression retains only persistence/application compatibility classes', legacy_files == allowed)
check('no migrated semantic classes remain in validation.expression', not unexpected)
check('all expected compatibility classes remain available', not missing)
check('old validation.expression test package contains no migrated tests', not legacy_tests.exists() or not any(legacy_tests.glob('*.java')))
check('neutral ExpressionTypeChecker exists', (semantic / 'ExpressionTypeChecker.java').is_file())
check('neutral ExpressionDefinition exists', (semantic / 'ExpressionDefinition.java').is_file())
check('neutral ExpressionDiagnostic exists', (semantic / 'ExpressionDiagnostic.java').is_file())
check('neutral ExpressionNode exists', (semantic / 'ExpressionNode.java').is_file())
check('legacy checker is physically absent', not (legacy / 'ExpressionTypeChecker.java').exists())
check('legacy definition is physically absent', not (legacy / 'ExpressionDefinition.java').exists())
check('legacy diagnostic is physically absent', not (legacy / 'ExpressionDiagnostic.java').exists())
check('legacy node is physically absent', not (legacy / 'ExpressionNode.java').exists())

failed = [label for label, ok in checks if not ok]
print(f'SEMANTIC_EXPRESSION_OVERLAY_CLEANUP_FIX25={len(checks)-len(failed)}/{len(checks)} ' + ('PASS' if not failed else 'FAIL'))
if unexpected:
    print('UNEXPECTED_LEGACY_FILES=' + ','.join(unexpected))
if missing:
    print('MISSING_COMPATIBILITY_FILES=' + ','.join(missing))
if failed:
    raise SystemExit(1)
