package com.example.editor.metamodel.design;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprueba los metadatos visuales de las relaciones del metamodelo.
 */
class MetamodelDiagramDocumentTests {

    /**
     * Descripción:
     * Comprueba la compatibilidad con el constructor anterior.
     */
    @Test
    void createsMinimalEdgeWithoutAdditionalMetadata() {
        MetamodelDiagramDocument.EdgeDesign edge = new MetamodelDiagramDocument.EdgeDesign("reference-1", "right-source", "left-target");

        assertThat(edge.kind()).isNull();
        assertThat(edge.sourceClassId()).isNull();
        assertThat(edge.targetClassId()).isNull();
    }

    /**
     * Descripción:
     * Comprueba los datos necesarios para dependencias y agregaciones.
     */
    @Test
    void storesExtendedRelationshipMetadata() {
        MetamodelDiagramDocument.EdgeDesign edge = new MetamodelDiagramDocument.EdgeDesign("dependency-1", "bottom-source", "top-target", "DEPENDENCY", "class-a", "class-b", "uses");

        assertThat(edge.kind()).isEqualTo("DEPENDENCY");
        assertThat(edge.sourceClassId()).isEqualTo("class-a");
        assertThat(edge.targetClassId()).isEqualTo("class-b");
        assertThat(edge.label()).isEqualTo("uses");
    }
}
