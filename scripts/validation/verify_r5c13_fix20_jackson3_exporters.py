from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[2]
checks = []

def text(path):
    return (ROOT / path).read_text(encoding='utf-8')

def check(label, condition):
    checks.append((label, bool(condition)))
    print(('PASS' if condition else 'FAIL') + ' - ' + label)

support = text(Path('src/main/java/com/example/editor/transformation/export/CanonicalRuntimeBundleSupport.java'))
java_adapter = text(Path('src/main/java/com/example/editor/transformation/export/JavaTransformationLanguageAdapter.java'))
xtend_adapter = text(Path('src/main/java/com/example/editor/transformation/export/XtendTransformationLanguageAdapter.java'))
tests = text(Path('src/test/java/com/example/editor/transformation/export/TransformationLanguageAdapterTests.java'))
transformation_java = '\n'.join(
    p.read_text(encoding='utf-8')
    for root in [ROOT / 'src/main/java/com/example/editor/transformation', ROOT / 'src/test/java/com/example/editor/transformation']
    for p in root.rglob('*.java')
)

check('canonical runtime bundle uses Jackson 3 ObjectMapper', 'import tools.jackson.databind.ObjectMapper;' in support)
check('canonical runtime bundle catches Jackson 3 base exception', 'import tools.jackson.core.JacksonException;' in support and 'catch(JacksonException exception)' in support)
check('Java adapter injects Jackson 3 ObjectMapper', 'import tools.jackson.databind.ObjectMapper;' in java_adapter)
check('Xtend adapter injects Jackson 3 ObjectMapper', 'import tools.jackson.databind.ObjectMapper;' in xtend_adapter)
check('adapter tests instantiate Jackson 3 ObjectMapper', 'import tools.jackson.databind.ObjectMapper;' in tests)
check('transformation sources do not import Jackson 2 core/databind', 'com.fasterxml.jackson.databind' not in transformation_java and 'com.fasterxml.jackson.core' not in transformation_java)
check('existing Jackson annotations remain independent of databind migration', 'com.fasterxml.jackson.annotation' in transformation_java)
check('canonical bundle still pretty-prints complete definition', 'writerWithDefaultPrettyPrinter().writeValueAsString(document)' in support)
check('serialization failure remains fail-closed', 'CANONICAL_SERIALIZATION_FAILED' in support and 'List.of()' in support)

passed = sum(ok for _, ok in checks)
print(f'TRANSFORMATION_JACKSON3_FIX20={passed}/{len(checks)} ' + ('PASS' if passed == len(checks) else 'FAIL'))
sys.exit(0 if passed == len(checks) else 1)
