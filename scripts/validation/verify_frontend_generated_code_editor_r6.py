from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
GRAMMAR = ROOT / 'frontend' / 'src' / 'grammar-designer'

view = (GRAMMAR / 'GrammarGeneratedCodeView.tsx').read_text(encoding='utf-8')
editor = (GRAMMAR / 'GrammarMonacoEditor.tsx').read_text(encoding='utf-8')
profile = (GRAMMAR / 'generatedCodeLanguage.ts').read_text(encoding='utf-8')
css = (GRAMMAR / 'grammar-designer.css').read_text(encoding='utf-8')
test = (GRAMMAR / 'generatedCodeLanguage.test.ts').read_text(encoding='utf-8')

checks = {
    'generated code renders with GrammarMonacoEditor': '<GrammarMonacoEditor' in view,
    'generated editor is read only': 'readOnly alwaysShowVerticalScrollbar' in view,
    'selected path drives language detection': 'generatedCodeLanguageProfile(activePath)' in view,
    'generated syntax customization is applied': 'generatedCodeCustomization(language)' in view,
    'typescript profile contains reserved words': "'typescript','TypeScript',TYPESCRIPT_KEYWORDS" in profile and 'const continue' in profile,
    'java profile exists': "'java','Java',JAVA_KEYWORDS" in profile,
    'grammar technologies have profiles': all(token in profile for token in ["'xtext','Xtext'", "'langium','Langium'", "'antlr','ANTLR'"]),
    'keyword color differs from normal text by contract': "roleStyles.KEYWORD={color:'#1d4ed8',bold:true" in profile,
    'Monaco exposes persistent vertical scrollbar option': "alwaysShowVerticalScrollbar?'visible':'auto'" in editor,
    'safe editor can force vertical scrollbar': 'grammar-textarea-fallback.always-vertical-scroll{overflow-y:scroll}' in css,
    'generated code surface is height constrained': '.grammar-generated-code-surface{' in css and 'min-height:0' in css,
    'generated file list scrolls independently': '.grammar-generated-file-list{' in css and 'overflow-y:auto' in css,
    'generated toolbar identifies current file and language': 'grammar-generated-code-toolbar' in view and 'language.label' in view,
    'regression test covers keyword contrast': "roleStyles.KEYWORD.color).not.toBe(customization.foreground)" in test,
}

failed = [name for name, ok in checks.items() if not ok]
for name, ok in checks.items():
    print(f"{'PASS' if ok else 'FAIL'}: {name}")
if failed:
    raise SystemExit(f"Generated code editor R6 failed: {failed}")
print(f"FRONTEND_GENERATED_CODE_EDITOR_R6 = {len(checks)}/{len(checks)} PASS")
