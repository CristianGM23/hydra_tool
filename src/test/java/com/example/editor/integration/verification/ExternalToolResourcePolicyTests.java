package com.example.editor.integration.verification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class ExternalToolResourcePolicyTests {
    @Test
    void exposesAndClampsTheProcessOutputBudget() {
        TechnologyProjectValidationProperties properties = new TechnologyProjectValidationProperties();
        assertEquals(2 * 1024 * 1024, properties.getMaximumProcessOutputBytes());
        properties.setMaximumProcessOutputBytes(1);
        assertEquals(1024, properties.getMaximumProcessOutputBytes());
    }

    @Test
    void keepsOtherResourceLimitsPositive() {
        TechnologyProjectValidationProperties properties = new TechnologyProjectValidationProperties();
        properties.setDefaultTimeoutSeconds(0);
        properties.setMaximumFiles(0);
        properties.setMaximumTotalBytes(0);
        assertEquals(1, properties.getDefaultTimeoutSeconds());
        assertEquals(1, properties.getMaximumFiles());
        assertEquals(1L, properties.getMaximumTotalBytes());
    }
}
