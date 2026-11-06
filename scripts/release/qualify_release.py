from __future__ import annotations
import argparse, hashlib, json, os, shutil, sys, time, zipfile
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'scripts/release'))
from qualification_engine import QualificationRunner,add_resume_arguments,validate_evidence_report  # noqa:E402

parser=argparse.ArgumentParser(description='Cualifica una release HYDRA desde el árbol hasta el paquete reextraído.')
parser.add_argument('--full',action='store_true',help='Ejecuta builds/tests completos también sobre el paquete reextraído.')
parser.add_argument('--ci',action='store_true')
parser.add_argument('--primary-already-verified',action='store_true',help='Acepta el gate completo previo de la misma ejecución CI/local y evita repetirlo en el árbol primario.')
add_resume_arguments(parser)
args=parser.parse_args()
OUT=ROOT/'target/release'; OUT.mkdir(parents=True,exist_ok=True)
policy=json.loads((ROOT/'config/release-qualification-policy.json').read_text(encoding='utf-8'))
version=json.loads((ROOT/'config/hydra-version.json').read_text(encoding='utf-8'))['version']
runner=QualificationRunner('fix100-release',args,fingerprint_extra={'full':args.full,'version':version})
steps=[]

def sha(path:Path)->str:
 d=hashlib.sha256()
 with path.open('rb') as fh:
  for chunk in iter(lambda:fh.read(1024*1024),b''): d.update(chunk)
 return d.hexdigest()

def sync_steps():
 steps.clear();steps.extend(runner.stage_payloads())

def write_report(status,extra=None):
 sync_steps();report={'schemaVersion':'1.1','version':version,'status':status,'full':args.full,'policy':policy,'fingerprint':runner.fingerprint,'checkpoint':str(runner.state_path.relative_to(ROOT)),'steps':steps}
 if extra: report.update(extra)
 path=OUT/'release-qualification.json'; path.write_text(json.dumps(report,indent=2,ensure_ascii=False)+'\n',encoding='utf-8')
 (OUT/'release-qualification.json.sha256').write_text(f'{sha(path)}  {path.name}\n',encoding='utf-8');print(path);return 0 if status=='PASS' else 1

def run(name,cmd,cwd=ROOT,timeout=3600,required=True):
 ok=runner.run(name,cmd,cwd=cwd,timeout=timeout,required=required)
 if required and not ok: raise SystemExit(write_report('FAIL'))
 return ok

# Fail fast when the working tree differs from the canonical RC source. This avoids
# spending many minutes on gates whose failures are merely consequences of a dirty overlay.
run('primary-source-integrity',[sys.executable,'scripts/validation/verify_manifest.py','--allow-extra'],timeout=300)

if args.full:
 npm_cmd=['npm.cmd','ci'] if os.name=='nt' else ['npm','ci']
 run('primary-npm-ci',npm_cmd,cwd=ROOT/'frontend',timeout=3600)
 npx='npx.cmd' if os.name=='nt' else 'npx'
 run('primary-playwright-chromium',[npx,'playwright','install','chromium'],cwd=ROOT/'frontend',timeout=3600)
 run('primary-toolchain',[sys.executable,'scripts/release/verify_toolchain.py','--strict','--full'],timeout=600)

if args.primary_already_verified:
 evidence=OUT/'release-readiness.json'
 ok,detail=validate_evidence_report(
  evidence, expected_statuses=('PASS','STRUCTURAL_PASS'), expected_version=version,
  fingerprint_extra_factory=lambda data:{'gate':'final-tree','structuralOnly':bool(data.get('structuralOnly')),'version':version},
 )
 if ok and args.full:
  readiness=json.loads(evidence.read_text(encoding='utf-8'));ok=not bool(readiness.get('structuralOnly'));detail += f" structuralOnly={readiness.get('structuralOnly')}"
 runner.record('primary-full-tree',ok,detail)
 if not ok:raise SystemExit(write_report('FAIL'))
else:
 run('primary-full-tree',[sys.executable,'scripts/validation/verify_final_tree.py'] + ([] if args.full else ['--structural-only']),timeout=7200)

if args.full:
 tests=','.join(policy['criticalBackendTests']);mvn=[str(ROOT/'mvnw.cmd'),'-B',f'-Dtest={tests}','test'] if os.name=='nt' else ['./mvnw','-B',f'-Dtest={tests}','test']
 run('critical-recovery-smoke',mvn,timeout=3600)
else:
 run('critical-recovery-contracts',[sys.executable,'scripts/validation/verify_r5c13_fix99_100_release_closure.py'])

run('source-package',[sys.executable,'scripts/release/package_source.py'])
run('sbom',[sys.executable,'scripts/release/generate_sbom.py'])
archive=OUT/f'HYDRA_V{version}_source.zip'; sbom=OUT/f'HYDRA_V{version}_sbom.cdx.json';archive_sha=sha(archive);sbom_sha=sha(sbom)

work=OUT/'work/release-extracted'; marker=work/'.hydra-source-sha256'
if not marker.is_file() or marker.read_text(encoding='utf-8').strip()!=archive_sha:
 if work.exists(): shutil.rmtree(work)
 work.mkdir(parents=True,exist_ok=True)
 with zipfile.ZipFile(archive) as z:
  for info in z.infolist():
   rel=Path(info.filename)
   if rel.is_absolute() or '..' in rel.parts: raise SystemExit(write_report('FAIL',{'reason':'unsafe package path'}))
  z.extractall(work)
 marker.write_text(archive_sha+'\n',encoding='utf-8')

run('package-integrity',[sys.executable,'scripts/validation/verify_manifest.py'],cwd=work)
run('extracted-release-metadata',[sys.executable,'scripts/release/verify_release_metadata.py'],cwd=work)
run('extracted-fix99-100',[sys.executable,'scripts/validation/verify_r5c13_fix99_100_release_closure.py'],cwd=work)
# El paquete fuente no contiene node_modules. Algunos gates estructurales ejecutan
# TypeScript, por lo que sus dependencias deben restaurarse antes de validar el árbol
# extraído. Esto también mantiene la cualificación independiente de un tsc global.
frontend=work/'frontend';npm=['npm.cmd','ci','--include=dev'] if os.name=='nt' else ['npm','ci','--include=dev']
run('extracted-npm-ci',npm,cwd=frontend,timeout=3600)
run('extracted-structural-gate',[sys.executable,'scripts/validation/verify_final_tree.py','--structural-only'],cwd=work,timeout=7200)
if args.full:
 run('extracted-full-tree',[sys.executable,'scripts/validation/verify_final_tree.py','--ci'],cwd=work,timeout=10800)
run('extracted-repackage',[sys.executable,'scripts/release/package_source.py'],cwd=work)
run('extracted-sbom',[sys.executable,'scripts/release/generate_sbom.py'],cwd=work)
rebuilt=work/'target/release'/archive.name;rebuilt_sbom=work/'target/release'/sbom.name
reproducible=rebuilt.is_file() and sha(rebuilt)==archive_sha;sbom_reproducible=rebuilt_sbom.is_file() and sha(rebuilt_sbom)==sbom_sha
runner.record('reproducible-package',reproducible,f'original={archive_sha} rebuilt={sha(rebuilt) if rebuilt.is_file() else "MISSING"}')
runner.record('reproducible-sbom',sbom_reproducible,f'original={sbom_sha} rebuilt={sha(rebuilt_sbom) if rebuilt_sbom.is_file() else "MISSING"}')
if not reproducible or not sbom_reproducible: raise SystemExit(write_report('FAIL'))
certificate={'sourceArchive':archive.name,'sourceSha256':archive_sha,'sbom':sbom.name,'sbomSha256':sbom_sha,'qualified':True,'full':args.full,'fingerprint':runner.fingerprint}
(OUT/'release-certificate.json').write_text(json.dumps(certificate,indent=2)+'\n',encoding='utf-8')
raise SystemExit(write_report('PASS',{'certificate':certificate}))
