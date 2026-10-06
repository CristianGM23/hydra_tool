#!/usr/bin/env python3
"""Comprueba documentación española y ausencia de supresiones/deuda en backend."""
from pathlib import Path
import sys
sys.path.insert(0,str(Path('scripts/maintenance').resolve()))
from document_backend_methods import declaration_candidates
root=Path('src/main/java'); missing=[]; total=0
for path in sorted(root.rglob('*.java')):
    text=path.read_text(encoding='utf-8')
    for start,name_start,name,*_ in declaration_candidates(text):
        total+=1
        line_start=text.rfind('\n',0,name_start)+1
        local=text[line_start:name_start]
        # Admite la convención compacta en la misma línea y Javadoc multilínea inmediatamente anterior.
        prefix=text[:line_start]
        javadoc_start=prefix.rfind('/**')
        javadoc_end=prefix.rfind('*/')
        preceding=prefix[javadoc_start:javadoc_end+2] if javadoc_start >= 0 and javadoc_end >= javadoc_start and prefix[javadoc_end+2:].strip() == '' else ''
        documentation=local + preceding
        required=('Descripción:', 'Entrada')
        if not all(token in documentation for token in required) or 'Salida:' not in documentation:
            missing.append(f'{path}:{text.count(chr(10),0,name_start)+1}:{name}')
forbidden=[]
for path in root.rglob('*.java'):
    text=path.read_text(encoding='utf-8')
    for marker in ('@SuppressWarnings','TODO','FIXME','HACK','XXX'):
        if marker in text: forbidden.append(f'{path}: contiene {marker}')
if missing or forbidden:
    print(f'BACKEND_SPANISH_METHOD_DOCS=FAIL documented={total-len(missing)}/{total} forbidden={len(forbidden)}')
    for item in (missing+forbidden)[:100]: print('FAIL :: '+item)
    raise SystemExit(1)
print(f'BACKEND_SPANISH_METHOD_DOCS=PASS documented={total}/{total} suppressions=0 debt_markers=0')
