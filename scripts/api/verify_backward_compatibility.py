from pathlib import Path
import json,sys
ROOT=Path(__file__).resolve().parents[2]
current=json.loads((ROOT/'config/api-contract.json').read_text(encoding='utf-8'))
baseline=json.loads((ROOT/'config/api-contract-v1-baseline.json').read_text(encoding='utf-8'))
cur={(o['method'],o['path']):o['operationId'] for o in current['operations']}
missing=[]; changed=[]
for old in baseline['operations']:
    key=(old['method'],old['path'])
    if key not in cur: missing.append(f"{old['method'].upper()} {old['path']}")
    elif cur[key]!=old['operationId']: changed.append(f"{old['method'].upper()} {old['path']} operationId {old['operationId']} -> {cur[key]}")
print(('PASS' if not missing else 'FAIL')+f' - no v1 routes removed ({len(baseline["operations"])} baseline operations)')
print(('PASS' if not changed else 'FAIL')+' - no generated v1 operationIds changed')
if missing:
    print('REMOVED: '+', '.join(missing[:20]))
if changed:
    print('CHANGED: '+', '.join(changed[:20]))
print(f'API_V1_BACKWARD_COMPATIBILITY={"PASS" if not missing and not changed else "FAIL"}')
sys.exit(1 if missing or changed else 0)
