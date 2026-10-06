package com.example.editor.runtime;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Stress ejecutable del lifecycle del executor común de E/S bloqueante. */
public final class HydraBlockingIoLifecycleStressHarness {
    private static int checks;
    private HydraBlockingIoLifecycleStressHarness() { }

    public static void main(String[] args) throws Exception {
        for(int round=0; round<20; round++) runRound(round);
        System.out.println("HYDRA_BLOCKING_IO_LIFECYCLE_STRESS="+checks+"/"+checks+" PASS");
    }

    private static void runRound(int round) throws Exception {
        ExecutorService executor = new HydraBlockingIoConfiguration().hydraBlockingIoExecutor();
        CountDownLatch entered = new CountDownLatch(64);
        AtomicInteger interrupted = new AtomicInteger();
        List<Runnable> tasks = new ArrayList<>();
        for(int i=0;i<64;i++) tasks.add(() -> {
            entered.countDown();
            try { Thread.sleep(Duration.ofSeconds(30)); }
            catch(InterruptedException exception) { interrupted.incrementAndGet(); Thread.currentThread().interrupt(); }
        });
        tasks.forEach(executor::submit);
        check(entered.await(3, TimeUnit.SECONDS), "round "+round+" tasks entered");
        long started = System.nanoTime();
        executor.shutdownNow();
        check(executor.awaitTermination(3, TimeUnit.SECONDS), "round "+round+" executor terminates");
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started);
        check(elapsedMillis < 3000, "round "+round+" shutdown bounded");
        check(interrupted.get()==64, "round "+round+" all active tasks interrupted");
        check(blockingThreads()==0, "round "+round+" no blocking I/O virtual threads remain");
    }

    private static long blockingThreads() {
        return Thread.getAllStackTraces().keySet().stream().filter(Thread::isAlive)
                .filter(t -> t.getName().startsWith("hydra-blocking-io-")).count();
    }

    private static void check(boolean ok,String message){checks++; if(!ok) throw new AssertionError(message);}
}
