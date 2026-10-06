package com.example.editor.metamodel.layout.routing;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.editor.metamodel.core.MetaRelation;
import com.example.editor.metamodel.design.MetamodelDiagramDocument;
import com.example.editor.metamodel.design.MetamodelDiagramDocument.EdgeDesign;
import com.example.editor.metamodel.design.MetamodelDiagramDocument.NodeDesign;
import com.example.editor.metamodel.design.MetamodelDiagramDocument.RoutePointDesign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Comprueba registros, métricas y el pipeline.
 */
class MetamodelRoutingMetricsAndPipelineTests {

    @Test
    void countsCrossings() {
        EdgeDesign horizontal = edge("h", "a", "b", List.of(new RoutePointDesign(0, 50), new RoutePointDesign(100, 50)));
        EdgeDesign vertical = edge("v", "c", "d", List.of(new RoutePointDesign(50, 0), new RoutePointDesign(50, 100)));
        var diagram = new MetamodelDiagramDocument("sample", List.of(), List.of(horizontal, vertical));

        assertThat(new MetamodelDiagramMetricsService().measure(diagram).crossings()).isEqualTo(1);
    }

    @Test
    void countsEdgeNodeIntersections() {
        EdgeDesign edge = edge("r", "a", "b", List.of(new RoutePointDesign(0, 50), new RoutePointDesign(200, 50)));
        NodeDesign obstacle = new NodeDesign("obstacle", 80, 20, 40, 60);
        var diagram = new MetamodelDiagramDocument("sample", List.of(new NodeDesign("a", -20, 30, 20, 40), obstacle, new NodeDesign("b", 200, 30, 20, 40)), List.of(edge));

        assertThat(new MetamodelDiagramMetricsService().measure(diagram).edgeNodeIntersections()).isEqualTo(1);
    }

    @Test
    void countsBendsAndLength() {
        EdgeDesign edge = edge("r", "a", "b", List.of(new RoutePointDesign(0, 0), new RoutePointDesign(100, 0), new RoutePointDesign(100, 50)));
        var metrics = new MetamodelDiagramMetricsService().measure(new MetamodelDiagramDocument("sample", List.of(), List.of(edge)));

        assertThat(metrics.bends()).isEqualTo(1);
        assertThat(metrics.totalLength()).isEqualTo(150);
    }

    @Test
    void detectsPortCongestion() {
        EdgeDesign first = new EdgeDesign("r1", "right-50", "left-40", "REFERENCE", "a", "b", "", null, List.of(), null);
        EdgeDesign second = new EdgeDesign("r2", "right-50", "left-60", "REFERENCE", "a", "c", "", null, List.of(), null);
        var metrics = new MetamodelDiagramMetricsService().measure(new MetamodelDiagramDocument("sample", List.of(), List.of(first, second)));

        assertThat(metrics.maximumPortCongestion()).isEqualTo(2);
    }

    @Test
    void rejectsDuplicatePortEngines() {
        var properties = RoutingTestFixtures.properties();
        var first = new DynamicMetamodelPortAssignmentEngine(properties);
        var second = new DynamicMetamodelPortAssignmentEngine(properties);

        assertThatThrownBy(() -> new MetamodelPortAssignmentEngineRegistry(List.of(first, second))).hasMessageContaining("Duplicate metamodel port assignment engine");
    }

    @Test
    void rejectsUnknownRoutingEngine() {
        var registry = new MetamodelEdgeRoutingEngineRegistry(List.of(new OrthogonalMetamodelEdgeRoutingEngine(RoutingTestFixtures.properties())));

        assertThatThrownBy(() -> registry.require("missing")).hasMessageContaining("Unknown metamodel edge routing engine");
    }

    @Test
    void pipelineUsesConfiguredEngines() {
        var properties = RoutingTestFixtures.properties();
        var pipeline = RoutingTestFixtures.pipeline(properties);
        MetaRelation relation = RoutingTestFixtures.relation("r", "a", "b");
        var diagram = RoutingTestFixtures.diagram(List.of(new NodeDesign("a", 20, 20, 180, 100), new NodeDesign("b", 600, 20, 180, 100)), List.of(relation));
        var result = pipeline.route(RoutingTestFixtures.model(List.of(relation), "a", "b"), diagram, RoutingTestFixtures.routeOptions());

        assertThat(result.edges().get(0).sourceHandle()).startsWith("right-");
        assertThat(result.edges().get(0).routePoints()).isNotEmpty();
        assertThat(result.edges().get(0).labelPosition()).isNotNull();
    }

    private static EdgeDesign edge(String id, String source, String target, List<RoutePointDesign> points) {
        return new EdgeDesign(id, "right-50", "left-50", "REFERENCE", source, target, id, null, points, null);
    }
}
