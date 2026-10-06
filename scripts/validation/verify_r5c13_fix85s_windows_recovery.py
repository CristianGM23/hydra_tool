#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[2]
checks = []

def text(relative):
    return (ROOT / relative).read_text(encoding="utf-8")

def check(name, condition):
    ok = bool(condition)
    checks.append((name, ok))
    print(("PASS" if ok else "FAIL") + " - " + name)

lifecycle = text("src/main/java/com/example/editor/metamodel/lifecycle/MetamodelLifecycleService.java")
lock = text("src/main/java/com/example/editor/runtime/instance/HydraInstanceLock.java")
lock_test = text("src/test/java/com/example/editor/runtime/instance/HydraInstanceLockTests.java")
system_test = text("src/test/java/com/example/editor/system/SystemEndpointsTests.java")
app_test = text("src/test/java/com/example/editor/EditorApplicationTests.java")
test_props = text("src/main/resources/application-test.properties")

check("prepared recovery reconciles physical state before cleanup",
      "journal.expectedFiles().stream().filter(this::stillExistsManaged)" in lifecycle)
check("prepared recovery still deletes reconciled residuals",
      "deleteManagedReferences(residualTargets)" in lifecycle)
check("instance metadata and OS guard are separate",
      'path.getFileName().toString() + ".guard"' in lock and "guardPath(path)" in lock)
check("metadata remains readable while guard holds FileLock",
      "writeOwnerMetadata(path)" in lock and "FileChannel.open(guard" in lock)
check("metadata is retired before releasing the guard",
      lock.find("Files.deleteIfExists(metadataPath)") < lock.find("lock.release()"))
check("existing Windows readability regression test remains active",
      'Files.readString(data.resolve(".hydra.lock"))' in lock_test)
check("SpringBoot system test activates deterministic test profile",
      '@ActiveProfiles("test")' in system_test)
check("SpringBoot application test activates deterministic test profile",
      '@ActiveProfiles("test")' in app_test)
check("test profile disables global instance lock",
      "hydra.instance-lock.enabled=false" in test_props)

failed = [name for name, ok in checks if not ok]
print(f"FIX85S_WINDOWS_RECOVERY={len(checks)-len(failed)}/{len(checks)} " + ("PASS" if not failed else "FAIL"))
if failed:
    for name in failed:
        print("  - " + name)
sys.exit(1 if failed else 0)
