from __future__ import annotations
from collections import defaultdict
from pathlib import Path
import re,sys
ROOT=Path(__file__).resolve().parents[2]
JAVA=ROOT/'src/main/java'
route_re=re.compile(r'@(Get|Post|Put|Delete|Patch)Mapping(?:\(([^\)]*)\))?',re.S)
request_re=re.compile(r'@RequestMapping\(([^\)]*)\)',re.S)
quote_re=re.compile(r'(?:value\s*=\s*)?"([^"]*)"')
routes=[]
for path in JAVA.rglob('*Controller.java'):
    source=path.read_text(encoding='utf-8',errors='ignore')
    class_pos=source.find('class ')
    class_prefix=''
    if class_pos>=0:
        matches=list(request_re.finditer(source[:class_pos]))
        if matches:
            quoted=quote_re.search(matches[-1].group(1))
            if quoted: class_prefix=quoted.group(1)
    for match in route_re.finditer(source):
        verb=match.group(1).upper(); args=match.group(2) or ''
        quoted=quote_re.search(args); suffix=quoted.group(1) if quoted else ''
        route=(class_prefix.rstrip('/')+'/'+suffix.lstrip('/')).replace('//','/') or '/'
        line=source.count('\n',0,match.start())+1
        routes.append((verb,route,str(path.relative_to(ROOT)).replace('\\','/'),line))
by_key=defaultdict(list)
for item in routes: by_key[item[:2]].append(item)
duplicates={key:values for key,values in by_key.items() if len(values)>1}
print(f'HTTP_ROUTES={len(routes)}')
if duplicates:
    for key,values in sorted(duplicates.items()):
        print('FAIL - duplicate mapping',key)
        for value in values: print(f'  {value[2]}:{value[3]}')
    sys.exit(1)
print('UPDATE_SITE_ROUTE_UNIQUENESS=PASS')
