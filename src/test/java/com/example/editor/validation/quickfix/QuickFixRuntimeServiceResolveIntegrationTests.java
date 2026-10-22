package com.example.editor.validation.quickfix;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.editor.config.StorageProperties;
import com.example.editor.emf.DynamicModelService;
import com.example.editor.emf.EmfResourceSetFactory;
import com.example.editor.emf.MetamodelLoader;
import com.example.editor.emf.XmiModelRepository;
import com.example.editor.event.bus.InMemoryCanonicalEventBus;
import com.example.editor.metamodel.application.CanonicalMetamodelApplicationService;
import com.example.editor.metamodel.behavior.OperationBehaviorModelApplicationService;
import com.example.editor.metamodel.behavior.OperationBehaviorModelProperties;
import com.example.editor.metamodel.behavior.persistence.JsonOperationBehaviorModelRepository;
import com.example.editor.metamodel.canonical.CanonicalMetamodelProperties;
import com.example.editor.metamodel.canonical.JsonCanonicalMetamodelRepository;
import com.example.editor.metamodel.registry.MetamodelRegistryService;
import com.example.editor.metamodel.registry.RegisteredMetamodel;
import com.example.editor.metamodel.registry.RuntimeMetamodelTestSupport;
import com.example.editor.metamodel.validation.NeutralMetamodelValidator;
import com.example.editor.metamodel.validation.OperationBehaviorModelValidator;
import com.example.editor.metamodel.behavior.OperationQueryRuntime;
import com.example.editor.model.ModelDocumentResponse;
import com.example.editor.model.ModelDocumentService;
import com.example.editor.semantic.expression.ExpressionDependencyAnalyzer;
import com.example.editor.semantic.expression.ExpressionEvaluator;
import com.example.editor.semantic.expression.ExpressionTypeChecker;
import com.example.editor.validation.action.ActionDefinition;
import com.example.editor.validation.action.ActionModel;
import com.example.editor.validation.action.ActionModelProperties;
import com.example.editor.validation.action.ActionSelector;
import com.example.editor.validation.action.ActionSelectorKind;
import com.example.editor.validation.action.ActionTransactionPolicy;
import com.example.editor.validation.action.ActionUndoPolicy;
import com.example.editor.validation.action.ActionValueSource;
import com.example.editor.validation.action.CanonicalActionExecutor;
import com.example.editor.validation.action.SetAttributeOperation;
import com.example.editor.validation.action.persistence.JsonActionModelRepository;
import com.example.editor.validation.expression.ExpressionModelProperties;
import com.example.editor.validation.expression.persistence.JsonExpressionModelRepository;
import com.example.editor.validation.orchestration.ValidationOrchestrator;
import com.example.editor.validation.runtime.ModelValidationDiagnostic;
import com.example.editor.validation.runtime.ModelValidationReport;
import com.example.editor.validation.runtime.ValidationDiagnosticLocation;
import com.example.editor.validation.runtime.ValidationDiagnosticStatus;
import com.example.editor.validation.runtime.ValidationExecutionMetrics;
import com.example.editor.validation.runtime.ValidationReportScope;
import com.example.editor.validation.runtime.ValidationRunMode;
import com.example.editor.validation.validation.ValidationSeverity;
import tools.jackson.databind.json.JsonMapper;

/**
 * Evidencia real, de intensidad MEDIA y documentada como tal, de QuickFixRuntimeService.resolve()
 * -- el metodo de entrada mas simple del servicio, deliberadamente abordado primero en este alcance
 * reducido. Se simula unicamente ValidationOrchestrator (clase concreta, no interfaz, que exige una
 * cadena real de 10 colaboradores para construirse) y se reutiliza para todo lo demas la cadena EMF
 * real ya probada en ModelDocumentTests (ecore real, ModelDocumentService real) mas un
 * ActionModelRepository real y sin mocks. Cubre validation_apply_quickfix.par/ok (resolver una
 * correccion real listada por un diagnostico real) y validation_author_quickfix.id (identidad de la
 * correccion real, releida desde su repositorio).
 */
class QuickFixRuntimeServiceResolveIntegrationTests {

    private static final String NS_URI = "https://example.com/introspection";
    private static final String MODEL_CLASS_ID = NS_URI + "#//Model";

    @TempDir
    Path temporaryDirectory;

    private StorageProperties storage;
    private ModelDocumentService modelService;
    private JsonMapper mapper;

    @BeforeEach
    void setUp() throws Exception {
        storage = new StorageProperties(temporaryDirectory.resolve("metamodels"), temporaryDirectory.resolve("models"),
                temporaryDirectory.resolve("notations"), temporaryDirectory.resolve("validations"), temporaryDirectory.resolve("quickfixes"),
                temporaryDirectory.resolve("generators"), temporaryDirectory.resolve("assets"), temporaryDirectory.resolve("exports"), true);
        Files.createDirectories(storage.metamodels());

        EmfResourceSetFactory resourceSetFactory = new EmfResourceSetFactory();
        MetamodelLoader metamodelLoader = new MetamodelLoader(resourceSetFactory);
        URL resourceUrl = Objects.requireNonNull(getClass().getResource("/metamodels/introspection.ecore"), "Test resource was not found");
        Path ecorePath = storage.metamodels().resolve("introspection.ecore");
        Files.copy(Path.of(resourceUrl.toURI()), ecorePath);
        RegisteredMetamodel registered = RuntimeMetamodelTestSupport.load("introspection", "introspection.ecore", ecorePath, metamodelLoader);
        MetamodelRegistryService registryService = RuntimeMetamodelTestSupport.fixedRegistry(storage, metamodelLoader, List.of(registered));

        modelService = new ModelDocumentService(storage, registryService, metamodelLoader, new DynamicModelService(), new XmiModelRepository());
        mapper = JsonMapper.builder().findAndAddModules().build();
    }

    @Test
    void resolvesARealQuickFixOfferedByARealDiagnosticFromAMockedOrchestrator() {
        ModelDocumentResponse live = modelService.create("introspection", MODEL_CLASS_ID);

        SetAttributeOperation renameOperation = new SetAttributeOperation("op:rename", new ActionSelector(ActionSelectorKind.SELF, "", ""),
                "name", new ActionValueSource("fixed", "", ""));
        ActionDefinition renameFix = new ActionDefinition("fix:rename", "Rename element", "Renames the offending element", true,
                List.of(), "", List.of(renameOperation), ActionTransactionPolicy.ATOMIC, ActionUndoPolicy.REQUIRED, Map.of());
        ActionModel actions = new ActionModel(null, "introspection-actions", "introspection", "introspection-expressions", List.of(renameFix), Map.of());
        JsonActionModelRepository actionRepository = new JsonActionModelRepository(storage, new ActionModelProperties(), mapper);
        actionRepository.create(actions);

        ModelValidationDiagnostic diagnostic = new ModelValidationDiagnostic("diag:1", ValidationDiagnosticStatus.VIOLATION, "rule:1",
                ValidationSeverity.ERROR, "Element name is invalid", new ValidationDiagnosticLocation(live.rootElementId(), "", ""),
                List.of("fix:rename"), Map.of());
        ModelValidationReport report = new ModelValidationReport(null, "report:1", live.id(), "introspection", "", Instant.now(),
                ValidationReportScope.model(), List.of(diagnostic), new ValidationExecutionMetrics(1, 0, 0), "", ValidationRunMode.MANUAL,
                Instant.now(), Instant.now(), List.of(), List.of());

        ValidationOrchestrator orchestrator = mock(ValidationOrchestrator.class);
        when(orchestrator.latest(live.id())).thenReturn(Optional.of(report));
        when(orchestrator.findDiagnostic(live.id(), "diag:1")).thenReturn(Optional.of(diagnostic));

        QuickFixRuntimeService service = service(actionRepository, orchestrator);

        List<QuickFixDescriptor> resolved = service.resolve(live.id(), "diag:1");

        assertThat(resolved).singleElement().satisfies(descriptor -> {
            assertThat(descriptor.actionId()).isEqualTo("fix:rename");
            assertThat(descriptor.name()).isEqualTo("Rename element");
            assertThat(descriptor.enabled()).isTrue();
            assertThat(descriptor.transactionPolicy()).isEqualTo(ActionTransactionPolicy.ATOMIC);
            assertThat(descriptor.undoPolicy()).isEqualTo(ActionUndoPolicy.REQUIRED);
        });
    }

    @Test
    void refusesToResolveWithABlankDiagnosticIdWithoutEvenConsultingTheOrchestrator() {
        ModelDocumentResponse live = modelService.create("introspection", MODEL_CLASS_ID);
        JsonActionModelRepository actionRepository = new JsonActionModelRepository(storage, new ActionModelProperties(), mapper);
        ValidationOrchestrator orchestrator = mock(ValidationOrchestrator.class);
        QuickFixRuntimeService service = service(actionRepository, orchestrator);

        assertThatThrownBy(() -> service.resolve(live.id(), " "))
                .isInstanceOf(QuickFixRuntimeException.class)
                .extracting(exception -> ((QuickFixRuntimeException) exception).getReason())
                .isEqualTo(QuickFixRuntimeException.Reason.INVALID_REQUEST);
    }

    private QuickFixRuntimeService service(JsonActionModelRepository actionRepository, ValidationOrchestrator orchestrator) {
        CanonicalMetamodelApplicationService metamodelService = new CanonicalMetamodelApplicationService(
                new JsonCanonicalMetamodelRepository(storage, new CanonicalMetamodelProperties(), mapper), new NeutralMetamodelValidator());
        JsonExpressionModelRepository expressionRepository = new JsonExpressionModelRepository(storage, new ExpressionModelProperties(), mapper);
        JsonOperationBehaviorModelRepository behaviorRepository = new JsonOperationBehaviorModelRepository(storage,
                new OperationBehaviorModelProperties(), mapper);
        OperationBehaviorModelApplicationService behaviorService = new OperationBehaviorModelApplicationService(
                behaviorRepository, metamodelService, expressionRepository, actionRepository, new OperationBehaviorModelValidator());
        ExpressionEvaluator evaluator = new ExpressionEvaluator(new ExpressionTypeChecker(new ExpressionDependencyAnalyzer()));
        CanonicalActionExecutor actionExecutor = new CanonicalActionExecutor(evaluator, new OperationQueryRuntime(evaluator));

        return new QuickFixRuntimeService(metamodelService, behaviorService, expressionRepository, actionRepository, actionExecutor,
                modelService, orchestrator, new CanonicalModelChangeAnalyzer(), new InMemoryCanonicalEventBus());
    }
}
