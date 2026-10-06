package com.example.editor.metamodel.importing.python;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import com.example.editor.metamodel.design.MetamodelDesignException;
import com.example.editor.metamodel.importing.ImportSourceEntry;
import com.example.editor.security.process.HydraExternalProcessPolicy;
import com.example.editor.security.process.HydraExternalProcessSecurityProperties;
import com.example.editor.security.process.HydraProcessTreeTerminator;

import tools.jackson.databind.ObjectMapper;

/** Aggressive executable completion/capacity harness for the isolated Python AST analyzer. */
public final class PythonProcessAstAnalyzerCompletionStressHarness {
    private static int checks;
    private PythonProcessAstAnalyzerCompletionStressHarness() { }

    public static void main(String[] args) throws Exception {
        if(isWindows()){
            System.out.println("HYDRA_PYTHON_AST_COMPLETION_STRESS=SKIPPED (POSIX fixture only)");
            return;
        }
        Path root=Files.createTempDirectory("hydra-python-ast-stress-");
        ExecutorService io=Executors.newFixedThreadPool(12);
        try {
            Path executable=createFakePython(root.resolve("python3"),root);
            normalAndFallback(root,executable,io);
            successfulRootCannotLeakChild(root,executable,io);
            timeoutKillsTree(root,executable,io);
            interruptionIsTerminal(root,executable,io);
            outputLimitFallsBack(root,executable,io);
            concurrentBurstRestoresCapacity(root,executable,io);
            System.out.println("HYDRA_PYTHON_AST_COMPLETION_STRESS="+checks+"/"+checks+" PASS");
        } finally { io.shutdownNow(); deleteTree(root); }
    }

    private static void normalAndFallback(Path root,Path executable,ExecutorService io){
        var analyzer=analyzer(properties(executable),io);
        var ok=analyzer.analyze(List.of(source("normal.py","class Normal:\n    value: int = 1\n")));
        check(!ok.fallbackUsed(),"normal fake Python result accepted: "+ok.diagnostics());
        check(analyzer.capacityStatus().inUse()==0,"normal analysis releases permit");

        PythonAstImportProperties unavailable=properties(root.resolve("missing-python3"));
        var fallback=analyzer(unavailable,io).analyze(List.of(source("fallback.py","class Fallback:\n    value: int = 1\n")));
        check(fallback.fallbackUsed(),"unavailable Python uses configured fallback");
        check(fallback.types().stream().anyMatch(type->type.name().equals("Fallback")),"fallback still recovers class information");
    }

    private static void successfulRootCannotLeakChild(Path root,Path executable,ExecutorService io)throws Exception{
        var analyzer=analyzer(properties(executable),io); Path pid=root.resolve("orphan.pid"); Files.deleteIfExists(pid);
        var result=analyzer.analyze(List.of(source("orphan.py","class Orphan:\n    pass\n")));
        waitFile(pid,3000); long child=Long.parseLong(Files.readString(pid).trim()); waitDead(child,2500);
        check(!result.fallbackUsed(),"orphaning Python root still returns normal result");
        check(!alive(child),"successful Python root cannot leak child");
        check(analyzer.capacityStatus().inUse()==0,"orphan success releases permit");
    }

    private static void timeoutKillsTree(Path root,Path executable,ExecutorService io)throws Exception{
        PythonAstImportProperties p=properties(executable); p.setTimeoutSeconds(1); var analyzer=analyzer(p,io); Path pid=root.resolve("timeout.pid"); Files.deleteIfExists(pid);
        var result=analyzer.analyze(List.of(source("timeout.py","class Timeout:\n    pass\n")));
        check(result.fallbackUsed(),"Python timeout degrades through configured fallback"); waitFile(pid,3000);
        for(long value:readPids(pid)){waitDead(value,2500);check(!alive(value),"Python timeout tree dead");}
        check(analyzer.capacityStatus().inUse()==0,"Python timeout releases permit");
    }

    private static void interruptionIsTerminal(Path root,Path executable,ExecutorService io)throws Exception{
        PythonAstImportProperties p=properties(executable); p.setTimeoutSeconds(30); var analyzer=analyzer(p,io); Path pid=root.resolve("interrupt.pid"); Files.deleteIfExists(pid);
        AtomicReference<Throwable> error=new AtomicReference<>(); AtomicBoolean interrupted=new AtomicBoolean();
        Thread caller=new Thread(()->{try{analyzer.analyze(List.of(source("interrupt.py","class Interrupted:\n    pass\n")));}catch(Throwable t){error.set(t);interrupted.set(Thread.currentThread().isInterrupted());}},"hydra-python-interrupt-harness");
        caller.start(); waitFile(pid,3000); caller.interrupt(); caller.join(5000);
        check(!caller.isAlive(),"Python interrupted caller returns");
        check(error.get() instanceof MetamodelDesignException,"Python cancellation remains terminal instead of fallback");
        check(interrupted.get(),"Python caller interruption flag preserved");
        for(long value:readPids(pid)){waitDead(value,2500);check(!alive(value),"Python interrupted tree dead");}
        check(analyzer.capacityStatus().inUse()==0,"Python interruption releases permit");
    }

    private static void outputLimitFallsBack(Path root,Path executable,ExecutorService io){
        PythonAstImportProperties p=properties(executable); p.setMaximumOutputBytes(16_384); var analyzer=analyzer(p,io);
        var result=analyzer.analyze(List.of(source("flood.py","class Flood:\n    pass\n")));
        check(result.fallbackUsed(),"oversized Python output uses controlled fallback");
        check(result.diagnostics().stream().anyMatch(value->value.contains("output")),"oversized Python output diagnostic retained");
        check(analyzer.capacityStatus().inUse()==0,"output-limit analysis releases permit");
    }

    private static void concurrentBurstRestoresCapacity(Path root,Path executable,ExecutorService io)throws Exception{
        PythonAstImportProperties p=properties(executable); p.setMaximumConcurrentProcesses(3); p.setProcessAcquireTimeoutSeconds(3); var analyzer=analyzer(p,io);
        ExecutorService callers=Executors.newFixedThreadPool(12);
        try {
            List<Future<PythonProjectAnalysis>> futures=new ArrayList<>();
            for(int i=0;i<24;i++){final int index=i;futures.add(callers.submit(()->analyzer.analyze(List.of(source("brief-"+index+".py","class C"+index+":\n    pass\n")))));}
            for(Future<PythonProjectAnalysis> future:futures) check(!future.get(10,TimeUnit.SECONDS).fallbackUsed(),"concurrent Python analysis completes");
        } finally { callers.shutdownNow(); }
        check(analyzer.capacityStatus().inUse()==0,"concurrent Python has no permits in use");
        check(analyzer.capacityStatus().queued()==0,"concurrent Python has no queued waiters");
        check(analyzer.availableProcessSlots()==p.getMaximumConcurrentProcesses(),"concurrent Python capacity restored");
    }

    private static PythonProcessAstAnalyzer analyzer(PythonAstImportProperties p,ExecutorService io){return new PythonProcessAstAnalyzer(new ObjectMapper(),p,io,new HydraExternalProcessPolicy(new HydraExternalProcessSecurityProperties()),new HydraProcessTreeTerminator());}
    private static PythonAstImportProperties properties(Path executable){PythonAstImportProperties p=new PythonAstImportProperties();p.setExecutable(executable.toString());p.setTimeoutSeconds(3);p.setMaximumOutputBytes(65_536);p.setMaximumConcurrentProcesses(4);p.setProcessAcquireTimeoutSeconds(2);p.setOutputCollectionTimeoutMillis(1000);p.setProcessTerminationGraceMillis(75);p.setProcessTerminationForceWaitMillis(750);p.setProcessTerminationMaximumPasses(8);p.setProcessTreeObservationIntervalMillis(2);return p;}
    private static ImportSourceEntry source(String path,String text){return new ImportSourceEntry(path,text.getBytes(StandardCharsets.UTF_8));}

    private static Path createFakePython(Path path,Path root)throws Exception{
        String base=root.toAbsolutePath().normalize().toString().replace("'","'\\''");
        String script=("""
                #!/bin/sh
                source_path="$6"
                cat >/dev/null
                case "$source_path" in
                  *orphan*) sleep 30 & child=$!; echo "$child" > '%s/orphan.pid'; sleep 0.25; echo '{"types":[]}' ;;
                  *timeout*) sleep 30 & child=$!; printf '%%s\\n%%s\\n' "$$" "$child" > '%s/timeout.pid'; sleep 30 ;;
                  *interrupt*) sleep 30 & child=$!; printf '%%s\\n%%s\\n' "$$" "$child" > '%s/interrupt.pid'; sleep 30 ;;
                  *flood*) head -c 131072 /dev/zero | tr '\\000' x ;;
                  *brief*) sleep 0.08; echo '{"types":[]}' ;;
                  *) echo '{"types":[]}' ;;
                esac
                """).formatted(base,base,base);
        Files.writeString(path,script,StandardCharsets.UTF_8);path.toFile().setExecutable(true,true);return path;
    }
    private static void waitFile(Path p,long ms)throws Exception{long end=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(ms);while((!Files.exists(p)||Files.size(p)==0)&&System.nanoTime()<end)Thread.sleep(10);check(Files.exists(p)&&Files.size(p)>0,"pid file produced");}
    private static List<Long> readPids(Path p)throws Exception{return Files.readAllLines(p).stream().filter(s->!s.isBlank()).map(Long::parseLong).toList();}
    private static boolean alive(long pid){return ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false);}
    private static void waitDead(long pid,long ms)throws Exception{long end=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(ms);while(alive(pid)&&System.nanoTime()<end)Thread.sleep(10);}
    private static void check(boolean ok,String what){checks++;if(!ok)throw new AssertionError(what);}
    private static boolean isWindows(){return System.getProperty("os.name","").toLowerCase().contains("win");}
    private static void deleteTree(Path root)throws Exception{if(root==null||!Files.exists(root))return;try(var stream=Files.walk(root)){for(Path path:stream.sorted(java.util.Comparator.reverseOrder()).toList())Files.deleteIfExists(path);}}
}
