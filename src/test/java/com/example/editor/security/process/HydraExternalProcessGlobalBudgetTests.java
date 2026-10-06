package com.example.editor.security.process;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HydraExternalProcessGlobalBudgetTests {
    @Test
    void rejectsRequestsAboveGlobalTimeoutAndOutputBudgets() {
        HydraExternalProcessSecurityProperties properties = new HydraExternalProcessSecurityProperties();
        properties.setMaximumProcessTimeoutSeconds(5);
        properties.setMaximumCapturedOutputBytes(1024);
        HydraExternalProcessPolicy policy = new HydraExternalProcessPolicy(properties);
        HydraExternalProcessRunRequest tooSlow = new HydraExternalProcessRunRequest(Path.of("."), List.of("java"), 6,
                Map.of(), 1000, 1, 0, 0, 1, 1);
        HydraExternalProcessRunRequest tooLarge = new HydraExternalProcessRunRequest(Path.of("."), List.of("java"), 5,
                Map.of(), 1025, 1, 0, 0, 1, 1);
        assertThatThrownBy(() -> policy.validateRuntimeBudget(tooSlow)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policy.validateRuntimeBudget(tooLarge)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void globalPermitCapacityIsBounded() throws Exception {
        HydraExternalProcessSecurityProperties properties = new HydraExternalProcessSecurityProperties();
        properties.setMaximumConcurrentProcesses(1);
        HydraExternalProcessPolicy policy = new HydraExternalProcessPolicy(properties);
        assertThat(policy.acquireGlobalProcessPermit(1)).isTrue();
        assertThat(policy.availableGlobalProcessPermits()).isZero();
        policy.releaseGlobalProcessPermit();
        assertThat(policy.availableGlobalProcessPermits()).isEqualTo(1);
    }
}
