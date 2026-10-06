from pathlib import Path
root = Path(__file__).resolve().parents[2]

def txt(path: str) -> str:
    return (root / path).read_text(encoding='utf-8')

checks = []
def check(label: str, condition: bool):
    checks.append((label, bool(condition)))

home = txt('frontend/src/application-shell/application-shell.css')
cap = txt('frontend/src/capability-center/CapabilityReadinessView.tsx')
cap_css = txt('frontend/src/capability-center/capability-center.css')
cap_test = txt('frontend/src/capability-center/CapabilityReadinessView.test.tsx')
designer = txt('frontend/src/grammar-designer/GrammarDesigner.tsx')
custom = txt('frontend/src/grammar-designer/GrammarTextualCustomization.tsx')
grammar_catalog = txt('frontend/src/i18n/grammarSpanishCatalog.ts')
compilation = txt('frontend/src/grammar-designer/GrammarCompilationPanel.tsx')
css = txt('frontend/src/grammar-designer/grammar-designer.css')

check('R4 Home sizing remains in the cumulative package', '.hydra-home-content>section.hydra-home-overview{max-width:1500px}' in home and '.hydra-home-overview-readiness{margin-top:16px;min-width:0}' in home and '.hydra-home-actions{display:flex;align-items:center;gap:8px;flex-wrap:wrap}' in home)
check('grammar readiness fallback loads the selected grammar', "loadGrammar(contextId)" in cap)
check('grammar readiness fallback reads the active compilation snapshot', 'loadActiveGrammarCompilation(contextId)' in cap)
check('grammar readiness fallback exposes real source and semantic metrics', all(token in cap for token in ['documents: grammar.documents.length', 'bindings > 0', 'generatedFiles', 'textualStyles']))
check('grammar readiness fallback is non-blocking in Home', "contextType === 'GRAMMAR'" in cap and 'GrammarCapabilityReadinessFallback' in cap)
check('grammar fallback has a permanent regression test', 'shows a real grammar summary instead of a blocking Home error' in cap_test and 'Preparación no disponible' in cap_test)
check('grammar summary cards use Home capability styling', 'grammar-capability-summary-grid' in cap_css and 'grammar-capability-summary-card' in cap_css)
check('grammar header uses compact visual hierarchy', 'grammar-designer-title-group' in designer and 'grammar-header-workspace' in designer and 'grammar-designer-kicker' not in designer)
check('textual customization has Spanish help coverage through shared i18n', 'createFieldHelp(t)' in custom and all(token in grammar_catalog for token in ['Elige la familia tipográfica base', 'Separación vertical entre líneas', 'Palabras clave que HYDRA destacará', 'Elemento de la gramática cuya información contextual']))
check('appearance layout fills the available width', 'grid-template-columns:minmax(250px,1.45fr)' in css and 'grammar-customization-keywords{grid-column:1/-1;max-width:none}' in css)
check('contextual-information layout fills the available width', 'grid-template-columns:minmax(0,2fr) minmax(320px,.9fr)' in css and '.grammar-hover-fields :is(input,select,textarea){max-width:none;width:100%}' in css)
check('compilation stages reuse guided status icons', 'HydraGuidedStatusIcon' in compilation and 'grammar-compilation-stage-grid' in compilation)
check('grammar designer keeps vertical scrolling', '.grammar-designer{overflow-y:scroll;overflow-x:hidden;scrollbar-gutter:stable both-edges}' in css)
check('R5 does not add important overrides to grammar customization tail', all('!important' not in line for line in css.splitlines()[760:]))

failed = [label for label, ok in checks if not ok]
for label, ok in checks:
    print(('PASS' if ok else 'FAIL') + ' - ' + label)
print(f'FRONTEND_HOME_GRAMMAR_UX_R5={len(checks)-len(failed)}/{len(checks)}')
raise SystemExit(1 if failed else 0)
