from __future__ import annotations
import json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2];OUT=ROOT/'target/release'
files=[('RC2',OUT/'rc2-qualification.json'),('RC1',OUT/'rc1-qualification.json'),('Fix100',OUT/'release-qualification.json'),('Final tree',OUT/'release-readiness.json')]
failed=0
for label,path in files:
 print(f'=== {label} ===')
 if not path.is_file():print('MISSING',path);continue
 data=json.loads(path.read_text(encoding='utf-8'));print('status=',data.get('status'))
 for step in data.get('steps',[]):
  if step.get('required',True) and not step.get('passed',False):
   failed+=1;print('\nFAIL',step.get('name'))
   if step.get('log'):print('log=',ROOT/step['log'])
   print(step.get('outputTail',''))
 print()
print('=== Checkpoints ===')
checkpoint_dir=OUT/'checkpoints'
if checkpoint_dir.is_dir():
 for path in sorted(checkpoint_dir.glob('*.json')):
  data=json.loads(path.read_text(encoding='utf-8'));failed_names=[name for name,stage in data.get('stages',{}).items() if not stage.get('passed')]
  print(path.name,'failed=',','.join(failed_names) if failed_names else '<none>')
raise SystemExit(1 if failed else 0)
