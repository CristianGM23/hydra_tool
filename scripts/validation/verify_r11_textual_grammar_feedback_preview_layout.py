from pathlib import Path
root=Path(__file__).resolve().parents[2]
checks=[]
def text(path): return (root/path).read_text(encoding='utf-8')
def check(label, condition):
    checks.append((label,bool(condition)))
    print(('PASS' if condition else 'FAIL')+' - '+label)

editor=text('frontend/src/textual-model-editor/TextualModelEditor.tsx')
problems=text('frontend/src/textual-model-editor/TextualModelProblems.tsx')
status=text('frontend/src/grammar-designer/GrammarStatusDock.tsx')
designer=text('frontend/src/grammar-designer/GrammarDesigner.tsx')
customization=text('frontend/src/grammar-designer/GrammarTextualCustomization.tsx')
example=text('frontend/src/grammar-designer/grammarTextualExample.ts')
css=text('frontend/src/grammar-designer/grammar-designer.css')+'\n'+text('frontend/src/grammar-designer/grammar-designer-responsive.css')
parser=text('src/main/java/com/example/editor/textual/runtime/NeutralGrammarSyntaxContract.java')
definition=text('src/main/java/com/example/editor/textual/definition/TextualEditorDefinitionService.java')
harness=text('scripts/validation/R8NeutralTextualParserStressHarness.java')

check('textual definitions follow the active grammar snapshot by default', 'hydra.grammarSnapshotPolicy' in definition and 'FOLLOW_ACTIVE' in definition and 'if(followsActiveSnapshot(m))return compilations.active' in definition)
check('frontend runtime also resolves the active snapshot under FOLLOW_ACTIVE', 'followsActiveGrammar(definition)' in editor and "'hydra.grammarSnapshotPolicy': 'FOLLOW_ACTIVE'" in editor)
check('live parsing is serialized instead of racing ETags', 'queuedSourceRef' in editor and 'parseBusyRef' in editor and 'drainParseQueue' in editor and 'sessionMatchesSource' in editor)
check('stale diagnostics are hidden while the current revision is validating', 'validationPending ? [] : diagnosticAdapter(session)' in editor and 'pending={session===null||validationPending}' in editor)
check('grammar mismatch diagnostics identify concrete rule and expected syntax', 'ruleContext' in parser and 'expectedHuman' in parser and 'humanMismatchMessage' in parser)
check('deterministic syntax mismatches expose safe quick-fix metadata', 'quickFixReplacement' in parser and 'quickFixLabel' in parser and 'suggestedFix' in parser)
check('grammar-specific mismatch suppresses contradictory generic delimiter noise', 'grammarSyntaxReported' in text('src/main/java/com/example/editor/textual/runtime/NeutralTextualModelParser.java') and 'if(!grammarSyntaxReported)checkDelimiters' in text('src/main/java/com/example/editor/textual/runtime/NeutralTextualModelParser.java'))
check('textual problems show expected grammar shape and apply syntax quickfixes', 'Expected by the grammar' in problems and 'onApplySyntaxFix' in problems and 'quickFixLabel' in problems and 'applySyntaxQuickFix' in editor)
check('entry informational diagnostics are excluded from Problems', "diagnostics.filter(item => item.severity !== 'INFO')" in status and 'diagnostics.filter(item => item.severity === \'INFO\')' in status and '{errors + warnings}' in status)
check('information diagnostics are rendered under Information', 'informationDiagnostics.map' in status and 'grammar-information-diagnostic' in status)
check('preview is generated from the current grammar draft', 'buildGrammarTextualExample(model)' in customization and 'model.documents' in example and 'generateRule' in example)
check('preview occupies the available editor canvas', '.grammar-editor-column>.grammar-textual-preview' in css and 'height:100%' in css and '.grammar-textual-preview .grammar-preview-editor' in css)
check('redundant grammar designer pill was removed', 'grammar-designer-kicker' not in designer)
check('grammar header has structured selector and homogeneous responsive actions', 'grammar-header-workspace' in designer and 'grid-template-columns:repeat(4,minmax(118px,1fr))' in css and '.grammar-header-actions .hydra-ui-button' in css)
check('stress harness covers freshly compiled grammar and detailed syntax feedback', 'compiledGrammarRefreshAndDetailedDiagnostic' in harness and 'elementSsss example surname1' in harness and 'quickFixReplacement' in harness)

failed=[label for label,ok in checks if not ok]
print(f'R11_TEXTUAL_GRAMMAR_FEEDBACK_PREVIEW_LAYOUT={len(checks)-len(failed)}/{len(checks)}')
if failed: raise SystemExit(1)
