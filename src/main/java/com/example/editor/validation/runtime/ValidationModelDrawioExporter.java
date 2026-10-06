package com.example.editor.validation.runtime;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import com.example.editor.projection.drawio.DrawioArtifactExporter;
import com.example.editor.projection.drawio.DrawioExportOptions;
import com.example.editor.projection.drawio.DrawioGraphBuilder;
import com.example.editor.validation.validation.ImportedValidationSource;
import com.example.editor.validation.validation.ValidationDependency;
import com.example.editor.validation.validation.ValidationModel;
import com.example.editor.validation.validation.ValidationRule;
import com.example.editor.validation.validation.ValidationRuleSet;

/** Proyecta todos los elementos editables de ValidationModel a un diagrama draw.io. */
@Component
public class ValidationModelDrawioExporter implements DrawioArtifactExporter<ValidationModel> {
    public static final String ARTIFACT_TYPE = "validation-definition";
    /** Descripción: Ejecuta la operación «artifactType» correspondiente a la responsabilidad de ValidationModelDrawioExporter. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @Override public String artifactType() { return ARTIFACT_TYPE; }
    /** Descripción: Ejecuta la operación «sourceType» correspondiente a la responsabilidad de ValidationModelDrawioExporter. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @Override public Class<ValidationModel> sourceType() { return ValidationModel.class; }

    @Override
    /** Descripción: Ejecuta la operación «export» correspondiente a la responsabilidad de ValidationModelDrawioExporter. Entradas: model: dato de entrada necesario para la operación; options: configuración que gobierna el comportamiento. Salida: devuelve el resultado definido por el contrato del método. */ public String export(ValidationModel model, DrawioExportOptions options) {
        DrawioGraphBuilder graph = new DrawioGraphBuilder(model.id(), options.title().isBlank() ? "Validation definitions" : options.title());
        String modelText = "ValidationModel\n" + model.id() + "\nMetamodel: " + model.metamodelId()
                + "\nExpressions: " + model.expressionModelId()
                + (model.actionModelId().isBlank() ? "" : "\nActions: " + model.actionModelId());
        graph.vertex("validation-model", modelText, 40, 40, 310, 115,
                "rounded=1;whiteSpace=wrap;html=1;fillColor=#dae8fc;strokeColor=#6c8ebf;fontStyle=1;");

        Map<String, ValidationRule> rulesById = new LinkedHashMap<>();
        model.rules().forEach(rule -> rulesById.put(rule.id(), rule));
        Set<String> rulesInSets = new LinkedHashSet<>();
        int ruleSetRow = 0;
        for(ValidationRuleSet ruleSet : model.ruleSets()) {
            double y = 40 + ruleSetRow++ * 120;
            String value = "Rule set\n" + ruleSet.name() + "\nOrder: " + ruleSet.order()
                    + " / " + (ruleSet.enabled() ? "enabled" : "disabled");
            graph.vertex(ruleSet.id(), value, 410, y, 280, 85,
                    "rounded=1;whiteSpace=wrap;html=1;fillColor=#e1d5e7;strokeColor=#9673a6;");
            graph.edge("model-ruleset-" + ruleSet.id(), "rule set", "validation-model", ruleSet.id(),
                    "edgeStyle=orthogonalEdgeStyle;html=1;endArrow=block;");
            int link = 0;
            for(String ruleId : ruleSet.ruleIds()) {
                rulesInSets.add(ruleId);
                if(rulesById.containsKey(ruleId)) {
                    graph.edge("ruleset-rule-" + ruleSet.id() + "-" + link++, "contains", ruleSet.id(), ruleId,
                            "edgeStyle=orthogonalEdgeStyle;html=1;endArrow=open;");
                }
            }
        }

        Map<String, String> externalNodes = new LinkedHashMap<>();
        int row = 0;
        for(ValidationRule rule : model.rules()) {
            double y = 190 + row++ * 175;
            String value = rule.name() + "\n[" + rule.severity() + "] " + rule.code()
                    + "\nContext: " + rule.context().kind()
                    + (rule.context().classId().isBlank() ? "" : " / " + rule.context().classId())
                    + (rule.context().featureId().isBlank() ? "" : " / " + rule.context().featureId())
                    + "\nExecution: " + rule.executionMode()
                    + "\nConversion: " + rule.conversionStatus();
            graph.vertex(rule.id(), value, 750, y, 360, 130,
                    rule.enabled()
                            ? "rounded=1;whiteSpace=wrap;html=1;fillColor=#fff2cc;strokeColor=#d6b656;"
                            : "rounded=1;whiteSpace=wrap;html=1;fillColor=#f5f5f5;strokeColor=#666666;dashed=1;");
            if(!rulesInSets.contains(rule.id())) {
                graph.edge("model-rule-" + rule.id(), "unassigned rule", "validation-model", rule.id(),
                        "edgeStyle=orthogonalEdgeStyle;rounded=0;html=1;endArrow=block;");
            }

            String expressionNode = "expression:" + rule.conditionExpressionId();
            if(externalNodes.putIfAbsent(expressionNode, expressionNode) == null) {
                graph.vertex(expressionNode, "Expression\n" + rule.conditionExpressionId(), 1180, y, 290, 65,
                        "rounded=1;whiteSpace=wrap;html=1;fillColor=#e1d5e7;strokeColor=#9673a6;");
            }
            graph.edge("condition-" + rule.id(), "condition", rule.id(), expressionNode,
                    "edgeStyle=orthogonalEdgeStyle;dashed=1;html=1;endArrow=open;");

            int quickIndex = 0;
            for(String quickFixId : rule.quickFixIds()) {
                String nodeId = "quickfix:" + quickFixId;
                if(externalNodes.putIfAbsent(nodeId, nodeId) == null) {
                    graph.vertex(nodeId, "Quick fix\n" + quickFixId, 1510, y + quickIndex * 70, 280, 55,
                            "rounded=1;whiteSpace=wrap;html=1;fillColor=#d5e8d4;strokeColor=#82b366;");
                }
                graph.edge("quickfix-" + rule.id() + "-" + quickIndex++, "quick fix", rule.id(), nodeId,
                        "edgeStyle=orthogonalEdgeStyle;html=1;endArrow=block;");
            }
            int depIndex = 0;
            for(ValidationDependency dependency : rule.dependencies()) {
                String nodeId = "dependency:" + dependency.kind() + ":" + dependency.elementId();
                if(externalNodes.putIfAbsent(nodeId, nodeId) == null) {
                    graph.vertex(nodeId, dependency.kind() + "\n" + dependency.elementId(), 1510, y + 80 + depIndex * 65, 280, 55,
                            "rounded=1;whiteSpace=wrap;html=1;fillColor=#f5f5f5;strokeColor=#666666;");
                }
                graph.edge("dependency-" + rule.id() + "-" + depIndex++, "depends", rule.id(), nodeId,
                        "edgeStyle=orthogonalEdgeStyle;dashed=1;html=1;endArrow=open;");
            }
        }

        int sourceRow = 0;
        for(ImportedValidationSource source : model.importedSources()) {
            String nodeId = "imported-source:" + source.id();
            String value = "Imported source\n" + source.technology() + " / " + source.conversionStatus()
                    + "\n" + source.fileName() + "\nHash: " + source.contentHash();
            graph.vertex(nodeId, value, 40, 220 + sourceRow * 110, 310, 90,
                    "rounded=1;whiteSpace=wrap;html=1;fillColor=#ffe6cc;strokeColor=#d79b00;");
            graph.edge("model-import-" + sourceRow, "provenance", "validation-model", nodeId,
                    "edgeStyle=orthogonalEdgeStyle;dashed=1;html=1;endArrow=open;");
            sourceRow++;
        }
        return graph.build();
    }
}
