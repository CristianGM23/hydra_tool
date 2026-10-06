package com.example.editor.integration.verification;

import java.nio.file.Path;
import java.util.Map;

/** Solicitud inmutable para validar un proyecto tecnológico exportado. */
public record TechnologyProjectValidationRequest(TechnologyValidationMode mode, Path workspaceDirectory,
        int timeoutSeconds, boolean keepWorkspace, Map<String, String> environment) {
    public TechnologyProjectValidationRequest {
        mode = mode == null ? TechnologyValidationMode.STRUCTURAL : mode;
        if(timeoutSeconds <= 0){timeoutSeconds = 180;}
        environment = environment == null ? Map.of() : Map.copyOf(environment);
    }

    /** Descripción: Ejecuta la operación «structural» correspondiente a la responsabilidad de TechnologyProjectValidationRequest. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public static TechnologyProjectValidationRequest structural(){
        return new TechnologyProjectValidationRequest(TechnologyValidationMode.STRUCTURAL, null, 180, false, Map.of());
    }
}
