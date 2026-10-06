from pathlib import Path
import re, sys
ROOT=Path(__file__).resolve().parents[2]
test=(ROOT/'src/test/java/com/example/editor/standalone/StandaloneEditorGeneratorServiceTests.java').read_text(encoding='utf-8')
template=(ROOT/'src/main/resources/standalone-template/frontend/app.js').read_text(encoding='utf-8')
checks=[]
def check(name, ok): checks.append((name, bool(ok)))
check('standalone template keeps mapped-child null guard', bool(re.search(r'filter\((\w+)=>\1&&nodeMappingFor\(\1\.classId\)\)', template)))
check('test uses semantic regex instead of lambda variable name', 'containsPattern("filter\\\\((\\\\w+)=>\\\\1&&nodeMappingFor\\\\(\\\\1\\\\.classId\\\\)\\\\)")' in test)
check('obsolete brittle item assertion removed', 'contains("filter(item=>item&&nodeMappingFor(item.classId))")' not in test)
for name,ok in checks: print(('PASS' if ok else 'FAIL')+' - '+name)
failed=[name for name,ok in checks if not ok]
print(f'V43285_STANDALONE_GENERATOR={len(checks)-len(failed)}/{len(checks)}')
if failed: sys.exit(1)
