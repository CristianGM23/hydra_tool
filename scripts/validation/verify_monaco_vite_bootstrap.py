from pathlib import Path
import json

root=Path(__file__).resolve().parents[2]
checks=[]
def check(label,ok):
    checks.append((label,bool(ok)))

package=json.loads((root/'frontend/package.json').read_text(encoding='utf-8'))
bootstrap=(root/'frontend/src/monacoBootstrap.ts').read_text(encoding='utf-8')
vite=(root/'frontend/vite.config.ts').read_text(encoding='utf-8')
preflight=(root/'frontend/scripts/check-monaco-dependency.mjs').read_text(encoding='utf-8')
main=(root/'frontend/src/main.tsx').read_text(encoding='utf-8')
lock=(root/'frontend/package-lock.json').read_text(encoding='utf-8')

check('Monaco pinned dependency', package.get('dependencies',{}).get('monaco-editor')=='0.52.2')
check('Lock contains Monaco 0.52.2', '"node_modules/monaco-editor"' in lock and '"version": "0.52.2"' in lock)
check('Bootstrap uses public Monaco entry', "from 'monaco-editor';" in bootstrap)
check('Bootstrap avoids editor.api internal entry', 'monaco-editor/esm/vs/editor/editor.api' not in bootstrap)
check('Worker uses Vite worker query', "monaco-editor/esm/vs/editor/editor.worker?worker" in bootstrap)
check('Vite chunks public Monaco entry', "monaco:['monaco-editor']" in vite)
check('Frontend bootstraps Monaco before application', "import './monacoBootstrap'" in main)
check('Dev has Monaco preflight', package.get('scripts',{}).get('predev')=='node scripts/check-monaco-dependency.mjs')
check('Build has Monaco preflight', package.get('scripts',{}).get('prebuild')=='node scripts/check-monaco-dependency.mjs')
check('Preflight requires npm ci on stale node_modules', 'npm ci' in preflight and 'node_modules' in preflight and 'editor.worker.js' in preflight)

failed=[label for label,ok in checks if not ok]
for label,ok in checks: print(f"[{'PASS' if ok else 'FAIL'}] {label}")
print(f"Monaco/Vite bootstrap: {len(checks)-len(failed)}/{len(checks)}")
if failed: raise SystemExit(1)
