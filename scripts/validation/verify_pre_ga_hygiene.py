from pathlib import Path
import json,re,sys
ROOT=Path(__file__).resolve().parents[2]
checks=[]
def check(name,ok,detail=''):
    checks.append((name,bool(ok),detail)); print(('PASS' if ok else 'FAIL')+' - '+name+((' :: '+detail) if detail and not ok else ''))

product_roots=[ROOT/'src/main',ROOT/'frontend/src']
all_product=[p for base in product_roots if base.exists() for p in base.rglob('*') if p.is_file()]
paths=[p.relative_to(ROOT).as_posix() for p in all_product]
check('no internal migration package',not any('/migration/' in '/'+x for x in paths))
check('no LTS/observatory package',not any('/lts/' in '/'+x or 'CompatibilityObservatory' in x for x in paths))
check('no versioned production module names',not any(re.search(r'(?:HydraV\d|/v\d+(?:\w*)Spanish)',x,re.I) for x in paths))
combined='\n'.join(p.read_text(encoding='utf-8',errors='ignore') for p in all_product)
for token in ['TECHNICAL_BASELINE','technicalBaseline','HYDRA_DISPLAY_VERSION','legacyProgress','hydraVersionRange']:
    check('removed token '+token,token not in combined)
check('old connection field removed','arrowStyle' not in combined)
check('old project image kinds removed','PROJECT_SVG' not in combined and 'PROJECT_PNG' not in combined)
check('no internal migration/LTS API routes','/api/migrations' not in combined and '/api/lts' not in combined)
baseline=(ROOT/'config/hydra-logical-size-baseline.properties').read_text(encoding='utf-8') if (ROOT/'config/hydra-logical-size-baseline.properties').exists() else ''
check('no per-file logical-size exceptions',not re.search(r'(?m)^exception\.',baseline))
old_docs=[]
for directory in ['docs/architecture','docs/security','docs/validation','docs/licensing']:
    for p in (ROOT/directory).glob('*'):
        if re.search(r'HYDRA_V\d',p.name): old_docs.append(p.relative_to(ROOT).as_posix())
check('no version-history documentation in source',not old_docs,', '.join(old_docs[:8]))
current=json.loads((ROOT/'config/hydra-version.json').read_text(encoding='utf-8'))['version']
root_old=[p.name for p in ROOT.glob('HYDRA_V*_RELEASE_NOTES.md') if f'V{current}_RELEASE_NOTES.md' not in p.name]
check('no previous release notes in working tree',not root_old,', '.join(root_old))
nested_manifests=[p.relative_to(ROOT).as_posix() for p in ROOT.rglob('MANIFEST_SHA256.txt') if p != ROOT/'MANIFEST_SHA256.txt' and 'target' not in p.parts]
check('single canonical source manifest at repository root',not nested_manifests,', '.join(nested_manifests[:8]))
# Tombstone sources that only state a former duplicate/retirement are prohibited.
tomb=[]
for p in (ROOT/'src/main').rglob('*.java'):
    text=p.read_text(encoding='utf-8',errors='ignore').strip()
    if not re.search(r'\b(class|record|interface|enum)\b',re.sub(r'/\*.*?\*/|//.*','',text,flags=re.S)):
        tomb.append(p.relative_to(ROOT).as_posix())
check('no tombstone Java sources',not tomb,', '.join(tomb[:8]))
failed=[n for n,o,_ in checks if not o]
print(f'PRE_GA_HYGIENE={len(checks)-len(failed)}/{len(checks)}')
sys.exit(1 if failed else 0)
