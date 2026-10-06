from pathlib import Path
import json,sys
ROOT=Path(__file__).resolve().parents[2]
data=json.loads((ROOT/'config/hydra-invariants.json').read_text(encoding='utf-8'))
checks=[];ids=set()
checks.append(('registry schema is current',data.get('schemaVersion')=='1.0'))
items=data.get('invariants',[])
checks.append(('registry contains a focused current invariant set',20 <= len(items) <= 40))
for item in items:
    ident=item.get('id','')
    unique=bool(ident) and ident not in ids;ids.add(ident)
    fields=all(isinstance(item.get(k),str) and item.get(k).strip() for k in ('owner','rationale','evidence'))
    evidence=(ROOT/item.get('evidence','')).is_file()
    no_history=not any(key in item for key in ('introducedIn','supersededBy','release'))
    checks.append((f'{ident}: fields/id/evidence',unique and fields and evidence and no_history))
for name,ok in checks: print(('PASS' if ok else 'FAIL')+' - '+name)
failed=[name for name,ok in checks if not ok]
print(f'CURRENT_INVARIANTS={len(checks)-len(failed)}/{len(checks)}')
sys.exit(1 if failed else 0)
