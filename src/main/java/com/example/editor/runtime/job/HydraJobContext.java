package com.example.editor.runtime.job;

import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;

/** Contexto cooperativo de una operación larga gestionada por HYDRA. */
public final class HydraJobContext {
    private final String jobId;
    private final HydraJobOrchestrator orchestrator;
    private final AtomicBoolean cancellationRequested;

    /** Descripción: Inicializa el contexto cooperativo de un trabajo. Entradas: jobId: identificador; orchestrator: registro compartido; cancellationRequested: señal de cancelación. Salida: deja el contexto preparado. */
    HydraJobContext(String jobId, HydraJobOrchestrator orchestrator, AtomicBoolean cancellationRequested) {
        this.jobId = jobId;
        this.orchestrator = orchestrator;
        this.cancellationRequested = cancellationRequested;
    }

    /** Descripción: Publica progreso y fase para el trabajo actual. Entradas: progress: porcentaje 0..100; phase: fase lógica; message: mensaje visible. Salida: devuelve la instantánea actualizada. */
    public HydraJobSnapshot progress(int progress, String phase, String message) {
        return orchestrator.update(jobId, HydraJobStatus.RUNNING, progress, phase, message);
    }

    /** Descripción: Indica si se ha solicitado la cancelación cooperativa del trabajo. Entradas: no requiere parámetros. Salida: true cuando el trabajo debe detenerse cuanto antes. */
    public boolean cancellationRequested() { return cancellationRequested.get() || Thread.currentThread().isInterrupted(); }

    /** Descripción: Interrumpe cooperativamente la operación cuando existe una solicitud de cancelación. Entradas: no requiere parámetros. Salida: no devuelve valor; lanza CancellationException si procede. */
    public void throwIfCancellationRequested() {
        if (cancellationRequested()) throw new CancellationException("HYDRA job cancellation requested: " + jobId);
    }

    /** Descripción: Devuelve el identificador estable del trabajo gestionado. Entradas: no requiere parámetros. Salida: identificador del trabajo. */
    public String jobId() { return jobId; }
}
