from pathlib import Path
root=Path(__file__).resolve().parents[2]
checks=[]
def expect(name, cond):
    checks.append((name,bool(cond)))

def text(rel): return (root/rel).read_text(encoding='utf-8')
checker=text('src/main/java/com/example/editor/event/flow/EventFlowChecker.java')
runtime=text('src/main/java/com/example/editor/event/flow/runtime/EventFlowRuntimeService.java')
matcher=text('src/main/java/com/example/editor/gamification/runtime/GamificationEventMatcher.java')
binding=text('src/main/java/com/example/editor/event/runtime/EventBindingRuntimeService.java')
ctx=text('src/main/java/com/example/editor/event/expression/CanonicalEventExpressionContext.java')
expect('shared event expression context exists', 'CanonicalEventExpressionContext' in ctx)
expect('event binding uses shared context', 'CanonicalEventExpressionContext.create' in binding)
expect('flow validates condition ids', 'UNKNOWN_CONDITION_EXPRESSION' in checker)
expect('flow validates boolean type', 'CONDITION_NOT_BOOLEAN' in checker)
expect('flow runtime filters node condition', 'Node condition evaluated to false' in runtime)
expect('flow runtime filters transition conditions', 'transition.conditionExpressionId()' in runtime)
expect('gamification imports neutral evaluator', 'semantic.expression.ExpressionEvaluator' in matcher)
expect('gamification compiles legacy condition', 'Compiled from the backwards-compatible gamification condition' in matcher)
expect('gamification does not introduce second expression model', 'ExpressionModel' not in matcher)
expect('event/gamification do not import legacy semantic types', 'validation.expression.ExpressionDefinition' not in checker + matcher + runtime)
failed=[n for n,ok in checks if not ok]
for n,ok in checks: print(('PASS' if ok else 'FAIL')+' - '+n)
print(f'EVENT_SEMANTIC_EXPRESSION_FIX28={len(checks)-len(failed)}/{len(checks)} '+('PASS' if not failed else 'FAIL'))
raise SystemExit(1 if failed else 0)
