#!/usr/bin/env python3
"""Regresión V4.32.99: tamaño lógico, npm portable y standalone textual SOURCE."""
from pathlib import Path
import re
import sys

checks=[]
def check(label, ok):
    checks.append((label, bool(ok)))
    print(('PASS' if ok else 'FAIL') + ' - ' + label)

# Exact ceilings shared by StructuralArchitectureGuardTests.
limits=[
    ('src/main/java/com/example/editor/textual/definition/TextualSyntaxStyleService.java', 2400),
    ('src/main/java/com/example/editor/textual/persistence/JsonTextualEditorDefinitionRepository.java', 2400),
    ('src/main/java/com/example/editor/textual/persistence/JsonTextualSyntaxStyleRepository.java', 2400),
    ('src/main/java/com/example/editor/textual/web/TextualEditorDefinitionController.java', 2400),
    ('frontend/src/grammar-designer/grammar-designer.css', 1400),
    ('frontend/src/textual-model-editor/textual-model-editor.css', 1400),
]
for rel, ceiling in limits:
    text=Path(rel).read_text(encoding='utf-8')
    longest=max((len(line) for line in text.splitlines()), default=0)
    check(f'{rel} longest line <= {ceiling}', longest <= ceiling)

native=Path('src/main/java/com/example/editor/grammar/application/GrammarNativeToolchainService.java').read_text(encoding='utf-8')
native_test=Path('src/test/java/com/example/editor/grammar/application/GrammarNativeToolchainServiceTests.java').read_text(encoding='utf-8')
check('production npm executable is OS-aware', '"npm.cmd" : "npm"' in native)
check('npm-ci regression test derives the expected executable from os.name',
      'String expectedNpm = System.getProperty("os.name"' in native_test and 'isEqualTo(expectedNpm)' in native_test)
check('npm-ci regression test still requires ci', 'command.get(1)).isEqualTo("ci")' in native_test)

standalone=Path('src/test/java/com/example/editor/standalone/StandaloneEditorGeneratorServiceTests.java').read_text(encoding='utf-8')
check('SOURCE textual standalone validates README.md runtime warning',
      'text(generated.get("README.md"))' in standalone and 'LANGUAGE_SERVER_HOST_REQUIRED' in standalone)
check('SOURCE textual standalone explicitly rejects container-only README',
      'doesNotContainKey("CONTAINER_README.md")' in standalone)

failed=[label for label,ok in checks if not ok]
print(f'V43299_BACKEND_HOTFIX={len(checks)-len(failed)}/{len(checks)}')
if failed:
    sys.exit(1)
