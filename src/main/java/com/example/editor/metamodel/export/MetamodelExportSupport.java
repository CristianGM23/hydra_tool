package com.example.editor.metamodel.export;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.example.editor.metamodel.core.MetaAttribute;
import com.example.editor.metamodel.core.MetaClass;
import com.example.editor.metamodel.core.MetaDataType;
import com.example.editor.metamodel.core.MetaEnum;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.core.MetaPrimitiveType;
import com.example.editor.metamodel.core.MetaRelation;
import com.example.editor.metamodel.core.MetaRelationKind;
import com.example.editor.metamodel.core.MetaTypeReference;
import com.example.editor.metamodel.core.MetaTypeReferenceKind;
import com.example.editor.metamodel.core.Multiplicity;
import com.example.editor.metamodel.design.MetamodelDiagramDocument.EdgeDesign;
import com.example.editor.metamodel.design.MetamodelDiagramDocument.NodeDesign;

/**
 * Reúne operaciones neutrales compartidas por los exportadores.
 */
public final class MetamodelExportSupport {

    /** Descripción: Inicializa una instancia de MetamodelExportSupport con sus dependencias y datos de entrada. Entradas: no requiere parámetros de entrada. Salida: deja la instancia inicializada y lista para su uso. */ private MetamodelExportSupport() {
    }

    /** Descripción: Ejecuta la operación «indexClasses» correspondiente a la responsabilidad de MetamodelExportSupport. Entradas: context: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static Map<String, MetaClass> indexClasses(NeutralMetamodelExportContext context) {
        Map<String, MetaClass> result = new LinkedHashMap<>();

        for(MetaClass item : context.neutralMetamodel().classes()) {
            result.put(item.id(), item);
        }

        return result;
    }

    /** Descripción: Ejecuta la operación «indexEnums» correspondiente a la responsabilidad de MetamodelExportSupport. Entradas: context: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static Map<String, MetaEnum> indexEnums(NeutralMetamodelExportContext context) {
        Map<String, MetaEnum> result = new LinkedHashMap<>();

        for(MetaEnum item : context.neutralMetamodel().enums()) {
            result.put(item.id(), item);
        }

        return result;
    }

    /** Descripción: Ejecuta la operación «indexDataTypes» correspondiente a la responsabilidad de MetamodelExportSupport. Entradas: context: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static Map<String, MetaDataType> indexDataTypes(NeutralMetamodelExportContext context) {
        Map<String, MetaDataType> result = new LinkedHashMap<>();

        for(MetaDataType item : context.neutralMetamodel().dataTypes()) {
            result.put(item.id(), item);
        }

        return result;
    }

    /** Descripción: Ejecuta la operación «indexRelations» correspondiente a la responsabilidad de MetamodelExportSupport. Entradas: context: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static Map<String, MetaRelation> indexRelations(NeutralMetamodelExportContext context) {
        Map<String, MetaRelation> result = new LinkedHashMap<>();

        for(MetaRelation item : context.neutralMetamodel().relations()) {
            result.put(item.id(), item);
        }

        return result;
    }

    /** Descripción: Ejecuta la operación «indexNodes» correspondiente a la responsabilidad de MetamodelExportSupport. Entradas: context: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static Map<String, NodeDesign> indexNodes(NeutralMetamodelExportContext context) {
        Map<String, NodeDesign> result = new HashMap<>();

        if(!context.options().includeLayout()) {
            return result;
        }

        for(NodeDesign item : context.diagram().nodes()) {
            result.put(item.elementId(), item);
        }

        return result;
    }

    /** Descripción: Ejecuta la operación «indexEdges» correspondiente a la responsabilidad de MetamodelExportSupport. Entradas: context: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static Map<String, EdgeDesign> indexEdges(NeutralMetamodelExportContext context) {
        Map<String, EdgeDesign> result = new HashMap<>();

        if(!context.options().includeLayout()) {
            return result;
        }

        for(EdgeDesign item : context.diagram().edges()) {
            result.put(item.relationId(), item);
        }

        return result;
    }

    /** Descripción: Ejecuta la operación «relationsFrom» correspondiente a la responsabilidad de MetamodelExportSupport. Entradas: context: dato de entrada necesario para la operación; sourceTypeId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public static List<MetaRelation> relationsFrom(NeutralMetamodelExportContext context, String sourceTypeId) {
        List<MetaRelation> result = new ArrayList<>();

        for(MetaRelation relation : context.neutralMetamodel().relations()) {
            if(relation.sourceTypeId().equals(sourceTypeId)) {
                result.add(relation);
            }
        }

        return result;
    }

    /** Descripción: Ejecuta la operación «structuralRelationsFrom» correspondiente a la responsabilidad de MetamodelExportSupport. Entradas: context: dato de entrada necesario para la operación; sourceTypeId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public static List<MetaRelation> structuralRelationsFrom(NeutralMetamodelExportContext context, String sourceTypeId) {
        return relationsFrom(context, sourceTypeId).stream().filter(MetamodelExportSupport::isStructural).toList();
    }

    /** Descripción: Ejecuta la operación «superTypeRelationsFrom» correspondiente a la responsabilidad de MetamodelExportSupport. Entradas: context: dato de entrada necesario para la operación; sourceTypeId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public static List<MetaRelation> superTypeRelationsFrom(NeutralMetamodelExportContext context, String sourceTypeId) {
        return relationsFrom(context, sourceTypeId).stream().filter(item -> item.kind() == MetaRelationKind.INHERITANCE || item.kind() == MetaRelationKind.IMPLEMENTATION).toList();
    }

    /** Descripción: Evalúa la condición «isStructural» sin modificar el estado externo. Entradas: relation: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ public static boolean isStructural(MetaRelation relation) {
        return relation.kind() == MetaRelationKind.REFERENCE || relation.kind() == MetaRelationKind.CONTAINMENT || relation.kind() == MetaRelationKind.AGGREGATION;
    }

    /** Descripción: Obtiene la información correspondiente a «resolveTypeName» dentro de MetamodelExportSupport. Entradas: context: dato de entrada necesario para la operación; type: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static String resolveTypeName(NeutralMetamodelExportContext context, MetaTypeReference type) {
        if(type == null || type.kind() == null) {
            return "Object";
        }

        if(type.kind() == MetaTypeReferenceKind.PRIMITIVE) {
            return primitiveDisplayName(type.targetId());
        }

        if(type.kind() == MetaTypeReferenceKind.ENUM) {
            MetaEnum value = indexEnums(context).get(type.targetId());
            return value == null ? type.targetId() : value.name();
        }

        if(type.kind() == MetaTypeReferenceKind.DATATYPE) {
            MetaDataType value = indexDataTypes(context).get(type.targetId());
            return value == null ? type.targetId() : value.name();
        }

        if(type.kind() == MetaTypeReferenceKind.CLASS) {
            MetaClass value = indexClasses(context).get(type.targetId());
            return value == null ? type.targetId() : value.name();
        }

        return text(type.externalName(), "Object");
    }

    /** Descripción: Ejecuta la operación «primitiveDisplayName» correspondiente a la responsabilidad de MetamodelExportSupport. Entradas: primitiveId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public static String primitiveDisplayName(String primitiveId) {
        try {
            return switch(MetaPrimitiveType.valueOf(primitiveId)) {
                case STRING -> "String";
                case BOOLEAN -> "Boolean";
                case BYTE -> "Byte";
                case SHORT -> "Short";
                case INTEGER -> "Integer";
                case LONG -> "Long";
                case FLOAT -> "Float";
                case DOUBLE -> "Double";
                case DECIMAL -> "Decimal";
                case BIG_INTEGER -> "BigInteger";
                case CHARACTER -> "Character";
                case DATE -> "Date";
                case BINARY -> "Binary";
                case OBJECT -> "Object";
            };
        } catch(IllegalArgumentException exception) {
            return primitiveId;
        }
    }

    /** Descripción: Ejecuta la operación «multiplicity» correspondiente a la responsabilidad de MetamodelExportSupport. Entradas: multiplicity: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static String multiplicity(Multiplicity multiplicity) {
        if(multiplicity == null) {
            return "";
        }

        String upper = multiplicity.unbounded() ? "*" : String.valueOf(multiplicity.upperBound());

        if(!multiplicity.unbounded() && multiplicity.lowerBound() == multiplicity.upperBound()) {
            return String.valueOf(multiplicity.lowerBound());
        }

        return multiplicity.lowerBound() + ".." + upper;
    }

    /** Descripción: Ejecuta la operación «relationTargetName» correspondiente a la responsabilidad de MetamodelExportSupport. Entradas: context: dato de entrada necesario para la operación; relation: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static String relationTargetName(NeutralMetamodelExportContext context, MetaRelation relation) {
        MetaClass target = indexClasses(context).get(relation.targetTypeId());
        return target == null ? relation.targetTypeId() : target.name();
    }

    /** Descripción: Obtiene la información correspondiente a «findNameAttribute» dentro de MetamodelExportSupport. Entradas: metaClass: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static MetaAttribute findNameAttribute(MetaClass metaClass) {
        for(MetaAttribute attribute : metaClass.attributes()) {
            if("name".equalsIgnoreCase(attribute.name()) && attribute.type().kind() == MetaTypeReferenceKind.PRIMITIVE && MetaPrimitiveType.STRING.name().equals(attribute.type().targetId())) {
                return attribute;
            }
        }

        return null;
    }

    /** Descripción: Ejecuta la operación «pascal» correspondiente a la responsabilidad de MetamodelExportSupport. Entradas: value: valor de entrada que debe procesarse; fallback: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static String pascal(String value, String fallback) {
        return GrammarGenerationSupport.toPascalIdentifier(value, fallback);
    }

    /** Descripción: Ejecuta la operación «camel» correspondiente a la responsabilidad de MetamodelExportSupport. Entradas: value: valor de entrada que debe procesarse; fallback: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static String camel(String value, String fallback) {
        return GrammarGenerationSupport.toCamelIdentifier(value, fallback);
    }

    /** Descripción: Ejecuta la operación «xml» correspondiente a la responsabilidad de MetamodelExportSupport. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ public static String xml(String value) {
        if(value == null) {
            return "";
        }

        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;");
    }

    /** Descripción: Ejecuta la operación «plantUml» correspondiente a la responsabilidad de MetamodelExportSupport. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ public static String plantUml(String value) {
        if(value == null) {
            return "";
        }

        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /** Descripción: Ejecuta la operación «markdown» correspondiente a la responsabilidad de MetamodelExportSupport. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ public static String markdown(String value) {
        if(value == null) {
            return "";
        }

        return value.replace("|", "\\|").replace("\n", " ");
    }

    /** Descripción: Ejecuta la operación «text» correspondiente a la responsabilidad de MetamodelExportSupport. Entradas: value: valor de entrada que debe procesarse; fallback: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static String text(String value, String fallback) {
        if(value == null || value.isBlank()) {
            return fallback;
        }

        return value;
    }

    /** Descripción: Ejecuta la operación «safePackageName» correspondiente a la responsabilidad de MetamodelExportSupport. Entradas: configured: configuración que gobierna el comportamiento; metamodel: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static String safePackageName(String configured, MetaModel metamodel) {
        if(configured == null || configured.isBlank()) {
            return "";
        }

        String[] parts = configured.split("\\.");
        StringBuilder result = new StringBuilder();

        for(String part : parts) {
            String normalized = GrammarGenerationSupport.toCamelIdentifier(part, "generated");

            if(!result.isEmpty()) {
                result.append('.');
            }

            result.append(normalized.toLowerCase(java.util.Locale.ROOT));
        }

        return result.toString();
    }
}
