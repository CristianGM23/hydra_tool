package com.example.editor.runtime.job;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Bounded retention for the cross-layer HYDRA job registry. */
@ConfigurationProperties(prefix = "hydra.jobs")
public class HydraJobProperties {
    private int maximumRetained = 256;
    private int maximumConcurrent = 4;
    private int maximumQueued = 64;
    /** Descripción: Obtiene la información correspondiente a «getMaximumRetained» dentro de HydraJobProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public int getMaximumRetained() { return maximumRetained; }
    /** Descripción: Actualiza el estado gestionado por HydraJobProperties mediante la operación «setMaximumRetained». Entradas: value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setMaximumRetained(int value) { maximumRetained = Math.max(16, value); }

    /** Descripción: Obtiene el máximo de trabajos que pueden ejecutarse simultáneamente en el executor transversal. Entradas: no requiere parámetros. Salida: límite de concurrencia configurado. */
    public int getMaximumConcurrent() { return maximumConcurrent; }

    /** Descripción: Configura el máximo de trabajos simultáneos manteniendo un rango operativo seguro. Entradas: value: límite solicitado. Salida: no devuelve valor; actualiza la configuración. */
    public void setMaximumConcurrent(int value) { maximumConcurrent = Math.max(1, Math.min(32, value)); }

    /** Descripción: Obtiene la capacidad máxima de la cola global de trabajos. Entradas: no requiere parámetros. Salida: número máximo de trabajos en espera. */
    public int getMaximumQueued() { return maximumQueued; }

    /** Descripción: Configura la capacidad de la cola global con un límite superior defensivo. Entradas: value: capacidad solicitada. Salida: no devuelve valor; actualiza la configuración. */
    public void setMaximumQueued(int value) { maximumQueued = Math.max(1, Math.min(4096, value)); }
}
