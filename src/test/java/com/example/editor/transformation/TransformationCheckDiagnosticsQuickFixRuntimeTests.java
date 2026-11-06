package com.example.editor.transformation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.example.editor.artifact.transaction.HydraArtifactTransactionService;
import com.example.editor.config.StorageProperties;
import com.example.editor.metamodel.application.CanonicalMetamodelApplicationService;
import com.example.editor.metamodel.canonical.CanonicalMetamodelProperties;
import com.example.editor.metamodel.canonical.JsonCanonicalMetamodelRepository;
import com.example.editor.metamodel.core.MetaAttribute;
import com.example.editor.metamodel.core.MetaClass;
import com.example.editor.metamodel.core.MetaClassKind;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.core.MetaPrimitiveType;
import com.example.editor.metamodel.core.MetaTypeReference;
import com.example.editor.metamodel.core.Multiplicity;
import com.example.editor.metamodel.validation.MetamodelValidationFingerprint;
import com.example.editor.metamodel.validation.NeutralMetamodelValidator;
import com.example.editor.persistence.LocalCanonicalDocumentStoreFactory;
import com.example.editor.semantic.expression.ExpressionDependencyAnalyzer;
import com.example.editor.semantic.expression.ExpressionTestFixtures;
import com.example.editor.semantic.expression.ExpressionTypeChecker;
import com.example.editor.testing.fault.HydraFaultInjectionProperties;
import com.example.editor.testing.fault.HydraFaultInjectionService;
import com.example.editor.transformation.core.TransformationBinding;
import com.example.editor.transformation.core.TransformationBindingKind;
import com.example.editor.transformation.core.TransformationCheckReport;
import com.example.editor.transformation.core.TransformationConversionMode;
import com.example.editor.transformation.core.TransformationDefinitionDocument;
import com.example.editor.transformation.core.TransformationDiagnostic;
import com.example.editor.transformation.core.TransformationDiagnosticSeverity;
import com.example.editor.transformation.core.TransformationEndpoint;
import com.example.editor.transformation.core.TransformationEndpointRole;
import com.example.editor.transformation.core.TransformationKind;
import com.example.editor.transformation.core.TransformationRule;
import com.example.editor.transformation.core.TransformationTargetPattern;
import com.example.editor.transformation.persistence.JsonTransformationRepository;
import com.example.editor.transformation.quickfix.TransformationQuickFix;
import com.example.editor.transformation.quickfix.TransformationQuickFixController;
import com.example.editor.transformation.quickfix.TransformationQuickFixReport;
import com.example.editor.transformation.quickfix.TransformationQuickFixService;
import com.example.editor.transformation.sync.JsonTransformationSynchronizationSessionRepository;
import com.example.editor.transformation.testing.persistence.JsonTransformationTestCaseRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Paso «Comprobar» del asistente guiado de transformaciones, parte de servicio: diagnosticos reales del comprobador y
 * correcciones rapidas reales (servicio y punto HTTP), con metamodelo y repositorio reales en disco temporal.
 * La pantalla que los muestra es interfaz y no se ejerce aqui.
 *
 * Etapa: transformation_author_guided.ck.
 * Oraculo: codigos, gravedad, regla y asignacion de cada diagnostico contra literales; la correccion propuesta se
 * vuelve a comprobar con el comprobador real y se guarda; el borrador original no cambia y nada se guarda al sugerir.
 */
class TransformationCheckDiagnosticsQuickFixRuntimeTests {
    private final JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();

    @Test
    @DisplayName("transformation_author_guided.ck: un error de tipos muestra su diagnostico y una conversion explicita que, aplicada, deja la definicion valida y guardable")
    void aTypeMismatchShowsItsDiagnosticAndAnExplicitConversionFixThatMakesTheDraftValid(@TempDir Path temporary) {
        Wired wired = wire(temporary);
        TransformationDefinitionDocument draft = m2m("transformation-guided-typed", List.of(personRule("rule:typed",
                binding("binding:age-to-name", ExpressionTestFixtures.NAME, ExpressionTestFixtures.AGE))));

        TransformationCheckReport report = wired.transformations().check(draft);
        assertThat(report.valid()).isFalse();
        assertThat(report.errorCount()).isGreaterThanOrEqualTo(1);
        TransformationDiagnostic conversion = report.diagnostics().stream()
                .filter(item -> item.code().equals("TYPE_CONVERSION_REQUIRED")).findFirst().orElseThrow();
        assertThat(conversion.severity()).isEqualTo(TransformationDiagnosticSeverity.ERROR);
        assertThat(conversion.ruleId()).isEqualTo("rule:typed");
        assertThat(conversion.bindingId()).isEqualTo("binding:age-to-name");
        assertThat(conversion.path()).endsWith(".valuePolicy");
        assertThat(conversion.title()).isEqualTo("Binding 'binding:age-to-name' needs an explicit conversion from INTEGER to STRING");
        assertThat(conversion.solution()).isNotBlank();

        TransformationQuickFixReport fixes = wired.quickFixes().suggest(draft);
        TransformationQuickFix fix = fixes.fixes().stream()
                .filter(item -> item.diagnosticCode().equals("TYPE_CONVERSION_REQUIRED")).findFirst().orElseThrow();
        assertThat(fixes.transformationId()).isEqualTo("transformation-guided-typed");
        assertThat(fix.id()).isEqualTo("fix:binding:age-to-name:conversion");
        assertThat(fix.ruleId()).isEqualTo("rule:typed");
        assertThat(fix.bindingId()).isEqualTo("binding:age-to-name");
        assertThat(fix.safeAutomatic()).isFalse();
        TransformationBinding proposed = fix.proposedTransformation().rules().getFirst().bindings().stream()
                .filter(item -> item.id().equals("binding:age-to-name")).findFirst().orElseThrow();
        assertThat(proposed.valuePolicy().conversionMode()).isEqualTo(TransformationConversionMode.TO_STRING);

        // El borrador original no se ha tocado y sugerir no guarda nada.
        TransformationBinding original = draft.rules().getFirst().bindings().getFirst();
        assertThat(original.valuePolicy().conversionMode()).isEqualTo(TransformationConversionMode.NONE);
        assertThat(wired.disk().listIds()).isEmpty();
        assertThatThrownBy(() -> wired.transformations().create(draft)).isInstanceOf(TransformationApplicationException.class);
        assertThat(wired.disk().listIds()).isEmpty();

        // Aplicar la correccion: el comprobador real ya no da error y la definicion se guarda y se relee de disco.
        TransformationCheckReport after = wired.transformations().check(fix.proposedTransformation());
        assertThat(after.valid()).isTrue();
        assertThat(after.diagnostics()).extracting(TransformationDiagnostic::code).doesNotContain("TYPE_CONVERSION_REQUIRED");
        wired.transformations().create(fix.proposedTransformation());
        TransformationBinding reread = wired.disk().find("transformation-guided-typed").orElseThrow().document()
                .rules().getFirst().bindings().stream().filter(item -> item.id().equals("binding:age-to-name"))
                .findFirst().orElseThrow();
        assertThat(reread.valuePolicy().conversionMode()).isEqualTo(TransformationConversionMode.TO_STRING);
    }

    @Test
    @DisplayName("transformation_author_guided.ck: un aviso de atributo obligatorio sin asignar propone la asignacion directa compatible")
    void aRequiredTargetWithoutMappingWarnsAndProposesTheCompatibleDirectMapping(@TempDir Path temporary) {
        Wired wired = wire(temporary);
        TransformationDefinitionDocument draft = m2m("transformation-guided-required", List.of(personRule("rule:person",
                binding("binding:age", ExpressionTestFixtures.AGE, ExpressionTestFixtures.AGE))));

        TransformationCheckReport report = wired.transformations().check(draft);
        assertThat(report.valid()).isTrue();
        TransformationDiagnostic warning = report.diagnostics().stream()
                .filter(item -> item.code().equals("REQUIRED_TARGET_ATTRIBUTE_UNMAPPED")).findFirst().orElseThrow();
        assertThat(warning.severity()).isEqualTo(TransformationDiagnosticSeverity.WARNING);
        assertThat(warning.ruleId()).isEqualTo("rule:person");

        TransformationQuickFix fix = wired.quickFixes().suggest(draft).fixes().stream()
                .filter(item -> item.diagnosticCode().equals("REQUIRED_TARGET_ATTRIBUTE_UNMAPPED")).findFirst().orElseThrow();
        assertThat(fix.id()).isEqualTo("fix:rule:person:map:" + ExpressionTestFixtures.NAME);
        assertThat(fix.bindingId()).isEqualTo("binding:" + ExpressionTestFixtures.NAME);
        TransformationBinding added = fix.proposedTransformation().rules().getFirst().bindings().stream()
                .filter(item -> item.id().equals("binding:" + ExpressionTestFixtures.NAME)).findFirst().orElseThrow();
        assertThat(added.targetFeatureId()).isEqualTo(ExpressionTestFixtures.NAME);
        assertThat(added.sourceFeatureId()).isEqualTo(ExpressionTestFixtures.NAME);
        assertThat(added.kind()).isEqualTo(TransformationBindingKind.DIRECT);
        assertThat(added.annotations()).containsEntry("hydra.quickFix", "required-compatible-name");
        assertThat(draft.rules().getFirst().bindings()).hasSize(1);

        TransformationCheckReport after = wired.transformations().check(fix.proposedTransformation());
        assertThat(after.valid()).isTrue();
        assertThat(after.diagnostics()).extracting(TransformationDiagnostic::code).doesNotContain("REQUIRED_TARGET_ATTRIBUTE_UNMAPPED");
    }

    @Test
    @DisplayName("transformation_author_guided.ck: sin problemas no hay diagnosticos de error ni correcciones, y el punto HTTP devuelve las mismas correcciones")
    void aCleanDraftHasNoFixesAndTheHttpEndpointReturnsTheSameFixesAsTheService(@TempDir Path temporary) throws Exception {
        Wired wired = wire(temporary);
        TransformationDefinitionDocument clean = m2m("transformation-guided-clean", List.of(personRule("rule:person",
                binding("binding:age", ExpressionTestFixtures.AGE, ExpressionTestFixtures.AGE),
                binding("binding:name", ExpressionTestFixtures.NAME, ExpressionTestFixtures.NAME))));
        TransformationCheckReport cleanReport = wired.transformations().check(clean);
        assertThat(cleanReport.valid()).isTrue();
        assertThat(cleanReport.diagnostics()).extracting(TransformationDiagnostic::code)
                .doesNotContain("TYPE_CONVERSION_REQUIRED", "REQUIRED_TARGET_ATTRIBUTE_UNMAPPED");
        assertThat(wired.quickFixes().suggest(clean).fixes()).isEmpty();

        MockMvc mvc = MockMvcBuilders.standaloneSetup(new TransformationQuickFixController(wired.quickFixes())).build();
        TransformationDefinitionDocument draft = m2m("transformation-guided-http", List.of(personRule("rule:typed",
                binding("binding:age-to-name", ExpressionTestFixtures.NAME, ExpressionTestFixtures.AGE))));
        MockHttpServletResponse response = mvc.perform(post("/api/transformations/quick-fixes")
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(draft))).andReturn().getResponse();
        assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(200);
        JsonNode body = mapper.readTree(response.getContentAsString(StandardCharsets.UTF_8));
        assertThat(body.get("transformationId").asString()).isEqualTo("transformation-guided-http");
        List<String> httpIds = new java.util.ArrayList<>();
        for (JsonNode item : body.get("fixes")) httpIds.add(item.get("id").asString());
        assertThat(httpIds).contains("fix:binding:age-to-name:conversion");
        assertThat(httpIds).isEqualTo(wired.quickFixes().suggest(draft).fixes().stream().map(TransformationQuickFix::id).toList());
        assertThat(wired.disk().listIds()).isEmpty();
    }

    private record Wired(TransformationApplicationService transformations, TransformationQuickFixService quickFixes,
            JsonTransformationRepository disk) { }

    private Wired wire(Path temporary) {
        StorageProperties storage = new StorageProperties(temporary.resolve("metamodels"), temporary.resolve("models"),
                temporary.resolve("notations"), temporary.resolve("validations"), temporary.resolve("quickfixes"),
                temporary.resolve("generators"), temporary.resolve("assets"), temporary.resolve("exports"), true);
        CanonicalMetamodelApplicationService metamodels = new CanonicalMetamodelApplicationService(
                new JsonCanonicalMetamodelRepository(storage, new CanonicalMetamodelProperties(), mapper), new NeutralMetamodelValidator());
        metamodels.create(metamodelWithRequiredName());

        TransformationProperties properties = new TransformationProperties();
        properties.setDirectory(temporary.resolve("transformations"));
        properties.setTestDirectory(temporary.resolve("transformation-tests"));
        properties.setSynchronizationSessionDirectory(temporary.resolve("transformation-sync-sessions"));
        properties.setSynchronizationHistoryDirectory(temporary.resolve("transformation-sync-history"));
        TransformationDefinitionChecker checker = new TransformationDefinitionChecker(
                new ExpressionTypeChecker(new ExpressionDependencyAnalyzer()));
        HydraArtifactTransactionService transactions = new HydraArtifactTransactionService(
                temporary.resolve("artifact-transactions").toString(), new HydraFaultInjectionService(new HydraFaultInjectionProperties()));
        TransformationApplicationService service = new TransformationApplicationService(
                new JsonTransformationRepository(properties, mapper), metamodels,
                new MetamodelValidationFingerprint(mapper), checker,
                new JsonTransformationTestCaseRepository(properties, mapper, new LocalCanonicalDocumentStoreFactory()),
                new JsonTransformationSynchronizationSessionRepository(properties, mapper, new LocalCanonicalDocumentStoreFactory()),
                transactions);
        return new Wired(service, new TransformationQuickFixService(service, metamodels),
                new JsonTransformationRepository(properties, mapper));
    }

    /** Metamodelo «library» con Person.age opcional (entero) y Person.name obligatorio (texto). */
    private static MetaModel metamodelWithRequiredName() {
        MetaAttribute age = new MetaAttribute(ExpressionTestFixtures.AGE, "age", MetaTypeReference.primitive(MetaPrimitiveType.INTEGER),
                Multiplicity.optional(), true, true, "", Map.of());
        MetaAttribute name = new MetaAttribute(ExpressionTestFixtures.NAME, "name", MetaTypeReference.primitive(MetaPrimitiveType.STRING),
                Multiplicity.one(), true, true, "", Map.of());
        MetaClass person = new MetaClass(ExpressionTestFixtures.PERSON, "Person", MetaClassKind.CLASS, List.of(age, name), List.of(), Map.of());
        return new MetaModel(MetaModel.CURRENT_SCHEMA_VERSION, ExpressionTestFixtures.METAMODEL_ID, "Library",
                List.of(person), List.of(), List.of(), List.of(), Map.of());
    }

    private static TransformationBinding binding(String id, String targetFeature, String sourceFeature) {
        return new TransformationBinding(id, "person", targetFeature, TransformationBindingKind.DIRECT,
                sourceFeature, null, null, null, Map.of());
    }

    private static TransformationRule personRule(String id, TransformationBinding... bindings) {
        return new TransformationRule(id, "Rule " + id, true, "source", ExpressionTestFixtures.PERSON, "personSource", null,
                List.of(new TransformationTargetPattern("target:person", "target", ExpressionTestFixtures.PERSON, "person", Map.of())),
                List.of(bindings), Map.of());
    }

    private static List<TransformationEndpoint> endpoints(TransformationEndpointRole role, String id) {
        return List.of(new TransformationEndpoint(id, role, ExpressionTestFixtures.METAMODEL_ID, "", id, true, Map.of()));
    }

    private static TransformationDefinitionDocument m2m(String id, List<TransformationRule> rules) {
        return new TransformationDefinitionDocument("1.0", id, "M2M " + id, TransformationKind.M2M,
                endpoints(TransformationEndpointRole.SOURCE, "source"), endpoints(TransformationEndpointRole.TARGET, "target"),
                rules, List.of(), null, null, null, Map.of());
    }
}
