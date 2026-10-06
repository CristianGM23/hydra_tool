package com.example.editor.metamodel.layout.routing;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.editor.metamodel.design.MetamodelDiagramDocument;
import com.example.editor.metamodel.design.MetamodelDiagramDocument.EdgeDesign;
import com.example.editor.metamodel.design.MetamodelDiagramDocument.LabelPositionDesign;
import com.example.editor.metamodel.design.MetamodelDiagramDocument.RoutePointDesign;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprueba el contrato actual de enrutado del documento visual.
 */
class MetamodelDiagramRoutingContractTests {

    @Test
    void minimalEdgeUsesEmptyRouteDefaults() {
        EdgeDesign edge = new EdgeDesign("r", "right-source", "left-target");

        assertThat(edge.routePoints()).isEmpty();
        assertThat(edge.labelPosition()).isNull();
    }

    @Test
    void styledEdgeUsesEmptyRouteDefaults() {
        EdgeDesign edge = new EdgeDesign("r", "right-50", "left-50", "REFERENCE", "a", "b", "name", null);

        assertThat(edge.relationId()).isEqualTo("r");
        assertThat(edge.routePoints()).isEmpty();
    }

    @Test
    void normalizesNullCollections() {
        MetamodelDiagramDocument diagram = new MetamodelDiagramDocument("sample", null, null);
        EdgeDesign edge = new EdgeDesign("r", "right-50", "left-50", "REFERENCE", "a", "b", "", null, null, null);

        assertThat(diagram.nodes()).isEmpty();
        assertThat(diagram.edges()).isEmpty();
        assertThat(edge.routePoints()).isEmpty();
    }

    @Test
    void createsCopiesWithRouteAndLabel() {
        EdgeDesign original = new EdgeDesign("r", "right-50", "left-50", "REFERENCE", "a", "b", "name");
        List<RoutePointDesign> route = List.of(new RoutePointDesign(0, 0), new RoutePointDesign(100, 0));
        LabelPositionDesign label = new LabelPositionDesign(50, -8, 0);
        EdgeDesign result = original.withRoutePoints(route).withLabelPosition(label);

        assertThat(result.routePoints()).isEqualTo(route);
        assertThat(result.labelPosition()).isEqualTo(label);
        assertThat(result.style()).isEqualTo(original.style());
    }
}
