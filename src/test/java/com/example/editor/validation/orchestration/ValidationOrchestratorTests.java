package com.example.editor.validation.orchestration;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.example.editor.event.bus.InMemoryCanonicalEventBus;
import com.example.editor.metamodel.application.CanonicalMetamodelApplicationService;
import com.example.editor.model.ModelDocumentService;
import com.example.editor.validation.model.CanonicalModelDocument;
import com.example.editor.validation.model.CanonicalModelElement;
import com.example.editor.validation.runtime.ModelValidationDiagnostic;
import com.example.editor.validation.runtime.ModelValidationReport;
import com.example.editor.validation.runtime.ValidationChangeSet;
import com.example.editor.validation.runtime.ValidationDiagnosticLocation;
import com.example.editor.validation.runtime.ValidationDiagnosticStatus;
import com.example.editor.validation.runtime.ValidationExecutionMetrics;
import com.example.editor.validation.runtime.ValidationExecutionRequest;
import com.example.editor.validation.runtime.ValidationReportScope;
import com.example.editor.validation.runtime.ValidationRunMode;
import com.example.editor.validation.runtime.ValidationRuntimeApplicationService;
import com.example.editor.validation.validation.ValidationSeverity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ValidationOrchestratorTests {
    private ValidationRuntimeApplicationService runtime;
    private ValidationOrchestrationProperties properties;
    private ValidationOrchestrator orchestrator;

    @BeforeEach void setUp() {
        runtime = mock(ValidationRuntimeApplicationService.class);
        properties = new ValidationOrchestrationProperties();
        orchestrator = new ValidationOrchestrator(runtime, mock(CanonicalMetamodelApplicationService.class), mock(ModelDocumentService.class),
                new DefaultValidationBlockingPolicy(properties), properties, new InMemoryCanonicalEventBus());
    }

    @Test void firstExecutionReportsEveryDiagnosticAsCreated() {
        when(runtime.validate(eq("meta"), any())).thenReturn(report("report:one", ValidationRunMode.FULL, List.of(diagnostic("d:a", "r:a", "a", "class:A")), List.of("r:a")));
        OrchestratedValidationResult result = orchestrator.validate("meta", request(ValidationRunMode.FULL, ValidationChangeSet.empty(), List.of(), ""), ValidationGate.NONE);
        assertThat(result.delta().created()).extracting(ModelValidationDiagnostic::id).containsExactly("d:a");
        assertThat(result.delta().unchanged()).isEmpty();
    }

    @Test void fullExecutionReplacesPreviousProblems() {
        when(runtime.validate(eq("meta"), any())).thenReturn(
                report("report:one", ValidationRunMode.FULL, List.of(diagnostic("d:a", "r:a", "a", "class:A")), List.of("r:a")),
                report("report:two", ValidationRunMode.FULL, List.of(), List.of("r:a")));
        orchestrator.validate("meta", request(ValidationRunMode.FULL, ValidationChangeSet.empty(), List.of(), ""), ValidationGate.NONE);
        OrchestratedValidationResult result = orchestrator.validate("meta", request(ValidationRunMode.FULL, ValidationChangeSet.empty(), List.of(), ""), ValidationGate.NONE);
        assertThat(result.report().diagnostics()).isEmpty();
        assertThat(result.delta().resolved()).extracting(ModelValidationDiagnostic::id).containsExactly("d:a");
    }

    @Test void incrementalExecutionKeepsUnaffectedProblems() {
        when(runtime.validate(eq("meta"), any())).thenReturn(
                report("report:one", ValidationRunMode.FULL, List.of(diagnostic("d:a", "r:a", "a", "class:A"), diagnostic("d:b", "r:b", "b", "class:B")), List.of("r:a", "r:b")),
                report("report:two", ValidationRunMode.INCREMENTAL, List.of(), List.of("r:a")));
        orchestrator.validate("meta", request(ValidationRunMode.FULL, ValidationChangeSet.empty(), List.of(), ""), ValidationGate.NONE);
        OrchestratedValidationResult result = orchestrator.validate("meta", request(ValidationRunMode.INCREMENTAL,
                new ValidationChangeSet(List.of("a"), List.of(), List.of("attribute:name"), List.of(), List.of(), true), List.of(), ""), ValidationGate.NONE);
        assertThat(result.report().diagnostics()).extracting(ModelValidationDiagnostic::id).containsExactly("d:b");
        assertThat(result.delta().resolved()).extracting(ModelValidationDiagnostic::id).containsExactly("d:a");
    }

    @Test void incrementalExecutionReplacesProblemOnAffectedElement() {
        when(runtime.validate(eq("meta"), any())).thenReturn(
                report("report:one", ValidationRunMode.FULL, List.of(diagnostic("d:a:old", "r:a", "a", "class:A")), List.of("r:a")),
                report("report:two", ValidationRunMode.INCREMENTAL, List.of(diagnostic("d:a:new", "r:a", "a", "class:A")), List.of("r:a")));
        orchestrator.validate("meta", request(ValidationRunMode.FULL, ValidationChangeSet.empty(), List.of(), ""), ValidationGate.NONE);
        OrchestratedValidationResult result = orchestrator.validate("meta", request(ValidationRunMode.INCREMENTAL,
                new ValidationChangeSet(List.of("a"), List.of(), List.of(), List.of(), List.of(), true), List.of(), ""), ValidationGate.NONE);
        assertThat(result.report().diagnostics()).extracting(ModelValidationDiagnostic::id).containsExactly("d:a:new");
        assertThat(result.delta().created()).extracting(ModelValidationDiagnostic::id).containsExactly("d:a:new");
        assertThat(result.delta().resolved()).extracting(ModelValidationDiagnostic::id).containsExactly("d:a:old");
    }

    @Test void selectionExecutionKeepsProblemsOutsideSelection() {
        when(runtime.validate(eq("meta"), any())).thenReturn(
                report("report:one", ValidationRunMode.FULL, List.of(diagnostic("d:a", "r:a", "a", "class:A"), diagnostic("d:b", "r:a", "b", "class:A")), List.of("r:a")),
                report("report:two", ValidationRunMode.SELECTION, List.of(), List.of("r:a")));
        orchestrator.validate("meta", request(ValidationRunMode.FULL, ValidationChangeSet.empty(), List.of(), ""), ValidationGate.NONE);
        OrchestratedValidationResult result = orchestrator.validate("meta", request(ValidationRunMode.SELECTION, ValidationChangeSet.empty(), List.of("a"), ""), ValidationGate.NONE);
        assertThat(result.report().diagnostics()).extracting(ModelValidationDiagnostic::id).containsExactly("d:b");
    }

    @Test void classExecutionKeepsProblemsFromOtherClasses() {
        when(runtime.validate(eq("meta"), any())).thenReturn(
                report("report:one", ValidationRunMode.FULL, List.of(diagnostic("d:a", "r:a", "a", "class:A"), diagnostic("d:b", "r:a", "b", "class:B")), List.of("r:a")),
                report("report:two", ValidationRunMode.CLASS, List.of(), List.of("r:a")));
        orchestrator.validate("meta", request(ValidationRunMode.FULL, ValidationChangeSet.empty(), List.of(), ""), ValidationGate.NONE);
        OrchestratedValidationResult result = orchestrator.validate("meta", request(ValidationRunMode.CLASS, ValidationChangeSet.empty(), List.of(), "class:A"), ValidationGate.NONE);
        assertThat(result.report().diagnostics()).extracting(ModelValidationDiagnostic::id).containsExactly("d:b");
    }

    @Test void latestAndClearManageProblemsState() {
        when(runtime.validate(eq("meta"), any())).thenReturn(report("report:one", ValidationRunMode.FULL, List.of(), List.of()));
        orchestrator.validate("meta", request(ValidationRunMode.FULL, ValidationChangeSet.empty(), List.of(), ""), ValidationGate.NONE);
        assertThat(orchestrator.latest("model")).isPresent();
        orchestrator.clear("model");
        assertThat(orchestrator.latest("model")).isEmpty();
    }

    @Test void reportCacheEvictsOldestModel() {
        properties.setMaxTrackedModels(1);
        when(runtime.validate(eq("meta"), any())).thenReturn(reportForModel("report:one", "model-one"), reportForModel("report:two", "model-two"));
        orchestrator.validate("meta", requestForModel("model-one"), ValidationGate.NONE);
        orchestrator.validate("meta", requestForModel("model-two"), ValidationGate.NONE);
        assertThat(orchestrator.latest("model-one")).isEmpty();
        assertThat(orchestrator.latest("model-two")).isPresent();
    }

    @Test void blockingDecisionDoesNotAlterDiagnostics() {
        when(runtime.validate(eq("meta"), any())).thenReturn(report("report:one", ValidationRunMode.FULL,
                List.of(diagnostic("d:a", "r:a", "a", "class:A")), List.of("r:a")));
        OrchestratedValidationResult result = orchestrator.validate("meta", request(ValidationRunMode.FULL, ValidationChangeSet.empty(), List.of(), ""), ValidationGate.SAVE);
        assertThat(result.blocking().blocked()).isTrue();
        assertThat(result.report().diagnostics()).extracting(ModelValidationDiagnostic::severity).containsExactly(ValidationSeverity.ERROR);
    }

    @Test void repeatedStableDiagnosticIsReportedAsUnchanged() {
        ModelValidationDiagnostic diagnostic = diagnostic("d:a", "r:a", "a", "class:A");
        when(runtime.validate(eq("meta"), any())).thenReturn(report("report:one", ValidationRunMode.FULL, List.of(diagnostic), List.of("r:a")),
                report("report:two", ValidationRunMode.FULL, List.of(diagnostic), List.of("r:a")));
        orchestrator.validate("meta", request(ValidationRunMode.FULL, ValidationChangeSet.empty(), List.of(), ""), ValidationGate.NONE);
        OrchestratedValidationResult result = orchestrator.validate("meta", request(ValidationRunMode.FULL, ValidationChangeSet.empty(), List.of(), ""), ValidationGate.NONE);
        assertThat(result.delta().unchanged()).extracting(ModelValidationDiagnostic::id).containsExactly("d:a");
    }

    private ValidationExecutionRequest request(ValidationRunMode mode, ValidationChangeSet changes, List<String> selection, String classId) {
        return new ValidationExecutionRequest(mode, model("model"), selection, classId, changes, Map.of());
    }
    private ValidationExecutionRequest requestForModel(String modelId) { return new ValidationExecutionRequest(ValidationRunMode.FULL, model(modelId), List.of(), "", ValidationChangeSet.empty(), Map.of()); }
    private CanonicalModelDocument model(String modelId) {
        return new CanonicalModelDocument("1.0", modelId, "meta", "", "Model", List.of("a"),
                List.of(new CanonicalModelElement("a", "class:A", Map.of(), Map.of()), new CanonicalModelElement("b", "class:B", Map.of(), Map.of())), List.of(), Map.of());
    }
    private ModelValidationDiagnostic diagnostic(String id, String ruleId, String elementId, String classId) {
        return new ModelValidationDiagnostic(id, ValidationDiagnosticStatus.VIOLATION, ruleId, ValidationSeverity.ERROR, "Problem",
                new ValidationDiagnosticLocation(elementId, "", ""), List.of(), Map.of("targetClassId", classId));
    }
    private ModelValidationReport report(String id, ValidationRunMode mode, List<ModelValidationDiagnostic> diagnostics, List<String> executedRules) {
        return new ModelValidationReport("1.0", id, "model", "meta", "validations", Instant.EPOCH, ValidationReportScope.model(), diagnostics,
                new ValidationExecutionMetrics(executedRules.size(), 0, 0), "plan", mode, Instant.EPOCH, Instant.EPOCH, executedRules, List.of());
    }
    private ModelValidationReport reportForModel(String id, String modelId) {
        return new ModelValidationReport("1.0", id, modelId, "meta", "validations", Instant.EPOCH, ValidationReportScope.model(), List.of(),
                new ValidationExecutionMetrics(0, 0, 0), "plan", ValidationRunMode.FULL, Instant.EPOCH, Instant.EPOCH, List.of(), List.of());
    }
}
