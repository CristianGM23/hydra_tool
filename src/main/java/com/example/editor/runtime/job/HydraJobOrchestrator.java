package com.example.editor.runtime.job;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Shared bounded registry for progress, cancellation and diagnostics of long-running operations. */
@Service
public final class HydraJobOrchestrator {
    private final Map<String, Entry> jobs = new ConcurrentHashMap<>();
    private final int maximumRetained;
    private final Clock clock;

    @Autowired
    /** Descripción: Inicializa una instancia de HydraJobOrchestrator con sus dependencias y datos de entrada. Entradas: properties: configuración que gobierna el comportamiento. Salida: deja la instancia inicializada y lista para su uso. */ public HydraJobOrchestrator(HydraJobProperties properties) {
        this(properties, Clock.systemUTC());
    }

    /** Descripción: Inicializa una instancia de HydraJobOrchestrator con sus dependencias y datos de entrada. Entradas: properties: configuración que gobierna el comportamiento; clock: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public HydraJobOrchestrator(HydraJobProperties properties, Clock clock) {
        this.maximumRetained = properties == null ? 256 : properties.getMaximumRetained();
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    public synchronized HydraJobSnapshot register(String id, String type, String label, String correlationId, Runnable cancellation) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Job id is required");
        makeRoomForRegistration();
        if (jobs.size() >= maximumRetained) {
            throw new IllegalStateException("HYDRA job registry capacity reached: " + maximumRetained);
        }
        Instant now = clock.instant();
        Entry candidate = new Entry(new HydraJobSnapshot(id, type, label, HydraJobStatus.QUEUED, 0, "QUEUED", "",
                correlationId, now, now, cancellation != null), cancellation);
        Entry previous = jobs.putIfAbsent(id, candidate);
        if (previous != null) throw new IllegalStateException("HYDRA job already exists: " + id);
        trim();
        return candidate.snapshot;
    }

    /** Descripción: Actualiza el estado gestionado por Entry mediante la operación «update». Entradas: id: identificador que delimita el recurso objetivo; status: dato de entrada necesario para la operación; progress: dato de entrada necesario para la operación; phase: dato de entrada necesario para la operación; message: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public HydraJobSnapshot update(String id, HydraJobStatus status, int progress, String phase, String message) {
        Entry entry = require(id);
        synchronized (entry) {
            HydraJobSnapshot current = entry.snapshot;
            if (terminal(current.status())) return current;
            entry.snapshot = new HydraJobSnapshot(current.id(), current.type(), current.label(), status, progress, phase,
                    message, current.correlationId(), current.createdAt(), clock.instant(), current.cancellable());
            return entry.snapshot;
        }
    }

    /** Descripción: Ejecuta la operación «complete» correspondiente a la responsabilidad de Entry. Entradas: id: identificador que delimita el recurso objetivo; message: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public HydraJobSnapshot complete(String id, String message) { return update(id, HydraJobStatus.COMPLETED, 100, "COMPLETED", message); }
    /** Descripción: Ejecuta la operación «fail» correspondiente a la responsabilidad de Entry. Entradas: id: identificador que delimita el recurso objetivo; phase: dato de entrada necesario para la operación; message: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public HydraJobSnapshot fail(String id, String phase, String message) { return update(id, HydraJobStatus.FAILED, progress(id), phase, message); }

    /** Descripción: Evalúa la condición «cancel» sin modificar el estado externo. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve un indicador que expresa el resultado de la condición. */ public HydraJobSnapshot cancel(String id) {
        Entry entry = require(id);
        Runnable cancellation;
        synchronized (entry) {
            if (terminal(entry.snapshot.status())) return entry.snapshot;
            cancellation = entry.cancellation;
            if (cancellation == null) throw new IllegalStateException("HYDRA job is not cancellable: " + id);
        }
        cancellation.run();
        return cancelled(id, "Cancelled by user");
    }

    /** Descripción: Publica el estado CANCELLED sin volver a invocar el callback de cancelación, útil para finalización cooperativa dentro del propio worker. Entradas: id: identificador; message: detalle. Salida: instantánea terminal. */
    public HydraJobSnapshot cancelled(String id, String message) {
        Entry entry = require(id);
        synchronized (entry) {
            HydraJobSnapshot current = entry.snapshot;
            if (current.status() == HydraJobStatus.COMPLETED || current.status() == HydraJobStatus.FAILED) return current;
            entry.snapshot = new HydraJobSnapshot(current.id(), current.type(), current.label(), HydraJobStatus.CANCELLED,
                    current.progress(), "CANCELLED", message, current.correlationId(), current.createdAt(), clock.instant(), current.cancellable());
            return entry.snapshot;
        }
    }

    /** Descripción: Obtiene la información correspondiente a «find» dentro de Entry. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public HydraJobSnapshot find(String id) { return require(id).snapshot; }

    /** Descripción: Obtiene la información correspondiente a «list» dentro de Entry. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public List<HydraJobSnapshot> list() {
        trim();
        return jobs.values().stream().map(entry -> entry.snapshot)
                .sorted(Comparator.comparing(HydraJobSnapshot::updatedAt).reversed()).toList();
    }

    /** Descripción: Ejecuta la operación «capacityStatus» correspondiente a la responsabilidad de Entry. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public CapacityStatus capacityStatus() {
        trim();
        long active = jobs.values().stream().filter(entry -> !terminal(entry.snapshot.status())).count();
        return new CapacityStatus(jobs.size(), maximumRetained, active, jobs.size() - active);
    }

    /** Descripción: Ejecuta la operación «progress» correspondiente a la responsabilidad de Entry. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private int progress(String id) { return require(id).snapshot.progress(); }
    /** Descripción: Valida las condiciones necesarias para «require» y rechaza entradas no válidas. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private Entry require(String id) {
        Entry entry = jobs.get(id);
        if (entry == null) throw new IllegalArgumentException("HYDRA job was not found: " + id);
        return entry;
    }
    /** Descripción: Ejecuta la operación «terminal» correspondiente a la responsabilidad de Entry. Entradas: status: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private static boolean terminal(HydraJobStatus status) {
        return status == HydraJobStatus.COMPLETED || status == HydraJobStatus.FAILED || status == HydraJobStatus.CANCELLED;
    }
    /** Descripción: Ejecuta la operación «trim» correspondiente a la responsabilidad de Entry. Entradas: no requiere parámetros de entrada. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void trim() { evictOldestTerminal(jobs.size() - maximumRetained); }

    /** Descripción: Ejecuta la operación «makeRoomForRegistration» correspondiente a la responsabilidad de Entry. Entradas: no requiere parámetros de entrada. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void makeRoomForRegistration() {
        evictOldestTerminal(jobs.size() - maximumRetained + 1);
    }

    /** Descripción: Ejecuta la operación «evictOldestTerminal» correspondiente a la responsabilidad de Entry. Entradas: count: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void evictOldestTerminal(int count) {
        if (count <= 0) return;
        List<Entry> terminalEntries = jobs.values().stream().filter(entry -> terminal(entry.snapshot.status()))
                .sorted(Comparator.comparing(entry -> entry.snapshot.updatedAt())).toList();
        int remaining = count;
        for (Entry entry : terminalEntries) {
            if (remaining-- <= 0) break;
            jobs.remove(entry.snapshot.id(), entry);
        }
    }

    public record CapacityStatus(int retainedJobs, int maximumRetainedJobs, long activeJobs, long terminalJobs) {
        /** Descripción: Ejecuta la operación «saturated» correspondiente a la responsabilidad de Entry. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean saturated() { return retainedJobs >= maximumRetainedJobs; }
    }

    private static final class Entry {
        private volatile HydraJobSnapshot snapshot;
        private final Runnable cancellation;
        /** Descripción: Inicializa una instancia de Entry con sus dependencias y datos de entrada. Entradas: snapshot: dato de entrada necesario para la operación; cancellation: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ private Entry(HydraJobSnapshot snapshot, Runnable cancellation) { this.snapshot = snapshot; this.cancellation = cancellation; }
    }
}
