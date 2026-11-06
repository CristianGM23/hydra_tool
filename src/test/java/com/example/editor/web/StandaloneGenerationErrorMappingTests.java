package com.example.editor.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.example.editor.standalone.StandaloneEditorGenerationException;
import com.example.editor.standalone.StandaloneEditorGenerationException.Reason;

/** Una petición de generación standalone inválida (p. ej. sin paquete de capacidades) es un error del cliente: nunca un 500 genérico. */
class StandaloneGenerationErrorMappingTests {
    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test void reasonsMapToTheirHttpStatus() {
        // numeric codes: what the client receives (in Spring 7 HttpStatus.valueOf(413) resolves to CONTENT_TOO_LARGE, alias of PAYLOAD_TOO_LARGE)
        assertThat(status(Reason.INVALID_REQUEST)).isEqualTo(400);
        assertThat(status(Reason.DISABLED)).isEqualTo(503);
        assertThat(status(Reason.LIMIT_EXCEEDED)).isEqualTo(413);
        assertThat(status(Reason.GENERATION_FAILED)).isEqualTo(500);
        assertThat(handler.handleStandaloneEditorGenerationException(new StandaloneEditorGenerationException(Reason.INVALID_REQUEST, "A HydraCapabilityPackage is required"))
                .getBody()).extracting("message").isEqualTo("A HydraCapabilityPackage is required");
    }

    private int status(Reason reason) {
        return handler.handleStandaloneEditorGenerationException(new StandaloneEditorGenerationException(reason, "x")).getStatusCode().value();
    }
}
