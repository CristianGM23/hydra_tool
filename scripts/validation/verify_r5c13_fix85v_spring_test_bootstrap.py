#!/usr/bin/env python3
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
checks=[]
def check(n,c):
    ok=bool(c); checks.append((n,ok)); print(("PASS" if ok else "FAIL")+" - "+n)
for rel in ["src/test/java/com/example/editor/EditorApplicationTests.java","src/test/java/com/example/editor/system/SystemEndpointsTests.java"]:
    text=(ROOT/rel).read_text(encoding="utf-8")
    check(rel+" does not replace runtime profile", "@ActiveProfiles" not in text)
    check(rel+" disables only instance lock for full-context test", "hydra.instance-lock.enabled=false" in text)
    check(rel+" keeps storage side effects disabled", "dsl.storage.create-directories=false" in text)
check("test profile remains available for focused tests", (ROOT/"src/main/resources/application-test.properties").exists())
failed=[n for n,o in checks if not o]
print(f"FIX85V_SPRING_TEST_BOOTSTRAP={len(checks)-len(failed)}/{len(checks)} "+("PASS" if not failed else "FAIL"))
raise SystemExit(1 if failed else 0)
