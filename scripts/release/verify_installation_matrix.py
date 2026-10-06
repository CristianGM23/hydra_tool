from __future__ import annotations
import argparse,json,os,shutil,stat,subprocess,sys,tempfile,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
p=argparse.ArgumentParser(); p.add_argument('--source-zip',default=''); p.add_argument('--smoke-build',action='store_true'); args=p.parse_args()
policy=json.loads((ROOT/'config/rc1-installation-matrix.json').read_text(encoding='utf-8'))
version=json.loads((ROOT/'config/hydra-version.json').read_text(encoding='utf-8'))['version']
archive=Path(args.source_zip) if args.source_zip else ROOT/'target/release'/f'HYDRA_V{version}_source.zip'
if not archive.exists():
 cp=subprocess.run([sys.executable,'scripts/release/package_source.py'],cwd=ROOT); 
 if cp.returncode: raise SystemExit(cp.returncode)
results=[]
with tempfile.TemporaryDirectory(prefix='hydra-rc1-install-') as td:
 base=Path(td)
 for case in policy['cases']:
  target=base/case['pathTemplate']; target.mkdir(parents=True,exist_ok=True)
  with zipfile.ZipFile(archive) as z: z.extractall(target)
  if case['id']=='readonly-source':
   for source in target.rglob('*'):
    if source.is_file():
     try: source.chmod(source.stat().st_mode & ~0o222)
     except OSError: pass
  cmds=[[sys.executable,'scripts/validation/verify_manifest.py'],[sys.executable,'scripts/release/verify_release_metadata.py']]
  ok=True; outputs=[]
  for cmd in cmds:
   cp=subprocess.run(cmd,cwd=target,text=True,capture_output=True); ok &= cp.returncode==0; outputs.append((cp.stdout+cp.stderr)[-3000:])
  if args.smoke_build and case['required']:
   mvn=['mvnw.cmd','-B','-DskipTests','compile'] if os.name=='nt' else ['./mvnw','-B','-DskipTests','compile']
   cp=subprocess.run(mvn,cwd=target,text=True,capture_output=True); ok &= cp.returncode==0; outputs.append((cp.stdout+cp.stderr)[-3000:])
  results.append({'id':case['id'],'path':str(target),'required':case['required'],'passed':ok,'outputTail':'\n'.join(outputs)[-6000:]})
report={'schemaVersion':'1.0','archive':archive.name,'smokeBuild':args.smoke_build,'results':results,'passed':all(r['passed'] or not r['required'] for r in results)}
out=ROOT/policy['report']; out.parent.mkdir(parents=True,exist_ok=True); out.write_text(json.dumps(report,indent=2,ensure_ascii=False)+'\n',encoding='utf-8')
for r in results: print(('PASS' if r['passed'] else 'FAIL'),r['id'])
print(out); raise SystemExit(0 if report['passed'] else 1)
