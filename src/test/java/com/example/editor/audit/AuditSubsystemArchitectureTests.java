package com.example.editor.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class AuditSubsystemArchitectureTests {
    @Test
    void acceptsACompletelyNewActivityDescriptorWithoutChangingTheCore() {
        AuditActivityDescriptor custom = new AuditActivityDescriptor("future.module.published", "future", AuditActions.APPLY,
                "Published {resource}", "Se publicó {resource}", "Future module", "Módulo futuro", AuditImportance.IMPORTANT);
        AuditActivityDescriptorRegistry registry = new AuditActivityDescriptorRegistry(List.of(() -> List.of(custom)));
        assertEquals(custom, registry.resolve(entry("future.module.published", "future", AuditActions.APPLY, "thing")));
    }

    @Test
    void genericDiffRemainsTheFallbackAndRedactsSensitiveFields() {
        AuditLogProperties properties = new AuditLogProperties();
        AuditRedactionRegistry redaction = new AuditRedactionRegistry(List.of(new ConfiguredAuditRedactionPolicy(properties)));
        GenericAuditDiffStrategy generic = new GenericAuditDiffStrategy(properties, redaction);
        AuditDiffStrategyRegistry registry = new AuditDiffStrategyRegistry(List.of(generic), redaction);
        AuditCapturedEvent captured = new AuditCapturedEvent("event", Instant.now(), new AuditActor("user", "User", "", Map.of()), "local",
                "future.module.updated", "future", AuditActions.UPDATE, AuditImportance.INFO, AuditResult.SUCCESS,
                new AuditResource("future.module", "thing", "thing", "Thing"),
                Map.of("name", "Before", "apiToken", "secret-a"), Map.of("name", "After", "apiToken", "secret-b"), Map.of(), "event", "");
        var diff = registry.diff(captured);
        AuditChange secret = diff.changes().stream().filter(change -> change.path().equals("apiToken")).findFirst().orElseThrow();
        assertEquals(AuditRedactionRegistry.REDACTED, secret.beforeValue());
        assertEquals(AuditRedactionRegistry.REDACTED, secret.afterValue());
        assertTrue(diff.changes().stream().anyMatch(change -> change.path().equals("name")));
    }

    @Test
    void exportContractIncludesHumanAndMachineReadableFormats() {
        assertEquals(List.of(AuditExportFormat.JSON, AuditExportFormat.CSV, AuditExportFormat.MARKDOWN, AuditExportFormat.TXT, AuditExportFormat.DOC),
                List.of(AuditExportFormat.values()));
        assertEquals("doc", AuditExportFormat.DOC.extension());
        assertEquals("application/msword", AuditExportFormat.DOC.mediaType());
    }

    private AuditEntry entry(String type, String module, String action, String id) {
        return new AuditEntry("1.0", "event", Instant.now(), new AuditActor("user", "User", "", Map.of()), "local", type, module, action,
                AuditImportance.INFO, AuditResult.SUCCESS, new AuditResource(module, "thing", id, "Thing"), List.of(), false, Map.of(), "event", "");
    }
}
