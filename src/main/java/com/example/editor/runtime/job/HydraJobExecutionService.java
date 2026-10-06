package com.example.editor.runtime.job;

import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Executor;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import com.example.editor.observability.HydraOperationTraceRegistry;
import org.springframework.stereotype.Service;
import org.slf4j.MDC;
import com.example.editor.audit.AuditRequestContext;

/** Ejecuta operaciones largas sobre el executor transversal y refleja siempre su estado en HydraJobOrchestrator. */
@Service
public class HydraJobExecutionService {
    private final HydraJobOrchestrator orchestrator;
    private final Executor executor;
    private final HydraOperationTraceRegistry traces;

    @Autowired
    /** Descripción: Inicializa el ejecutor global de trabajos con trazabilidad transversal. Entradas: orchestrator: registro; executor: executor; traces: trazas. Salida: servicio listo. */
    public HydraJobExecutionService(HydraJobOrchestrator orchestrator, @Qualifier("hydraJobExecutor") Executor executor, HydraOperationTraceRegistry traces) {
        this.orchestrator = orchestrator;
        this.executor = executor;
        this.traces = traces;
    }

    /** Descripción: Constructor compatible para pruebas sin contexto Spring. Entradas: orchestrator: registro; executor: executor. Salida: servicio listo con un registro local acotado. */
    public HydraJobExecutionService(HydraJobOrchestrator orchestrator, Executor executor) {
        this(orchestrator, executor, new HydraOperationTraceRegistry());
    }

    /** Descripción: Encola una operación larga con identificador generado. Entradas: type: tipo funcional; label: etiqueta visible; correlationId: correlación; work: trabajo cooperativo. Salida: instantánea QUEUED registrada. */
    public HydraJobSnapshot submit(String type, String label, String correlationId, HydraJobWork work) {
        return submit(UUID.randomUUID().toString(), type, label, correlationId, work);
    }

    /** Descripción: Encola una operación larga con identificador estable proporcionado por la capa llamadora. Entradas: id: identificador; type: tipo; label: etiqueta; correlationId: correlación; work: trabajo cooperativo. Salida: instantánea inicial. */
    public HydraJobSnapshot submit(String id, String type, String label, String correlationId, HydraJobWork work) {
        if (work == null) throw new IllegalArgumentException("HYDRA job work is required");
        String effectiveCorrelation = correlationId == null || correlationId.isBlank()
                ? (AuditRequestContext.active() ? AuditRequestContext.correlationId() : UUID.randomUUID().toString())
                : correlationId.trim();
        AtomicBoolean cancelled = new AtomicBoolean(false);
        AtomicReference<FutureTask<Void>> holder = new AtomicReference<>();
        FutureTask<Void> task = new FutureTask<>(() -> {
            HydraJobContext context = new HydraJobContext(id, orchestrator, cancelled);
            orchestrator.update(id, HydraJobStatus.RUNNING, 1, "RUNNING", "Job started");
            traces.record(effectiveCorrelation,"JOB","RUNNING",type+" "+id);
            try (AuditRequestContext.Scope ignored = AuditRequestContext.begin(effectiveCorrelation)) {
                MDC.put("hydraCorrelationId", effectiveCorrelation);
                context.throwIfCancellationRequested();
                work.run(context);
                context.throwIfCancellationRequested();
                orchestrator.complete(id, "Job completed");
                traces.record(effectiveCorrelation,"JOB","COMPLETED",type+" "+id);
            } catch (CancellationException exception) {
                cancelled.set(true);
                safeCancelSnapshot(id);
                traces.record(effectiveCorrelation,"JOB","CANCELLED",type+" "+id);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                cancelled.set(true);
                safeCancelSnapshot(id);
                traces.record(effectiveCorrelation,"JOB","CANCELLED",type+" "+id);
            } catch (Exception exception) {
                orchestrator.fail(id, "FAILED", safeMessage(exception));
                traces.record(effectiveCorrelation,"JOB","FAILED",type+" "+id+" "+safeMessage(exception));
            } finally {
                MDC.remove("hydraCorrelationId");
            }
            return null;
        });
        holder.set(task);
        HydraJobSnapshot snapshot = orchestrator.register(id, type, label, effectiveCorrelation, () -> {
            cancelled.set(true);
            FutureTask<Void> current = holder.get();
            if (current != null) current.cancel(true);
        });
        try {
            executor.execute(task);
            return snapshot;
        } catch (RejectedExecutionException exception) {
            orchestrator.fail(id, "REJECTED", "Global HYDRA job executor is saturated");
            throw exception;
        }
    }

    /** Descripción: Marca un trabajo como cancelado sin ocultar excepciones de carreras con finalización normal. Entradas: id: identificador del trabajo. Salida: no devuelve valor. */
    private void safeCancelSnapshot(String id) {
        try { orchestrator.cancelled(id, "Job cancelled"); }
        catch (RuntimeException ignored) { /* La instantánea puede haber quedado terminal en una carrera de finalización. */ }
    }

    /** Descripción: Convierte una excepción de trabajo en un mensaje corto y seguro. Entradas: exception: fallo capturado. Salida: mensaje no vacío. */
    private String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }
}
