#!/usr/bin/env python3
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[2]
checks = []

def text(path: str) -> str:
    return (ROOT / path).read_text(encoding='utf-8')

def check(name: str, condition: bool) -> None:
    checks.append((name, condition))
    print(f"{'PASS' if condition else 'FAIL'} - {name}")

backend_test = text('src/test/java/com/example/editor/project/recovery/UpdateSiteDeepSiriusRuntimeMaterializationTests.java')
assembler = text('src/main/java/com/example/editor/definition/runtime/session/EditorSessionViewAssembler.java')
bridge = text('src/test/java/com/example/editor/definition/runtime/session/EditorSessionViewAssemblerTestBridge.java')
update_test = text('frontend/src/update-site-import-center/UpdateSiteImportCenter.test.tsx')
runtime_test = text('frontend/src/editor-definition/EditorRuntimeSession.test.tsx')
home_test = text('frontend/src/application-shell/HydraHome.test.tsx')
shell_test = text('frontend/src/application-shell/HydraApplicationShell.test.tsx')
responsive_test = text('frontend/src/application-shell/HydraResponsiveWorkbenchLayoutContract.test.ts')
design_test = text('frontend/src/design-system/HydraDesignSystemContract.test.ts')
remote_test = text('frontend/src/remote-storage/RemoteStorageContract.test.ts')
repository_test = text('frontend/src/repository-connections/RepositoryConnectionsUxContract.test.ts')
guided_test = text('frontend/src/i18n/guidedSpanishCatalog.test.ts')
app = text('frontend/src/App.tsx')

check('production session assembler remains package-private', bool(re.search(r'^final class EditorSessionViewAssembler', assembler, re.M)) and 'public final class EditorSessionViewAssembler' not in assembler)
check('cross-package Sirius regression uses test bridge', 'EditorSessionViewAssemblerTestBridge.assemble(' in backend_test and 'EditorSessionViewAssembler;' not in backend_test)
check('test bridge is isolated under src/test and delegates internally', 'new EditorSessionViewAssembler(null).assemble(' in bridge)
check('verified remote snapshot import persists selected layers directly', "candidateId: 'exact', persist: true" in update_test and 'expect(importRemoteSnapshotRecovery).not.toHaveBeenCalled()' in update_test)
check('runtime quick-fix regression waits for descriptor call and target selection', "listEditorSessionQuickFixes).toHaveBeenCalledWith('people','people-editor','session-one','diagnostic:progress')" in runtime_test and "selectionElementIds:['element:one']" in runtime_test)
check('runtime quick-fix regression verifies exact preview and resolved diagnostics', '0 created · 1 modified · 0 deleted' in runtime_test and "getByText('No problems detected.')" in runtime_test)
check('Home contract exposes validation and quick-fix independently', '/Validation designer/i' in home_test and '/Quick-fix designer/i' in home_test and 'Validation and quick-fix designer' not in home_test)
check('shell contract accepts intentional contextual description duplication', 'getAllByText' in shell_test and 'toHaveLength(2)' in shell_test)
check('responsive CSS contract compares normalized CSS consistently', ".project-command-primary{flex:11720px;" in responsive_test)
check('design-system contract recognizes forwardRef renderable element types', "'$$typeof' in primitive" in design_test)
check('remote-storage i18n contract validates effective translations', "translateUiText('Remote storage', 'es')" in remote_test)
check('repository i18n contract validates effective translations', 'Object.entries(translated)' in repository_test and "translateUiText(english, 'es')" in repository_test)
check('guided metamodel copy matches canonical Spanish translation', "toBe('Visual / edición manual')" in guided_test)
check('deferred App initialization uses a zero-argument Promise callback', 'Promise.resolve().then(() => initializeEffect())' in app and 'Promise.resolve().then(initializeEffect)' not in app)

failed = [name for name, ok in checks if not ok]
print(f"RESULT: {len(checks)-len(failed)}/{len(checks)} PASS")
sys.exit(1 if failed else 0)
