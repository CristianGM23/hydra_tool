#!/usr/bin/env python3
"""D8: opt-in OS installer/copy + real Tauri process launch on ephemeral CI agents.

WARNING: Executes genuine installer/application code. Run ONLY on trusted CI
runners with reviewed build outputs, NEVER from a REST request or user upload.
Successful launch is NOT proof of model round-trip or signed auto-updates.
"""
from __future__ import annotations
import argparse
import json
import os
from pathlib import Path
import platform
import shutil
import subprocess
import sys
import tempfile
import time

PLATFORM={'Windows':'windows','Linux':'linux','Darwin':'macos'}

def locate_bundle(folder:Path,system:str)->Path:
    if folder.is_symlink() or not folder.is_dir():raise ValueError('Native bundle directory is missing or linked')
    types={'windows':('.msi',),'linux':('.appimage',),'macos':('.app',)}
    members=[p for p in folder.rglob('*') if p.name.lower().endswith(types[system]) and
             (p.is_dir() if system=='macos' else p.is_file())]
    members=sorted(members)
    if len(members)!=1:raise ValueError('Require exactly one supported verified package for native OS: '+str(len(members)))
    item=members[0]
    for part in [item,*list(item.parents)]:
        if part==folder.parent:break
        if part.is_symlink():raise ValueError('Symbolic installer path')
    return item

def stage(bundle:Path,system:str,destination:Path)->tuple[list[str],Path]:
    if system=='macos':
        app=destination/'Applications'/bundle.name
        app.parent.mkdir(parents=True,exist_ok=True)
        shutil.copytree(bundle,app,symlinks=False)
        binaries=[p for p in (app/'Contents/MacOS').iterdir() if p.is_file() and os.access(p,os.X_OK)]
        if len(binaries)!=1:raise ValueError('Expected exactly one executable inside copied macOS application')
        return [str(binaries[0])],app
    if system=='linux':
        app=destination/bundle.name
        shutil.copy2(bundle,app);app.chmod(app.stat().st_mode|0o100)
        runner=shutil.which('xvfb-run')
        if not runner:raise ValueError('xvfb-run unavailable: required for Linux headless GUI smoke')
        return [runner,'-a',str(app),'--appimage-extract-and-run'],app
    if system=='windows':
        install=destination/'InstalledEditor'
        install.mkdir(parents=True,exist_ok=True)
        result=subprocess.run(['msiexec.exe','/i',str(bundle),'/qn','/norestart','ALLUSERS=2','MSIINSTALLPERUSER=1',
                               'INSTALLDIR='+str(install)],timeout=180,capture_output=True,text=True)
        if result.returncode!=0:raise ValueError('Real MSI installation failed with exit '+str(result.returncode))
        executables=[p for p in install.rglob('*.exe') if p.is_file() and not p.is_symlink()]
        if len(executables)!=1:raise ValueError('MSI installed but executable not found uniquely under isolated root')
        return [str(executables[0])],install
    raise ValueError('Unknown system')

def run(bundle_dir:Path,system:str,*,execute:bool,seconds:float=4.0)->dict:
    if system!=PLATFORM.get(platform.system()):raise ValueError('Requested OS is not the executing system')
    if not execute:return {'status':'NOT_EXECUTED','installed':False,'launched':False,'nativeModelRoundtripCertified':False}
    if not 1.0<=seconds<=15.0:raise ValueError('Smoke observation outside allowed range')
    bundle=locate_bundle(bundle_dir,system)
    with tempfile.TemporaryDirectory(prefix='hydra-fix180-os-native-') as temp:
        target=Path(temp)
        command,installed=stage(bundle,system,target)
        if not installed.exists():raise ValueError('OS installed/copied application does not exist')
        process=subprocess.Popen(command,cwd=target,stdin=subprocess.DEVNULL,stdout=subprocess.DEVNULL,stderr=subprocess.PIPE,
                                 shell=False)
        try:
            deadline=time.monotonic()+seconds
            while time.monotonic()<deadline:
                if process.poll() is not None:
                    raise ValueError('Native editor exited unexpectedly with code '+str(process.returncode))
                time.sleep(.2)
            return {'schemaVersion':1,'status':'PROCESS_LAUNCH_VERIFIED','platform':system,
                    'installerOrApp':bundle.relative_to(bundle_dir).as_posix(),
                    'installed':True,'launched':True,'observedSeconds':seconds,
                    'nativeModelRoundtripCertified':False,'osCodeSignatureVerified':False,
                    'nativeSignedUpdateCertified':False}
        finally:
            if process.poll() is None:
                process.terminate()
                try:process.wait(timeout=8)
                except subprocess.TimeoutExpired:process.kill();process.wait(timeout=8)
            process.stderr.close()

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--bundle-dir',type=Path,required=True)
    parser.add_argument('--platform',choices=('windows','linux','macos'),required=True)
    parser.add_argument('--execute',action='store_true')
    parser.add_argument('--seconds',type=float,default=4.0)
    parser.add_argument('--report',type=Path)
    args=parser.parse_args()
    try:
        r=run(args.bundle_dir,args.platform,execute=args.execute,seconds=args.seconds)
        data=json.dumps(r,sort_keys=True,indent=2)+'\n'
        if args.report:args.report.parent.mkdir(parents=True,exist_ok=True);args.report.write_text(data)
        print(data,end='')
        return 0 if r['status']=='PROCESS_LAUNCH_VERIFIED' else 1
    except (ValueError,OSError,subprocess.TimeoutExpired) as exc:
        print('FIX180_NATIVE_OS_SMOKE=FAIL '+str(exc),file=sys.stderr);return 2

if __name__=='__main__':raise SystemExit(main())
