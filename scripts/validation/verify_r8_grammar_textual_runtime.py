#!/usr/bin/env python3
"""Gate R8: diagnóstico concurrente de gramáticas y sintaxis textual acotada por el snapshot activo."""
from pathlib import Path
import sys

checks=[]
def check(label, condition):
    ok=bool(condition); checks.append((label,ok)); print(('PASS' if ok else 'FAIL')+' - '+label)

def text(path): return Path(path).read_text(encoding='utf-8')

local=text('frontend/src/grammar-designer/grammarLocalInspection.ts')
designer=text('frontend/src/grammar-designer/GrammarDesigner.tsx')
dock=text('frontend/src/grammar-designer/GrammarStatusDock.tsx')
custom=text('frontend/src/grammar-designer/textualCustomization.ts')
appearance=text('frontend/src/grammar-designer/GrammarTextualCustomization.tsx')
monaco=text('frontend/src/grammar-designer/GrammarMonacoEditor.tsx')
model_editor=text('frontend/src/textual-model-editor/TextualModelEditor.tsx')
css=text('frontend/src/grammar-designer/grammar-designer.css')
contract=text('src/main/java/com/example/editor/textual/runtime/NeutralGrammarSyntaxContract.java')
parser=text('src/main/java/com/example/editor/textual/runtime/NeutralTextualModelParser.java')
sessions=text('src/main/java/com/example/editor/textual/runtime/TextualModelSessionService.java')
harness=text('scripts/validation/R8NeutralTextualParserStressHarness.java')

check('local grammar diagnostics are explicitly bounded', 'MAX_LOCAL_DIAGNOSTICS = 250' in local)
check('local inspection emits malformed-entry, unexpected-text and undefined-reference diagnostics', all(code in local for code in ('GRAMMAR_LOCAL_MALFORMED_ENTRY','GRAMMAR_LOCAL_UNEXPECTED_TEXT','GRAMMAR_LOCAL_UNDEFINED_REFERENCE')))
check('effective entry rule is retained as INFO on its source range', 'GRAMMAR_EFFECTIVE_ENTRY_RULE' in local and "'INFO'" in local and 'rangeAt(entryRule.index' in local)
check('designer merges local, unused, backend and operation diagnostics instead of replacing them', 'mergeGrammarDiagnostics' in designer and ('...localInspection.diagnostics' in designer or '...localFeedback.diagnostics' in designer) and '...(currentAnalysis?.diagnostics??[])' in designer and '...operationDiagnostics' in designer)
check('local errors block compilation even after backend state exists', "const blocking=baseDiagnostics.filter(item=>item.severity==='ERROR')" in designer and 'if(blocking.length>0)' in designer)
check('Problems counter excludes informational diagnostics while Information retains them', '{errors + warnings}' in dock and 'informationDiagnostics' in dock and 'errors + warnings + infos' not in dock)
check('effective textual customization merges persisted and inferred keywords', 'effectiveTextualCustomization' in custom and '...persisted.keywords,...inferKeywords(model)' in custom)
check('appearance and textual model editor consume the same effective customization', 'effectiveTextualCustomization(model)' in appearance and 'effectiveTextualCustomization(grammarModel)' in model_editor)
check('Monaco variables carry foreground/background plus bold and italic role state', all(token in monaco for token in ('--hydra-textual-foreground','--hydra-textual-background',"-weight`]=style.bold","-style`]=style.italic")))
check('grammar palette suppresses horizontal overflow at its structural boundaries', '.grammar-workbench>aside:first-child,.grammar-outline,.grammar-structure-tree{overflow-x:hidden}' in css and '.grammar-symbol-row{width:100%;overflow:hidden}' in css)
check('neutral grammar syntax contract is reconstructed from the compilation snapshot', 'NeutralGrammarSyntaxContract from(GrammarCompilationSnapshot snapshot)' in contract and 'grammarSources(snapshot)' in contract)
check('syntax validation has explicit full, incremental and state budgets', all(token in contract for token in ('FULL_MAX_CHARS','FULL_MAX_TOKENS','INCREMENTAL_MAX_TOKENS','MAX_EARLEY_STATES','INCREMENTAL_BOUNDED')))
check('textual parser validates against the grammar contract before canonical extraction', 'NeutralGrammarSyntaxContract.from(snapshot)' in parser and 'syntaxContract.validate' in parser and 'TEXTUAL_GRAMMAR_MISMATCH' in parser)
check('configured maximumDiagnostics reaches the parser instead of only truncating afterwards', 'properties.getMaximumDiagnostics()' in sessions and 'parser.parse(revision,docs,snapshot,properties.getMaximumDiagnostics())' in sessions and 'diagnosticLimit=Math.max(1,maximumDiagnostics)' in parser)
check('parser publishes validation strategy and truncation evidence', 'grammarValidationStrategy' in parser and 'diagnosticsTruncated' in parser and 'maximumDiagnostics' in parser)
check('permanent stress harness covers all grammar technologies, 20k input, injected large-model failure and deep grammar', all(token in harness for token in ('GrammarTechnology.LANGIUM','GrammarTechnology.XTEXT','GrammarTechnology.ANTLR','20_000','largeInvalidModel','deeplyNestedGrammarDoesNotOverflow')))

failed=[label for label,ok in checks if not ok]
print(f'R8_GRAMMAR_TEXTUAL_RUNTIME={len(checks)-len(failed)}/{len(checks)}')
if failed: sys.exit(1)
