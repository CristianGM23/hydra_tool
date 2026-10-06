#!/usr/bin/env python3
"""Audita deuda que suele ocultar warnings o fallos silenciosos en el backend."""
from pathlib import Path
import re,sys
ROOT=Path(__file__).resolve().parents[2]
files=list((ROOT/'src/main/java').rglob('*.java'))
text='\n'.join(p.read_text(encoding='utf-8') for p in files)
checks=[]
def check(name,ok): checks.append((name,bool(ok)))
check('no @SuppressWarnings in production backend','@SuppressWarnings' not in text)
check('no compiler-warning suppression flags','-nowarn' not in (ROOT/'pom.xml').read_text(encoding='utf-8') and '<showWarnings>false</showWarnings>' not in (ROOT/'pom.xml').read_text(encoding='utf-8'))
check('no printStackTrace debugging','printStackTrace(' not in text)
check('no direct System.out debugging','System.out.print' not in text)
check('no direct System.err debugging','System.err.print' not in text)
check('no obsolete Thread stop/suspend/resume',not re.search(r'\bThread\s*\.\s*(?:stop|suspend|resume)\s*\(',text) and 'Thread.currentThread().stop(' not in text and 'Thread.currentThread().suspend(' not in text and 'Thread.currentThread().resume(' not in text)
check('no deprecated Class.newInstance','Class.newInstance(' not in text and '.class.newInstance(' not in text)
check('no finalize overrides',not re.search(r'\bvoid\s+finalize\s*\(',text))
check('no explicit debt markers',not re.search(r'\b(?:TODO|FIXME|HACK|XXX)\b',text))
# Empty catches are never useful warning recovery.
check('no empty catch blocks',not re.search(r'catch\s*\([^)]*\)\s*\{\s*\}',text,re.S))
for name,ok in checks: print(('PASS' if ok else 'FAIL')+' - '+name)
failed=[name for name,ok in checks if not ok]
print(f'BACKEND_WARNING_DEBT={len(checks)-len(failed)}/{len(checks)} '+('PASS' if not failed else 'FAIL'))
sys.exit(1 if failed else 0)
