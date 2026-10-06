package com.example.editor.metamodel.export;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprueba la normalización de opciones.
 */
class MetamodelExportOptionsTests {

    @Test
    void raisesScaleToMinimum() {
        MetamodelExportOptions options = new MetamodelExportOptions(true, true, true, false, 0, "");

        assertThat(options.imageScale()).isEqualTo(1);
    }

    @Test
    void limitsScaleToMaximum() {
        MetamodelExportOptions options = new MetamodelExportOptions(true, true, true, false, 20, "");

        assertThat(options.imageScale()).isEqualTo(4);
    }

    @Test
    void normalizesNullPackageName() {
        MetamodelExportOptions options = new MetamodelExportOptions(true, true, true, false, 2, null);

        assertThat(options.codePackageName()).isEmpty();
    }

    @Test
    void providesStableDefaults() {
        MetamodelExportOptions options = MetamodelExportOptions.defaults();

        assertThat(options.includeLayout()).isTrue();
        assertThat(options.includeStyles()).isTrue();
        assertThat(options.imageScale()).isEqualTo(2);
    }
}
