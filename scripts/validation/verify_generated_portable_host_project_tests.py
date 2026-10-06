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
    node = shutil.which('node')
    local_script = ROOT / 'frontend' / 'node_modules' / 'typescript' / 'bin' / 'tsc'
    if node and local_script.is_file():
        return [node, str(local_script)]
    fallback = shutil.which('tsc')
    return [fallback] if fallback else None

SUPPORT = ROOT / 'src/main/java/com/example/editor/integration/compiler/TechnologyAdapterRuntimeLifecycleSupport.java'
WME = ROOT / 'src/main/java/com/example/editor/integration/adapter/besser/BesserWebModelingEditorTechnologyAdapter.java'
BESSER = ROOT / 'src/main/java/com/example/editor/integration/adapter/besser/BesserSdkTechnologyAdapter.java'
VSCODE = ROOT / 'src/main/java/com/example/editor/integration/adapter/vscode/VsCodeExtensionTechnologyAdapter.java'


def run(command: list[str], cwd: Path) -> str:
    completed = subprocess.run(command, cwd=cwd, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, check=False)
    if completed.returncode != 0:
        raise AssertionError(f"command failed ({' '.join(command)}):\n{completed.stdout}")
    return completed.stdout.strip()


def text_block(source: str, method_marker: str) -> str:
    start = source.index(method_marker)
    return_index = source.index('return """', start)
    content_start = source.index('\n', return_index) + 1
    match = re.search(r'\n[ \t]+"""(?:;|\.formatted)', source[content_start:])
    if match is None:
        raise AssertionError(f'could not find Java text-block end for {method_marker}')
    end = content_start + match.start()
    return textwrap.dedent(source[content_start:end]).rstrip() + '\n'


def replace_once(source: str, value: str) -> str:
    if '%s' not in source:
        raise AssertionError('not enough %s placeholders in generated test source')
    return source.replace('%s', value, 1)


def write_vitest_stub(root: Path) -> None:
    vitest = root / 'node_modules' / 'vitest'
    vitest.mkdir(parents=True)
    (vitest / 'package.json').write_text(
        '{"name":"vitest","version":"0.0.0-hydra","type":"module","exports":{".":{"types":"./index.d.ts","default":"./index.js"}}}\n',
        encoding='utf-8',
    )
    (vitest / 'index.d.ts').write_text(textwrap.dedent('''
        export function afterEach(callback:()=>void):void;
        export function describe(name:string,callback:()=>void):void;
        export function it(name:string,callback:()=>void):void;
        export function expect(actual:any):{toBe(expected:any):void;toEqual(expected:any):void;toThrow(expected?:string):void};
    '''), encoding='utf-8')
    (vitest / 'index.js').write_text(textwrap.dedent('''
        let hook=()=>{};
        export function afterEach(callback){hook=callback;}
        export function describe(_name,callback){callback();}
        export function it(name,callback){try{callback();console.log('PASS - '+name);}finally{hook();}}
        const same=(a,b)=>JSON.stringify(a)===JSON.stringify(b);
        export function expect(actual){return{
          toBe(expected){if(!Object.is(actual,expected))throw new Error(`expected ${JSON.stringify(actual)} to be ${JSON.stringify(expected)}`);},
          toEqual(expected){if(!same(actual,expected))throw new Error(`expected ${JSON.stringify(actual)} to equal ${JSON.stringify(expected)}`);},
          toThrow(expected){if(typeof actual!=='function')throw new Error('toThrow requires a function');let thrown;try{actual();}catch(error){thrown=error;}if(!thrown)throw new Error('expected function to throw');if(expected && !String(thrown.message??thrown).includes(expected))throw new Error(`unexpected thrown error ${String(thrown.message??thrown)}`);}
        };}
    '''), encoding='utf-8')


def python_project_test(support: str, adapter: str) -> None:
    runtime = text_block(adapter, 'private String runtimeSource()')
    tests = text_block(adapter, 'private String testSource(String module, boolean visual)')
    for value in ('people_hydra', 'people_hydra', 'people_hydra', '', ''):
        tests = replace_once(tests, value)
    semantic = text_block(support, 'public static String pythonSemanticRuntimeSource()')
    with tempfile.TemporaryDirectory(prefix='hydra-generated-besser-tests-') as tmp:
        root = Path(tmp)
        pkg = root / 'people_hydra'
        tests_dir = root / 'tests'
        pkg.mkdir(); tests_dir.mkdir()
        (pkg / '__init__.py').write_text(
            "from .model import hydra_domain_model\nfrom .runtime import HydraBesserRuntime\n__all__=['hydra_domain_model','HydraBesserRuntime']\n",
            encoding='utf-8',
        )
        (pkg / 'model.py').write_text(textwrap.dedent('''
            class _Model:
                def validate(self, raise_exception=False): return {'success': True}
            hydra_domain_model=_Model()
        '''), encoding='utf-8')
        (pkg / 'runtime_activation.py').write_text(textwrap.dedent('''
            _enabled=False
            _epoch=0
            def is_enabled(): return _enabled
            def epoch(): return _epoch
            def set_enabled(value):
                global _enabled,_epoch
                value=bool(value)
                if _enabled != value:
                    _enabled=value; _epoch+=1
                return _enabled
            def toggle(): return set_enabled(not _enabled)
        '''), encoding='utf-8')
        (pkg / 'semantic_runtime.py').write_text(semantic, encoding='utf-8')
        (pkg / 'runtime.py').write_text(runtime, encoding='utf-8')
        (tests_dir / 'test_generated_model.py').write_text(tests, encoding='utf-8')
        output = run([sys.executable, '-m', 'pytest', '-q'], root)
        if '4 passed' not in output:
            raise AssertionError(f'generated BESSER pytest suite did not report four passes:\n{output}')
        print('PASS - generated BESSER SDK pytest lifecycle suite')


def wme_project_test(support: str, adapter: str) -> None:
    tsc = resolve_tsc(); node = shutil.which('node')
    if not tsc or not node:
        raise AssertionError('local frontend TypeScript compiler (or tsc fallback) and node are required')
    runtime = text_block(adapter, 'private String runtimeSource()')
    tests = text_block(adapter, 'private String testSource(String diagramType, boolean visual)')
    for value in ('', 'people-diagram', ''):
        tests = replace_once(tests, value)
    semantic = text_block(support, 'public static String typescriptSemanticRuntimeSource()')
    with tempfile.TemporaryDirectory(prefix='hydra-generated-wme-tests-') as tmp:
        root = Path(tmp); src = root / 'src'; tests_dir = root / 'tests'
        src.mkdir(); tests_dir.mkdir(); write_vitest_stub(root)
        (root / 'package.json').write_text('{"type":"module"}\n', encoding='utf-8')
        (src / 'hydra-runtime-activation.ts').write_text(textwrap.dedent('''
            let enabled=false;let activationEpoch=0;
            export const hydraRuntimeActivation={isEnabled:()=>enabled,epoch:()=>activationEpoch,setEnabled:(value:boolean)=>{if(enabled!==value){enabled=value;activationEpoch++;}return enabled;},toggle:()=>hydraRuntimeActivation.setEnabled(!enabled)} as const;
        '''), encoding='utf-8')
        (src / 'hydra-semantic-runtime.ts').write_text(semantic, encoding='utf-8')
        (src / 'hydra-runtime.ts').write_text(runtime, encoding='utf-8')
        (src / 'hydra-diagram-type.ts').write_text("export const HYDRA_BESSER_DIAGRAM_TYPE='people-diagram';\n", encoding='utf-8')
        (tests_dir / 'extension.test.ts').write_text(tests, encoding='utf-8')
        (root / 'tsconfig.json').write_text(textwrap.dedent('''
            {"compilerOptions":{"target":"ES2022","module":"NodeNext","moduleResolution":"NodeNext","strict":true,"outDir":"dist","rootDir":".","skipLibCheck":true},"include":["src/**/*.ts","tests/**/*.ts"]}
        '''), encoding='utf-8')
        run([*tsc, '-p', 'tsconfig.json'], root)
        output = run([node, 'dist/tests/extension.test.js'], root)
        for name in (
            'keeps a stable diagram type',
            'keeps draft validation side-effect free and commits only confirmed states',
            'rolls back the complete runtime checkpoint when a commit capability fails',
            'does not replay changes made while HYDRA is disabled',
        ):
            if f'PASS - {name}' not in output:
                raise AssertionError(f'missing generated WME test result {name!r}:\n{output}')
        print('PASS - generated BESSER WME lifecycle suite')


def vscode_project_test(support: str, adapter: str) -> None:
    tsc = resolve_tsc(); node = shutil.which('node')
    if not tsc or not node:
        raise AssertionError('local frontend TypeScript compiler (or tsc fallback) and node are required')
    semantic = text_block(support, 'public static String typescriptSemanticRuntimeSource()')
    bridge = text_block(adapter, 'private String portableRuntimeBridge()')
    tests = text_block(adapter, 'private String runtimeTestSource()')
    with tempfile.TemporaryDirectory(prefix='hydra-generated-vscode-tests-') as tmp:
        root = Path(tmp); src = root / 'src'; tests_dir = root / 'tests'
        src.mkdir(); tests_dir.mkdir(); write_vitest_stub(root)
        (root / 'package.json').write_text('{"type":"module"}\n', encoding='utf-8')
        (src / 'hydra-runtime-activation.ts').write_text(textwrap.dedent('''
            let enabled=false;let activationEpoch=0;
            export const hydraRuntimeActivation={isEnabled:()=>enabled,isEnabledFor:(_scope:string)=>enabled,epoch:()=>activationEpoch,setEnabled:(value:boolean)=>{if(enabled!==value){enabled=value;activationEpoch++;}return enabled;},toggle:()=>hydraRuntimeActivation.setEnabled(!enabled)} as const;
        '''), encoding='utf-8')
        (src / 'hydra-semantic-runtime.ts').write_text(semantic, encoding='utf-8')
        (src / 'portableRuntimeBridge.ts').write_text(bridge, encoding='utf-8')
        (tests_dir / 'runtime.test.ts').write_text(tests, encoding='utf-8')
        (root / 'tsconfig.json').write_text(textwrap.dedent('''
            {"compilerOptions":{"target":"ES2022","module":"NodeNext","moduleResolution":"NodeNext","strict":true,"outDir":"dist","rootDir":".","skipLibCheck":true},"include":["src/**/*.ts","tests/**/*.ts"]}
        '''), encoding='utf-8')
        run([*tsc, '-p', 'tsconfig.json'], root)
        output = run([node, 'dist/tests/runtime.test.js'], root)
        for name in (
            'keeps draft validation side-effect free and commits only saved states',
            'restores the complete checkpoint after a failed confirmed capability',
            'rebaselines after OFF changes without replaying events',
        ):
            if f'PASS - {name}' not in output:
                raise AssertionError(f'missing generated VS Code test result {name!r}:\n{output}')
        print('PASS - generated VS Code lifecycle suite')


def main() -> None:
    support = SUPPORT.read_text(encoding='utf-8')
    wme = WME.read_text(encoding='utf-8')
    besser = BESSER.read_text(encoding='utf-8')
    vscode = VSCODE.read_text(encoding='utf-8')
    for expected in ('tests/runtime.test.ts', '"test": "vitest run"', 'npm run check && npm test && npm run bundle', 'tests/**'):
        if expected not in vscode:
            raise AssertionError(f'VS Code generated project contract is missing {expected!r}')
    print('PASS - generated VS Code package executes and excludes lifecycle tests correctly')
    python_project_test(support, besser)
    wme_project_test(support, wme)
    vscode_project_test(support, vscode)
    print('GENERATED_PORTABLE_HOST_PROJECT_TESTS=PASS')


if __name__ == '__main__':
    main()
