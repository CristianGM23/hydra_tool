package com.example.editor.validation.runtime;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ValidationRuntimePropertiesTests {
    @Test
    void publishesSafeAndConfigurableDefaults() {
        ValidationRuntimeProperties properties = new ValidationRuntimeProperties();
        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.getMaxCachedPlans()).isEqualTo(256);
        assertThat(properties.isAllowPartialPlanExecution()).isTrue();
        assertThat(properties.isDrawioEnabled()).isTrue();
    }

    @Test
    void rejectsAnUnboundedZeroSizedCache() {
        ValidationRuntimeProperties properties = new ValidationRuntimeProperties();
        assertThatThrownBy(() -> properties.setMaxCachedPlans(0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void allowsRuntimeAndDrawioToBeDisabledIndependently() {
        ValidationRuntimeProperties properties = new ValidationRuntimeProperties();
        properties.setEnabled(false);
        properties.setDrawioEnabled(false);
        properties.setAllowPartialPlanExecution(false);
        assertThat(properties.isEnabled()).isFalse();
        assertThat(properties.isDrawioEnabled()).isFalse();
        assertThat(properties.isAllowPartialPlanExecution()).isFalse();
    }
}
