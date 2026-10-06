package com.example.editor.metamodel.importing;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import com.example.editor.metamodel.core.MetaClass;
import com.example.editor.metamodel.core.MetaClassKind;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.design.MetamodelDiagramDocument;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprueba de forma aislada la política configurable que decide el routing de una importación.
 */
class MetamodelImportRoutingPolicyTests {

    private final MetamodelImportProperties properties = new MetamodelImportProperties();
    private final MetamodelImportRoutingPolicy policy = new MetamodelImportRoutingPolicy(properties);

    @Test
    void usesNormalConnectionsWithExactlyFourteenClasses() {
        assertThat(policy.usesOrthogonalRouting(modelWithClassCount(14))).isFalse();
    }

    @Test
    void usesOrthogonalConnectionsWhenThereAreMoreThanFourteenClasses() {
        assertThat(policy.usesOrthogonalRouting(modelWithClassCount(15))).isTrue();
    }

    @Test
    void honorsTheConfiguredOrthogonalRoutingThreshold() {
        properties.setOrthogonalRoutingClassThreshold(3);
        assertThat(policy.usesOrthogonalRouting(modelWithClassCount(3))).isFalse();
        assertThat(policy.usesOrthogonalRouting(modelWithClassCount(4))).isTrue();
    }

    @Test
    void normalizesNegativeThresholds() {
        properties.setOrthogonalRoutingClassThreshold(-1);
        assertThat(properties.getOrthogonalRoutingClassThreshold()).isZero();
    }

    @Test
    void removesOrthogonalPointsFromSmallImportsWithoutLosingStylesPortsOrNodes() {
        MetamodelDiagramDocument.EdgeStyleDesign edgeStyle = new MetamodelDiagramDocument.EdgeStyleDesign("#123456", "#111111", "Arial", 12.0, 400.0, 3.0, "SOLID");
        MetamodelDiagramDocument.NodeDesign node = new MetamodelDiagramDocument.NodeDesign("class-0", 10, 20, 200, 110);
        var edge = new MetamodelDiagramDocument.EdgeDesign("relation-a", "bottom-35-source", "top-65-target", "REFERENCE", "class-0", "class-1", "b", edgeStyle, List.of(new MetamodelDiagramDocument.RoutePointDesign(10, 20), new MetamodelDiagramDocument.RoutePointDesign(30, 20)), new MetamodelDiagramDocument.LabelPositionDesign(20, 20, 0));
        var diagram = new MetamodelDiagramDocument("sample", List.of(node), List.of(edge));

        var result = policy.apply(modelWithClassCount(14), diagram);

        assertThat(result.nodes()).containsExactly(node);
        assertThat(result.edges()).singleElement().satisfies(updated -> {
            assertThat(updated.routePoints()).isEmpty();
            assertThat(updated.labelPosition()).isNull();
            assertThat(updated.sourceHandle()).isEqualTo("bottom-35-source");
            assertThat(updated.targetHandle()).isEqualTo("top-65-target");
            assertThat(updated.style()).isEqualTo(edgeStyle);
        });
    }

    @Test
    void keepsOrthogonalPointsFromLargeImports() {
        var points = List.of(new MetamodelDiagramDocument.RoutePointDesign(10, 20), new MetamodelDiagramDocument.RoutePointDesign(30, 20));
        var edge = new MetamodelDiagramDocument.EdgeDesign("relation-a", "right-50-source", "left-50-target", "REFERENCE", "class-0", "class-1", "b", null, points, new MetamodelDiagramDocument.LabelPositionDesign(20, 20, 0));
        var diagram = new MetamodelDiagramDocument("sample", List.of(), List.of(edge));

        var result = policy.apply(modelWithClassCount(15), diagram);

        assertThat(result).isSameAs(diagram);
        assertThat(result.edges()).singleElement().satisfies(updated -> {
            assertThat(updated.routePoints()).containsExactlyElementsOf(points);
            assertThat(updated.labelPosition()).isNotNull();
        });
    }

    @Test
    void describesTheRoutingDecisionForImportLogs() {
        assertThat(policy.describe(modelWithClassCount(15)))
                .contains("routingMode=ORTHOGONAL", "classCount=15", "orthogonalThreshold=14");
    }

    private MetaModel modelWithClassCount(int classCount) {
        List<MetaClass> classes = IntStream.range(0, classCount)
                .mapToObj(index -> new MetaClass("class-" + index, "Class" + index, MetaClassKind.CLASS, List.of(), List.of(), Map.of()))
                .toList();
        return new MetaModel(MetaModel.CURRENT_SCHEMA_VERSION, "sample", "Sample", classes, List.of(), List.of(), List.of(), Map.of());
    }
}
