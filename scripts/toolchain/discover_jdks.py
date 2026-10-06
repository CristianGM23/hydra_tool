from __future__ import annotations
import argparse,json,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'scripts/toolchain'))
from hydra_environment import discover_jdks  # noqa:E402
p=argparse.ArgumentParser(description='Descubre JDK compatibles con la política HYDRA.')
p.add_argument('--jdk-home',default='');p.add_argument('--json',action='store_true');p.add_argument('--all',action='store_true');p.add_argument('--write-cmd',default='',help='Escribe un .cmd que activa el JDK seleccionado solo para la sesión llamadora.')
a=p.parse_args();candidates=discover_jdks(a.jdk_home or None);selected=next((x for x in candidates if x.accepted),None)
payload={'schemaVersion':'1.0','selected':selected.__dict__ if selected else None,'candidates':[x.__dict__ for x in candidates]}
if a.write_cmd:
 out=Path(a.write_cmd);out.parent.mkdir(parents=True,exist_ok=True)
 if selected:
  root=str(Path(selected.root));bin_dir=str(Path(root)/'bin')
  out.write_text('@echo off\nset "HYDRA_JAVA_HOME='+root+'"\nset "JAVA_HOME='+root+'"\nset "PATH='+bin_dir+';%PATH%"\n',encoding='utf-8')
 else:
  out.write_text('@echo off\necho FAIL: no compatible HYDRA JDK selected.\nexit /b 2\n',encoding='utf-8')
if a.json: print(json.dumps(payload,ensure_ascii=False))
else:
 for item in candidates:
  print(('PASS' if item.accepted else 'SKIP'),item.root,'source='+item.source,'version='+item.version,'reasons='+','.join(item.reasons))
 if selected: print('SELECTED',selected.root)
raise SystemExit(0 if selected else 2)
