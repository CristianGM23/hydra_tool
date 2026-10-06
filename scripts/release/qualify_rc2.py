from __future__ import annotations
import argparse,hashlib,json,os,sys,zipfile,shutil
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2];OUT=ROOT/'target/release';OUT.mkdir(parents=True,exist_ok=True)
sys.path.insert(0,str(ROOT/'scripts/release'))
from qualification_engine import QualificationRunner,add_resume_arguments,validate_evidence_report  # noqa:E402
p=argparse.ArgumentParser(description='Cualifica HYDRA 4.32.107 como RC2 estabilizada.')
p.add_argument('--full',action='store_true');p.add_argument('--primary-rc1-qualified',action='store_true');add_resume_arguments(p);args=p.parse_args()
policy=json.loads((ROOT/'config/rc2-qualification-policy.json').read_text(encoding='utf-8'));version=policy['releaseVersion'];timeouts=policy.get('timeoutsSeconds',{});runner=QualificationRunner('rc2',args,fingerprint_extra={'candidate':'RC2','full':args.full,'version':version})

def sha(path:Path)->str:
 d=hashlib.sha256();
 with path.open('rb') as fh:
  for chunk in iter(lambda:fh.read(1024*1024),b''):d.update(chunk)
 return d.hexdigest()

def finish(status:str,extra:dict|None=None)->int:
 payload={'schemaVersion':'1.0','candidate':'RC2','releaseVersion':version,'status':status,'full':args.full,'fingerprint':runner.fingerprint,'checkpoint':str(runner.state_path.relative_to(ROOT)),'steps':runner.stage_payloads()}
 if extra:payload.update(extra)
 path=OUT/'rc2-qualification.json';path.write_text(json.dumps(payload,indent=2,ensure_ascii=False)+'\n',encoding='utf-8');digest=sha(path);(OUT/'rc2-qualification.json.sha256').write_text(f'{digest}  {path.name}\n',encoding='utf-8')
 if status=='PASS':
  # Regenerate provenance after the RC2 report exists so the evidence graph is closed.
  import subprocess
  subprocess.run([sys.executable,'scripts/release/generate_provenance.py'],cwd=ROOT,check=True)
  provenance=OUT/'release-provenance.json';certificate={'candidate':'RC2','releaseVersion':version,'qualified':True,'full':args.full,'qualificationSha256':digest,'provenanceSha256':sha(provenance) if provenance.is_file() else None,'fingerprint':runner.fingerprint,'featureFreeze':bool(policy.get('freezeOnPass',True))};(OUT/'rc2-certificate.json').write_text(json.dumps(certificate,indent=2,ensure_ascii=False)+'\n',encoding='utf-8')
  freeze={'candidate':'RC2','releaseVersion':version,'featureFreeze':True,'sourceManifestSha256':sha(ROOT/'MANIFEST_SHA256.txt'),'qualificationSha256':digest,'provenanceSha256':sha(provenance) if provenance.is_file() else None};(OUT/'rc2-freeze.json').write_text(json.dumps(freeze,indent=2)+'\n',encoding='utf-8')
 print(path);return 0 if status=='PASS' else 1

def run(name,cmd,*,cwd=ROOT,timeout=None,required=True):
 effective=int(timeout if timeout is not None else timeouts.get(name,7200))
 ok=runner.run(name,cmd,cwd=cwd,timeout=effective,required=required)
 if required and not ok:raise SystemExit(finish('FAIL'))
 return ok

def inherited_rc1_stage(report_path:Path,stage_name:str)->tuple[bool,str]:
 return validate_evidence_report(
  report_path, expected_candidate='RC1', expected_version=version, require_full=True, required_stages=(stage_name,),
  fingerprint_extra_factory=lambda data:{'full':bool(data.get('full')),'releaseVersion':version},
 )

def inherited_readiness_stage(report_path:Path,stage_name:str)->tuple[bool,str]:
 return validate_evidence_report(
  report_path, expected_statuses=('PASS',), expected_version=version, required_stages=(stage_name,),
  fingerprint_extra_factory=lambda data:{'gate':'final-tree','structuralOnly':bool(data.get('structuralOnly')),'version':version},
 )

run('workspace-integrity',[sys.executable,'scripts/validation/verify_manifest.py'],timeout=300)
run('environment-doctor',[sys.executable,'scripts/doctor/hydra_doctor.py','--strict'],timeout=600)
run('toolchain-hermetic',[sys.executable,'scripts/release/verify_toolchain.py','--strict'],timeout=600)
if args.full:
 frontend=ROOT/'frontend';npm=['npm.cmd','ci'] if os.name=='nt' else ['npm','ci']
 run('frontend-preflight-npm-ci',npm,cwd=frontend,timeout=3600)
 run('frontend-preflight-vitest',['node','scripts/hydra-test-runner.mjs','full'],cwd=frontend,timeout=7200)
if not args.primary_rc1_qualified:
 nested=[sys.executable,'scripts/release/qualify_rc1.py']+(['--full'] if args.full else [])
 if args.resume or args.rerun_failed:nested.append('--resume')
 run('rc1-qualification',nested,timeout=28800)
else:
 evidence=OUT/'rc1-qualification.json'
 ok,detail=validate_evidence_report(
  evidence, expected_candidate='RC1', expected_version=version, require_full=args.full,
  fingerprint_extra_factory=lambda data:{'full':bool(data.get('full')),'releaseVersion':version},
 )
 runner.record('rc1-qualification',ok,detail)
 if not ok:raise SystemExit(finish('FAIL'))
run('fix111-117-structural',[sys.executable,'scripts/validation/verify_r5c13_fix111_117_rc2_stabilization.py'])
run('reference-projects',[sys.executable,'scripts/testing/verify_reference_projects.py'])
run('recovery-contracts',[sys.executable,'scripts/validation/verify_external_project_recovery.py'])
run('fault-injection-contracts',[sys.executable,'scripts/validation/verify_r5c13_fix92_98_platform_hardening.py'])
run('installation-matrix',[sys.executable,'scripts/release/verify_installation_matrix.py']+(['--smoke-build'] if args.full else []),timeout=10800)
if args.full:
 run('environment-doctor-full',[sys.executable,'scripts/doctor/hydra_doctor.py','--full','--strict'],timeout=600)
 rc1_report=OUT/'rc1-qualification.json'
 for local,upstream in [('scale-50000','scale-full'),('live-e2e','product-e2e-live'),('frontend-playwright','frontend-playwright')]:
  ok,detail=inherited_rc1_stage(rc1_report,upstream);runner.record(local,ok,detail)
  if not ok:raise SystemExit(finish('FAIL'))
 readiness=OUT/'release-readiness.json';ok,detail=inherited_readiness_stage(readiness,'Maven full verify');runner.record('maven-clean-verify',ok,detail)
 if not ok:raise SystemExit(finish('FAIL'))
else:
 runner.record('full-runtime-evidence',True,'Structural RC2; use --full for live/scale/build evidence.')
run('source-package',[sys.executable,'scripts/release/package_source.py'])
run('sbom',[sys.executable,'scripts/release/generate_sbom.py'])
run('provenance',[sys.executable,'scripts/release/generate_provenance.py'])
archive=OUT/f'HYDRA_V{version}_source.zip';archive_sha=sha(archive)
work=OUT/'work/rc2-extracted';marker=work/'.hydra-source-sha256'
if not marker.is_file() or marker.read_text(encoding='utf-8').strip()!=archive_sha:
 if work.exists():shutil.rmtree(work)
 work.mkdir(parents=True,exist_ok=True)
 with zipfile.ZipFile(archive) as z:
  for info in z.infolist():
   rel=Path(info.filename)
   if rel.is_absolute() or '..' in rel.parts:raise SystemExit(finish('FAIL',{'reason':'unsafe-package-path'}))
  z.extractall(work)
 marker.write_text(archive_sha+'\n',encoding='utf-8')
run('package-requalification',[sys.executable,'scripts/validation/verify_manifest.py'],cwd=work,timeout=600)
run('package-rc2-contract',[sys.executable,'scripts/validation/verify_r5c13_fix111_117_rc2_stabilization.py'],cwd=work,timeout=1800)
if args.full:
 frontend=work/'frontend';npm=['npm.cmd','ci'] if os.name=='nt' else ['npm','ci'];run('package-npm-ci',npm,cwd=frontend,timeout=3600)
 run('package-second-full-validation',[sys.executable,'scripts/validation/verify_final_tree.py','--ci'],cwd=work,timeout=10800)
run('package-rebuild',[sys.executable,'scripts/release/package_source.py'],cwd=work,timeout=1800)
rebuilt=work/'target/release'/archive.name;reproducible=rebuilt.is_file() and sha(rebuilt)==archive_sha;runner.record('package-byte-reproducibility',reproducible,f'original={archive_sha} rebuilt={sha(rebuilt) if rebuilt.is_file() else "MISSING"}')
if not reproducible:raise SystemExit(finish('FAIL'))
run('provenance-final',[sys.executable,'scripts/release/generate_provenance.py'])
raise SystemExit(finish('PASS',{'sourceArchive':archive.name,'sourceSha256':archive_sha}))
