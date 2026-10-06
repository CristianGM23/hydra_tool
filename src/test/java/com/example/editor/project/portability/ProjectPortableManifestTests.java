package com.example.editor.project.portability;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ProjectPortableManifestTests {
    @Test
    void refusesPortablePackagesThatClaimToContainSecrets() {
        assertThatThrownBy(() -> new ProjectPortableManifest("1.0", "4.32.107", "abc", 10, true, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
