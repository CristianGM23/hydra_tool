from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[2]
checks = []

def text(path):
    return (ROOT / path).read_text(encoding='utf-8')

def check(label, condition):
    checks.append((label, bool(condition)))
    print(('PASS' if condition else 'FAIL') + ' - ' + label)

support = text(Path('src/main/java/com/example/editor/transformation/export/TemplateM2TExportSupport.java'))
tests = text(Path('src/test/java/com/example/editor/transformation/export/TransformationLanguageAdapterTests.java'))
policy = text(Path('src/main/java/com/example/editor/contract/StableIdentifierPolicy.java'))

check('stable identifier policy admits colon dot underscore and hyphen', '[A-Za-z0-9._:-]' in policy)
check('M2T token parser admits the stable element-id alphabet', 'attr:[A-Za-z0-9][A-Za-z0-9._:-]{0,239}' in support)
check('legacy narrow attribute-token pattern is gone', 'attr:[A-Za-z_][A-Za-z0-9_.-]*' not in support)
check('Acceleo adapter regression uses canonical colon-delimited attribute id', 'ExpressionTestFixtures.NAME' in tests and '[e.name/]' in tests)
check('EGL adapter regression uses canonical colon-delimited attribute id', 'ExpressionTestFixtures.NAME' in tests and '[%=e.name%]' in tests)
check('compatible native artifacts may not leak raw attr tokens', 'doesNotContain("{{attr:")' in tests)
check('canonical identity tokens remain fail-closed', 'CANONICAL_ID_TOKEN_NOT_NATIVE' in support and 'CANONICAL_ID_TOKEN_NOT_NATIVE' in tests)

# Mirror the Java token grammar against representative StableIdentifierPolicy values.
token = re.compile(r'\{\{\s*(id|classId|attr:[A-Za-z0-9][A-Za-z0-9._:-]{0,239})\s*}}')
for value in [
    '{{attr:attribute:Person:name}}',
    '{{attr:attribute:class:Book:title}}',
    '{{attr:attribute:Person:display_name-v2.1}}',
]:
    check('token grammar accepts ' + value, token.fullmatch(value) is not None)

passed = sum(ok for _, ok in checks)
print(f'TRANSFORMATION_M2T_TOKEN_FIX21={passed}/{len(checks)} ' + ('PASS' if passed == len(checks) else 'FAIL'))
sys.exit(0 if passed == len(checks) else 1)
