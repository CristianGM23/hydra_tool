from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
editor=(ROOT/'frontend/src/grammar-designer/GrammarMonacoEditor.tsx').read_text(encoding='utf-8')
problems=(ROOT/'frontend/src/grammar-designer/GrammarProblemsPanel.tsx').read_text(encoding='utf-8')
designer=(ROOT/'frontend/src/grammar-designer/GrammarDesigner.tsx').read_text(encoding='utf-8')
css=(ROOT/'frontend/src/grammar-designer/grammar-designer-responsive.css').read_text(encoding='utf-8')
checks={
 'inline diagnostic cards removed': 'grammar-monaco-feedback' not in editor,
 'editor still exposes Monaco markers': "setModelMarkers(model,'hydra-grammar'" in editor,
 'editor still exposes native code actions': 'registerCodeActionProvider' in editor and 'monacoQuickFixes' in editor,
 'problems resolves linked actions': 'const quickFixes = item.codeActionIds.map' in problems,
 'quick fixes visible outside details': 'grammar-problem-quick-fixes' in problems and problems.index('grammar-problem-quick-fixes') < problems.index('{open && <div className="grammar-problem-details">'),
 'quick fix actions apply through canonical callback': 'onClick={() => onApply(action)}' in problems,
 'details do not duplicate quickfix buttons': problems.count('className="grammar-quick-fix"') == 1,
 'designer no longer passes editor quickfix callback': 'onApplyCodeAction={applyAction}' not in designer,
 'quickfix dock has explicit layout': '.grammar-problem-quick-fixes{' in css,
}
failed=[name for name,ok in checks.items() if not ok]
for name,ok in checks.items(): print(('PASS' if ok else 'FAIL')+': '+name)
if failed: raise SystemExit(f'FIX77 failed: {failed}')
print(f'FIX77_GRAMMAR_PROBLEM_QUICKFIX_DOCK=PASS ({len(checks)}/{len(checks)})')
