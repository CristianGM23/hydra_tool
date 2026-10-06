from pathlib import Path
import json,re,subprocess,sys,tempfile,shutil
ROOT=Path(__file__).resolve().parents[2]
subprocess.run([sys.executable,str(ROOT/'scripts/api/generate_openapi.py')],cwd=ROOT,check=True)
contract=json.loads((ROOT/'config/api-contract.json').read_text())
ops=contract['operations']; pairs=[(x['method'],x['path']) for x in ops]
checks=[]
def check(name,ok):checks.append((name,bool(ok)));print(('PASS' if ok else 'FAIL'),name)
check('API contract contains broad controller surface',len(ops)>=350)
check('exact method/path mappings are unique',len(pairs)==len(set(pairs)))
check('generated operation ids are unique',len([x['operationId'] for x in ops])==len({x['operationId'] for x in ops}))
yaml=(ROOT/'docs/api/hydra-openapi.yaml').read_text();ts=(ROOT/'frontend/src/api/generated/hydraApiClient.ts').read_text()
check('OpenAPI is 3.1 and release-versioned','openapi: 3.1.0' in yaml and f"version: {contract['hydraVersion']}" in yaml)
check('generated TS client exposes every operation',all(x['operationId'] in ts for x in ops))
check('generated client uses portable backendFetch',"backendFetch" in ts and 'localhost' not in ts)
print(f"OPENAPI_CONTRACT={sum(ok for _,ok in checks)}/{len(checks)} operations={len(ops)}")
if not all(ok for _,ok in checks):sys.exit(1)
