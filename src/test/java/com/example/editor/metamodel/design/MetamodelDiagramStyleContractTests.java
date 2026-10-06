package com.example.editor.metamodel.design;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.editor.metamodel.design.MetamodelDiagramDocument.EdgeDesign;
import com.example.editor.metamodel.design.MetamodelDiagramDocument.EdgeStyleDesign;
import com.example.editor.metamodel.design.MetamodelDiagramDocument.NodeDesign;
import com.example.editor.metamodel.design.MetamodelDiagramDocument.NodeStyleDesign;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprueba el contrato actual del documento visual con estilos.
 */
class MetamodelDiagramStyleContractTests {

    /**
     * Descripción:
     * Comprueba estilos opcionales y valores visuales explícitos.
     */
    @Test
    void supportsOptionalAndExplicitVisualStyles() {
        NodeDesign plainNode = new NodeDesign("class-1", 10, 20, 200, 100);
        NodeStyleDesign nodeStyle = new NodeStyleDesign("#FFFFFF", "#E2E8F0", "#475569", "#0F172A", "Arial", 12.0, 600.0, 2.0, 5.0);
        NodeDesign styledNode = new NodeDesign("class-2", 250, 20, 200, 100, nodeStyle);
        EdgeDesign plainEdge = new EdgeDesign("reference-1", "right-source", "left-target");
        EdgeStyleDesign edgeStyle = new EdgeStyleDesign("#334155", "#1F2937", "Arial", 11.0, 600.0, 3.0, "SOLID");
        EdgeDesign styledEdge = new EdgeDesign("reference-2", "right-source", "left-target", "REFERENCE", "class-1", "class-2", "target [0..1]", edgeStyle);
        MetamodelDiagramDocument document = new MetamodelDiagramDocument("sample", List.of(plainNode, styledNode), List.of(plainEdge, styledEdge));

        assertThat(document.nodes().get(0).style()).isNull();
        assertThat(document.nodes().get(1).style().fillColor()).isEqualTo("#FFFFFF");
        assertThat(document.edges().get(0).style()).isNull();
        assertThat(document.edges().get(1).style().strokeWidth()).isEqualTo(3.0);
    }
}
