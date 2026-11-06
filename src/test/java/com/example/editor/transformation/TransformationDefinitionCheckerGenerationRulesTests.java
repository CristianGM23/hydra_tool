package com.example.editor.transformation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.semantic.expression.ExpressionDependencyAnalyzer;
import com.example.editor.semantic.expression.ExpressionTestFixtures;
import com.example.editor.semantic.expression.ExpressionTypeChecker;
import com.example.editor.transformation.core.GenerationOverwritePolicy;
import com.example.editor.transformation.core.TransformationDefinitionDocument;
import com.example.editor.transformation.core.TransformationEndpoint;
import com.example.editor.transformation.core.TransformationEndpointRole;
import com.example.editor.transformation.core.TransformationGenerationRule;
import com.example.editor.transformation.core.TransformationKind;

/**
 * Evidencia FUERTE real, sin mocks, de la comprobación de reglas de generación de ficheros (M2T).
 * Cubre directamente "ck" (Comprobar: identificador único, clase existente, ruta relativa y
 * portable, y al menos una regla) y "ok" (¿Sin errores?: un error impide guardar) de
 * transformation_author_generation, usando el comprobador real citado por el propio modelo para
 * las etapas de comprobación de este tipo de transformación.
 */
class TransformationDefinitionCheckerGenerationRulesTests {
    private final TransformationDefinitionChecker checker = new TransformationDefinitionChecker(
            new ExpressionTypeChecker(new ExpressionDependencyAnalyzer()));

    @Test
    void acceptsAGenerationRuleWithAUniqueIdExistingClassAndSafeRelativePath() {
        MetaModel model = ExpressionTestFixtures.metamodel();
        TransformationGenerationRule rule = new TransformationGenerationRule("rule:person-file", "Person file", true,
                "source", ExpressionTestFixtures.PERSON, null, "generated/{{name}}.txt", "Hello {{name}}",
                GenerationOverwritePolicy.FAIL, Map.of());

        var report = checker.check(document(List.of(rule)), List.of(model), List.of());

        // "ok": sin errores, la definición puede guardarse.
        assertThat(report.valid()).isTrue();
    }

    @Test
    void requiresAtLeastOneGenerationRule() {
        MetaModel model = ExpressionTestFixtures.metamodel();

        var report = checker.check(document(List.of()), List.of(model), List.of());

        // "ck": sin ninguna regla, el espacio de generación está incompleto.
        assertThat(report.diagnostics()).extracting(item -> item.code()).contains("GENERATION_RULE_REQUIRED");
        assertThat(report.valid()).isFalse();
    }

    @Test
    void rejectsDuplicateIdsUnknownEndpointsUnknownClassesAndUnsafePaths() {
        MetaModel model = ExpressionTestFixtures.metamodel();
        TransformationGenerationRule first = new TransformationGenerationRule("rule:duplicate", "First", true,
                "source", ExpressionTestFixtures.PERSON, null, "generated/a.txt", "", GenerationOverwritePolicy.FAIL, Map.of());
        // Mismo id que "first" (duplicado), origen inexistente, clase inexistente y ruta absoluta insegura.
        TransformationGenerationRule duplicate = new TransformationGenerationRule("rule:duplicate", "Second", true,
                "missing-source", "class:Ghost", null, "/etc/passwd", "", GenerationOverwritePolicy.FAIL, Map.of());
        TransformationGenerationRule blankPath = new TransformationGenerationRule("rule:blank-path", "Third", true,
                "source", ExpressionTestFixtures.PERSON, null, "", "", GenerationOverwritePolicy.FAIL, Map.of());

        var report = checker.check(document(List.of(first, duplicate, blankPath)), List.of(model), List.of());

        // "ck": cada problema real produce su propio diagnóstico, sin inventar ninguno silenciosamente.
        assertThat(report.diagnostics()).extracting(item -> item.code()).contains(
                "GENERATION_RULE_ID_REQUIRED_OR_DUPLICATED", "UNKNOWN_SOURCE_ENDPOINT", "UNKNOWN_GENERATION_CONTEXT",
                "UNSAFE_OUTPUT_PATH", "OUTPUT_PATH_REQUIRED");
        // "ok": con errores reales presentes, la definición no puede darse por válida.
        assertThat(report.valid()).isFalse();
    }

    private TransformationDefinitionDocument document(List<TransformationGenerationRule> generationRules) {
        return new TransformationDefinitionDocument("1.0", "transformation:m2t-test", "M2T test", TransformationKind.M2T,
                List.of(new TransformationEndpoint("source", TransformationEndpointRole.SOURCE, ExpressionTestFixtures.METAMODEL_ID, "", "source", true, Map.of())),
                List.of(), List.of(), generationRules, null, null, null, Map.of());
    }
}
