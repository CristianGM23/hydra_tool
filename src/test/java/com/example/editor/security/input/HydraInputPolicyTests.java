package com.example.editor.security.input;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HydraInputPolicyTests {
    @Test
    void acceptsNormalPlainText() {
        assertDoesNotThrow(() -> policy().plainText("title", "Safe model", 128));
    }

    @Test
    void rejectsExecutableMarkupFromPlainText() {
        assertThrows(HydraInputValidationException.class,
                () -> policy().plainText("description", "<script>alert(1)</script>", 512));
    }

    @Test
    void rejectsTraversalSyntaxFromIdentifiers() {
        assertThrows(HydraInputValidationException.class,
                () -> policy().identifier("id", "../outside", 128));
    }

    @Test
    void acceptsAbsoluteHttpsUrlsWithoutCredentials() {
        assertDoesNotThrow(() -> policy().httpsUrl("repositoryUrl", "https://example.org/team/project.git"));
    }

    @Test
    void rejectsEmbeddedUrlCredentials() {
        assertThrows(HydraInputValidationException.class,
                () -> policy().httpsUrl("repositoryUrl", "https://user:secret@example.org/project.git"));
    }

    @Test
    void rejectsForbiddenControlCharacters() {
        assertThrows(HydraInputValidationException.class,
                () -> policy().plainText("title", "bad\u0001value", 128));
    }

    @Test
    void rejectsPayloadsPastTheNodeBudget() {
        HydraInputSecurityProperties properties = new HydraInputSecurityProperties();
        properties.setMaxPayloadNodes(8);
        HydraInputPolicy policy = new HydraInputPolicy(properties);
        List<Integer> values = new ArrayList<>();
        for(int index = 0; index < 20; index++) values.add(index);
        assertThrows(HydraInputValidationException.class,
                () -> policy.validatePayload(Map.of("values", values)));
    }

    private HydraInputPolicy policy() {
        return new HydraInputPolicy(new HydraInputSecurityProperties());
    }
}
