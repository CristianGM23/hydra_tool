from pathlib import Path
import json, os, shutil, subprocess, tempfile

ROOT=Path(__file__).resolve().parents[2]

def builder_version(text):
    marker = "VERSION = '"
    start = text.find(marker)
    if start < 0:
        return ()
    value = text[start + len(marker):].split("'", 1)[0]
    try:
        return tuple(int(part) for part in value.split('.'))
    except ValueError:
        return ()

checks=[]
def check(name, cond):
    if not cond: raise SystemExit(f'FAIL - {name}')
    print(f'PASS - {name}'); checks.append(name)

support=(ROOT/'src/main/java/com/example/editor/transformation/nativeexec/TransformationNativeRuntimeSupport.java').read_text()
adapter=(ROOT/'src/main/java/com/example/editor/transformation/export/NativeCodeTransformationLanguageAdapterSupport.java').read_text()
builder=(ROOT/'scripts/toolchains/build_official_transformation_bridges.py').read_text()
check('Java Python Go advertise native M2M and M2T capability', support.count('nativeCodeM2mM2tCapability()') >= 4)
check('reverse is blocked for non-M2M native families', 'kind != TransformationKind.M2M && direction == TransformationExecutionDirection.REVERSE' in support)
check('managed adapter emits exact M2T native plan', 'NativeM2TPlan' in adapter and 'NativeGenerationRulePlan' in adapter and 'TransformationExportFidelity.EXACT' in adapter)
check('managed M2T remains fail closed for guards', 'Managed native M2T execution does not evaluate generation guards yet.' in adapter)
check('official package builder has advanced beyond the retained 1.2.0 package', builder_version(builder) >= (2, 0, 0))

java_exec=Path(shutil.which('java') or '')
check('Java executable is available for bridge harness', bool(java_exec) and java_exec.exists())
source={'schemaVersion':'1.0','id':'source.model','metamodelId':'meta','metamodelEtag':'','name':'People','rootElementIds':['p1'],'elements':[{'id':'p1','classId':'Person','attributes':{'name':'Alice','active':True},'annotations':{}}],'relations':[],'annotations':{}}
plan={'schemaVersion':'1.0','kind':'M2T','transformationId':'tx.m2t','transformationName':'Gen','generationRules':[{'ruleId':'r1','contextTypeId':'Person','outputPathTemplate':'generated/{{attr:name}}.txt','contentTemplate':'id={{id}};class={{classId}};name={{attr:name}};active={{attr:active}}','overwritePolicy':'FAIL'}]}
for language,slot in [('JAVA','java'),('PYTHON','python'),('GO','go')]:
    payload=ROOT/'distribution/transformation-toolchains'/slot/'hydra-official-bridge'/'1.2.0'/'payload'
    check(f'{language} official 1.2.0 package exists', (payload/'HydraOfficialBridge.java').is_file())
    desc=json.loads(subprocess.check_output([str(java_exec),str(payload/'HydraOfficialBridge.java'),str(payload/'bridge.properties'),'--hydra-describe'],text=True))
    check(f'{language} advertises M2T execution', desc['executionAvailable'] is True and 'M2T' in desc['supportedKinds'])
    with tempfile.TemporaryDirectory(prefix=f'hydra-fix47-{slot}-') as tmp:
        workspace=Path(tmp); (workspace/'export/native').mkdir(parents=True); (workspace/'native-output').mkdir()
        (workspace/'source-model.json').write_text(json.dumps(source),encoding='utf-8')
        (workspace/'export/native/native-plan.json').write_text(json.dumps(plan),encoding='utf-8')
        (workspace/'transformation.json').write_text('{}',encoding='utf-8')
        request={'schemaVersion':'1.0','transformationId':'tx.m2t','language':language,'kind':'M2T','direction':'FORWARD','transformationFile':'transformation.json','sourceModelFile':'source-model.json','sourceMetamodelFile':'','targetMetamodelFile':'','exportArtifactFiles':['export/native/native-plan.json'],'outputDirectory':'native-output','resultFile':'native-result.json'}
        (workspace/'native-request.json').write_text(json.dumps(request),encoding='utf-8')
        subprocess.check_call([str(java_exec),str(payload/'HydraOfficialBridge.java'),str(payload/'bridge.properties'),str(workspace/'native-request.json')])
        result=json.loads((workspace/'native-result.json').read_text())
        artifact=result['artifacts'][0]
        content=(workspace/artifact['file']).read_text()
        check(f'{language} executes independent token M2T evidence', result['kind']=='M2T' and artifact['path']=='generated/Alice.txt' and content=='id=p1;class=Person;name=Alice;active=true')
print(f'FIX47_JAVA_PYTHON_GO_NATIVE_M2T={len(checks)}/{len(checks)} PASS')
