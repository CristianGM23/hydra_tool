from __future__ import annotations
from pathlib import Path
import json,re,sys
ROOT=Path(__file__).resolve().parents[2]
checks=[]
def check(name,condition):checks.append((name,bool(condition)))
def text(path):return (ROOT/path).read_text(encoding='utf-8',errors='replace')
def exists(path):return (ROOT/path).is_file()

# P0 baseline/regression fixes.
palette=text('frontend/src/components/Palette.tsx'); check('Palette uses representationName contract','style.representationName' in palette and 'style.name' not in palette)
qf=text('frontend/src/validation-designer/QuickFixDesigner.tsx'); check('QuickFix guided finish prop reaches editor','onFinishGuided={onFinishGuided}' in qf)
resizer=text('frontend/src/designer-ui/HydraNodeResizer.tsx'); check('Node resizer keeps defaults outside the Fast Refresh module','export const HYDRA_NODE_RESIZE_DEFAULTS' not in resizer and "from './hydraNodeResizeDefaults'" in resizer)
check('Node resize defaults split out',exists('frontend/src/designer-ui/hydraNodeResizeDefaults.ts'))
check('full final-tree release gate exists',exists('scripts/validation/verify_final_tree.py'))

# Reproducibility.
ignore=text('.gitignore'); check('runtime/build payload excluded',all(x in ignore for x in ['frontend/node_modules/','frontend/dist/','/data/','target/']))
package=text('scripts/release/package_source.py'); inventory=text('scripts/release/source_inventory.py'); check('source packager uses allowlist','source_files' in package and 'ALLOWED_ROOTS' in inventory and 'excluded(' in inventory)
check('toolchain manifest exists',exists('config/hydra-toolchain.json'))
version=json.loads(text('config/hydra-version.json'))['version']; check('version is synchronized',version in text('pom.xml') and f'"version": "{version}"' in text('frontend/package.json'))
check('CI covers Windows and Linux',all(x in text('.github/workflows/hydra-ci.yml') for x in ['ubuntu-latest','windows-latest','java-version: \'25.0.5\'','npm ci']))
check('SBOM generator inventories npm and Maven',all(x in text('scripts/release/generate_sbom.py') for x in ['pkg:npm/','pkg:maven/']))
check('POM exposes release metadata',all(x in text('pom.xml') for x in ['<licenses>','<scm>','<inceptionYear>']))

# i18n.
check('canonical Spanish convergence catalog exists',exists('frontend/src/i18n/currentSpanishCatalog.ts'))
check('canonical glossary exists',exists('docs/i18n/HYDRA_ES_GLOSSARY.csv'))
dom=text('frontend/src/i18n/HydraDomTranslator.tsx'); check('DOM translator can be disabled by module','disabledModules' in dom or 'data-hydra-i18n-explicit' in dom)
check('DOM translator publishes metrics','hydra:i18n-dom-translation' in dom)
check('full-tree i18n validator exists',exists('scripts/validation/verify_hydra_i18n_full.py'))

# Frontend architecture/performance.
for rel in ['frontend/src/model-editor/ModelEditorWorkspace.tsx','frontend/src/metamodel-designer/MetamodelCanvas.tsx','frontend/src/editor-definition/EditorRuntimeCanvas.tsx','frontend/src/event-designer/EventArchitectureVisualEditor.tsx','frontend/src/event-designer/flow/EventFlowVisualEditor.tsx','frontend/src/gamification-designer/GamificationVisualEditor.tsx']:
    t=text(rel);check(rel+' uses performance profile','onlyRenderVisibleElements' in t and 'useHydraFlowPerformance' in t)
check('shared virtual list exists',exists('frontend/src/design-system/HydraVirtualList.tsx'))
tree=text('frontend/src/design-system/HydraTree.tsx'); check('shared tree virtualizes large expanded structures','HydraVirtualList' in tree and 'virtualizationThreshold' in tree)
scale=text('frontend/src/diagram/flowPerformance.test.ts').replace('_','');check('large scale benchmark cases exist',all(value in scale for value in ['100','500','2000','5000']))
telemetry=text('frontend/src/diagram/performanceTelemetry.ts');check('diagram telemetry covers render layout serialization and commit',all(x in telemetry for x in ["'render'","'layout'","'serialization'","'commit'"]))
check('event/model orchestrators use extracted model modules',all(exists(rel) for rel in ['frontend/src/event-designer/eventDesignerModel.ts','frontend/src/event-designer/flow/eventFlowDesignerModel.ts','frontend/src/gamification-designer/gamificationDesignerModel.ts','frontend/src/model-editor/modelEditorDocumentView.ts']))

# I/O/storage.
production='\n'.join(p.read_text(encoding='utf-8',errors='ignore') for p in (ROOT/'src/main/java').rglob('*.java'))
check('no Files.readAllBytes in production','Files.readAllBytes' not in production)
check('bounded IO abstraction exists',exists('src/main/java/com/example/editor/io/BoundedIo.java'))
check('transactional storage SPI exists',exists('src/main/java/com/example/editor/storage/TransactionalStorage.java') and exists('src/main/java/com/example/editor/storage/LocalTransactionalStorage.java'))
archive=text('src/main/java/com/example/editor/metamodel/importing/updatesite/UpdateSiteArchiveReader.java');check('archive ratio/time/cpu budgets enforced',all(x in archive for x in ['maxCompressionRatio','deadlineNanos','maxCpuNanos']))

# Concurrency/bounds.
gam=text('src/main/java/com/example/editor/gamification/runtime/GamificationRuntimeService.java');check('gamification lock map removed','ResourceStripedLock' in gam and 'ConcurrentHashMap<String, Object> locks' not in gam)
check('working copies are bounded','getMaximumWorkingCopiesInMemory' in text('src/main/java/com/example/editor/definition/runtime/session/EditorSessionWorkingCopyStore.java'))
check('shared blocking IO executor is consumed','@Qualifier("hydraBlockingIoExecutor")' in production)
check('shared process and transfer permit pool exists and is used',exists('src/main/java/com/example/editor/concurrent/BoundedPermitPool.java') and production.count('BoundedPermitPool')>=4)
check('all dynamic maps and synchronized scopes have review manifests',exists('config/hydra-concurrency-state-budgets.json') and exists('config/hydra-synchronization-review.json'))
check('runtime capacity endpoint exists',exists('src/main/java/com/example/editor/runtime/RuntimeCapacityController.java'))

# Update Site modularization / prepared import.
controller=text('src/main/java/com/example/editor/metamodel/importing/updatesite/UpdateSiteImportController.java')
endpoint_count=len(re.findall(r'@(Get|Post|Put|Delete|Patch)Mapping',controller))
check('UpdateSite core controller is reduced to the audited multipart entry surface',endpoint_count<=3 and 'withStaged(files' in controller and 'withStaged(List.of(file)' in controller)
for rel in ['UpdateSiteSourceController.java','UpdateSiteAnalysisController.java','UpdateSiteRecoveryController.java','UpdateSiteMaterializationController.java','UpdateSiteAdapterController.java','UpdateSitePerformanceController.java']:
    check(rel+' exists',exists('src/main/java/com/example/editor/metamodel/importing/updatesite/'+rel))
prepared=text('src/main/java/com/example/editor/metamodel/importing/MetamodelImportController.java');check('prepared import job endpoint remains','/analysis-jobs/{jobId}/import' in prepared and 'analysisJobService.prepared(jobId)' in prepared)
props=text('src/main/java/com/example/editor/metamodel/importing/updatesite/UpdateSiteImportProperties.java');check('cache TTL snapshot retention and archive budgets are configurable',all(x in props for x in ['analysisCacheTtlSeconds','repositorySnapshotMaxCount','repositorySnapshotRetentionHours','maxArchiveCpuMillis','maxCompressionRatio']))
check('Update Site parser fuzz suite covers ZIP XML manifest and grammar',exists('src/test/java/com/example/editor/metamodel/importing/updatesite/UpdateSiteParserFuzzTests.java') and all(x in text('src/test/java/com/example/editor/metamodel/importing/updatesite/UpdateSiteParserFuzzTests.java') for x in ['Zip','Xml','Manifest','Grammar']))
bench=text('src/test/java/com/example/editor/metamodel/importing/updatesite/UpdateSiteScaleBenchmark.java').replace('_','');check('Update Site benchmark covers 1k 10k and 50k entries',all(x in bench for x in ['1000','10000','50000']))

failed=[name for name,ok in checks if not ok]
for i,(name,ok) in enumerate(checks,1):print(f'[{i:02}] {"PASS" if ok else "FAIL"} - {name}')
print(f'P01_CONTRACTS={len(checks)-len(failed)}/{len(checks)}')
sys.exit(1 if failed else 0)
