package com.example.editor.transformation.nativeexec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.example.editor.semantic.expression.ExpressionTestFixtures;
import com.example.editor.transformation.core.TransformationBinding;
import com.example.editor.transformation.core.TransformationBindingKind;
import com.example.editor.transformation.core.TransformationDefinitionDocument;
import com.example.editor.transformation.core.TransformationEndpoint;
import com.example.editor.transformation.core.TransformationEndpointRole;
import com.example.editor.transformation.core.TransformationKind;
import com.example.editor.transformation.core.TransformationRule;
import com.example.editor.transformation.core.TransformationTargetPattern;
import com.example.editor.transformation.export.TransformationExportLanguage;
import com.example.editor.transformation.parity.TransformationParityDifference;
import com.example.editor.transformation.parity.TransformationParityReport;
import com.example.editor.transformation.parity.TransformationParityRequest;
import com.example.editor.transformation.parity.TransformationParityService;
import com.example.editor.transformation.parity.TransformationParityStatus;

/**
 * Regresión del defecto #13 de HALLAZGOS_PRODUCCION: la paridad nativa lanzaba IllegalArgumentException cuando faltaba el
 * modelo origen, mientras la verificación de paridad lo trata como evidencia inválida. Los servicios reales no tocan sus
 * colaboradores en este camino (la validación del modelo origen ocurre antes de exportar o ejecutar nada), por eso se
 * construyen sin ellos; los resultados esperados son literales.
 */
class TransformationNativeParityMissingSourceTests {
    private final TransformationNativeParityService nativeParity = new TransformationNativeParityService(null, null, null);
    private final TransformationParityService parity = new TransformationParityService(null, null, null, null);

    @Test
    void aMissingSourceModelIsInvalidEvidenceAndNothingIsExecuted() {
        TransformationNativeParityReport report = nativeParity.execute(new TransformationNativeParityRequest(
                m2m(), TransformationExportLanguage.ATL, null));

        assertThat(report.execution().status()).isEqualTo(TransformationNativeRuntimeStatus.NOT_RUN);
        assertThat(report.parity().status()).isEqualTo(TransformationParityStatus.INVALID_EVIDENCE);
        assertThat(report.parity().matched()).isFalse();
        assertThat(report.parity().differences()).containsExactly(new TransformationParityDifference("$.sourceModel", "present", "missing"));
    }

    @Test
    void nativeParityAndPlainParityAgreeOnTheMissingSourceVerdict() {
        TransformationNativeParityReport nativeReport = nativeParity.execute(new TransformationNativeParityRequest(
                m2m(), TransformationExportLanguage.ATL, null));
        TransformationParityReport plain = parity.verify(new TransformationParityRequest(
                m2m(), TransformationExportLanguage.ATL, null, null, List.of()));

        assertThat(nativeReport.parity().status()).isEqualTo(plain.status());
        assertThat(nativeReport.parity().differences()).isEqualTo(plain.differences());
    }

    @Test
    void aMissingTransformationOrLanguageIsStillAnArgumentError() {
        assertThatThrownBy(() -> nativeParity.execute(new TransformationNativeParityRequest(null, TransformationExportLanguage.ATL, null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> nativeParity.execute(new TransformationNativeParityRequest(m2m(), null, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static TransformationDefinitionDocument m2m() {
        TransformationBinding binding = new TransformationBinding("binding:name", "person", ExpressionTestFixtures.NAME,
                TransformationBindingKind.DIRECT, ExpressionTestFixtures.NAME, null, null, null, Map.of());
        TransformationRule rule = new TransformationRule("rule:person", "Person to Person", true, "source", ExpressionTestFixtures.PERSON,
                "personSource", null,
                List.of(new TransformationTargetPattern("target:person", "target", ExpressionTestFixtures.PERSON, "person", Map.of())),
                List.of(binding), Map.of());
        return new TransformationDefinitionDocument("1.0", "transformation-missing-source", "Missing source", TransformationKind.M2M,
                List.of(new TransformationEndpoint("source", TransformationEndpointRole.SOURCE, ExpressionTestFixtures.METAMODEL_ID, "", "source", true, Map.of())),
                List.of(new TransformationEndpoint("target", TransformationEndpointRole.TARGET, ExpressionTestFixtures.METAMODEL_ID, "", "target", true, Map.of())),
                List.of(rule), List.of(), null, null, null, Map.of());
    }
}
