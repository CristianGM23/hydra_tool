from pathlib import Path

root = Path(__file__).resolve().parents[2]
checks = []


def expect(name, condition):
    checks.append((name, bool(condition)))


def text(relative):
    return (root / relative).read_text(encoding="utf-8")

context = text("src/main/java/com/example/editor/semantic/expression/ExpressionEvaluationContext.java")
context_test = text("src/test/java/com/example/editor/semantic/expression/ExpressionEvaluationContextTests.java")
event_context = text("src/main/java/com/example/editor/event/expression/CanonicalEventExpressionContext.java")
gamification_test = text("src/test/java/com/example/editor/gamification/runtime/GamificationNeutralExpressionConditionTests.java")
matcher = text("src/main/java/com/example/editor/gamification/runtime/GamificationEventMatcher.java")

expect("evaluation context no longer uses Map.copyOf for runtime variables",
       "Map.copyOf(variables)" not in context)
expect("evaluation context delegates variable freezing to nullable-aware helper",
       "variables = immutableVariables(variables);" in context)
expect("nullable-aware helper takes a defensive linked hash map copy",
       "LinkedHashMap<String, Object> copy = new LinkedHashMap<>();" in context)
expect("nullable-aware helper preserves entry values including null",
       "entry.getValue()" in context and "copy.put(Objects.requireNonNull(entry.getKey()" in context)
expect("nullable-aware helper exposes an unmodifiable map",
       "Collections.unmodifiableMap(copy)" in context)
expect("kernel regression covers explicit null variable",
       'variables.put("actor", null);' in context_test and 'context.variable("actor")' in context_test)
expect("kernel regression covers defensive-copy semantics",
       'variables.put("optional", "changed");' in context_test and 'doesNotContainKey("late")' in context_test)
expect("kernel regression covers immutable variable view",
       "UnsupportedOperationException.class" in context_test)
expect("kernel regression covers null current value",
       ".withCurrent(null)" in context_test and 'containsKey("_current")' in context_test)
expect("canonical event projection deliberately exposes optional roots",
       'values.put("subject", event.subject());' in event_context and 'values.put("actor", event.actor());' in event_context)
expect("gamification regression still exercises absent optional actor and subject",
       'new EventContext(EventContextKind.MODEL_EDITING' in gamification_test and
       'null, null, "", "", "", Map.of("score", 12)' in gamification_test)
expect("gamification runtime still executes through neutral evaluator",
       "evaluator.evaluate(expression, CanonicalEventExpressionContext.create(metamodel, event)).value()" in matcher)
expect("gamification does not bypass neutral kernel in four-argument matcher",
       "if(metamodel == null || evaluator == null) return matches(rule, event);" in matcher)

failed = [name for name, ok in checks if not ok]
for name, ok in checks:
    print(("PASS" if ok else "FAIL") + " - " + name)
print(f"NULLABLE_EXPRESSION_VARIABLES_FIX31={len(checks)-len(failed)}/{len(checks)} " + ("PASS" if not failed else "FAIL"))
raise SystemExit(1 if failed else 0)
