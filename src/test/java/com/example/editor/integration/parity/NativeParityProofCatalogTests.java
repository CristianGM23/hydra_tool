package com.example.editor.integration.parity;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.example.editor.integration.IntegrationTechnology;

class NativeParityProofCatalogTests {
    private final NativeParityProofCatalog catalog = new NativeParityProofCatalog();

    @Test void languageParserSmokeIsNotMisrepresentedAsFullTextualEditorParity() {
        assertThat(catalog.proofIds(IntegrationTechnology.LANGIUM, NativeParityCapability.LANGUAGE_SERVICE_SMOKE))
                .containsExactly("generate-langium-language");
        assertThat(catalog.proofIds(IntegrationTechnology.LANGIUM, NativeParityCapability.TEXTUAL_EDITOR))
                .isEmpty();
    }

    @Test void nativeBuildEvidenceDoesNotProveFullGraphicalParity() {
        assertThat(catalog.proofIds(IntegrationTechnology.ECLIPSE_GLSP, NativeParityCapability.NATIVE_BUILD))
                .containsExactly("build-glsp-client", "build-glsp-server");
        assertThat(catalog.proofIds(IntegrationTechnology.ECLIPSE_GLSP, NativeParityCapability.GRAPHICAL_EDITOR))
                .isEmpty();
    }

    @Test void generatedBesserSdkLifecycleTestSupportsOnlyItsActuallyCoveredScopes() {
        assertThat(catalog.proofIds(IntegrationTechnology.BESSER_SDK, NativeParityCapability.GAMIFICATION))
                .containsExactly("test-besser-package");
        assertThat(catalog.proofIds(IntegrationTechnology.BESSER_SDK, NativeParityCapability.ROUND_TRIP))
                .isEqualTo(List.of());
    }
}
