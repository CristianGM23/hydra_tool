package com.example.editor.integration.parity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import com.example.editor.integration.IntegrationVerificationStep;
import com.example.editor.integration.verification.TechnologyProjectValidationReport;
import com.example.editor.integration.verification.TechnologyValidationStatus;
import com.example.editor.integration.verification.TechnologyValidationStepResult;
import com.example.editor.integration.verification.TechnologyValidationStepStatus;

/**
 * Fail-closed functional evidence verification. Unlike version probes, a semantic
 * proof must be a generator-owned validation step whose ID is
 * explicitly registered in NativeParityProofCatalog. Unknown/unexecuted steps
 * cannot certify. New adapters must register reviewed behavioral fixtures.
 */
public final class NativeParityEvidenceAssessor {
    private static final String NATIVE_REQUIRED = "NATIVE_REQUIRED";
    private final NativeParityProofCatalog catalog = new NativeParityProofCatalog();

    /** Descripción: Contrasta las pruebas nativas declaradas por el compilador con su ejecución real. Entradas: informe, pruebas generadas y capacidades solicitadas. Salida: cobertura certificada o lagunas explícitas. */
    public NativeParityCertificationReport assess(TechnologyProjectValidationReport report,
            List<IntegrationVerificationStep> compiledProofs,
            List<NativeParityCapability> requested) {
        Objects.requireNonNull(report, "report");
        Objects.requireNonNull(compiledProofs, "compiledProofs");
        Objects.requireNonNull(requested, "requested");
        if(requested.isEmpty() || requested.stream().anyMatch(Objects::isNull)
                || requested.stream().distinct().count() != requested.size()) {
            throw new IllegalArgumentException("At least one unique capability is required");
        }
        Map<String, IntegrationVerificationStep> trusted = new HashMap<>();
        for(IntegrationVerificationStep proof : compiledProofs) {
            if(trusted.putIfAbsent(proof.id(), proof) != null) {
                throw new IllegalArgumentException("Duplicate generated proof ID: " + proof.id());
            }
        }
        Map<String, TechnologyValidationStepResult> executed = new HashMap<>();
        for(TechnologyValidationStepResult step : report.steps()) {
            if(executed.putIfAbsent(step.id(), step) != null) {
                throw new IllegalArgumentException("Duplicate executed proof ID: " + step.id());
            }
        }
        boolean nativeMode = NATIVE_REQUIRED.equals(report.metadata().get("validationMode"));
        boolean validationPassed = report.status() == TechnologyValidationStatus.PASSED;
        List<NativeParityCapabilityResult> results = new ArrayList<>();
        for(NativeParityCapability capability : requested) {
            List<String> passed = new ArrayList<>();
            List<String> failed = new ArrayList<>();
            List<String> expected = catalog.proofIds(report.technology(), capability);
            for(String id : expected) {
                IntegrationVerificationStep defined = trusted.get(id);
                TechnologyValidationStepResult found = executed.get(id);
                if(!nativeMode || !validationPassed || !defined.required() || found == null
                        || found.status() != TechnologyValidationStepStatus.PASSED
                        || found.exitCode() == null || found.exitCode() != defined.expectedExitCode()
                        || !equivalentCommand(defined.command(), found.command())) {
                    failed.add(id);
                } else {
                    passed.add(id);
                }
            }
            NativeParityEvidenceStatus status = expected.isEmpty() ? NativeParityEvidenceStatus.UNVERIFIED
                    : failed.isEmpty() ? NativeParityEvidenceStatus.PROVEN : NativeParityEvidenceStatus.FAILED;
            String reason = expected.isEmpty() ? "No generator-owned native behavioral fixture was provided"
                    : !nativeMode ? "Native-required execution was not performed"
                    : !validationPassed ? "Native project validation did not pass"
                    : failed.isEmpty() ? "All declared native behavioral fixtures completed" : "Required behavioral fixtures failed or were not run";
            results.add(new NativeParityCapabilityResult(capability, status, passed, failed, reason));
        }
        int proven = (int) results.stream().filter(value -> value.status() == NativeParityEvidenceStatus.PROVEN).count();
        boolean certified = nativeMode && validationPassed && proven == results.size();
        return new NativeParityCertificationReport(report.technology(), certified, proven,
                results.size(), results, report.status().name());
    }

    /** Descripción: Compara el comando ejecutado con el declarado sin aceptar sustituciones. Entradas: argumentos esperados y observados. Salida: true solo cuando coinciden. */
    private boolean equivalentCommand(List<String> defined, List<String> executed) {
        if(defined.isEmpty() || executed.isEmpty() || defined.size() != executed.size()) return false;
        for(int i=0; i<defined.size(); i++) {
            String wanted = defined.get(i);
            String actual = executed.get(i);
            // The existing Windows native runner translates only these three launchers.
            if(i == 0 && ((wanted.equals("mvn") && actual.equals("mvn.cmd"))
                    || (wanted.equals("npm") && actual.equals("npm.cmd"))
                    || (wanted.equals("python") && actual.equals("python.exe")))) continue;
            if(!wanted.equals(actual)) return false;
        }
        return true;
    }
}
