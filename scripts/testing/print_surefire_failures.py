from __future__ import annotations
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

if len(sys.argv)>1:
    root=Path(sys.argv[1])
else:
    candidates=(Path('build/hydra-maven/surefire-reports'),Path('target/surefire-reports'))
    root=next((item for item in candidates if item.exists()),candidates[0])
if not root.exists():
    print(f'[SUREFIRE] No existe {root}.')
    sys.exit(0)
found=False
for xml in sorted(root.glob('TEST-*.xml')):
    try:
        tree=ET.parse(xml).getroot()
    except Exception:
        continue
    failures=int(tree.attrib.get('failures','0') or 0); errors=int(tree.attrib.get('errors','0') or 0)
    if failures==0 and errors==0: continue
    found=True
    print(f'\n[SUREFIRE] {tree.attrib.get("name",xml.stem)}: failures={failures} errors={errors}')
    for case in tree.findall('.//testcase'):
        problem=case.find('failure') or case.find('error')
        if problem is None: continue
        print(f'  - {case.attrib.get("classname","")}.{case.attrib.get("name","")}')
        msg=(problem.attrib.get('message') or '').strip()
        if msg: print(f'    {msg}')
        body=(problem.text or '').strip().splitlines()
        for line in body[:16]: print('    '+line)
if not found:
    texts=[]
    for txt in sorted(root.glob('*.txt')):
        value=txt.read_text(encoding='utf-8',errors='replace')
        if 'FAILURE' in value or 'ERROR' in value or '<<<' in value:
            texts.append((txt,value))
    if texts:
        for txt,value in texts:
            print(f'\n[SUREFIRE] {txt.name}')
            for line in value.splitlines()[-80:]: print(line)
    else:
        print('[SUREFIRE] No se encontró ningún testcase fallido en los informes disponibles.')
