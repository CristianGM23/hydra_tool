from pathlib import Path
import re,sys
ROOT=Path(__file__).resolve().parents[2]
checks=[]
def text(rel): return (ROOT/rel).read_text(encoding='utf-8',errors='ignore')
def check(name,ok): checks.append((name,bool(ok))); print(('PASS' if ok else 'FAIL')+' - '+name)
app=text('frontend/src/application-shell/HydraApplicationShell.tsx')
home=text('frontend/src/application-shell/HydraHome.tsx')
publication=text('frontend/src/publication-center/PublicationCenter.tsx')
status=text('src/main/java/com/example/editor/system/SystemStatusController.java')
production='\n'.join(p.read_text(encoding='utf-8',errors='ignore') for p in (ROOT/'frontend/src').rglob('*.tsx') if '.test.' not in p.name)
check('brand renders HYDRA without release number','<strong>HYDRA</strong>' in app and not re.search(r'HYDRA\s+V?\d+\.\d+',app))
check('home does not render a HYDRA release number',not re.search(r'HYDRA\s+V?\d+\.\d+',home))
check('no generated display-version symbol',(ROOT/'frontend/src/generated/hydraVersion.ts').is_file() and 'HYDRA_DISPLAY_VERSION' not in text('frontend/src/generated/hydraVersion.ts'))
check('no visible HYDRA release literal in production TSX',not re.search(r'["\'`]HYDRA\s+V?\d+(?:\.\d+)+',production,re.I))
metamodel_ui='\n'.join(text(rel) for rel in ['frontend/src/application-shell/HydraApplicationShell.tsx','frontend/src/application-shell/HydraHome.tsx','frontend/src/metamodel-designer/MetamodelDesigner.tsx','frontend/src/metamodel-designer/MetamodelImportDialog.tsx','frontend/src/metamodel-designer/MetamodelExportDialog.tsx'] if (ROOT/rel).is_file())
check('no metamodel schema/version field label',not re.search(r'(?:label|title|heading)\s*=\s*["\'`](?:Metamodel\s+)?(?:schema\s+)?version["\'`]',metamodel_ui,re.I))
check('publication release version is internal only','HYDRA_VERSION' in publication and not re.search(r'(?:label|title)=["\'`]Version["\'`]',publication))
check('system status exposes no version field',not re.search(r'record\s+SystemStatusResponse\([^)]*\bversion\b',status,re.I))
# Known metamodel selectors must describe content, not schemas.
selectors=app+'\n'+home
check('metamodel selectors do not render schemaVersion','.schemaVersion' not in selectors)
failed=[n for n,o in checks if not o]
print(f'NO_VISUAL_VERSIONS={len(checks)-len(failed)}/{len(checks)}')
sys.exit(1 if failed else 0)
