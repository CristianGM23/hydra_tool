from pathlib import Path

root = Path(__file__).resolve().parents[2]
checks = []

def expect(name, condition):
    checks.append((name, bool(condition)))

def text(rel):
    return (root / rel).read_text(encoding="utf-8")

enum_source = text("src/main/java/com/example/editor/event/EventContextKind.java")
test_source = text("src/test/java/com/example/editor/gamification/runtime/GamificationNeutralExpressionConditionTests.java")
all_java = "\n".join(p.read_text(encoding="utf-8", errors="replace") for base in (root / "src/main/java", root / "src/test/java") for p in base.rglob("*.java"))

expect("event context enum keeps MODEL_EDITING", "MODEL_EDITING" in enum_source)
expect("event context enum has no ambiguous MODEL alias", " MODEL," not in enum_source and "{ MODEL," not in enum_source)
expect("gamification neutral expression tests use MODEL_EDITING twice", test_source.count("EventContextKind.MODEL_EDITING") == 2)
expect("no Java source references missing EventContextKind.MODEL", "EventContextKind.MODEL" not in all_java.replace("EventContextKind.MODEL_EDITING", ""))

failed = [name for name, ok in checks if not ok]
for name, ok in checks:
    print(("PASS" if ok else "FAIL") + " - " + name)
print(f"EVENT_CONTEXT_KIND_FIX29={len(checks)-len(failed)}/{len(checks)} " + ("PASS" if not failed else "FAIL"))
raise SystemExit(1 if failed else 0)
