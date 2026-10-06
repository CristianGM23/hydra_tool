package com.example.editor.metamodel.export;

import org.springframework.stereotype.Component;

import com.example.editor.metamodel.core.MetaAttribute;
import com.example.editor.metamodel.core.MetaClass;
import com.example.editor.metamodel.core.MetaDataType;
import com.example.editor.metamodel.core.MetaEnum;
import com.example.editor.metamodel.core.MetaRelation;
import com.example.editor.metamodel.core.MetaOperation;

/**
 * Genera documentación Markdown neutral.
 */
@Component
public class MarkdownMetamodelExporter implements MetamodelTextExporter {

    @Override
    /** Descripción: Ejecuta la operación «format» correspondiente a la responsabilidad de MarkdownMetamodelExporter. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public MetamodelExportFormat format() {
        return MetamodelExportFormat.MARKDOWN;
    }

    @Override
    /** Descripción: Ejecuta la operación «export» correspondiente a la responsabilidad de MarkdownMetamodelExporter. Entradas: context: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public String export(NeutralMetamodelExportContext context) {
        var model = context.neutralMetamodel();
        StringBuilder result = new StringBuilder();
        result.append("# Metamodel: ").append(model.name()).append("\n\n");
        result.append("- Stable ID: `").append(model.id()).append("`\n");
        result.append("- Schema version: `").append(model.schemaVersion()).append("`\n");
        var ecore = context.targets().ecore();
        result.append("- Namespace URI: `").append(ecore.namespaceUri()).append("`\n");
        result.append("- Namespace prefix: `").append(ecore.namespacePrefix()).append("`\n\n");
        appendDataTypes(result, context);
        appendEnums(result, model.enums());
        result.append("## Classes\n\n");

        for(MetaClass metaClass : model.classes()) {
            result.append("### ").append(metaClass.name()).append("\n\n");
            result.append("- Stable ID: `").append(metaClass.id()).append("`\n");
            result.append("- Kind: ").append(metaClass.kind()).append("\n\n");
            result.append("#### Attributes\n\n");

            if(metaClass.attributes().isEmpty()) {
                result.append("No attributes.\n\n");
            } else {
                result.append("| Name | Type | Multiplicity | Default |\n|---|---|---:|---|\n");

                for(MetaAttribute attribute : metaClass.attributes()) {
                    result.append("| ").append(MetamodelExportSupport.markdown(attribute.name())).append(" | ").append(MetamodelExportSupport.markdown(MetamodelExportSupport.resolveTypeName(context, attribute.type()))).append(" | ").append(MetamodelExportSupport.multiplicity(attribute.multiplicity())).append(" | ").append(MetamodelExportSupport.markdown(MetamodelExportSupport.text(attribute.defaultValue(), "—"))).append(" |\n");
                }

                result.append("\n");
            }

            result.append("#### Operations\n\n");
            if(metaClass.operations().isEmpty()) {
                result.append("No operations.\n\n");
            } else {
                result.append("| Name | Parameters | Return | Behavior |\n|---|---|---|---|\n");
                for(MetaOperation operation : metaClass.operations()) {
                    String parameters = operation.parameters().stream().map(parameter -> parameter.name() + ": " + MetamodelExportSupport.resolveTypeName(context, parameter.type())).collect(java.util.stream.Collectors.joining(", "));
                    String returnType = operation.returnType() == null ? "void" : MetamodelExportSupport.resolveTypeName(context, operation.returnType());
                    result.append("| ").append(MetamodelExportSupport.markdown(operation.name())).append(" | ").append(MetamodelExportSupport.markdown(parameters)).append(" | ").append(MetamodelExportSupport.markdown(returnType)).append(" | ").append(operation.behaviorKind()).append(" |\n");
                }
                result.append("\n");
            }

            result.append("#### Relations\n\n");
            var relations = MetamodelExportSupport.relationsFrom(context, metaClass.id());

            if(relations.isEmpty()) {
                result.append("No outgoing relations.\n\n");
            } else {
                result.append("| Name | Target | Multiplicity | Kind |\n|---|---|---:|---|\n");

                for(MetaRelation relation : relations) {
                    result.append("| ").append(MetamodelExportSupport.markdown(MetamodelExportSupport.text(relation.name(), "—"))).append(" | ").append(MetamodelExportSupport.markdown(MetamodelExportSupport.relationTargetName(context, relation))).append(" | ").append(MetamodelExportSupport.multiplicity(relation.targetMultiplicity())).append(" | ").append(relation.kind()).append(" |\n");
                }

                result.append("\n");
            }
        }

        if(context.options().includeWarnings() && !context.warnings().isEmpty()) {
            result.append("## Transformation warnings\n\n");
            context.warnings().forEach(item -> result.append("- `").append(item.code()).append("` at `").append(item.path()).append("`: ").append(item.message()).append("\n"));
        }

        return result.toString();
    }

    /** Descripción: Ejecuta la operación «appendDataTypes» correspondiente a la responsabilidad de MarkdownMetamodelExporter. Entradas: result: dato de entrada necesario para la operación; context: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void appendDataTypes(StringBuilder result, NeutralMetamodelExportContext context) {
        var dataTypes = context.neutralMetamodel().dataTypes();

        if(dataTypes.isEmpty()) {
            return;
        }

        result.append("## Custom data types\n\n| Name | Implementation | Serializable |\n|---|---|---:|\n");
        dataTypes.forEach(item -> {
            var configuration = context.targets().ecore().dataType(item.id());
            result.append("| ").append(item.name()).append(" | `").append(configuration.instanceClassName()).append("` | ").append(configuration.serializable()).append(" |\n");
        });
        result.append("\n");
    }

    /** Descripción: Ejecuta la operación «appendEnums» correspondiente a la responsabilidad de MarkdownMetamodelExporter. Entradas: result: dato de entrada necesario para la operación; enums: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void appendEnums(StringBuilder result, java.util.List<MetaEnum> enums) {
        if(enums.isEmpty()) {
            return;
        }

        result.append("## Enumerations\n\n");

        for(MetaEnum enumDesign : enums) {
            result.append("### ").append(enumDesign.name()).append("\n\n| Literal | Value |\n|---|---:|\n");
            enumDesign.literals().forEach(literal -> result.append("| ").append(literal.name()).append(" | ").append(literal.value()).append(" |\n"));
            result.append("\n");
        }
    }
}
