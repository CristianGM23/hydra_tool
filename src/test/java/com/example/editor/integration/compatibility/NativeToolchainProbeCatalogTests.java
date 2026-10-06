package com.example.editor.integration.compatibility;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class NativeToolchainProbeCatalogTests {
    private final NativeToolchainProbeCatalog catalog = new NativeToolchainProbeCatalog();

    @Test
    void exposesVersionedServerOwnedProfilesForEverySupportedNativeFamily(){
        assertThat(catalog.entries()).extracting(NativeToolchainCatalogEntry::technology)
                .containsExactly("EMF", "Sirius Desktop", "Xtext", "Langium", "Eclipse GLSP", "BESSER SDK", "BESSER Web Modeling Editor");
        assertThat(catalog.entries()).allSatisfy(entry -> {
            assertThat(entry.key()).contains(":");
            assertThat(entry.commandIds()).isNotEmpty();
            assertThat(entry.description()).contains("without accepting arbitrary commands");
        });
    }

    @Test
    void selectsCatalogKeysInCallerOrderWithoutDuplicatingDefinitions(){
        List<NativeToolchainProbeDefinition> selected = catalog.select(List.of("langium:configured", "emf:2.39/2.42", "langium:configured"));
        assertThat(selected).extracting(NativeToolchainProbeDefinition::technology).containsExactly("Langium", "EMF");
    }

    @Test
    void rejectsAnythingThatIsNotAnAllowListedProfileKey(){
        assertThatThrownBy(() -> catalog.select(List.of("npm install malicious-package")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Unknown native toolchain profile");
    }
}
