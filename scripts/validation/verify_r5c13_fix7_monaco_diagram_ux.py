#!/usr/bin/env python3
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]
checks=[]

def check(name, condition):
    checks.append((name,bool(condition)))

def text(path):
    return (ROOT/path).read_text(encoding='utf-8')

monaco=text(Path('frontend/src/grammar-designer/GrammarMonacoEditor.tsx'))
custom=text(Path('frontend/src/grammar-designer/textualCustomization.ts'))
designer=text(Path('frontend/src/grammar-designer/GrammarDesigner.tsx'))
diagram=text(Path('frontend/src/grammar-designer/GrammarClassDiagram.tsx'))
shared=text(Path('frontend/src/metamodel-designer/MetamodelReadOnlyCanvas.tsx'))
relation=text(Path('frontend/src/metamodel-designer/MetamodelRelationLayer.tsx'))
metamodel_node=text(Path('frontend/src/metamodel-designer/MetamodelClassNode.tsx'))
css=text(Path('frontend/src/grammar-designer/grammar-designer.css'))
responsive=text(Path('frontend/src/grammar-designer/grammar-designer-responsive.css'))
completion_css=text(Path('frontend/src/grammar-designer/grammar-editor-completion.css'))
textual=text(Path('frontend/src/textual-model-editor/TextualModelEditor.tsx'))

check('monaco uses decoration classes instead of unsupported foreground option', 'inlineClassName:styleClassName(style)' in monaco and 'foreground:style.color' not in monaco)
check('monaco customization injects scoped css', 'monacoCustomizationCss(scopeClass,customization)' in monaco)
check('monaco background follows customization', 'background-color:${c.background}!important' in monaco)
check('monaco base foreground follows customization', 'color:${c.foreground}!important' in monaco)
check('monaco contextual hover uses runtime mapping', 'hoverForToken(current,value)' in monaco)
check('preview and runtime share effective customization', 'effectiveTextualCustomization(model)' in text(Path('frontend/src/grammar-designer/GrammarTextualCustomization.tsx')) and 'effectiveTextualCustomization(grammarModel)' in textual)
check('runtime hints map grammar literals to parser rules', 'literalSymbols[token[1]]=name' in custom)
check('runtime hints map identifiers to terminals', 'identifierTerminal' in custom and 'runtimeSymbolForToken' in custom)
check('grammar source passes code actions into monaco', 'codeActions={currentAnalysis?.codeActions??[]}' in designer or 'codeActions={effectiveCodeActions}' in designer)
check('grammar source passes symbols into monaco', 'symbols={currentAnalysis?.symbols??localInspection.symbols}' in designer)
check('grammar source applies code actions from monaco surface', 'onApplyCodeAction={applyAction}' in designer)
check('monaco diagnostic marker includes cause', 'Cause: ${item.cause}' in monaco)
check('monaco diagnostic marker includes impact', 'Impact: ${item.consequence}' in monaco)
check('monaco diagnostic marker includes recommendation', 'Recommendation: ${item.solution}' in monaco)
check('monaco feedback surface exists', 'grammar-monaco-feedback' in monaco and 'grammar-monaco-feedback' in completion_css)
check('monaco feedback exposes quick fixes', 'onApplyCodeAction(action)' in monaco and 'applyDiagnosticCandidate' in monaco)
check('monaco uses modern decoration collections', 'createDecorationsCollection' in monaco and 'diagnosticDecorationCollection.current.set(next)' in monaco)
check('monaco exposes grammar definitions', 'registerDefinitionProvider' in monaco)
check('monaco exposes grammar references', 'registerReferenceProvider' in monaco)
check('monaco exposes native code actions', 'registerCodeActionProvider' in monaco)
check('inferred diagram reuses canonical metamodel canvas', 'MetamodelReadOnlyCanvas' in diagram)
check('shared canvas reuses canonical class node', 'MetamodelClassNode' in shared)
check('shared canvas reuses canonical edge builder', 'buildMetamodelEdges' in shared)
check('shared canvas reuses canonical relation layer', 'MetamodelRelationLayer' in shared)
check('canonical relations include composition diamond', 'metamodel-containment-diamond' in relation)
check('canonical relations include aggregation diamond', 'metamodel-aggregation-diamond' in relation)
check('canonical relations include directional arrow', 'metamodel-reference-arrow' in relation)
check('canonical relations include inheritance triangle', 'metamodel-inheritance-triangle' in relation)
check('shared inferred nodes keep semantic read-only state while allowing transient visual resize', 'readOnly: true' in shared and 'resizeEnabled: allowNodeResize' in shared and 'data.resizeEnabled !== false' in metamodel_node)
check('shared inferred layout auto sizes feature rows', 'rowUnits' in shared and 'width' in shared and 'height' in shared)
check('desktop identity row uses seven columns', 'grid-template-columns:repeat(7,minmax(0,1fr))' in css or 'grid-template-columns:minmax(0,.9fr)' in responsive)
check('metamodel stays in same desktop row', '.grammar-identity-grid .hydra-ui-form-field.grammar-metamodel-field{grid-column:auto' in css)
check('header buttons and controls share fixed height', ('min-height:40px;height:40px' in css or 'height:44px' in responsive) and ('height:40px;min-height:40px' in css or 'height:44px;min-height:44px' in responsive))
check('desktop hints do not force a third row', '.grammar-identity-grid .hydra-ui-form-hint{display:none}' in css)

failed=[name for name,ok in checks if not ok]
for name,ok in checks:
    print(f"[{'OK' if ok else 'FAIL'}] {name}")
print(f"\n{len(checks)-len(failed)}/{len(checks)} checks passed")
if failed:
    raise SystemExit(1)
