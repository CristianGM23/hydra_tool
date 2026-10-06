package com.example.editor.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

class AuditRetentionDeletionTests {
    @Test
    void appliesShortRetentionToNavigationWithoutShorteningPersistentChanges() {
        AuditLogProperties properties = new AuditLogProperties();
        AuditRetentionPolicy policy = new AuditRetentionPolicy(properties);
        assertEquals(7, policy.retentionDays(entry("navigation.module.changed", AuditActions.NAVIGATE, AuditImportance.INFO)));
        assertEquals(14, policy.retentionDays(entry("editor.preview.opened", AuditActions.OPEN, AuditImportance.INFO)));
        assertEquals(60, policy.retentionDays(entry("validation.executed", AuditActions.VALIDATE, AuditImportance.INFO)));
        assertEquals(180, policy.retentionDays(entry("metamodel.updated", AuditActions.UPDATE, AuditImportance.INFO)));
    }

    @Test
    void warningAndSecurityEntriesKeepMaximumRetentionEvenWhenTheyAreNavigation() {
        AuditLogProperties properties = new AuditLogProperties();
        AuditRetentionPolicy policy = new AuditRetentionPolicy(properties);
        assertEquals(180, policy.retentionDays(entry("navigation.module.changed", AuditActions.NAVIGATE, AuditImportance.WARNING)));
        assertEquals(180, policy.retentionDays(entry("ui.view.closed", AuditActions.CLOSE, AuditImportance.SECURITY)));
    }

    @Test
    void storagePressureEvictsNavigationBeforeValuableChanges() {
        AuditLogProperties properties = new AuditLogProperties();
        AuditRetentionPolicy policy = new AuditRetentionPolicy(properties);
        assertEquals(0, policy.evictionPriority(entry("ui.tab.changed", AuditActions.NAVIGATE, AuditImportance.INFO)));
        assertEquals(3, policy.evictionPriority(entry("ui.tab.changed", AuditActions.NAVIGATE, AuditImportance.WARNING)));
        assertEquals(1, policy.evictionPriority(entry("editor.preview.opened", AuditActions.OPEN, AuditImportance.INFO)));
        assertEquals(2, policy.evictionPriority(entry("validation.executed", AuditActions.VALIDATE, AuditImportance.INFO)));
        assertEquals(3, policy.evictionPriority(entry("metamodel.updated", AuditActions.UPDATE, AuditImportance.IMPORTANT)));
    }

    @Test
    void filteredDeletionCannotAccidentallyBecomeDeleteAll() {
        assertThrows(IllegalArgumentException.class, () -> new AuditDeletionCriteria("local", Set.of("user"), AuditDeletionMode.FILTERED,
                Set.of(), Set.of(), "", null, null));
        new AuditDeletionCriteria("local", Set.of("user"), AuditDeletionMode.FILTERED, Set.of("metamodel"), Set.of(), "", null, null);
        new AuditDeletionCriteria("local", Set.of("user"), AuditDeletionMode.ALL, Set.of(), Set.of(), "", null, null);
    }

    @Test
    void defaultsKeepAuditStorageBounded() {
        AuditLogProperties properties = new AuditLogProperties();
        assertEquals(134217728L, properties.getMaximumStorageBytes());
        assertEquals(7, properties.getNavigationRetentionDays());
        assertEquals(14, properties.getOpenRetentionDays());
        assertEquals(60, properties.getRoutineRetentionDays());
        assertEquals(180, properties.getRetentionDays());
    }

    private AuditEntry entry(String activityType, String action, AuditImportance importance) {
        return new AuditEntry("1.0", "event", Instant.now(), new AuditActor("user", "User", "", Map.of()), "local", activityType, "module", action,
                importance, AuditResult.SUCCESS, new AuditResource("module", "thing", "thing", "Thing"), List.of(), false, Map.of(), "event", "");
    }
}
