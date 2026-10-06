package com.example.editor.observability;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class HydraOperationTraceRegistryTests {
    @Test
    void filtersByCorrelationAndBoundsDetailText() {
        HydraOperationTraceRegistry registry = new HydraOperationTraceRegistry();
        registry.record("corr-a", "HTTP", "START", "line1\nline2");
        registry.record("corr-b", "JOB", "RUNNING", "other");
        assertThat(registry.find("corr-a")).hasSize(1);
        assertThat(registry.find("corr-a").getFirst().detail()).isEqualTo("line1 line2");
    }
}
