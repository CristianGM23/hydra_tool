import com.example.editor.security.process.HydraProcessTreeTerminator;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Executable, dependency-free stress harness for HYDRA native process-tree completion. */
public final class HydraProcessTreeTerminationStressHarness {
    private static final AtomicInteger ASSERTIONS = new AtomicInteger();
    private static final String JAVA = Path.of(System.getProperty("java.home"), "bin",
            System.getProperty("os.name", "").toLowerCase().contains("win") ? "java.exe" : "java").toString();
    private static final String CP = System.getProperty("java.class.path");

    public static void main(String[] args) throws Exception {
        if (args.length > 0) {
            childMode(args);
            return;
        }
        HydraProcessTreeTerminator terminator = new HydraProcessTreeTerminator();
        check(terminator.terminate(null, Duration.ZERO, Duration.ZERO, 1).terminated(), "null process is terminal");

        Process alreadyDone = child("leaf", "1");
        alreadyDone.waitFor(5, TimeUnit.SECONDS);
        var doneReport = terminator.terminate(alreadyDone, Duration.ofMillis(10), Duration.ofMillis(20), 2);
        check(doneReport.terminated(), "already completed root remains terminal");
        check(doneReport.survivingProcesses() == 0, "already completed root has no survivors");

        treeTermination(terminator, 8);
        interruptionPreserved(terminator);
        raceSpawner(terminator, 150);
        trackedOrphans(terminator, 24);
        fastExitTrackedOrphans(terminator, 16, 3);
        concurrentTrackedOrphans(terminator, 20, 3);
        concurrentTrees(terminator, 24, 4);
        repeatedIdempotence(terminator, 100);

        System.out.println("HYDRA_PROCESS_TREE_TERMINATION_STRESS=" + ASSERTIONS.get() + "/" + ASSERTIONS.get() + " PASS");
    }

    private static void treeTermination(HydraProcessTreeTerminator terminator, int children) throws Exception {
        Path pids = Files.createTempFile("hydra-tree-pids-", ".txt");
        Process root = child("tree", pids.toString(), Integer.toString(children));
        waitForLines(pids, children, 5000);
        List<Long> ids = readPids(pids);
        var report = terminator.terminate(root, Duration.ofMillis(150), Duration.ofMillis(750), 6);
        check(report.terminated(), "tree termination reports terminal");
        check(!root.isAlive(), "tree root is dead");
        for (long pid : ids) check(!alive(pid), "tree descendant " + pid + " is dead");
        Files.deleteIfExists(pids);
    }

    private static void interruptionPreserved(HydraProcessTreeTerminator terminator) throws Exception {
        Process root = child("leaf", "30000");
        Thread.currentThread().interrupt();
        var report = terminator.terminate(root, Duration.ofMillis(50), Duration.ofMillis(300), 4);
        check(report.interrupted(), "interruption is recorded");
        check(Thread.currentThread().isInterrupted(), "caller interruption flag is restored");
        check(!root.isAlive(), "interrupted caller still completes cleanup");
        Thread.interrupted();
    }

    private static void raceSpawner(HydraProcessTreeTerminator terminator, int requestedChildren) throws Exception {
        Path pids = Files.createTempFile("hydra-spawner-pids-", ".txt");
        Process root = child("spawner", pids.toString(), Integer.toString(requestedChildren));
        waitForLines(pids, Math.min(10, requestedChildren), 5000);
        var report = terminator.terminate(root, Duration.ofMillis(100), Duration.ofMillis(900), 12);
        Thread.sleep(150);
        List<Long> ids = readPids(pids);
        check(report.terminated(), "racing spawner reports terminal");
        check(!root.isAlive(), "racing spawner root is dead");
        check(ids.stream().noneMatch(HydraProcessTreeTerminationStressHarness::alive),
                "every racing descendant observed before shutdown is dead");
        Files.deleteIfExists(pids);
    }


    private static void trackedOrphans(HydraProcessTreeTerminator terminator, int children) throws Exception {
        Path pids = Files.createTempFile("hydra-orphan-pids-", ".txt");
        Process root = child("orphan", pids.toString(), Integer.toString(children), "250");
        var tracker = terminator.track(root, Duration.ofMillis(2));
        waitForLines(pids, children, 5000);
        check(root.waitFor(5, TimeUnit.SECONDS), "tracked orphan root exits normally");
        List<Long> ids = readPids(pids);
        check(ids.size() == children, "tracker orphan fixture created every child");
        var report = terminator.terminateTracked(tracker, Duration.ofMillis(100), Duration.ofMillis(750), 6);
        check(report.terminated(), "tracked descendants terminate after root exit");
        check(report.observedProcesses() >= children + 1, "tracker retained descendants after root exit");
        for (long pid : ids) check(!alive(pid), "tracked orphan descendant " + pid + " is dead");
        Files.deleteIfExists(pids);
    }


    private static void fastExitTrackedOrphans(HydraProcessTreeTerminator terminator, int roots, int children) throws Exception {
        for (int round = 0; round < roots; round++) {
            Path pids = Files.createTempFile("hydra-fast-orphan-pids-", ".txt");
            Process root = child("orphan", pids.toString(), Integer.toString(children), "20");
            var tracker = terminator.track(root, Duration.ofMillis(1));
            waitForLines(pids, children, 5000);
            check(root.waitFor(5, TimeUnit.SECONDS), "fast orphan root exits " + round);
            var report = terminator.terminateTracked(tracker, Duration.ofMillis(50), Duration.ofMillis(500), 6);
            check(report.terminated(), "fast orphan tree terminates " + round);
            check(readPids(pids).stream().noneMatch(HydraProcessTreeTerminationStressHarness::alive),
                    "fast orphan descendants are dead " + round);
            Files.deleteIfExists(pids);
        }
    }

    private static void concurrentTrackedOrphans(HydraProcessTreeTerminator terminator, int roots, int children) throws Exception {
        List<Path> pidFiles = new ArrayList<>();
        List<Process> processes = new ArrayList<>();
        List<HydraProcessTreeTerminator.ProcessTreeTracker> trackers = new ArrayList<>();
        for (int i = 0; i < roots; i++) {
            Path file = Files.createTempFile("hydra-concurrent-orphan-pids-", ".txt");
            pidFiles.add(file);
            Process root = child("orphan", file.toString(), Integer.toString(children), "300");
            processes.add(root);
            trackers.add(terminator.track(root, Duration.ofMillis(2)));
        }
        for (Path file : pidFiles) waitForLines(file, children, 7000);
        for (Process process : processes) check(process.waitFor(5, TimeUnit.SECONDS), "concurrent orphan root exits");
        ExecutorService pool = Executors.newFixedThreadPool(Math.min(roots, 10));
        List<Callable<HydraProcessTreeTerminator.TerminationReport>> tasks = new ArrayList<>();
        for (var tracker : trackers) tasks.add(() -> terminator.terminateTracked(tracker, Duration.ofMillis(75), Duration.ofMillis(600), 6));
        List<Future<HydraProcessTreeTerminator.TerminationReport>> futures = pool.invokeAll(tasks, 20, TimeUnit.SECONDS);
        pool.shutdownNow();
        for (int i = 0; i < roots; i++) {
            check(futures.get(i).get().terminated(), "concurrent tracked orphan tree terminates " + i);
            for (long pid : readPids(pidFiles.get(i))) check(!alive(pid), "concurrent tracked orphan descendant is dead");
            Files.deleteIfExists(pidFiles.get(i));
        }
    }

    private static void concurrentTrees(HydraProcessTreeTerminator terminator, int roots, int children) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(Math.min(roots, 12));
        List<Path> pidFiles = new ArrayList<>();
        List<Process> processes = new ArrayList<>();
        for (int i = 0; i < roots; i++) {
            Path file = Files.createTempFile("hydra-concurrent-pids-", ".txt");
            pidFiles.add(file);
            processes.add(child("tree", file.toString(), Integer.toString(children)));
        }
        for (Path file : pidFiles) waitForLines(file, children, 7000);
        List<Callable<HydraProcessTreeTerminator.TerminationReport>> tasks = new ArrayList<>();
        for (Process process : processes) tasks.add(() -> terminator.terminate(process, Duration.ofMillis(75), Duration.ofMillis(600), 6));
        List<Future<HydraProcessTreeTerminator.TerminationReport>> futures = pool.invokeAll(tasks, 20, TimeUnit.SECONDS);
        pool.shutdownNow();
        for (int i = 0; i < roots; i++) {
            check(futures.get(i).get().terminated(), "concurrent tree " + i + " terminates");
            check(!processes.get(i).isAlive(), "concurrent root " + i + " is dead");
            for (long pid : readPids(pidFiles.get(i))) check(!alive(pid), "concurrent descendant is dead");
            Files.deleteIfExists(pidFiles.get(i));
        }
    }

    private static void repeatedIdempotence(HydraProcessTreeTerminator terminator, int count) throws Exception {
        for (int i = 0; i < count; i++) {
            Process process = child("leaf", "30000");
            var first = terminator.terminate(process, Duration.ZERO, Duration.ofMillis(200), 3);
            var second = terminator.terminate(process, Duration.ZERO, Duration.ZERO, 1);
            check(first.terminated(), "idempotence first termination " + i);
            check(second.terminated(), "idempotence second termination " + i);
            check(!process.isAlive(), "idempotence process dead " + i);
        }
    }

    private static void childMode(String[] args) throws Exception {
        switch (args[0]) {
            case "leaf" -> Thread.sleep(Long.parseLong(args[1]));
            case "tree" -> {
                Path file = Path.of(args[1]);
                int count = Integer.parseInt(args[2]);
                List<Process> children = new ArrayList<>();
                for (int i = 0; i < count; i++) {
                    Process child = child("leaf", "30000");
                    children.add(child);
                    appendPid(file, child.pid());
                }
                for (Process child : children) child.waitFor();
            }
            case "spawner" -> {
                Path file = Path.of(args[1]);
                int count = Integer.parseInt(args[2]);
                List<Process> children = new ArrayList<>();
                for (int i = 0; i < count; i++) {
                    Process child = child("leaf", "30000");
                    children.add(child);
                    appendPid(file, child.pid());
                    Thread.sleep(5);
                }
                for (Process child : children) child.waitFor();
            }
            case "orphan" -> {
                Path file = Path.of(args[1]);
                int count = Integer.parseInt(args[2]);
                long holdMillis = Long.parseLong(args[3]);
                for (int i = 0; i < count; i++) {
                    Process child = child("leaf", "30000");
                    appendPid(file, child.pid());
                }
                Thread.sleep(holdMillis);
            }
            default -> throw new IllegalArgumentException("Unknown child mode: " + args[0]);
        }
    }

    private static Process child(String... childArgs) throws IOException {
        List<String> command = new ArrayList<>();
        command.add(JAVA); command.add("-cp"); command.add(CP); command.add(HydraProcessTreeTerminationStressHarness.class.getName());
        command.addAll(List.of(childArgs));
        return new ProcessBuilder(command).redirectErrorStream(true).start();
    }

    private static synchronized void appendPid(Path file, long pid) throws IOException {
        Files.writeString(file, Long.toString(pid) + System.lineSeparator(), StandardCharsets.UTF_8,
                java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
    }

    private static void waitForLines(Path file, int count, long timeoutMillis) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        while (System.nanoTime() < deadline) {
            if (readPids(file).size() >= count) return;
            Thread.sleep(20);
        }
        throw new AssertionError("Timed out waiting for child PIDs: expected " + count + ", got " + readPids(file).size());
    }

    private static List<Long> readPids(Path file) throws IOException {
        if (!Files.exists(file)) return List.of();
        List<Long> result = new ArrayList<>();
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) if (!line.isBlank()) result.add(Long.parseLong(line.trim()));
        return result;
    }

    private static boolean alive(long pid) { return ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false); }
    private static void check(boolean condition, String message) { ASSERTIONS.incrementAndGet(); if (!condition) throw new AssertionError(message); }
}
