#!/usr/bin/env python3
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[2]
result = ROOT / 'src/main/java/com/example/editor/textual/runtime/TextualParseResult.java'
service = ROOT / 'src/main/java/com/example/editor/textual/runtime/TextualModelSessionService.java'
reconcile = ROOT / 'src/main/java/com/example/editor/textual/runtime/TextualCanonicalReconciliationService.java'

checks = []
def check(name: str, condition: bool) -> None:
    checks.append((name, condition))

result_text = result.read_text(encoding='utf-8')
service_text = service.read_text(encoding='utf-8')
reconcile_text = reconcile.read_text(encoding='utf-8')

check('TextualParseResult keeps a single neutral revision component', bool(re.search(r'record\s+TextualParseResult\s*\(\s*long\s+revision\b', result_text)))
check('commitReconciled compares the parsed neutral revision', 'reconciliation.parsed().revision()!=nextRevision' in service_text)
check('no stale parsed draftRevision accessor remains', 'reconciliation.parsed().draftRevision()' not in service_text)
check('reconciliation reparses using nextRevision', 'parser.parse(nextRevision, updated, snapshot)' in reconcile_text)
check('session request remains the owner of draftRevision naming', 'req.draftRevision()' in service_text)

failed = [name for name, ok in checks if not ok]
for name, ok in checks:
    print(('PASS' if ok else 'FAIL') + ' - ' + name)
print(f'TEXTUAL_PARSE_REVISION_CONTRACT={len(checks)-len(failed)}/{len(checks)}')
raise SystemExit(1 if failed else 0)
