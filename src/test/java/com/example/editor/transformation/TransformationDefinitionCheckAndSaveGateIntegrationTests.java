package com.example.editor.transformation;

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
import com.example.editor.transformation.core.TransformationConflictPolicy;
import com.example.editor.transformation.core.TransformationDefinitionDocument;
import com.example.editor.transformation.core.TransformationDiagnostic;
import com.example.editor.transformation.core.TransformationDiagnosticSeverity;
import com.example.editor.transformation.core.TransformationDirectionality;
import com.example.editor.transformation.core.TransformationEndpoint;
import com.example.editor.transformation.core.TransformationEndpointRole;
import com.example.editor.transformation.core.TransformationKind;
import com.example.editor.transformation.core.TransformationRule;
import com.example.editor.transformation.core.TransformationRuleDirection;
import com.example.editor.transformation.core.TransformationSaveResponse;
import com.example.editor.transformation.core.TransformationSynchronizationPolicy;
import com.example.editor.transformation.core.TransformationTargetPattern;
import com.example.editor.transformation.core.TransformationTextToModelConfiguration;
import com.example.editor.transformation.core.TransformationTracePolicy;
import com.example.editor.transformation.persistence.JsonTransformationRepository;
import com.example.editor.transformation.sync.JsonTransformationSynchronizationSessionRepository;
import com.example.editor.transformation.testing.persistence.JsonTransformationTestCaseRepository;
import tools.jackson.databind.json.JsonMapper;

/**
 * Evidencia real, sin mocks, de la puerta «comprobar y guardar» (transformation_author_mapping.ck
 * y transformation_define.chk): con el servicio de aplicacion real, el comprobador real y un
 * repositorio JSON en @TempDir, los errores bloquean el guardado (INVALID_REQUEST y nada en
 * disco), los avisos no lo bloquean, y una definicion T2M solo se guarda con gramatica y se
 * relee de disco con su configuracion.
 */
class TransformationDefinitionCheckAndSaveGateIntegrationTests {
    private static final String REQUIRED_NAME_METAMODEL = ExpressionTestFixtures.METAMODEL_ID;

    @Test
    @DisplayName("transformation_author_mapping.ck / transformation_define.chk: errores de regla bloquean y no persisten nada")
    void blockingRuleErrorsAreReportedByCheckAndRejectedBySaveWithoutPersisting(@TempDir Path temporary) {
        TransformationApplicationService service = service(temporary);
        JsonTransformationRepository disk = repository(temporary);

        TransformationRule person = personRule("rule:person", ExpressionTestFixtures.PERSON, "person",
                binding("binding:age", ExpressionTestFixtures.AGE, ExpressionTestFixtures.AGE));
        TransformationRule duplicate = personRule("rule:person", ExpressionTestFixtures.PERSON, "person",
                binding("binding:age", ExpressionTestFixtures.AGE, ExpressionTestFixtures.AGE));
        TransformationRule twoTargetsSameAlias = new TransformationRule("rule:alias", "Alias", true, "source",
                ExpressionTestFixtures.PERSON, "personSource", null,
                List.of(new TransformationTargetPattern("target:one", "target", ExpressionTestFixtures.PERSON, "same", Map.of()),
                        new TransformationTargetPattern("target:two", "target", ExpressionTestFixtures.PERSON, "same", Map.of())),
                List.of(), Map.of());
        TransformationRule ghostSource = personRule("rule:ghost", "class:Ghost", "person",
                binding("binding:age", ExpressionTestFixtures.AGE, ExpressionTestFixtures.AGE));
        TransformationRule badlyTyped = personRule("rule:typed", ExpressionTestFixtures.PERSON, "person",
                binding("binding:age-to-name", ExpressionTestFixtures.NAME, ExpressionTestFixtures.AGE));

        assertRejected(service, disk, m2m("transformation-dup-rule", List.of(person, duplicate)), "DUPLICATE_RULE_ID");
        assertRejected(service, disk, m2m("transformation-dup-alias", List.of(twoTargetsSameAlias)), "TARGET_ALIAS_REQUIRED_OR_DUPLICATED");
        assertRejected(service, disk, m2m("transformation-ghost-source", List.of(ghostSource)), "UNKNOWN_SOURCE_TYPE");
        assertRejected(service, disk, m2m("transformation-bad-typing", List.of(badlyTyped)), "TYPE_CONVERSION_REQUIRED");
        assertThat(disk.listIds()).isEmpty();
    }

    @Test
    @DisplayName("transformation_define.chk: un AVISO (atributo obligatorio sin asignar) no bloquea el guardado")
    void aWarningOnlyDefinitionIsSavedAndCheckReportsWarningWithValidTrue(@TempDir Path temporary) {
        TransformationApplicationService service = service(temporary);
        TransformationDefinitionDocument document = m2m("transformation-warning-only", List.of(personRule("rule:person",
                ExpressionTestFixtures.PERSON, "person", binding("binding:age", ExpressionTestFixtures.AGE, ExpressionTestFixtures.AGE))));

        TransformationCheckReport checked = service.check(document);
        assertThat(checked.valid()).isTrue();
        assertThat(checked.errorCount()).isZero();
        assertThat(checked.diagnostics()).filteredOn(item -> item.code().equals("REQUIRED_TARGET_ATTRIBUTE_UNMAPPED"))
                .singleElement().satisfies(item -> {
                    assertThat(item.severity()).isEqualTo(TransformationDiagnosticSeverity.WARNING);
                    assertThat(item.ruleId()).isEqualTo("rule:person");
                });

        TransformationSaveResponse saved = service.create(document);
        assertThat(saved.checkReport().valid()).isTrue();
        assertThat(saved.checkReport().warningCount()).isGreaterThanOrEqualTo(1);
        assertThat(repository(temporary).find("transformation-warning-only")).isPresent();
    }

    @Test
    @DisplayName("transformation_define.chk: bidireccional sin regla de vuelta -> error y no se guarda")
    void bidirectionalWithoutReverseRuleIsBlocked(@TempDir Path temporary) {
        TransformationApplicationService service = service(temporary);
        TransformationRule forwardOnly = new TransformationRule("rule:forward", "Forward", true, "source",
                ExpressionTestFixtures.PERSON, "personSource", null,
                List.of(new TransformationTargetPattern("target:person", "target", ExpressionTestFixtures.PERSON, "person", Map.of())),
                List.of(binding("binding:age", ExpressionTestFixtures.AGE, ExpressionTestFixtures.AGE)), Map.of(),
                TransformationRuleDirection.FORWARD, "corr:person");
        TransformationDefinitionDocument document = new TransformationDefinitionDocument("1.1", "transformation-no-reverse", "No reverse", null,
                TransformationDirectionality.BIDIRECTIONAL,
                new TransformationSynchronizationPolicy(TransformationConflictPolicy.FAIL_ON_CONFLICT, false, true),
                endpoints(TransformationEndpointRole.SOURCE, "source"), endpoints(TransformationEndpointRole.TARGET, "target"),
                List.of(forwardOnly), List.of(), null, null, TransformationTracePolicy.defaults(), Map.of());

        assertRejected(service, repository(temporary), document, "INCOMPLETE_BIDIRECTIONAL_CORRESPONDENCE");
    }

    @Test
    @DisplayName("transformation_define.chk: T2M sin gramatica bloqueado; con gramatica se guarda y se relee")
    void textToModelNeedsAGrammarAndItsConfigurationIsReloadedFromDisk(@TempDir Path temporary) {
        TransformationApplicationService service = service(temporary);
        JsonTransformationRepository disk = repository(temporary);

        assertRejected(service, disk, t2m("transformation-t2m-no-grammar", new TransformationTextToModelConfiguration("", "", "")),
                "GRAMMAR_REQUIRED");

        TransformationSaveResponse saved = service.create(t2m("transformation-t2m-ok",
                new TransformationTextToModelConfiguration("grammar:people", "out/model.hydra", "transformation-post")));
        assertThat(saved.checkReport().valid()).isTrue();

        TransformationTextToModelConfiguration reread = disk.find("transformation-t2m-ok").orElseThrow().document().textToModel();
        assertThat(reread.grammarId()).isEqualTo("grammar:people");
        assertThat(reread.relativePath()).isEqualTo("out/model.hydra");
        assertThat(reread.postTransformationId()).isEqualTo("transformation-post");
        assertThat(disk.find("transformation-t2m-no-grammar")).isEmpty();
    }

    private static void assertRejected(TransformationApplicationService service, JsonTransformationRepository disk,
            TransformationDefinitionDocument document, String expectedCode) {
        TransformationCheckReport report = service.check(document);
        assertThat(report.valid()).as(expectedCode).isFalse();
        assertThat(report.diagnostics()).extracting(TransformationDiagnostic::code).contains(expectedCode);
        assertThatThrownBy(() -> service.create(document)).as(expectedCode)
                .isInstanceOf(TransformationApplicationException.class)
                .extracting(exception -> ((TransformationApplicationException) exception).getReason())
                .isEqualTo(TransformationApplicationException.Reason.INVALID_REQUEST);
        assertThat(disk.find(document.id())).as("nothing persisted for " + document.id()).isEmpty();
    }

    private static TransformationBinding binding(String id, String targetFeature, String sourceFeature) {
        return new TransformationBinding(id, "person", targetFeature, TransformationBindingKind.DIRECT,
                sourceFeature, null, null, null, Map.of());
    }

    private static TransformationRule personRule(String id, String sourceType, String alias, TransformationBinding binding) {
        return new TransformationRule(id, "Rule " + id, true, "source", sourceType, "personSource", null,
                List.of(new TransformationTargetPattern("target:person", "target", ExpressionTestFixtures.PERSON, alias, Map.of())),
                List.of(binding), Map.of());
    }

    private static List<TransformationEndpoint> endpoints(TransformationEndpointRole role, String id) {
        return List.of(new TransformationEndpoint(id, role, ExpressionTestFixtures.METAMODEL_ID, "", id, true, Map.of()));
    }

    private static TransformationDefinitionDocument m2m(String id, List<TransformationRule> rules) {
        return new TransformationDefinitionDocument("1.0", id, "M2M " + id, TransformationKind.M2M,
                endpoints(TransformationEndpointRole.SOURCE, "source"), endpoints(TransformationEndpointRole.TARGET, "target"),
                rules, List.of(), null, null, null, Map.of());
    }

    private static TransformationDefinitionDocument t2m(String id, TransformationTextToModelConfiguration configuration) {
        return new TransformationDefinitionDocument("1.1", id, "T2M " + id, TransformationKind.T2M,
                List.of(), List.of(), List.of(), List.of(), configuration, null, null, Map.of());
    }

    /** Metamodelo «library» con Person.name OBLIGATORIO (multiplicidad 1) para provocar el aviso de atributo sin asignar. */
    private static MetaModel metamodelWithRequiredName() {
        MetaAttribute age = new MetaAttribute(ExpressionTestFixtures.AGE, "age", MetaTypeReference.primitive(MetaPrimitiveType.INTEGER),
                Multiplicity.optional(), true, true, "", Map.of());
        MetaAttribute name = new MetaAttribute(ExpressionTestFixtures.NAME, "name", MetaTypeReference.primitive(MetaPrimitiveType.STRING),
                Multiplicity.one(), true, true, "", Map.of());
        MetaClass person = new MetaClass(ExpressionTestFixtures.PERSON, "Person", MetaClassKind.CLASS, List.of(age, name), List.of(), Map.of());
        return new MetaModel(MetaModel.CURRENT_SCHEMA_VERSION, REQUIRED_NAME_METAMODEL, "Library", List.of(person), List.of(), List.of(), List.of(), Map.of());
    }

    private static TransformationProperties properties(Path temporary) {
        TransformationProperties properties = new TransformationProperties();
        properties.setDirectory(temporary.resolve("transformations"));
        properties.setTestDirectory(temporary.resolve("transformation-tests"));
        properties.setSynchronizationSessionDirectory(temporary.resolve("transformation-sync-sessions"));
        properties.setSynchronizationHistoryDirectory(temporary.resolve("transformation-sync-history"));
        return properties;
    }

    private static JsonTransformationRepository repository(Path temporary) {
        return new JsonTransformationRepository(properties(temporary), JsonMapper.builder().findAndAddModules().build());
    }

    private static TransformationApplicationService service(Path temporary) {
        JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();
        StorageProperties storage = new StorageProperties(temporary.resolve("metamodels"), temporary.resolve("models"),
                temporary.resolve("notations"), temporary.resolve("validations"), temporary.resolve("quickfixes"),
                temporary.resolve("generators"), temporary.resolve("assets"), temporary.resolve("exports"), true);
        CanonicalMetamodelApplicationService metamodelService = new CanonicalMetamodelApplicationService(
                new JsonCanonicalMetamodelRepository(storage, new CanonicalMetamodelProperties(), mapper), new NeutralMetamodelValidator());
        metamodelService.create(metamodelWithRequiredName());

        TransformationProperties properties = properties(temporary);
        TransformationDefinitionChecker checker = new TransformationDefinitionChecker(
                new ExpressionTypeChecker(new ExpressionDependencyAnalyzer()));
        HydraArtifactTransactionService transactions = new HydraArtifactTransactionService(
                temporary.resolve("artifact-transactions").toString(), new HydraFaultInjectionService(new HydraFaultInjectionProperties()));
        return new TransformationApplicationService(
                new JsonTransformationRepository(properties, mapper), metamodelService,
                new MetamodelValidationFingerprint(mapper), checker,
                new JsonTransformationTestCaseRepository(properties, mapper, new LocalCanonicalDocumentStoreFactory()),
                new JsonTransformationSynchronizationSessionRepository(properties, mapper, new LocalCanonicalDocumentStoreFactory()),
                transactions);
    }
}
