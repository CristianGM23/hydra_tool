package com.example.editor.metamodel.importing;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import com.example.editor.metamodel.core.TransformationWarning;

/** Dependency-light stress harness for asynchronous import-job completion semantics. */
public final class MetamodelImportJobCompletionStressHarness {
    private static int checks;
    private MetamodelImportJobCompletionStressHarness() { }

    public static void main(String[] args) throws Exception {
        successAndPrepared();
        failureTerminalizesEveryStage();
        queuedTimeoutCannotRevive();
        runningTimeoutCannotRevive();
        shutdownCancelsRunningWork();
        ownedFallbackWatchdogLifecycle();
        activityAndRetentionStress();
        concurrentCompletionStress();
        System.out.println("HYDRA_IMPORT_JOB_COMPLETION_STRESS=" + checks + "/" + checks + " PASS");
    }

    private static MetamodelImportProperties props() {
        MetamodelImportProperties p = new MetamodelImportProperties();
        p.setAnalysisMaximumDurationSeconds(1);
        p.setAnalysisMaxRetainedJobs(20);
        p.setAnalysisMaximumActivityMessages(100);
        p.setAnalysisMaximumActivityDetailCharacters(128);
        p.setAnalysisMaximumStageLogCharacters(4096);
        return p;
    }

    private static MetamodelImportPreview preview() {
        return new MetamodelImportPreview("ecore", "FULL", null, null, null,
                List.of(new TransformationWarning("WARN", "root", "warning")), null);
    }

    private static void successAndPrepared() throws Exception {
        ExecutorService workers = Executors.newFixedThreadPool(2); ScheduledExecutorService watchdog = Executors.newScheduledThreadPool(1);
        try {
            MetamodelImportAnalysisProcessor processor = (name, type, content, options, listener) -> {
                for (MetamodelImportAnalysisStage stage : MetamodelImportAnalysisStage.values()) {
                    if(stage == MetamodelImportAnalysisStage.FILE_RECEIVED) continue;
                    listener.stageStarted(stage, "start", "d");
                    listener.log(stage, MetamodelImportActivitySeverity.INFO, "OK_" + stage.id(), "ok", "details");
                    listener.stageCompleted(stage, "done", "d");
                }
                return preview();
            };
            var service = new MetamodelImportAnalysisJobService(processor, workers, props(), Clock.systemUTC(), watchdog);
            String id = service.start("a.ecore", "application/xml", new byte[]{1,2,3}, null).jobId();
            var result = awaitTerminal(service,id,3000);
            check(result.status()==MetamodelImportAnalysisStatus.COMPLETED,"success status");
            check(result.progress()==100,"success progress");
            check(result.stages().stream().allMatch(MetamodelImportJobCompletionStressHarness::terminal),"all success stages terminal");
            check(service.prepared(id).preview()!=null,"prepared available");
            check(service.capacityStatus().activeJobs()==0,"no active success jobs");
        } finally { workers.shutdownNow(); watchdog.shutdownNow(); }
    }

    private static void failureTerminalizesEveryStage() throws Exception {
        ExecutorService workers=Executors.newSingleThreadExecutor(); ScheduledExecutorService watchdog=Executors.newSingleThreadScheduledExecutor();
        try {
            MetamodelImportAnalysisProcessor processor=(n,t,c,o,l)-> { l.stageStarted(MetamodelImportAnalysisStage.PARSING,"parse",""); throw new IllegalStateException("boom"); };
            var service=new MetamodelImportAnalysisJobService(processor,workers,props(),Clock.systemUTC(),watchdog);
            String id=service.start("bad.ecore","application/xml",new byte[]{1},null).jobId();
            var r=awaitTerminal(service,id,3000);
            check(r.status()==MetamodelImportAnalysisStatus.FAILED,"failure status");
            check(r.stages().stream().allMatch(MetamodelImportJobCompletionStressHarness::terminal),"all failed stages terminal");
            check(r.stages().stream().anyMatch(s->s.status()==MetamodelImportAnalysisStageStatus.FAILED),"failure stage recorded");
            check(service.capacityStatus().activeJobs()==0,"no active failed jobs");
        } finally { workers.shutdownNow(); watchdog.shutdownNow(); }
    }

    private static void queuedTimeoutCannotRevive() throws Exception {
        ExecutorService workers=Executors.newSingleThreadExecutor(); ScheduledExecutorService watchdog=Executors.newSingleThreadScheduledExecutor();
        CountDownLatch blocker=new CountDownLatch(1); workers.submit(()->{try{blocker.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}});
        AtomicInteger calls=new AtomicInteger(); Path staged=Files.createTempFile("hydra-queued-", ".ecore"); Files.writeString(staged,"x");
        try {
            MetamodelImportAnalysisProcessor processor=(n,t,c,o,l)->{calls.incrementAndGet();return preview();};
            var service=new MetamodelImportAnalysisJobService(processor,workers,props(),Clock.systemUTC(),watchdog);
            String id=service.start(new MetamodelImportStagedFile(staged,"queued.ecore","application/xml",1,"sha"),null).jobId();
            var r=awaitTerminal(service,id,3000);
            check(r.status()==MetamodelImportAnalysisStatus.FAILED,"queued timeout status");
            check(r.errorMessage().contains("configured total duration"),"queued timeout reason");
            check(r.stages().stream().allMatch(MetamodelImportJobCompletionStressHarness::terminal),"queued timeout all stages terminal");
            waitUntil(()->!Files.exists(staged),1500); check(!Files.exists(staged),"queued staging removed");
            blocker.countDown(); Thread.sleep(250);
            check(calls.get()==0,"expired queued job never entered processor");
            check(service.get(id).status()==MetamodelImportAnalysisStatus.FAILED,"queued job cannot revive");
        } finally { blocker.countDown(); workers.shutdownNow(); watchdog.shutdownNow(); Files.deleteIfExists(staged); }
    }

    private static void runningTimeoutCannotRevive() throws Exception {
        ExecutorService workers=Executors.newSingleThreadExecutor(); ScheduledExecutorService watchdog=Executors.newSingleThreadScheduledExecutor();
        AtomicBoolean interrupted=new AtomicBoolean(); AtomicBoolean returned=new AtomicBoolean();
        try {
            MetamodelImportAnalysisProcessor processor=(n,t,c,o,l)->{
                l.stageStarted(MetamodelImportAnalysisStage.PARSING,"parse","");
                long end=System.nanoTime()+Duration.ofMillis(1600).toNanos();
                while(System.nanoTime()<end){try{Thread.sleep(50);}catch(InterruptedException e){interrupted.set(true); /* deliberately ignore to emulate hostile tool */}}
                l.log(MetamodelImportAnalysisStage.PARSING,MetamodelImportActivitySeverity.INFO,"LATE","late",""); returned.set(true); return preview();
            };
            var service=new MetamodelImportAnalysisJobService(processor,workers,props(),Clock.systemUTC(),watchdog);
            String id=service.start("slow.ecore","application/xml",new byte[]{1},null).jobId();
            var timed=awaitTerminal(service,id,3000); int activitiesAtTimeout=timed.activities().size();
            check(timed.status()==MetamodelImportAnalysisStatus.FAILED,"running timeout status");
            check(interrupted.get(),"running timeout interrupts worker");
            check(timed.stages().stream().allMatch(MetamodelImportJobCompletionStressHarness::terminal),"running timeout all stages terminal");
            waitUntil(returned::get,2500); Thread.sleep(100);
            var after=service.get(id);
            check(after.status()==MetamodelImportAnalysisStatus.FAILED,"late processor cannot revive");
            check(after.preview()==null,"late preview discarded");
            check(after.activities().size()==activitiesAtTimeout,"late callbacks cannot append activity");
        } finally { workers.shutdownNow(); watchdog.shutdownNow(); }
    }

    private static void shutdownCancelsRunningWork() throws Exception {
        ExecutorService workers=Executors.newSingleThreadExecutor(); ScheduledExecutorService watchdog=Executors.newSingleThreadScheduledExecutor(); CountDownLatch entered=new CountDownLatch(1); AtomicBoolean interrupted=new AtomicBoolean();
        try {
            MetamodelImportProperties p=props(); p.setAnalysisMaximumDurationSeconds(30);
            MetamodelImportAnalysisProcessor processor=(n,t,c,o,l)->{entered.countDown(); try{Thread.sleep(30_000);}catch(InterruptedException e){interrupted.set(true); Thread.currentThread().interrupt(); throw new IllegalStateException("cancelled");} return preview();};
            var service=new MetamodelImportAnalysisJobService(processor,workers,p,Clock.systemUTC(),watchdog);
            String id=service.start("cancel.ecore","application/xml",new byte[]{1},null).jobId(); check(entered.await(2,TimeUnit.SECONDS),"shutdown work entered");
            service.cancelOutstandingAnalyses(); var r=awaitTerminal(service,id,1500);
            check(r.status()==MetamodelImportAnalysisStatus.FAILED,"shutdown terminal status");
            waitUntil(interrupted::get,1000); check(interrupted.get(),"shutdown interrupts worker");
            check(r.stages().stream().allMatch(MetamodelImportJobCompletionStressHarness::terminal),"shutdown all stages terminal");
        } finally { workers.shutdownNow(); watchdog.shutdownNow(); }
    }


    private static void ownedFallbackWatchdogLifecycle() throws Exception {
        long baseline = fallbackWatchdogThreads();
        ExecutorService workers = Executors.newFixedThreadPool(4);
        try {
            MetamodelImportProperties p = props(); p.setAnalysisMaximumDurationSeconds(5); p.setAnalysisShutdownWaitSeconds(1);
            MetamodelImportAnalysisProcessor processor=(n,t,c,o,l)->preview();
            for(int i=0;i<40;i++) {
                var service = new MetamodelImportAnalysisJobService(processor, workers, p, Clock.systemUTC());
                String id = service.start("owned-"+i+".ecore", "application/xml", new byte[]{1}, null).jobId();
                awaitTerminal(service,id,3000);
                service.cancelOutstandingAnalyses();
            }
            waitUntil(() -> fallbackWatchdogThreads() <= baseline, 3000);
            check(fallbackWatchdogThreads() <= baseline, "owned fallback watchdog threads terminate");
        } finally { workers.shutdownNow(); }
    }

    private static long fallbackWatchdogThreads() {
        return Thread.getAllStackTraces().keySet().stream()
                .filter(Thread::isAlive)
                .filter(thread -> thread.getName().startsWith("metamodel-import-fallback-watchdog-"))
                .count();
    }

    private static void activityAndRetentionStress() throws Exception {
        ExecutorService workers=Executors.newFixedThreadPool(8); ScheduledExecutorService watchdog=Executors.newScheduledThreadPool(2);
        try {
            MetamodelImportProperties p=props(); p.setAnalysisMaximumDurationSeconds(10); p.setAnalysisMaxRetainedJobs(20); p.setAnalysisMaximumActivityMessages(100); p.setAnalysisMaximumActivityDetailCharacters(128);
            MetamodelImportAnalysisProcessor processor=(n,t,c,o,l)->{
                l.stageStarted(MetamodelImportAnalysisStage.PARSING,"parse","");
                String payload="X".repeat(2000);
                for(int i=0;i<5000;i++) l.log(MetamodelImportAnalysisStage.PARSING,MetamodelImportActivitySeverity.INFO,"MSG"+i,"m"+i,payload);
                l.stageCompleted(MetamodelImportAnalysisStage.PARSING,"done",""); return preview();
            };
            var service=new MetamodelImportAnalysisJobService(processor,workers,p,Clock.systemUTC(),watchdog);
            String stressed=service.start("activity.ecore","application/xml",new byte[]{1},null).jobId(); var r=awaitTerminal(service,stressed,8000);
            check(r.status()==MetamodelImportAnalysisStatus.COMPLETED,"activity stress completes");
            check(r.activities().size()<=100,"activity retention bounded");
            check(r.activities().stream().allMatch(a->a.details().length()<=p.getAnalysisMaximumActivityDetailCharacters()),"activity details bounded");
            for(int i=0;i<80;i++){String id=service.start("r"+i+".ecore","application/xml",new byte[]{1},null).jobId(); awaitTerminal(service,id,8000);}
            var cap=service.capacityStatus(); check(cap.retainedJobs()<=20,"job retention bounded"); check(cap.activeJobs()==0,"retention stress no active jobs");
        } finally { workers.shutdownNow(); watchdog.shutdownNow(); }
    }

    private static void concurrentCompletionStress() throws Exception {
        ExecutorService workers=Executors.newFixedThreadPool(12); ScheduledExecutorService watchdog=Executors.newScheduledThreadPool(2);
        try {
            MetamodelImportProperties p=props(); p.setAnalysisMaximumDurationSeconds(10); p.setAnalysisMaxRetainedJobs(200);
            MetamodelImportAnalysisProcessor processor=(n,t,c,o,l)->{for(var s:MetamodelImportAnalysisStage.values()){if(s==MetamodelImportAnalysisStage.FILE_RECEIVED) continue;l.stageStarted(s,"s","");l.stageCompleted(s,"d","");}return preview();};
            var service=new MetamodelImportAnalysisJobService(processor,workers,p,Clock.systemUTC(),watchdog);
            String[] ids=new String[120]; for(int i=0;i<ids.length;i++) ids[i]=service.start("c"+i+".ecore","application/xml",new byte[]{1},null).jobId();
            for(String id:ids){var r=awaitTerminal(service,id,10000);check(r.status()==MetamodelImportAnalysisStatus.COMPLETED,"concurrent completion");check(r.stages().stream().allMatch(MetamodelImportJobCompletionStressHarness::terminal),"concurrent stages terminal");}
            check(service.capacityStatus().activeJobs()==0,"concurrent stress no active jobs");
        } finally { workers.shutdownNow(); watchdog.shutdownNow(); }
    }

    private static boolean terminal(MetamodelImportAnalysisStageResponse s){return s.status()!=MetamodelImportAnalysisStageStatus.PENDING&&s.status()!=MetamodelImportAnalysisStageStatus.RUNNING;}
    private static MetamodelImportAnalysisJobResponse awaitTerminal(MetamodelImportAnalysisJobService s,String id,long ms)throws Exception{long end=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(ms);for(;;){var r=s.get(id);if(r.status()==MetamodelImportAnalysisStatus.COMPLETED||r.status()==MetamodelImportAnalysisStatus.FAILED)return r;if(System.nanoTime()>end)throw new AssertionError("job did not terminate: "+id);Thread.sleep(10);}}
    private static void waitUntil(Check c,long ms)throws Exception{long end=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(ms);while(!c.ok()&&System.nanoTime()<end)Thread.sleep(10);}
    private static void check(boolean ok,String what){checks++;if(!ok)throw new AssertionError(what);}
    @FunctionalInterface private interface Check{boolean ok()throws Exception;}
}
