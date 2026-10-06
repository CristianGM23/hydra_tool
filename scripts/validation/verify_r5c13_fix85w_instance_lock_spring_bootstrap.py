#!/usr/bin/env python3
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[2]
source = (ROOT / 'src/main/java/com/example/editor/runtime/instance/HydraInstanceLock.java').read_text(encoding='utf-8')
test = (ROOT / 'src/test/java/com/example/editor/runtime/instance/HydraInstanceLockTests.java').read_text(encoding='utf-8')
editor_tests = (ROOT / 'src/test/java/com/example/editor/EditorApplicationTests.java').read_text(encoding='utf-8')
system_tests = (ROOT / 'src/test/java/com/example/editor/system/SystemEndpointsTests.java').read_text(encoding='utf-8')

checks = []
def check(name, condition):
    ok = bool(condition)
    checks.append((name, ok))
    print(('PASS' if ok else 'FAIL') + ' - ' + name)

check('HydraInstanceLock remains a Spring component', '@Component' in source)
check('instance lock bean is conditional on enabled=true', '@ConditionalOnProperty(prefix = "hydra.instance-lock", name = "enabled", havingValue = "true", matchIfMissing = true)' in source)
check('production constructor is explicitly autowired', re.search(r'@Autowired\s+(?:/\*\*.*?\*/\s*)?public\s+HydraInstanceLock\s*\(HydraInstanceLockProperties\s+properties,\s*StorageProperties\s+storage\)', source, re.S) is not None)
check('test-only one-argument constructor remains available', 'HydraInstanceLock(HydraInstanceLockProperties properties)' in source)
check('smoke tests disable only the instance lock', 'hydra.instance-lock.enabled=false' in editor_tests and 'hydra.instance-lock.enabled=false' in system_tests)
check('smoke tests do not activate synthetic test profile', '@ActiveProfiles("test")' not in editor_tests and '@ActiveProfiles("test")' not in system_tests)
check('regression test protects conditional bean contract', 'springBootstrapUsesConditionalBeanAndExplicitInjectionConstructor' in test)
check('regression test inspects ConditionalOnProperty', 'getAnnotation(ConditionalOnProperty.class)' in test)
check('regression test inspects Autowired constructor', 'isAnnotationPresent(Autowired.class)' in test)

failed = [name for name, ok in checks if not ok]
print(f'FIX85W_INSTANCE_LOCK_SPRING_BOOTSTRAP={len(checks)-len(failed)}/{len(checks)} ' + ('PASS' if not failed else 'FAIL'))
raise SystemExit(1 if failed else 0)
