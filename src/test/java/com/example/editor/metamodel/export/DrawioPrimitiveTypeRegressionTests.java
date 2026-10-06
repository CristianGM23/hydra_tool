package com.example.editor.metamodel.export;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Comprueba la nomenclatura de tipos primitivos usada por draw.io. */
class DrawioPrimitiveTypeRegressionTests {

    @Test
    void exportsStringAsEString() {
        String result = new DrawioMetamodelExporter().export(CanonicalExportTestFixtures.context());

        assertThat(result).contains("+ name : EString [1]");
    }
}
