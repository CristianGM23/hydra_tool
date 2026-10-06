from pathlib import Path
import json, subprocess, tempfile, os, shutil

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
base=(ROOT/'src/main/java/com/example/editor/transformation/export/NativeCodeTransformationLanguageAdapterSupport.java').read_text()
bridge=(ROOT/'toolchains/official/HydraOfficialBridge.java').read_text()
builder=(ROOT/'scripts/toolchains/build_official_transformation_bridges.py').read_text()

check('Java Python and Go are promoted to native directional M2M capability', all(x in support for x in [
    'TransformationExportLanguage.JAVA','TransformationExportLanguage.PYTHON','TransformationExportLanguage.GO','nativeM2mDirectionalCapability()']))
check('Xtend remains registered while later releases may promote its managed backend', 'TransformationExportLanguage.XTEND' in support)
check('managed code export preserves M2T canonical fallback', 'return super.export(document, source, target)' in base)
check('native subset blocks guards', 'GUARD_NOT_NATIVE' in base)
check('native subset blocks expressions and trace-equivalent mappings', 'BINDING_NOT_NATIVE' in base)
check('native subset blocks value-policy approximation', 'VALUE_POLICY_NOT_NATIVE' in base)
check('native subset emits exact portable plan', 'native/native-plan.json' in base and 'TransformationExportFidelity.EXACT' in base)
check('native subset carries descendants for canonical inheritance matching', 'MetaRelationKind.INHERITANCE' in base and 'sourceTypes(source, sourceClass.id())' in base)
check('official bridge has bounded Java source backend', 'case "JAVA_SOURCE"' in bridge and 'waitFor(90, TimeUnit.SECONDS)' in bridge)
check('official bridge has Python backend', 'case "PYTHON"' in bridge)
check('official bridge has Go source backend', 'case "GO_SOURCE"' in bridge)
check('official bridge uses fixed runtime files from its own bundle', 'native.runtime.file' in bridge and 'safeBundleFile(bundle, runtimeValue)' in bridge)
check('package builder ships Java Python and Go runtime implementations', all(x in builder for x in ['HydraJavaNativeRuntime.java','HydraPythonNativeRuntime.py','HydraGoNativeRuntime.go']))
check('official bridge builder has advanced beyond the retained 1.1.0 package', builder_version(builder) >= (2, 0, 0))

java=os.environ.get('JAVA_HOME')
java_exec=(Path(java)/'bin'/'java') if java else Path(shutil.which('java') or '')
if os.name=='nt' and java and not java_exec.exists(): java_exec=Path(java)/'bin'/'java.exe'
check('Java executable is available for bridge harness', bool(java_exec) and java_exec.exists())

for language,slot in [('JAVA','java'),('PYTHON','python'),('GO','go')]:
    payload=ROOT/'distribution/transformation-toolchains'/slot/'hydra-official-bridge'/'1.1.0'/'payload'
    check(f'{language} official package 1.1.0 exists', (payload/'HydraOfficialBridge.java').is_file())
    description=subprocess.check_output([str(java_exec),str(payload/'HydraOfficialBridge.java'),str(payload/'bridge.properties'),'--hydra-describe'],text=True)
    desc=json.loads(description)
    check(f'{language} bridge diagnoses independent M2M execution', desc['executionAvailable'] is True and desc['supportedKinds']==['M2M'] and desc['supportedDirections']==['FORWARD','REVERSE'])
    with tempfile.TemporaryDirectory(prefix=f'hydra-fix46-{slot}-') as tmp:
        workspace=Path(tmp); (workspace/'export/native').mkdir(parents=True); (workspace/'native-output').mkdir()
        source={'schemaVersion':'1.0','id':'source.model','metamodelId':'source.meta','metamodelEtag':'s1','name':'People','rootElementIds':['p1'],'elements':[{'id':'p1','classId':'Person','attributes':{'name':'Alice','age':7},'annotations':{}}],'relations':[],'annotations':{}}
        plan={'schemaVersion':'1.0','transformationId':'tx.demo','transformationName':'Demo','targetMetamodelId':'target.meta','targetMetamodelFingerprint':'t1','rules':[{'ruleId':'r1','sourceTypeIds':['Person'],'targetTypeId':'Customer','bindings':[{'kind':'DIRECT','sourceFeatureId':'name','targetFeatureId':'label','literalValue':None},{'kind':'CONSTANT','sourceFeatureId':'','targetFeatureId':'active','literalValue':True}]}]}
        (workspace/'source-model.json').write_text(json.dumps(source),encoding='utf-8')
        (workspace/'export/native/native-plan.json').write_text(json.dumps(plan),encoding='utf-8')
        request={'schemaVersion':'1.0','transformationId':'tx.demo','language':language,'kind':'M2M','transformationFile':'transformation.json','sourceModelFile':'source-model.json','sourceMetamodelFile':'','targetMetamodelFile':'','exportArtifactFiles':['export/native/native-plan.json'],'outputDirectory':'native-output','resultFile':'native-result.json'}
        (workspace/'native-request.json').write_text(json.dumps(request),encoding='utf-8')
        subprocess.check_call([str(java_exec),str(payload/'HydraOfficialBridge.java'),str(payload/'bridge.properties'),str(workspace/'native-request.json')])
        result=json.loads((workspace/'native-result.json').read_text())
        model=json.loads((workspace/'native-output/target-model.json').read_text())
        check(f'{language} bridge executes native direct/constant M2M subset', result['language']==language and model['metamodelId']=='target.meta' and model['elements'][0]['attributes']=={'label':'Alice','active':True})

print(f'FIX46_JAVA_PYTHON_GO_NATIVE_EXECUTION={len(checks)}/{len(checks)} PASS')
