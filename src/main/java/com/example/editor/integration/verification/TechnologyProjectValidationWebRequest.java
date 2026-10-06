package com.example.editor.integration.verification;

import java.util.Map;

/** Parámetros REST para validar una exportación con o sin toolchain nativo. */
public record TechnologyProjectValidationWebRequest(TechnologyValidationMode mode, int timeoutSeconds,
        boolean keepWorkspace, Map<String, String> environment) {
    public TechnologyProjectValidationWebRequest {
        mode = mode == null ? TechnologyValidationMode.STRUCTURAL : mode;
        if(timeoutSeconds <= 0){timeoutSeconds = 180;}
        environment = environment == null ? Map.of() : Map.copyOf(environment);
    }
}
