from __future__ import annotations
from pathlib import Path
import re,sys
ROOT=Path(__file__).resolve().parents[2]
SRC=ROOT/'frontend/src'
EXTS={'.ts','.tsx'}
files=[p for p in SRC.rglob('*') if p.suffix in EXTS and '.test.' not in p.name]
by_abs={p.resolve():p for p in files}
g={p.resolve():[] for p in files}
IMPORT=re.compile(r"(?:import|export)\s+(?:[^'\"]*?\s+from\s+)?['\"]([^'\"]+)['\"]")
for p in files:
    text=p.read_text(encoding='utf-8',errors='ignore')
    for match in IMPORT.finditer(text):
        value=match.group(1)
        if not value.startswith('.'): continue
        base=(p.parent/value).resolve()
        candidates=[Path(str(base)+'.ts').resolve(),Path(str(base)+'.tsx').resolve(),(base/'index.ts').resolve(),(base/'index.tsx').resolve()]
        for candidate in candidates:
            if candidate in g: g[p.resolve()].append(candidate); break
index=0;indices={};low={};stack=[];on=set();cycles=[]
def visit(v):
    global index
    indices[v]=low[v]=index;index+=1;stack.append(v);on.add(v)
    for w in g[v]:
        if w not in indices: visit(w);low[v]=min(low[v],low[w])
        elif w in on: low[v]=min(low[v],indices[w])
    if low[v]==indices[v]:
        component=[]
        while True:
            w=stack.pop();on.remove(w);component.append(w)
            if w==v:break
        if len(component)>1:cycles.append(component)
for v in g:
    if v not in indices:visit(v)

def logical_lines(text:str)->int:
    text=re.sub(r'/\*.*?\*/','',text,flags=re.S)
    return sum(1 for line in text.splitlines() if line.strip() and not line.lstrip().startswith('//'))

errors=[]; limit=750
for component in cycles:
    errors.append('dependency cycle: '+' -> '.join(str(by_abs[p].relative_to(SRC)) for p in component))
for p in files:
    rel=p.relative_to(SRC)
    lines=logical_lines(p.read_text(encoding='utf-8',errors='ignore'))
    if lines>limit: errors.append(f'source ceiling exceeded: {rel} {lines}>{limit}')
    parts=rel.parts
    if len(parts)>=2:
        subsystem=parts[0];text=p.read_text(encoding='utf-8',errors='ignore')
        if re.search(rf"from ['\"]\.\./{re.escape(subsystem)}['\"]",text): errors.append(f'internal barrel import: {rel}')
print('FRONTEND_ARCHITECTURE='+('PASS' if not errors else 'FAIL'))
print(f'FILES={len(files)} UNIFORM_LOGICAL_LINE_LIMIT={limit} EXCEPTIONS=0')
for error in errors:print('FAIL ::',error)
sys.exit(1 if errors else 0)
