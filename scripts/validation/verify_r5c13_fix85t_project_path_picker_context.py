#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
checks = []

def check(name, condition):
    ok = bool(condition)
    checks.append((name, ok))
    print(("PASS" if ok else "FAIL") + " - " + name)

headless_path = ROOT / "src/main/java/com/example/editor/project/storage/HeadlessProjectPathPicker.java"
swing_path = ROOT / "src/main/java/com/example/editor/project/storage/SwingProjectPathPicker.java"
config_path = ROOT / "src/main/java/com/example/editor/project/storage/ProjectPathPickerConfiguration.java"
controller_path = ROOT / "src/main/java/com/example/editor/project/web/ProjectController.java"
test_props_path = ROOT / "src/main/resources/application-test.properties"
editor_test_path = ROOT / "src/test/java/com/example/editor/EditorApplicationTests.java"
system_test_path = ROOT / "src/test/java/com/example/editor/system/SystemEndpointsTests.java"
focused_test_path = ROOT / "src/test/java/com/example/editor/project/storage/ProjectPathPickerConditionalBeanTests.java"

headless = headless_path.read_text(encoding="utf-8")
swing = swing_path.read_text(encoding="utf-8")
config = config_path.read_text(encoding="utf-8")
controller = controller_path.read_text(encoding="utf-8")
test_props = test_props_path.read_text(encoding="utf-8")
editor_test = editor_test_path.read_text(encoding="utf-8")
system_test = system_test_path.read_text(encoding="utf-8")
focused_test = focused_test_path.read_text(encoding="utf-8")

check("picker implementations are plain delegates rather than conditionally scanned components",
      "@Component" not in headless and "@Component" not in swing)
check("explicit Spring configuration owns ProjectPathPicker beans",
      "@Configuration(proxyBeanMethods = false)" in config and "ProjectPathPicker" in config)
check("headless bean is selected explicitly when native picker is false",
      'havingValue = "false"' in config and "headlessProjectPathPicker" in config)
check("Swing bean is selected when native picker is true or missing",
      'havingValue = "true"' in config and "matchIfMissing = true" in config and "swingProjectPathPicker" in config)
check("test profile disables native picker",
      "dsl.projects.local.native-path-picker-enabled=false" in test_props)
check("test profile disables cross-process instance lock",
      "hydra.instance-lock.enabled=false" in test_props)
check("EditorApplicationTests keeps real default profile and disables only the lock inline", '@ActiveProfiles("test")' not in editor_test and 'hydra.instance-lock.enabled=false' in editor_test)
check("SystemEndpointsTests keeps real default profile and disables only the lock inline", '@ActiveProfiles("test")' not in system_test and 'hydra.instance-lock.enabled=false' in system_test)
check("ProjectController injects ProjectPathPicker through ObjectProvider with headless fallback",
      "ObjectProvider<ProjectPathPicker>" in controller and "getIfAvailable(HeadlessProjectPathPicker::new)" in controller)
check("focused conditional-bean regression test uses explicit picker configuration",
      "ProjectPathPickerConfiguration.class" in focused_test and "registersHeadlessFallbackWhenNativePickerIsDisabled" in focused_test and "registersSwingPickerWhenNativePickerIsEnabled" in focused_test)

failed = [name for name, ok in checks if not ok]
print(f"PROJECT_PATH_PICKER_CONTEXT_FIX85T={len(checks)-len(failed)}/{len(checks)} " + ("PASS" if not failed else "FAIL"))
raise SystemExit(1 if failed else 0)
