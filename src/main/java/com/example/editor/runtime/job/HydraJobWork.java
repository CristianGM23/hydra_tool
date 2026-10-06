package com.example.editor.runtime.job;

/** Trabajo cooperativo ejecutado mediante el orquestador transversal de HYDRA. */
@FunctionalInterface
public interface HydraJobWork {
    /** Descripción: Ejecuta la unidad de trabajo usando el contexto para progreso y cancelación. Entradas: context: contexto del trabajo. Salida: no devuelve valor y comunica fallos mediante excepción. */
    void run(HydraJobContext context) throws Exception;
}
