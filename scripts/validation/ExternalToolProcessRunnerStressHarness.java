package com.example.editor.integration.verification;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.example.editor.security.process.HydraExternalProcessPolicy;
import com.example.editor.security.process.HydraExternalProcessSecurityProperties;
import com.example.editor.security.process.HydraProcessTreeTerminator;

/** Executes the real external-tool runner against real child JVMs without build-tool dependencies. */
public final class ExternalToolProcessRunnerStressHarness {
    private static int checks;
    private ExternalToolProcessRunnerStressHarness() { }

    public static void main(String[] args) throws Exception {
        if(args.length>0){child(args);return;}
        Path work=Files.createTempDirectory("hydra-external-runner-");
        ExecutorService io=Executors.newFixedThreadPool(16);
        try{
            TechnologyProjectValidationProperties p=new TechnologyProjectValidationProperties();
            p.setMaximumProcessOutputBytes(1_048_576); p.setMaximumConcurrentProcesses(8); p.setProcessAcquireTimeoutSeconds(3); p.setProcessOutputJoinTimeoutSeconds(3);
            p.setProcessTerminationGraceMillis(100); p.setProcessTerminationForceWaitMillis(1000); p.setProcessTerminationMaximumPasses(8);
            HydraExternalProcessSecurityProperties sec=new HydraExternalProcessSecurityProperties();
            ExternalToolProcessRunner runner=new ExternalToolProcessRunner(new ExternalToolCircuitBreaker(64),p,io,new HydraExternalProcessPolicy(sec),new HydraProcessTreeTerminator());
            successFailureFlood(runner,work,p);
            successfulRootCannotLeakChild(runner,work);
            timeoutTree(runner,work);
            interruptionTree(runner,work);
            concurrentBurst(runner,work,p);
            check(runner.capacityStatus().inUse()==0,"no permits in use"); check(runner.capacityStatus().queued()==0,"no queued permit waiters"); check(runner.availableProcessSlots()==p.getMaximumConcurrentProcesses(),"all permits restored");
            System.out.println("HYDRA_EXTERNAL_TOOL_COMPLETION_STRESS="+checks+"/"+checks+" PASS");
        }finally{io.shutdownNow();deleteTree(work);}
    }

    private static void successFailureFlood(ExternalToolProcessRunner r,Path work,TechnologyProjectValidationProperties p){
        var ok=r.run("ok","success",work,cmd("exit","0"),0,3,Map.of()); check(ok.status()==TechnologyValidationStepStatus.PASSED,"success"); check(ok.exitCode()==0,"success exit");
        var bad=r.run("bad","failure",work,cmd("exit","7"),0,3,Map.of()); check(bad.status()==TechnologyValidationStepStatus.FAILED,"nonzero failure"); check(bad.exitCode()==7,"nonzero exit");
        var flood=r.run("flood","flood",work,cmd("flood","4194304"),0,10,Map.of()); check(flood.status()==TechnologyValidationStepStatus.PASSED,"flood completes");
        check(flood.stdout().getBytes(java.nio.charset.StandardCharsets.UTF_8).length<=p.getMaximumProcessOutputBytes(),"stdout bounded"); check(flood.stderr().getBytes(java.nio.charset.StandardCharsets.UTF_8).length<=p.getMaximumProcessOutputBytes(),"stderr bounded"); check(!flood.diagnostics().isEmpty(),"truncation diagnostic");
    }

    private static void timeoutTree(ExternalToolProcessRunner r,Path work)throws Exception{
        Path pid=work.resolve("timeout.pid"); var out=r.run("timeout","timeout tree",work,cmd("tree",pid.toString(),"30000"),0,1,Map.of());
        check(out.status()==TechnologyValidationStepStatus.TIMED_OUT,"timeout status"); waitPidFile(pid); for(long value:readPids(pid)){waitDead(value,2500);check(!alive(value),"timeout tree process terminated");}
    }

    private static void successfulRootCannotLeakChild(ExternalToolProcessRunner r,Path work)throws Exception{
        Path pid=work.resolve("orphan-success.pid");
        var out=r.run("orphan-success","successful root with inherited-output child",work,
                cmd("orphan-success",pid.toString(),"30000"),0,5,Map.of());
        waitPidFile(pid);
        long childPid=readPids(pid).getFirst();
        waitDead(childPid,2500);
        check(out.status()==TechnologyValidationStepStatus.PASSED,"successful root remains successful");
        check(!alive(childPid),"successful root cannot leak tracked child");
        check(out.diagnostics().stream().anyMatch(value->value.contains("descendant process")),"orphan cleanup is observable");
    }

    private static void interruptionTree(ExternalToolProcessRunner r,Path work)throws Exception{
        Path pid=work.resolve("interrupt.pid"); AtomicReference<TechnologyValidationStepResult> result=new AtomicReference<>();
        Thread thread=new Thread(()->result.set(r.run("cancel","interrupt tree",work,cmd("tree",pid.toString(),"30000"),0,30,Map.of())),"runner-interrupt-test"); thread.start(); waitPidFile(pid); thread.interrupt(); thread.join(5000);
        check(!thread.isAlive(),"interrupted caller terminates"); check(result.get()!=null&&result.get().status()==TechnologyValidationStepStatus.CANCELLED,"cancelled status");
        for(long value:readPids(pid)){waitDead(value,2500);check(!alive(value),"interrupted tree process terminated");}
    }

    private static void concurrentBurst(ExternalToolProcessRunner r,Path work,TechnologyProjectValidationProperties p)throws Exception{
        ExecutorService callers=Executors.newFixedThreadPool(20); try{List<java.util.concurrent.Future<TechnologyValidationStepResult>> futures=new ArrayList<>();for(int i=0;i<40;i++)futures.add(callers.submit(()->r.run("burst","burst",work,cmd("sleep","80"),0,5,Map.of())));for(var f:futures)check(f.get(8,TimeUnit.SECONDS).status()==TechnologyValidationStepStatus.PASSED,"burst completes");}finally{callers.shutdownNow();}
        check(r.capacityStatus().inUse()==0,"burst releases all permits"); check(r.availableProcessSlots()==p.getMaximumConcurrentProcesses(),"burst capacity restored");
    }

    private static List<String> cmd(String... tail){List<String> c=new ArrayList<>();c.add(javaBin());c.add("-cp");c.add(absoluteClasspath());c.add(ExternalToolProcessRunnerStressHarness.class.getName());c.addAll(List.of(tail));return c;}
    private static String absoluteClasspath(){String separator=System.getProperty("path.separator");return java.util.Arrays.stream(System.getProperty("java.class.path").split(java.util.regex.Pattern.quote(separator))).map(Path::of).map(Path::toAbsolutePath).map(Path::normalize).map(Path::toString).collect(java.util.stream.Collectors.joining(separator));}
    private static String javaBin(){return Path.of(System.getProperty("java.home"),"bin",isWindows()?"java.exe":"java").toString();}
    private static boolean isWindows(){return System.getProperty("os.name","").toLowerCase().contains("win");}
    private static void child(String[] a)throws Exception{
        switch(a[0]){
            case "exit" -> System.exit(Integer.parseInt(a[1]));
            case "sleep" -> Thread.sleep(Long.parseLong(a[1]));
            case "flood" -> {int n=Integer.parseInt(a[1]);byte[] chunk="0123456789abcdef".repeat(512).getBytes();for(int x=0;x<n;x+=chunk.length){System.out.write(chunk,0,Math.min(chunk.length,n-x));System.err.write(chunk,0,Math.min(chunk.length,n-x));}System.out.flush();System.err.flush();}
            case "tree" -> {Path f=Path.of(a[1]);long sleep=Long.parseLong(a[2]);Process child=new ProcessBuilder(javaBin(),"-cp",System.getProperty("java.class.path"),ExternalToolProcessRunnerStressHarness.class.getName(),"sleep",Long.toString(sleep)).start();Files.writeString(f,ProcessHandle.current().pid()+"\n"+child.pid()+"\n");Thread.sleep(sleep);}
            case "orphan-success" -> {Path f=Path.of(a[1]);long sleep=Long.parseLong(a[2]);Process child=new ProcessBuilder(javaBin(),"-cp",System.getProperty("java.class.path"),ExternalToolProcessRunnerStressHarness.class.getName(),"sleep",Long.toString(sleep)).inheritIO().start();Files.writeString(f,Long.toString(child.pid()));Thread.sleep(250);}
            default -> throw new IllegalArgumentException(a[0]);
        }
    }
    private static void waitPidFile(Path p)throws Exception{long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);while((!Files.exists(p)||Files.size(p)==0)&&System.nanoTime()<end)Thread.sleep(10);check(Files.exists(p)&&Files.size(p)>0,"pid file produced");}
    private static List<Long> readPids(Path p)throws Exception{return Files.readAllLines(p).stream().filter(s->!s.isBlank()).map(Long::parseLong).toList();}
    private static boolean alive(long pid){return ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false);}
    private static void waitDead(long pid,long ms)throws Exception{long end=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(ms);while(alive(pid)&&System.nanoTime()<end)Thread.sleep(10);}
    private static void check(boolean ok,String what){checks++;if(!ok)throw new AssertionError(what);}
    private static void deleteTree(Path root)throws Exception{if(root==null||!Files.exists(root))return;try(var s=Files.walk(root)){for(Path p:s.sorted(java.util.Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}}
}
