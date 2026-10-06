from __future__ import annotations
from pathlib import Path
import shutil, subprocess, tempfile, textwrap, sys

ROOT=Path(__file__).resolve().parents[2]
GLSP=ROOT/'src/main/java/com/example/editor/integration/adapter/glsp/EclipseGlspTechnologyAdapter.java'
ECLIPSE=ROOT/'src/main/java/com/example/editor/integration/adapter/eclipse/EclipseModelingDistributionTechnologyAdapter.java'
SUPPORT=ROOT/'src/main/java/com/example/editor/integration/compiler/TechnologyAdapterRuntimeLifecycleSupport.java'
GEN=ROOT/'src/main/java/com/example/editor/integration/compiler/TechnologyAdapterGenerationSupport.java'

def require(text:str, needle:str, label:str):
    if needle not in text: raise AssertionError(f'MISSING {label}: {needle}')
    print('PASS',label)

def reject(text:str, needle:str, label:str):
    if needle in text: raise AssertionError(f'FORBIDDEN {label}: {needle}')
    print('PASS',label)

glsp=GLSP.read_text(); eclipse=ECLIPSE.read_text(); support=SUPPORT.read_text(); gen=GEN.read_text()
require(gen,'AtomicLong EPOCH','java activation epoch')
require(gen,'epoch:()=>epoch','typescript activation epoch')
require(support,'validateHost','side-effect-free validation entry')
require(support,'checkpointBaseline','pre-commit checkpoint')
require(support,'runEventFlow','eventflow ordering')
require(support,'updateGamification','gamification ordering')
require(support,'GamificationState nextGamification','java atomic side-effect staging')
require(support,'const nextGamification','typescript atomic side-effect staging')
require(support,'Math.max(16,historyLimit)','bounded history parity')
require(glsp,'extends DiagramModule','GLSP 2.5 DiagramModule')
reject(glsp,'DefaultDiagramModule','obsolete GLSP DefaultDiagramModule absent')
require(glsp,'implements SourceModelStorage','concrete GLSP source storage')
require(glsp,'implements GModelFactory','concrete GLSP GModelFactory')
require(glsp,'extends DefaultGModelState','GLSP transactional GModelState')
require(glsp,'implements ModelValidator','GLSP validator bridge')
require(glsp,'GLSP_OPERATION','GLSP confirmed operation trigger')
require(glsp,'GLSP_UNDO','GLSP undo lifecycle')
require(glsp,'GLSP_REDO','GLSP redo lifecycle')
require(glsp,'commandStack.getUndoCommand()==command','GLSP Java execute rollback')
require(glsp,'runtime.restore(rollbackRuntime)','GLSP Node execute rollback')
require(glsp,'runtime.restore(currentRuntime)','GLSP Node undo redo rollback')
require(glsp,'HydraSemanticRuntimeService','GLSP per-session semantic service')
require(glsp,'hydra-semantic-runtime.ts','Node semantic runtime')
require(glsp,'@eclipse-glsp/server@','GLSP Node published npm package')
reject(glsp,'@eclipse-glsp/server-node@','obsolete GLSP Node package absent')
require(glsp,'inversify@6.1.3','GLSP 2.5 Inversify peer compatibility')
require(glsp,'bindSourceModelStorage():BindingTarget<SourceModelStorage>','Node SourceModelStorage binding')
require(glsp,'bindModelState():BindingTarget<ModelState>','Node ModelState binding')
require(glsp,'bindDiagramConfiguration():BindingTarget<DiagramConfiguration>','Node DiagramConfiguration binding')
require(glsp,'bindGModelFactory():BindingTarget<GModelFactory>','Node GModelFactory binding')
require(glsp,'configureOperationHandlers','Node OperationHandler registration')
require(glsp,'extends OperationHandler','Node real GLSP OperationHandler')
require(glsp,'createCommand(operation:Operation):Command|undefined','Node reversible command creation')
require(glsp,"GGraph.builder().id('hydra-root')",'Node real GModel projection')
require(glsp,'class HydraSemanticRuntimeService','Node per-session runtime service class')
reject(glsp,'export const hydraSemanticRuntimeService','Node global runtime singleton absent')
require(glsp,'GLSP_NODE_OPERATION','Node confirmed operation trigger')
require(eclipse,'SEMANTIC_SCOPE_ID','Eclipse neutral semantic scope')
require(eclipse,'EPOCH_KEY','Eclipse persistent activation epoch')
require(eclipse,'public final class HydraPortableRuntimeBridge','executable Eclipse bridge')
reject(eclipse,'public interface HydraPortableRuntimeBridge','passive Eclipse bridge absent')
require(eclipse,'HydraEclipseCommandLifecycle','EMF command lifecycle')
require(eclipse,'restoreOrRebaseline','epoch-aware EMF undo redo')
require(eclipse,'ECLIPSE_QUICK_FIX','transactional quick fix')
require(eclipse,'stack.getUndoCommand()==command','Eclipse execute rollback')
require(eclipse,'bridge.restoreRuntime(current)','Eclipse undo redo rollback')

javac=shutil.which('javac'); java=shutil.which('java')
if not javac or not java: raise SystemExit('javac/java are required')
with tempfile.TemporaryDirectory(prefix='hydra-runtime-harness-') as td:
    td=Path(td); classes=td/'classes'; classes.mkdir()
    subprocess.run([javac,'-d',str(classes),str(SUPPORT)],check=True,cwd=ROOT)
    generator=td/'Generate.java'
    generator.write_text(textwrap.dedent('''
        import java.nio.file.*;
        import com.example.editor.integration.compiler.TechnologyAdapterRuntimeLifecycleSupport;
        public class Generate {
          public static void main(String[] args) throws Exception {
            Path dir=Path.of(args[0],"probe");Files.createDirectories(dir);
            Files.writeString(dir.resolve("HydraSemanticRuntime.java"),TechnologyAdapterRuntimeLifecycleSupport.javaSemanticRuntimeSource("probe"));
          }
        }
    '''))
    subprocess.run([javac,'-cp',str(classes),'-d',str(classes),str(generator)],check=True)
    src=td/'src'; subprocess.run([java,'-cp',str(classes),'Generate',str(src)],check=True)
    pkg=src/'probe'
    (pkg/'HydraRuntimeActivation.java').write_text(textwrap.dedent('''
        package probe;
        public final class HydraRuntimeActivation {
          private static boolean enabled; private static long epoch;
          public static boolean isEnabled(){return enabled;}
          public static long epoch(){return epoch;}
          public static boolean setEnabled(boolean value){if(enabled!=value){enabled=value;epoch++;}return enabled;}
        }
    '''))
    (pkg/'LifecycleHarness.java').write_text(textwrap.dedent('''
        package probe;
        import java.util.*;
        public final class LifecycleHarness {
          static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
          static final class Engine implements HydraSemanticRuntime.Engine {
            boolean failGamification; boolean failValidation;
            public List<HydraSemanticRuntime.Diagnostic> validate(String state,String trigger){if(failValidation)throw new IllegalStateException("validation-injected");return List.of(new HydraSemanticRuntime.Diagnostic("d-"+state,"INFO","ok","model",List.of()));}
            public List<HydraSemanticRuntime.Event> deriveEvents(String before,String after,String trigger,long sequence){return List.of(new HydraSemanticRuntime.Event(sequence,"MODEL_CHANGED","model",Map.of("from",before,"to",after,"trigger",trigger)));}
            public List<HydraSemanticRuntime.Event> runEventFlow(List<HydraSemanticRuntime.Event> confirmed,String state){return confirmed.isEmpty()?List.of():List.of(new HydraSemanticRuntime.Event(confirmed.getFirst().sequence(),"FLOW","model",Map.of("state",state)));}
            public HydraSemanticRuntime.GamificationState updateGamification(HydraSemanticRuntime.GamificationState previous,List<HydraSemanticRuntime.Event> confirmed,List<HydraSemanticRuntime.Event> flow){if(failGamification)throw new IllegalStateException("injected");long p=previous.points().getOrDefault("xp",0L)+confirmed.size()+flow.size();return new HydraSemanticRuntime.GamificationState(Map.of("xp",p),"L1",List.of(),0);}
          }
          public static void main(String[] args){
            Engine engine=new Engine();HydraSemanticRuntime rt=new HydraSemanticRuntime(engine,1);
            HydraRuntimeActivation.setEnabled(true);
            Engine rebaselineFailureEngine=new Engine();rebaselineFailureEngine.failValidation=true;HydraSemanticRuntime rebaselineFailure=new HydraSemanticRuntime(rebaselineFailureEngine,16);var beforeFailedRebaseline=rebaselineFailure.checkpoint();boolean rebaselineFailed=false;try{rebaselineFailure.synchronize("X","LOAD");}catch(IllegalStateException expected){rebaselineFailed=true;}check(rebaselineFailed&&rebaselineFailure.checkpoint().equals(beforeFailedRebaseline),"failed rebaseline is atomic");
            var a=rt.synchronize("A","LOAD");check(a.rebaselined()&&!a.changed()&&rt.sequence()==0,"initial rebaseline");
            var validation=rt.validateHost("B","VALIDATE");check(validation.size()==1,"validation result");check(rt.baseline().equals("A")&&rt.events().isEmpty()&&rt.sequence()==0,"validation side effects");
            var b=rt.synchronize("B","COMMIT");check(b.changed()&&!b.rebaselined(),"commit changed");check(b.confirmedEvents().size()==1&&b.flowEvents().size()==1,"event ordering outputs");check(rt.sequence()==1&&rt.events().size()==2&&rt.gamification().points().get("xp")==2,"observable commit state");
            var beforeB=rt.checkpointBaseline();var afterB=rt.checkpoint();check(beforeB.semanticState().equals("A")&&beforeB.sequence()==0,"baseline checkpoint");
            engine.failGamification=true;boolean failed=false;try{rt.synchronize("C","FAIL_COMMIT");}catch(IllegalStateException expected){failed=true;}engine.failGamification=false;check(failed,"injected failure");check(rt.checkpoint().equals(afterB),"failed commit is atomic");
            rt.restore(beforeB);check(rt.baseline().equals("A")&&rt.events().isEmpty()&&rt.sequence()==0,"restore before");
            rt.restore(afterB);check(rt.baseline().equals("B")&&rt.events().size()==2&&rt.sequence()==1,"restore after");
            HydraRuntimeActivation.setEnabled(false);var off=rt.synchronize("C","OFF_EDIT");check(off.rebaselined()&&!off.changed()&&off.diagnostics().isEmpty(),"disabled silent baseline");long seq=rt.sequence();int events=rt.events().size();long xp=rt.gamification().points().get("xp");
            HydraRuntimeActivation.setEnabled(true);var on=rt.synchronize("C","REENABLE");check(on.rebaselined()&&!on.changed(),"reenable silent baseline");check(rt.sequence()==seq&&rt.events().size()==events&&rt.gamification().points().get("xp")==xp,"no replay after off");
            var d=rt.synchronize("D","COMMIT");check(d.changed()&&rt.sequence()==seq+1,"post-reactivation commit");
            for(int i=0;i<30;i++)rt.synchronize("S"+i,"STRESS");check(rt.events().size()<=16,"bounded history");
            System.out.println("HYDRA_GLSP_ECLIPSE_RUNTIME_LIFECYCLE=PASS seq="+rt.sequence()+" history="+rt.events().size());
          }
        }
    '''))
    generated_classes=td/'generated-classes'; generated_classes.mkdir()
    subprocess.run([javac,'-d',str(generated_classes),str(pkg/'HydraRuntimeActivation.java'),str(pkg/'HydraSemanticRuntime.java'),str(pkg/'LifecycleHarness.java')],check=True)
    out=subprocess.check_output([java,'-cp',str(generated_classes),'probe.LifecycleHarness'],text=True).strip()
    print(out)
print('GLSP_ECLIPSE_RUNTIME_STATIC=PASS')
