from __future__ import annotations
import argparse,json,time,urllib.error,urllib.request
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
p=argparse.ArgumentParser(description='Ejecuta los escenarios E2E de producto de HYDRA RC1.')
p.add_argument('--live',action='store_true',help='Ejecuta peticiones contra un backend HYDRA ya arrancado.')
p.add_argument('--base-url',default='')
p.add_argument('--scenario',action='append',default=[])
p.add_argument('--timeout',type=float,default=15.0)
args=p.parse_args()
config=json.loads((ROOT/'config/rc1-product-scenarios.json').read_text(encoding='utf-8'))
selected=[s for s in config['scenarios'] if not args.scenario or s['id'] in args.scenario]
errors=[]; results=[]
for scenario in selected:
 project=ROOT/'reference-projects'/scenario['referenceProject']/'reference-project.json'
 if not project.exists(): errors.append(f"{scenario['id']}: reference project missing: {project}")
 for step in scenario['steps']:
  if not step.get('method') or not step.get('path') or not step.get('expectedStatus'): errors.append(f"{scenario['id']}: invalid step {step}")
  body=step.get('bodyFile')
  if body:
   path=ROOT/body
   if not path.exists(): errors.append(f"{scenario['id']}: bodyFile missing: {body}")
   else:
    try: json.loads(path.read_text(encoding='utf-8'))
    except Exception as exc: errors.append(f"{scenario['id']}: invalid JSON {body}: {exc}")
if errors:
 for e in errors: print('FAIL -',e)
 raise SystemExit(1)
print(f'RC1_PRODUCT_E2E_CONTRACT={len(selected)}/{len(selected)} PASS')
if not args.live: raise SystemExit(0)
base=(args.base_url or config['defaultBaseUrl']).rstrip('/')
for scenario in selected:
 started=time.time(); step_results=[]
 print('SCENARIO',scenario['id'])
 for step in scenario['steps']:
  url=base+step['path']; data=None; headers={'Accept':'application/json','X-Hydra-RC1':'true'}
  if step['method'].upper()=='DELETE': headers['If-Match']='*'
  if step.get('bodyFile'):
   data=(ROOT/step['bodyFile']).read_bytes(); headers['Content-Type']='application/json'
  req=urllib.request.Request(url,data=data,headers=headers,method=step['method'])
  status=None; body=''
  try:
   with urllib.request.urlopen(req,timeout=args.timeout) as response:
    status=response.status; body=response.read(1024*1024).decode('utf-8','replace')
  except urllib.error.HTTPError as exc:
   status=exc.code; body=exc.read(1024*1024).decode('utf-8','replace')
  ok=status in step['expectedStatus']; step_results.append({'name':step['name'],'status':status,'passed':ok})
  print(('PASS' if ok else 'FAIL'),step['name'],status)
  if not ok:
   print(body[-2000:]); results.append({'id':scenario['id'],'passed':False,'steps':step_results}); break
 else: results.append({'id':scenario['id'],'passed':True,'seconds':round(time.time()-started,3),'steps':step_results})
report={'schemaVersion':'1.0','baseUrl':base,'results':results,'passed':all(r['passed'] for r in results)}
out=ROOT/'target/release/rc1-product-e2e.json'; out.parent.mkdir(parents=True,exist_ok=True); out.write_text(json.dumps(report,indent=2,ensure_ascii=False)+'\n',encoding='utf-8')
print(out)
raise SystemExit(0 if report['passed'] else 1)
