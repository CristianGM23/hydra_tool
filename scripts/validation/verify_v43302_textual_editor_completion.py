from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[2]
checks = []
def check(label, condition): checks.append((label, bool(condition)))
def text(path): return (ROOT / path).read_text(encoding='utf-8')

editor = text('frontend/src/textual-model-editor/TextualModelEditor.tsx')
problems = text('frontend/src/textual-model-editor/TextualModelProblems.tsx')
services = text('frontend/src/textual-model-editor/TextualLanguageServicesPanel.tsx')
textual_css = text('frontend/src/textual-model-editor/textual-model-editor.css').replace(' ', '').replace('\n', '')
grammar = text('frontend/src/grammar-designer/GrammarDesigner.tsx')
compilation = text('frontend/src/grammar-designer/GrammarCompilationPanel.tsx')
grammar_api = text('frontend/src/grammar-designer/api.ts')
controller = text('src/main/java/com/example/editor/grammar/web/GrammarCompilationController.java')
archive = text('src/main/java/com/example/editor/grammar/application/GrammarGeneratedProjectArchiveService.java')
parser = text('src/main/java/com/example/editor/textual/runtime/NeutralTextualModelParser.java')
catalog = text('frontend/src/i18n/grammarSpanishCatalog.ts')

check('textual workbench owns vertical scroll', 'overflow-y:auto' in textual_css and 'height:100%' in textual_css)
check('textual workbench stretches full width', '.textual-model-editor{' in textual_css and 'width:100%' in textual_css and 'max-width:none' in textual_css)
check('editor focuses exact textual problem range', 'focusRange={focusProblem?.range}' in editor and 'onNavigate={setFocusProblem}' in editor)
check('problems explain why', "t('Why')" in problems)
check('problems recommend a concrete solution', "t('Recommended solution')" in problems)
check('problems keep consequences visible', "t('Consequence')" in problems)
check('backend enriches textual causes', 'details.put("cause", guidance.cause())' in parser and 'TEXTUAL_REFERENCE_NOT_FOUND' in parser)
check('textual UI negotiates actual runtime', 'const pinned={...definition,compilationSnapshotId:snapshot.id};' in editor and 'checkTextualRuntime(pinned)' in editor and 'TextualLanguageServicesPanel' in editor)
check('runtime UI exposes completion and hover', 'COMPLETION' in services and 'HOVER' in services and 'GO_TO_DEFINITION' in services)
check('runtime UI distinguishes unsupported features', 'unavailableFeatures' in services and 'Requires another generated runtime or provider capability' in services)
check('grammar offers generate-only action', 'generateLanguageProject' in grammar and "t('Generate language project')" in grammar)
check('generated project can be downloaded', 'downloadGeneratedGrammarProject' in grammar_api and 'generated-project.zip' in controller)
check('download uses snapshot generated project', 'snapshot.generatedProject()' in controller)
check('archive is deterministic and bounded', 'maximumGeneratedFiles' not in archive or ('getMaximumGeneratedFiles' in archive and 'getMaximumGeneratedBytes' in archive and 'setTime(0L)' in archive))
check('navigation has current Spanish step labels', all(key in catalog for key in ["'2. Grammars': '2. Gramáticas'", "'3. Editor & representation': '3. Editor y representación'", "'Textual models': 'Modelos textuales'", "'USE': 'USO'"]))
check('generated project panel explains generated-host semantics', 'HYDRA does not claim them as live until that host is running' in compilation)
check('permanent frontend UX contract exists', (ROOT / 'frontend/src/textual-model-editor/TextualModelEditorUxContract.test.ts').exists())
check('permanent archive test exists', (ROOT / 'src/test/java/com/example/editor/grammar/GrammarGeneratedProjectArchiveServiceTests.java').exists())

for label, ok in checks: print(('PASS' if ok else 'FAIL') + ' - ' + label)
passed = sum(ok for _, ok in checks)
print(f'V4.32.102_TEXTUAL_EDITOR_COMPLETION={passed}/{len(checks)}')
sys.exit(0 if passed == len(checks) else 1)
