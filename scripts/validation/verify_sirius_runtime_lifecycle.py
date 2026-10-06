#!/usr/bin/env python3
from __future__ import annotations
import shutil, subprocess, tempfile, textwrap
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
SUPPORT=ROOT/'src/main/java/com/example/editor/integration/compiler/TechnologyAdapterRuntimeLifecycleSupport.java'
SIRIUS=ROOT/'src/main/java/com/example/editor/integration/adapter/sirius/SiriusDesktopTechnologyAdapter.java'

def require(text,needle,label):
    if needle not in text: raise AssertionError(f'{label}: missing {needle!r}')
    print(f'PASS - {label}')

def block(source,method):
    start=source.index(method); ret=source.index('return """',start); begin=source.index('\n',ret)+1; end=source.index('"""',begin)
    return textwrap.dedent(source[begin:end]).rstrip()+'\n'

def write(root,rel,content):
    path=root/rel;path.parent.mkdir(parents=True,exist_ok=True);path.write_text(textwrap.dedent(content),encoding='utf-8')

def run(cmd,cwd):
    p=subprocess.run(cmd,cwd=cwd,text=True,stdout=subprocess.PIPE,stderr=subprocess.STDOUT)
    if p.returncode: raise AssertionError(f"command failed ({' '.join(cmd)}):\n{p.stdout}")
    return p.stdout.strip()

def main():
    source=SIRIUS.read_text(encoding='utf-8'); support=SUPPORT.read_text(encoding='utf-8')
    for needle,label in [
      ('HydraSiriusTransactionLifecycle.java','Sirius transaction lifecycle generated'),('extends ResourceSetListenerImpl','EMF transaction listener'),('transactionAboutToCommit','pre-commit trigger'),('new HydraCommitCommand(root)','reversible HYDRA trigger command'),('SIRIUS_TRANSACTION_COMMIT','confirmed Sirius commit trigger'),('restoreOrRebaseline','epoch-aware undo redo'),('service:stableLabel','VSM attaches runtime through Java service'),('ownedJavaExtensions','VSM Java extension registration'),('RecordingCommand(domain,"HYDRA quick fix")','transactional Sirius quick fix'),('RecordingCommand(domain,"HYDRA operation")','transactional Sirius operation'),('org.eclipse.emf.transaction:org.eclipse.emf.transaction','declared EMF Transaction dependency'),('org.eclipse.emf.ecore.change','declared EMF change bundle')]: require(source,needle,label)
    for old,label in [('public interface HydraSiriusValidationService','passive validation interface absent'),('public interface HydraSiriusQuickFixService','passive quickfix interface absent'),('public interface HydraSiriusOperationService','passive operation interface absent'),('public interface HydraSiriusEventBridge','passive event interface absent')]:
        if old in source: raise AssertionError(label)
        print('PASS - '+label)
    javac=shutil.which('javac');java=shutil.which('java')
    if not javac or not java: raise AssertionError('javac/java required')
    semantic=block(support,'public static String javaSemanticRuntimeSource').replace('%s','probe')
    runtime=block(source,'private String siriusRuntimeSource').replace('%s','probe')
    lifecycle=block(source,'private String transactionLifecycleSource').replace('%s','probe')
    with tempfile.TemporaryDirectory(prefix='hydra-sirius-runtime-') as tmp:
      root=Path(tmp)
      write(root,'probe/HydraRuntimeActivation.java','''package probe; public final class HydraRuntimeActivation {private static boolean enabled=true;private static long epoch=1;public static boolean isEnabled(){return enabled;}public static long epoch(){return epoch;}public static void setEnabled(boolean v){if(v!=enabled){enabled=v;epoch++;}}}''')
      write(root,'probe/HydraSemanticRuntime.java',semantic);write(root,'probe/HydraSiriusRuntime.java',runtime);write(root,'probe/HydraSiriusTransactionLifecycle.java',lifecycle)
      write(root,'org/eclipse/emf/common/command/Command.java','''package org.eclipse.emf.common.command; public interface Command {void execute();void undo();void redo();default boolean canExecute(){return true;}default boolean canUndo(){return true;}}''')
      write(root,'org/eclipse/emf/common/command/AbstractCommand.java','''package org.eclipse.emf.common.command; public abstract class AbstractCommand implements Command {protected AbstractCommand(String label){} protected abstract boolean prepare();public boolean canExecute(){return prepare();}public boolean canUndo(){return true;}}''')
      write(root,'org/eclipse/emf/common/command/CommandStack.java','''package org.eclipse.emf.common.command; public interface CommandStack {void execute(Command command);}''')
      write(root,'org/eclipse/emf/common/notify/Notification.java','''package org.eclipse.emf.common.notify; public interface Notification {Object getNotifier();}''')
      write(root,'org/eclipse/emf/ecore/EPackage.java','''package org.eclipse.emf.ecore; public final class EPackage {public String getNsURI(){return "urn:test";}}''')
      write(root,'org/eclipse/emf/ecore/EStructuralFeature.java','''package org.eclipse.emf.ecore; public final class EStructuralFeature {public String getName(){return "name";}public boolean isDerived(){return false;}public boolean isTransient(){return false;}}''')
      write(root,'org/eclipse/emf/ecore/EClass.java','''package org.eclipse.emf.ecore; import java.util.*; public final class EClass {public EPackage getEPackage(){return new EPackage();}public String getName(){return "Model";}public List<EStructuralFeature> getEAllStructuralFeatures(){return List.of(new EStructuralFeature());}public boolean isAbstract(){return false;}}''')
      write(root,'org/eclipse/emf/ecore/EObject.java','''package org.eclipse.emf.ecore; import org.eclipse.emf.ecore.resource.Resource; public interface EObject {EClass eClass();Resource eResource();boolean eIsSet(EStructuralFeature f);Object eGet(EStructuralFeature f,boolean resolve);EStructuralFeature eContainmentFeature();}''')
      write(root,'org/eclipse/emf/ecore/resource/ResourceSet.java','''package org.eclipse.emf.ecore.resource; public interface ResourceSet {}''')
      write(root,'org/eclipse/emf/ecore/resource/Resource.java','''package org.eclipse.emf.ecore.resource; import java.util.List; import org.eclipse.emf.ecore.EObject; public interface Resource {Object getURI();ResourceSet getResourceSet();List<EObject> getContents();}''')
      write(root,'org/eclipse/emf/ecore/util/EcoreUtil.java','''package org.eclipse.emf.ecore.util; import org.eclipse.emf.ecore.EObject; public final class EcoreUtil {public static EObject getRootContainer(EObject e){return e;}public static Object getURI(EObject e){return "memory:/model";}}''')
      write(root,'org/eclipse/emf/ecore/change/ChangeDescription.java','''package org.eclipse.emf.ecore.change; public interface ChangeDescription {void apply();}''')
      write(root,'org/eclipse/emf/ecore/change/util/ChangeRecorder.java','''package org.eclipse.emf.ecore.change.util; import org.eclipse.emf.ecore.EObject;import org.eclipse.emf.ecore.change.ChangeDescription; public final class ChangeRecorder {private final Object target;private final String snapshot;public ChangeRecorder(EObject t){target=t;snapshot=snapshot(t);}public ChangeDescription endRecording(){return ()->restore(target,snapshot);}public void dispose(){}private static String snapshot(Object o){try{return (String)o.getClass().getMethod("snapshot").invoke(o);}catch(Exception e){throw new RuntimeException(e);}}private static void restore(Object o,String v){try{o.getClass().getMethod("restoreSnapshot",String.class).invoke(o,v);}catch(Exception e){throw new RuntimeException(e);}}}''')
      write(root,'org/eclipse/emf/transaction/RollbackException.java','''package org.eclipse.emf.transaction; public class RollbackException extends Exception {public RollbackException(String m){super(m);}}''')
      write(root,'org/eclipse/emf/transaction/ResourceSetChangeEvent.java','''package org.eclipse.emf.transaction; import java.util.*;import org.eclipse.emf.common.notify.Notification; public final class ResourceSetChangeEvent {private final List<Notification> notifications;public ResourceSetChangeEvent(List<Notification> n){notifications=n;}public List<Notification> getNotifications(){return notifications;}}''')
      write(root,'org/eclipse/emf/transaction/ResourceSetListenerImpl.java','''package org.eclipse.emf.transaction; import org.eclipse.emf.common.command.Command; public class ResourceSetListenerImpl {protected ResourceSetListenerImpl(){}public boolean isPrecommitOnly(){return false;}public Command transactionAboutToCommit(ResourceSetChangeEvent e)throws RollbackException{return null;}}''')
      write(root,'org/eclipse/emf/transaction/TransactionalEditingDomain.java','''package org.eclipse.emf.transaction; import org.eclipse.emf.common.command.CommandStack; public interface TransactionalEditingDomain {void addResourceSetListener(ResourceSetListenerImpl l);CommandStack getCommandStack();}''')
      write(root,'org/eclipse/emf/transaction/RecordingCommand.java','''package org.eclipse.emf.transaction; import org.eclipse.emf.common.command.AbstractCommand; public abstract class RecordingCommand extends AbstractCommand {protected RecordingCommand(TransactionalEditingDomain d,String label){super(label);}protected boolean prepare(){return true;}protected abstract void doExecute();public final void execute(){doExecute();}public void undo(){}public void redo(){doExecute();}}''')
      write(root,'org/eclipse/emf/transaction/util/TransactionUtil.java','''package org.eclipse.emf.transaction.util; import org.eclipse.emf.ecore.EObject;import org.eclipse.emf.transaction.TransactionalEditingDomain; public final class TransactionUtil {private static TransactionalEditingDomain domain;public static void setEditingDomain(TransactionalEditingDomain d){domain=d;}public static TransactionalEditingDomain getEditingDomain(EObject e){return domain;}}''')
      write(root,'probe/Harness.java','''
        package probe; import java.util.*;import org.eclipse.emf.common.command.*;import org.eclipse.emf.common.notify.Notification;import org.eclipse.emf.ecore.*;import org.eclipse.emf.ecore.resource.*;import org.eclipse.emf.transaction.*;import org.eclipse.emf.transaction.util.TransactionUtil;
        public final class Harness {
          static boolean failGame=false,returnFalse=false;
          static final class RS implements ResourceSet{}
          static final class R implements Resource {final RS rs=new RS();final Model model;R(Model m){model=m;}public Object getURI(){return "memory:/m";}public ResourceSet getResourceSet(){return rs;}public List<EObject> getContents(){return List.of(model);}}
          public static final class Model implements EObject {final EClass cls=new EClass();String name;final R resource;Model(String n){name=n;resource=new R(this);}public String snapshot(){return name;}public void restoreSnapshot(String v){name=v;}public EClass eClass(){return cls;}public Resource eResource(){return resource;}public boolean eIsSet(EStructuralFeature f){return true;}public Object eGet(EStructuralFeature f,boolean r){return name;}public EStructuralFeature eContainmentFeature(){return null;}}
          static final class Domain implements TransactionalEditingDomain {ResourceSetListenerImpl listener;final CommandStack stack=Command::execute;public void addResourceSetListener(ResourceSetListenerImpl l){listener=l;}public CommandStack getCommandStack(){return stack;}Command trigger(Model m)throws Exception{return listener.transactionAboutToCommit(new ResourceSetChangeEvent(List.of((Notification)()->m)));}}
          static void check(boolean v,String m){if(!v)throw new AssertionError(m);}
          public static void main(String[] a)throws Exception {Model m=new Model("base");Domain d=new Domain();TransactionUtil.setEditingDomain(d);HydraSiriusRuntime.install(new HydraSiriusRuntime.Adapter(){public List<HydraSemanticRuntime.Diagnostic> validate(String s,String t){return List.of();}public HydraSemanticRuntime.GamificationState updateGamification(HydraSemanticRuntime.GamificationState p,List<HydraSemanticRuntime.Event> e,List<HydraSemanticRuntime.Event> f){if(failGame)throw new IllegalStateException("game");return p;}public boolean applyQuickFix(EObject o,String di,String ai,Map<String,Object> p){((Model)o).name="fixed";return !returnFalse;}public Object invoke(EObject o,String id,Map<String,Object> p){((Model)o).name="operated";return "ok";}});
            HydraSiriusRuntime.synchronize(m,"LOAD");check(HydraSiriusTransactionLifecycle.attach(m)&&d.listener!=null,"attach");var draft=HydraSiriusRuntime.checkpoint(m);HydraSiriusRuntime.validateHost(m);check(draft.equals(HydraSiriusRuntime.checkpoint(m)),"draft side effect");
            m.name="committed";Command c=d.trigger(m);c.execute();var after=HydraSiriusRuntime.checkpoint(m);check(!after.events().isEmpty(),"commit events");m.name="base";c.undo();check(HydraSiriusRuntime.events(m).isEmpty(),"undo exact runtime");m.name="committed";c.redo();check(HydraSiriusRuntime.checkpoint(m).equals(after),"redo exact runtime");
            var stable=HydraSiriusRuntime.checkpoint(m);m.name="broken";failGame=true;try{d.trigger(m).execute();throw new AssertionError("failure expected");}catch(IllegalStateException expected){}failGame=false;check(HydraSiriusRuntime.checkpoint(m).equals(stable),"failed precommit atomic");m.name="committed";
            returnFalse=true;check(!HydraSiriusRuntime.applyQuickFix(m,"d","noop",Map.of()),"false quickfix result");check("committed".equals(m.name),"false quickfix host rollback");returnFalse=false;
            HydraRuntimeActivation.setEnabled(false);m.name="hidden";d.trigger(m).execute();int history=HydraSiriusRuntime.events(m).size();HydraRuntimeActivation.setEnabled(true);Command on=d.trigger(m);on.execute();check(HydraSiriusRuntime.events(m).size()==history,"reactivation replay");
            System.out.println("HYDRA_SIRIUS_RUNTIME_E2E=PASS events="+history);
          }
        }
      ''')
      sources=[str(p.relative_to(root)) for p in root.rglob('*.java')]
      run([javac,'-encoding','UTF-8','-d','out',*sources],root)
      out=run([java,'-cp','out','probe.Harness'],root);require(out,'HYDRA_SIRIUS_RUNTIME_E2E=PASS','Sirius precommit/undo/redo execution')
    print('SIRIUS_RUNTIME_LIFECYCLE=PASS')
if __name__=='__main__': main()
