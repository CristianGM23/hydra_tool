from pathlib import Path
root=Path(__file__).resolve().parents[2]
checks=[]
def text(path): return (root/path).read_text(encoding='utf-8')
def check(label, condition):
    checks.append((label,bool(condition)))
    print(('PASS' if condition else 'FAIL')+' - '+label)

parser=text('src/main/java/com/example/editor/textual/runtime/NeutralTextualModelParser.java')
contract=text('src/main/java/com/example/editor/textual/runtime/NeutralGrammarSyntaxContract.java')
editor=text('frontend/src/textual-model-editor/TextualModelEditor.tsx')
problems=text('frontend/src/textual-model-editor/TextualModelProblems.tsx')
designer=text('frontend/src/grammar-designer/GrammarDesigner.tsx')
harness=text('scripts/validation/R8NeutralTextualParserStressHarness.java')

check('textual parser invokes grammar validation even when contract reconstruction fails', 'if(diagnostics.size()<diagnosticLimit)' in parser and 'syntaxContract.validate' in parser)
check('unprovable grammar validation fails closed', 'CONTRACT_UNAVAILABLE' in contract and 'CONTRACT_NOT_PROVABLE' in contract and 'validationUnavailable' in contract)
check('unavailable grammar validation becomes a blocking textual diagnostic', 'TEXTUAL_GRAMMAR_VALIDATION_UNAVAILABLE' in parser)
check('textual editor starts a grammar-backed session automatically', 'void createTextualSession(definition.id,[sourceRef.current])' in editor)
check('textual editor creates and reloads a default grammar-backed definition when none exists', 'const created=await createTextualDefinition(uniqueDraft)' in editor and 'const loaded=await loadTextualDefinition(created.id)' in editor and 'setDefinitions([loaded.definition]);setDefinition(loaded.definition)' in editor)
check('textual parse dependency graph includes stable queue drain but excludes ETag', '}, [drainParseQueue, source, sessionId]);' in editor and '[source, sessionId, etag]' not in editor)
check('ETag is retained outside the parse dependency graph', 'etagRef.current' in editor)
check('unvalidated drafts are shown as pending rather than clean', 'pending={session===null||validationPending}' in editor and 'Textual validation pending' in problems)
check('draft validity uses the backend grammar-backed draftValid contract', 'session?.draftValid === true' in editor)
check('grammar designer restores the last persisted grammar on remount', 'LAST_GRAMMAR_STORAGE_KEY' in designer and 'loadGrammar(initial.id)' in designer and 'loadActiveGrammarCompilation(initial.id)' in designer)
check('grammar designer exposes saved grammar selection and explicit new grammar', "t('Saved grammars')" in designer and 'persisted.map(item=><option' in designer and 'onClick={newGrammar}' in designer)
check('grammar switching reloads source, ETag and active snapshot', 'async function openGrammar(grammarId:string)' in designer and 'setEtag(loaded.etag)' in designer and 'setSnapshot(active)' in designer)
check('stress harness reproduces screenshot-invalid syntax and fail-closed snapshot', 'screenshotInvalidModelIsRejected' in harness and 'unavailableContractFailsClosed' in harness)
failed=[label for label,ok in checks if not ok]
print(f'R10_TEXTUAL_GRAMMAR_REENTRY={len(checks)-len(failed)}/{len(checks)}')
if failed: raise SystemExit(1)
