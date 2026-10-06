package com.example.editor.metamodel.layout;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.example.editor.metamodel.core.MetaAttribute;
import com.example.editor.metamodel.core.MetaClass;
import com.example.editor.metamodel.core.MetaClassKind;
import com.example.editor.metamodel.core.MetaEnum;
import com.example.editor.metamodel.core.MetaEnumLiteral;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.core.MetaPrimitiveType;
import com.example.editor.metamodel.core.MetaRelation;
import com.example.editor.metamodel.core.MetaRelationKind;
import com.example.editor.metamodel.core.MetaTypeReference;
import com.example.editor.metamodel.core.Multiplicity;
import com.example.editor.metamodel.design.MetamodelDiagramDocument;
import com.example.editor.metamodel.layout.routing.DynamicMetamodelPortAssignmentEngine;
import com.example.editor.metamodel.layout.routing.LongestSegmentMetamodelEdgeLabelPlacementEngine;
import com.example.editor.metamodel.layout.routing.MetamodelDiagramMetricsService;
import com.example.editor.metamodel.layout.routing.MetamodelEdgeLabelPlacementEngineRegistry;
import com.example.editor.metamodel.layout.routing.MetamodelEdgeRoutingEngineRegistry;
import com.example.editor.metamodel.layout.routing.MetamodelPortAssignmentEngineRegistry;
import com.example.editor.metamodel.layout.routing.MetamodelRoutingPipeline;
import com.example.editor.metamodel.layout.routing.OrthogonalMetamodelEdgeRoutingEngine;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprueba el algoritmo por capas.
 */
class LayeredMetamodelDiagramLayoutEngineTests {

    private MetamodelLayoutProperties properties;
    private LayeredMetamodelDiagramLayoutEngine engine;

    @BeforeEach
    void prepare() {
        properties = new MetamodelLayoutProperties();
        var portRegistry = new MetamodelPortAssignmentEngineRegistry(List.of(new DynamicMetamodelPortAssignmentEngine(properties)));
        var edgeRegistry = new MetamodelEdgeRoutingEngineRegistry(List.of(new OrthogonalMetamodelEdgeRoutingEngine(properties)));
        var labelRegistry = new MetamodelEdgeLabelPlacementEngineRegistry(List.of(new LongestSegmentMetamodelEdgeLabelPlacementEngine(properties)));
        var routingPipeline = new MetamodelRoutingPipeline(portRegistry, edgeRegistry, labelRegistry);
        engine = new LayeredMetamodelDiagramLayoutEngine(properties, routingPipeline, new MetamodelDiagramMetricsService());
    }

    @Test
    void placesTargetsToTheRight() {
        var result = layout(chainModel(), options("LEFT_TO_RIGHT"));
        var a = node(result, "class-a");
        var b = node(result, "class-b");
        var c = node(result, "class-c");
        assertThat(a.x()).isLessThan(b.x());
        assertThat(b.x()).isLessThan(c.x());
    }

    @Test
    void placesTargetsBelowForTopToBottom() {
        var result = layout(chainModel(), options("TOP_TO_BOTTOM"));
        var a = node(result, "class-a");
        var b = node(result, "class-b");
        var c = node(result, "class-c");
        assertThat(a.y()).isLessThan(b.y());
        assertThat(b.y()).isLessThan(c.y());
    }

    @Test
    void assignsFacingHandles() {
        var result = layout(chainModel(), options("LEFT_TO_RIGHT"));
        assertThat(result.diagram().edges().get(0).sourceHandle()).startsWith("right-");
        assertThat(result.diagram().edges().get(0).targetHandle()).startsWith("left-");
    }

    @Test
    void groupsCyclesAndReportsWarning() {
        MetaClass a = metaClass("class-a", "A");
        MetaClass b = metaClass("class-b", "B");
        MetaModel model = model(List.of(a, b), List.of(relation("a-b", "class-a", "class-b"), relation("b-a", "class-b", "class-a")), List.of());
        var result = layout(model, options("LEFT_TO_RIGHT"));
        assertThat(result.statistics().cycles()).isEqualTo(1);
        assertThat(result.warnings()).extracting(item -> item.code()).contains("LAYOUT_CYCLES_GROUPED");
    }

    @Test
    void doesNotOverlapNodesInsideTheSameLayer() {
        MetaClass root = metaClass("root", "Root");
        MetaClass left = metaClass("left", "Left");
        MetaClass right = metaClass("right", "Right");
        MetaModel model = model(List.of(root, left, right), List.of(relation("root-left", "root", "left"), relation("root-right", "root", "right")), List.of());
        var result = layout(model, options("LEFT_TO_RIGHT"));
        var first = node(result, "left");
        var second = node(result, "right");
        assertThat(rectanglesOverlap(first, second)).isFalse();
    }

    @Test
    void preservesExistingNodeSizeWhenRequested() {
        MetamodelDiagramDocument current = new MetamodelDiagramDocument("sample", List.of(new MetamodelDiagramDocument.NodeDesign("class-a", 10, 10, 410, 280)), List.of());
        var result = engine.layout(chainModel(), current, options("LEFT_TO_RIGHT"));
        assertThat(node(result, "class-a").width()).isEqualTo(410);
        assertThat(node(result, "class-a").height()).isEqualTo(280);
    }

    @Test
    void keepsFixedNodePosition() {
        MetamodelDiagramDocument current = new MetamodelDiagramDocument("sample", List.of(new MetamodelDiagramDocument.NodeDesign("class-b", 900, 700, 240, 135)), List.of());
        MetamodelLayoutOptions options = new MetamodelLayoutOptions("layered", "LEFT_TO_RIGHT", true, true, true, List.of("class-b"));
        var result = engine.layout(chainModel(), current, options);
        assertThat(node(result, "class-b").x()).isEqualTo(900);
        assertThat(node(result, "class-b").y()).isEqualTo(700);
    }

    @Test
    void arrangesIsolatedNodesInSeveralColumns() {
        properties.setIsolatedNodeColumns(2);
        MetaModel model = model(List.of(metaClass("a", "A"), metaClass("b", "B"), metaClass("c", "C")), List.of(), List.of());
        var result = layout(model, options("LEFT_TO_RIGHT"));
        assertThat(node(result, "a").x()).isNotEqualTo(node(result, "b").x());
        assertThat(node(result, "c").y()).isGreaterThan(node(result, "a").y());
    }

    @Test
    void placesUsedEnumerationInTheSameComponent() {
        MetaEnum status = new MetaEnum("enum-status", "Status", List.of(new MetaEnumLiteral("active", "ACTIVE", 0, Map.of())), Map.of());
        MetaAttribute attribute = new MetaAttribute("status", "status", MetaTypeReference.enumType("enum-status"), Multiplicity.one(), true, true, "", Map.of());
        MetaClass person = new MetaClass("class-person", "Person", MetaClassKind.CLASS, List.of(attribute), List.of(), Map.of());
        MetaModel model = model(List.of(person), List.of(), List.of(status));
        var result = layout(model, options("LEFT_TO_RIGHT"));
        assertThat(result.statistics().components()).isEqualTo(1);
        assertThat(node(result, "class-person").x()).isLessThan(node(result, "enum-status").x());
    }

    @Test
    void canIgnoreAttributeTypesForPlacement() {
        MetaEnum status = new MetaEnum("enum-status", "Status", List.of(), Map.of());
        MetaAttribute attribute = new MetaAttribute("status", "status", MetaTypeReference.enumType("enum-status"), Multiplicity.one(), true, true, "", Map.of());
        MetaClass person = new MetaClass("class-person", "Person", MetaClassKind.CLASS, List.of(attribute), List.of(), Map.of());
        MetaModel model = model(List.of(person), List.of(), List.of(status));
        MetamodelLayoutOptions options = new MetamodelLayoutOptions("layered", "LEFT_TO_RIGHT", true, true, false, List.of());
        var result = layout(model, options);
        assertThat(result.statistics().components()).isEqualTo(2);
    }

    @Test
    void returnsDeterministicCoordinates() {
        var first = layout(chainModel(), options("LEFT_TO_RIGHT"));
        var second = layout(chainModel(), options("LEFT_TO_RIGHT"));
        assertThat(first.diagram().nodes()).isEqualTo(second.diagram().nodes());
        assertThat(first.diagram().edges()).isEqualTo(second.diagram().edges());
    }

    @Test
    void reportsNodeAndEdgeStatistics() {
        var result = layout(chainModel(), options("LEFT_TO_RIGHT"));
        assertThat(result.statistics().nodes()).isEqualTo(3);
        assertThat(result.statistics().edges()).isEqualTo(2);
        assertThat(result.statistics().layers()).isEqualTo(3);
    }

    private MetamodelLayoutResult layout(MetaModel model, MetamodelLayoutOptions options) {
        return engine.layout(model, new MetamodelDiagramDocument(model.id(), List.of(), List.of()), options);
    }

    private MetamodelLayoutOptions options(String direction) {
        return new MetamodelLayoutOptions("layered", direction, true, true, true, List.of());
    }

    private MetaModel chainModel() {
        return model(List.of(metaClass("class-a", "A"), metaClass("class-b", "B"), metaClass("class-c", "C")), List.of(relation("a-b", "class-a", "class-b"), relation("b-c", "class-b", "class-c")), List.of());
    }

    private MetaModel model(List<MetaClass> classes, List<MetaRelation> relations, List<MetaEnum> enums) {
        return new MetaModel(MetaModel.CURRENT_SCHEMA_VERSION, "sample", "Sample", classes, enums, List.of(), relations, Map.of());
    }

    private MetaClass metaClass(String id, String name) {
        MetaAttribute nameAttribute = new MetaAttribute(id + "-name", "name", MetaTypeReference.primitive(MetaPrimitiveType.STRING), Multiplicity.one(), true, true, "", Map.of());
        return new MetaClass(id, name, MetaClassKind.CLASS, List.of(nameAttribute), List.of(), Map.of());
    }

    private MetaRelation relation(String id, String source, String target) {
        return new MetaRelation(id, id, source, target, MetaRelationKind.REFERENCE, Multiplicity.optional(), Multiplicity.optional(), "", true, true, Map.of());
    }

    private MetamodelDiagramDocument.NodeDesign node(MetamodelLayoutResult result, String id) {
        return result.diagram().nodes().stream().filter(item -> item.elementId().equals(id)).findFirst().orElseThrow();
    }

    private boolean rectanglesOverlap(MetamodelDiagramDocument.NodeDesign first, MetamodelDiagramDocument.NodeDesign second) {
        return first.x() < second.x() + second.width() && first.x() + first.width() > second.x() && first.y() < second.y() + second.height() && first.y() + first.height() > second.y();
    }
}
