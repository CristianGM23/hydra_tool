#!/usr/bin/env python3
from __future__ import annotations

import re
import os
import shutil
import subprocess
import sys
import tempfile
import textwrap
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def resolve_tsc() -> list[str] | None:
    """Resolve TypeScript through Node before falling back to an OS shim."""
    node = shutil.which('node')
    local_script = ROOT / 'frontend' / 'node_modules' / 'typescript' / 'bin' / 'tsc'
    if node and local_script.is_file():
        return [node, str(local_script)]
    fallback = shutil.which('tsc')
    return [fallback] if fallback else None

SUPPORT = ROOT / 'src/main/java/com/example/editor/integration/compiler/TechnologyAdapterRuntimeLifecycleSupport.java'
VSCODE = ROOT / 'src/main/java/com/example/editor/integration/adapter/vscode/VsCodeExtensionTechnologyAdapter.java'
WME = ROOT / 'src/main/java/com/example/editor/integration/adapter/besser/BesserWebModelingEditorTechnologyAdapter.java'
BESSER = ROOT / 'src/main/java/com/example/editor/integration/adapter/besser/BesserSdkTechnologyAdapter.java'


def require(source: str, needle: str, label: str) -> None:
    if needle not in source:
        raise AssertionError(f'{label}: missing {needle!r}')
    print(f'PASS - {label}')


def java_text_block(source: str, method: str) -> str:
    start = source.index(method)
    return_index = source.index('return """', start)
    content_start = source.index('\n', return_index) + 1
    end = source.index('                """;', content_start)
    return textwrap.dedent(source[content_start:end]).rstrip() + '\n'


def run(command: list[str], cwd: Path) -> str:
    completed = subprocess.run(command, cwd=cwd, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, check=False)
    if completed.returncode != 0:
        raise AssertionError(f"command failed ({' '.join(command)}):\n{completed.stdout}")
    return completed.stdout.strip()


def python_runtime_test(support: str) -> None:
    runtime = java_text_block(support, 'public static String pythonSemanticRuntimeSource()')
    with tempfile.TemporaryDirectory(prefix='hydra-python-runtime-') as tmp:
        root = Path(tmp)
        package = root / 'runtimepkg'
        package.mkdir()
        (package / '__init__.py').write_text('', encoding='utf-8')
        (package / 'runtime_activation.py').write_text(textwrap.dedent('''
            _enabled=False
            _epoch=0
            def is_enabled(): return _enabled
            def epoch(): return _epoch
            def set_enabled(value):
                global _enabled,_epoch
                value=bool(value)
                if value != _enabled:
                    _enabled=value; _epoch+=1
                return _enabled
        '''), encoding='utf-8')
        (package / 'semantic_runtime.py').write_text(runtime, encoding='utf-8')
        (root / 'test_runtime.py').write_text(textwrap.dedent('''
            from runtimepkg import runtime_activation
            from runtimepkg.semantic_runtime import HydraDiagnostic,HydraEvent,HydraGamificationState,HydraSemanticRuntime

            class Engine:
                def __init__(self): self.fail_validation=False; self.fail_game=False
                def validate(self,state,trigger):
                    if self.fail_validation: raise RuntimeError('validation failure')
                    return [HydraDiagnostic('ok','INFO',trigger)]
                def derive_events(self,before,after,trigger,sequence): return [HydraEvent(sequence,'MODEL_CHANGED','model',{'trigger':trigger})]
                def run_event_flow(self,events,state): return [HydraEvent(events[0].sequence,'FLOW','model',{})] if events else []
                def update_gamification(self,previous,events,flow):
                    if self.fail_game: raise RuntimeError('gamification failure')
                    return HydraGamificationState({'xp':previous.points.get('xp',0)+len(events)},'',(),0)

            engine=Engine(); runtime=HydraSemanticRuntime(engine,1)
            r=runtime.synchronize('off-1','OFF'); assert r.rebaselined and not r.changed and runtime.events()==[]
            runtime_activation.set_enabled(True)
            r=runtime.synchronize('on-1','ENABLE'); assert r.rebaselined and not r.changed and runtime.events()==[]
            baseline=runtime.checkpoint(); assert runtime.validate_host('draft','DRAFT')[0].message=='DRAFT'; assert runtime.checkpoint()==baseline
            r=runtime.synchronize('on-2','SAVE'); assert r.changed and len(r.confirmed_events)==1 and len(r.flow_events)==1 and runtime.gamification().points['xp']==1
            before_failure=runtime.checkpoint(); engine.fail_game=True
            try: runtime.synchronize('broken','SAVE')
            except RuntimeError: pass
            else: raise AssertionError('expected gamification failure')
            assert runtime.checkpoint()==before_failure
            engine.fail_game=False
            runtime_activation.set_enabled(False); runtime.synchronize('off-change','OFF_CHANGE')
            hidden=runtime.checkpoint(); assert hidden.events==before_failure.events and hidden.diagnostics==()
            runtime_activation.set_enabled(True); r=runtime.synchronize('reactivated','ON'); assert r.rebaselined and not r.changed and runtime.events()==list(hidden.events)
            stable=runtime.checkpoint(); runtime_activation.set_enabled(False); runtime.synchronize('off-2','OFF'); runtime_activation.set_enabled(True); engine.fail_validation=True
            before_rebaseline=runtime.checkpoint()
            try: runtime.synchronize('rebaseline-failure','ON')
            except RuntimeError: pass
            else: raise AssertionError('expected validation failure')
            assert runtime.checkpoint()==before_rebaseline
            engine.fail_validation=False; runtime.synchronize('seed','ON')
            for index in range(24): runtime.synchronize(f's{index}','SAVE')
            assert len(runtime.events())==16, len(runtime.events())
            print(f'HYDRA_PYTHON_PORTABLE_RUNTIME=PASS seq={runtime.sequence()} history={len(runtime.events())}')
        '''), encoding='utf-8')
        output = run([sys.executable, 'test_runtime.py'], root)
        require(output, 'HYDRA_PYTHON_PORTABLE_RUNTIME=PASS', 'Python lifecycle execution')


def typescript_runtime_test(support: str) -> None:
    tsc = resolve_tsc()
    node = shutil.which('node')
    if not tsc or not node:
        raise AssertionError('local frontend TypeScript compiler (or tsc fallback) and node are required for the portable runtime gate')
    runtime = java_text_block(support, 'public static String typescriptSemanticRuntimeSource()')
    with tempfile.TemporaryDirectory(prefix='hydra-ts-runtime-') as tmp:
        root = Path(tmp)
        (root / 'package.json').write_text('{"type":"module"}\n', encoding='utf-8')
        (root / 'hydra-runtime-activation.ts').write_text(textwrap.dedent('''
            let enabled=false;let activationEpoch=0;
            export const hydraRuntimeActivation={isEnabled:()=>enabled,epoch:()=>activationEpoch,setEnabled:(value:boolean)=>{if(enabled!==value){enabled=value;activationEpoch++;}return enabled;}} as const;
        '''), encoding='utf-8')
        (root / 'hydra-semantic-runtime.ts').write_text(runtime, encoding='utf-8')
        (root / 'test-runtime.ts').write_text(textwrap.dedent('''
            import {hydraRuntimeActivation} from './hydra-runtime-activation.js';
            import {HydraSemanticRuntime,type HydraSemanticEngine,type HydraGamificationState} from './hydra-semantic-runtime.js';
            let failValidation=false,failGame=false;
            const engine:HydraSemanticEngine={
              validate:(_state,trigger)=>{if(failValidation)throw new Error('validation failure');return[{id:'ok',severity:'INFO',message:trigger,elementId:'',quickFixIds:[]}];},
              deriveEvents:(_before,_after,trigger,sequence)=>[{sequence,type:'MODEL_CHANGED',elementId:'model',payload:{trigger}}],
              runEventFlow:(events)=>events.length?[{sequence:events[0].sequence,type:'FLOW',elementId:'model',payload:{}}]:[],
              updateGamification:(previous:HydraGamificationState,events)=>{if(failGame)throw new Error('gamification failure');return{points:{xp:(previous.points.xp??0)+events.length},level:'',achievements:[],penalties:0};}
            };
            const runtime=new HydraSemanticRuntime(engine,1);
            let result=runtime.synchronize('off-1','OFF');if(!result.rebaselined||result.changed||runtime.events().length)throw new Error('disabled baseline');
            hydraRuntimeActivation.setEnabled(true);result=runtime.synchronize('on-1','ENABLE');if(!result.rebaselined||result.changed)throw new Error('enable baseline');
            const baseline=JSON.stringify(runtime.checkpoint());runtime.validateHost('draft','DRAFT');if(JSON.stringify(runtime.checkpoint())!==baseline)throw new Error('validation side effect');
            result=runtime.synchronize('on-2','SAVE');if(!result.changed||result.confirmedEvents.length!==1||result.flowEvents.length!==1||runtime.gamification().points.xp!==1)throw new Error('commit');
            const beforeFailure=JSON.stringify(runtime.checkpoint());failGame=true;try{runtime.synchronize('broken','SAVE');throw new Error('expected failure');}catch(error){if((error as Error).message==='expected failure')throw error;}if(JSON.stringify(runtime.checkpoint())!==beforeFailure)throw new Error('partial mutation');failGame=false;
            hydraRuntimeActivation.setEnabled(false);runtime.synchronize('off-change','OFF_CHANGE');const hidden=runtime.checkpoint();hydraRuntimeActivation.setEnabled(true);result=runtime.synchronize('reactivated','ON');if(!result.rebaselined||result.changed||runtime.events().length!==hidden.events.length)throw new Error('replay');
            hydraRuntimeActivation.setEnabled(false);runtime.synchronize('off-2','OFF');hydraRuntimeActivation.setEnabled(true);failValidation=true;const beforeRebaseline=JSON.stringify(runtime.checkpoint());try{runtime.synchronize('bad','ON');throw new Error('expected validation failure');}catch(error){if((error as Error).message==='expected validation failure')throw error;}if(JSON.stringify(runtime.checkpoint())!==beforeRebaseline)throw new Error('failed rebaseline mutation');failValidation=false;
            runtime.synchronize('seed','ON');for(let i=0;i<24;i++)runtime.synchronize(`s${i}`,'SAVE');if(runtime.events().length!==16)throw new Error(`history ${runtime.events().length}`);
            console.log(`HYDRA_TYPESCRIPT_PORTABLE_RUNTIME=PASS seq=${runtime.sequence()} history=${runtime.events().length}`);
        '''), encoding='utf-8')
        (root / 'tsconfig.json').write_text(textwrap.dedent('''
            {"compilerOptions":{"target":"ES2022","module":"NodeNext","moduleResolution":"NodeNext","strict":true,"outDir":"dist","skipLibCheck":true},"include":["*.ts"]}
        '''), encoding='utf-8')
        run([*tsc, '-p', 'tsconfig.json'], root)
        output = run([node, 'dist/test-runtime.js'], root)
        require(output, 'HYDRA_TYPESCRIPT_PORTABLE_RUNTIME=PASS', 'TypeScript lifecycle execution')


def main() -> None:
    support = SUPPORT.read_text(encoding='utf-8')
    vscode = VSCODE.read_text(encoding='utf-8')
    wme = WME.read_text(encoding='utf-8')
    besser = BESSER.read_text(encoding='utf-8')
    for needle,label in [
        ('pythonSemanticRuntimeSource', 'common Python lifecycle source'),
        ('Math.max(16,historyLimit)', 'TypeScript bounded history'),
        ('nextGamification=this.engine.updateGamification', 'TypeScript staged gamification'),
        ('next_gamification = deepcopy', 'Python staged gamification'),
    ]: require(support, needle, label)
    for needle,label in [
        ('runtimeSessions.ts', 'VS Code document session generator'),
        ('VSCODE_DRAFT_VALIDATE', 'VS Code side-effect-free draft validation'),
        ('onDidSaveTextDocument(document=>sessions.commit(document))', 'VS Code save commit boundary'),
        ('activationEpoch===hydraRuntimeActivation.epoch()', 'VS Code checkpoint epoch guard'),
        ('runtime.restore(before)', 'VS Code capability rollback'),
    ]: require(vscode, needle, label)
    for needle,label in [
        ('class HydraBesserWmeRuntime', 'WME concrete runtime'),
        ('BESSER_WME_DRAFT_VALIDATE', 'WME draft validation'),
        ('this.lifecycle.restore(before)', 'WME capability rollback'),
    ]: require(wme, needle, label)
    for needle,label in [
        ('semantic_runtime.py', 'BESSER SDK Python lifecycle file'),
        ('class HydraBesserRuntime', 'BESSER SDK concrete runtime'),
        ('BESSER_SDK_DRAFT_VALIDATE', 'BESSER SDK draft validation'),
        ('self.lifecycle.restore(before)', 'BESSER SDK capability rollback'),
    ]: require(besser, needle, label)
    python_runtime_test(support)
    typescript_runtime_test(support)
    print('PORTABLE_HOST_RUNTIME_LIFECYCLE=PASS')


if __name__ == '__main__':
    main()
