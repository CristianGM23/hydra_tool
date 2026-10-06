from __future__ import annotations
import hashlib,json,platform,sys,time
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'scripts/toolchain'))
from hydra_environment import collect_toolchain,sha256_file  # noqa:E402
OUT=ROOT/'target/release';OUT.mkdir(parents=True,exist_ok=True)
version=json.loads((ROOT/'config/hydra-version.json').read_text(encoding='utf-8'))['version']
discovery=json.loads((ROOT/'config/toolchain-discovery-policy.json').read_text(encoding='utf-8'));java_policy=discovery['java']

def optional(path:Path):
 return {'path':str(path.relative_to(ROOT)) if path.exists() else str(path),'sha256':sha256_file(path)} if path.is_file() else None
archive=OUT/f'HYDRA_V{version}_source.zip';sbom=OUT/f'HYDRA_V{version}_sbom.cdx.json'
qualification=OUT/'release-qualification.json';rc1=OUT/'rc1-qualification.json';rc2=OUT/'rc2-qualification.json'
manifest=ROOT/'MANIFEST_SHA256.txt'
payload={
 'schemaVersion':'1.0','releaseVersion':version,'generatedAtEpochMs':int(time.time()*1000),
 'source':{'manifest':optional(manifest),'archive':optional(archive),'sbom':optional(sbom)},
 'qualification':{'release':optional(qualification),'rc1':optional(rc1),'rc2':optional(rc2)},
 'toolchain':collect_toolchain(),
 'buildContract':{'bytecodeTarget':int(java_policy['bytecodeTarget']),'qualificationJdkFeatures':[int(item) for item in java_policy['qualificationFeatures']]},
 'platform':{'system':platform.system(),'release':platform.release(),'version':platform.version(),'machine':platform.machine(),'python':platform.python_version()},
}
out=OUT/'release-provenance.json';out.write_text(json.dumps(payload,indent=2,ensure_ascii=False)+'\n',encoding='utf-8')
digest=hashlib.sha256(out.read_bytes()).hexdigest();(OUT/'release-provenance.json.sha256').write_text(f'{digest}  {out.name}\n',encoding='utf-8')
print(out);print('sha256',digest)
