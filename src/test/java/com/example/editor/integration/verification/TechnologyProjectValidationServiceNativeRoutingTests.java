package com.example.editor.integration.verification;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.editor.integration.IntegrationTechnology;
import com.example.editor.integration.IntegrationVerificationStep;
import com.example.editor.integration.compiler.TechnologyCompilationResult;

/**
 * Evidencia real, sin mocks, de que TechnologyProjectValidationService solo arranca el runner
 * externo real cuando el modo de validación realmente lo exige: cubre directamente "d2"
 * (¿Necesita un programa externo?) de security_filter_sandbox, usando el mismo
 * ExternalToolProcessRunner real que respalda las etapas ya cerradas b/d3/c/x.
 */
class TechnologyProjectValidationServiceNativeRoutingTests {

    @Test
    void structuralModeNeverStartsTheExternalProcessRunnerForANativeVerificationStep(@TempDir Path temporary) {
        var report = validate(temporary, TechnologyProjectValidationRequest.structural());

        assertThat(report.steps()).noneMatch(step -> step.id().equals("native-check"));
    }

    @Test
    void nativeRequiredModeReallyRoutesThroughTheSandboxedRunnerAndHonestlyDeclaresTheMissingToolUnavailable(@TempDir Path temporary) {
        var report = validate(temporary, new TechnologyProjectValidationRequest(TechnologyValidationMode.NATIVE_REQUIRED, null, 30, false, Map.of()));

        assertThat(report.steps()).anySatisfy(step -> {
            assertThat(step.id()).isEqualTo("native-check");
            assertThat(step.status()).isEqualTo(TechnologyValidationStepStatus.TOOL_UNAVAILABLE);
        });
        assertThat(report.status()).isEqualTo(TechnologyValidationStatus.FAILED);
    }

    private TechnologyProjectValidationReport validate(Path temporary, TechnologyProjectValidationRequest request) {
        var verificationStep = new IntegrationVerificationStep("native-check", "Run a native tool that does not exist",
                ".", List.of("hydra-definitely-missing-binary-9f3c2a", "--help"), 0, true);
        var compilation = new TechnologyCompilationResult(IntegrationTechnology.XTEXT, List.of(), List.of(), List.of(),
                List.of(), List.of(verificationStep), List.of(), Map.of());
        var validators = new TechnologyProjectStructureValidatorRegistry(List.of(new XtextProjectStructureValidator()));
        var properties = new TechnologyProjectValidationProperties();
        properties.setWorkspaceRoot(temporary);
        properties.setDefaultTimeoutSeconds(30);
        var service = new TechnologyProjectValidationService(validators, new ExternalToolProcessRunner(new ExternalToolCircuitBreaker()), properties);
        return service.validate(compilation, request);
    }
}
