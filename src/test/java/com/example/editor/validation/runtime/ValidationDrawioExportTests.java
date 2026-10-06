package com.example.editor.validation.runtime;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.example.editor.projection.drawio.DrawioArtifactExporter;
import com.example.editor.projection.drawio.DrawioProjectionService;
import com.example.editor.validation.validation.ConversionStatus;
import com.example.editor.validation.validation.ImportedValidationSource;
import com.example.editor.validation.validation.ValidationModel;
import com.example.editor.validation.validation.ValidationRuleSet;
import com.example.editor.validation.validation.ValidationSeverity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ValidationDrawioExportTests {
    private final ValidationCompiler compiler = new ValidationCompiler(
            new com.example.editor.validation.validation.ValidationModelChecker(
                    new com.example.editor.semantic.expression.ExpressionTypeChecker(new com.example.editor.semantic.expression.ExpressionDependencyAnalyzer())),
            new ValidationPlanCache());

    @Test
    void exportsEditableValidationDefinitions() {
        ValidationModel base = ValidationRuntimeTestFixtures.validations();
        ValidationModel model = new ValidationModel(base.schemaVersion(), base.id(), base.metamodelId(), base.expressionModelId(),
                base.actionModelId(), List.of(new ValidationRuleSet("rule-set:quality", "Quality", "", true, 0,
                        List.of(ValidationRuntimeTestFixtures.RULE), Map.of())), base.rules(),
                List.of(new ImportedValidationSource("source:ocl", "OCL", "people.ocl", "abc123",
                        ConversionStatus.FULL, Instant.EPOCH, Map.of())), Map.of());
        String xml = new ValidationModelDrawioExporter().export(model, com.example.editor.projection.drawio.DrawioExportOptions.defaults());
        assertThat(xml).contains("<mxfile", ValidationRuntimeTestFixtures.RULE, ValidationRuntimeTestFixtures.EXPRESSION,
                "condition", "Rule set", "Imported source", "people.ocl");
    }

    @Test
    void exportsCompiledPlanAndDependencies() {
        ExecutableValidationPlan plan = compiler.compile(ValidationRuntimeTestFixtures.metamodel(), ValidationRuntimeTestFixtures.expressions(),
                ValidationRuntimeTestFixtures.actions(), ValidationRuntimeTestFixtures.validations(), ValidationRuntimeTestFixtures.versions()).plan();
        String xml = new ValidationPlanDrawioExporter().export(plan, com.example.editor.projection.drawio.DrawioExportOptions.defaults());
        assertThat(xml).contains("ExecutableValidationPlan", ValidationRuntimeTestFixtures.RULE, ValidationRuntimeTestFixtures.AGE,
                "indexed by", "Source versions", "Attribute index");
    }

    @Test
    void exportsRuntimeReportAndQuickFixLinks() {
        ModelValidationReport report = new ModelValidationReport("1.0", "report:people", "people-model", "people", "people-validations",
                Instant.EPOCH, ValidationReportScope.model(),
                List.of(new ModelValidationDiagnostic("diagnostic-1", ValidationDiagnosticStatus.VIOLATION, "rule:positive-age", ValidationSeverity.ERROR,
                        "Age is invalid", new ValidationDiagnosticLocation("person:bob", "attribute:Person:age", ""), List.of("action:repair"),
                        Map.of("problemCode", "PERSON_AGE_POSITIVE", "targetClassId", "class:Person"))),
                new ValidationExecutionMetrics(1, 0, 0), "people-plan", ValidationRunMode.FULL,
                Instant.EPOCH, Instant.EPOCH, List.of("rule:positive-age"), List.of());
        String xml = new ValidationReportDrawioExporter().export(report, com.example.editor.projection.drawio.DrawioExportOptions.defaults());
        assertThat(xml).contains("Age is invalid", "action:repair", "Quick fix", "Valid: false", "Scope", "Metrics",
                "Location", "person:bob", "Evaluated: 1");
    }

    @Test
    void registrySelectsExportersByStableArtifactType() {
        List<DrawioArtifactExporter<?>> exporters = List.of(new ValidationModelDrawioExporter(), new ValidationPlanDrawioExporter(), new ValidationReportDrawioExporter());
        DrawioProjectionService service = new DrawioProjectionService(exporters);
        assertThat(service.artifactTypes()).containsExactly("validation-definition", "validation-plan", "validation-report");
        assertThat(service.export("validation-definition", ValidationRuntimeTestFixtures.validations())).contains("<mxGraphModel");
    }

    @Test
    void registryRejectsUnknownOrWrongArtifactTypes() {
        DrawioProjectionService service = new DrawioProjectionService(List.of(new ValidationModelDrawioExporter()));
        assertThatThrownBy(() -> service.export("unknown", ValidationRuntimeTestFixtures.validations())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.export("validation-definition", Map.of())).isInstanceOf(IllegalArgumentException.class);
    }
}
