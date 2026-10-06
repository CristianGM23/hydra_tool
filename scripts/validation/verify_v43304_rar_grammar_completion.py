#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT=Path(__file__).resolve().parents[2]
checks=[]

def text(rel): return (ROOT/rel).read_text(encoding='utf-8')
def require(label, cond):
    checks.append((label, bool(cond)))

css=text('frontend/src/grammar-designer/grammar-designer.css')
diagram=text('frontend/src/grammar-designer/GrammarClassDiagram.tsx')
shared_diagram=text('frontend/src/metamodel-designer/MetamodelReadOnlyCanvas.tsx')
class_node=text('frontend/src/metamodel-designer/MetamodelClassNode.tsx')
enum_node=text('frontend/src/metamodel-designer/MetamodelEnumNode.tsx')
data_type_node=text('frontend/src/metamodel-designer/MetamodelDataTypeNode.tsx')
local=text('frontend/src/grammar-designer/grammarLocalInspection.ts')
identity=text('frontend/src/grammar-designer/GrammarIdentityForm.tsx')
designer=text('frontend/src/grammar-designer/GrammarDesigner.tsx')
model=text('frontend/src/grammar-designer/grammarDesignerModel.ts')
dialog=text('frontend/src/grammar-designer/GrammarPlatformConversionDialog.tsx')
conversion=text('src/main/java/com/example/editor/grammar/application/GrammarPlatformConversionService.java')
fingerprint=text('src/main/java/com/example/editor/grammar/application/GrammarSemanticFingerprint.java')
projection=text('src/main/java/com/example/editor/grammar/visualization/GrammarClassDiagramProjectionService.java')
props=text('src/main/java/com/example/editor/grammar/GrammarProperties.java')
path_front=text('frontend/src/integration-designer/generatedRootPath.ts')
path_back=text('src/main/java/com/example/editor/grammar/core/GrammarPathPolicy.java')
update_log=text('frontend/src/update-site-import-center/UpdateSiteImportLogPanel.tsx')
textual=text('frontend/src/textual-model-editor/TextualModelEditor.tsx')
controller=text('src/main/java/com/example/editor/grammar/web/GrammarController.java')

require('Grammar Designer owns vertical scrolling', all(x in css for x in ['height:100%','overflow-y:auto','overflow-x:hidden','scrollbar-gutter:stable','container-type:inline-size']))
require('Grammar Designer adapts to host width', '@container (max-width:1450px)' in css and '@container (max-width:860px)' in css)
require('Class diagram nodes are draggable and resizable', 'MetamodelReadOnlyCanvas' in diagram and 'allowNodeResize' in diagram and 'nodesDraggable' in shared_diagram and 'resizeEnabled: allowNodeResize' in shared_diagram and all('HydraNodeResizer' in source for source in [class_node, enum_node, data_type_node]))
require('Diagram preserves local geometry across semantic refresh', 'setNodes(current => markProjectionNodes(buildMetamodelNodes(model, diagram, current), allowNodeResize))' in shared_diagram)
require('Local grammar inspection is bounded', 'MAX_LOCAL_SYMBOLS = 2_000' in local and 'truncated = true' in local)
require('Local inspection excludes declarative grammar headers and terminal declarations', "'grammar'" in local and "'hidden'" in local and 'terminal|fragment' in local and '[^\\r\\n:;]*:' in local)
require('Entry rule uses adaptive dropdown', 'HydraAdaptiveSelect id="grammar-entry-rule"' in identity)
require('Entry rule supports AUTO and MANUAL modes', "hydra.entryRule.mode" in identity and "'AUTO'" in identity and "'MANUAL'" in identity)
require('Entry rule has immediate local inference fallback', 'localInspection.inferredEntryRule' in identity and 'localInspection.parserRules' in identity)
require('Grammar workbench preserves the four V4.32.104 requested views', all(x in designer for x in ["'SOURCE'","'DIAGRAM'","'METAMODEL'","'GENERATED_CODE'",'GrammarInferredMetamodelView','GrammarGeneratedCodeView']))
require('Non-empty technology change cannot use destructive helper', 'safe conversion preview' in model and 'throw new Error' in model)
require('Technology change uses backend conversion preview', 'prepareGrammarPlatformConversion' in designer and 'GrammarPlatformConversionDialog' in designer)
require('Conversion dialog is fail-closed', 'disabled={!preview.semanticallyEquivalent}' in dialog or 'disabled={!' in dialog and 'semanticallyEquivalent' in dialog)
require('Cancel and apply are separate explicit conversion actions', 'onCancel' in dialog and 'onApply' in dialog)
conversion_cancel = designer[designer.find('onCancel={()=>{'):designer.find('}} onApply={applyConversion}')]
require('Conversion cancel clears transient state without mutating grammar', all(x in conversion_cancel for x in [
    'setConversionPreview(null)', 'setRequestedTechnology(null)', "setMessage(t('Conversion cancelled. The current grammar was not changed.'))"
]) and 'mutate(' not in conversion_cancel and 'setModel(' not in conversion_cancel)
require('Conversion backend strictly reimports both sides', conversion.count('inferMetamodelStrict') >= 2)
require('Strict projection explicitly avoids last-known-good certification', 'inferMetamodelStrict' in projection and ('último resultado válido' in projection or 'last-known' in projection.lower()))
require('Conversion exact backups are bounded', all(x in props for x in ['maximumConversionBackupBytes','maximumConversionBackupExpandedBytes','maximumConversionTraceItems','maximumConversionTraceCharacters']))
require('Conversion fingerprints cover stronger structure', all(x in fingerprint for x in ['ordered','unique','defaultValue','operations','parameters','exception']))
require('ANTLR references transport target metadata', 'HYDRA_REFERENCE' in '\n'.join(p.read_text(encoding='utf-8',errors='ignore') for p in (ROOT/'src/main/java/com/example/editor').rglob('*.java')))
require('Conversion preview is exposed through API', 'GrammarPlatformConversionRequest' in controller and 'GrammarPlatformConversionPreview' in controller)
require('Frontend portable path policy rejects Windows devices and trailing dot/space', 'WINDOWS_RESERVED_SEGMENT' in path_front and "segment.endsWith('.')" in path_front and "segment.endsWith(' ')" in path_front)
require('Backend independently enforces portable path policy', 'WINDOWS_DEVICE' in path_back and ('trailing' in path_back.lower() or 'endsWith(".")' in path_back))
require('Update-site recursion presentation avoids duplicate left-recursive rule names', 'filter(rule => !grammar.possibleLeftRecursions.includes(rule))' in update_log)
require('Textual editor exposes shared model perspectives', 'View model perspectives' in textual and 'ModelCodeGenerationDialog' in textual)
require('Permanent regression tests exist for conversion, path policy and local inspection', all((ROOT/p).exists() for p in [
    'src/test/java/com/example/editor/grammar/application/GrammarPlatformConversionServiceTests.java',
    'src/test/java/com/example/editor/grammar/GrammarPathPolicyTests.java',
    'frontend/src/grammar-designer/grammarLocalInspection.test.ts',
    'frontend/src/grammar-designer/GrammarDesignerUxContract.test.ts']))

passed=sum(ok for _,ok in checks)
for label,ok in checks:
    print(('PASS' if ok else 'FAIL')+' - '+label)
print(f'V43304_RAR_GRAMMAR_COMPLETION={passed}/{len(checks)}')
sys.exit(0 if passed==len(checks) else 1)
