package com.example.editor.metamodel.layout.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.editor.metamodel.core.MetaRelation;
import com.example.editor.metamodel.design.MetamodelDiagramDocument;
import com.example.editor.metamodel.design.MetamodelDiagramDocument.EdgeDesign;
import com.example.editor.metamodel.design.MetamodelDiagramDocument.NodeDesign;

/**
 * An edge whose target port is enclosed by neighbouring nodes has no grid route. The A* used to flood the whole free grid (up to its
 * expansion budget) for every such edge before falling back, so importing a metamodel of a few dozen classes took minutes (battery
 * scenario MM-SCALE: 229 s for 80 classes). The impossibility must be detected without the search, and the edge still gets the fallback route.
 */
class OrthogonalRoutingUnreachableTargetTests {

    @Test
    void edgesToAnEnclosedTargetAreRoutedWithoutFloodingTheGrid() {
        List<NodeDesign> nodes = new ArrayList<>();
        nodes.add(new NodeDesign("target", 3000, 3000, 100, 100, null));
        // a tight ring around the target: every lead point next to it falls inside a neighbour's clearance
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx != 0 || dy != 0) nodes.add(new NodeDesign("ring" + dx + dy, 3000 + dx * 110, 3000 + dy * 110, 100, 100, null));
            }
        }
        nodes.add(new NodeDesign("far", 6000, 6000, 100, 60, null));
        List<MetaRelation> relations = new ArrayList<>();
        List<String> ids = new ArrayList<>(nodes.stream().map(NodeDesign::elementId).toList());
        for (int i = 0; i < 30; i++) {
            nodes.add(new NodeDesign("source" + i, 100, 100 + i * 150, 100, 60, null));
            ids.add("source" + i);
            relations.add(RoutingTestFixtures.relation("toTarget" + i, "source" + i, "target"));
        }
        relations.add(RoutingTestFixtures.relation("free", "source0", "far"));
        MetamodelDiagramDocument diagram = RoutingTestFixtures.diagram(nodes, relations);
        OrthogonalMetamodelEdgeRoutingEngine engine = new OrthogonalMetamodelEdgeRoutingEngine(RoutingTestFixtures.properties());

        MetamodelDiagramDocument routed = assertTimeoutPreemptively(Duration.ofSeconds(5),
                () -> engine.route(RoutingTestFixtures.model(relations, ids.toArray(String[]::new)), diagram, RoutingTestFixtures.routeOptions()));

        for (EdgeDesign edge : routed.edges()) {
            NodeDesign source = nodes.stream().filter(n -> n.elementId().equals(edge.sourceClassId())).findFirst().orElseThrow();
            NodeDesign target = nodes.stream().filter(n -> n.elementId().equals(edge.targetClassId())).findFirst().orElseThrow();
            assertThat(edge.routePoints()).as(edge.relationId()).hasSizeGreaterThanOrEqualTo(2);
            assertThat(edge.routePoints().get(0)).isEqualTo(MetamodelRoutingGeometry.handlePoint(source, edge.sourceHandle()));
            assertThat(edge.routePoints().get(edge.routePoints().size() - 1)).isEqualTo(MetamodelRoutingGeometry.handlePoint(target, edge.targetHandle()));
        }
    }
}
