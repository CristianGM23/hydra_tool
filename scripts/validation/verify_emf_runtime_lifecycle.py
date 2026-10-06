#!/usr/bin/env python3
from pathlib import Path
import re, subprocess, tempfile, textwrap, sys
ROOT=Path(__file__).resolve().parents[2]
adapter=(ROOT/'src/main/java/com/example/editor/integration/adapter/emf/EmfTechnologyAdapter.java').read_text()
support=(ROOT/'src/main/java/com/example/editor/integration/compiler/TechnologyAdapterRuntimeLifecycleSupport.java').read_text()
checks={
'common semantic runtime generated':'HydraSemanticRuntime.java' in adapter and 'javaSemanticRuntimeSource(packageName)' in adapter,
'concrete emf runtime generated':'HydraEmfRuntime.java' in adapter and 'private String emfRuntimeSource' in adapter,
'raw notifications are not commit source':'extends AdapterImpl' not in adapter and 'Raw EMF notifications are not treated as commits' in adapter,
'change command quick fixes':'extends ChangeCommand' in adapter and 'AbortExecutionException' in adapter,
'change command operations':'new ChangeCommand(target)' in adapter,
'change recorder compensation':'new ChangeRecorder(EcoreUtil.getRootContainer(root))' in adapter and 'fallback.apply()' in adapter,
'command lifecycle undo redo':'public void undo(EObject root)' in adapter and 'public void redo(EObject root)' in adapter,
'epoch guarded restore':'snapshot.activationEpoch()==HydraRuntimeActivation.epoch()' in adapter,
'current emf versions':all(x in adapter for x in ['2.46.0','2.43.0','2.41.0','2.18.0','2.24.0']),
'java 21 generated module':'<maven.compiler.release>21</maven.compiler.release>' in adapter,
}
for name,ok in checks.items(): print(('PASS' if ok else 'FAIL')+' - '+name)
if not all(checks.values()): sys.exit(1)

def extract(src, signature):
    i=src.index(signature)
    a=src.index('"""',i)+3
    b=src.index('""".formatted(packageName)',a)
    return src[a:b].strip('\n').replace('%s','generated',1)
semantic=extract(support,'public static String javaSemanticRuntimeSource(String packageName)')
emf=extract(adapter,'private String emfRuntimeSource(String packageName)')
editing=extract(adapter,'private String editingDomainSource(String packageName)')
validation=extract(adapter,'private String validationSource(String packageName)')
quickfix=extract(adapter,'private String quickFixSource(String packageName)')
operation=extract(adapter,'private String operationSource(String packageName)')
event=extract(adapter,'private String eventSource(String packageName)')
with tempfile.TemporaryDirectory(prefix='hydra-emf-life-') as td:
    d=Path(td)
    def w(rel,txt):
        p=d/rel;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(textwrap.dedent(txt))
    w('generated/HydraSemanticRuntime.java',semantic)
    w('generated/HydraEmfRuntime.java',emf)
    w('generated/HydraEmfEditingDomain.java',editing)
    w('generated/HydraEmfValidationAdapter.java',validation)
    w('generated/HydraEmfQuickFixCommand.java',quickfix)
    w('generated/HydraEmfOperationAdapter.java',operation)
    w('generated/HydraEmfEventAdapter.java',event)
    w('generated/HydraRuntimeActivation.java','''
        package generated;
        public final class HydraRuntimeActivation { private static boolean enabled=true; private static long epoch=0;
          public static boolean isEnabled(){return enabled;} public static long epoch(){return epoch;}
          public static boolean setEnabled(boolean value){if(enabled!=value){enabled=value;epoch++;}return enabled;}
        }
    ''')
    w('org/eclipse/emf/common/notify/Notifier.java','package org.eclipse.emf.common.notify; public interface Notifier {}')
    w('org/eclipse/emf/common/command/Command.java','''
        package org.eclipse.emf.common.command; public interface Command {void execute();void undo();void redo();default boolean canExecute(){return true;}}
    ''')
    w('org/eclipse/emf/common/command/AbortExecutionException.java','package org.eclipse.emf.common.command; public class AbortExecutionException extends RuntimeException {public AbortExecutionException(){}}')
    w('org/eclipse/emf/common/command/CommandStack.java','''
        package org.eclipse.emf.common.command; public interface CommandStack {void execute(Command c);boolean canUndo();Command getUndoCommand();void undo();boolean canRedo();Command getRedoCommand();void redo();Command getMostRecentCommand();}
    ''')
    w('org/eclipse/emf/ecore/EPackage.java','package org.eclipse.emf.ecore; public interface EPackage {String getNsURI();}')
    w('org/eclipse/emf/ecore/EStructuralFeature.java','''
        package org.eclipse.emf.ecore; public interface EStructuralFeature {String getName();default boolean isDerived(){return false;}default boolean isTransient(){return false;}}
    ''')
    w('org/eclipse/emf/ecore/EClass.java','''
        package org.eclipse.emf.ecore; import java.util.List; public interface EClass {EPackage getEPackage();String getName();List<EStructuralFeature> getEAllStructuralFeatures();}
    ''')
    w('org/eclipse/emf/ecore/EObject.java','''
        package org.eclipse.emf.ecore; import org.eclipse.emf.common.notify.Notifier; import org.eclipse.emf.ecore.resource.Resource;
        public interface EObject extends Notifier {EClass eClass();boolean eIsSet(EStructuralFeature f);Object eGet(EStructuralFeature f,boolean resolve);EStructuralFeature eContainmentFeature();Resource eResource();}
    ''')
    w('org/eclipse/emf/ecore/resource/Resource.java','package org.eclipse.emf.ecore.resource; import org.eclipse.emf.common.notify.Notifier; public interface Resource extends Notifier {}')
    w('org/eclipse/emf/ecore/util/EcoreUtil.java','''
        package org.eclipse.emf.ecore.util; import org.eclipse.emf.ecore.EObject; public final class EcoreUtil {public static EObject getRootContainer(EObject o){return o;}public static String getURI(EObject o){return "mock:/"+o.hashCode();}}
    ''')
    w('org/eclipse/emf/ecore/change/ChangeDescription.java','package org.eclipse.emf.ecore.change; public interface ChangeDescription {void apply();}')
    w('org/eclipse/emf/ecore/change/util/ChangeRecorder.java','''
        package org.eclipse.emf.ecore.change.util; import org.eclipse.emf.common.notify.Notifier; import org.eclipse.emf.ecore.change.ChangeDescription;
        public class ChangeRecorder {private final Notifier target;private final String before;public ChangeRecorder(Notifier n){target=n;before=read(n);}private static String read(Object n){try{var f=n.getClass().getDeclaredField("name");f.setAccessible(true);return String.valueOf(f.get(n));}catch(Exception e){return null;}}private static void write(Object n,String v){if(v==null)return;try{var f=n.getClass().getDeclaredField("name");f.setAccessible(true);f.set(n,v);}catch(Exception e){throw new RuntimeException(e);}}public ChangeDescription endRecording(){return ()->write(target,before);} public void dispose(){}}
    ''')
    w('org/eclipse/emf/edit/command/ChangeCommand.java','''
        package org.eclipse.emf.edit.command; import org.eclipse.emf.common.command.Command; import org.eclipse.emf.common.notify.Notifier;
        public abstract class ChangeCommand implements Command {protected final Notifier notifier;private String label="";public ChangeCommand(Notifier n){notifier=n;}protected boolean prepare(){return true;}protected abstract void doExecute();protected void setLabel(String v){label=v;}public void execute(){if(prepare())doExecute();}public void undo(){}public void redo(){doExecute();}}
    ''')
    w('org/eclipse/emf/edit/domain/EditingDomain.java','package org.eclipse.emf.edit.domain; import org.eclipse.emf.common.command.CommandStack; public interface EditingDomain {CommandStack getCommandStack();}')
    w('generated/Harness.java','''
        package generated;
        import java.util.*; import org.eclipse.emf.ecore.*; import org.eclipse.emf.ecore.resource.Resource; import org.eclipse.emf.common.command.*; import org.eclipse.emf.edit.domain.EditingDomain;
        public final class Harness {
          static final class F implements EStructuralFeature {public String getName(){return "name";}}
          static final F FNAME=new F();
          static final class Obj implements EObject {String name="a"; public EClass eClass(){return new EClass(){public EPackage getEPackage(){return ()->"urn:test";}public String getName(){return "Root";}public List<EStructuralFeature> getEAllStructuralFeatures(){return List.of(FNAME);}};}public boolean eIsSet(EStructuralFeature f){return true;}public Object eGet(EStructuralFeature f,boolean r){return name;}public EStructuralFeature eContainmentFeature(){return null;}public Resource eResource(){return null;}}
          static final class Mut implements Command {final Obj o;final String next;String old;Mut(Obj o,String n){this.o=o;next=n;}public void execute(){old=o.name;o.name=next;}public void undo(){o.name=old;}public void redo(){o.name=next;}}
          static final class Bad implements Command {final Obj o;String old;Bad(Obj o){this.o=o;}public void execute(){old=o.name;o.name="bad";throw new IllegalStateException("command failed");}public void undo(){o.name=old;}public void redo(){execute();}}
          static final class AbortMut implements Command {final Obj o;AbortMut(Obj o){this.o=o;}public void execute(){o.name="aborted";throw new org.eclipse.emf.common.command.AbortExecutionException();}public void undo(){}public void redo(){execute();}}
          static final class Stack implements CommandStack {final List<Command> cmds=new ArrayList<>();int pos=-1;Command recent;public void execute(Command c){while(cmds.size()>pos+1)cmds.remove(cmds.size()-1);try{c.execute();}catch(org.eclipse.emf.common.command.AbortExecutionException aborted){return;}cmds.add(c);pos++;recent=c;}public boolean canUndo(){return pos>=0;}public Command getUndoCommand(){return canUndo()?cmds.get(pos):null;}public void undo(){Command c=cmds.get(pos--);c.undo();recent=c;}public boolean canRedo(){return pos+1<cmds.size();}public Command getRedoCommand(){return canRedo()?cmds.get(pos+1):null;}public void redo(){Command c=cmds.get(++pos);c.redo();recent=c;}public Command getMostRecentCommand(){return recent;}}
          static final class Domain implements EditingDomain {final Stack s=new Stack();public CommandStack getCommandStack(){return s;}}
          static long points(HydraSemanticRuntime.GamificationState g){return g.points().getOrDefault("xp",0L);}
          public static void main(String[] a){
            Obj root=new Obj();Domain d=new Domain();HydraEmfEditingDomain life=new HydraEmfEditingDomain(d);
            HydraEmfRuntime.install(new HydraEmfRuntime.Adapter(){public List<HydraSemanticRuntime.Event> deriveEvents(String b,String n,String t,long s){return Objects.equals(b,n)?List.of():List.of(new HydraSemanticRuntime.Event(s,"MODEL_CHANGED","root",Map.of("trigger",t)));}public HydraSemanticRuntime.GamificationState updateGamification(HydraSemanticRuntime.GamificationState p,List<HydraSemanticRuntime.Event> e,List<HydraSemanticRuntime.Event> f){return new HydraSemanticRuntime.GamificationState(Map.of("xp",points(p)+e.size()),"",List.of(),0);}});
            life.baseline(root); if(!HydraEmfRuntime.events(root).isEmpty())throw new AssertionError("baseline emitted");
            life.execute(new Mut(root,"b"),root); if(HydraEmfRuntime.events(root).size()!=1||points(HydraEmfRuntime.gamification(root))!=1)throw new AssertionError("commit");
            life.undo(root); if(!root.name.equals("a")||!HydraEmfRuntime.events(root).isEmpty()||points(HydraEmfRuntime.gamification(root))!=0)throw new AssertionError("undo");
            life.redo(root); if(!root.name.equals("b")||HydraEmfRuntime.events(root).size()!=1||points(HydraEmfRuntime.gamification(root))!=1)throw new AssertionError("redo");
            HydraEmfRuntime.install(new HydraEmfRuntime.Adapter(){public List<HydraSemanticRuntime.Event> deriveEvents(String b,String n,String t,long s){return List.of(new HydraSemanticRuntime.Event(s,"MODEL_CHANGED","root",Map.of()));}public HydraSemanticRuntime.GamificationState updateGamification(HydraSemanticRuntime.GamificationState p,List<HydraSemanticRuntime.Event> e,List<HydraSemanticRuntime.Event> f){throw new IllegalStateException("boom");}});life.baseline(root);String before=root.name;var snap=HydraEmfRuntime.checkpoint(root);try{life.execute(new Mut(root,"c"),root);throw new AssertionError("expected failure");}catch(IllegalStateException ok){}if(!root.name.equals(before)||!HydraEmfRuntime.checkpoint(root).equals(snap))throw new AssertionError("rollback");
            String stable=root.name;var stableSnap=HydraEmfRuntime.checkpoint(root);try{life.execute(new Bad(root),root);throw new AssertionError("expected command failure");}catch(IllegalStateException ok){}if(!root.name.equals(stable)||!HydraEmfRuntime.checkpoint(root).equals(stableSnap))throw new AssertionError("execute compensation");
            life.execute(new AbortMut(root),root);if(!root.name.equals(stable)||!HydraEmfRuntime.checkpoint(root).equals(stableSnap))throw new AssertionError("abort compensation");
            HydraRuntimeActivation.setEnabled(false);life.execute(new Mut(root,"off"),root);HydraRuntimeActivation.setEnabled(true);life.baseline(root);if(!HydraEmfRuntime.events(root).isEmpty())throw new AssertionError("off replay");
            System.out.println("HYDRA_EMF_RUNTIME_LIFECYCLE=PASS");
          }
        }
    ''')
    files=[str(p) for p in d.rglob('*.java')]
    cp=subprocess.run(['javac','-d',str(d/'out'),*files],capture_output=True,text=True)
    if cp.returncode: print(cp.stdout);print(cp.stderr);sys.exit(cp.returncode)
    run=subprocess.run(['java','-cp',str(d/'out'),'generated.Harness'],capture_output=True,text=True)
    print(run.stdout.strip())
    if run.returncode: print(run.stderr);sys.exit(run.returncode)
print('EMF_RUNTIME_LIFECYCLE=PASS')
