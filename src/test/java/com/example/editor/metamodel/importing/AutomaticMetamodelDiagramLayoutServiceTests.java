package com.example.editor.metamodel.importing;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.example.editor.metamodel.core.MetaClass;
import com.example.editor.metamodel.core.MetaClassKind;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.core.MetaRelation;
import com.example.editor.metamodel.core.MetaRelationKind;
import com.example.editor.metamodel.core.Multiplicity;
import com.example.editor.metamodel.design.MetamodelDiagramDocument;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprueba el layout automático y la eliminación de estilos.
 */
class AutomaticMetamodelDiagramLayoutServiceTests {

    private final MetamodelImportProperties properties = new MetamodelImportProperties();
    private final AutomaticMetamodelDiagramLayoutService service = new AutomaticMetamodelDiagramLayoutService(null, properties, new MetamodelImportRoutingPolicy(properties));

    @Test
    void createsGridNodesAndEdges() {
        MetaModel model = model();
        var diagram = service.layout(model);
        assertThat(diagram.nodes()).hasSize(2);
        assertThat(diagram.edges()).singleElement().satisfies(edge -> {
            assertThat(edge.kind()).isEqualTo("REFERENCE");
            assertThat(edge.sourceHandle()).isEqualTo("right-50");
        });
    }

    @Test
    void honorsConfiguredColumnCount() {
        properties.setLayoutColumns(1);
        var diagram = service.layout(model());
        assertThat(diagram.nodes().get(1).y()).isGreaterThan(diagram.nodes().get(0).y());
    }


    @Test
    void removesNodeAndEdgeStyles() {
        MetamodelDiagramDocument.NodeStyleDesign nodeStyle = new MetamodelDiagramDocument.NodeStyleDesign("#fff", "#eee", "#000", "#000", "Arial", 12.0, 400.0, 2.0, 4.0);
        MetamodelDiagramDocument.EdgeStyleDesign edgeStyle = new MetamodelDiagramDocument.EdgeStyleDesign("#000", "#000", "Arial", 12.0, 400.0, 2.0, "DASHED");
        MetamodelDiagramDocument diagram = new MetamodelDiagramDocument("sample", List.of(new MetamodelDiagramDocument.NodeDesign("class-a", 0, 0, 200, 100, nodeStyle)), List.of(new MetamodelDiagramDocument.EdgeDesign("relation-a", "right-50", "left-50", "REFERENCE", "class-a", "class-b", "b", edgeStyle)));
        var result = service.withoutStyles(diagram);
        assertThat(result.nodes().get(0).style()).isNull();
        assertThat(result.edges().get(0).style()).isNull();
    }

    private MetaModel model() {
        MetaClass first = new MetaClass("class-a", "A", MetaClassKind.CLASS, List.of(), List.of(), Map.of());
        MetaClass second = new MetaClass("class-b", "B", MetaClassKind.CLASS, List.of(), List.of(), Map.of());
        MetaRelation relation = new MetaRelation("relation-a-b", "b", "class-a", "class-b", MetaRelationKind.REFERENCE, null, Multiplicity.optional(), "", true, true, Map.of());
        return new MetaModel(MetaModel.CURRENT_SCHEMA_VERSION, "sample", "Sample", List.of(first, second), List.of(), List.of(), List.of(relation), Map.of());
    }
}
