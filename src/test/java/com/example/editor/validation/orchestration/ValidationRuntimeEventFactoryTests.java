package com.example.editor.validation.orchestration;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.example.editor.event.EventContextKind;
import com.example.editor.event.EventSubjectKind;
import com.example.editor.validation.runtime.ModelValidationDiagnostic;
import com.example.editor.validation.runtime.ModelValidationReport;
import com.example.editor.validation.runtime.ValidationDiagnosticLocation;
import com.example.editor.validation.runtime.ValidationDiagnosticStatus;
import com.example.editor.validation.runtime.ValidationExecutionMetrics;
import com.example.editor.validation.runtime.ValidationReportScope;
import com.example.editor.validation.runtime.ValidationRunMode;
import com.example.editor.validation.validation.ValidationSeverity;
import static org.assertj.core.api.Assertions.assertThat;

class ValidationRuntimeEventFactoryTests {
    @Test void preservesValidModelDocumentId() {
        assertThat(ValidationRuntimeEventFactory.validation("core.validation.execution-completed", report("model-one"), Map.of()).subject().documentId()).isEqualTo("model-one");
    }
    @Test void normalizesRuntimeModelIdThatContainsColons() {
        String documentId = ValidationRuntimeEventFactory.validation("core.validation.execution-completed", report("model:one"), Map.of()).subject().documentId();
        assertThat(documentId).startsWith("runtime-model-").doesNotContain(":");
    }
    @Test void diagnosticEventUsesDiagnosticSubjectAndPayload() {
        var event = ValidationRuntimeEventFactory.diagnostic("core.validation.violation-created", report("model-one"), diagnostic());
        assertThat(event.subject().kind()).isEqualTo(EventSubjectKind.VALIDATION_DIAGNOSTIC);
        assertThat(event.subject().elementId()).isEqualTo("diagnostic:one");
        assertThat(event.payload()).containsEntry("ruleId", "rule:one");
        assertThat(event.payload()).containsEntry("diagnosticId", "diagnostic:one");
    }
    @Test void quickFixEventUsesQuickFixContextAndCorrelation() {
        var event = ValidationRuntimeEventFactory.quickFix("core.quickfix.applied", "meta", "model-one", "action:one", "diagnostic:one", Map.of("status", "OK"), "transaction:one");
        assertThat(event.context().kind()).isEqualTo(EventContextKind.QUICK_FIX);
        assertThat(event.subject().kind()).isEqualTo(EventSubjectKind.QUICK_FIX);
        assertThat(event.payload()).containsEntry("diagnosticId", "diagnostic:one");
        assertThat(event.correlationId()).isEqualTo("transaction:one");
    }
    private ModelValidationDiagnostic diagnostic() {
        return new ModelValidationDiagnostic("diagnostic:one", ValidationDiagnosticStatus.VIOLATION, "rule:one", ValidationSeverity.ERROR, "Problem",
                new ValidationDiagnosticLocation("element:one", "attribute:name", ""), List.of(), Map.of());
    }
    private ModelValidationReport report(String modelId) {
        return new ModelValidationReport("1.0", "report:one", modelId, "meta", "validations", Instant.EPOCH, ValidationReportScope.model(), List.of(diagnostic()),
                new ValidationExecutionMetrics(1, 0, 0), "plan", ValidationRunMode.FULL, Instant.EPOCH, Instant.EPOCH, List.of("rule:one"), List.of());
    }
}
