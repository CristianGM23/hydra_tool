from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
checks=[]
def check(name,ok):
    checks.append((name,bool(ok))); print(('PASS' if ok else 'FAIL')+' - '+name)

def text(rel): return (ROOT/rel).read_text(encoding='utf-8')
fix100=text('scripts/release/qualify_release.py')
rc1=text('scripts/release/qualify_rc1.py')
tool=text('scripts/release/verify_toolchain.py');env=text('scripts/toolchain/hydra_environment.py')
lang=text('scripts/validation/verify_language_host_runtime_lifecycle.py')
portable=text('scripts/validation/verify_portable_host_runtime_lifecycle.py')
generated=text('scripts/validation/verify_generated_portable_host_project_tests.py')
final=text('scripts/validation/verify_final_tree.py')
check('Fix100 fails fast on primary source integrity', "run('primary-source-integrity'" in fix100 and 'verify_manifest.py' in fix100)
check('Fix100 bootstraps npm before full tree', "run('primary-npm-ci'" in fix100 and fix100.index("run('primary-npm-ci'") < fix100.index("run('primary-full-tree'"))
check('Fix100 provisions Playwright Chromium before full tree', "run('primary-playwright-chromium'" in fix100 and fix100.index("run('primary-playwright-chromium'") < fix100.index("run('primary-full-tree'"))
check('Fix100 validates compatible toolchain before full tree', "run('primary-toolchain'" in fix100 and fix100.index("run('primary-toolchain'") < fix100.index("run('primary-full-tree'"))
check('RC1 fails fast on workspace manifest drift', "run('rc1-workspace-integrity'" in rc1)
check('toolchain accepts supported Java feature range', 'java_feature_in_range' in tool and 'javaQualificationMax' in tool)
check('toolchain accepts compatible Node/npm patch-minor versions', tool.count('same_major_at_least') >= 3)
check('toolchain uses Windows Maven wrapper when appropriate', 'collect_toolchain' in tool and 'mvnw.cmd' in env and "os.name == \"nt\"" in env)
for label,source in [('language host',lang),('portable host',portable),('generated portable host',generated)]:
    check(label+' gate resolves project-local TypeScript first', "frontend' / 'node_modules' / 'typescript' / 'bin' / 'tsc'" in source and 'resolve_tsc()' in source)
check('qualification diagnostic helper exists', (ROOT/'scripts/release/diagnose_qualification.py').is_file())
check('Fix108 is registered in final tree', 'verify_r5c13_fix108_qualification_bootstrap.py' in final)
failed=[n for n,o in checks if not o]
print(f'FIX108_QUALIFICATION_BOOTSTRAP={len(checks)-len(failed)}/{len(checks)} '+('PASS' if not failed else 'FAIL'))
raise SystemExit(1 if failed else 0)
