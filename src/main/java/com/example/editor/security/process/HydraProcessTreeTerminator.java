package com.example.editor.security.process;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Terminates a native process and the descendants observed while it is being shut down.
 * The implementation is bounded, idempotent and preserves the caller interruption flag.
 */
public final class HydraProcessTreeTerminator {
    private static final long MAX_WAIT_MILLIS = 30_000L;
    private static final long MAX_OBSERVATION_MILLIS = 1_000L;
    private static final int MAX_PASSES = 16;
    private static final AtomicLong TRACKER_SEQUENCE = new AtomicLong();

    /**
     * Starts lightweight observation of a process tree while its root is alive. This is useful because
     * some operating systems re-parent descendants immediately after the root exits, making them
     * undiscoverable through {@link ProcessHandle#descendants()} afterwards.
     */
    /** Descripción: Ejecuta la operación «track» correspondiente a la responsabilidad de ProcessTreeTracker. Entradas: process: dato de entrada necesario para la operación; observationInterval: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ProcessTreeTracker track(Process process, Duration observationInterval) {
        return new ProcessTreeTracker(process, boundedObservationMillis(observationInterval));
    }

    /**
     * Attempts an orderly tree shutdown and escalates to forced termination when necessary.
     *
     * @param process root process; {@code null} is treated as an already-finished tree
     * @param gracefulWait maximum time allocated to orderly termination
     * @param forcedWait maximum time allocated after forceful termination
     * @param maximumPasses number of bounded re-scan passes used to catch newly spawned children
     * @return immutable termination evidence
     */
    /** Descripción: Ejecuta la operación «terminate» correspondiente a la responsabilidad de ProcessTreeTracker. Entradas: process: dato de entrada necesario para la operación; gracefulWait: dato de entrada necesario para la operación; forcedWait: dato de entrada necesario para la operación; maximumPasses: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public TerminationReport terminate(Process process, Duration gracefulWait, Duration forcedWait, int maximumPasses) {
        if (process == null) {
            return new TerminationReport(0, 0, 0, 0, false, true);
        }
        ProcessTreeTracker tracker = track(process, Duration.ofMillis(1));
        return terminate(process, process.toHandle(), tracker.snapshot(), gracefulWait, forcedWait, maximumPasses, tracker);
    }

    /**
     * Terminates a tracked tree, including descendants observed before the root exited.
     */
    /** Descripción: Ejecuta la operación «terminateTracked» correspondiente a la responsabilidad de ProcessTreeTracker. Entradas: tracker: dato de entrada necesario para la operación; gracefulWait: dato de entrada necesario para la operación; forcedWait: dato de entrada necesario para la operación; maximumPasses: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public TerminationReport terminateTracked(ProcessTreeTracker tracker, Duration gracefulWait, Duration forcedWait, int maximumPasses) {
        if (tracker == null || tracker.root() == null) {
            return new TerminationReport(0, 0, 0, 0, false, true);
        }
        tracker.refresh();
        return terminate(tracker.rootProcess(), tracker.root(), tracker.snapshot(), gracefulWait, forcedWait, maximumPasses, tracker);
    }

    /** Descripción: Ejecuta la operación «terminate» manteniendo una observación viva del árbol durante el cierre para no perder descendientes creados entre un muestreo y la salida del proceso raíz. Entradas: rootProcess: proceso raíz; root: handle raíz; seed: procesos ya observados; gracefulWait: espera ordenada; forcedWait: espera forzada; maximumPasses: máximo de barridos; tracker: observador vivo opcional. Salida: devuelve evidencia inmutable del cierre. */ private TerminationReport terminate(Process rootProcess, ProcessHandle root, Map<Long, ProcessHandle> seed, Duration gracefulWait,
            Duration forcedWait, int maximumPasses, ProcessTreeTracker tracker) {
        boolean interrupted = Thread.interrupted();
        Map<Long, ProcessHandle> observed = new LinkedHashMap<>(seed == null ? Map.of() : seed);
        int gracefulSignals = 0;
        int forcedSignals = 0;
        int passes = Math.max(1, Math.min(MAX_PASSES, maximumPasses));
        long gracefulMillis = boundedMillis(gracefulWait);
        long forcedMillis = boundedMillis(forcedWait);
        try {
            for (int pass = 0; pass < passes; pass++) {
                refreshObserved(root, observed, tracker);
                gracefulSignals += signal(observed, root.pid(), false);
                if (rootAlive(rootProcess, root)) {
                    try {
                        rootProcess.destroy();
                        gracefulSignals++;
                    } catch (RuntimeException ignored) {
                        // Continue with the forced pass below.
                    }
                }
                interrupted |= await(observed, gracefulMillis, rootProcess, root, tracker);
                refreshObserved(root, observed, tracker);
                if (!anyAlive(observed, rootProcess, root)) {
                    break;
                }
                forcedSignals += signal(observed, root.pid(), true);
                if (rootAlive(rootProcess, root)) {
                    try {
                        rootProcess.destroyForcibly();
                        forcedSignals++;
                    } catch (RuntimeException ignored) {
                        // Final bounded sweep still handles observed descendants.
                    }
                }
                interrupted |= await(observed, forcedMillis, rootProcess, root, tracker);
                refreshObserved(root, observed, tracker);
                if (!anyAlive(observed, rootProcess, root)) {
                    break;
                }
            }
            // Freeze the live observer only after the bounded shutdown passes, merge its last sample,
            // then force one final sweep over every PID seen during the complete termination window.
            if (tracker != null) {
                tracker.refresh();
                tracker.stopAndAwait();
                observed.putAll(tracker.snapshot());
            }
            forcedSignals += signal(observed, root.pid(), true);
            if (rootAlive(rootProcess, root)) {
                try {
                    rootProcess.destroyForcibly();
                    forcedSignals++;
                } catch (RuntimeException ignored) {
                    // Evidence below reports a survivor if the root still cannot be terminated.
                }
            }
            interrupted |= await(observed, forcedMillis, rootProcess, root, null);
            long survivors = observed.values().stream()
                    .filter(handle -> handle.pid() != root.pid())
                    .filter(ProcessHandle::isAlive).count()
                    + (rootAlive(rootProcess, root) ? 1 : 0);
            return new TerminationReport(observed.size(), gracefulSignals, forcedSignals, survivors, interrupted, survivors == 0);
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /** Descripción: Ejecuta la operación «collect» correspondiente a la responsabilidad de ProcessTreeTracker. Entradas: root: ruta o ubicación utilizada por la operación; observed: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private static void collect(ProcessHandle root, Map<Long, ProcessHandle> observed) {
        if (root == null) {
            return;
        }
        observed.put(root.pid(), root);
        if (!root.isAlive()) {
            return;
        }
        try {
            root.descendants().forEach(handle -> observed.put(handle.pid(), handle));
        } catch (RuntimeException ignored) {
            // The process can disappear between isAlive() and descendants(); previously observed
            // handles remain available for the final sweep.
        }
    }

    /** Descripción: Ejecuta la operación «signal» correspondiente a la responsabilidad de ProcessTreeTracker. Entradas: observed: dato de entrada necesario para la operación; rootPid: identificador que delimita el recurso objetivo; forcibly: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static int signal(Map<Long, ProcessHandle> observed, long rootPid, boolean forcibly) {
        List<ProcessHandle> handles = new ArrayList<>(observed.values());
        // Descendants first. PID is used only for deterministic ordering; the root is handled last.
        handles.sort(Comparator.comparingLong(ProcessHandle::pid).reversed());
        int signals = 0;
        for (ProcessHandle handle : handles) {
            if (handle.pid() == rootPid || !handle.isAlive()) {
                continue;
            }
            try {
                boolean accepted = forcibly ? handle.destroyForcibly() : handle.destroy();
                if (accepted) {
                    signals++;
                }
            } catch (RuntimeException ignored) {
                // Best effort; a later pass may still observe/terminate the same process.
            }
        }
        return signals;
    }

    /** Descripción: Espera de forma acotada mientras continúa incorporando los descendientes observados por el tracker y por el root aún vivo. Entradas: observed: procesos conocidos; waitMillis: tiempo máximo; rootProcess: proceso raíz; root: handle raíz; tracker: observador vivo opcional. Salida: indica si el hilo llamante fue interrumpido durante la espera. */ private static boolean await(Map<Long, ProcessHandle> observed, long waitMillis, Process rootProcess, ProcessHandle root, ProcessTreeTracker tracker) {
        refreshObserved(root, observed, tracker);
        if (waitMillis <= 0 || !anyAlive(observed, rootProcess, root)) {
            return false;
        }
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(waitMillis);
        boolean interrupted = false;
        while (anyAlive(observed, rootProcess, root)) {
            refreshObserved(root, observed, tracker);
            long remainingNanos = deadline - System.nanoTime();
            if (remainingNanos <= 0) {
                break;
            }
            long sleepMillis = Math.max(1L, Math.min(10L, TimeUnit.NANOSECONDS.toMillis(remainingNanos)));
            try {
                Thread.sleep(sleepMillis);
            } catch (InterruptedException exception) {
                interrupted = true;
                // Clear the flag temporarily so cleanup can continue; it is restored by terminate().
            }
        }
        refreshObserved(root, observed, tracker);
        return interrupted;
    }

    /** Descripción: Fusiona en el mapa de cierre los descendientes visibles desde el root y cualquier PID capturado por el observador concurrente. Entradas: root: handle raíz; observed: mapa acumulado; tracker: observador opcional. Salida: no devuelve valor; amplía únicamente la evidencia de procesos conocida. */ private static void refreshObserved(ProcessHandle root, Map<Long, ProcessHandle> observed, ProcessTreeTracker tracker) {
        collect(root, observed);
        if (tracker != null) {
            tracker.refresh();
            observed.putAll(tracker.snapshot());
        }
    }

    /** Descripción: Ejecuta la operación «anyAlive» correspondiente a la responsabilidad de ProcessTreeTracker. Entradas: observed: dato de entrada necesario para la operación; rootProcess: dato de entrada necesario para la operación; root: ruta o ubicación utilizada por la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private static boolean anyAlive(Map<Long, ProcessHandle> observed, Process rootProcess, ProcessHandle root) {
        if (rootAlive(rootProcess, root)) {
            return true;
        }
        long rootPid = root == null ? Long.MIN_VALUE : root.pid();
        return observed.values().stream().filter(handle -> handle.pid() != rootPid).anyMatch(ProcessHandle::isAlive);
    }

    /** Descripción: Ejecuta la operación «rootAlive» correspondiente a la responsabilidad de ProcessTreeTracker. Entradas: process: dato de entrada necesario para la operación; root: ruta o ubicación utilizada por la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private static boolean rootAlive(Process process, ProcessHandle root) {
        if (process != null) {
            try {
                return process.isAlive();
            } catch (RuntimeException ignored) {
                // Fall through to ProcessHandle only if the Process implementation cannot answer.
            }
        }
        return root != null && root.isAlive();
    }

    /** Descripción: Ejecuta la operación «boundedMillis» correspondiente a la responsabilidad de ProcessTreeTracker. Entradas: duration: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static long boundedMillis(Duration duration) {
        if (duration == null || duration.isNegative()) {
            return 0L;
        }
        long millis;
        try {
            millis = duration.toMillis();
        } catch (ArithmeticException exception) {
            millis = MAX_WAIT_MILLIS;
        }
        return Math.max(0L, Math.min(MAX_WAIT_MILLIS, millis));
    }

    /** Descripción: Ejecuta la operación «boundedObservationMillis» correspondiente a la responsabilidad de ProcessTreeTracker. Entradas: duration: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static long boundedObservationMillis(Duration duration) {
        if (duration == null || duration.isNegative() || duration.isZero()) {
            return 10L;
        }
        long millis;
        try {
            millis = duration.toMillis();
        } catch (ArithmeticException exception) {
            millis = MAX_OBSERVATION_MILLIS;
        }
        return Math.max(1L, Math.min(MAX_OBSERVATION_MILLIS, millis));
    }

    /**
     * Live, bounded observation of one native process tree. The watcher is a virtual thread and therefore
     * does not consume a platform thread while sleeping between samples.
     */
    public static final class ProcessTreeTracker implements AutoCloseable {
        private final Process rootProcess;
        private final ProcessHandle root;
        private final long observationMillis;
        private final Map<Long, ProcessHandle> observed = new LinkedHashMap<>();
        private final AtomicBoolean active = new AtomicBoolean(true);
        private final Thread watcher;

        /** Descripción: Inicializa una instancia de ProcessTreeTracker con sus dependencias y datos de entrada. Entradas: process: dato de entrada necesario para la operación; observationMillis: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ private ProcessTreeTracker(Process process, long observationMillis) {
            this.rootProcess = process;
            this.root = process == null ? null : process.toHandle();
            this.observationMillis = observationMillis;
            refresh();
            if (root == null) {
                active.set(false);
                watcher = null;
            } else {
                watcher = Thread.ofVirtual().name("hydra-process-tree-tracker-" + TRACKER_SEQUENCE.incrementAndGet()).start(this::watch);
            }
        }

        /** Descripción: Ejecuta la operación «watch» correspondiente a la responsabilidad de ProcessTreeTracker. Entradas: no requiere parámetros de entrada. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void watch() {
            try {
                while (active.get()) {
                    refresh();
                    if (!rootAlive(rootProcess, root)) {
                        break;
                    }
                    try {
                        Thread.sleep(observationMillis);
                    } catch (InterruptedException exception) {
                        if (!active.get()) {
                            break;
                        }
                    }
                }
                refresh();
            } finally {
                active.set(false);
            }
        }

        private synchronized void refresh() {
            if (root != null) {
                collect(root, observed);
            }
        }

        private synchronized Map<Long, ProcessHandle> snapshot() {
            return new LinkedHashMap<>(observed);
        }

        /** Descripción: Ejecuta la operación «root» correspondiente a la responsabilidad de ProcessTreeTracker. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private ProcessHandle root() {
            return root;
        }

        /** Descripción: Ejecuta la operación «rootProcess» correspondiente a la responsabilidad de ProcessTreeTracker. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private Process rootProcess() {
            return rootProcess;
        }

        /** Number of currently alive descendants/root handles already observed. */
        public synchronized long aliveObservedProcesses() {
            long rootPid = root == null ? Long.MIN_VALUE : root.pid();
            return observed.values().stream().filter(handle -> handle.pid() != rootPid).filter(ProcessHandle::isAlive).count()
                    + (rootAlive(rootProcess, root) ? 1 : 0);
        }

        /** Stops observation without terminating the native process itself. */
        /** Descripción: Ejecuta la operación «stop» correspondiente a la responsabilidad de ProcessTreeTracker. Entradas: no requiere parámetros de entrada. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void stop() {
            active.set(false);
            if (watcher != null) {
                watcher.interrupt();
            }
        }

        /**
         * Stops observation and waits only long enough for the watcher to publish its final bounded refresh.
         * The caller interruption flag is preserved, but an interruption never skips process cleanup.
         */
        /** Descripción: Ejecuta la operación «stopAndAwait» correspondiente a la responsabilidad de ProcessTreeTracker. Entradas: no requiere parámetros de entrada. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void stopAndAwait() {
            boolean interrupted = Thread.interrupted();
            stop();
            if (watcher != null && watcher != Thread.currentThread()) {
                long waitMillis = Math.min(MAX_OBSERVATION_MILLIS + 250L, observationMillis + 250L);
                long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(waitMillis);
                while (watcher.isAlive()) {
                    long remaining = deadline - System.nanoTime();
                    if (remaining <= 0L) {
                        break;
                    }
                    try {
                        watcher.join(Math.max(1L, Math.min(50L, TimeUnit.NANOSECONDS.toMillis(remaining))));
                    } catch (InterruptedException exception) {
                        interrupted = true;
                    }
                }
            }
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }

        /** Descripción: Elimina o libera el recurso gestionado por «close» de forma controlada. Entradas: no requiere parámetros de entrada. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ @Override public void close() {
            stop();
        }
    }

    /** Evidence returned after a termination attempt. */
    public record TerminationReport(int observedProcesses, int gracefulSignals, int forcedSignals,
            long survivingProcesses, boolean interrupted, boolean terminated) {
    }
}
