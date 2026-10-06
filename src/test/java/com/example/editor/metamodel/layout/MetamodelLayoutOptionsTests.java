package com.example.editor.metamodel.layout;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprueba la normalización de opciones.
 */
class MetamodelLayoutOptionsTests {

    @Test
    void normalizesAlgorithmDirectionAndFixedIds() {
        MetamodelLayoutOptions options = new MetamodelLayoutOptions(" Layered ", "top_to_bottom", true, true, true, null);
        assertThat(options.algorithm()).isEqualTo("layered");
        assertThat(options.direction()).isEqualTo("TOP_TO_BOTTOM");
        assertThat(options.fixedElementIds()).isEqualTo(List.of());
    }

    @Test
    void readsDefaultsFromProperties() {
        MetamodelLayoutProperties properties = new MetamodelLayoutProperties();
        properties.setDefaultDirection("TOP_TO_BOTTOM");
        properties.setPreserveStyles(false);
        MetamodelLayoutOptions options = MetamodelLayoutOptions.defaults(properties);
        assertThat(options.direction()).isEqualTo("TOP_TO_BOTTOM");
        assertThat(options.preserveStyles()).isFalse();
    }
}
