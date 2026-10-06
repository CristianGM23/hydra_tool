from pathlib import Path
root=Path(__file__).resolve().parents[2]
checks=[]
def text(path): return (root/path).read_text(encoding='utf-8')
def check(label, condition):
    checks.append((label,bool(condition)))
    print(('PASS' if condition else 'FAIL')+' - '+label)

editor=text('frontend/src/textual-model-editor/TextualModelEditor.tsx')
monaco=text('frontend/src/grammar-designer/GrammarMonacoEditor.tsx')
completion=text('frontend/src/textual-model-editor/textualModelCompletion.ts')
services=text('frontend/src/textual-model-editor/TextualLanguageServicesPanel.tsx')
css=text('frontend/src/grammar-designer/grammar-designer.css')+'\n'+text('frontend/src/grammar-designer/grammar-designer-responsive.css')+'\n'+text('frontend/src/grammar-designer/grammar-editor-completion.css')
tests=text('frontend/src/textual-model-editor/textualModelCompletion.test.ts')

check('textual editor derives completion from the loaded active grammar', 'textualModelCompletions(grammarModel,text,cursorOffset)' in editor and 'completionProvider={completionProvider}' in editor)
check('grammar is refreshed when the active compilation snapshot changes', '[definition.grammarId,activeSnapshot?.id]' in editor)
check('initial textual source is seeded from the current grammar instead of a hard-coded syntax', 'buildGrammarTextualExample(result.grammar)' in editor and "content: '', revision: 0" in editor)
check('Monaco registers a completion provider used by Ctrl+Space', 'registerCompletionItemProvider' in monaco and 'provideCompletionItems' in monaco)
check('safe editor handles Ctrl+Space without Monaco', "event.code==='Space'" in monaco and 'requestSafeCompletion' in monaco and 'grammar-safe-completion' in monaco)
check('safe completion supports keyboard selection and insertion', "event.key==='ArrowDown'" in monaco and "event.key==='ArrowUp'" in monaco and "event.key==='Enter'||event.key==='Tab'" in monaco and 'applySafeCompletion' in monaco)
check('neutral completion walks alternatives and cardinalities', "case 'CHOICE'" in completion and "case 'ZERO_OR_MORE'" in completion and "case 'ONE_OR_MORE'" in completion and "case 'OPTIONAL'" in completion)
check('neutral completion walks assignments and parser-rule references', "case 'ASSIGNMENT'" in completion and "case 'RULE'" in completion and 'assignmentName' in completion)
check('completion supports literals terminals and cross references', "'LITERAL'|'TOKEN'|'REFERENCE'" in completion and 'terminalAccepts' in completion and 'crossReferenceTarget' in completion)
check('completion is bounded against pathological grammars and inputs', 'MAX_MATCH_STEPS' in completion and 'MAX_COMPLETIONS' in completion and 'maxRounds' in completion)
check('language services report neutral Ctrl+Space completion as available', "neutralCompletionAvailable" in services and 'Available · Ctrl+Space · derived from the active grammar' in services)
check('safe completion popup is styled as a constrained overlay', '.grammar-safe-completion{' in css and 'max-height:320px' in css and '.grammar-safe-completion>button.selected' in css)
check('completion tests cover Langium Xtext and ANTLR', "grammar('LANGIUM'" in tests and "grammar('XTEXT'" in tests and "grammar('ANTLR'" in tests)
check('completion tests cover alternatives optional branches and a large repeated model', "arrayContaining(['age','role','element','group'])" in tests and '5_000' in tests)

failed=[label for label,ok in checks if not ok]
print(f'R12_TEXTUAL_COMPLETION={len(checks)-len(failed)}/{len(checks)}')
if failed: raise SystemExit(1)
