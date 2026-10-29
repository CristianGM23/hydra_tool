package com.example.editor.transformation.export;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.editor.config.StorageProperties;
import com.example.editor.metamodel.application.CanonicalMetamodelApplicationService;
import com.example.editor.metamodel.canonical.CanonicalMetamodelProperties;
import com.example.editor.metamodel.canonical.JsonCanonicalMetamodelRepository;
import com.example.editor.metamodel.validation.NeutralMetamodelValidator;
import com.example.editor.semantic.expression.ExpressionDependencyAnalyzer;
import com.example.editor.semantic.expression.ExpressionTestFixtures;
import com.example.editor.semantic.expression.ExpressionTypeChecker;
import com.example.editor.transformation.TransformationDefinitionChecker;
import com.example.editor.transformation.core.TransformationBinding;
import com.example.editor.transformation.core.TransformationBindingKind;
import com.example.editor.transformation.core.TransformationConflictPolicy;
import com.example.editor.transformation.core.TransformationDefinitionDocument;
import com.example.editor.transformation.core.TransformationDirectionality;
import com.example.editor.transformation.core.TransformationEndpoint;
import com.example.editor.transformation.core.TransformationEndpointRole;
import com.example.editor.transformation.core.TransformationExecutionDirection;
import com.example.editor.transformation.core.TransformationGenerationRule;
import com.example.editor.transformation.core.TransformationKind;
import com.example.editor.transformation.core.TransformationRule;
import com.example.editor.transformation.core.TransformationRuleDirection;
import com.example.editor.transformation.core.TransformationSynchronizationPolicy;
import com.example.editor.transformation.core.TransformationTargetPattern;
import com.example.editor.transformation.core.TransformationTextToModelConfiguration;
import com.example.editor.transformation.core.TransformationTracePolicy;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Evidencia real, sin mocks, de la exportacion a lenguajes de transformacion con el servicio de
 * exportacion real (transformation_export_native.lg/cp/ex): registro real de adaptadores,
 * comprobador canonico real y metamodelo guardado en disco. Comprueba los ficheros con ruta y
 * contenido (literales), la fidelidad, las puertas de compatibilidad por tipo y direccion y que
 * una definicion invalida no produce ficheros.
 */
class TransformationExportServiceRealIntegrationTests {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    @DisplayName("transformation_export_native.ex: M2M valido FORWARD a ATL, ETL y JAVA devuelve ficheros con ruta, contenido y fidelidad")
    void validM2mForwardExportsFilesForAtlEtlAndJava(@TempDir Path temporary) throws Exception {
        TransformationExportService service = service(temporary);
        TransformationDefinitionDocument document = m2m(false);

        TransformationExportResult atl = service.export(new TransformationExportRequest(document, TransformationExportLanguage.ATL));
        TransformationExportResult etl = service.export(new TransformationExportRequest(document, TransformationExportLanguage.ETL,
                TransformationExecutionDirection.FORWARD));
        TransformationExportResult java = service.export(new TransformationExportRequest(document, TransformationExportLanguage.JAVA));

        assertThat(atl.compatible()).isTrue();
        assertThat(atl.language()).isEqualTo(TransformationExportLanguage.ATL);
        assertThat(atl.fidelity()).isEqualTo(TransformationExportFidelity.SUBSET);
        assertThat(atl.artifacts()).singleElement().satisfies(file -> {
            assertThat(file.path()).isEqualTo("Export.atl");
            assertThat(file.content()).isEqualTo("module Export;\ncreate OUT : TARGET from IN : SOURCE;\n\n"
                    + "rule Person_to_Person {\n  from personSource : SOURCE!Person\n  to person : TARGET!Person (\n"
                    + "    name <- personSource.name\n  )\n}\n\n");
        });
        assertThat(etl.compatible()).isTrue();
        assertThat(etl.artifacts()).singleElement().satisfies(file -> assertThat(file.content())
                .contains("transform personSource : SOURCE!Person", "person.name = personSource.name"));

        assertThat(java.compatible()).isTrue();
        assertThat(java.fidelity()).isEqualTo(TransformationExportFidelity.EXACT);
        assertThat(java.artifacts()).extracting(TransformationExportArtifact::path).containsExactly("native/native-plan.json",
                "transformation.json", "src/main/java/generated/HydraTransformation.java", "README.md");
        JsonNode plan = MAPPER.readTree(content(java, "native/native-plan.json"));
        assertThat(plan.get("kind").asString()).isEqualTo("M2M");
        assertThat(plan.get("transformationId").asString()).isEqualTo("transformation:export");
        assertThat(plan.get("rules").get(0).get("ruleId").asString()).isEqualTo("rule:person");
        assertThat(plan.get("rules").get(0).get("bindings").get(0).get("sourceFeatureId").asString()).isEqualTo(ExpressionTestFixtures.NAME);
        assertThat(MAPPER.readTree(content(java, "transformation.json")).get("id").asString()).isEqualTo("transformation:export");
        assertThat(content(java, "src/main/java/generated/HydraTransformation.java"))
                .contains("public static String transformationId() { return \"transformation:export\"; }");
    }

    @Test
    @DisplayName("transformation_export_native.cp: REVERSE en M2M no bidireccional, REVERSE en M2T/T2M y ATL con M2T se rechazan")
    void incompatibleDirectionOrKindIsBlockedWithoutFiles(@TempDir Path temporary) {
        TransformationExportService service = service(temporary);

        TransformationExportResult reverseUnidirectional = service.export(new TransformationExportRequest(m2m(false),
                TransformationExportLanguage.JAVA, TransformationExecutionDirection.REVERSE));
        TransformationExportResult m2tReverse = service.export(new TransformationExportRequest(m2t(),
                TransformationExportLanguage.JAVA, TransformationExecutionDirection.REVERSE));
        TransformationExportResult t2mReverse = service.export(new TransformationExportRequest(t2m(),
                TransformationExportLanguage.JAVA, TransformationExecutionDirection.REVERSE));
        TransformationExportResult atlWithM2t = service.export(new TransformationExportRequest(m2t(), TransformationExportLanguage.ATL));

        assertBlocked(reverseUnidirectional, "REVERSE_REQUIRES_BIDIRECTIONAL");
        assertBlocked(m2tReverse, "DIRECTION_NOT_SUPPORTED");
        assertBlocked(t2mReverse, "DIRECTION_NOT_SUPPORTED");
        assertBlocked(atlWithM2t, "UNSUPPORTED_KIND");
        // La misma definicion M2T si es compatible con un lenguaje que la soporta.
        TransformationExportResult javaWithM2t = service.export(new TransformationExportRequest(m2t(), TransformationExportLanguage.JAVA));
        assertThat(javaWithM2t.compatible()).isTrue();
        assertThat(javaWithM2t.fidelity()).isEqualTo(TransformationExportFidelity.EXACT);
    }

    @Test
    @DisplayName("transformation_export_native.cp: una definicion invalida devuelve CANONICAL_* y ningun fichero")
    void invalidDefinitionIsReportedWithCanonicalCodesAndNoFiles(@TempDir Path temporary) {
        TransformationExportService service = service(temporary);
        TransformationDefinitionDocument invalid = withSourceType(m2m(false), "class:Ghost");

        for(TransformationExportLanguage language : List.of(TransformationExportLanguage.ATL, TransformationExportLanguage.JAVA)) {
            TransformationExportResult result = service.export(new TransformationExportRequest(invalid, language));
            assertThat(result.compatible()).as(language.name()).isFalse();
            assertThat(result.artifacts()).as(language.name()).isEmpty();
            assertThat(result.diagnostics()).extracting(TransformationExportDiagnostic::code).contains("CANONICAL_UNKNOWN_SOURCE_TYPE");
            assertThat(result.diagnostics()).extracting(TransformationExportDiagnostic::severity).containsOnly("ERROR");
        }
    }

    @Test
    @DisplayName("transformation_export_native.ex: una definicion bidireccional exporta la vista directa o la inversa segun la direccion")
    void bidirectionalDefinitionExportsTheRequestedDirectionalView(@TempDir Path temporary) throws Exception {
        TransformationExportService service = service(temporary);
        TransformationDefinitionDocument document = bidirectional();

        TransformationExportResult forward = service.export(new TransformationExportRequest(document, TransformationExportLanguage.JAVA,
                TransformationExecutionDirection.FORWARD));
        TransformationExportResult reverse = service.export(new TransformationExportRequest(document, TransformationExportLanguage.JAVA,
                TransformationExecutionDirection.REVERSE));
        TransformationExportResult atlReverse = service.export(new TransformationExportRequest(document, TransformationExportLanguage.ATL,
                TransformationExecutionDirection.REVERSE));

        assertThat(forward.compatible()).isTrue();
        assertThat(reverse.compatible()).isTrue();
        JsonNode forwardRules = MAPPER.readTree(content(forward, "native/native-plan.json")).get("rules");
        JsonNode reverseRules = MAPPER.readTree(content(reverse, "native/native-plan.json")).get("rules");
        assertThat(forwardRules.size()).isEqualTo(1);
        assertThat(forwardRules.get(0).get("ruleId").asString()).isEqualTo("rule:person");
        assertThat(reverseRules.size()).isEqualTo(1);
        assertThat(reverseRules.get(0).get("ruleId").asString()).isEqualTo("rule:person-reverse");
        assertThat(atlReverse.compatible()).isTrue();
        assertThat(atlReverse.artifacts()).singleElement().satisfies(file -> assertThat(file.content())
                .contains("rule Person_to_Person__reverse_", "from personTarget : SOURCE!Person", "name <- personTarget.name"));
    }

    private static void assertBlocked(TransformationExportResult result, String code) {
        assertThat(result.compatible()).as(code).isFalse();
        assertThat(result.artifacts()).as(code).isEmpty();
        assertThat(result.diagnostics()).as(code).extracting(TransformationExportDiagnostic::code).contains(code);
    }

    private static String content(TransformationExportResult result, String path) {
        return result.artifacts().stream().filter(item -> item.path().equals(path)).findFirst().orElseThrow().content();
    }

    private static List<TransformationEndpoint> endpoints(TransformationEndpointRole role, String id) {
        return List.of(new TransformationEndpoint(id, role, ExpressionTestFixtures.METAMODEL_ID, "", id, true, Map.of()));
    }

    private static TransformationDefinitionDocument m2m(boolean guarded) {
        TransformationBinding binding = new TransformationBinding("binding:name", "person", ExpressionTestFixtures.NAME,
                TransformationBindingKind.DIRECT, ExpressionTestFixtures.NAME, null, null, null, Map.of());
        TransformationRule rule = new TransformationRule("rule:person", "Person to Person", true, "source", ExpressionTestFixtures.PERSON,
                "personSource", guarded ? ExpressionTestFixtures.positiveAge() : null,
                List.of(new TransformationTargetPattern("target:person", "target", ExpressionTestFixtures.PERSON, "person", Map.of())),
                List.of(binding), Map.of());
        return new TransformationDefinitionDocument("1.0", "transformation:export", "Export", TransformationKind.M2M,
                endpoints(TransformationEndpointRole.SOURCE, "source"), endpoints(TransformationEndpointRole.TARGET, "target"),
                List.of(rule), List.of(), null, null, null, Map.of());
    }

    private static TransformationDefinitionDocument withSourceType(TransformationDefinitionDocument base, String sourceType) {
        TransformationRule rule = base.rules().get(0);
        TransformationRule changed = new TransformationRule(rule.id(), rule.name(), rule.enabled(), rule.sourceEndpointId(), sourceType,
                rule.sourceAlias(), rule.guard(), rule.targets(), rule.bindings(), rule.annotations());
        return new TransformationDefinitionDocument(base.schemaVersion(), base.id(), base.name(), base.kind(), base.sources(),
                base.targets(), List.of(changed), base.generationRules(), base.textToModel(), base.executionPolicy(), base.tracePolicy(),
                base.annotations());
    }

    private static TransformationDefinitionDocument bidirectional() {
        TransformationRule forward = new TransformationRule("rule:person", "Person to Person", true, "source",
                ExpressionTestFixtures.PERSON, "personSource", null,
                List.of(new TransformationTargetPattern("target:person", "target", ExpressionTestFixtures.PERSON, "person", Map.of())),
                List.of(new TransformationBinding("binding:name", "person", ExpressionTestFixtures.NAME, TransformationBindingKind.DIRECT,
                        ExpressionTestFixtures.NAME, null, null, null, Map.of())),
                Map.of(), TransformationRuleDirection.FORWARD, "corr:person");
        TransformationRule reverse = new TransformationRule("rule:person-reverse", "Person to Person (reverse)", true, "target",
                ExpressionTestFixtures.PERSON, "personTarget", null,
                List.of(new TransformationTargetPattern("source:person", "source", ExpressionTestFixtures.PERSON, "person", Map.of())),
                List.of(new TransformationBinding("binding:name-reverse", "person", ExpressionTestFixtures.NAME, TransformationBindingKind.DIRECT,
                        ExpressionTestFixtures.NAME, null, null, null, Map.of())),
                Map.of(), TransformationRuleDirection.REVERSE, "corr:person");
        return new TransformationDefinitionDocument("1.1", "transformation:bidirectional", "Export", null,
                TransformationDirectionality.BIDIRECTIONAL,
                new TransformationSynchronizationPolicy(TransformationConflictPolicy.FAIL_ON_CONFLICT, false, true),
                endpoints(TransformationEndpointRole.SOURCE, "source"), endpoints(TransformationEndpointRole.TARGET, "target"),
                List.of(forward, reverse), List.of(), null, null, TransformationTracePolicy.defaults(), Map.of());
    }

    private static TransformationDefinitionDocument m2t() {
        TransformationGenerationRule rule = new TransformationGenerationRule("gen:person", "Generate person", true, "source",
                ExpressionTestFixtures.PERSON, null, "generated/{{attr:" + ExpressionTestFixtures.NAME + "}}.txt",
                "Name={{attr:" + ExpressionTestFixtures.NAME + "}}", null, Map.of());
        return new TransformationDefinitionDocument("1.0", "transformation:m2t", "Generate", TransformationKind.M2T,
                endpoints(TransformationEndpointRole.SOURCE, "source"), List.of(), List.of(), List.of(rule), null, null, null, Map.of());
    }

    private static TransformationDefinitionDocument t2m() {
        return new TransformationDefinitionDocument("1.1", "transformation:t2m", "Text to model", TransformationKind.T2M,
                List.of(), List.of(), List.of(), List.of(), new TransformationTextToModelConfiguration("grammar:people", "model.hydra", ""),
                null, null, Map.of());
    }

    private static TransformationExportService service(Path temporary) {
        ObjectMapper mapper = new ObjectMapper();
        tools.jackson.databind.json.JsonMapper jsonMapper = tools.jackson.databind.json.JsonMapper.builder().findAndAddModules().build();
        StorageProperties storage = new StorageProperties(temporary.resolve("metamodels"), temporary.resolve("models"),
                temporary.resolve("notations"), temporary.resolve("validations"), temporary.resolve("quickfixes"),
                temporary.resolve("generators"), temporary.resolve("assets"), temporary.resolve("exports"), true);
        CanonicalMetamodelApplicationService metamodels = new CanonicalMetamodelApplicationService(
                new JsonCanonicalMetamodelRepository(storage, new CanonicalMetamodelProperties(), jsonMapper), new NeutralMetamodelValidator());
        metamodels.create(ExpressionTestFixtures.metamodel());
        TransformationLanguageAdapterRegistry registry = new TransformationLanguageAdapterRegistry(List.of(
                new AtlTransformationLanguageAdapter(), new EtlTransformationLanguageAdapter(),
                new JavaTransformationLanguageAdapter(mapper), new XtendTransformationLanguageAdapter(mapper),
                new PythonTransformationLanguageAdapter(mapper), new GoTransformationLanguageAdapter(mapper),
                new JavaScriptTransformationLanguageAdapter(mapper), new TypeScriptTransformationLanguageAdapter(mapper),
                new AcceleoTransformationLanguageAdapter(), new EglTransformationLanguageAdapter()));
        return new TransformationExportService(registry, new TransformationDefinitionChecker(
                new ExpressionTypeChecker(new ExpressionDependencyAnalyzer())), metamodels);
    }
}
