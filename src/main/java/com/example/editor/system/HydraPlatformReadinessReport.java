package com.example.editor.system;

import java.util.List;

/** Estado operativo previo a publicación de la instalación HYDRA actual. */
public record HydraPlatformReadinessReport(String product, String status, int javaFeatureVersion, List<HydraPlatformReadinessCheck> checks) {
    public HydraPlatformReadinessReport {
        checks = checks == null ? List.of() : List.copyOf(checks);
    }

    /** Descripción: Obtiene la información correspondiente a «ready» dentro de HydraPlatformReadinessReport. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean ready() {
        return "READY".equals(status);
    }
}
