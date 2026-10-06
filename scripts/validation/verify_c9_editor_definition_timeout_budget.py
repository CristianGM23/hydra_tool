from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
TEST = ROOT / 'frontend/src/editor-definition/EditorDefinitionDesigner.test.tsx'
CONFIG = ROOT / 'frontend/vitest.config.ts'

checks: list[tuple[str, bool]] = []

def check(name: str, condition: bool) -> None:
    checks.append((name, condition))
    print(('PASS' if condition else 'FAIL') + ' ' + name)

source = TEST.read_text(encoding='utf-8')
config = CONFIG.read_text(encoding='utf-8')

check('central Vitest timeout remains configurable', 'HYDRA_TEST_TIMEOUT_MS' in config)
check('central Vitest timeout keeps a 30 second default', '30_000' in config and 'testTimeout' in config)

# Integration-heavy tests in this file must inherit the centrally tuned timeout.
# A fourth positional numeric argument to it()/test() silently overrides that policy
# and caused reproducible false failures on slower Windows worker pools.
local_test_timeout = re.compile(r"\bit\s*\([\s\S]*?\}\s*,\s*\d[\d_]*\s*\)\s*;", re.MULTILINE)
# Keep the pattern bounded per test declaration rather than allowing it to cross into
# the next declaration.
per_test_chunks = re.split(r"(?=\n\s*it\s*\()", source)
local_timeout_chunks = [chunk for chunk in per_test_chunks if re.search(r"\}\s*,\s*\d[\d_]*\s*\)\s*;", chunk)]
check('designer integration tests do not override the central timeout', not local_timeout_chunks)

check('visual-container regression remains present', "configures a mapped class as a visual container" in source)
check('visual-container relation assertion remains present', "Accepted containment relations" in source and "Container padding" in source)
check('tree mapping edit regression remains present', "opens editable mapping properties from the generated structure tree" in source)
check('tree mapping edit still verifies persisted visible name', "Department container" in source and "Visible name" in source)

failed = [name for name, ok in checks if not ok]
print(f'C9_EDITOR_DEFINITION_TIMEOUT_BUDGET={len(checks) - len(failed)}/{len(checks)}')
raise SystemExit(1 if failed else 0)
