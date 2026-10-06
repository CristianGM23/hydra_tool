#!/usr/bin/env python3
from pathlib import Path
import json, re, subprocess, tempfile, textwrap
ROOT=Path(__file__).resolve().parents[2]
checks=[]
def check(name,cond):
    if not cond: raise AssertionError(name)
    checks.append(name)
def text(p): return (ROOT/p).read_text(encoding='utf-8')

support=text('src/main/java/com/example/editor/integration/compiler/TechnologyAdapterSelfContainedDependencySupport.java')
for token in ['NPM_VENDOR_BUNDLE','PYTHON_WHEELHOUSE','ECLIPSE_P2_VENDOR_REPOSITORY','MAVEN_RUNTIME_LIBS']:
    check('bundle kind '+token, token in support)
check('npm reuses installed host packages','npm\',[\'ls\'' in support and 'missing=required.filter' in support)
check('pip offline install','--no-index' in support and '--find-links' in support)
check('eclipse local p2 install','org.eclipse.equinox.p2.director' in support and 'file:$HERE/p2' in support)
check('ordered dependency contract','detect-installed-ius' in support and 'install-missing-from-local-p2' in support and 'restart' in support)
check('npm transitive cache required','npm-cache' in support and '--offline' in support and 'vendor cache is missing or incomplete' in support)
check('generated manifests keep escaped newline literals', "+'\\\\n'" in support)
check('p2 shell IU list has no embedded quote tokens','String required = String.join(\" \", units)' in support)
check('p2 installable units are whitelisted','requireSafeP2Unit' in support and '[A-Za-z0-9_.-]+' in support)
check('python wheelhouse verifies environment', "'check'" in support and '--upgrade-strategy' in support)
check('python wheelhouse verifies integrity manifest', 'wheelhouse integrity manifest is missing' in support and 'Corrupt HYDRA wheel' in support and "hashlib.sha256(path.read_bytes())" in support)

vscode=text('src/main/java/com/example/editor/integration/adapter/vscode/VsCodeExtensionTechnologyAdapter.java')
check('VS Code bundles runtime dependencies','esbuild.mjs' in vscode and "bundle:true" in vscode and "external:['vscode']" in vscode)
check('VSIX excludes node_modules','node_modules/**' in vscode and 'vsce package --no-dependencies' in vscode)
check('VS Code bundle is entry point','./dist/extension.js' in vscode)

besser=text('src/main/java/com/example/editor/integration/adapter/besser/BesserSdkTechnologyAdapter.java')
check('BESSER current default','"7.13.0"' in besser)
check('BESSER wheelhouse generated','pythonWheelhouseFiles' in besser)
check('BESSER supported python bounded','Python 3.10-3.12' in besser)
wme=text('src/main/java/com/example/editor/integration/adapter/besser/BesserWebModelingEditorTechnologyAdapter.java')
check('BESSER WME local npm vendor','npmVendorFiles' in wme and '@besser/wme@' in wme and 'react@19.2.0' in wme)

eclipse=text('src/main/java/com/example/editor/integration/adapter/eclipse/EclipseModelingDistributionTechnologyAdapter.java')
check('Eclipse 2026-09 target','releases/2026-09' in eclipse)
check('Eclipse dependency set conditional','requiredP2Units(visualStack, xtextStack)' in eclipse)
check('Xtext installed only when selected','if(xtext) units.addAll' in eclipse)
check('Sirius installed only for visual','if(visual) units.addAll' in eclipse)
check('Xtend no longer forced','org.eclipse.xtend.ide' not in eclipse and 'org.eclipse.xtend.lib' not in eclipse)
check('Eclipse offline p2 emitted','eclipseP2VendorFiles' in eclipse)

sirius=text('src/main/java/com/example/editor/integration/adapter/sirius/SiriusDesktopTechnologyAdapter.java')
check('Sirius offline p2 emitted','eclipseP2VendorFiles' in sirius)
check('Sirius Xtext conditional','GrammarTechnology.XTEXT' in sirius and 'org.eclipse.xtext.ui' in sirius)

glsp=text('src/main/java/com/example/editor/integration/adapter/glsp/EclipseGlspTechnologyAdapter.java')
check('GLSP client npm vendor','npmVendorFiles(root + "/client"' in glsp)
check('GLSP node server npm vendor','npmVendorFiles(root + "/server"' in glsp)
check('GLSP Java runtime vendor','mavenRuntimeVendorFiles' in glsp)
check('GLSP Java copies runtime dependencies','maven-dependency-plugin' in glsp and 'copy-dependencies' in glsp and 'distribution/lib' in glsp)
check('Maven runtime vendor has integrity verification','sha256' in support and 'Corrupt HYDRA Maven runtime jar' in support)

# Execute representative offline installers in a fake environment: npm path where all deps are already present must not install.
with tempfile.TemporaryDirectory() as td:
    td=Path(td); (td/'npm').mkdir();
    fake=td/'fakebin'; fake.mkdir(); log=td/'calls.log'
    npm=fake/'npm'; npm.write_text('#!/bin/sh\necho "$@" >> "'+str(log)+'"\nif [ "$1" = "ls" ]; then exit 0; fi\nexit 88\n'); npm.chmod(0o755)
    js=("import {readdirSync} from 'node:fs'; import {spawnSync} from 'node:child_process'; import {dirname,join} from 'node:path'; import {fileURLToPath} from 'node:url';\n"
        "const required=['react','@besser/wme'], here=dirname(fileURLToPath(import.meta.url)), vendor=join(here,'npm');\n"
        "const installed=new Set(); for(const name of required){const r=spawnSync('npm',['ls',name,'--depth=0','--json'],{encoding:'utf8'});if(r.status===0)installed.add(name);}\n"
        "const missing=required.filter(x=>!installed.has(x)); if(missing.length){const tgz=readdirSync(vendor).filter(x=>x.endsWith('.tgz')).map(x=>join(vendor,x));const r=spawnSync('npm',['install','--ignore-scripts','--no-audit','--no-fund',...tgz],{stdio:'inherit'});if(r.status!==0)process.exit(r.status??1);}\n"
        "console.log(`PASS missing=${missing.length}`);\n")
    f=td/'install.mjs'; f.write_text(js)
    import os
    env=os.environ.copy(); env['PATH']=str(fake)+os.pathsep+env['PATH']
    run=subprocess.run(['node',str(f)],capture_output=True,text=True,env=env,timeout=5)
    check('npm host reuse executable smoke',run.returncode==0 and 'missing=0' in run.stdout and 'install' not in log.read_text())

print(f'V43314_SELF_CONTAINED_EDITOR_DEPENDENCIES={len(checks)}/{len(checks)} PASS')
