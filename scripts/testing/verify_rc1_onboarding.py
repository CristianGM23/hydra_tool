from __future__ import annotations
import re,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]; DOC=ROOT/'docs/rc1'
required={'README.md':['Ruta recomendada','reference-projects'],'FIRST_PROJECT.md':['Resultado esperado','correlationId'],'MODEL_VALIDATION.md':['quick fix','Problemas'],'GRAMMAR_TEXTUAL.md':['Monaco','Problemas'],'TRANSFORMATIONS.md':['FAIL_ON_CONFLICT','incremental'],'RECOVERY.md':['correlationId','RECOVERY_REQUIRED'],'INSTALLATION.md':['verify_installation_matrix.py','qualify_rc1.py']}
errors=[]
for name,tokens in required.items():
 p=DOC/name
 if not p.exists(): errors.append(f'missing {name}'); continue
 text=p.read_text(encoding='utf-8')
 for token in tokens:
  if token.lower() not in text.lower(): errors.append(f'{name}: missing concept {token}')
 if len(text.split())<45: errors.append(f'{name}: too short')
for e in errors: print('FAIL -',e)
print(f'RC1_ONBOARDING={len(required)} docs status={"PASS" if not errors else "FAIL"}')
raise SystemExit(1 if errors else 0)
