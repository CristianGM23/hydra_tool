package com.example.editor.portability;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.example.editor.integration.IntegrationDeliveryMode;
import com.example.editor.integration.IntegrationTechnology;
import com.example.editor.integration.IntegrationTechnologyCatalog;

import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.JsonNodeFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CapabilityPackageTests {

    private final tools.jackson.databind.ObjectMapper mapper = JsonMapper.builder().findAndAddModules().build();
    private final CapabilityPackageChecker checker = new CapabilityPackageChecker();

    @Test void acceptsAPartialValidationAndQuickFixPackage() {
        var validation = selection("validation", HydraCapabilityKind.VALIDATIONS, "validation.json", true);
        var fixes = selection("fixes", HydraCapabilityKind.QUICK_FIXES, "actions.json", true);
        var manifest = manifest(List.of(validation, fixes), List.of(new CapabilityDependency("fixes", "validation", true, ">=1.0", "diagnostic source")), Map.of());
        var value = new HydraCapabilityPackage(manifest, Map.of("validation.json", JsonNodeFactory.instance.objectNode(), "actions.json", JsonNodeFactory.instance.objectNode()), Map.of());
        assertThat(checker.check(value).valid()).isTrue();
    }

    @Test void rejectsMissingManifest() {
        assertThat(checker.check(new HydraCapabilityPackage(null, Map.of(), Map.of())).diagnostics()).extracting(item -> item.code()).contains("PACKAGE_REQUIRED");
    }

    @Test void rejectsMissingIdentityFields() {
        var invalid = new HydraCapabilityPackage(new HydraCapabilityPackageManifest("1.0", "", "", "", "", null, List.of(), List.of(), List.of(), Map.of(), Map.of()), Map.of(), Map.of());
        assertThat(checker.check(invalid).diagnostics()).extracting(item -> item.code()).contains("PACKAGE_ID_REQUIRED", "PACKAGE_NAME_REQUIRED", "SEMANTIC_SCOPE_ID_REQUIRED");
    }


    @Test void defaultsLegacyCapabilitiesToEmbeddedAndPreservesExplicitModes() {
        var legacy = new CapabilitySelection("legacy", HydraCapabilityKind.VALIDATIONS, "validation.json", "1.0", true, false, Map.of());
        var remote = new CapabilitySelection("remote", HydraCapabilityKind.EVENT_FLOW, "flow.json", "1.0", false, true, CapabilityExecutionMode.REMOTE, Map.of());
        assertThat(legacy.executionMode()).isEqualTo(CapabilityExecutionMode.EMBEDDED);
        assertThat(remote.executionMode()).isEqualTo(CapabilityExecutionMode.REMOTE);
    }

    @Test void acceptsGrammarFirstPackageWithoutArtificialMetamodel() {
        var grammar = new CapabilitySelection("grammar", HydraCapabilityKind.GRAMMAR, "grammar.json", "1.0", true, true, CapabilityExecutionMode.HYBRID, Map.of());
        var manifest = new HydraCapabilityPackageManifest("1.0", "grammar-package", "Grammar package", "1.0.0", "", "people-grammar",
                new MetamodelOrigin(MetamodelOriginKind.OWNED, null, "", "", "", "", "", Map.of(), Map.of()),
                List.of(grammar), List.of(), List.of(), Map.of(), Map.of());
        var value = new HydraCapabilityPackage(manifest, Map.of("grammar.json", JsonNodeFactory.instance.objectNode()), Map.of());
        assertThat(checker.check(value).valid()).isTrue();
        assertThat(value.manifest().metamodelId()).isBlank();
        assertThat(value.manifest().semanticScopeId()).isEqualTo("people-grammar");
    }

    @Test void rejectsDuplicateCapabilityIds() {
        var first = selection("same", HydraCapabilityKind.VALIDATIONS, "one", false);
        var second = selection("same", HydraCapabilityKind.QUICK_FIXES, "two", false);
        assertThat(checker.check(new HydraCapabilityPackage(manifest(List.of(first, second), List.of(), Map.of()), Map.of(), Map.of())).diagnostics()).extracting(item -> item.code()).contains("DUPLICATE_CAPABILITY_ID");
    }

    @Test void distinguishesRequiredAndOptionalMissingDocuments() {
        var required = selection("required", HydraCapabilityKind.VALIDATIONS, "required.json", true);
        var optional = selection("optional", HydraCapabilityKind.ASSETS, "optional.json", false);
        var report = checker.check(new HydraCapabilityPackage(manifest(List.of(required, optional), List.of(), Map.of()), Map.of(), Map.of()));
        assertThat(report.diagnostics()).extracting(item -> item.code()).contains("REQUIRED_DOCUMENT_MISSING", "OPTIONAL_DOCUMENT_MISSING");
    }

    @Test void detectsMissingDependencyTargets() {
        var selected = selection("flow", HydraCapabilityKind.EVENT_FLOW, "flow.json", false);
        var report = checker.check(new HydraCapabilityPackage(manifest(List.of(selected), List.of(new CapabilityDependency("flow", "events", true, "", "source events")), Map.of()), Map.of(), Map.of()));
        assertThat(report.diagnostics()).extracting(item -> item.code()).contains("DEPENDENCY_TARGET_MISSING");
    }

    @Test void detectsDependencyCycles() {
        var one = selection("one", HydraCapabilityKind.EVENT_FLOW, "one", false);
        var two = selection("two", HydraCapabilityKind.EVENT_BINDINGS, "two", false);
        var dependencies = List.of(new CapabilityDependency("one", "two", true, "", ""), new CapabilityDependency("two", "one", true, "", ""));
        assertThat(checker.check(new HydraCapabilityPackage(manifest(List.of(one, two), dependencies, Map.of()), Map.of(), Map.of())).diagnostics()).extracting(item -> item.code()).contains("CAPABILITY_DEPENDENCY_CYCLE");
    }

    @Test void validatesSha256Checksums() {
        var report = checker.check(new HydraCapabilityPackage(manifest(List.of(), List.of(), Map.of("file.json", "bad")), Map.of(), Map.of()));
        assertThat(report.diagnostics()).extracting(item -> item.code()).contains("INVALID_SHA256");
    }

    @Test void rejectsDraftSchemasInsteadOfGuessingHistoricalFields() throws Exception {
        var source = mapper.readTree("{\"manifest\":{\"schemaVersion\":\"0.9\",\"id\":\"p\",\"name\":\"P\",\"metamodelId\":\"m\",\"metamodelOrigin\":{\"kind\":\"OWNED\"},\"capabilities\":[],\"dependencies\":[],\"compatibility\":[],\"checksums\":{},\"annotations\":{}},\"canonicalDocuments\":{},\"resources\":{}}");
        var manifestNode = source.path("manifest");
        assertThat(manifestNode.path("schemaVersion").asText()).isEqualTo("0.9");
        assertThatThrownBy(() -> mapper.treeToValue(manifestNode, HydraCapabilityPackageManifest.class))
                .isInstanceOf(RuntimeException.class);
    }

    @Test void publishesGlspCompatibilityInNativeLanguages() {
        var values = new CapabilityCompatibilityMatrixService().matrix();
        assertThat(values)
                .filteredOn(item -> item.technology() == IntegrationTechnology.ECLIPSE_GLSP && item.capability() == HydraCapabilityKind.VALIDATIONS)
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.nativeLanguage()).contains("TypeScript", "Java");
                    assertThat(item.generatedArtifacts()).contains("ModelValidator", "markers");
                });
    }

    @Test void keepsPlantUmlProjectionOnly() {
        var values = new CapabilityCompatibilityMatrixService().matrix();
        assertThat(values).filteredOn(item -> item.technology() == IntegrationTechnology.PLANTUML).allSatisfy(item -> assertThat(item.fidelity()).isEqualTo(CapabilityFidelity.PROJECTION_ONLY));
    }

    @Test void keepsPlantUmlDeliveryModeProjectionOnly() {
        var values = new CapabilityCompatibilityMatrixService().matrix();
        assertThat(values).filteredOn(item -> item.technology() == IntegrationTechnology.PLANTUML).allSatisfy(item -> assertThat(item.deliveryMode()).isEqualTo(IntegrationDeliveryMode.PROJECTION_ONLY));
    }

    @Test void describesPlantUmlAsANonExecutableProjection() {
        var values = new CapabilityCompatibilityMatrixService().matrix();
        assertThat(values)
                .filteredOn(item -> item.technology() == IntegrationTechnology.PLANTUML && item.capability() == HydraCapabilityKind.EVENT_FLOW)
                .singleElement()
                .satisfies(item -> assertThat(item.limitations()).anyMatch(value -> value.contains("projection target")));
    }

    @Test void doesNotConvertUnsupportedCapabilitiesOfOtherTechnologiesIntoProjections() {
        var values = new CapabilityCompatibilityMatrixService().matrix();
        assertThat(values)
                .filteredOn(item -> item.technology() != IntegrationTechnology.PLANTUML && item.fidelity() == CapabilityFidelity.UNSUPPORTED)
                .isNotEmpty()
                .allSatisfy(item -> assertThat(item.deliveryMode()).isNotEqualTo(IntegrationDeliveryMode.PROJECTION_ONLY));
    }

    @Test void catalogRegistersEclipseGlspWithoutReplacingExistingTechnologies() {
        assertThat(IntegrationTechnologyCatalog.descriptors()).extracting(item -> item.technology()).contains(IntegrationTechnology.ECLIPSE_GLSP, IntegrationTechnology.EMF, IntegrationTechnology.LANGIUM, IntegrationTechnology.PLANTUML);
    }

    @Test void glspSupportsGeneratedModuleOrHybridDelivery() {
        var descriptor = IntegrationTechnologyCatalog.descriptor(IntegrationTechnology.ECLIPSE_GLSP);
        assertThat(descriptor.deliveryModes()).contains(IntegrationDeliveryMode.GENERATED_MODULE, IntegrationDeliveryMode.HYBRID);
    }

    @Test void publishesSchemaAndExampleResources() throws Exception {
        try(InputStream schema = getClass().getResourceAsStream("/schemas/portability/hydra-capability-package-1.0.schema.json");
                InputStream example = getClass().getResourceAsStream("/examples/portability/people-validations.capability-package.json")) {
            assertThat(schema).isNotNull();
            assertThat(example).isNotNull();
            assertThat(mapper.readTree(schema).path("title").asText()).contains("Capability Package");
            assertThat(mapper.readTree(example).path("manifest").path("metamodelOrigin").path("technology").asText()).isEqualTo("EMF");
        }
    }

    private CapabilitySelection selection(String id, HydraCapabilityKind kind, String documentId, boolean required) {
        return new CapabilitySelection(id, kind, documentId, "1.0", required, false, Map.of());
    }

    private HydraCapabilityPackageManifest manifest(List<CapabilitySelection> selections, List<CapabilityDependency> dependencies, Map<String, String> checksums) {
        return new HydraCapabilityPackageManifest("1.0", "package", "Package", "1.0.0", "metamodel", new MetamodelOrigin(MetamodelOriginKind.IMPORTED, IntegrationTechnology.ECLIPSE_GLSP, "project.zip", "", "", "", "", Map.of(), Map.of()), selections, dependencies, List.of(), checksums, Map.of());
    }
}
