package com.example.editor.transformation.text;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.editor.artifact.transaction.HydraArtifactTransactionService;
import com.example.editor.config.StorageProperties;
import com.example.editor.metamodel.application.CanonicalMetamodelApplicationService;
import com.example.editor.metamodel.canonical.CanonicalMetamodelProperties;
import com.example.editor.metamodel.canonical.JsonCanonicalMetamodelRepository;
import com.example.editor.metamodel.validation.MetamodelValidationFingerprint;
import com.example.editor.metamodel.validation.NeutralMetamodelValidator;
import com.example.editor.persistence.LocalCanonicalDocumentStoreFactory;
import com.example.editor.semantic.expression.ExpressionDependencyAnalyzer;
import com.example.editor.semantic.expression.ExpressionEvaluator;
import com.example.editor.semantic.expression.ExpressionTestFixtures;
import com.example.editor.semantic.expression.ExpressionTypeChecker;
import com.example.editor.testing.fault.HydraFaultInjectionProperties;
import com.example.editor.testing.fault.HydraFaultInjectionService;
import com.example.editor.transformation.TransformationApplicationException;
import com.example.editor.transformation.TransformationApplicationService;
import com.example.editor.transformation.TransformationDefinitionChecker;
import com.example.editor.transformation.TransformationProperties;
import com.example.editor.transformation.core.GenerationOverwritePolicy;
import com.example.editor.transformation.core.TransformationCheckReport;
import com.example.editor.transformation.core.TransformationDefinitionDocument;
import com.example.editor.transformation.core.TransformationDiagnostic;
import com.example.editor.transformation.core.TransformationEndpoint;
import com.example.editor.transformation.core.TransformationEndpointRole;
import com.example.editor.transformation.core.TransformationGenerationRule;
import com.example.editor.transformation.core.TransformationKind;
import com.example.editor.transformation.persistence.JsonTransformationRepository;
import com.example.editor.transformation.runtime.TransformationExecutionException;
import com.example.editor.transformation.sync.JsonTransformationSynchronizationSessionRepository;
import com.example.editor.transformation.testing.persistence.JsonTransformationTestCaseRepository;
import com.example.editor.validation.model.CanonicalModelDocument;
import com.example.editor.validation.model.CanonicalModelElement;
import tools.jackson.databind.json.JsonMapper;

/**
 * Evidencia real, sin mocks, de la generacion M2T con plantillas: «Editar la plantilla de
 * contenido» (transformation_author_generation.ed: las marcas {{id}}, {{classId}} y
 * {{attr:...}} se interpretan de verdad en ruta y contenido) y «Activar o borrar»
 * (transformation_author_generation.en: una regla desactivada no genera). Usa el servicio real,
 * el runtime M2T real, el comprobador real y un repositorio JSON en @TempDir.
 */
class TransformationTextGenerationServiceIntegrationTests {
    private static final String NAME_TOKEN = "{{attr:" + ExpressionTestFixtures.NAME + "}}";
    private static final String AGE_TOKEN = "{{attr:" + ExpressionTestFixtures.AGE + "}}";

    @Test
    @DisplayName("transformation_author_generation.ed: ruta y contenido renderizados con {{id}}, {{classId}} y {{attr:...}}")
    void rendersPathAndContentTokensForEachMatchingElement(@TempDir Path temporary) {
        Fixture fixture = fixture(temporary);
        TransformationDefinitionDocument document = document("transformation-m2t-render", List.of(
                rule("rule:person", true, ExpressionTestFixtures.PERSON, "people/{{id}}.txt",
                        "class={{classId}};name=" + NAME_TOKEN + ";age=" + AGE_TOKEN + ";id={{ id }}", GenerationOverwritePolicy.FAIL),
                rule("rule:employee", true, ExpressionTestFixtures.EMPLOYEE, "employees/" + NAME_TOKEN + ".txt",
                        "age=" + AGE_TOKEN + ";missing={{attr:attribute:Person:nickname}}.", GenerationOverwritePolicy.FAIL)));

        TransformationGenerationResult result = fixture.text().preview(document, model());

        // El runtime compara classId exacto: Employee (Bob) solo casa con la regla de Employee.
        assertThat(result.transformationId()).isEqualTo("transformation-m2t-render");
        assertThat(result.artifacts()).containsExactly(
                new TransformationTextArtifact("people/object:alice.txt",
                        "class=class:Person;name=Alice;age=35;id=object:alice", "rule:person", "object:alice"),
                new TransformationTextArtifact("employees/Bob.txt", "age=-2;missing=.", "rule:employee", "object:bob"),
                new TransformationTextArtifact("people/object:carol.txt",
                        "class=class:Person;name=Carol;age=41;id=object:carol", "rule:person", "object:carol"));
    }

    @Test
    @DisplayName("transformation_author_generation.en: una regla desactivada no genera artefactos")
    void disabledRuleDoesNotGenerate(@TempDir Path temporary) {
        Fixture fixture = fixture(temporary);
        TransformationDefinitionDocument onlyDisabled = document("transformation-m2t-disabled", List.of(
                rule("rule:person", false, ExpressionTestFixtures.PERSON, "people/{{id}}.txt", "x", GenerationOverwritePolicy.FAIL)));
        TransformationDefinitionDocument mixed = document("transformation-m2t-mixed", List.of(
                rule("rule:off", false, ExpressionTestFixtures.PERSON, "off/{{id}}.txt", "off", GenerationOverwritePolicy.FAIL),
                rule("rule:on", true, ExpressionTestFixtures.PERSON, "on/{{id}}.txt", "on", GenerationOverwritePolicy.FAIL)));

        assertThat(fixture.text().preview(onlyDisabled, model()).artifacts()).isEmpty();
        assertThat(fixture.text().preview(mixed, model()).artifacts()).extracting(TransformationTextArtifact::path)
                .containsExactly("on/object:alice.txt", "on/object:carol.txt");
    }

    @Test
    @DisplayName("transformation_author_generation.en: colision de ruta con FAIL lanza excepcion; con REPLACE gana la ultima")
    void pathCollisionFailsOrReplacesAccordingToTheOverwritePolicy(@TempDir Path temporary) {
        Fixture fixture = fixture(temporary);
        TransformationDefinitionDocument failing = document("transformation-m2t-collision", List.of(
                rule("rule:first", true, ExpressionTestFixtures.PERSON, "out/fixed.txt", "first", GenerationOverwritePolicy.FAIL),
                rule("rule:second", true, ExpressionTestFixtures.PERSON, "out/fixed.txt", "second", GenerationOverwritePolicy.FAIL)));
        TransformationDefinitionDocument replacing = document("transformation-m2t-replace", List.of(
                rule("rule:first", true, ExpressionTestFixtures.PERSON, "out/fixed.txt", "first", GenerationOverwritePolicy.REPLACE),
                rule("rule:second", true, ExpressionTestFixtures.PERSON, "out/fixed.txt", "second", GenerationOverwritePolicy.REPLACE)));

        assertThatThrownBy(() -> fixture.text().preview(failing, model()))
                .isInstanceOf(TransformationExecutionException.class)
                .hasMessage("Generated artifact collision: out/fixed.txt");
        assertThat(fixture.text().preview(replacing, model()).artifacts()).containsExactly(
                new TransformationTextArtifact("out/fixed.txt", "second", "rule:second", "object:carol"));
    }

    @Test
    @DisplayName("transformation_author_generation.ed: ruta insegura (literal o producida por un dato) bloqueada")
    void unsafePathsAreBlockedBothStaticallyAndAtRenderTime(@TempDir Path temporary) {
        Fixture fixture = fixture(temporary);
        TransformationDefinitionDocument literalEscape = document("transformation-m2t-escape", List.of(
                rule("rule:escape", true, ExpressionTestFixtures.PERSON, "../evil.txt", "x", GenerationOverwritePolicy.FAIL)));
        TransformationDefinitionDocument dataEscape = document("transformation-m2t-data-escape", List.of(
                rule("rule:data", true, ExpressionTestFixtures.PERSON, "out/" + NAME_TOKEN + ".txt", "x", GenerationOverwritePolicy.FAIL)));

        TransformationCheckReport report = fixture.checker().check(literalEscape, List.of(ExpressionTestFixtures.metamodel()), List.of());
        assertThat(report.valid()).isFalse();
        assertThat(report.diagnostics()).extracting(TransformationDiagnostic::code).contains("UNSAFE_OUTPUT_PATH");
        assertThatThrownBy(() -> fixture.text().preview(literalEscape, model()))
                .isInstanceOf(TransformationExecutionException.class)
                .hasMessage("M2T preview contains blocking diagnostics");

        // La plantilla es estatica y valida, pero el valor del atributo introduce «../» al renderizar.
        CanonicalModelElement mallory = new CanonicalModelElement("object:mallory", ExpressionTestFixtures.PERSON,
                Map.of(ExpressionTestFixtures.NAME, "../evil"), Map.of());
        CanonicalModelDocument hostile = new CanonicalModelDocument("1.0", "library-model", ExpressionTestFixtures.METAMODEL_ID,
                "etag-1", "Hostile", List.of("object:mallory"), List.of(mallory), List.of(), Map.of());
        assertThatThrownBy(() -> fixture.text().preview(dataEscape, hostile))
                .isInstanceOf(TransformationExecutionException.class)
                .hasMessage("Generated artifact path must be relative and portable: out/../evil.txt");
    }

    @Test
    @DisplayName("transformation_author_generation.ed: definicion con errores bloqueantes rechazada al guardar y al previsualizar")
    void definitionWithBlockingErrorsIsRejectedAndNeverPersisted(@TempDir Path temporary) {
        Fixture fixture = fixture(temporary);
        TransformationDefinitionDocument ghostContext = document("transformation-m2t-ghost", List.of(
                rule("rule:ghost", true, "class:Ghost", "ghost/{{id}}.txt", "x", GenerationOverwritePolicy.FAIL)));

        assertThatThrownBy(() -> fixture.text().preview(ghostContext, model()))
                .isInstanceOf(TransformationExecutionException.class)
                .hasMessage("M2T preview contains blocking diagnostics");
        assertThatThrownBy(() -> fixture.transformations().create(ghostContext))
                .isInstanceOf(TransformationApplicationException.class)
                .extracting(exception -> ((TransformationApplicationException) exception).getReason())
                .isEqualTo(TransformationApplicationException.Reason.INVALID_REQUEST);
        assertThat(new JsonTransformationRepository(fixture.properties(), JsonMapper.builder().findAndAddModules().build())
                .find("transformation-m2t-ghost")).isEmpty();
    }

    @Test
    @DisplayName("transformation_author_generation.ed/en: las reglas guardadas con la definicion se releen y generan lo mismo")
    void savedRulesAreReloadedFromDiskAndGenerateTheSameArtifacts(@TempDir Path temporary) {
        Fixture fixture = fixture(temporary);
        TransformationDefinitionDocument document = document("transformation-m2t-saved", List.of(
                rule("rule:on", true, ExpressionTestFixtures.PERSON, "on/" + NAME_TOKEN + ".txt", "Hello " + NAME_TOKEN + " ({{classId}})",
                        GenerationOverwritePolicy.FAIL),
                rule("rule:off", false, ExpressionTestFixtures.PERSON, "off/{{id}}.txt", "never", GenerationOverwritePolicy.FAIL)));

        fixture.transformations().create(document);

        TransformationDefinitionDocument reread = new JsonTransformationRepository(fixture.properties(),
                JsonMapper.builder().findAndAddModules().build()).find("transformation-m2t-saved").orElseThrow().document();
        assertThat(reread.kind()).isEqualTo(TransformationKind.M2T);
        assertThat(reread.generationRules()).extracting(TransformationGenerationRule::id, TransformationGenerationRule::enabled,
                TransformationGenerationRule::outputPathTemplate, TransformationGenerationRule::contentTemplate)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("rule:on", true, "on/" + NAME_TOKEN + ".txt", "Hello " + NAME_TOKEN + " ({{classId}})"),
                        org.assertj.core.groups.Tuple.tuple("rule:off", false, "off/{{id}}.txt", "never"));

        TransformationGenerationResult executed = fixture.text().execute("transformation-m2t-saved", model());
        assertThat(executed.artifacts()).containsExactly(
                new TransformationTextArtifact("on/Alice.txt", "Hello Alice (class:Person)", "rule:on", "object:alice"),
                new TransformationTextArtifact("on/Carol.txt", "Hello Carol (class:Person)", "rule:on", "object:carol"));
    }

    private static CanonicalModelDocument model() {
        CanonicalModelElement alice = new CanonicalModelElement("object:alice", ExpressionTestFixtures.PERSON,
                Map.of(ExpressionTestFixtures.AGE, 35, ExpressionTestFixtures.NAME, "Alice"), Map.of());
        CanonicalModelElement bob = new CanonicalModelElement("object:bob", ExpressionTestFixtures.EMPLOYEE,
                Map.of(ExpressionTestFixtures.AGE, -2, ExpressionTestFixtures.NAME, "Bob"), Map.of());
        CanonicalModelElement carol = new CanonicalModelElement("object:carol", ExpressionTestFixtures.PERSON,
                Map.of(ExpressionTestFixtures.AGE, 41, ExpressionTestFixtures.NAME, "Carol"), Map.of());
        return new CanonicalModelDocument("1.0", "library-model", ExpressionTestFixtures.METAMODEL_ID, "etag-1", "Library model",
                List.of(), List.of(alice, bob, carol), List.of(), Map.of());
    }

    private static TransformationGenerationRule rule(String id, boolean enabled, String contextTypeId, String path, String content,
            GenerationOverwritePolicy policy) {
        return new TransformationGenerationRule(id, id, enabled, "source", contextTypeId, null, path, content, policy, Map.of());
    }

    private static TransformationDefinitionDocument document(String id, List<TransformationGenerationRule> rules) {
        return new TransformationDefinitionDocument("1.0", id, "M2T " + id, TransformationKind.M2T,
                List.of(new TransformationEndpoint("source", TransformationEndpointRole.SOURCE, ExpressionTestFixtures.METAMODEL_ID, "", "source", true, Map.of())),
                List.of(), List.of(), rules, null, null, null, Map.of());
    }

    private record Fixture(TransformationTextGenerationService text, TransformationApplicationService transformations,
            TransformationDefinitionChecker checker, TransformationProperties properties) { }

    private static Fixture fixture(Path temporary) {
        JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();
        StorageProperties storage = new StorageProperties(temporary.resolve("metamodels"), temporary.resolve("models"),
                temporary.resolve("notations"), temporary.resolve("validations"), temporary.resolve("quickfixes"),
                temporary.resolve("generators"), temporary.resolve("assets"), temporary.resolve("exports"), true);
        CanonicalMetamodelApplicationService metamodels = new CanonicalMetamodelApplicationService(
                new JsonCanonicalMetamodelRepository(storage, new CanonicalMetamodelProperties(), mapper), new NeutralMetamodelValidator());
        metamodels.create(ExpressionTestFixtures.metamodel());

        TransformationProperties properties = new TransformationProperties();
        properties.setDirectory(temporary.resolve("transformations"));
        properties.setTestDirectory(temporary.resolve("transformation-tests"));
        properties.setSynchronizationSessionDirectory(temporary.resolve("transformation-sync-sessions"));
        properties.setSynchronizationHistoryDirectory(temporary.resolve("transformation-sync-history"));

        ExpressionTypeChecker typeChecker = new ExpressionTypeChecker(new ExpressionDependencyAnalyzer());
        TransformationDefinitionChecker checker = new TransformationDefinitionChecker(typeChecker);
        HydraArtifactTransactionService transactions = new HydraArtifactTransactionService(
                temporary.resolve("artifact-transactions").toString(), new HydraFaultInjectionService(new HydraFaultInjectionProperties()));
        TransformationApplicationService transformations = new TransformationApplicationService(
                new JsonTransformationRepository(properties, mapper), metamodels,
                new MetamodelValidationFingerprint(mapper), checker,
                new JsonTransformationTestCaseRepository(properties, mapper, new LocalCanonicalDocumentStoreFactory()),
                new JsonTransformationSynchronizationSessionRepository(properties, mapper, new LocalCanonicalDocumentStoreFactory()),
                transactions);
        TransformationTextGenerationService text = new TransformationTextGenerationService(transformations, metamodels, checker,
                new CanonicalTextGenerationRuntime(new ExpressionEvaluator(typeChecker)));
        return new Fixture(text, transformations, checker, properties);
    }
}
