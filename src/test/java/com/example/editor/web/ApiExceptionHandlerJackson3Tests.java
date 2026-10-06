package com.example.editor.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import tools.jackson.core.JacksonException;

class ApiExceptionHandlerJackson3Tests {

    /** Exposes the protected Jackson 3 constructor only inside this test. */
    private static final class TestJacksonException extends JacksonException {
        private TestJacksonException(String message) {
            super(message);
        }
    }

    @Test
    void extractsAPropertyFromJacksonThree() {
        JacksonException exception = new TestJacksonException("invalid");
        exception.prependPath(this, "id");

        assertThat(ApiExceptionHandler.jacksonPath(exception)).isEqualTo("id");
    }

    @Test
    void extractsNestedPropertiesAndIndexesFromJacksonThree() {
        JacksonException exception = new TestJacksonException("invalid");
        exception.prependPath(this, "id");
        exception.prependPath(this, 0);
        exception.prependPath(this, "rules");

        assertThat(ApiExceptionHandler.jacksonPath(exception)).isEqualTo("rules[0].id");
    }

    @Test
    void returnsAnEmptyPathWhenJacksonDidNotReportOne() {
        assertThat(ApiExceptionHandler.jacksonPath(null)).isEmpty();
        assertThat(ApiExceptionHandler.jacksonPath(new TestJacksonException("invalid"))).isEmpty();
    }
    @Test
    void mapsNoSuchElementToNotFound() {
        var response = new ApiExceptionHandler().handleNoSuchElementException(new java.util.NoSuchElementException("Grammar not found: missing"));

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("NOT_FOUND");
    }

}
