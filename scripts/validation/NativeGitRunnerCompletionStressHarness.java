package com.example.editor.remote.git;

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

import com.example.editor.remote.storage.RemoteStorageCredentials;
import com.example.editor.remote.storage.RemoteStorageException;
import com.example.editor.remote.storage.RemoteStorageProperties;
import com.example.editor.remote.storage.RemoteStorageProviderType;
import com.example.editor.security.process.HydraExternalProcessPolicy;
import com.example.editor.security.process.HydraExternalProcessSecurityProperties;
import com.example.editor.security.process.HydraProcessTreeTerminator;

/** Aggressive executable completion/security harness for the native Git runner. */
public final class NativeGitRunnerCompletionStressHarness {
    private static int checks;
    private NativeGitRunnerCompletionStressHarness() { }

    public static void main(String[] args) throws Exception {
        if (isWindows()) {
            System.out.println("HYDRA_NATIVE_GIT_COMPLETION_STRESS=SKIPPED (POSIX fixture only)");
            return;
        }
        Path root=Files.createTempDirectory("hydra-native-git-stress-");
        try {
            Path executable=createFakeGit(root.resolve("git"));
            basicExitAndBoundedOutput(root,executable);
            successfulRootCannotLeakChild(root,executable);
            timeoutKillsTree(root,executable);
            interruptionKillsTree(root,executable);
            credentialsAreExplicitlyRedacted(root,executable);
            concurrentBurstRestoresCapacity(root,executable);
            System.out.println("HYDRA_NATIVE_GIT_COMPLETION_STRESS="+checks+"/"+checks+" PASS");
        } finally { deleteTree(root); }
    }

    private static void basicExitAndBoundedOutput(Path work,Path executable) {
        NativeGitProperties p=properties(executable); p.setMaximumOutputBytes(16_384);
        NativeGitRunner runner=runner(p);
        var ok=runner.run(work,List.of("success"));
        check(ok.successful(),"git success"); check(ok.output().contains("git-ok"),"git success output");
        var bad=runner.run(work,List.of("fail"));
        check(!bad.successful()&&bad.exitCode()==7,"git nonzero exit preserved");
        var flood=runner.run(work,List.of("flood"));
        check(flood.successful(),"git flood completes");
        check(flood.output().length()<=17_000,"git flood output bounded");
        check(flood.output().contains("configured capture limit"),"git flood truncation observable");
        check(runner.capacityStatus().inUse()==0,"basic git permits released");
    }

    private static void successfulRootCannotLeakChild(Path work,Path executable) throws Exception {
        NativeGitRunner runner=runner(properties(executable)); Path pid=work.resolve("git-orphan.pid");
        var result=runner.run(work,List.of("orphan",pid.toString()));
        waitFile(pid,3000); long child=Long.parseLong(Files.readString(pid).trim()); waitDead(child,2500);
        check(result.successful(),"orphaning git root remains successful");
        check(!alive(child),"git success cannot leak child");
        check(result.output().contains("tracked tree was terminated"),"git orphan cleanup observable");
    }

    private static void timeoutKillsTree(Path work,Path executable) throws Exception {
        NativeGitProperties p=properties(executable); p.setTimeout(Duration.ofMillis(350)); NativeGitRunner runner=runner(p);
        Path pid=work.resolve("git-timeout.pid"); boolean timedOut=false;
        try { runner.run(work,List.of("timeout",pid.toString())); }
        catch(RemoteStorageException exception){ timedOut=exception.getMessage().contains("timed out"); }
        check(timedOut,"git timeout reported"); waitFile(pid,3000);
        for(long value:readPids(pid)){waitDead(value,2500);check(!alive(value),"git timeout tree dead");}
        check(runner.capacityStatus().inUse()==0,"git timeout permit released");
    }

    private static void interruptionKillsTree(Path work,Path executable) throws Exception {
        NativeGitProperties p=properties(executable); p.setTimeout(Duration.ofSeconds(30)); NativeGitRunner runner=runner(p);
        Path pid=work.resolve("git-interrupt.pid"); AtomicReference<Throwable> error=new AtomicReference<>(); AtomicBoolean flag=new AtomicBoolean();
        Thread caller=new Thread(()->{try{runner.run(work,List.of("timeout",pid.toString()));}catch(Throwable t){error.set(t);flag.set(Thread.currentThread().isInterrupted());}},"hydra-git-interrupt-harness");
        caller.start(); waitFile(pid,3000); caller.interrupt(); caller.join(5000);
        check(!caller.isAlive(),"git interrupted caller returns");
        check(error.get() instanceof RemoteStorageException,"git interrupted error type");
        check(flag.get(),"git interrupted flag preserved");
        for(long value:readPids(pid)){waitDead(value,2500);check(!alive(value),"git interrupted tree dead");}
        check(runner.capacityStatus().inUse()==0,"git interrupted permit released");
    }

    private static void credentialsAreExplicitlyRedacted(Path work,Path executable) {
        NativeGitRunner runner=runner(properties(executable));
        RemoteStorageProperties.Profile profile=new RemoteStorageProperties.Profile(); profile.setProvider(RemoteStorageProviderType.GITHUB);
        String token="arbitrary-token-value-XYZ-987654321";
        var result=runner.runAuthenticated(work,List.of("secret"),profile,new RemoteStorageCredentials(token));
        check(result.successful(),"authenticated git fixture succeeds");
        check(!result.output().contains(token),"explicit credential absent from output");
        check(result.output().contains("[REDACTED]"),"credential redaction visible");
    }

    private static void concurrentBurstRestoresCapacity(Path work,Path executable) throws Exception {
        NativeGitProperties p=properties(executable); p.setMaximumConcurrentProcesses(3); p.setProcessAcquireTimeoutSeconds(3); NativeGitRunner runner=runner(p);
        ExecutorService callers=Executors.newFixedThreadPool(12);
        try {
            List<Future<NativeGitRunner.Result>> futures=new ArrayList<>();
            for(int i=0;i<24;i++) futures.add(callers.submit(()->runner.run(work,List.of("brief"))));
            for(Future<NativeGitRunner.Result> future:futures) check(future.get(8,TimeUnit.SECONDS).successful(),"concurrent git completes");
        } finally { callers.shutdownNow(); }
        check(runner.capacityStatus().inUse()==0,"concurrent git has no permits in use");
        check(runner.capacityStatus().queued()==0,"concurrent git has no queued waiters");
        check(runner.availableProcessSlots()==p.getMaximumConcurrentProcesses(),"concurrent git capacity restored");
    }

    private static NativeGitRunner runner(NativeGitProperties p){return new NativeGitRunner(p,new HydraExternalProcessPolicy(new HydraExternalProcessSecurityProperties()),new HydraProcessTreeTerminator());}
    private static NativeGitProperties properties(Path executable){NativeGitProperties p=new NativeGitProperties();p.setExecutable(executable.toString());p.setTimeout(Duration.ofSeconds(3));p.setOutputDrainWaitMillis(1000);p.setProcessTerminationGraceMillis(75);p.setProcessTerminationForceWaitMillis(750);p.setProcessTerminationMaximumPasses(8);p.setProcessTreeObservationIntervalMillis(2);p.setProcessAcquireTimeoutSeconds(2);return p;}

    private static Path createFakeGit(Path path) throws Exception {
        String script="""
                #!/bin/sh
                mode=""; value=""; previous=""
                for arg in "$@"; do
                  if [ "$previous" = "orphan" ] || [ "$previous" = "timeout" ]; then value="$arg"; fi
                  case "$arg" in success|fail|flood|orphan|timeout|secret|brief) mode="$arg" ;; esac
                  previous="$arg"
                done
                case "$mode" in
                  success) echo git-ok ;;
                  fail) echo git-failed; exit 7 ;;
                  flood) head -c 262144 /dev/zero | tr '\\000' x ;;
                  orphan) sleep 30 & child=$!; echo "$child" > "$value"; sleep 0.25; echo orphan-ok ;;
                  timeout) sleep 30 & child=$!; printf '%s\\n%s\\n' "$$" "$child" > "$value"; sleep 30 ;;
                  secret) printf '%s\\n' "$HYDRA_GIT_PASSWORD" ;;
                  brief) sleep 0.08; echo brief-ok ;;
                  *) echo git-default ;;
                esac
                """;
        Files.writeString(path,script,StandardCharsets.UTF_8); path.toFile().setExecutable(true,true); return path;
    }
    private static void waitFile(Path p,long ms)throws Exception{long end=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(ms);while((!Files.exists(p)||Files.size(p)==0)&&System.nanoTime()<end)Thread.sleep(10);check(Files.exists(p)&&Files.size(p)>0,"pid file produced");}
    private static List<Long> readPids(Path p)throws Exception{return Files.readAllLines(p).stream().filter(s->!s.isBlank()).map(Long::parseLong).toList();}
    private static boolean alive(long pid){return ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false);}
    private static void waitDead(long pid,long ms)throws Exception{long end=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(ms);while(alive(pid)&&System.nanoTime()<end)Thread.sleep(10);}
    private static void check(boolean ok,String what){checks++;if(!ok)throw new AssertionError(what);}
    private static boolean isWindows(){return System.getProperty("os.name","").toLowerCase().contains("win");}
    private static void deleteTree(Path root)throws Exception{if(root==null||!Files.exists(root))return;try(var stream=Files.walk(root)){for(Path path:stream.sorted(java.util.Comparator.reverseOrder()).toList())Files.deleteIfExists(path);}}
}
