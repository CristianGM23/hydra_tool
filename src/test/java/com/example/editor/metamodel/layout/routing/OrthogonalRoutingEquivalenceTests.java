package com.example.editor.metamodel.layout.routing;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.editor.metamodel.core.MetaRelation;
import com.example.editor.metamodel.design.MetamodelDiagramDocument;
import com.example.editor.metamodel.design.MetamodelDiagramDocument.EdgeDesign;
import com.example.editor.metamodel.design.MetamodelDiagramDocument.NodeDesign;
import com.example.editor.metamodel.design.MetamodelDiagramDocument.RoutePointDesign;

/**
 * The orthogonal router keeps its search state in arrays indexed by grid cell and direction instead of hash maps of records (profiled:
 * ~40 % of the time was spent in HashMap tree bins). The routes must be EXACTLY those of the previous implementation: this fingerprint was
 * produced by the engine of jar 11 (before the change) on a deterministic diagram with obstacles, crossings, congestion and unreachable
 * targets, and any change of the produced routes changes it.
 */
class OrthogonalRoutingEquivalenceTests {

    // identical with the engines of jar 10 (before FIND-MM-IMPORT-ROUTING-FLOOD), jar 11 and the array-indexed version
    static final String ROUTES_OF_THE_PREVIOUS_ENGINE = "008029648ffb549643743ce6c9fe51ba74fedb2e72502995aa531c69d8324ff1";

    @Test
    void producesExactlyTheRoutesOfThePreviousImplementation() throws Exception {
        assertThat(fingerprint(route())).isEqualTo(ROUTES_OF_THE_PREVIOUS_ENGINE);
    }

    static MetamodelDiagramDocument route() {
        List<NodeDesign> nodes = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        for (int row = 0; row < 6; row++) {
            for (int column = 0; column < 7; column++) {
                String id = "n" + row + "_" + column;
                // irregular widths and gaps: some neighbours are closer than the clearance (enclosed ports), others leave channels
                double x = column * (220 + (row % 3) * 35) + (column % 2) * 15;
                double y = row * (150 + (column % 4) * 20);
                nodes.add(new NodeDesign(id, x, y, 140 + (column * 13 + row * 7) % 60, 70 + (row * 11 + column * 5) % 40, null));
                ids.add(id);
            }
        }
        List<MetaRelation> relations = new ArrayList<>();
        for (int i = 0; i < ids.size(); i++) {
            relations.add(RoutingTestFixtures.relation("near" + i, ids.get(i), ids.get((i + 1) % ids.size())));
            relations.add(RoutingTestFixtures.relation("far" + i, ids.get(i), ids.get((i * 17 + 5) % ids.size())));
            if (i % 5 == 0) relations.add(RoutingTestFixtures.relation("self" + i, ids.get(i), ids.get(i)));
        }
        MetamodelDiagramDocument diagram = RoutingTestFixtures.diagram(nodes, relations);
        return new OrthogonalMetamodelEdgeRoutingEngine(RoutingTestFixtures.properties())
                .route(RoutingTestFixtures.model(relations, ids.toArray(String[]::new)), diagram, RoutingTestFixtures.routeOptions());
    }

    static String fingerprint(MetamodelDiagramDocument routed) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        for (EdgeDesign edge : routed.edges()) {
            digest.update(edge.relationId().getBytes(StandardCharsets.UTF_8));
            for (RoutePointDesign point : edge.routePoints()) {
                digest.update((";" + Double.toString(point.x()) + "," + Double.toString(point.y())).getBytes(StandardCharsets.UTF_8));
            }
            digest.update((byte) '\n');
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
