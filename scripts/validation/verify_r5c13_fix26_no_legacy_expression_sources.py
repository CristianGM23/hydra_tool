from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[2]
legacy_main = ROOT / 'src/main/java/com/example/editor/validation/expression'
legacy_test = ROOT / 'src/test/java/com/example/editor/validation/expression'
semantic_main = ROOT / 'src/main/java/com/example/editor/semantic/expression'

allowed_main = {
    'ExpressionModel.java',
    'ExpressionModelApplicationException.java',
    'ExpressionModelApplicationService.java',
    'ExpressionModelCheckReport.java',
    'ExpressionModelLoadResult.java',
    'ExpressionModelProperties.java',
    'ExpressionModelSaveResponse.java',
    'ExpressionModelSummary.java',
}

failures = []
actual_main = {p.name for p in legacy_main.glob('*.java')} if legacy_main.exists() else set()
unexpected = sorted(actual_main - allowed_main)
missing_allowed = sorted(allowed_main - actual_main)
if unexpected:
    failures.append('legacy validation.expression semantic sources remain: ' + ', '.join(unexpected))
if missing_allowed:
    failures.append('expected compatibility/persistence sources missing: ' + ', '.join(missing_allowed))

legacy_tests = sorted(p.name for p in legacy_test.glob('*.java')) if legacy_test.exists() else []
if legacy_tests:
    failures.append('legacy validation.expression tests remain: ' + ', '.join(legacy_tests))

semantic_checker = semantic_main / 'ExpressionTypeChecker.java'
if not semantic_checker.exists():
    failures.append('semantic.expression.ExpressionTypeChecker is missing')

all_checkers = sorted(ROOT.glob('src/**/ExpressionTypeChecker.java'))
checker_paths = [p.relative_to(ROOT).as_posix() for p in all_checkers]
expected_checker = 'src/main/java/com/example/editor/semantic/expression/ExpressionTypeChecker.java'
if checker_paths != [expected_checker]:
    failures.append('ExpressionTypeChecker must exist only in semantic.expression; found: ' + ', '.join(checker_paths))

checks = [
    ('semantic checker exists', semantic_checker.exists()),
    ('legacy main package contains only persistence/compatibility files', not unexpected and not missing_allowed),
    ('legacy expression tests removed', not legacy_tests),
    ('only one ExpressionTypeChecker source remains', checker_paths == [expected_checker]),
]

for label, ok in checks:
    print(('PASS ' if ok else 'FAIL ') + label)

if failures:
    for failure in failures:
        print('ERROR ' + failure)
    sys.exit(1)

print(f'SEMANTIC_EXPRESSION_SOURCE_CLEANUP_FIX26={len(checks)}/{len(checks)} PASS')
