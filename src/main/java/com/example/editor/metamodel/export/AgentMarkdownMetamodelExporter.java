package com.example.editor.metamodel.export;

import org.springframework.stereotype.Component;

import com.example.editor.metamodel.core.MetaAttribute;
import com.example.editor.metamodel.core.MetaClass;
import com.example.editor.metamodel.core.MetaRelation;
import com.example.editor.metamodel.core.MetaOperation;

/**
 * Genera Markdown estable para agentes de IA.
 */
@Component
public class AgentMarkdownMetamodelExporter implements MetamodelTextExporter {

    private final PlantUmlMetamodelExporter plantUmlExporter;

    /** Descripción: Inicializa una instancia de AgentMarkdownMetamodelExporter con sus dependencias y datos de entrada. Entradas: plantUmlExporter: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public AgentMarkdownMetamodelExporter(PlantUmlMetamodelExporter plantUmlExporter) {
        this.plantUmlExporter = plantUmlExporter;
    }

    @Override
    /** Descripción: Ejecuta la operación «format» correspondiente a la responsabilidad de AgentMarkdownMetamodelExporter. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public MetamodelExportFormat format() {
        return MetamodelExportFormat.MARKDOWN_AGENT;
    }

    @Override
    /** Descripción: Ejecuta la operación «export» correspondiente a la responsabilidad de AgentMarkdownMetamodelExporter. Entradas: context: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public String export(NeutralMetamodelExportContext context) {
        var model = context.neutralMetamodel();
        StringBuilder result = new StringBuilder();
        var ecore = context.targets().ecore();
        result.append("---\nartifactType: metamodel\nschemaVersion: ").append(model.schemaVersion()).append("\nmetamodelId: ").append(model.id()).append("\nname: ").append(model.name()).append("\nnamespaceUri: ").append(ecore.namespaceUri()).append("\nnamespacePrefix: ").append(ecore.namespacePrefix()).append("\n---\n\n");
        result.append("# Neutral metamodel specification\n\n");
        result.append("## Interpretation rules\n\n- Stable IDs are authoritative.\n- Preserve relation kinds and multiplicities.\n- `null` upper bounds mean unbounded multiplicity.\n- Do not infer missing elements from the visual layout.\n\n");
        result.append("## Class index\n\n");
        model.classes().forEach(item -> result.append("- `").append(item.id()).append("`: ").append(item.name()).append(" (").append(item.kind()).append(")\n"));
        result.append("\n## Class definitions\n\n");

        for(MetaClass metaClass : model.classes()) {
            result.append("### `").append(metaClass.id()).append("`\n\n```yaml\nid: ").append(metaClass.id()).append("\nname: ").append(metaClass.name()).append("\nkind: ").append(metaClass.kind()).append("\nattributes:\n");

            for(MetaAttribute attribute : metaClass.attributes()) {
                result.append("  - id: ").append(attribute.id()).append("\n    name: ").append(attribute.name()).append("\n    typeKind: ").append(attribute.type().kind()).append("\n    type: ").append(MetamodelExportSupport.resolveTypeName(context, attribute.type())).append("\n    multiplicity: \"").append(MetamodelExportSupport.multiplicity(attribute.multiplicity())).append("\"\n");
            }

            result.append("operations:\n");
            for(MetaOperation operation : metaClass.operations()) {
                result.append("  - id: ").append(operation.id()).append("\n    name: ").append(operation.name()).append("\n    behaviorKind: ").append(operation.behaviorKind()).append("\n    returnType: ").append(operation.returnType() == null ? "void" : MetamodelExportSupport.resolveTypeName(context, operation.returnType())).append("\n    parameters:\n");
                operation.parameters().forEach(parameter -> result.append("      - id: ").append(parameter.id()).append("\n        name: ").append(parameter.name()).append("\n        type: ").append(MetamodelExportSupport.resolveTypeName(context, parameter.type())).append("\n"));
            }

            result.append("relations:\n");

            for(MetaRelation relation : MetamodelExportSupport.relationsFrom(context, metaClass.id())) {
                result.append("  - id: ").append(relation.id()).append("\n    name: ").append(MetamodelExportSupport.text(relation.name(), "null")).append("\n    kind: ").append(relation.kind()).append("\n    targetTypeId: ").append(relation.targetTypeId()).append("\n    targetMultiplicity: \"").append(MetamodelExportSupport.multiplicity(relation.targetMultiplicity())).append("\"\n");
            }

            result.append("```\n\n");
        }

        if(context.options().includeLayout()) {
            result.append("## Diagram summary\n\n```json\n{\n  \"nodeCount\": ").append(context.diagram().nodes().size()).append(",\n  \"edgeCount\": ").append(context.diagram().edges().size()).append("\n}\n```\n\n");
        }

        if(context.options().includeWarnings() && !context.warnings().isEmpty()) {
            result.append("## Warnings\n\n");
            context.warnings().forEach(item -> result.append("- `").append(item.code()).append("`: ").append(item.message()).append("\n"));
            result.append("\n");
        }

        result.append("## PlantUML projection\n\n```plantuml\n").append(plantUmlExporter.export(context)).append("```\n");
        return result.toString();
    }
}
