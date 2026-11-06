package com.example.editor.transformation.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

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
import com.example.editor.semantic.expression.ExpressionEvaluator;
import com.example.editor.semantic.expression.ExpressionTestFixtures;
import com.example.editor.semantic.expression.ExpressionTypeChecker;
import com.example.editor.testing.fault.HydraFaultInjectionProperties;
import com.example.editor.testing.fault.HydraFaultInjectionService;
import com.example.editor.transformation.TransformationApplicationService;
import com.example.editor.transformation.TransformationDefinitionChecker;
import com.example.editor.transformation.TransformationProperties;
import com.example.editor.transformation.core.TransformationBinding;
import com.example.editor.transformation.core.TransformationBindingKind;
import com.example.editor.transformation.core.TransformationConflictPolicy;
import com.example.editor.transformation.core.TransformationDefinitionDocument;
import com.example.editor.transformation.core.TransformationDirectionality;
import com.example.editor.transformation.core.TransformationEndpoint;
import com.example.editor.transformation.core.TransformationEndpointRole;
import com.example.editor.transformation.core.TransformationExecutionDirection;
import com.example.editor.transformation.core.TransformationKind;
import com.example.editor.transformation.core.TransformationRule;
import com.example.editor.transformation.core.TransformationRuleDirection;
import com.example.editor.transformation.core.TransformationSynchronizationPolicy;
import com.example.editor.transformation.core.TransformationTargetPattern;
import com.example.editor.transformation.core.TransformationTracePolicy;
import com.example.editor.transformation.persistence.JsonTransformationRepository;
import com.example.editor.transformation.sync.JsonTransformationSynchronizationSessionRepository;
import com.example.editor.transformation.testing.persistence.JsonTransformationTestCaseRepository;
import com.example.editor.validation.model.CanonicalModelDocument;
import com.example.editor.validation.model.CanonicalModelElement;
import tools.jackson.databind.json.JsonMapper;

/**
 * Evidencia real, sin mocks, de transformation_execute.in (modelo de entrada y direccion) y
 * transformation_execute.ok (plan valido) con DOS metamodelos guardados distintos: «library»
 * (Person.name) y «staff» (Worker.fullName). Los oraculos son identificadores de metamodelo, clases
 * y valores escritos a mano, y la ausencia de ficheros en el directorio de transformaciones.
 */
class TransformationRuntimeServiceExecuteDraftDistinctMetamodelsReverseIntegrationTests {
    private static final String STAFF_ID = "staff";
    private static final String WORKER = "class:Worker";
    private static final String FULL_NAME = "attribute:Worker:fullName";
    private static final String NAME = ExpressionTestFixtures.NAME;

    @Test
    @DisplayName("transformation_execute.in: FORWARD lee un modelo library y escribe elementos Worker del metamodelo staff")
    void forwardReadsTheLibrarySideAndWritesTheStaffSide(@TempDir Path temporary) throws IOException {
        TransformationRuntimeService service = service(temporary);

        TransformationExecutionResult result = service.executeDraft(bidirectional("transformation-distinct-ok", "class:Worker"),
                libraryModel(), TransformationExecutionDirection.FORWARD);

        CanonicalModelDocument target = result.targetModel();
        assertThat(target.metamodelId()).isEqualTo(STAFF_ID);
        assertThat(target.annotations()).containsEntry("hydra.transformation.direction", "FORWARD")
                .containsEntry("hydra.transformation.sourceModel", "library-people");
        assertThat(target.elements()).hasSize(2);
        assertThat(target.elements()).allSatisfy(element -> {
            assertThat(element.classId()).isEqualTo(WORKER);
            assertThat(element.attributes()).containsOnlyKeys(FULL_NAME);
        });
        assertThat(target.elements()).extracting(element -> element.attributes().get(FULL_NAME)).containsExactlyInAnyOrder("Ana", "Luis");
        assertThat(target.elements()).extracting(element -> element.annotations().get("hydra.transformation.source"))
                .containsExactlyInAnyOrder("object:ana", "object:luis");
        assertThat(filesUnder(temporary.resolve("transformations"))).isEmpty();
    }

    @Test
    @DisplayName("transformation_execute.in: REVERSE lee un modelo staff y escribe elementos Person del metamodelo library")
    void reverseReadsTheStaffSideAndWritesTheLibrarySide(@TempDir Path temporary) throws IOException {
        TransformationRuntimeService service = service(temporary);

        TransformationExecutionResult result = service.executeDraft(bidirectional("transformation-distinct-reverse", "class:Worker"),
                staffModel(), TransformationExecutionDirection.REVERSE);

        CanonicalModelDocument target = result.targetModel();
        assertThat(target.metamodelId()).isEqualTo(ExpressionTestFixtures.METAMODEL_ID);
        assertThat(target.annotations()).containsEntry("hydra.transformation.direction", "REVERSE")
                .containsEntry("hydra.transformation.sourceModel", "staff-roster");
        assertThat(target.elements()).singleElement().satisfies(element -> {
            assertThat(element.classId()).isEqualTo(ExpressionTestFixtures.PERSON);
            assertThat(element.attributes()).containsOnlyKeys(NAME).containsEntry(NAME, "Marta");
            assertThat(element.annotations()).containsEntry("hydra.transformation.source", "object:marta");
        });
        assertThat(filesUnder(temporary.resolve("transformations"))).isEmpty();
    }

    @Test
    @DisplayName("transformation_execute.in: un modelo del lado equivocado o una vuelta pedida a una unidireccional se rechazan")
    void aModelFromTheWrongSideOrAReverseRunOfAOneWayDefinitionIsRejected(@TempDir Path temporary) {
        TransformationRuntimeService service = service(temporary);
        TransformationDefinitionDocument both = bidirectional("transformation-distinct-wrong-side", "class:Worker");

        assertThatThrownBy(() -> service.executeDraft(both, staffModel(), TransformationExecutionDirection.FORWARD))
                .isInstanceOf(TransformationExecutionException.class)
                .hasMessage("Source model does not conform to the transformation source metamodel");
        assertThatThrownBy(() -> service.executeDraft(both, libraryModel(), TransformationExecutionDirection.REVERSE))
                .isInstanceOf(TransformationExecutionException.class)
                .hasMessage("Source model does not conform to the transformation source metamodel");

        TransformationDefinitionDocument oneWay = definition("transformation-distinct-one-way", TransformationDirectionality.UNIDIRECTIONAL,
                List.of(forwardRule("class:Worker")));
        assertThatThrownBy(() -> service.executeDraft(oneWay, staffModel(), TransformationExecutionDirection.REVERSE))
                .isInstanceOf(TransformationExecutionException.class)
                .hasMessage("Reverse execution requires a bidirectional transformation");
    }

    @Test
    @DisplayName("transformation_execute.ok: un error solo en la regla de vuelta detiene tambien la ida antes de ejecutar nada")
    void anErrorInTheReturnRuleStopsEvenTheForwardRunBeforeExecuting(@TempDir Path temporary) throws IOException {
        TransformationRuntimeService service = service(temporary);
        // La regla de vuelta escribe en una clase que no existe en library.
        TransformationDefinitionDocument broken = definition("transformation-distinct-broken-return", TransformationDirectionality.BIDIRECTIONAL,
                List.of(forwardRule("class:Worker"), reverseRule("class:Ghost")));

        assertThatThrownBy(() -> service.executeDraft(broken, libraryModel(), TransformationExecutionDirection.FORWARD))
                .isInstanceOf(TransformationExecutionException.class)
                .hasMessageContaining("cannot be compiled because it contains blocking diagnostics")
                .hasMessageContaining("UNKNOWN_TARGET_TYPE");
        assertThatThrownBy(() -> service.executeDraft(broken, staffModel(), TransformationExecutionDirection.REVERSE))
                .isInstanceOf(TransformationExecutionException.class)
                .hasMessageContaining("UNKNOWN_TARGET_TYPE");
        assertThat(filesUnder(temporary.resolve("transformations"))).isEmpty();
    }

    // ----------------------------------------------------------------------------------- fixtures

    private static List<Path> filesUnder(Path directory) throws IOException {
        if(!Files.exists(directory)) return List.of();
        try(Stream<Path> walk = Files.walk(directory)) {
            return walk.filter(Files::isRegularFile).toList();
        }
    }

    private static MetaModel staffMetamodel() {
        MetaAttribute fullName = new MetaAttribute(FULL_NAME, "fullName", MetaTypeReference.primitive(MetaPrimitiveType.STRING),
                Multiplicity.optional(), true, true, "", Map.of());
        MetaClass worker = new MetaClass(WORKER, "Worker", MetaClassKind.CLASS, List.of(fullName), List.of(), Map.of());
        return new MetaModel(MetaModel.CURRENT_SCHEMA_VERSION, STAFF_ID, "Staff", List.of(worker), List.of(), List.of(), List.of(), Map.of());
    }

    private static CanonicalModelDocument libraryModel() {
        CanonicalModelElement ana = new CanonicalModelElement("object:ana", ExpressionTestFixtures.PERSON, Map.of(NAME, "Ana"), Map.of());
        CanonicalModelElement luis = new CanonicalModelElement("object:luis", ExpressionTestFixtures.PERSON, Map.of(NAME, "Luis"), Map.of());
        return new CanonicalModelDocument("1.0", "library-people", ExpressionTestFixtures.METAMODEL_ID, "etag-1", "Library people",
                List.of("object:ana", "object:luis"), List.of(ana, luis), List.of(), Map.of());
    }

    private static CanonicalModelDocument staffModel() {
        CanonicalModelElement marta = new CanonicalModelElement("object:marta", WORKER, Map.of(FULL_NAME, "Marta"), Map.of());
        return new CanonicalModelDocument("1.0", "staff-roster", STAFF_ID, "etag-1", "Staff roster",
                List.of("object:marta"), List.of(marta), List.of(), Map.of());
    }

    private static TransformationRule forwardRule(String targetClass) {
        return new TransformationRule("rule:person-to-worker", "Person to Worker", true, "source",
                ExpressionTestFixtures.PERSON, "personSource", null,
                List.of(new TransformationTargetPattern("target:worker", "target", targetClass, "worker", Map.of())),
                List.of(new TransformationBinding("binding:name-to-full-name", "worker", FULL_NAME, TransformationBindingKind.DIRECT,
                        NAME, null, null, null, Map.of())),
                Map.of(), TransformationRuleDirection.FORWARD, "corr:person-worker");
    }

    private static TransformationRule reverseRule(String targetClass) {
        return new TransformationRule("rule:worker-to-person", "Worker to Person", true, "target",
                WORKER, "workerTarget", null,
                List.of(new TransformationTargetPattern("source:person", "source", targetClass, "person", Map.of())),
                List.of(new TransformationBinding("binding:full-name-to-name", "person", NAME, TransformationBindingKind.DIRECT,
                        FULL_NAME, null, null, null, Map.of())),
                Map.of(), TransformationRuleDirection.REVERSE, "corr:person-worker");
    }

    private static TransformationDefinitionDocument bidirectional(String id, String workerClass) {
        return definition(id, TransformationDirectionality.BIDIRECTIONAL,
                List.of(forwardRule(workerClass), reverseRule(ExpressionTestFixtures.PERSON)));
    }

    private static TransformationDefinitionDocument definition(String id, TransformationDirectionality directionality, List<TransformationRule> rules) {
        return new TransformationDefinitionDocument("1.1", id, "People and staff " + id, TransformationKind.M2M, directionality,
                new TransformationSynchronizationPolicy(TransformationConflictPolicy.FAIL_ON_CONFLICT, false, true),
                List.of(new TransformationEndpoint("source", TransformationEndpointRole.SOURCE, ExpressionTestFixtures.METAMODEL_ID, "", "source", true, Map.of())),
                List.of(new TransformationEndpoint("target", TransformationEndpointRole.TARGET, STAFF_ID, "", "target", true, Map.of())),
                rules, List.of(), null, null, TransformationTracePolicy.defaults(), Map.of());
    }

    private static TransformationRuntimeService service(Path temporary) {
        JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();
        StorageProperties storage = new StorageProperties(temporary.resolve("metamodels"), temporary.resolve("models"),
                temporary.resolve("notations"), temporary.resolve("validations"), temporary.resolve("quickfixes"),
                temporary.resolve("generators"), temporary.resolve("assets"), temporary.resolve("exports"), true);
        CanonicalMetamodelApplicationService metamodels = new CanonicalMetamodelApplicationService(
                new JsonCanonicalMetamodelRepository(storage, new CanonicalMetamodelProperties(), mapper), new NeutralMetamodelValidator());
        metamodels.create(ExpressionTestFixtures.metamodel());
        metamodels.create(staffMetamodel());

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

        return new TransformationRuntimeService(transformations, metamodels, new TransformationPlanCompiler(checker),
                new CanonicalTransformationRuntime(new ExpressionEvaluator(typeChecker)));
    }
}
