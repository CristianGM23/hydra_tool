#!/usr/bin/env python3
from __future__ import annotations
import hashlib
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]
checks=[]
def check(name, condition):
    checks.append((name,bool(condition)))

def text(path): return (ROOT/path).read_text(encoding='utf-8')

# Reviewed canonical schema baseline must include every schema, including Fix11 feedback.
props={}
for line in text(Path('config/hydra-contract-baseline.properties')).splitlines():
    if '=' in line and not line.lstrip().startswith('#'):
        k,v=line.split('=',1);props[k.strip()]=v.strip()
schemas=sorted((ROOT/'src/main/resources/schemas').rglob('*.schema.json'))
check('canonical schema count is 40', props.get('schema.count')=='40' and len(schemas)==40)
missing=[]; mismatched=[]
for schema in schemas:
    rel=schema.relative_to(ROOT).as_posix(); key='schema.'+rel
    expected=props.get(key,'')
    actual=hashlib.sha256(schema.read_bytes()).hexdigest()
    if not expected: missing.append(rel)
    elif expected!=actual: mismatched.append(rel)
check('all canonical schemas have reviewed hashes', not missing)
check('canonical schema hashes match', not mismatched)
check('feedback envelope schema is baselined', 'schema.src/main/resources/schemas/gamification/user-feedback-envelope-1.0.schema.json' in props)

# Runtime duplicate delivery must fail closed before any commit or feedback projection.
runtime=text(Path('src/main/java/com/example/editor/gamification/runtime/GamificationRuntimeService.java'))
guard='current.ledger.entries().stream().anyMatch(entry -> entry.sourceEventId().equals(event.id()))'
check('event-level idempotency guard exists', guard in runtime)
check('duplicate result explicitly carries empty feedback', 'current.state, List.of(), List.of(), List.of(), List.of()' in runtime)
check('idempotency guard precedes mutable ledger copy', runtime.find(guard) < runtime.find('new ArrayList<>(current.ledger.entries())'))
check('idempotency guard precedes persistence', runtime.find(guard) < runtime.find('persist(current, updatedState, updatedLedger)'))

# Feedback numeric policy parsing must be explicit and bounded.
resolver=text(Path('src/main/java/com/example/editor/gamification/feedback/GamificationFeedbackPolicyResolver.java'))
check('duration policy is bounded', 'defaults.durationMillis(), 1000, 30000' in resolver)
check('visible feedback count is bounded', 'defaults.maxVisible(), 1, 20' in resolver)
check('dedupe policy is bounded', 'defaults.dedupeWindowSeconds(), 0, 3600' in resolver)
check('aggregation policy is bounded', 'defaults.aggregateWindowMillis(), 0, 60000' in resolver)
check('malformed integers fall back', 'catch (NumberFormatException exception) { return fallback; }' in resolver)

# Regression tests must keep both functional bugs permanently covered.
penalty=text(Path('src/test/java/com/example/editor/gamification/GamificationPenaltyRuntimeTests.java'))
projector=text(Path('src/test/java/com/example/editor/gamification/feedback/GamificationFeedbackProjectorTests.java'))
check('duplicate feedback regression exists', 'duplicateEventDoesNotEmitDuplicateFeedback' in penalty)
check('duplicate processing avoids rewrites and republish', 'duplicateEventDoesNotRewriteRuntimeDocumentsOrRepublishSideEffects' in penalty)
check('corrupt policy regression exists', 'corruptPolicyAnnotationsFailClosedToDefaults' in projector)
check('numeric policy boundary regression exists', 'numericPolicyBoundsAreClampedAndMalformedValuesUseDefaults' in projector)


# Frontend gamification API must keep UserFeedbackEnvelope type-only; importing it as a runtime symbol blocks Vite/OXC parsing.
api=text(Path('frontend/src/gamification-designer/api.ts'))
check('feedback envelope remains a type-only import', "import type {" in api and "UserFeedbackEnvelope" in api.splitlines()[3])
check('normalization import does not redeclare feedback envelope', "gamificationNormalization, UserFeedbackEnvelope" not in api and "normalizeGamificationRuntimeSnapshot, UserFeedbackEnvelope" not in api)

# Shared logical-size budgets: reproduce the production guard for the affected source classes.
limits={'.java':(2400,160000),'.ts':(4500,180000),'.tsx':(4500,180000),'.css':(1400,120000)}
violations=[]
for root in [ROOT/'src/main/java',ROOT/'frontend/src']:
    for p in root.rglob('*'):
        if not p.is_file() or p.suffix not in limits or '.test.' in p.name: continue
        maxline,maxchars=limits[p.suffix]; content=p.read_text(encoding='utf-8')
        longest=max((len(x) for x in content.splitlines()),default=0)
        if len(content)>maxchars or longest>maxline: violations.append((p.relative_to(ROOT).as_posix(),len(content),longest))
check('production logical-size budgets pass', not violations)

failed=[name for name,ok in checks if not ok]
for name,ok in checks: print(('PASS' if ok else 'FAIL')+' '+name)
if failed:
    if missing: print('missing schema baselines:',missing)
    if mismatched: print('mismatched schema hashes:',mismatched)
    if violations: print('logical size violations:',violations)
    raise SystemExit(1)
print(f'[HYDRA] R5C13 Fix12 backend regressions: {len(checks)}/{len(checks)} PASS')
