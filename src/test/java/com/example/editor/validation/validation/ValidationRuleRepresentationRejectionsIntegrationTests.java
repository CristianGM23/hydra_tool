package com.example.editor.validation.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.editor.config.StorageProperties;
import com.example.editor.metamodel.application.CanonicalMetamodelApplicationService;
import com.example.editor.metamodel.canonical.CanonicalMetamodelProperties;
import com.example.editor.metamodel.canonical.JsonCanonicalMetamodelRepository;
import com.example.editor.metamodel.validation.NeutralMetamodelValidator;
import com.example.editor.semantic.expression.ExpressionDependencyAnalyzer;
import com.example.editor.semantic.expression.ExpressionEvaluator;
import com.example.editor.semantic.expression.ExpressionTestFixtures;
import com.example.editor.semantic.expression.ExpressionTypeChecker;
import com.example.editor.validation.action.ActionModelProperties;
import com.example.editor.validation.action.persistence.JsonActionModelRepository;
import com.example.editor.validation.expression.ExpressionModelApplicationService;
import com.example.editor.validation.expression.ExpressionModelProperties;
import com.example.editor.validation.expression.persistence.JsonExpressionModelRepository;
import com.example.editor.validation.validation.persistence.JsonValidationModelRepository;

import tools.jackson.databind.json.JsonMapper;

/**
 * Evidencia real de validation_author_form.rp (seccion avanzada: representacion y conversion). Guardar una
 * regla con modo externo y representacion canonica, o canonica con conversion externa, devuelve un error con
 * codigo literal; una regla externa con procedencia se guarda sin errores. Los repositorios JSON viven en
 * @TempDir y el oraculo son los codigos literales y el JSON que queda en disco.
 *
 * <p>Hallazgo de diseno: el servicio de guardado informa de los errores pero conserva el documento
 * (el informe es la puerta, no el guardado); por eso no se afirma que "no se escriba nada".
 */
class ValidationRuleRepresentationRejectionsIntegrationTests {
    private static final String M = ExpressionTestFixtures.METAMODEL_ID;
    private static final String AGE_EXPRESSION = "expression:positive-age";

    @TempDir Path temporary;

    private record Fixture(StorageProperties storage, JsonMapper mapper, ValidationModelApplicationService validations) { }

    @Test @DisplayName("validation_author_form.rp: modo externo con representacion canonica da un error literal")
    void externalModeWithCanonicalRepresentationIsReported() throws Exception {
        Fixture fixture = fixture("external-canonical");
        ValidationModelSaveResponse response = fixture.validations().create(M, model(rule("rule:ext-canonical", "EXT_CANONICAL",
                ValidationExecutionMode.EXTERNAL, AuthoritativeRepresentation.CANONICAL, ConversionStatus.FULL, null)));

        assertThat(errorCodes(response)).containsExactly("EXTERNAL_RULE_CANNOT_BE_CANONICAL");
        assertThat(errorPaths(response)).containsExactly("$.rules[0].authoritativeRepresentation");
        // Hallazgo: el documento se conserva junto al informe de errores.
        assertThat(rulesJson(fixture)).contains("rule:ext-canonical").contains("\"authoritativeRepresentation\":\"CANONICAL\"");
    }

    @Test @DisplayName("validation_author_form.rp: representacion canonica con conversion externa da un error literal")
    void canonicalRepresentationWithExternalConversionIsReported() throws Exception {
        Fixture fixture = fixture("canonical-external");
        ValidationModelSaveResponse response = fixture.validations().create(M, model(rule("rule:canonical-ext", "CANONICAL_EXT",
                ValidationExecutionMode.INCREMENTAL, AuthoritativeRepresentation.CANONICAL, ConversionStatus.EXTERNAL, null)));

        assertThat(errorCodes(response)).containsExactly("CANONICAL_RULE_CANNOT_BE_EXTERNAL");
        assertThat(errorPaths(response)).containsExactly("$.rules[0].conversionStatus");
        assertThat(rulesJson(fixture)).contains("rule:canonical-ext").contains("\"conversionStatus\":\"EXTERNAL\"");
    }

    @Test @DisplayName("validation_author_form.rp: una regla externa con procedencia se guarda sin errores ni avisos de procedencia")
    void anExternalRuleWithProvenanceIsSavedCleanly() throws Exception {
        Fixture fixture = fixture("external-with-provenance");
        ValidationProvenance provenance = new ValidationProvenance("OCL", "rules/person.ocl", "positiveAge", "sha256:abc",
                Instant.parse("2026-07-28T10:00:00Z"), "context Person inv: age > 0");
        ValidationModelSaveResponse response = fixture.validations().create(M, model(rule("rule:external-ok", "EXTERNAL_OK",
                ValidationExecutionMode.EXTERNAL, AuthoritativeRepresentation.OCL, ConversionStatus.EXTERNAL, provenance)));

        assertThat(errorCodes(response)).isEmpty();
        assertThat(allCodes(response)).doesNotContain("EXTERNAL_RULE_WITHOUT_PROVENANCE");
        String json = rulesJson(fixture);
        assertThat(json).contains("rule:external-ok").contains("\"authoritativeRepresentation\":\"OCL\"")
                .contains("\"technology\":\"OCL\"").contains("rules/person.ocl").contains("positiveAge");
        ValidationRule reread = new JsonValidationModelRepository(fixture.storage(), new ValidationModelProperties(), fixture.mapper())
                .findById(M).orElseThrow().document().rules().get(0);
        assertThat(reread.executionMode()).isEqualTo(ValidationExecutionMode.EXTERNAL);
        assertThat(reread.provenance().sourceFile()).isEqualTo("rules/person.ocl");
    }

    @Test @DisplayName("validation_author_form.rp: la misma regla externa sin procedencia solo recibe un aviso")
    void anExternalRuleWithoutProvenanceOnlyGetsAWarning() {
        Fixture fixture = fixture("external-no-provenance");
        ValidationModelSaveResponse response = fixture.validations().create(M, model(rule("rule:external-warn", "EXTERNAL_WARN",
                ValidationExecutionMode.EXTERNAL, AuthoritativeRepresentation.OCL, ConversionStatus.EXTERNAL, null)));

        assertThat(errorCodes(response)).isEmpty();
        assertThat(allCodes(response)).contains("EXTERNAL_RULE_WITHOUT_PROVENANCE");
    }

    // ---- helpers ----

    private static List<ValidationModelDiagnostic> diagnostics(ValidationModelSaveResponse response) {
        ValidationModelCheckReport report = response.checkReport();
        return Stream.concat(report.diagnostics().stream(), report.rules().stream().flatMap(rule -> rule.diagnostics().stream())).toList();
    }

    private static List<String> errorCodes(ValidationModelSaveResponse response) {
        return diagnostics(response).stream().filter(item -> item.severity() == ValidationModelDiagnosticSeverity.ERROR)
                .map(ValidationModelDiagnostic::code).toList();
    }

    private static List<String> errorPaths(ValidationModelSaveResponse response) {
        return diagnostics(response).stream().filter(item -> item.severity() == ValidationModelDiagnosticSeverity.ERROR)
                .map(ValidationModelDiagnostic::path).toList();
    }

    private static List<String> allCodes(ValidationModelSaveResponse response) {
        return diagnostics(response).stream().map(ValidationModelDiagnostic::code).toList();
    }

    /** El directorio de validaciones se comparte con otros documentos: se elige el que contiene las reglas. */
    private static String rulesJson(Fixture fixture) throws Exception {
        try (Stream<Path> files = Files.list(fixture.storage().validations())) {
            return files.filter(path -> path.getFileName().toString().endsWith(".json")).map(path -> {
                try { return Files.readString(path, StandardCharsets.UTF_8).replaceAll("\\s", ""); }
                catch(java.io.IOException exception) { throw new java.io.UncheckedIOException(exception); }
            }).filter(content -> content.contains("\"rules\"")).findFirst().orElseThrow();
        }
    }

    private Fixture fixture(String name) {
        Path root = temporary.resolve(name);
        JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();
        StorageProperties storage = new StorageProperties(root.resolve("metamodels"), root.resolve("models"), root.resolve("notations"),
                root.resolve("validations"), root.resolve("quickfixes"), root.resolve("generators"), root.resolve("assets"), root.resolve("exports"), true);
        CanonicalMetamodelApplicationService metamodelService = new CanonicalMetamodelApplicationService(
                new JsonCanonicalMetamodelRepository(storage, new CanonicalMetamodelProperties(), mapper), new NeutralMetamodelValidator());
        metamodelService.create(ExpressionTestFixtures.metamodel());
        ExpressionTypeChecker typeChecker = new ExpressionTypeChecker(new ExpressionDependencyAnalyzer());
        JsonExpressionModelRepository expressionRepository = new JsonExpressionModelRepository(storage, new ExpressionModelProperties(), mapper);
        new ExpressionModelApplicationService(expressionRepository, metamodelService, typeChecker, new ExpressionEvaluator(typeChecker))
                .create(M, ExpressionTestFixtures.expressionModel(ExpressionTestFixtures.positiveAge()));
        JsonActionModelRepository actionRepository = new JsonActionModelRepository(storage, new ActionModelProperties(), mapper);
        ValidationModelApplicationService validations = new ValidationModelApplicationService(
                new JsonValidationModelRepository(storage, new ValidationModelProperties(), mapper), expressionRepository, actionRepository,
                metamodelService, new ValidationModelChecker(typeChecker));
        return new Fixture(storage, mapper, validations);
    }

    private static ValidationRule rule(String id, String code, ValidationExecutionMode mode, AuthoritativeRepresentation representation,
            ConversionStatus conversion, ValidationProvenance provenance) {
        return new ValidationRule(id, "Rule " + code, code, "", true,
                new ValidationContext(ValidationContextKind.ATTRIBUTE, ExpressionTestFixtures.PERSON, ExpressionTestFixtures.AGE),
                ValidationSeverity.ERROR, AGE_EXPRESSION, "Age must be positive",
                mode == ValidationExecutionMode.EXTERNAL ? List.of() : List.of(ValidationTrigger.ON_ATTRIBUTE_CHANGE, ValidationTrigger.ON_SAVE),
                List.of(new ValidationDependency(ValidationDependencyKind.CLASS, ExpressionTestFixtures.PERSON),
                        new ValidationDependency(ValidationDependencyKind.ATTRIBUTE, ExpressionTestFixtures.AGE)),
                List.of(), mode, representation, conversion, provenance, Map.of());
    }

    private static ValidationModel model(ValidationRule rule) {
        return new ValidationModel("1.0", M, M, M, "",
                List.of(new ValidationRuleSet("set:default", "Default", "", true, 0, List.of(rule.id()), Map.of())), List.of(rule), List.of(), Map.of());
    }
}
