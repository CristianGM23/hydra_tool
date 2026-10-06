#!/usr/bin/env python3
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[2]
checks=[]
def check(name, condition):
    ok=bool(condition); checks.append((name,ok)); print(('PASS' if ok else 'FAIL')+' - '+name)

compat={
    'ExpressionModel.java','ExpressionModelApplicationException.java','ExpressionModelApplicationService.java',
    'ExpressionModelCheckReport.java','ExpressionModelLoadResult.java','ExpressionModelProperties.java',
    'ExpressionModelSaveResponse.java','ExpressionModelSummary.java'
}
legacy=ROOT/'src/main/java/com/example/editor/validation/expression'
legacy_tests=ROOT/'src/test/java/com/example/editor/validation/expression'
actual={p.name for p in legacy.glob('*.java')} if legacy.exists() else set()
unexpected=actual-compat
root_tests={p.name for p in legacy_tests.glob('*.java')} if legacy_tests.exists() else set()

pom=ET.parse(ROOT/'pom.xml').getroot()
ns={'m':'http://maven.apache.org/POM/4.0.0'}
plugin=None
for p in pom.findall('.//m:plugin',ns):
    aid=p.find('m:artifactId',ns)
    if aid is not None and aid.text=='maven-compiler-plugin':
        plugin=p; break
config=plugin.find('m:configuration',ns) if plugin is not None else None
main_excludes=set() if config is None else {e.text for e in config.findall('m:excludes/m:exclude',ns) if e.text}
test_excludes=set() if config is None else {e.text for e in config.findall('m:testExcludes/m:testExclude',ns) if e.text}

def main_pattern(name): return f'com/example/editor/validation/expression/{name}'
def test_pattern(name): return f'com/example/editor/validation/expression/{name}'

clean_main = not unexpected
quarantined_main = all(main_pattern(name) in main_excludes for name in unexpected)
clean_tests = not root_tests
quarantined_tests = all(test_pattern(name) in test_excludes for name in root_tests)

check('maven compiler plugin configuration exists', plugin is not None)
check('canonical semantic checker exists', (ROOT/'src/main/java/com/example/editor/semantic/expression/ExpressionTypeChecker.java').exists())
check('compatibility validation.expression classes remain available', compat.issubset(actual))
check('legacy semantic main sources are absent or fully quarantined', clean_main or quarantined_main)
check('legacy expression tests are absent or fully quarantined', clean_tests or quarantined_tests)
check('legacy checker cannot participate in Maven compile', not (legacy/'ExpressionTypeChecker.java').exists() or main_pattern('ExpressionTypeChecker.java') in main_excludes)
check('legacy checker test cannot participate in Maven testCompile', not (legacy_tests/'ExpressionTypeCheckerTests.java').exists() or test_pattern('ExpressionTypeCheckerTests.java') in test_excludes)
service=(ROOT/'src/main/java/com/example/editor/validation/expression/ExpressionModelApplicationService.java').read_text(encoding='utf-8')
check('application service injects canonical semantic checker', 'import com.example.editor.semantic.expression.ExpressionTypeChecker;' in service)
check('quarantine is explicit rather than broad package exclusion', not any(x.endswith('validation/expression/**') or x.endswith('validation/expression/*.java') for x in main_excludes))

failed=[name for name,ok in checks if not ok]
mode='clean' if clean_main and clean_tests else 'windows-overlay-quarantine'
print('SEMANTIC_EXPRESSION_SOURCE_MODE='+mode)
print(f'SEMANTIC_EXPRESSION_MAVEN_QUARANTINE_FIX27={len(checks)-len(failed)}/{len(checks)} '+('PASS' if not failed else 'FAIL'))
raise SystemExit(1 if failed else 0)
