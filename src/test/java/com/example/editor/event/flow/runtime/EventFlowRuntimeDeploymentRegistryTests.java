package com.example.editor.event.flow.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import com.example.editor.event.flow.EventFlowApplicationException;

class EventFlowRuntimeDeploymentRegistryTests {
    @Test void evictsOnlyInactiveDeploymentsWhenCapacityIsReached(){
        EventFlowRuntimeDeploymentRegistry registry = new EventFlowRuntimeDeploymentRegistry(2);
        EventFlowRuntimeDeployment first = new EventFlowRuntimeDeployment("first", null); first.updatedAt = Instant.EPOCH;
        EventFlowRuntimeDeployment second = new EventFlowRuntimeDeployment("second", null); second.updatedAt = Instant.parse("2026-01-01T00:00:00Z");
        registry.put("first", first); registry.put("second", second); registry.put("third", new EventFlowRuntimeDeployment("third", null));
        assertThat(registry.get("first")).isNull(); assertThat(registry.get("second")).isNotNull(); assertThat(registry.get("third")).isNotNull();
    }

    @Test void refusesToEvictActiveDeployments(){
        EventFlowRuntimeDeploymentRegistry registry = new EventFlowRuntimeDeploymentRegistry(1);
        EventFlowRuntimeDeployment active = new EventFlowRuntimeDeployment("active", null); active.active = true; registry.put("active", active);
        assertThatThrownBy(() -> registry.put("other", new EventFlowRuntimeDeployment("other", null)))
                .isInstanceOf(EventFlowApplicationException.class).hasMessageContaining("limit reached");
    }
}
