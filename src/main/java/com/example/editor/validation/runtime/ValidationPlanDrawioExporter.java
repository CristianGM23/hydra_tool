package com.example.editor.validation.runtime;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import com.example.editor.projection.drawio.DrawioArtifactExporter;
import com.example.editor.projection.drawio.DrawioExportOptions;
import com.example.editor.projection.drawio.DrawioGraphBuilder;
import com.example.editor.validation.validation.ValidationDependency;

/** Proyecta todos los elementos del plan ejecutable y sus índices de selección. */
@Component
public class ValidationPlanDrawioExporter implements DrawioArtifactExporter<ExecutableValidationPlan> {
    public static final String ARTIFACT_TYPE = "validation-plan";
    /** Descripción: Ejecuta la operación «artifactType» correspondiente a la responsabilidad de ValidationPlanDrawioExporter. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @Override public String artifactType() { return ARTIFACT_TYPE; }
    /** Descripción: Ejecuta la operación «sourceType» correspondiente a la responsabilidad de ValidationPlanDrawioExporter. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @Override public Class<ExecutableValidationPlan> sourceType() { return ExecutableValidationPlan.class; }

    @Override
    /** Descripción: Ejecuta la operación «export» correspondiente a la responsabilidad de ValidationPlanDrawioExporter. Entradas: plan: dato de entrada necesario para la operación; options: configuración que gobierna el comportamiento. Salida: devuelve el resultado definido por el contrato del método. */ public String export(ExecutableValidationPlan plan, DrawioExportOptions options) {
        DrawioGraphBuilder graph = new DrawioGraphBuilder(plan.id(), options.title().isBlank() ? "Executable validation plan" : options.title());
        String planText = "ExecutableValidationPlan\n" + plan.id() + "\nRules: " + plan.rules().size()
                + "\nFingerprint: " + abbreviate(plan.sourceFingerprint(), 16);
        graph.vertex("plan", planText, 40, 40, 330, 105,
                "rounded=1;whiteSpace=wrap;html=1;fillColor=#dae8fc;strokeColor=#6c8ebf;fontStyle=1;");

        ValidationPlanSourceVersions versions = plan.sourceVersions();
        String versionsText = "Source versions\nMetamodel: " + versions.metamodelEtag()
                + "\nExpressions: " + versions.expressionModelEtag()
                + "\nActions: " + versions.actionModelEtag()
                + "\nValidations: " + versions.validationModelEtag()
                + "\nOperations: " + versions.operationBehaviorEtag();
        graph.vertex("source-versions", versionsText, 40, 190, 330, 120,
                "rounded=1;whiteSpace=wrap;html=1;fillColor=#e1d5e7;strokeColor=#9673a6;");
        graph.edge("plan-source-versions", "compiled from", "plan", "source-versions",
                "edgeStyle=orthogonalEdgeStyle;dashed=1;html=1;endArrow=open;");

        Map<String, Boolean> dependencies = new LinkedHashMap<>();
        int row = 0;
        for(CompiledValidationRule rule : plan.rules()) {
            double y = 160 + row++ * 150;
            String value = rule.order() + ". " + rule.name() + "\n" + rule.severity() + " / " + rule.executionMode()
                    + "\nContext: " + rule.context().kind()
                    + "\nTriggers: " + String.join(", ", rule.triggers().stream().map(Enum::name).toList());
            graph.vertex(rule.ruleId(), value, 430, y, 360, 110,
                    "rounded=1;whiteSpace=wrap;html=1;fillColor=#fff2cc;strokeColor=#d6b656;");
            graph.edge("plan-rule-" + rule.ruleId(), "executes", "plan", rule.ruleId(),
                    "edgeStyle=orthogonalEdgeStyle;html=1;endArrow=block;");
            int depIndex = 0;
            for(ValidationDependency dependency : rule.dependencies()) {
                String nodeId = "dep-" + dependency.kind() + "-" + dependency.elementId();
                if(dependencies.putIfAbsent(nodeId, true) == null) {
                    graph.vertex(nodeId, dependency.kind() + "\n" + dependency.elementId(), 850, y + depIndex * 65, 285, 55,
                            "rounded=1;whiteSpace=wrap;html=1;fillColor=#f5f5f5;strokeColor=#666666;");
                }
                graph.edge("plan-dep-" + rule.ruleId() + "-" + depIndex++, "indexed by", rule.ruleId(), nodeId,
                        "edgeStyle=orthogonalEdgeStyle;dashed=1;html=1;endArrow=open;");
            }
        }

        int indexRow = 0;
        indexRow = addIndex(graph, "class-index", "Class index", plan.dependencyIndex().classRules(), indexRow);
        indexRow = addIndex(graph, "attribute-index", "Attribute index", plan.dependencyIndex().attributeRules(), indexRow);
        indexRow = addIndex(graph, "relation-index", "Relation index", plan.dependencyIndex().relationRules(), indexRow);
        indexRow = addIndex(graph, "operation-index", "Operation index", plan.dependencyIndex().operationRules(), indexRow);
        indexRow = addIndex(graph, "trigger-index", "Trigger index", plan.dependencyIndex().triggerRules(), indexRow);
        if(!plan.dependencyIndex().modelRules().isEmpty()) {
            String id = "model-index";
            graph.vertex(id, "Model index\n" + String.join("\n", plan.dependencyIndex().modelRules()), 1190, 160 + indexRow * 90, 340, 70,
                    "rounded=1;whiteSpace=wrap;html=1;fillColor=#d5e8d4;strokeColor=#82b366;");
            graph.edge("plan-model-index", "selection index", "plan", id,
                    "edgeStyle=orthogonalEdgeStyle;dashed=1;html=1;endArrow=open;");
            indexRow++;
        }

        int diagnosticRow = 0;
        for(ValidationCompilationDiagnostic diagnostic : plan.diagnostics()) {
            String id = "compile-diagnostic-" + diagnosticRow;
            graph.vertex(id, diagnostic.severity() + "\n" + diagnostic.code() + "\n" + diagnostic.message(), 1580,
                    160 + diagnosticRow * 105, 390, 85,
                    diagnostic.severity() == ValidationCompilationSeverity.ERROR
                            ? "rounded=1;whiteSpace=wrap;html=1;fillColor=#f8cecc;strokeColor=#b85450;"
                            : "rounded=1;whiteSpace=wrap;html=1;fillColor=#fff2cc;strokeColor=#d6b656;");
            graph.edge("plan-diagnostic-" + diagnosticRow, "compilation", "plan", id,
                    "edgeStyle=orthogonalEdgeStyle;dashed=1;html=1;endArrow=open;");
            diagnosticRow++;
        }
        return graph.build();
    }

    /** Descripción: Ejecuta la operación «addIndex» correspondiente a la responsabilidad de ValidationPlanDrawioExporter. Entradas: graph: dato de entrada necesario para la operación; idPrefix: dato de entrada necesario para la operación; title: dato de entrada necesario para la operación; values: colección de elementos que debe procesarse; row: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private int addIndex(DrawioGraphBuilder graph, String idPrefix, String title, Map<String, List<String>> values, int row) {
        for(Map.Entry<String, List<String>> entry : values.entrySet()) {
            String id = idPrefix + ":" + entry.getKey();
            graph.vertex(id, title + "\n" + entry.getKey() + "\n" + String.join("\n", entry.getValue()), 1190,
                    160 + row * 90, 340, 70,
                    "rounded=1;whiteSpace=wrap;html=1;fillColor=#d5e8d4;strokeColor=#82b366;");
            graph.edge("plan-index-" + idPrefix + "-" + row, "selection index", "plan", id,
                    "edgeStyle=orthogonalEdgeStyle;dashed=1;html=1;endArrow=open;");
            row++;
        }
        return row;
    }

    /** Descripción: Ejecuta la operación «abbreviate» correspondiente a la responsabilidad de ValidationPlanDrawioExporter. Entradas: value: valor de entrada que debe procesarse; length: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String abbreviate(String value, int length) {
        if(value == null || value.length() <= length) return value == null ? "" : value;
        return value.substring(0, length) + "…";
    }
}
