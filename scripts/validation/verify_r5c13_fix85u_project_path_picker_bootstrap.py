#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
checks=[]
def check(name, cond):
    ok=bool(cond); checks.append((name,ok)); print(("PASS" if ok else "FAIL")+" - "+name)

config=(ROOT/'src/main/java/com/example/editor/project/storage/ProjectPathPickerConfiguration.java').read_text(encoding='utf-8')
controller=(ROOT/'src/main/java/com/example/editor/project/web/ProjectController.java').read_text(encoding='utf-8')
headless=(ROOT/'src/main/java/com/example/editor/project/storage/HeadlessProjectPathPicker.java').read_text(encoding='utf-8')
swing=(ROOT/'src/main/java/com/example/editor/project/storage/SwingProjectPathPicker.java').read_text(encoding='utf-8')
props=(ROOT/'src/main/resources/application-test.properties').read_text(encoding='utf-8')

check('explicit picker configuration is component-scanned', '@Configuration(proxyBeanMethods = false)' in config)
check('configuration publishes exactly complementary true/false conditions', config.count('native-path-picker-enabled') == 2 and 'havingValue = "true"' in config and 'havingValue = "false"' in config)
check('headless implementation is independent of Spring scanning', '@Component' not in headless and '@ConditionalOnProperty' not in headless)
check('Swing implementation is independent of Spring scanning', '@Component' not in swing and '@ConditionalOnProperty' not in swing)
check('controller cannot fail startup when optional picker bean is absent', 'ObjectProvider<ProjectPathPicker>' in controller and 'getIfAvailable(HeadlessProjectPathPicker::new)' in controller)
check('test profile selects headless picker explicitly', 'dsl.projects.local.native-path-picker-enabled=false' in props)

failed=[n for n,ok in checks if not ok]
print(f'PROJECT_PATH_PICKER_BOOTSTRAP_FIX85U={len(checks)-len(failed)}/{len(checks)} '+('PASS' if not failed else 'FAIL'))
raise SystemExit(1 if failed else 0)
