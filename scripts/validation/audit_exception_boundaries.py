from pathlib import Path
import re,sys,json
ROOT=Path(__file__).resolve().parents[2]
java=list((ROOT/'src/main/java').rglob('*.java'))
text='\n'.join(p.read_text(encoding='utf-8',errors='ignore') for p in java)
broad=len(re.findall(r'catch\s*\(\s*Exception\s+\w+\s*\)',text))
empty=0
for p in java:
    s=p.read_text(encoding='utf-8',errors='ignore')
    empty += len(re.findall(r'catch\s*\([^)]*\)\s*\{\s*\}',s,re.S))
print(('PASS' if empty==0 else 'FAIL')+f' - empty Java catches: {empty}')
print(('PASS' if broad<=57 else 'FAIL')+f' - broad catch(Exception) boundaries: {broad}/57 baseline maximum')
sink=(ROOT/'src/main/java/com/example/editor/observability/HydraBestEffortDiagnostics.java').read_text()
bounded=bool(re.search(r'MAX_SITES\s*=\s*256',sink)) and 'COUNTS.size()>=MAX_SITES' in sink
print('PASS - best-effort diagnostic sink bounded' if bounded else 'FAIL - best-effort diagnostic sink bound')
sys.exit(0 if empty==0 and broad<=57 and bounded else 1)
