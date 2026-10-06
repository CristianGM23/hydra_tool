package com.example.editor.definition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.example.editor.definition.runtime.session.EditorRenderedNode;
import com.example.editor.metamodel.core.MetaClass;
import com.example.editor.metamodel.core.MetaClassKind;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.core.MetaRelation;
import com.example.editor.metamodel.core.MetaRelationKind;
import com.example.editor.metamodel.core.Multiplicity;

class VisualNotationContainerContractTests {
    private final EditorDefinitionChecker checker = new EditorDefinitionChecker();

    @Test void supportsProjectImagesAndExtendedGeometricShapes() {
        assertThat(EditorNodeShape.values()).contains(EditorNodeShape.PROJECT_IMAGE,
                EditorNodeShape.TRIANGLE, EditorNodeShape.PENTAGON, EditorNodeShape.OCTAGON,
                EditorNodeShape.PARALLELOGRAM, EditorNodeShape.TRAPEZOID, EditorNodeShape.CYLINDER,
                EditorNodeShape.DOCUMENT, EditorNodeShape.CLOUD, EditorNodeShape.STAR, EditorNodeShape.CROSS);
    }

    @Test void oldNodeMappingConstructorKeepsPortableDefaults() {
        NodeMapping mapping = oldMapping();
        assertThat(mapping.imageAssetPath()).isEmpty();
        assertThat(mapping.imageFit()).isEqualTo(EditorImageFit.CONTAIN);
        assertThat(mapping.container()).isFalse();
        assertThat(mapping.containmentRelationIds()).isEmpty();
        assertThat(mapping.containerPadding()).isEqualTo(18);
    }

    @Test void normalizesWindowsProjectAssetPaths() {
        NodeMapping mapping = mapping(EditorNodeShape.PROJECT_IMAGE, "assets\\notation\\department.svg", false, List.of());
        assertThat(mapping.imageAssetPath()).isEqualTo("assets/notation/department.svg");
    }

    @Test void rejectsAbsoluteProjectAssetPaths() {
        assertThatThrownBy(() -> mapping(EditorNodeShape.PROJECT_IMAGE, "C:\\secret\\node.png", false, List.of()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("project-relative");
    }

    @Test void rejectsParentTraversalProjectAssetPaths() {
        assertThatThrownBy(() -> mapping(EditorNodeShape.PROJECT_IMAGE, "assets/../secret.svg", false, List.of()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("project-relative");
    }

    @Test void removesDuplicateContainmentRelationIdentifiers() {
        NodeMapping mapping = mapping(EditorNodeShape.RECTANGLE, "", true,
                List.of("relation:Department:employees", "relation:Department:employees"));
        assertThat(mapping.containmentRelationIds()).containsExactly("relation:Department:employees");
    }

    @Test void restoresSafeContainerPaddingForOldDocuments() {
        NodeMapping mapping = new NodeMapping("node:department", "class:Department", "Department", EditorNodeShape.RECTANGLE, 300, 240, 120, 80, "", "", EditorImageFit.CONTAIN, true, List.of("relation:Department:employees"), 0, Map.of(), List.of(), Map.of());
        assertThat(mapping.containerPadding()).isEqualTo(18);
    }

    @Test void imageBackedMappingRequiresAnAsset() {
        assertThat(check(mapping(EditorNodeShape.PROJECT_IMAGE, "", false, List.of())))
                .contains("IMAGE_ASSET_REQUIRED");
    }

    @Test void projectImageAcceptsSvgAndPngAssets() {
        assertThat(check(mapping(EditorNodeShape.PROJECT_IMAGE, "assets/department.svg", false, List.of()))).isEmpty();
        assertThat(check(mapping(EditorNodeShape.PROJECT_IMAGE, "assets/department.png", false, List.of()))).isEmpty();
    }


    @Test void svgPathRequiresPortablePathData() {
        assertThat(check(mapping(EditorNodeShape.SVG_PATH, "", false, List.of())))
                .contains("SVG_PATH_REQUIRED");
    }

    @Test void rejectsInvalidNotationColorsAndOptions() {
        NodeMapping mapping = new NodeMapping("node:department", "class:Department", "Department", EditorNodeShape.RECTANGLE, 300, 240, 120, 80, "", "", EditorImageFit.CONTAIN, false, List.of(), 18, Map.of("fillColor", "red", "borderStyle", "FLASHING", "opacity", "2"), List.of(), Map.of());
        assertThat(check(mapping)).contains("INVALID_NOTATION_COLOR", "INVALID_NOTATION_OPTION", "INVALID_NOTATION_NUMBER");
    }

    @Test void rejectsExecutableSvgPathAndInvalidLabelVisibility() {
        NodeMapping mapping = new NodeMapping("node:department", "class:Department", "Department", EditorNodeShape.SVG_PATH, 300, 240, 120, 80, "", "", EditorImageFit.CONTAIN, false, List.of(), 18, Map.of("svgPath", "<script>alert(1)</script>", "labelVisible", "sometimes"), List.of(), Map.of());
        assertThat(check(mapping)).contains("INVALID_SVG_PATH", "INVALID_NOTATION_BOOLEAN");
    }

    @Test void acceptsPortableFigureBorderAndLabelStyles() {
        NodeMapping mapping = new NodeMapping("node:department", "class:Department", "Department", EditorNodeShape.STAR, 300, 240, 120, 80, "", "", EditorImageFit.CONTAIN, false, List.of(), 18, Map.of("fillColor", "#F8FAFC", "strokeColor", "#0F172A", "strokeWidth", "3",
                        "borderStyle", "DASHED", "labelPosition", "OUTSIDE_BOTTOM", "labelColor", "#111827"), List.of(), Map.of());
        assertThat(check(mapping)).isEmpty();
    }

    @Test void containerRequiresContainmentRelation() {
        assertThat(check(mapping(EditorNodeShape.RECTANGLE, "", true, List.of())))
                .contains("CONTAINER_RELATION_REQUIRED");
    }

    @Test void containerRejectsOrdinaryReference() {
        assertThat(check(mapping(EditorNodeShape.RECTANGLE, "", true, List.of("relation:Department:manager"))))
                .contains("NON_CONTAINMENT_CONTAINER_RELATION");
    }

    @Test void containerRejectsContainmentOwnedByAnotherClass() {
        assertThat(check(mapping(EditorNodeShape.RECTANGLE, "", true, List.of("relation:Employee:departments"))))
                .contains("CONTAINER_SOURCE_MISMATCH");
    }

    @Test void validContainerAndProjectImageAreAccepted() {
        NodeMapping mapping = mapping(EditorNodeShape.PROJECT_IMAGE, "assets/notation/department.svg", true,
                List.of("relation:Department:employees"));
        assertThat(check(mapping)).isEmpty();
    }

    @Test void renderedNodeCarriesContainerAndImageMetadata() {
        EditorRenderedNode node = new EditorRenderedNode("department-one", "class:Department", "Department", "node:department", "Department", EditorNodeShape.PROJECT_IMAGE, "Research", 320, 240, 120, 90, Map.of(), Map.of(), "assets/notation/department.png", EditorImageFit.COVER, true, List.of("relation:Department:employees"), 24, List.of());
        assertThat(node.container()).isTrue();
        assertThat(node.imageAssetPath()).endsWith("department.png");
        assertThat(node.containmentRelationIds()).containsExactly("relation:Department:employees");
    }

    private NodeMapping oldMapping() {
        return new NodeMapping("node:department", "class:Department", "Department", EditorNodeShape.ROUNDED_RECTANGLE, 300, 240, 120, 80, "", "", com.example.editor.definition.EditorImageFit.CONTAIN, false, List.of(), 18, Map.of(), List.of(), Map.of());
    }

    private NodeMapping mapping(EditorNodeShape shape, String path, boolean container, List<String> relationIds) {
        return new NodeMapping("node:department", "class:Department", "Department", shape, 300, 240, 120, 80, "", path, EditorImageFit.CONTAIN, container, relationIds, 18, Map.of(), List.of(), Map.of());
    }

    private List<String> check(NodeMapping mapping) {
        RepresentationDefinition representation = new RepresentationDefinition("representation:main", "Main", RepresentationKind.DIAGRAM,
                List.of("class:Department"), List.of(mapping), List.of(), List.of(), true, Map.of());
        EditorDefinitionModel model = new EditorDefinitionModel("1.0", "organization-editor", "organization", "Organization editor",
                List.of(representation), new ToolPaletteDefinition(List.of(), List.of()), List.of(),
                new EditorCapabilityManifest(Set.of(EditorCapability.MODEL_EDITING)), Map.of());
        return checker.check(model, metamodel(), null, null).diagnostics().stream().map(EditorDefinitionDiagnostic::code).toList();
    }

    private MetaModel metamodel() {
        MetaClass department = new MetaClass("class:Department", "Department", MetaClassKind.CLASS, List.of(), List.of(), Map.of());
        MetaClass employee = new MetaClass("class:Employee", "Employee", MetaClassKind.CLASS, List.of(), List.of(), Map.of());
        MetaRelation employees = new MetaRelation("relation:Department:employees", "employees", department.id(), employee.id(),
                MetaRelationKind.CONTAINMENT, Multiplicity.one(), Multiplicity.many(), "", true, true, Map.of());
        MetaRelation manager = new MetaRelation("relation:Department:manager", "manager", department.id(), employee.id(),
                MetaRelationKind.REFERENCE, Multiplicity.one(), Multiplicity.optional(), "", true, true, Map.of());
        MetaRelation employeeDepartments = new MetaRelation("relation:Employee:departments", "departments", employee.id(), department.id(),
                MetaRelationKind.CONTAINMENT, Multiplicity.one(), Multiplicity.many(), "", true, true, Map.of());
        return new MetaModel("2.1", "organization", "Organization", List.of(department, employee), List.of(), List.of(),
                List.of(employees, manager, employeeDepartments), Map.of());
    }
}
