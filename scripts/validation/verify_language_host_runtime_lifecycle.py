#!/usr/bin/env python3
from __future__ import annotations

import os
import shutil
import subprocess
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
XTEXT = ROOT / 'src/main/java/com/example/editor/integration/adapter/xtext/XtextTechnologyAdapter.java'
LANGIUM = ROOT / 'src/main/java/com/example/editor/integration/adapter/langium/LangiumTechnologyAdapter.java'


def require(source: str, needle: str, label: str) -> None:
    if needle not in source:
        raise AssertionError(f'{label}: missing {needle!r}')
    print(f'PASS - {label}')


def java_text_block(source: str, method: str) -> str:
    start = source.index(method)
    return_index = source.index('return """', start)
    content_start = source.index('\n', return_index) + 1
    end = source.index('"""', content_start)
    return textwrap.dedent(source[content_start:end]).rstrip() + '\n'


def run(command: list[str], cwd: Path) -> str:
    completed = subprocess.run(command, cwd=cwd, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, check=False)
    if completed.returncode != 0:
        raise AssertionError(f"command failed ({' '.join(command)}):\n{completed.stdout}")
    return completed.stdout.strip()


def write(root: Path, relative: str, content: str) -> None:
    path = root / relative
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(textwrap.dedent(content), encoding='utf-8')


def xtext_runtime_test(support: str, xtext: str) -> None:
    javac = shutil.which('javac')
    java = shutil.which('java')
    if not javac or not java:
        raise AssertionError('javac and java are required for the Xtext lifecycle gate')
    semantic = java_text_block(support, 'public static String javaSemanticRuntimeSource').replace('%s', 'test.runtime')
    host = java_text_block(xtext, 'private String xtextRuntimeSource').replace('%s', 'test')
    with tempfile.TemporaryDirectory(prefix='hydra-xtext-runtime-') as tmp:
        root = Path(tmp)
        write(root, 'test/runtime/HydraRuntimeActivation.java', '''
            package test.runtime;
            public final class HydraRuntimeActivation {
                private static boolean enabled=true; private static long epoch=1L;
                public static boolean isEnabled(){return enabled;} public static long epoch(){return epoch;}
                public static void setEnabled(boolean value){if(enabled!=value){enabled=value;epoch++;}}
            }
        ''')
        write(root, 'test/runtime/HydraSemanticRuntime.java', semantic)
        write(root, 'test/runtime/HydraXtextRuntime.java', host)
        write(root, 'org/eclipse/emf/ecore/EPackage.java', '''
            package org.eclipse.emf.ecore; public final class EPackage { private final String ns; public EPackage(String ns){this.ns=ns;} public String getNsURI(){return ns;} }
        ''')
        write(root, 'org/eclipse/emf/ecore/EStructuralFeature.java', '''
            package org.eclipse.emf.ecore; public final class EStructuralFeature { private final String name; public EStructuralFeature(String name){this.name=name;} public String getName(){return name;} public boolean isDerived(){return false;} public boolean isTransient(){return false;} }
        ''')
        write(root, 'org/eclipse/emf/ecore/EClass.java', '''
            package org.eclipse.emf.ecore; import java.util.List; public final class EClass { private final EPackage pkg=new EPackage("urn:test"); private final List<EStructuralFeature> features=List.of(new EStructuralFeature("name")); public EPackage getEPackage(){return pkg;} public String getName(){return "Model";} public List<EStructuralFeature> getEAllStructuralFeatures(){return features;} }
        ''')
        write(root, 'org/eclipse/emf/ecore/EObject.java', '''
            package org.eclipse.emf.ecore; import org.eclipse.emf.ecore.resource.Resource; public interface EObject { EClass eClass(); Resource eResource(); boolean eIsSet(EStructuralFeature feature); Object eGet(EStructuralFeature feature,boolean resolve); EStructuralFeature eContainmentFeature(); }
        ''')
        write(root, 'org/eclipse/emf/ecore/resource/ResourceSet.java', 'package org.eclipse.emf.ecore.resource; public interface ResourceSet {}\n')
        write(root, 'org/eclipse/emf/ecore/resource/Resource.java', '''
            package org.eclipse.emf.ecore.resource; public interface Resource { Object getURI(); ResourceSet getResourceSet(); }
        ''')
        write(root, 'org/eclipse/emf/ecore/util/EcoreUtil.java', '''
            package org.eclipse.emf.ecore.util; import org.eclipse.emf.ecore.EObject; public final class EcoreUtil { public static EObject getRootContainer(EObject value){return value;} public static Object getURI(EObject value){return "memory:/"+Integer.toHexString(System.identityHashCode(value));} }
        ''')
        write(root, 'org/eclipse/emf/ecore/change/ChangeDescription.java', 'package org.eclipse.emf.ecore.change; public interface ChangeDescription { void apply(); }\n')
        write(root, 'org/eclipse/emf/ecore/change/util/ChangeRecorder.java', '''
            package org.eclipse.emf.ecore.change.util;
            import java.lang.reflect.Method;
            import org.eclipse.emf.ecore.EObject; import org.eclipse.emf.ecore.change.ChangeDescription; import org.eclipse.emf.ecore.resource.Resource; import org.eclipse.emf.ecore.resource.ResourceSet;
            public final class ChangeRecorder {
                private final Object target; private final String snapshot;
                public ChangeRecorder(EObject target){this.target=target;this.snapshot=snapshot(target);} public ChangeRecorder(Resource ignored){this.target=null;this.snapshot="";} public ChangeRecorder(ResourceSet ignored){this.target=null;this.snapshot="";}
                public ChangeDescription endRecording(){Object t=target;String s=snapshot;return ()->{if(t!=null)restore(t,s);};} public void dispose(){}
                private static String snapshot(Object target){try{return String.valueOf(target.getClass().getMethod("snapshot").invoke(target));}catch(Exception e){throw new RuntimeException(e);}}
                private static void restore(Object target,String value){try{Method method=target.getClass().getMethod("restoreSnapshot",String.class);method.invoke(target,value);}catch(Exception e){throw new RuntimeException(e);}}
            }
        ''')
        write(root, 'test/ModelObject.java', '''
            package test;
            import org.eclipse.emf.ecore.*; import org.eclipse.emf.ecore.resource.Resource;
            public final class ModelObject implements EObject { private final EClass type=new EClass(); private String name; public ModelObject(String name){this.name=name;} public String name(){return name;} public void name(String value){name=value;} public String snapshot(){return name;} public void restoreSnapshot(String value){name=value;} public EClass eClass(){return type;} public Resource eResource(){return null;} public boolean eIsSet(EStructuralFeature feature){return true;} public Object eGet(EStructuralFeature feature,boolean resolve){return name;} public EStructuralFeature eContainmentFeature(){return null;} }
        ''')
        write(root, 'test/TestXtextRuntime.java', '''
            package test;
            import java.util.*; import test.runtime.*;
            public final class TestXtextRuntime {
                private static boolean failGame=false, returnFalse=false;
                public static void main(String[] args){
                    ModelObject model=new ModelObject("base");
                    HydraXtextRuntime.install(new HydraXtextRuntime.Adapter(){
                        public List<HydraSemanticRuntime.Diagnostic> validate(String state,String trigger){return List.of(new HydraSemanticRuntime.Diagnostic("d","INFO",trigger,"",List.of()));}
                        public HydraSemanticRuntime.GamificationState updateGamification(HydraSemanticRuntime.GamificationState previous,List<HydraSemanticRuntime.Event> events,List<HydraSemanticRuntime.Event> flow){if(failGame)throw new IllegalStateException("game");return previous;}
                        public boolean applyQuickFix(org.eclipse.emf.ecore.EObject target,String diagnosticId,String actionId,Map<String,Object> parameters){((ModelObject)target).name("fixed");return !returnFalse;}
                        public Object invoke(org.eclipse.emf.ecore.EObject target,String operationId,Map<String,Object> parameters){((ModelObject)target).name("operated");return "ok";}
                    });
                    HydraXtextRuntime.synchronize(model,"BASE");
                    var stable=HydraXtextRuntime.checkpoint(model); HydraXtextRuntime.validateHost(model); if(!stable.equals(HydraXtextRuntime.checkpoint(model)))throw new AssertionError("draft mutated runtime");
                    failGame=true; try{HydraXtextRuntime.applyQuickFix(model,"d","fix",Map.of());throw new AssertionError("expected quick-fix failure");}catch(IllegalStateException expected){} if(!"base".equals(model.name())||!stable.equals(HydraXtextRuntime.checkpoint(model)))throw new AssertionError("quick-fix rollback");
                    try{HydraXtextRuntime.invoke(model,"op",Map.of());throw new AssertionError("expected operation failure");}catch(IllegalStateException expected){} if(!"base".equals(model.name())||!stable.equals(HydraXtextRuntime.checkpoint(model)))throw new AssertionError("operation rollback");
                    failGame=false;returnFalse=true;if(HydraXtextRuntime.applyQuickFix(model,"d","noop",Map.of()))throw new AssertionError("false fix");if(!"base".equals(model.name())||!stable.equals(HydraXtextRuntime.checkpoint(model)))throw new AssertionError("false quick-fix leaked mutation");
                    returnFalse=false;if(!HydraXtextRuntime.applyQuickFix(model,"d","fix",Map.of()))throw new AssertionError("fix not applied");if(!"fixed".equals(model.name())||HydraXtextRuntime.events(model).isEmpty())throw new AssertionError("successful commit");
                    HydraRuntimeActivation.setEnabled(false);model.name("hidden");HydraXtextRuntime.synchronize(model,"OFF");int history=HydraXtextRuntime.events(model).size();HydraRuntimeActivation.setEnabled(true);var result=HydraXtextRuntime.synchronize(model,"ON");if(!result.rebaselined()||result.changed()||HydraXtextRuntime.events(model).size()!=history)throw new AssertionError("reactivation replay");
                    System.out.println("HYDRA_XTEXT_RUNTIME_E2E=PASS events="+history);
                }
            }
        ''')
        sources = [str(path.relative_to(root)) for path in root.rglob('*.java')]
        output = run([javac, '-encoding', 'UTF-8', '-d', 'out', *sources], root)
        if output:
            print(output)
        output = run([java, '-cp', 'out', 'test.TestXtextRuntime'], root)
        require(output, 'HYDRA_XTEXT_RUNTIME_E2E=PASS', 'Xtext EObject + runtime rollback execution')


def langium_runtime_test(support: str, langium: str) -> None:
    tsc = resolve_tsc(); node = shutil.which('node')
    if not tsc or not node:
        raise AssertionError('local frontend TypeScript compiler (or tsc fallback) and node are required for the Langium lifecycle gate')
    semantic = java_text_block(support, 'public static String typescriptSemanticRuntimeSource()')
    host = java_text_block(langium, 'private String languageRuntimeSource()')
    with tempfile.TemporaryDirectory(prefix='hydra-langium-runtime-') as tmp:
        root = Path(tmp)
        write(root, 'package.json', '{"type":"module"}\n')
        write(root, 'hydra-runtime-activation.ts', '''
            let enabled=false;let activationEpoch=0;
            export const hydraRuntimeActivation={isEnabled:()=>enabled,epoch:()=>activationEpoch,setEnabled:(value:boolean)=>{if(enabled!==value){enabled=value;activationEpoch++;}return enabled;}} as const;
        ''')
        write(root, 'hydra-semantic-runtime.ts', semantic)
        write(root, 'hydra-language-runtime.ts', host)
        write(root, 'test.ts', '''
            import {hydraRuntimeActivation} from './hydra-runtime-activation.js';
            import {hydraLanguageRuntime,type HydraLanguageRuntimeAdapter} from './hydra-language-runtime.js';
            let failGame=false;
            const adapter:HydraLanguageRuntimeAdapter={
              validate:(_state,trigger)=>[{id:'d',severity:'INFO',message:trigger,elementId:'',quickFixIds:[]}],
              deriveEvents:(before,after,trigger,sequence)=>before===after?[]:[{sequence,type:'MODEL_CHANGED',elementId:'document',payload:{trigger}}],
              runEventFlow:(events)=>events.length?[{sequence:events[0].sequence,type:'FLOW',elementId:'document',payload:{}}]:[],
              updateGamification:(previous,events)=>{if(failGame)throw new Error('game');return{points:{xp:(previous.points.xp??0)+events.length},level:'',achievements:[],penalties:0};},
              applyQuickFix:async state=>state+'|fixed',
              invoke:async state=>({state:state+'|op',result:'ok'})
            };
            hydraLanguageRuntime.install(adapter);
            let result=hydraLanguageRuntime.commit('file:///a','a0','OFF');if(!result.rebaselined||result.changed)throw new Error('disabled baseline');
            hydraRuntimeActivation.setEnabled(true);result=hydraLanguageRuntime.commit('file:///a','a1','ON');if(!result.rebaselined||result.changed)throw new Error('enable baseline');
            const stable=JSON.stringify(hydraLanguageRuntime.checkpoint('file:///a'));hydraLanguageRuntime.validate('file:///a','draft','DRAFT');if(JSON.stringify(hydraLanguageRuntime.checkpoint('file:///a'))!==stable)throw new Error('draft side effect');
            result=hydraLanguageRuntime.commit('file:///a','a2','SAVE');if(!result.changed||result.confirmedEvents.length!==1)throw new Error('save commit');
            const beforeFailure=JSON.stringify(hydraLanguageRuntime.checkpoint('file:///a'));failGame=true;try{await hydraLanguageRuntime.applyQuickFix('file:///a','a2','d','fix',{});throw new Error('expected failure');}catch(error){if((error as Error).message==='expected failure')throw error;}if(JSON.stringify(hydraLanguageRuntime.checkpoint('file:///a'))!==beforeFailure)throw new Error('quickfix rollback');failGame=false;
            const op=await hydraLanguageRuntime.invoke('file:///a','a2','op',{});if(op.state!=='a2|op'||op.result!=='ok'||!op.commit.changed)throw new Error('operation state');
            hydraLanguageRuntime.commit('file:///b','b0','B');const aSeq=hydraLanguageRuntime.checkpoint('file:///a').sequence;const bSeq=hydraLanguageRuntime.checkpoint('file:///b').sequence;if(aSeq===bSeq&&aSeq!==0)throw new Error('sessions unexpectedly coupled');
            hydraRuntimeActivation.setEnabled(false);hydraLanguageRuntime.commit('file:///a','hidden','OFF_CHANGE');const history=hydraLanguageRuntime.events('file:///a').length;hydraRuntimeActivation.setEnabled(true);result=hydraLanguageRuntime.commit('file:///a','reactivated','ON');if(!result.rebaselined||result.changed||hydraLanguageRuntime.events('file:///a').length!==history)throw new Error('reactivation replay');
            hydraLanguageRuntime.release('file:///b');
            console.log(`HYDRA_LANGIUM_RUNTIME_E2E=PASS seq=${hydraLanguageRuntime.checkpoint('file:///a').sequence} events=${history}`);
        ''')
        write(root, 'tsconfig.json', '''
            {"compilerOptions":{"target":"ES2022","module":"NodeNext","moduleResolution":"NodeNext","strict":true,"skipLibCheck":true,"outDir":"out"},"include":["*.ts"]}
        ''')
        run([*tsc, '-p', 'tsconfig.json'], root)
        output = run([node, 'out/test.js'], root)
        require(output, 'HYDRA_LANGIUM_RUNTIME_E2E=PASS', 'Langium per-document lifecycle execution')


def main() -> None:
    support = SUPPORT.read_text(encoding='utf-8')
    xtext = XTEXT.read_text(encoding='utf-8')
    langium = LANGIUM.read_text(encoding='utf-8')
    require(xtext, 'ChangeRecorder', 'Xtext EMF change recorder')
    require(xtext, 'rollbackHost(changes,failure)', 'Xtext host rollback on failure')
    require(xtext, 'restoreLifecycle(lifecycle,before,failure)', 'Xtext lifecycle rollback on failure')
    require(xtext, 'HydraXtextRuntime.applyQuickFix', 'Xtext executable quick fix')
    require(xtext, 'void didSave(DidSaveTextDocumentParams params)', 'Xtext didSave commit boundary')
    require(xtext, 'XTEXT_DID_SAVE', 'Xtext save trigger')
    require(xtext, 'HydraXtextRuntime.validateHost', 'Xtext side-effect-free validation')
    require(xtext, 'org.eclipse.emf:org.eclipse.emf.ecore.change', 'Xtext declared EMF change dependency')
    require(langium, 'override didChangeContent', 'Langium draft boundary')
    require(langium, 'didSaveDocument', 'Langium save callback')
    require(langium, 'LANGIUM_DID_SAVE', 'Langium save trigger')
    require(langium, 'didCloseDocument', 'Langium session release')
    require(langium, 'new Map<string,Session>()', 'Langium per-URI sessions')
    require(langium, 'outcome.state', 'Langium operations return host state')
    require(langium, 'runtime.restore(before)', 'Langium lifecycle rollback')
    xtext_runtime_test(support, xtext)
    langium_runtime_test(support, langium)
    print('LANGUAGE_HOST_RUNTIME_LIFECYCLE=PASS')


if __name__ == '__main__':
    main()
