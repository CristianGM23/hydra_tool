package com.example.editor.integration.parity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.example.editor.integration.IntegrationTechnology;
import com.example.editor.integration.IntegrationVerificationStep;
import com.example.editor.integration.verification.TechnologyProjectValidationReport;
import com.example.editor.integration.verification.TechnologyValidationStatus;
import com.example.editor.integration.verification.TechnologyValidationStepResult;
import com.example.editor.integration.verification.TechnologyValidationStepStatus;

class NativeParityEvidenceAssessorTests {
    private final NativeParityEvidenceAssessor assessor = new NativeParityEvidenceAssessor();

    @Test void provesOnlyActualGeneratorOwnedBehavioralFixtures() {
        NativeParityCertificationReport report = assessor.assess(report("NATIVE_REQUIRED", true, "python"),
                List.of(proof(true)), List.of(NativeParityCapability.GAMIFICATION));
        assertThat(report.certified()).isTrue();
        assertThat(report.provenCount()).isEqualTo(1);
        assertThat(report.capabilities().getFirst().status()).isEqualTo(NativeParityEvidenceStatus.PROVEN);
    }

    @Test void structuralValidationCannotPretendToProveNativeParity() {
        NativeParityCertificationReport report = assessor.assess(report("STRUCTURAL", true, "python"),
                List.of(proof(true)), List.of(NativeParityCapability.GAMIFICATION));
        assertThat(report.certified()).isFalse();
        assertThat(report.capabilities().getFirst().status()).isEqualTo(NativeParityEvidenceStatus.FAILED);
    }

    @Test void missingCapabilityProofMustBeUnverified() {
        NativeParityCertificationReport report = assessor.assess(report("NATIVE_REQUIRED", true, "python"),
                List.of(proof(true)), List.of(NativeParityCapability.ROUND_TRIP));
        assertThat(report.certified()).isFalse();
        assertThat(report.capabilities().getFirst().status()).isEqualTo(NativeParityEvidenceStatus.UNVERIFIED);
    }

    @Test void optionalProofCannotCertifyAndCommandSubstitutionIsRejected() {
        assertThat(assessor.assess(report("NATIVE_REQUIRED", true, "python"), List.of(proof(false)),
                List.of(NativeParityCapability.GAMIFICATION)).certified()).isFalse();
        assertThat(assessor.assess(report("NATIVE_REQUIRED", true, "echo"), List.of(proof(true)),
                List.of(NativeParityCapability.GAMIFICATION)).certified()).isFalse();
    }

    @Test void failedNativeBuildBlocksEveryCertification() {
        assertThat(assessor.assess(report("NATIVE_REQUIRED", false, "python"), List.of(proof(true)),
                List.of(NativeParityCapability.GAMIFICATION)).certified()).isFalse();
    }

    @Test void duplicatedRequestedCapabilityAndProofIdAreRejected() {
        assertThatThrownBy(() -> assessor.assess(report("NATIVE_REQUIRED", true, "python"),
                List.of(proof(true)), List.of(NativeParityCapability.GAMIFICATION, NativeParityCapability.GAMIFICATION)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> assessor.assess(report("NATIVE_REQUIRED", true, "python"),
                List.of(proof(true), proof(true)), List.of(NativeParityCapability.GAMIFICATION)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private IntegrationVerificationStep proof(boolean required) {
        return new IntegrationVerificationStep("test-besser-package", "Validate invalid model", ".",
                List.of("python", "-m", "pytest", "-q"), 0, required);
    }

    private TechnologyProjectValidationReport report(String mode, boolean pass, String launcher) {
        TechnologyValidationStepResult step = new TechnologyValidationStepResult("test-besser-package",
                "Validates an invalid model", TechnologyValidationStepStatus.PASSED, List.of(launcher, "-m", "pytest", "-q"),
                0, 125, "ok", "", List.of());
        return new TechnologyProjectValidationReport(IntegrationTechnology.BESSER_SDK,
                pass ? TechnologyValidationStatus.PASSED : TechnologyValidationStatus.FAILED,
                null, List.of(step), Map.of("validationMode", mode));
    }
}
