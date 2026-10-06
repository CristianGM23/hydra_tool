from __future__ import annotations
import argparse,json,os,re,subprocess,sys,time
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
p=argparse.ArgumentParser(); p.add_argument('--full',action='store_true'); args=p.parse_args()
policy=json.loads((ROOT/'config/rc1-scale-budgets.json').read_text(encoding='utf-8'))
cmd=['mvnw.cmd' if os.name=='nt' else './mvnw','-B','-Dtest=Rc1TransformationScaleQualificationTests']
if args.full: cmd.append('-Dhydra.rc1.scale.full=true')
cmd.append('test')
started=time.time(); cp=subprocess.run(cmd,cwd=ROOT,text=True,capture_output=True); out=cp.stdout+'\n'+cp.stderr
metrics={int(a):int(b) for a,b in re.findall(r'RC1_SCALE elements=(\d+) millis=(\d+)',out)}
checks=[]
for level in policy['levels']:
 n=level['elements']; required=level['required'] or args.full
 if not required: continue
 ms=metrics.get(n); passed=ms is not None and ms <= level['maxSeconds']*1000
 checks.append({'elements':n,'millis':ms,'budgetMillis':level['maxSeconds']*1000,'passed':passed})
report={'schemaVersion':'1.0','full':args.full,'mavenReturnCode':cp.returncode,'seconds':round(time.time()-started,3),'checks':checks,'passed':cp.returncode==0 and all(c['passed'] for c in checks)}
outp=ROOT/policy['report']; outp.parent.mkdir(parents=True,exist_ok=True); outp.write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
print('\n'.join(line for line in out.splitlines() if 'RC1_SCALE ' in line))
print(outp)
if not report['passed']: print('\n'.join(out.splitlines()[-120:]))
raise SystemExit(0 if report['passed'] else 1)
