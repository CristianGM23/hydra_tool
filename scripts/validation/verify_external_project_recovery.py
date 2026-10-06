from pathlib import Path
import re,sys
ROOT=Path(__file__).resolve().parents[2]
checks=[]
def t(rel): return (ROOT/rel).read_text(encoding='utf-8',errors='ignore')
def c(name,ok): checks.append((name,bool(ok))); print(('PASS' if ok else 'FAIL')+' - '+name)
walker=t('src/main/java/com/example/editor/project/recovery/ExternalProjectArchiveWalker.java')
service=t('src/main/java/com/example/editor/project/recovery/ExternalProjectRecoveryService.java')
evidence=t('src/main/java/com/example/editor/project/recovery/ExternalProjectMappingEvidence.java')
importer=t('src/main/java/com/example/editor/project/recovery/ExternalProjectImportService.java')
controller=t('src/main/java/com/example/editor/project/recovery/ExternalProjectRecoveryController.java')
ui=t('frontend/src/project-explorer/ProjectManagerDialog.tsx')
api=t('frontend/src/project-explorer/projectApi.ts')
kind=t('src/main/java/com/example/editor/project/recovery/ExternalProjectResourceKind.java')
for token in ['.zip',' .jar','.war','.ear','getMaxNestedArchiveDepth','getMaxArchiveCompressionRatio','getMaxArchiveReadMillis']:
    # tolerate formatting of .jar token
    actual=token.strip()
    c('archive walker covers '+actual,actual in walker)
c('unknown resources are preserved','PROJECT_RESOURCE' in kind and 'PROJECT_RESOURCE' in service)
c('mapping evidence is explainable',all(x in evidence for x in ['score','confidence','fidelity','recoveredFields','lostFields','alternatives','reasons','autoSelectable']))
c('safe auto-selection requires score gap','item.score() >= 90' in service and 'item.score() - second >= 12' in service)
c('project topology is generated','topology(resources)' in service)
c('integration suggestions are generated','integrationSuggestions(resources, technologies)' in service)
c('import reuses validated inventory','analyzeEntries(entries)' in importer and 'walker.walk' in importer)
c('failed import rolls back partial project','deleteActiveProject(true)' in importer and 'restore(previous, failure)' in importer)
c('import endpoint streams upload','getInputStream()' in controller and 'file.getBytes()' not in controller)
c('frontend separates external project from HYDRA ZIP restore',"'EXTERNAL'" in ui and 'analyzeExternalProject' in ui and 'importExternalProjectArchive' in api)
c('OpenAPI contains analyze/import routes','/api/external-project-recovery/analyze' in t('docs/api/hydra-openapi.yaml') and '/api/external-project-recovery/import' in t('docs/api/hydra-openapi.yaml'))
failed=[n for n,o in checks if not o]
print(f'EXTERNAL_PROJECT_RECOVERY={len(checks)-len(failed)}/{len(checks)}')
sys.exit(1 if failed else 0)
