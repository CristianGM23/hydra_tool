package com.example.editor.metamodel.export;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprueba los metadatos centralizados de formatos.
 */
class MetamodelExportFormatRegistryTests {

    @Test
    void assignsCategoriesToAllFormats() {
        for(MetamodelExportFormat format : MetamodelExportFormat.values()) {
            assertThat(format.category()).isNotNull();
        }
    }

    @Test
    void excludesBundleFromItsOwnDefaultContent() {
        assertThat(MetamodelExportFormat.BUNDLE.includedByDefault()).isFalse();
        assertThat(MetamodelExportFormat.TYPESCRIPT.category()).isEqualTo(MetamodelExportCategory.CODE);
        assertThat(MetamodelExportFormat.GO.category()).isEqualTo(MetamodelExportCategory.CODE);
    }

    @Test
    void keepsStableExternalIdentifiers() {
        assertThat(MetamodelExportFormat.fromId("markdown-agent")).isEqualTo(MetamodelExportFormat.MARKDOWN_AGENT);
        assertThat(MetamodelExportFormat.fromId("drawio")).isEqualTo(MetamodelExportFormat.DRAWIO);
    }
}
