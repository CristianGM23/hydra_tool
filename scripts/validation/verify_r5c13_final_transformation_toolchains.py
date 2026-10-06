from pathlib import Path
import hashlib, json, shutil, subprocess, tempfile, zipfile

ROOT=Path(__file__).resolve().parents[2]
VERSION='2.1.0'
checks=[]
def check(name, cond):
    if not cond: raise SystemExit(f'FAIL - {name}')
    print(f'PASS - {name}'); checks.append(name)

def sha(path): return hashlib.sha256(path.read_bytes()).hexdigest()

builder=(ROOT/'scripts/toolchains/build_official_transformation_bridges.py').read_text(encoding='utf-8')
assembler=(ROOT/'scripts/toolchains/assemble_full_transformation_toolchain.py').read_text(encoding='utf-8')
check('official bridge release is 2.1.0', "VERSION = '2.1.0'" in builder)
check('full toolchain assembler is isolated per language', 'if not payload.is_dir()' in assembler and 'Never rebuild all packages here' in assembler)
check('full toolchain assembler rejects symlinks', 'item.is_symlink()' in assembler)

java=shutil.which('java')
check('Java is available', bool(java))
external={
'atl':('ATL','M2M','hydra-atl-runtime.jar','hydra.atl.runtime.HydraAtlNativeRuntime'),
'etl':('ETL','M2M','hydra-etl-runtime.jar','hydra.etl.runtime.HydraEtlNativeRuntime'),
'acceleo':('ACCELEO','M2T','hydra-acceleo-runtime.jar','hydra.acceleo.runtime.HydraAcceleoNativeRuntime'),
'egl':('EGL','M2T','hydra-egl-runtime.jar','hydra.egl.runtime.HydraEglNativeRuntime'),
'xtend':('XTEND','M2M,M2T','hydra-xtend-runtime.jar','hydra.xtend.runtime.HydraXtendNativeRuntime'),
}
for slot,(language,kinds,driver,main) in external.items():
    payload=ROOT/'distribution/transformation-toolchains'/slot/'hydra-official-bridge'/VERSION/'payload'
    profile=(payload/'bridge.properties').read_text(encoding='utf-8')
    check(f'{language} base profile uses managed JVM backend', 'execution.backend=JVM_CLASSPATH' in profile and 'execution.ready=true' in profile)
    desc=json.loads(subprocess.check_output([java,str(payload/'HydraOfficialBridge.java'),str(payload/'bridge.properties'),'--hydra-describe'],text=True))
    check(f'{language} base package fails closed without third-party engine', desc['toolAvailable'] is False and desc['executionAvailable'] is False)

# Existing independent runtimes still execute M2T after the unified release bump.
source={'schemaVersion':'1.0','id':'source.model','metamodelId':'meta','metamodelEtag':'','name':'People','rootElementIds':['p1'],'elements':[{'id':'p1','classId':'Person','attributes':{'name':'Alice','active':True},'annotations':{}}],'relations':[],'annotations':{}}
plan={'schemaVersion':'1.0','kind':'M2T','transformationId':'tx.m2t','transformationName':'Gen','generationRules':[{'ruleId':'r1','contextTypeId':'Person','outputPathTemplate':'generated/{{attr:name}}.txt','contentTemplate':'id={{id}};class={{classId}};name={{attr:name}};active={{attr:active}}','overwritePolicy':'FAIL'}]}
for language,slot in [('JAVA','java'),('PYTHON','python'),('GO','go'),('JAVASCRIPT','javascript'),('TYPESCRIPT','typescript')]:
    payload=ROOT/'distribution/transformation-toolchains'/slot/'hydra-official-bridge'/VERSION/'payload'
    desc=json.loads(subprocess.check_output([java,str(payload/'HydraOfficialBridge.java'),str(payload/'bridge.properties'),'--hydra-describe'],text=True))
    check(f'{language} remains executable at 2.1.0', desc['executionAvailable'] is True and 'M2T' in desc['supportedKinds'])
    with tempfile.TemporaryDirectory(prefix='hydra-final-') as tmp:
        w=Path(tmp); (w/'export/native').mkdir(parents=True); (w/'native-output').mkdir()
        (w/'source-model.json').write_text(json.dumps(source),encoding='utf-8')
        (w/'export/native/native-plan.json').write_text(json.dumps(plan),encoding='utf-8')
        (w/'transformation.json').write_text('{}',encoding='utf-8')
        req={'schemaVersion':'1.0','transformationId':'tx.m2t','language':language,'kind':'M2T','direction':'FORWARD','transformationFile':'transformation.json','sourceModelFile':'source-model.json','sourceMetamodelFile':'','targetMetamodelFile':'','exportArtifactFiles':['export/native/native-plan.json'],'outputDirectory':'native-output','resultFile':'native-result.json'}
        (w/'native-request.json').write_text(json.dumps(req),encoding='utf-8')
        subprocess.check_call([java,str(payload/'HydraOfficialBridge.java'),str(payload/'bridge.properties'),str(w/'native-request.json')])
        result=json.loads((w/'native-result.json').read_text())
        art=result['artifacts'][0]
        check(f'{language} native M2T evidence remains exact', art['path']=='generated/Alice.txt' and (w/art['file']).read_text()=='id=p1;class=Person;name=Alice;active=true')

# Every base package has complete checksum coverage.
for slot in ['atl','etl','acceleo','egl','java','xtend','python','go','javascript','typescript']:
    base=ROOT/'distribution/transformation-toolchains'/slot/'hydra-official-bridge'/VERSION
    manifest=json.loads((base/'package.json').read_text(encoding='utf-8'))
    payload=base/'payload'
    actual={p.relative_to(payload).as_posix():sha(p) for p in sorted(payload.rglob('*')) if p.is_file()}
    check(f'{slot} package checksum coverage is exact', manifest['sha256']==actual and manifest['bridgeVersion']==VERSION)

print(f'FINAL_TRANSFORMATION_TOOLCHAINS={len(checks)}/{len(checks)} PASS')
