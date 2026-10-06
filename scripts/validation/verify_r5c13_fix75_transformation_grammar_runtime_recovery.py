#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
checks = []

def text(path: str) -> str:
    return (ROOT / path).read_text(encoding='utf-8')

def check(name: str, condition: bool) -> None:
    checks.append((name, bool(condition)))

transformation = text('frontend/src/transformation-designer/TransformationDesigner.tsx')
grammar = text('frontend/src/grammar-designer/GrammarDesigner.tsx')
grammar_api = text('frontend/src/grammar-designer/api.ts')
status = text('frontend/src/grammar-designer/GrammarStatusDock.tsx')
catalog = text('frontend/src/i18n/grammarSpanishCatalog.ts')

check('transformation scope receives source metamodel state', 'sourceModel={sourceModel}' in transformation and 'sourceModel: MetaModel | null' in transformation)
check('transformation scope receives target metamodel state', 'targetModel={targetModel}' in transformation and 'targetModel: MetaModel | null' in transformation)
check('transformation scope destructures endpoint models explicitly', 'function TransformationScope({ document, sourceId, targetId, sourceModel, targetModel' in transformation)
check('grammar visual tabs capture the active analysis hash before forcing a fresh draft projection', "const expectedSourceHash=currentAnalysis?.sourceHash??''" in grammar and 'refreshDraftProjection(true,expectedSourceHash)' in grammar)
check('grammar visual refresh is not suppressed by revision-only snapshot match', "if(classDiagram!==null&&classDiagram.draftRevision===revision)return;\n        void refreshDraftProjection(true)" not in grammar)
check('grammar projection requests are unique per invocation', 'requestId:`grammar-diagram:${revision}:${unique}`' in grammar_api and 'crypto.randomUUID' in grammar_api)
check('effective entry rule is excluded from editor feedback', "visibleDiagnostics.filter(item=>item.code!=='GRAMMAR_EFFECTIVE_ENTRY_RULE')" in grammar and 'diagnostics={editorDiagnostics.filter' in grammar)
check('effective entry rule is excluded from generic information diagnostics', "item.code !== 'GRAMMAR_EFFECTIVE_ENTRY_RULE'" in status)
check('effective entry rule has one dedicated Information card', "t('Effective entry rule')" in status and "entryRuleInference.mode === 'MANUAL'" in status)
check('effective entry rule label is translated to Spanish', "'Effective entry rule': 'Regla de entrada efectiva'" in catalog)

failed = [name for name, ok in checks if not ok]
for name, ok in checks:
    print(f"[{'OK' if ok else 'FAIL'}] {name}")
print(f"FIX75_RUNTIME_RECOVERY={len(checks)-len(failed)}/{len(checks)}")
if failed:
    raise SystemExit(1)
