#!/usr/bin/env python3
from pathlib import Path
import re, sys
ROOT=Path(__file__).resolve().parents[2]
checks=[]
def check(name, ok, detail=''):
    checks.append((name,bool(ok),detail)); print(('PASS' if ok else 'FAIL')+' - '+name+((' :: '+detail) if detail and not ok else ''))
java=list((ROOT/'src/main/java').rglob('*.java'))+list((ROOT/'src/test/java').rglob('*.java'))
texts={p:p.read_text(encoding='utf-8',errors='replace') for p in java}
obsolete=[
 'src/main/java/com/example/editor/metamodel/migration/MetaModelV1ToV2Migration.java',
 'src/main/java/com/example/editor/migration/execution/HydraMigrationExecutionService.java',
 'src/test/java/com/example/editor/integration/compatibility/HydraV10CompatibilityPreservationTests.java',
 'src/test/java/com/example/editor/migration/execution/HydraMigrationExecutionServiceTests.java',
]
check('obsolete internal migration/version sources are absent', all(not (ROOT/p).exists() for p in obsolete))
name_re=re.compile(r'(?:Legacy|Historical|Migration|(?:^|[^A-Za-z])V\d+(?:ToV\d+)?)',re.I)
bad_names=[str(p.relative_to(ROOT)) for p in java if name_re.search(p.name)]
check('Java source filenames are neutral and non-versioned', not bad_names, ', '.join(bad_names[:10]))
# tombstones: comments/whitespace only
bad_tomb=[]
for p,s in texts.items():
    stripped=re.sub(r'/\*.*?\*/|//[^\n]*','',s,flags=re.S).strip()
    if not stripped: bad_tomb.append(str(p.relative_to(ROOT)))
check('no comment-only Java tombstone sources', not bad_tomb, ', '.join(bad_tomb[:10]))
combined='\n'.join(texts.values())
for token in ['.migrations()', 'legacyProgress', 'MetaModelV1ToV2Migration', 'HydraMigrationExecutionService', 'HydraV10CompatibilityPreservationTests']:
    check('removed API token absent: '+token, token not in combined)
# Current toolchain API signatures
bad_execute=[]; bad_build=[]; bad_report=[]
for p,s in texts.items():
    if re.search(r'\.execute\(\s*"[^\"]+"\s*,\s*List\.of\(',s): bad_execute.append(str(p.relative_to(ROOT)))
    if re.search(r'\.build\(\s*"[^\"]+"\s*,\s*List\.of\(',s): bad_build.append(str(p.relative_to(ROOT)))
    if re.search(r'\bMetamodelImportProgressListener\b[\s\S]{0,500}?\.report\(',s): bad_report.append(str(p.relative_to(ROOT)))
check('toolchain orchestrator uses canonical execute(List)', not bad_execute, ', '.join(bad_execute[:10]))
check('compatibility matrix uses canonical build(List)', not bad_build, ', '.join(bad_build[:10]))
check('metamodel import progress uses stage/activity contract only', not bad_report, ', '.join(bad_report[:10]))
# Exact constructor mismatches that caused IDE errors
adapter_bad=[]; manifest_bad=[]
for p,s in texts.items():
    if 'new UpdateSiteDeclarativeAdapterDocument(' in s and re.search(r'new\s+UpdateSiteDeclarativeAdapterDocument\(\s*"[^"]+"\s*,\s*"[^"]+"\s*,',s): adapter_bad.append(str(p.relative_to(ROOT)))
    # historical 12-argument manifest included a second list after checksums
    if re.search(r'new\s+HydraCapabilityPackageManifest\([\s\S]{0,600}?Map\.of\(\)\s*,\s*List\.of\(\)\s*,\s*Map\.of\(\)\s*\)',s): manifest_bad.append(str(p.relative_to(ROOT)))
check('declarative adapter documents use current constructor', not adapter_bad, ', '.join(adapter_bad[:10]))
check('capability package manifests use current constructor', not manifest_bad, ', '.join(manifest_bad[:10]))
# No compatibility-history wording in current source comments/tests. External technology compatibility naming itself is allowed.
history=[]
for p,s in texts.items():
    for pat in [r'constructor histórico',r'compatibilidad con documentos.*anteriores',r'código y pruebas anteriores',r'old .*constructor',r'legacy .*constructor']:
        if re.search(pat,s,re.I): history.append(str(p.relative_to(ROOT))); break
check('no historical-constructor contracts remain', not history, ', '.join(history[:10]))
passed=sum(ok for _,ok,_ in checks)
print(f'BACKEND_CONTRACT_CONVERGENCE={passed}/{len(checks)} '+('PASS' if passed==len(checks) else 'FAIL'))
sys.exit(0 if passed==len(checks) else 1)
