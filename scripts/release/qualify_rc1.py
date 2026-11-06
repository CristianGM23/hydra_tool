from __future__ import annotations
import argparse,contextlib,hashlib,json,os,socket,subprocess,sys,tempfile,time,urllib.request
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2];OUT=ROOT/'target/release';OUT.mkdir(parents=True,exist_ok=True)
sys.path.insert(0,str(ROOT/'scripts/release'))
from qualification_engine import QualificationRunner,_kill_tree,add_resume_arguments,validate_evidence_report  # noqa:E402
p=argparse.ArgumentParser(description='Cualifica HYDRA 4.32.107 como RC1 de producto.')
p.add_argument('--full',action='store_true');p.add_argument('--primary-release-qualified',action='store_true');p.add_argument('--base-url',default='http://127.0.0.1:8081');add_resume_arguments(p);args=p.parse_args()
policy=json.loads((ROOT/'config/rc1-qualification-policy.json').read_text(encoding='utf-8'));runner=QualificationRunner('rc1',args,fingerprint_extra={'full':args.full,'releaseVersion':policy['releaseVersion']});steps=[]

def wait_http(url,timeout=60):
 deadline=time.time()+timeout;last=None
 while time.time()<deadline:
  try:
   with urllib.request.urlopen(url,timeout=2) as response:
    if response.status<500:return
  except Exception as exc:last=exc
  time.sleep(.5)
 raise RuntimeError(f'Backend RC1 did not become ready: {last}')

@contextlib.contextmanager
def managed_backend():
 port=18081;base=f'http://127.0.0.1:{port}';temporary=tempfile.TemporaryDirectory(prefix='hydra-rc1-live-');cwd=Path(temporary.name)
 java_home=os.environ.get('HYDRA_JAVA_HOME') or os.environ.get('JAVA_HOME');java=str(Path(java_home)/'bin'/('java.exe' if os.name=='nt' else 'java')) if java_home else 'java'
 # A backend already listening (e.g. left over by an interrupted run) would answer the health check with stale code.
 with contextlib.closing(socket.socket()) as probe:
  if probe.connect_ex(('127.0.0.1',port))==0:temporary.cleanup();raise RuntimeError(f'Port {port} is already in use; refusing to qualify against an unknown backend')
 log_dir=ROOT/'target/release/logs/rc1';log_dir.mkdir(parents=True,exist_ok=True);creationflags=subprocess.CREATE_NEW_PROCESS_GROUP if os.name=='nt' else 0
 jars=sorted((ROOT/'build/hydra-maven').glob('editor-*.jar'))
 if not jars:
  # The pom cleans build/hydra-maven on every Maven run, so an earlier gate's jar is normally gone. Package it first: compiling
  # inside the Spring Boot Maven run goal (2,600+ sources) consumed the 90 s readiness budget and started the backend with devtools.
  with (log_dir/'managed-backend-package.log').open('w',encoding='utf-8') as package_log:
   builder=subprocess.Popen([str(ROOT/('mvnw.cmd' if os.name=='nt' else 'mvnw')),'-B','-DskipTests','package'],cwd=ROOT,stdout=package_log,stderr=subprocess.STDOUT,creationflags=creationflags,start_new_session=(os.name!='nt'))
   try:code=builder.wait(timeout=1800)
   except subprocess.TimeoutExpired:_kill_tree(builder,15);code=None
  jars=sorted((ROOT/'build/hydra-maven').glob('editor-*.jar'))
  if code!=0 or not jars:temporary.cleanup();raise RuntimeError('Could not package the RC1 backend jar; see target/release/logs/rc1/managed-backend-package.log')
 cmd=[java,'-jar',str(jars[-1]),f'--server.port={port}','--server.address=127.0.0.1','--spring.profiles.active=headless-server','--hydra.instance-lock.enabled=false','--dsl.projects.local.native-path-picker-enabled=false']
 log=(log_dir/'managed-backend.log').open('w',encoding='utf-8')
 proc=subprocess.Popen(cmd,cwd=cwd,stdout=log,stderr=subprocess.STDOUT,text=True,creationflags=creationflags,start_new_session=(os.name!='nt'))
 try:wait_http(base+'/actuator/health',90);yield base
 finally:
  # mvnw.cmd -> Maven JVM -> application JVM: terminating only the wrapper leaves the backend listening.
  _kill_tree(proc,15)
  try:proc.wait(timeout=15)
  except subprocess.TimeoutExpired:proc.kill();proc.wait(timeout=5)
  log.close();temporary.cleanup()

def sync_steps():steps.clear();steps.extend(runner.stage_payloads())
def finish(status):
 sync_steps();report={'schemaVersion':'1.1','candidate':'RC1','releaseVersion':policy['releaseVersion'],'status':status,'full':args.full,'fingerprint':runner.fingerprint,'checkpoint':str(runner.state_path.relative_to(ROOT)),'steps':steps};path=OUT/'rc1-qualification.json';path.write_text(json.dumps(report,indent=2,ensure_ascii=False)+'\n',encoding='utf-8');digest=hashlib.sha256(path.read_bytes()).hexdigest();(OUT/'rc1-qualification.json.sha256').write_text(f'{digest}  {path.name}\n',encoding='utf-8')
 if status=='PASS':(OUT/'rc1-certificate.json').write_text(json.dumps({'candidate':'RC1','releaseVersion':policy['releaseVersion'],'qualified':True,'full':args.full,'qualificationSha256':digest,'fingerprint':runner.fingerprint},indent=2)+'\n',encoding='utf-8')
 print(path);return 0 if status=='PASS' else 1

def run(name,cmd,timeout=7200,required=True,cwd=ROOT):
 ok=runner.run(name,cmd,cwd=cwd,timeout=timeout,required=required)
 if required and not ok:raise SystemExit(finish('FAIL'))
 return ok

run('rc1-workspace-integrity',[sys.executable,'scripts/validation/verify_manifest.py','--allow-extra'],timeout=300)
if not args.primary_release_qualified:
 nested=[sys.executable,'scripts/release/qualify_release.py']+(['--full'] if args.full else [])
 if args.resume or args.rerun_failed:nested.append('--resume')
 run('fix100-release-qualification',nested,timeout=18000)
else:
 evidence=OUT/'release-qualification.json'
 ok,detail=validate_evidence_report(
  evidence, expected_version=policy['releaseVersion'], require_full=args.full,
  fingerprint_extra_factory=lambda data:{'full':bool(data.get('full')),'version':policy['releaseVersion']},
 )
 runner.record('fix100-release-qualification',ok,detail)
 if not ok:raise SystemExit(finish('FAIL'))
run('fix101-107-structural',[sys.executable,'scripts/validation/verify_r5c13_fix101_107_rc1.py'])
run('reference-projects',[sys.executable,'scripts/testing/verify_reference_projects.py'])
run('product-e2e-contract',[sys.executable,'scripts/testing/run_rc1_product_e2e.py'])
run('ux-audit',[sys.executable,'scripts/ux/audit_product_ux.py'])
run('onboarding-contract',[sys.executable,'scripts/testing/verify_rc1_onboarding.py'])
run('source-package',[sys.executable,'scripts/release/package_source.py'])
run('installation-matrix',[sys.executable,'scripts/release/verify_installation_matrix.py']+(['--smoke-build'] if args.full else []),timeout=7200)
if args.full:
 run('scale-full',[sys.executable,'scripts/benchmarks/run_rc1_scale.py','--full'],timeout=7200)
 if args.base_url!='http://127.0.0.1:8081':run('product-e2e-live',[sys.executable,'scripts/testing/run_rc1_product_e2e.py','--live','--base-url',args.base_url],timeout=1800)
 else:
  started=time.time()
  try:
   with managed_backend() as live_url:ok=runner.run('product-e2e-live',[sys.executable,'scripts/testing/run_rc1_product_e2e.py','--live','--base-url',live_url],timeout=1800)
   if not ok:raise SystemExit(finish('FAIL'))
  except Exception as exc:
   runner.record('product-e2e-live',False,str(exc),seconds=round(time.time()-started,3));raise SystemExit(finish('FAIL'))
 npm=['npm.cmd' if os.name=='nt' else 'npm','run','test:a11y:e2e','--','rc1-product-navigation.spec.ts'];run('frontend-playwright',npm,timeout=1800,cwd=ROOT/'frontend')
else:runner.record('scale-required',True,'Scale JUnit contract included; execute --full for runtime budgets.')
run('package-requalification',[sys.executable,'scripts/validation/verify_manifest.py','--allow-extra'])
raise SystemExit(finish('PASS'))
