package com.example.editor.metamodel.export;

import org.springframework.stereotype.Component;

import com.example.editor.metamodel.core.MetaAttribute;
import com.example.editor.metamodel.core.MetaClass;
import com.example.editor.metamodel.core.MetaClassKind;
import com.example.editor.metamodel.core.MetaDataType;
import com.example.editor.metamodel.core.MetaEnum;
import com.example.editor.metamodel.core.MetaRelation;
import com.example.editor.metamodel.core.MetaOperation;
import com.example.editor.metamodel.core.MetaParameter;
import com.example.editor.metamodel.core.MetaRelationKind;

/**
 * Genera un diagrama de clases PlantUML neutral.
 */
@Component
public class PlantUmlMetamodelExporter implements MetamodelTextExporter {

    @Override
    /** Descripción: Ejecuta la operación «format» correspondiente a la responsabilidad de PlantUmlMetamodelExporter. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public MetamodelExportFormat format() {
        return MetamodelExportFormat.PLANTUML;
    }

    @Override
    /** Descripción: Ejecuta la operación «export» correspondiente a la responsabilidad de PlantUmlMetamodelExporter. Entradas: context: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public String export(NeutralMetamodelExportContext context) {
        StringBuilder result = new StringBuilder("@startuml\nskinparam classAttributeIconSize 0\nhide empty methods\n\n");

        for(MetaEnum enumDesign : context.neutralMetamodel().enums()) {
            result.append("enum \"").append(MetamodelExportSupport.plantUml(enumDesign.name())).append("\" as ").append(alias(enumDesign.id())).append(" {\n");
            enumDesign.literals().forEach(literal -> result.append("  ").append(literal.name()).append(" = ").append(literal.value()).append("\n"));
            result.append("}\n\n");
        }

        for(MetaDataType dataType : context.neutralMetamodel().dataTypes()) {
            result.append("class \"").append(MetamodelExportSupport.plantUml(dataType.name())).append("\" as ").append(alias(dataType.id())).append(" <<datatype>>\n");
        }

        if(!context.neutralMetamodel().dataTypes().isEmpty()) {
            result.append("\n");
        }

        for(MetaClass metaClass : context.neutralMetamodel().classes()) {
            String keyword = metaClass.kind() == MetaClassKind.INTERFACE ? "interface" : metaClass.kind() == MetaClassKind.ABSTRACT_CLASS ? "abstract class" : "class";
            result.append(keyword).append(" \"").append(MetamodelExportSupport.plantUml(metaClass.name())).append("\" as ").append(alias(metaClass.id())).append(" {\n");

            for(MetaAttribute attribute : metaClass.attributes()) {
                result.append("  ").append(attribute.name()).append(" : ").append(MetamodelExportSupport.resolveTypeName(context, attribute.type())).append(" [").append(MetamodelExportSupport.multiplicity(attribute.multiplicity())).append("]\n");
            }

            for(MetaOperation operation : metaClass.operations()) {
                result.append("  ").append(operation.name()).append("(");
                for(int index = 0; index < operation.parameters().size(); index += 1) {
                    MetaParameter parameter = operation.parameters().get(index);
                    if(index > 0) result.append(", ");
                    result.append(parameter.name()).append(" : ").append(MetamodelExportSupport.resolveTypeName(context, parameter.type()));
                }
                result.append(") : ").append(operation.returnType() == null ? "void" : MetamodelExportSupport.resolveTypeName(context, operation.returnType()));
                if(operation.behaviorKind() != com.example.editor.metamodel.core.MetaOperationBehaviorKind.UNSPECIFIED) result.append(" <<").append(operation.behaviorKind().name().toLowerCase(java.util.Locale.ROOT)).append(">>");
                result.append("\n");
            }

            result.append("}\n\n");
        }

        for(MetaRelation relation : context.neutralMetamodel().relations()) {
            result.append(alias(relation.sourceTypeId())).append(" ").append(connector(relation.kind()));

            if(MetamodelExportSupport.isStructural(relation) && relation.targetMultiplicity() != null) {
                result.append(" \"").append(MetamodelExportSupport.multiplicity(relation.targetMultiplicity())).append("\"");
            }

            result.append(" ").append(alias(relation.targetTypeId()));

            if(!relation.name().isBlank() && relation.kind() != MetaRelationKind.INHERITANCE && relation.kind() != MetaRelationKind.IMPLEMENTATION) {
                result.append(" : ").append(MetamodelExportSupport.plantUml(relation.name()));
            }

            result.append("\n");
        }

        result.append("\n@enduml\n");
        return result.toString();
    }

    /** Descripción: Ejecuta la operación «connector» correspondiente a la responsabilidad de PlantUmlMetamodelExporter. Entradas: kind: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String connector(MetaRelationKind kind) {
        return switch(kind) {
            case REFERENCE -> "-->";
            case CONTAINMENT -> "*--";
            case AGGREGATION -> "o--";
            case DEPENDENCY -> "..>";
            case INHERITANCE -> "--|>";
            case IMPLEMENTATION -> "..|>";
        };
    }

    /** Descripción: Ejecuta la operación «alias» correspondiente a la responsabilidad de PlantUmlMetamodelExporter. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private String alias(String id) {
        return "M_" + MetamodelExportSupport.pascal(id, "Element").replaceAll("[^A-Za-z0-9_]", "");
    }
}
