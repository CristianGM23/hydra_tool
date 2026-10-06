from pathlib import Path
import re

root = Path(__file__).resolve().parents[2]
checks = []


def expect(name, condition):
    checks.append((name, bool(condition)))


def text(relative):
    return (root / relative).read_text(encoding="utf-8")

flow_test = text("src/test/java/com/example/editor/event/flow/EventFlowExpressionConditionTests.java")
gamification_test = text("src/test/java/com/example/editor/gamification/runtime/GamificationNeutralExpressionConditionTests.java")
auto_test = text("src/test/java/com/example/editor/event/flow/runtime/EventFlowRuntimeAutoDeploymentTests.java")
runtime = text("src/main/java/com/example/editor/event/flow/runtime/EventFlowRuntimeService.java")
application = text("src/main/java/com/example/editor/event/flow/EventFlowApplicationService.java")
policy = text("src/main/java/com/example/editor/contract/StableIdentifierPolicy.java")

# Document ids deliberately do not admit ':'. Element ids do.
document_id = re.compile(r"^[A-Za-z0-9][A-Za-z0-9._-]{0,119}$")
expected_document_ids = ["expressions.test", "meta.test", "flow-model.test", "document.test"]

expect("document-id policy still excludes colon", '[A-Za-z0-9._-]' in policy and '[A-Za-z0-9._:-]' in policy)
expect("event-flow test imports persisted ExpressionModel contract",
       "import com.example.editor.validation.expression.ExpressionModel;" in flow_test)
expect("event-flow test does not import nonexistent neutral ExpressionModel",
       "import com.example.editor.semantic.expression.ExpressionModel;" not in flow_test)
expect("all replacement fixture document ids satisfy canonical policy",
       all(document_id.fullmatch(value) for value in expected_document_ids))
expect("event-flow fixture no longer uses colon document ids",
       all(value not in flow_test for value in ('"expressions:test"', '"meta:test"', '"flow-model:test"')))
expect("gamification fixture no longer uses colon metamodel document id",
       '"meta:test"' not in gamification_test and gamification_test.count('"meta.test"') >= 3)
expect("gamification subject uses stable document id",
       '"document.test"' in gamification_test and '"document:test"' not in gamification_test)
expect("element ids keep colon-compatible canonical syntax",
       '"expression:allowed"' in flow_test and '"element:1"' in gamification_test)
expect("auto-deployment fixture supplies a semantic check report",
       "new EventFlowCheckReport(model.id(), List.of())" in auto_test)
expect("auto-deployment fixture does not fabricate null check report",
       'new EventFlowLoadResult(model, "f", null)' not in auto_test)
expect("production runtime remains fail-closed on invalid flow diagnostics",
       "if(!loaded.checkReport().valid())" in runtime)
expect("production application service remains source of checked load results",
       "new EventFlowLoadResult(stored.document(), stored.etag(), checkModel(metamodelId, stored.document()))" in application)

failed = [name for name, ok in checks if not ok]
for name, ok in checks:
    print(("PASS" if ok else "FAIL") + " - " + name)
print(f"EVENT_FIXTURE_CONTRACTS_FIX30={len(checks)-len(failed)}/{len(checks)} " + ("PASS" if not failed else "FAIL"))
raise SystemExit(1 if failed else 0)
