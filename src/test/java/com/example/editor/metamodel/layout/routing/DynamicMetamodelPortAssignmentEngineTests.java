package com.example.editor.metamodel.layout.routing;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.editor.metamodel.core.MetaRelation;
import com.example.editor.metamodel.design.MetamodelDiagramDocument;
import com.example.editor.metamodel.design.MetamodelDiagramDocument.NodeDesign;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprueba la asignación dinámica y ordenada de puertos.
 */
class DynamicMetamodelPortAssignmentEngineTests {

    @Test
    void assignsDifferentPortsToManyOutgoingRelations() {
        var properties = RoutingTestFixtures.properties();
        var engine = new DynamicMetamodelPortAssignmentEngine(properties);
        List<MetaRelation> relations = relationsFromA(12);
        List<NodeDesign> nodes = nodesForFan(12);
        MetamodelDiagramDocument result = engine.assign(RoutingTestFixtures.model(relations, ids(13)), RoutingTestFixtures.diagram(nodes, relations), RoutingTestFixtures.routeOptions());

        assertThat(result.edges().stream().map(item -> item.sourceHandle()).distinct().toList()).hasSizeGreaterThanOrEqualTo(10);
    }

    @Test
    void ordersRightPortsByTargetVerticalPosition() {
        var engine = new DynamicMetamodelPortAssignmentEngine(RoutingTestFixtures.properties());
        List<MetaRelation> relations = List.of(RoutingTestFixtures.relation("top", "a", "b"), RoutingTestFixtures.relation("middle", "a", "c"), RoutingTestFixtures.relation("bottom", "a", "d"));
        List<NodeDesign> nodes = List.of(new NodeDesign("a", 20, 200, 180, 160), new NodeDesign("b", 600, 20, 180, 100), new NodeDesign("c", 600, 220, 180, 100), new NodeDesign("d", 600, 420, 180, 100));
        var result = engine.assign(RoutingTestFixtures.model(relations, "a", "b", "c", "d"), RoutingTestFixtures.diagram(nodes, relations), RoutingTestFixtures.routeOptions());

        double top = MetamodelRoutingGeometry.percent(result.edges().get(0).sourceHandle());
        double middle = MetamodelRoutingGeometry.percent(result.edges().get(1).sourceHandle());
        double bottom = MetamodelRoutingGeometry.percent(result.edges().get(2).sourceHandle());
        assertThat(top).isLessThan(middle);
        assertThat(middle).isLessThan(bottom);
    }

    @Test
    void assignsFacingSidesForHorizontalNodes() {
        var engine = new DynamicMetamodelPortAssignmentEngine(RoutingTestFixtures.properties());
        MetaRelation relation = RoutingTestFixtures.relation("r", "a", "b");
        var diagram = RoutingTestFixtures.diagram(List.of(new NodeDesign("a", 20, 20, 180, 100), new NodeDesign("b", 600, 20, 180, 100)), List.of(relation));
        var result = engine.assign(RoutingTestFixtures.model(List.of(relation), "a", "b"), diagram, RoutingTestFixtures.routeOptions());

        assertThat(result.edges().get(0).sourceHandle()).startsWith("right-");
        assertThat(result.edges().get(0).targetHandle()).startsWith("left-");
    }

    @Test
    void assignsFacingSidesForVerticalNodes() {
        var engine = new DynamicMetamodelPortAssignmentEngine(RoutingTestFixtures.properties());
        MetaRelation relation = RoutingTestFixtures.relation("r", "a", "b");
        var diagram = RoutingTestFixtures.diagram(List.of(new NodeDesign("a", 20, 20, 180, 100), new NodeDesign("b", 20, 500, 180, 100)), List.of(relation));
        var result = engine.assign(RoutingTestFixtures.model(List.of(relation), "a", "b"), diagram, RoutingTestFixtures.routeOptions());

        assertThat(result.edges().get(0).sourceHandle()).startsWith("bottom-");
        assertThat(result.edges().get(0).targetHandle()).startsWith("top-");
    }

    @Test
    void assignsDifferentSidesToSelfLoop() {
        var engine = new DynamicMetamodelPortAssignmentEngine(RoutingTestFixtures.properties());
        MetaRelation relation = RoutingTestFixtures.relation("loop", "a", "a");
        var diagram = RoutingTestFixtures.diagram(List.of(new NodeDesign("a", 20, 20, 180, 100)), List.of(relation));
        var result = engine.assign(RoutingTestFixtures.model(List.of(relation), "a"), diagram, RoutingTestFixtures.routeOptions());

        assertThat(result.edges().get(0).sourceHandle()).startsWith("right-");
        assertThat(result.edges().get(0).targetHandle()).startsWith("bottom-");
    }

    @Test
    void clearsOldRoutesWhenPortsChange() {
        var engine = new DynamicMetamodelPortAssignmentEngine(RoutingTestFixtures.properties());
        MetaRelation relation = RoutingTestFixtures.relation("r", "a", "b");
        var oldEdge = new com.example.editor.metamodel.design.MetamodelDiagramDocument.EdgeDesign("r", "right-50", "left-50", "REFERENCE", "a", "b", "r", null, List.of(new com.example.editor.metamodel.design.MetamodelDiagramDocument.RoutePointDesign(1, 1)), null);
        var diagram = new MetamodelDiagramDocument("sample", List.of(new NodeDesign("a", 20, 20, 180, 100), new NodeDesign("b", 600, 20, 180, 100)), List.of(oldEdge));
        var result = engine.assign(RoutingTestFixtures.model(List.of(relation), "a", "b"), diagram, RoutingTestFixtures.routeOptions());

        assertThat(result.edges().get(0).routePoints()).isEmpty();
    }

    @Test
    void isDeterministic() {
        var engine = new DynamicMetamodelPortAssignmentEngine(RoutingTestFixtures.properties());
        List<MetaRelation> relations = relationsFromA(8);
        var model = RoutingTestFixtures.model(relations, ids(9));
        var diagram = RoutingTestFixtures.diagram(nodesForFan(8), relations);

        assertThat(engine.assign(model, diagram, RoutingTestFixtures.routeOptions())).isEqualTo(engine.assign(model, diagram, RoutingTestFixtures.routeOptions()));
    }

    private static List<MetaRelation> relationsFromA(int count) {
        List<MetaRelation> result = new ArrayList<>();

        for(int index = 0; index < count; index += 1) {
            result.add(RoutingTestFixtures.relation("r" + index, "a", "n" + index));
        }

        return result;
    }

    private static List<NodeDesign> nodesForFan(int count) {
        List<NodeDesign> result = new ArrayList<>();
        result.add(new NodeDesign("a", 20, 300, 220, 300));

        for(int index = 0; index < count; index += 1) {
            result.add(new NodeDesign("n" + index, 800, 20 + index * 120, 180, 90));
        }

        return result;
    }

    private static String[] ids(int count) {
        String[] result = new String[count];
        result[0] = "a";

        for(int index = 1; index < count; index += 1) {
            result[index] = "n" + (index - 1);
        }

        return result;
    }
}
