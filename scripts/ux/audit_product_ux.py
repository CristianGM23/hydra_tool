from __future__ import annotations
import json,re,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
policy=json.loads((ROOT/'config/rc1-ux-audit-policy.json').read_text(encoding='utf-8'))
production=[p for p in (ROOT/'frontend/src').rglob('*') if p.is_file() and p.suffix in {'.ts','.tsx'} and '.test.' not in p.name and '.spec.' not in p.name]
findings=[]
allowed=[(ROOT/x).resolve() for x in policy.get('allowedTechnicalFiles',[])]
allowed_generic=[(ROOT/x).resolve() for x in policy.get('allowedGenericFiles',[])]
def allowed_in(p,entries):
 r=p.resolve()
 return any(r==a or (a.is_dir() and a in r.parents) for a in entries)
def allowed_file(p): return allowed_in(p,allowed)
for p in production:
 text=p.read_text(encoding='utf-8',errors='replace'); rel=p.relative_to(ROOT).as_posix()
 if not allowed_in(p,allowed_generic):
  for pat in policy['genericErrorPatterns']:
   if pat in text: findings.append({'severity':'ERROR','kind':'generic-error-without-action','file':rel,'evidence':pat})
 if not allowed_file(p):
  for pat in policy['technicalVisiblePatterns']:
   if pat in text: findings.append({'severity':'ERROR','kind':'raw-technical-visible','file':rel,'evidence':pat})
# Critical surface coverage is intentionally semantic and coarse: each surface must expose help/guidance and error handling vocabulary.
coverage=[]
for surface in policy['criticalSurfaces']:
 files=[]
 for base in surface['paths']:
  path=ROOT/base
  if path.exists(): files += [p for p in path.rglob('*') if p.is_file() and p.suffix in {'.ts','.tsx'} and '.test.' not in p.name]
 corpus='\n'.join(p.read_text(encoding='utf-8',errors='replace') for p in files).lower()
 checks={}
 for required in surface['requires']:
  tokens={'help':['help','guidance','tooltip','ayuda'],'error':['error','problem','diagnostic','problema'],'emptyState':['empty','vacío','vacio','sin '],'problems':['problems','diagnostic','problemas']}[required]
  checks[required]=any(t.lower() in corpus for t in tokens)
  if not checks[required]: findings.append({'severity':'ERROR','kind':'missing-guidance-critical-surface','surface':surface['id'],'evidence':required})
 coverage.append({'surface':surface['id'],'checks':checks,'files':len(files)})
report={'schemaVersion':'1.0','productionFiles':len(production),'criticalSurfaces':coverage,'findings':findings,'passed':not any(f['severity']=='ERROR' for f in findings)}
out=ROOT/policy['report']; out.parent.mkdir(parents=True,exist_ok=True); out.write_text(json.dumps(report,indent=2,ensure_ascii=False)+'\n',encoding='utf-8')
print(f"RC1_UX_AUDIT findings={len(findings)} files={len(production)} status={'PASS' if report['passed'] else 'FAIL'}")
for f in findings[:50]: print(f['severity'],f['kind'],f.get('file',f.get('surface','')),f['evidence'])
raise SystemExit(0 if report['passed'] else 1)
