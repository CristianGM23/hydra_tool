from pathlib import Path
import json, shutil, subprocess, tempfile
ROOT=Path(__file__).resolve().parents[2]
checks=[]
def check(name,cond):
    if not cond: raise SystemExit('FAIL - '+name)
    print('PASS - '+name);checks.append(name)
node=shutil.which('node'); java=shutil.which('java')
check('Node.js available', bool(node)); check('Java available for official launcher', bool(java))
support=(ROOT/'src/main/java/com/example/editor/transformation/nativeexec/TransformationNativeRuntimeSupport.java').read_text()
enum=(ROOT/'src/main/java/com/example/editor/transformation/export/TransformationExportLanguage.java').read_text()
check('JavaScript export language registered','JAVASCRIPT' in enum and 'TransformationExportLanguage.JAVASCRIPT' in support)
check('TypeScript export language registered','TYPESCRIPT' in enum and 'TransformationExportLanguage.TYPESCRIPT' in support)
source={'schemaVersion':'1.0','id':'source.model','metamodelId':'meta','metamodelEtag':'','name':'People','rootElementIds':['p1'],'elements':[{'id':'p1','classId':'Person','attributes':{'name':'Alice','active':True},'annotations':{}}],'relations':[],'annotations':{}}
plan={'schemaVersion':'1.0','kind':'M2T','transformationId':'tx.m2t','transformationName':'Gen','generationRules':[{'ruleId':'r1','contextTypeId':'Person','outputPathTemplate':'generated/{{attr:name}}.txt','contentTemplate':'id={{id}};class={{classId}};name={{attr:name}};active={{attr:active}}','overwritePolicy':'FAIL'}]}
for language,slot in [('JAVASCRIPT','javascript'),('TYPESCRIPT','typescript')]:
    payload=ROOT/'distribution/transformation-toolchains'/slot/'hydra-official-bridge'/'2.1.0'/'payload'
    check(f'{language} managed package exists',(payload/'toolchain.json').is_file())
    desc=json.loads(subprocess.check_output([java,str(payload/'HydraOfficialBridge.java'),str(payload/'bridge.properties'),'--hydra-describe'],text=True))
    check(f'{language} advertises execution',desc['executionAvailable'] is True and 'M2M' in desc['supportedKinds'] and 'M2T' in desc['supportedKinds'])
    with tempfile.TemporaryDirectory(prefix='hydra-fix57-') as tmp:
        ws=Path(tmp);(ws/'export/native').mkdir(parents=True);(ws/'native-output').mkdir()
        (ws/'source-model.json').write_text(json.dumps(source),encoding='utf-8');(ws/'export/native/native-plan.json').write_text(json.dumps(plan),encoding='utf-8');(ws/'transformation.json').write_text('{}',encoding='utf-8')
        request={'schemaVersion':'1.0','transformationId':'tx.m2t','language':language,'kind':'M2T','direction':'FORWARD','transformationFile':'transformation.json','sourceModelFile':'source-model.json','sourceMetamodelFile':'','targetMetamodelFile':'','exportArtifactFiles':['export/native/native-plan.json'],'outputDirectory':'native-output','resultFile':'native-result.json'}
        (ws/'native-request.json').write_text(json.dumps(request),encoding='utf-8')
        subprocess.check_call([java,str(payload/'HydraOfficialBridge.java'),str(payload/'bridge.properties'),str(ws/'native-request.json')])
        result=json.loads((ws/'native-result.json').read_text()); art=result['artifacts'][0]; content=(ws/art['file']).read_text()
        check(f'{language} executes M2T independently',result['language']==language and art['path']=='generated/Alice.txt' and content=='id=p1;class=Person;name=Alice;active=true')

    plan_m2m={'schemaVersion':'1.0','kind':'M2M','transformationId':'tx.m2m','transformationName':'CopyPerson','targetMetamodelId':'target.meta','targetMetamodelFingerprint':'fp-target','rules':[{'ruleId':'r1','sourceTypeIds':['Person'],'targetTypeId':'Employee','bindings':[{'kind':'DIRECT','sourceFeatureId':'name','targetFeatureId':'fullName','literalValue':None},{'kind':'CONSTANT','sourceFeatureId':'','targetFeatureId':'active','literalValue':True}]}]}
    with tempfile.TemporaryDirectory(prefix='hydra-fix57-m2m-') as tmp:
        ws=Path(tmp);(ws/'export/native').mkdir(parents=True);(ws/'native-output').mkdir()
        (ws/'source-model.json').write_text(json.dumps(source),encoding='utf-8');(ws/'export/native/native-plan.json').write_text(json.dumps(plan_m2m),encoding='utf-8');(ws/'transformation.json').write_text('{}',encoding='utf-8')
        request={'schemaVersion':'1.0','transformationId':'tx.m2m','language':language,'kind':'M2M','direction':'FORWARD','transformationFile':'transformation.json','sourceModelFile':'source-model.json','sourceMetamodelFile':'','targetMetamodelFile':'','exportArtifactFiles':['export/native/native-plan.json'],'outputDirectory':'native-output','resultFile':'native-result.json'}
        (ws/'native-request.json').write_text(json.dumps(request),encoding='utf-8')
        subprocess.check_call([java,str(payload/'HydraOfficialBridge.java'),str(payload/'bridge.properties'),str(ws/'native-request.json')])
        result=json.loads((ws/'native-result.json').read_text()); model=json.loads((ws/result['targetModelFile']).read_text())
        check(f'{language} executes M2M independently',result['language']==language and model['elements'][0]['classId']=='Employee' and model['elements'][0]['attributes']=={'fullName':'Alice','active':True})
print(f'FIX57_JS_TS_NATIVE={len(checks)}/{len(checks)} PASS')
