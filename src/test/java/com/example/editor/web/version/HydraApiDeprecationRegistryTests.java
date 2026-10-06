package com.example.editor.web.version;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class HydraApiDeprecationRegistryTests {
    @Test
    void startsWithoutInventingDeprecatedRoutes() {
        HydraApiDeprecationRegistry registry = new HydraApiDeprecationRegistry();
        assertThat(registry.entries()).isEmpty();
    }
}
