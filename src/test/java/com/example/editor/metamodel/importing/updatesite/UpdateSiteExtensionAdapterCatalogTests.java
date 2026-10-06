package com.example.editor.metamodel.importing.updatesite;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UpdateSiteExtensionAdapterCatalogTests {
    private final UpdateSiteExtensionAdapterCatalog catalog = new UpdateSiteExtensionAdapterCatalog();

    @Test
    void exposesSafeServerOwnedRulesForCommonCustomExtensionFamilies() {
        assertThat(catalog.descriptors()).extracting(UpdateSiteExtensionAdapterDescriptor::id)
                .contains("custom-palettes", "custom-representations", "custom-mappings",
                        "custom-importers", "custom-generators", "custom-operations",
                        "custom-quickfixes", "custom-forms");
        assertThat(catalog.descriptors()).allSatisfy(adapter -> {
            assertThat(adapter.enabled()).isTrue();
            assertThat(adapter.purpose()).isNotBlank();
            assertThat(adapter.neutralTarget()).isNotBlank();
        });
    }

    @Test
    void matchesExtensionPointAndElementWithoutExecutingPluginCode() {
        assertThat(catalog.match("org.example.language.importers", "importer"))
                .get().extracting(UpdateSiteExtensionAdapterDescriptor::id)
                .isEqualTo("custom-importers");
        assertThat(catalog.match("org.example.unknown", "customThing")).isEmpty();
    }
}
