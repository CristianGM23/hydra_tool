from __future__ import annotations
import json,re,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]; BASE=ROOT/'reference-projects'
secret=re.compile(r'(^|/)(\.env|credentials\.json|secrets?\.json|id_rsa)$|\.(pem|key)$',re.I)
errors=[]; projects=[]
for manifest_path in sorted(BASE.glob('*/reference-project.json')):
 project=json.loads(manifest_path.read_text(encoding='utf-8')); root=manifest_path.parent
 if project.get('schemaVersion')!='1.0': errors.append(f'{project.get("id")}: schemaVersion')
 if project.get('id')!=root.name: errors.append(f'{root.name}: manifest id mismatch')
 seen=set()
 for item in project.get('expectedArtifacts',[]):
  rel=item['path']; p=root/rel
  if rel in seen: errors.append(f'{root.name}: duplicate artifact {rel}')
  seen.add(rel)
  if item.get('required') and not p.is_file(): errors.append(f'{root.name}: missing {rel}')
  if secret.search(rel): errors.append(f'{root.name}: secret-like file {rel}')
  if p.suffix=='.json' and p.exists():
   try: json.loads(p.read_text(encoding='utf-8'))
   except Exception as exc: errors.append(f'{root.name}: invalid JSON {rel}: {exc}')
 # semantic cross checks
 json_docs=[]
 for p in root.rglob('*.json'):
  if p.name=='reference-project.json': continue
  try: json_docs.append((p,json.loads(p.read_text(encoding='utf-8'))))
  except Exception: pass
 metas={d.get('id') for _,d in json_docs if isinstance(d,dict) and 'classes' in d and 'relations' in d}
 for p,d in json_docs:
  mid=d.get('metamodelId') if isinstance(d,dict) else None
  if mid and mid not in metas and not (root.name=='library-basic' and mid=='library'): errors.append(f'{root.name}: {p.name} references unknown metamodel {mid}')
 if root.name=='sync-advanced':
  t=next(d for p,d in json_docs if p.name.endswith('.transformation.json'))
  if t.get('directionality')!='BIDIRECTIONAL': errors.append('sync-advanced: not bidirectional')
  dirs={r.get('direction') for r in t.get('rules',[])}
  if not {'FORWARD','REVERSE'}<=dirs: errors.append('sync-advanced: missing directional rules')
 projects.append({'id':root.name,'artifacts':len(project.get('expectedArtifacts',[])),'capabilities':len(project.get('capabilities',[]))})
if len(projects)<3: errors.append('at least three reference projects are required')
for e in errors: print('FAIL -',e)
print(f'RC1_REFERENCE_PROJECTS={len(projects)} projects status={"PASS" if not errors else "FAIL"}')
raise SystemExit(1 if errors else 0)
