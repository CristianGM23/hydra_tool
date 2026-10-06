package com.example.editor.metamodel.layout.routing;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.editor.metamodel.core.MetaRelation;
import com.example.editor.metamodel.design.MetamodelDiagramDocument;
import com.example.editor.metamodel.design.MetamodelDiagramDocument.EdgeDesign;
import com.example.editor.metamodel.design.MetamodelDiagramDocument.NodeDesign;
import com.example.editor.metamodel.design.MetamodelDiagramDocument.RoutePointDesign;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprueba el encaminamiento ortogonal y la evasión de obstáculos.
 */
class OrthogonalMetamodelEdgeRoutingEngineTests {

    @Test
    void createsOrthogonalRoute() {
        TestResult result = routeSimple();

        assertOrthogonal(result.edge());
        assertThat(result.edge().routePoints()).hasSizeGreaterThanOrEqualTo(4);
    }

    @Test
    void avoidsIntermediateNode() {
        TestResult result = routeSimple();
        var metrics = new MetamodelDiagramMetricsService().measure(result.diagram());

        assertThat(metrics.edgeNodeIntersections()).isZero();
    }

    @Test
    void startsAndEndsAtAssignedPorts() {
        TestResult result = routeSimple();
        RoutePointDesign expectedSource = MetamodelRoutingGeometry.handlePoint(result.source(), result.edge().sourceHandle());
        RoutePointDesign expectedTarget = MetamodelRoutingGeometry.handlePoint(result.target(), result.edge().targetHandle());

        assertThat(result.edge().routePoints().get(0)).isEqualTo(expectedSource);
        assertThat(result.edge().routePoints().get(result.edge().routePoints().size() - 1)).isEqualTo(expectedTarget);
    }

    @Test
    void createsSelfLoopOutsideNode() {
        var properties = RoutingTestFixtures.properties();
        var pipeline = RoutingTestFixtures.pipeline(properties);
        MetaRelation relation = RoutingTestFixtures.relation("loop", "a", "a");
        NodeDesign node = new NodeDesign("a", 100, 100, 200, 120);
        var result = pipeline.route(RoutingTestFixtures.model(List.of(relation), "a"), RoutingTestFixtures.diagram(List.of(node), List.of(relation)), RoutingTestFixtures.routeOptions());
        EdgeDesign edge = result.edges().get(0);

        assertOrthogonal(edge);
        assertThat(edge.routePoints().stream().mapToDouble(RoutePointDesign::x).max().orElse(0)).isGreaterThan(node.x() + node.width());
        assertThat(edge.routePoints().stream().mapToDouble(RoutePointDesign::y).max().orElse(0)).isGreaterThan(node.y() + node.height());
    }

    @Test
    void routesSeveralEdgesThroughDifferentChannels() {
        var properties = RoutingTestFixtures.properties();
        var pipeline = RoutingTestFixtures.pipeline(properties);
        List<MetaRelation> relations = List.of(RoutingTestFixtures.relation("r1", "a", "b"), RoutingTestFixtures.relation("r2", "a", "b"), RoutingTestFixtures.relation("r3", "a", "b"));
        var model = RoutingTestFixtures.model(relations, "a", "b");
        var diagram = RoutingTestFixtures.diagram(List.of(new NodeDesign("a", 20, 200, 180, 180), new NodeDesign("b", 800, 200, 180, 180)), relations);
        var result = pipeline.route(model, diagram, RoutingTestFixtures.routeOptions());

        assertThat(result.edges().stream().map(EdgeDesign::sourceHandle).distinct().toList()).hasSize(3);
        assertThat(result.edges().stream().map(EdgeDesign::routePoints).distinct().toList()).hasSizeGreaterThan(1);
    }

    @Test
    void createsLabelPosition() {
        TestResult result = routeSimple();

        assertThat(result.edge().labelPosition()).isNotNull();
        assertThat(result.edge().labelPosition().segmentIndex()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void isDeterministic() {
        var properties = RoutingTestFixtures.properties();
        var pipeline = RoutingTestFixtures.pipeline(properties);
        MetaRelation relation = RoutingTestFixtures.relation("r", "a", "b");
        var model = RoutingTestFixtures.model(List.of(relation), "a", "obstacle", "b");
        var diagram = RoutingTestFixtures.diagram(List.of(new NodeDesign("a", 20, 180, 180, 100), new NodeDesign("obstacle", 320, 100, 220, 260), new NodeDesign("b", 800, 180, 180, 100)), List.of(relation));

        assertThat(pipeline.route(model, diagram, RoutingTestFixtures.routeOptions())).isEqualTo(pipeline.route(model, diagram, RoutingTestFixtures.routeOptions()));
    }

    @Test
    void preservesEdgeStyle() {
        var properties = RoutingTestFixtures.properties();
        var pipeline = RoutingTestFixtures.pipeline(properties);
        MetaRelation relation = RoutingTestFixtures.relation("r", "a", "b");
        var style = new com.example.editor.metamodel.design.MetamodelDiagramDocument.EdgeStyleDesign("#ff0000", "#000000", "Arial", 12.0, 600.0, 3.0, "DASHED");
        var edge = new EdgeDesign("r", "right-50", "left-50", "REFERENCE", "a", "b", "r", style);
        var diagram = new MetamodelDiagramDocument("sample", List.of(new NodeDesign("a", 20, 20, 180, 100), new NodeDesign("b", 600, 20, 180, 100)), List.of(edge));
        var result = pipeline.route(RoutingTestFixtures.model(List.of(relation), "a", "b"), diagram, RoutingTestFixtures.routeOptions());

        assertThat(result.edges().get(0).style()).isEqualTo(style);
    }

    @Test
    void fallbackStillProducesOrthogonalRouteWhenSearchLimitIsSmall() {
        var properties = RoutingTestFixtures.properties();
        properties.setRoutingMaximumExpandedStates(1);
        var pipeline = RoutingTestFixtures.pipeline(properties);
        MetaRelation relation = RoutingTestFixtures.relation("r", "a", "b");
        var model = RoutingTestFixtures.model(List.of(relation), "a", "obstacle", "b");
        var diagram = RoutingTestFixtures.diagram(List.of(new NodeDesign("a", 20, 180, 180, 100), new NodeDesign("obstacle", 320, 100, 220, 260), new NodeDesign("b", 800, 180, 180, 100)), List.of(relation));
        EdgeDesign result = pipeline.route(model, diagram, RoutingTestFixtures.routeOptions()).edges().get(0);

        assertOrthogonal(result);
        assertThat(result.routePoints()).isNotEmpty();
    }

    @Test
    void routesLargeFanWithoutSharingSourcePorts() {
        var properties = RoutingTestFixtures.properties();
        var pipeline = RoutingTestFixtures.pipeline(properties);
        List<MetaRelation> relations = new ArrayList<>();
        List<NodeDesign> nodes = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        ids.add("hub");
        nodes.add(new NodeDesign("hub", 20, 400, 240, 420));

        for(int index = 0; index < 20; index += 1) {
            String id = "n" + index;
            ids.add(id);
            relations.add(RoutingTestFixtures.relation("r" + index, "hub", id));
            nodes.add(new NodeDesign(id, 1000, 20 + index * 100, 180, 80));
        }

        var result = pipeline.route(RoutingTestFixtures.model(relations, ids.toArray(String[]::new)), RoutingTestFixtures.diagram(nodes, relations), RoutingTestFixtures.routeOptions());

        assertThat(result.edges().stream().map(EdgeDesign::sourceHandle).distinct().toList()).hasSizeGreaterThanOrEqualTo(16);
        result.edges().forEach(OrthogonalMetamodelEdgeRoutingEngineTests::assertOrthogonal);
    }

    private static TestResult routeSimple() {
        var properties = RoutingTestFixtures.properties();
        var pipeline = RoutingTestFixtures.pipeline(properties);
        MetaRelation relation = RoutingTestFixtures.relation("r", "a", "b");
        NodeDesign source = new NodeDesign("a", 20, 180, 180, 100);
        NodeDesign obstacle = new NodeDesign("obstacle", 320, 100, 220, 260);
        NodeDesign target = new NodeDesign("b", 800, 180, 180, 100);
        var model = RoutingTestFixtures.model(List.of(relation), "a", "obstacle", "b");
        MetamodelDiagramDocument result = pipeline.route(model, RoutingTestFixtures.diagram(List.of(source, obstacle, target), List.of(relation)), RoutingTestFixtures.routeOptions());
        return new TestResult(result, result.edges().get(0), source, target);
    }

    private static void assertOrthogonal(EdgeDesign edge) {
        for(int index = 0; index < edge.routePoints().size() - 1; index += 1) {
            RoutePointDesign first = edge.routePoints().get(index);
            RoutePointDesign second = edge.routePoints().get(index + 1);
            assertThat(first.x() == second.x() || first.y() == second.y()).as("segment %s of %s", index, edge.relationId()).isTrue();
        }
    }

    private record TestResult(MetamodelDiagramDocument diagram, EdgeDesign edge, NodeDesign source, NodeDesign target) {
    }
}
