package com.example.editor.validation.runtime;

import org.springframework.stereotype.Component;
import com.example.editor.projection.drawio.DrawioArtifactExporter;
import com.example.editor.projection.drawio.DrawioExportOptions;
import com.example.editor.projection.drawio.DrawioGraphBuilder;
import com.example.editor.validation.validation.ValidationSeverity;

/** Proyecta todos los elementos de un informe de ejecución, sus ubicaciones y quick fixes. */
@Component
public class ValidationReportDrawioExporter implements DrawioArtifactExporter<ModelValidationReport> {
    public static final String ARTIFACT_TYPE = "validation-report";
    /** Descripción: Ejecuta la operación «artifactType» correspondiente a la responsabilidad de ValidationReportDrawioExporter. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @Override public String artifactType() { return ARTIFACT_TYPE; }
    /** Descripción: Ejecuta la operación «sourceType» correspondiente a la responsabilidad de ValidationReportDrawioExporter. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @Override public Class<ModelValidationReport> sourceType() { return ModelValidationReport.class; }

    @Override
    /** Descripción: Ejecuta la operación «export» correspondiente a la responsabilidad de ValidationReportDrawioExporter. Entradas: report: dato de entrada necesario para la operación; options: configuración que gobierna el comportamiento. Salida: devuelve el resultado definido por el contrato del método. */ public String export(ModelValidationReport report, DrawioExportOptions options) {
        DrawioGraphBuilder graph = new DrawioGraphBuilder(report.modelId() + "-validation-report",
                options.title().isBlank() ? "Validation report" : options.title());
        graph.vertex("model", "Model\n" + report.modelId() + "\nMode: " + report.mode() + "\nValid: " + report.valid(), 40, 40, 310, 100,
                report.valid()
                        ? "rounded=1;whiteSpace=wrap;html=1;fillColor=#d5e8d4;strokeColor=#82b366;fontStyle=1;"
                        : "rounded=1;whiteSpace=wrap;html=1;fillColor=#f8cecc;strokeColor=#b85450;fontStyle=1;");

        ValidationReportScope scope = report.scope();
        String scopeText = "Scope\n" + scope.kind()
                + value("Element", scope.elementId()) + value("Feature", scope.featureId())
                + value("Rule", scope.ruleId()) + value("Rule set", scope.ruleSetId());
        graph.vertex("scope", scopeText, 40, 185, 310, 105,
                "rounded=1;whiteSpace=wrap;html=1;fillColor=#e1d5e7;strokeColor=#9673a6;");
        graph.edge("model-scope", "scope", "model", "scope",
                "edgeStyle=orthogonalEdgeStyle;dashed=1;html=1;endArrow=open;");

        ValidationExecutionMetrics metrics = report.metrics();
        String metricsText = "Metrics\nEvaluated: " + metrics.evaluatedRules() + "\nSkipped: " + metrics.skippedRules()
                + "\nDuration: " + metrics.durationMillis() + " ms"
                + "\nExecuted IDs: " + String.join(", ", report.executedRuleIds())
                + "\nSkipped IDs: " + String.join(", ", report.skippedRuleIds());
        graph.vertex("metrics", metricsText, 40, 330, 310, 130,
                "rounded=1;whiteSpace=wrap;html=1;fillColor=#dae8fc;strokeColor=#6c8ebf;");
        graph.edge("model-metrics", "execution", "model", "metrics",
                "edgeStyle=orthogonalEdgeStyle;dashed=1;html=1;endArrow=open;");

        int row = 0;
        for(ModelValidationDiagnostic diagnostic : report.diagnostics()) {
            String style = diagnostic.severity() == ValidationSeverity.ERROR
                    ? "rounded=1;whiteSpace=wrap;html=1;fillColor=#f8cecc;strokeColor=#b85450;"
                    : diagnostic.severity() == ValidationSeverity.WARNING
                            ? "rounded=1;whiteSpace=wrap;html=1;fillColor=#fff2cc;strokeColor=#d6b656;"
                            : "rounded=1;whiteSpace=wrap;html=1;fillColor=#dae8fc;strokeColor=#6c8ebf;";
            String value = diagnostic.status() + " / " + diagnostic.problemCode() + "\n" + diagnostic.message()
                    + "\nRule: " + diagnostic.ruleId();
            double y = 140 + row * 160;
            graph.vertex(diagnostic.id(), value, 430, y, 450, 105, style);
            graph.edge("model-diagnostic-" + row, diagnostic.severity().name(), "model", diagnostic.id(),
                    "edgeStyle=orthogonalEdgeStyle;html=1;endArrow=block;");

            ValidationDiagnosticLocation location = diagnostic.location();
            String locationId = diagnostic.id() + "-location";
            String locationText = "Location" + value("Element", location.modelElementId())
                    + value("Feature", location.featureId()) + value("Relation instance", location.relationInstanceId());
            graph.vertex(locationId, locationText, 940, y, 330, 85,
                    "rounded=1;whiteSpace=wrap;html=1;fillColor=#f5f5f5;strokeColor=#666666;");
            graph.edge("diagnostic-location-" + row, "located at", diagnostic.id(), locationId,
                    "edgeStyle=orthogonalEdgeStyle;dashed=1;html=1;endArrow=open;");

            int quickIndex = 0;
            for(String quickFix : diagnostic.quickFixIds()) {
                String quickId = diagnostic.id() + "-quickfix-" + quickIndex;
                graph.vertex(quickId, "Quick fix\n" + quickFix, 1320, y + quickIndex * 70, 290, 55,
                        "rounded=1;whiteSpace=wrap;html=1;fillColor=#d5e8d4;strokeColor=#82b366;");
                graph.edge("diagnostic-quickfix-" + row + "-" + quickIndex, "available", diagnostic.id(), quickId,
                        "edgeStyle=orthogonalEdgeStyle;dashed=1;html=1;endArrow=open;");
                quickIndex++;
            }
            row++;
        }
        return graph.build();
    }

    /** Descripción: Ejecuta la operación «value» correspondiente a la responsabilidad de ValidationReportDrawioExporter. Entradas: label: dato de entrada necesario para la operación; value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String value(String label, String value) {
        return value == null || value.isBlank() ? "" : "\n" + label + ": " + value;
    }
}
