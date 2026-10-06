from pathlib import Path
import json,re,sys
ROOT=Path(__file__).resolve().parents[2]
config=json.loads((ROOT/'config/frontend-control-budget.json').read_text())
native=[]; hydra=0
for path in (ROOT/'frontend/src').rglob('*.tsx'):
    if '.test.' in path.name or path.name.endswith('.spec.tsx'): continue
    text=path.read_text(encoding='utf-8',errors='ignore')
    count=len(re.findall(r'<button\b',text))
    if count: native.extend([path.relative_to(ROOT).as_posix()]*count)
    hydra += len(re.findall(r'<HydraButton\b',text))
owner=config['nativeButtonOwner']; ok=True
native_ok=len(native)<=config['maxNativeButtons'] and all(item==owner for item in native)
print(('PASS' if native_ok else 'FAIL')+f' - native buttons belong only to HydraButton: {len(native)}/{config["maxNativeButtons"]}')
ok &= native_ok
adoption=hydra>=config['minHydraButtons']
print(('PASS' if adoption else 'FAIL')+f' - HydraButton adoption floor: {hydra}/{config["minHydraButtons"]}')
ok &= adoption
print('FRONTEND_CONTROL_CONVERGENCE='+('PASS' if ok else 'FAIL'))
sys.exit(0 if ok else 1)
