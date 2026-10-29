package com.example.editor.transformation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.example.editor.semantic.expression.ExpressionDependencyAnalyzer;
import com.example.editor.semantic.expression.ExpressionTypeChecker;
import com.example.editor.transformation.core.TransformationCheckReport;
import com.example.editor.transformation.core.TransformationDefinitionDocument;
import com.example.editor.transformation.core.TransformationKind;
import com.example.editor.transformation.core.TransformationTextToModelConfiguration;

/**
 * Evidencia real, sin mocks, de la comprobacion de transformaciones texto-a-modelo que respalda
 * transformation_author_text2model.ck/ok/art: sin una gramatica compilada asignada, el comprobador
 * real rechaza la configuracion T2M con GRAMMAR_REQUIRED; con una gramatica asignada, el mismo
 * comprobador real acepta la configuracion sin errores, dejandola lista para guardarse con la
 * definicion.
 */
class TransformationDefinitionCheckerTextToModelTests {

    private final TransformationDefinitionChecker checker = new TransformationDefinitionChecker(
            new ExpressionTypeChecker(new ExpressionDependencyAnalyzer()));

    @Test
    void aTextToModelTransformationWithoutAnAssignedGrammarFailsWithGrammarRequired() {
        TransformationDefinitionDocument document = textToModelDocument(new TransformationTextToModelConfiguration("", "", ""));

        TransformationCheckReport report = checker.check(document, List.of(), List.of());

        assertThat(report.valid()).isFalse();
        assertThat(report.diagnostics()).extracting("code").contains("GRAMMAR_REQUIRED");
    }

    @Test
    void aTextToModelTransformationWithAnAssignedGrammarPassesTheRealCheckAndIsReadyToSave() {
        TransformationDefinitionDocument document = textToModelDocument(
                new TransformationTextToModelConfiguration("grammar:people", "model.hydra", ""));

        TransformationCheckReport report = checker.check(document, List.of(), List.of());

        assertThat(report.valid()).isTrue();
        assertThat(report.diagnostics()).extracting("code").doesNotContain("GRAMMAR_REQUIRED");
    }

    private static TransformationDefinitionDocument textToModelDocument(TransformationTextToModelConfiguration t2m) {
        return new TransformationDefinitionDocument("1.1", "transformation-text2model", "Text to model", TransformationKind.T2M,
                List.of(), List.of(), List.of(), List.of(), t2m, null, null, Map.of());
    }
}
