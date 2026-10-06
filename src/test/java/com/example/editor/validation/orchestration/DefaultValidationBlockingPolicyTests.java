package com.example.editor.validation.orchestration;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.example.editor.validation.runtime.ModelValidationDiagnostic;
import com.example.editor.validation.runtime.ModelValidationReport;
import com.example.editor.validation.runtime.ValidationDiagnosticLocation;
import com.example.editor.validation.runtime.ValidationDiagnosticStatus;
import com.example.editor.validation.runtime.ValidationExecutionMetrics;
import com.example.editor.validation.runtime.ValidationReportScope;
import com.example.editor.validation.runtime.ValidationRunMode;
import com.example.editor.validation.validation.ValidationSeverity;
import static org.assertj.core.api.Assertions.assertThat;

class DefaultValidationBlockingPolicyTests {
    private final ValidationOrchestrationProperties properties = new ValidationOrchestrationProperties();
    private final DefaultValidationBlockingPolicy policy = new DefaultValidationBlockingPolicy(properties);

    @Test void noneGateNeverBlocks() {
        assertThat(policy.evaluate(report(diagnostic(ValidationSeverity.ERROR, Map.of())), ValidationGate.NONE).blocked()).isFalse();
    }

    @Test void saveBlocksErrorsByDefault() {
        ValidationBlockDecision decision = policy.evaluate(report(diagnostic(ValidationSeverity.ERROR, Map.of())), ValidationGate.SAVE);
        assertThat(decision.blocked()).isTrue();
        assertThat(decision.diagnosticIds()).containsExactly("diagnostic:one");
    }

    @Test void warningDoesNotBlockWithoutExplicitPolicy() {
        assertThat(policy.evaluate(report(diagnostic(ValidationSeverity.WARNING, Map.of())), ValidationGate.EXPORT).blocked()).isFalse();
    }

    @Test void explicitBlockingOperationIsIndependentFromSeverity() {
        ModelValidationDiagnostic warning = diagnostic(ValidationSeverity.WARNING, Map.of("blockingOperations", "EXPORT"));
        assertThat(policy.evaluate(report(warning), ValidationGate.EXPORT).blocked()).isTrue();
    }

    @Test void saveDefaultCanBeDisabledWithoutChangingSeverity() {
        properties.setBlockSaveOnErrors(false);
        ModelValidationDiagnostic error = diagnostic(ValidationSeverity.ERROR, Map.of());
        ValidationBlockDecision decision = policy.evaluate(report(error), ValidationGate.SAVE);
        assertThat(error.severity()).isEqualTo(ValidationSeverity.ERROR);
        assertThat(decision.blocked()).isFalse();
    }

    private ModelValidationDiagnostic diagnostic(ValidationSeverity severity, Map<String, String> details) {
        return new ModelValidationDiagnostic("diagnostic:one", ValidationDiagnosticStatus.VIOLATION, "rule:one", severity, "Problem",
                new ValidationDiagnosticLocation("element:one", "attribute:name", ""), List.of(), details);
    }
    private ModelValidationReport report(ModelValidationDiagnostic diagnostic) {
        return new ModelValidationReport("1.0", "report:one", "model", "meta", "validations", Instant.EPOCH,
                ValidationReportScope.model(), List.of(diagnostic), new ValidationExecutionMetrics(1, 0, 0), "plan", ValidationRunMode.FULL,
                Instant.EPOCH, Instant.EPOCH, List.of("rule:one"), List.of());
    }
}
