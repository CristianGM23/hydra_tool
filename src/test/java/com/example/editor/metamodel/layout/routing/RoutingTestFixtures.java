package com.example.editor.metamodel.layout.routing;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.example.editor.metamodel.core.MetaClass;
import com.example.editor.metamodel.core.MetaClassKind;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.core.MetaRelation;
import com.example.editor.metamodel.core.MetaRelationKind;
import com.example.editor.metamodel.core.Multiplicity;
import com.example.editor.metamodel.design.MetamodelDiagramDocument;
import com.example.editor.metamodel.design.MetamodelDiagramDocument.EdgeDesign;
import com.example.editor.metamodel.design.MetamodelDiagramDocument.NodeDesign;
import com.example.editor.metamodel.layout.MetamodelLayoutOptions;
import com.example.editor.metamodel.layout.MetamodelLayoutProperties;

/**
 * Crea metamodelos y diagramas reutilizables en los tests.
 */
final class RoutingTestFixtures {

    private RoutingTestFixtures() {
    }

    static MetamodelLayoutProperties properties() {
        MetamodelLayoutProperties result = new MetamodelLayoutProperties();
        result.setRoutingGridSize(20);
        result.setRoutingNodeClearance(20);
        result.setRoutingTurnCost(20);
        result.setRoutingCrossingCost(800);
        result.setRoutingCongestionCost(120);
        result.setMinimumPortGap(12);
        result.setMaximumPortsPerSide(24);
        return result;
    }

    static MetamodelLayoutOptions routeOptions() {
        return new MetamodelLayoutOptions("layered", "LEFT_TO_RIGHT", true, true, true, List.of(), "ROUTE_ONLY", "dynamic", "orthogonal", "longest-segment");
    }

    static MetaModel model(List<MetaRelation> relations, String... ids) {
        List<MetaClass> classes = new ArrayList<>();

        for(String id : ids) {
            classes.add(new MetaClass(id, id.toUpperCase(java.util.Locale.ROOT), MetaClassKind.CLASS, List.of(), List.of(), Map.of()));
        }

        return new MetaModel(MetaModel.CURRENT_SCHEMA_VERSION, "sample", "Sample", classes, List.of(), List.of(), relations, Map.of());
    }

    static MetaRelation relation(String id, String source, String target) {
        return new MetaRelation(id, id, source, target, MetaRelationKind.REFERENCE, null, Multiplicity.optional(), "", true, true, Map.of());
    }

    static EdgeDesign edge(MetaRelation relation) {
        return new EdgeDesign(relation.id(), "right-50", "left-50", relation.kind().name(), relation.sourceTypeId(), relation.targetTypeId(), relation.name());
    }

    static MetamodelDiagramDocument diagram(List<NodeDesign> nodes, List<MetaRelation> relations) {
        return new MetamodelDiagramDocument("sample", nodes, relations.stream().map(RoutingTestFixtures::edge).toList());
    }

    static MetamodelRoutingPipeline pipeline(MetamodelLayoutProperties properties) {
        DynamicMetamodelPortAssignmentEngine portEngine = new DynamicMetamodelPortAssignmentEngine(properties);
        OrthogonalMetamodelEdgeRoutingEngine routingEngine = new OrthogonalMetamodelEdgeRoutingEngine(properties);
        LongestSegmentMetamodelEdgeLabelPlacementEngine labelEngine = new LongestSegmentMetamodelEdgeLabelPlacementEngine(properties);
        return new MetamodelRoutingPipeline(new MetamodelPortAssignmentEngineRegistry(List.of(portEngine)), new MetamodelEdgeRoutingEngineRegistry(List.of(routingEngine)), new MetamodelEdgeLabelPlacementEngineRegistry(List.of(labelEngine)));
    }
}
