package com.example.editor.audit;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class AuditEntryValidationTests {
    @Test
    void rejectsIncompleteEntriesInsteadOfInventingHistoricalDefaults() {
        assertThatThrownBy(() -> new AuditEntry(null, null, null, null, null, null, null, null, null, null,
                null, null, false, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("eventId");
    }
}
