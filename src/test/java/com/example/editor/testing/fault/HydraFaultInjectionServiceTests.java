package com.example.editor.testing.fault;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.util.Set;
import org.junit.jupiter.api.Test;

class HydraFaultInjectionServiceTests {
    @Test
    void remainsInertUntilExplicitlyEnabled() {
        HydraFaultInjectionProperties properties = new HydraFaultInjectionProperties();
        properties.setActivePoints(Set.of("PROJECT_PORTABLE_EXPORT"));
        HydraFaultInjectionService service = new HydraFaultInjectionService(properties);
        assertThatCode(() -> service.check(HydraFaultInjectionService.Point.PROJECT_PORTABLE_EXPORT)).doesNotThrowAnyException();
        properties.setEnabled(true);
        assertThatThrownBy(() -> service.check(HydraFaultInjectionService.Point.PROJECT_PORTABLE_EXPORT))
                .isInstanceOf(HydraFaultInjectionService.HydraInjectedFaultException.class);
    }
}
