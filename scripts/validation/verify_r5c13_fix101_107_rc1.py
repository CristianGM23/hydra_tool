from __future__ import annotations
import json,re,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]; checks=[]
def check(label,cond): checks.append((label,bool(cond))); print(('PASS' if cond else 'FAIL'),'-',label)
check('Fix101 product scenarios exist',(ROOT/'config/rc1-product-scenarios.json').exists() and (ROOT/'scripts/testing/run_rc1_product_e2e.py').exists())
sc=json.loads((ROOT/'config/rc1-product-scenarios.json').read_text(encoding='utf-8')); check('Fix101 has at least three cross-layer scenarios',len(sc.get('scenarios',[]))>=3)
check('Fix102 UX audit is policy-driven',(ROOT/'config/rc1-ux-audit-policy.json').exists() and (ROOT/'scripts/ux/audit_product_ux.py').exists())
check('Fix103 ships three reference projects',len(list((ROOT/'reference-projects').glob('*/reference-project.json')))>=3)
check('Fix104 scale qualification includes 100/1000/10000/50000',{100,1000,10000,50000}<={x['elements'] for x in json.loads((ROOT/'config/rc1-scale-budgets.json').read_text())['levels']})
check('Fix104 runtime scale test exists',(ROOT/'src/test/java/com/example/editor/rc1/Rc1TransformationScaleQualificationTests.java').exists())
check('Fix105 installation matrix covers spaces and unicode',all(x in (ROOT/'config/rc1-installation-matrix.json').read_text(encoding='utf-8') for x in ['ascii-space','unicode']))
check('Fix105 installation verifier exists',(ROOT/'scripts/release/verify_installation_matrix.py').exists())
check('Fix106 onboarding has seven verified documents',len(list((ROOT/'docs/rc1').glob('*.md')))>=7 and (ROOT/'scripts/testing/verify_rc1_onboarding.py').exists())
check('Fix107 RC1 policy and qualifier exist',(ROOT/'config/rc1-qualification-policy.json').exists() and (ROOT/'scripts/release/qualify_rc1.py').exists())
rc1=(ROOT/'scripts/release/qualify_rc1.py').read_text(encoding='utf-8'); backend=rc1[rc1.index('def managed_backend'):rc1.index('def sync_steps')]
check('RC1 managed backend terminates the whole process tree','_kill_tree(proc' in backend and 'proc.terminate()' not in backend)
check('RC1 managed backend refuses an occupied port','connect_ex' in backend and 'already in use' in backend)
check('RC1 managed backend runs the packaged jar, not spring-boot:run','spring-boot:run' not in backend and "'package'" in backend and "'-jar'" in backend)
final=(ROOT/'scripts/validation/verify_final_tree.py').read_text(encoding='utf-8'); check('RC1 gate participates in final tree','Fix101-107 RC1 product qualification' in final)
ci=(ROOT/'.github/workflows/hydra-ci.yml').read_text(encoding='utf-8'); check('CI executes RC1 full qualification','qualify_rc1.py --full' in ci)
inv=(ROOT/'scripts/release/source_inventory.py').read_text(encoding='utf-8'); check('reference-projects are in reproducible source inventory',"'reference-projects'" in inv)
notes=(ROOT/'HYDRA_V4.32.107_RELEASE_NOTES.md').read_text(encoding='utf-8'); check('release notes cover Fix101 through Fix107',all(f'Fix{n}' in notes for n in range(101,108)))
failed=[x for x in checks if not x[1]]
print(f'FIX101_107_RC1={len(checks)-len(failed)}/{len(checks)} '+('PASS' if not failed else 'FAIL'))
raise SystemExit(1 if failed else 0)
