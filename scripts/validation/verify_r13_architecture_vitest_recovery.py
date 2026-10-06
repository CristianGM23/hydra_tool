#!/usr/bin/env python3
from pathlib import Path
import re

ROOT=Path(__file__).resolve().parents[2]
checks=[]
def text(path): return (ROOT/path).read_text(encoding='utf-8')
def check(label, condition):
    checks.append((label,bool(condition)))
    print(('PASS' if condition else 'FAIL')+' - '+label)

main_css_path=ROOT/'frontend/src/grammar-designer/grammar-designer.css'
responsive_css_path=ROOT/'frontend/src/grammar-designer/grammar-designer-responsive.css'
completion_css_path=ROOT/'frontend/src/grammar-designer/grammar-editor-completion.css'
main_css=main_css_path.read_text(encoding='utf-8')
responsive_css=responsive_css_path.read_text(encoding='utf-8')
completion_css=completion_css_path.read_text(encoding='utf-8')
monaco=text('frontend/src/grammar-designer/GrammarMonacoEditor.tsx')
designer=text('frontend/src/grammar-designer/GrammarDesigner.tsx')
runner=text('frontend/scripts/hydra-test-runner.mjs')
policy=text('frontend/scripts/hydra-test-runner-policy.mjs')
policy_test=text('frontend/scripts/hydra-test-runner-policy.test.mjs')

check('grammar designer primary stylesheet stays below shared 1500-line ceiling', len(main_css.splitlines()) <= 1500)
check('extracted responsive stylesheet stays below shared 1500-line ceiling', len(responsive_css.splitlines()) <= 1500)
check('shared completion stylesheet stays below shared 1500-line ceiling', len(completion_css.splitlines()) <= 1500)
check('grammar designer imports the extracted stylesheet after the base stylesheet', "import './grammar-designer.css';\nimport './grammar-designer-responsive.css';" in designer)
check('responsive stylesheet owns grammar header and live preview surfaces', all(token in responsive_css for token in ['.grammar-designer-header{','.grammar-textual-preview .grammar-preview-editor']))
check('completion stylesheet is loaded by the shared editor and owns Ctrl+Space popup styles', "import './grammar-editor-completion.css';" in monaco and '.grammar-safe-completion{' in completion_css)
check('runner captures batch output so worker bootstrap errors can be classified', 'runVitestCaptured' in runner and "stdio: 'pipe'" in runner and 'isWorkerInfrastructureFailure(batchResult.output)' in runner)
check('Windows recovery has multiple isolated pool choices', "['threads', 'forks', 'vmThreads']" in policy and 'resolveRecoveryPools' in policy)
check('worker infrastructure classification remains guarded against real product failures', '[vitest-pool' in policy and 'Timeout waiting for worker to respond' in policy and 'PRODUCT_FAILURE_SIGNATURE' in policy and 'AssertionError' in policy)
check('recovery changes pool only after infrastructure classification', 'if (!isWorkerInfrastructureFailure(recoveryResult.output))' in runner and 'trying the next isolated pool' in runner)
check('policy regression covers the reported inputErrorExplanation worker failure', 'inputErrorExplanation.test.ts' in policy_test and "expected 2 to be 3" in policy_test and 'Test timed out in 30000ms' in policy_test)

failed=[label for label,ok in checks if not ok]
print(f'R13_ARCHITECTURE_VITEST_RECOVERY={len(checks)-len(failed)}/{len(checks)}')
if failed: raise SystemExit(1)
