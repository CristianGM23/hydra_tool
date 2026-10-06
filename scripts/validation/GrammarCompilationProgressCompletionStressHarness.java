import com.example.editor.grammar.GrammarProperties;
import com.example.editor.grammar.application.GrammarCompilationProgressService;
import com.example.editor.grammar.runtime.GrammarCompilationProgress;
import com.example.editor.grammar.runtime.GrammarCompilationStage;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Stress standalone del registro efímero de progreso de compilación. */
public final class GrammarCompilationProgressCompletionStressHarness {
    private static final AtomicInteger checks=new AtomicInteger();
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);checks.incrementAndGet();}
    private static void expectFailure(Runnable action,String message){try{action.run();throw new AssertionError(message);}catch(IllegalArgumentException|IllegalStateException expected){checks.incrementAndGet();}}
    private static GrammarCompilationProgress require(GrammarCompilationProgressService service,String id){return service.find(id).orElseThrow(()->new AssertionError("missing progress "+id));}
    private static GrammarProperties properties(int entries,int concurrent){GrammarProperties p=new GrammarProperties();p.setMaximumCompilationProgressEntries(entries);p.setMaximumConcurrentCompilations(concurrent);p.setMaximumCompilationProgressDetailCharacters(256);return p;}

    public static void main(String[] args) throws Exception {
        basicMonotonicLifecycle();
        boundedDetailsAndIdentity();
        terminalLruNeverEvictsActive();
        allActiveSaturationIsExplicit();
        concurrentStageMonotonicity();
        terminalRaceIsOneWay();
        terminalChurnCannotCorruptActiveWork();
        System.out.println("GRAMMAR_COMPILATION_PROGRESS_COMPLETION_STRESS="+checks.get()+"/"+checks.get()+" PASS");
    }

    private static void basicMonotonicLifecycle(){
        GrammarCompilationProgressService service=new GrammarCompilationProgressService(properties(16,4));
        GrammarCompilationProgress start=service.start("basic","g");
        check("RUNNING".equals(start.status()),"start must run");
        check(service.activeEntries()==1,"one active expected");
        GrammarCompilationProgress advanced=service.stage("basic","g",GrammarCompilationStage.GENERATE_PROJECT,6,"generate");
        check(advanced.completedStages()==6,"completed stage count must advance");
        GrammarCompilationProgress regressed=service.stage("basic","g",GrammarCompilationStage.FULL_ANALYSIS,1,"older callback");
        check(GrammarCompilationStage.GENERATE_PROJECT.name().equals(regressed.stage()),"stage cannot regress");
        check(regressed.completedStages()==6,"counter cannot regress");
        GrammarCompilationProgress success=service.complete("basic","g","ok");
        check("SUCCESS".equals(success.status()),"success expected");
        check(success.completedStages()==GrammarCompilationStage.values().length,"success completes all stages");
        check(service.activeEntries()==0,"success must be terminal");
        check("SUCCESS".equals(service.fail("basic","g",GrammarCompilationStage.FULL_ANALYSIS,0,"late failure").status()),"late fail cannot replace success");
        check("SUCCESS".equals(service.cancel("basic","g","late cancel").status()),"late cancel cannot replace success");
        check("SUCCESS".equals(service.stage("basic","g",GrammarCompilationStage.FREEZE_DRAFT,0,"late stage").status()),"late stage cannot revive success");
        check(service.ensureTerminal("basic","g","guard").orElseThrow().status().equals("SUCCESS"),"finally guard preserves terminal");
        check(service.ensureTerminal("evicted-or-never-started","g","guard").isEmpty(),"missing progress is a safe no-op for finally");

        service.start("failed","g");
        service.stage("failed","g",GrammarCompilationStage.SMOKE_TEST,9,"smoke");
        GrammarCompilationProgress failed=service.fail("failed","g",GrammarCompilationStage.RESOLVE_IMPORTS,2,"failure");
        check("FAILED".equals(failed.status()),"failure terminal expected");
        check(GrammarCompilationStage.SMOKE_TEST.name().equals(failed.stage()),"failure callback cannot move stage backwards");
        check(failed.completedStages()==9,"failure callback cannot move counter backwards");

        service.start("cancelled","g");
        service.stage("cancelled","g",GrammarCompilationStage.MATERIALIZE_RUNTIME,8,"runtime");
        GrammarCompilationProgress cancelled=service.cancel("cancelled","g","user cancel");
        check("CANCELLED".equals(cancelled.status()),"cancelled terminal expected");
        check(service.activeEntries()==0,"no active entries after terminalization");

        service.start("guarded","g");
        GrammarCompilationProgress guarded=service.ensureTerminal("guarded","g","unexpected exception").orElseThrow();
        check("FAILED".equals(guarded.status()),"finally must terminalize forgotten progress");
        check(service.activeEntries()==0,"guard must remove logical activity");
    }

    private static void boundedDetailsAndIdentity(){
        GrammarCompilationProgressService service=new GrammarCompilationProgressService(properties(16,4));
        service.start("detail","g");
        GrammarCompilationProgress value=service.stage("detail","g",GrammarCompilationStage.FULL_ANALYSIS,Integer.MAX_VALUE," X ".repeat(20_000));
        check(value.detail().length()==256,"detail must be bounded by configured limit");
        check(value.completedStages()==value.totalStages(),"completed count must be clamped to total stages");
        expectFailure(()->service.start("detail","g"),"duplicate requestId must fail");
        expectFailure(()->service.stage("unknown","g",GrammarCompilationStage.FULL_ANALYSIS,1,"x"),"unknown progress transition must fail");
        expectFailure(()->service.stage("detail","other",GrammarCompilationStage.FULL_ANALYSIS,1,"x"),"cross-grammar requestId reuse must fail");
        expectFailure(()->service.start(" ","g"),"blank requestId must fail");
        expectFailure(()->service.start("x"," "),"blank grammarId must fail");
    }

    private static void terminalLruNeverEvictsActive(){
        GrammarCompilationProgressService service=new GrammarCompilationProgressService(properties(16,8));
        for(int i=0;i<8;i++)service.start("active-"+i,"g");
        for(int i=0;i<8;i++)service.reject("terminal-"+i,"g","busy");
        check(service.retainedEntries()==16,"registry must reach configured bound");
        require(service,"terminal-0"); // promote to MRU
        service.reject("terminal-new","g","busy");
        check(service.retainedEntries()==16,"registry must remain bounded");
        check(service.find("terminal-0").isPresent(),"recent terminal must be retained");
        check(service.find("terminal-1").isEmpty(),"least-recent terminal should be evicted first");
        for(int i=0;i<8;i++)check("RUNNING".equals(require(service,"active-"+i).status()),"active progress must never be evicted");
        for(int i=0;i<8;i++)service.complete("active-"+i,"g","done");
        check(service.activeEntries()==0,"all active entries must terminalize");
        check(service.retainedEntries()<=16,"terminalization must preserve bound");
    }

    private static void allActiveSaturationIsExplicit(){
        GrammarCompilationProgressService service=new GrammarCompilationProgressService(properties(16,16));
        for(int i=0;i<16;i++)service.start("slot-"+i,"g");
        check(service.activeEntries()==16,"all configured progress slots should be active");
        expectFailure(()->service.start("overflow","g"),"active-only saturation must reject a new RUNNING entry");
        GrammarCompilationProgress rejected=service.reject("capacity-reject","g","busy");
        check("FAILED".equals(rejected.status()),"capacity rejection still returns a terminal result");
        check(service.find("capacity-reject").isEmpty(),"capacity rejection must not evict active progress just to be observable");
        service.cancel("slot-0","g","cancelled");
        service.start("replacement","g");
        check(service.activeEntries()==16,"terminal slot should be recyclable after cancellation");
        check(service.find("slot-0").isEmpty(),"old terminal should be recycled before active entries");
    }

    private static void concurrentStageMonotonicity() throws Exception {
        GrammarCompilationProgressService service=new GrammarCompilationProgressService(properties(32,8));
        service.start("stage-race","g");
        ExecutorService pool=Executors.newFixedThreadPool(16);
        List<Future<?>> futures=new ArrayList<>();
        GrammarCompilationStage[] stages=GrammarCompilationStage.values();
        for(int i=0;i<2_000;i++){final int n=i;futures.add(pool.submit(()->service.stage("stage-race","g",stages[n%stages.length],n,"step-"+n)));}
        for(Future<?> f:futures)f.get(10,TimeUnit.SECONDS);
        pool.shutdown();check(pool.awaitTermination(5,TimeUnit.SECONDS),"stage race pool must terminate");
        GrammarCompilationProgress finalProgress=require(service,"stage-race");
        check(GrammarCompilationStage.PROMOTE_SNAPSHOT.name().equals(finalProgress.stage()),"concurrent stages must converge on furthest stage");
        check(finalProgress.completedStages()==finalProgress.totalStages(),"concurrent counter must remain bounded and monotonic");
        check("RUNNING".equals(finalProgress.status()),"stage-only race must remain running");
        service.ensureTerminal("stage-race","g","test cleanup");
        check(service.activeEntries()==0,"stage race cleanup must be terminal");
    }

    private static void terminalRaceIsOneWay() throws Exception {
        GrammarCompilationProgressService service=new GrammarCompilationProgressService(properties(32,8));
        service.start("terminal-race","g");
        ExecutorService pool=Executors.newFixedThreadPool(24);
        CountDownLatch start=new CountDownLatch(1);
        List<Future<?>> futures=new ArrayList<>();
        for(int i=0;i<600;i++){final int n=i;futures.add(pool.submit(()->{start.await();switch(n%4){case 0->service.complete("terminal-race","g","ok");case 1->service.fail("terminal-race","g",GrammarCompilationStage.SMOKE_TEST,9,"fail");case 2->service.cancel("terminal-race","g","cancel");default->service.stage("terminal-race","g",GrammarCompilationStage.REGISTER_ARTIFACTS,10,"late stage");}return null;}));}
        start.countDown();for(Future<?> f:futures)f.get(10,TimeUnit.SECONDS);
        pool.shutdown();check(pool.awaitTermination(5,TimeUnit.SECONDS),"terminal race pool must terminate");
        GrammarCompilationProgress terminal=require(service,"terminal-race");
        String status=terminal.status();
        check(!"RUNNING".equals(status),"terminal race must converge to terminal state");
        for(int i=0;i<100;i++){
            check(status.equals(service.stage("terminal-race","g",GrammarCompilationStage.FREEZE_DRAFT,0,"late").status()),"stage cannot revive terminal");
            check(status.equals(service.fail("terminal-race","g",GrammarCompilationStage.FREEZE_DRAFT,0,"late").status()),"fail cannot overwrite terminal");
            check(status.equals(service.cancel("terminal-race","g","late").status()),"cancel cannot overwrite terminal");
        }
        check(service.activeEntries()==0,"terminal race must leave no running progress");
    }

    private static void terminalChurnCannotCorruptActiveWork() throws Exception {
        GrammarCompilationProgressService service=new GrammarCompilationProgressService(properties(16,8));
        for(int i=0;i<8;i++)service.start("compile-"+i,"g");
        ExecutorService pool=Executors.newFixedThreadPool(12);
        CountDownLatch staged=new CountDownLatch(8);
        CountDownLatch allowComplete=new CountDownLatch(1);
        List<Future<?>> compilers=new ArrayList<>();
        for(int i=0;i<8;i++){final int id=i;compilers.add(pool.submit(()->{
            for(GrammarCompilationStage stage:GrammarCompilationStage.values())service.stage("compile-"+id,"g",stage,stage.ordinal(),stage.name());
            staged.countDown();
            allowComplete.await();
            service.complete("compile-"+id,"g","done");
            // Simula el finally real después de permitir churn terminal entre complete y ensureTerminal.
            Thread.yield();
            service.ensureTerminal("compile-"+id,"g","finally");
            return null;
        }));}
        check(staged.await(10,TimeUnit.SECONDS),"all active compilations must reach their last running stage");
        for(int i=0;i<20_000;i++){
            service.reject("noise-"+i,"g","capacity");
            if((i&255)==0)for(int a=0;a<8;a++)check(service.find("compile-"+a).isPresent()&&"RUNNING".equals(require(service,"compile-"+a).status()),"terminal churn must not evict active work");
        }
        check(service.activeEntries()==8,"all tracked compilations must remain active during terminal churn");
        allowComplete.countDown();
        Future<?> postChurn=pool.submit(()->{for(int i=0;i<20_000;i++)service.reject("post-noise-"+i,"g","capacity");});
        for(Future<?> f:compilers)f.get(20,TimeUnit.SECONDS);postChurn.get(20,TimeUnit.SECONDS);
        pool.shutdown();check(pool.awaitTermination(5,TimeUnit.SECONDS),"churn pool must terminate");
        check(service.retainedEntries()<=16,"heavy churn must respect absolute retention bound");
        check(service.activeEntries()==0,"heavy churn must leave zero logical activity");
        for(int i=0;i<2_000;i++)service.reject("post-"+i,"g","done");
        check(service.retainedEntries()<=16,"post-terminal churn must remain bounded");
        check(service.activeEntries()==0,"post-terminal churn cannot recreate RUNNING state");
        Optional<GrammarCompilationProgress> missing=service.ensureTerminal("compile-0","g","finally after LRU eviction");
        check(missing.isEmpty()||!"RUNNING".equals(missing.orElseThrow().status()),"finally after terminal eviction must be harmless");
    }
}
