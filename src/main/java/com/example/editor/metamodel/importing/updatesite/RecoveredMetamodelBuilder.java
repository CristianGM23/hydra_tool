package com.example.editor.metamodel.importing.updatesite;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.example.editor.metamodel.core.MetaAttribute;
import com.example.editor.metamodel.core.MetaClass;
import com.example.editor.metamodel.core.MetaClassKind;
import com.example.editor.metamodel.core.MetaEnum;
import com.example.editor.metamodel.core.MetaEnumLiteral;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.core.MetaOperation;
import com.example.editor.metamodel.core.MetaOperationBehaviorKind;
import com.example.editor.metamodel.core.MetaParameter;
import com.example.editor.metamodel.core.MetaPrimitiveType;
import com.example.editor.metamodel.core.MetaRelation;
import com.example.editor.metamodel.core.MetaRelationKind;
import com.example.editor.metamodel.core.MetaTypeReference;
import com.example.editor.metamodel.core.Multiplicity;

/** Constructor determinista para reconstrucciones parciales o inferidas. */
final class RecoveredMetamodelBuilder {
    private final Map<String, MutableClass> classes = new LinkedHashMap<>();
    private final Map<String, MetaEnum> enums = new LinkedHashMap<>();
    private final Map<String, MetaRelation> relations = new LinkedHashMap<>();

    /** Descripción: Ejecuta la operación «addClass» correspondiente a la responsabilidad de MutableClass. Entradas: name: dato de entrada necesario para la operación; kind: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ MutableClass addClass(String name, MetaClassKind kind) {
        String safeName = visibleName(name, "RecoveredClass");
        return classes.computeIfAbsent(safeName, ignored -> new MutableClass(safeName, kind));
    }

    /** Descripción: Ejecuta la operación «addAttribute» correspondiente a la responsabilidad de MutableClass. Entradas: className: dato de entrada necesario para la operación; name: dato de entrada necesario para la operación; externalType: dato de entrada necesario para la operación; multiplicity: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ void addAttribute(String className, String name, String externalType, Multiplicity multiplicity) {
        MutableClass owner = addClass(className, MetaClassKind.CLASS);
        owner.attributes.putIfAbsent(name, new MetaAttribute(id("attribute", owner.name, name), visibleName(name, "value"),
                type(externalType), multiplicity == null ? Multiplicity.optional() : multiplicity,
                true, true, "", Map.of()));
    }

    /** Descripción: Ejecuta la operación «addRelation» correspondiente a la responsabilidad de MutableClass. Entradas: className: dato de entrada necesario para la operación; name: dato de entrada necesario para la operación; targetClassName: dato de entrada necesario para la operación; multiplicity: dato de entrada necesario para la operación; containment: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ void addRelation(String className, String name, String targetClassName, Multiplicity multiplicity, boolean containment) {
        MutableClass source = addClass(className, MetaClassKind.CLASS);
        MutableClass target = addClass(targetClassName, MetaClassKind.CLASS);
        String relationId = id("relation", source.name, name);
        relations.putIfAbsent(relationId, new MetaRelation(relationId, visibleName(name, target.name),
                source.id(), target.id(), containment ? MetaRelationKind.CONTAINMENT : MetaRelationKind.REFERENCE,
                Multiplicity.one(), multiplicity == null ? Multiplicity.optional() : multiplicity,
                "", true, true, Map.of()));
    }

    /** Descripción: Ejecuta la operación «addInheritance» correspondiente a la responsabilidad de MutableClass. Entradas: className: dato de entrada necesario para la operación; superClassName: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ void addInheritance(String className, String superClassName) {
        MutableClass source = addClass(className, MetaClassKind.CLASS);
        MutableClass target = addClass(superClassName, MetaClassKind.CLASS);
        String relationId = id("inheritance", source.name, target.name);
        relations.putIfAbsent(relationId, new MetaRelation(relationId, "extends", source.id(), target.id(),
                MetaRelationKind.INHERITANCE, Multiplicity.one(), Multiplicity.one(), "", true, true, Map.of()));
    }

    /** Descripción: Ejecuta la operación «addOperation» correspondiente a la responsabilidad de MutableClass. Entradas: className: dato de entrada necesario para la operación; operationName: dato de entrada necesario para la operación; returnType: dato de entrada necesario para la operación; parameters: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ void addOperation(String className, String operationName, String returnType, List<ParameterSpec> parameters) {
        MutableClass owner = addClass(className, MetaClassKind.CLASS);
        List<MetaParameter> converted = parameters == null ? List.of() : parameters.stream().map(parameter ->
                new MetaParameter(id("parameter", owner.name, operationName, parameter.name()), parameter.name(),
                        type(parameter.type()), parameter.many() ? Multiplicity.many() : Multiplicity.one(),
                        true, true, Map.of())).toList();
        MetaTypeReference returnReference = isVoid(returnType) ? null : type(returnType);
        owner.operations.putIfAbsent(operationName, new MetaOperation(id("operation", owner.name, operationName),
                operationName, returnReference, returnReference == null ? null : Multiplicity.one(), true, true,
                converted, List.of(), MetaOperationBehaviorKind.UNSPECIFIED, Map.of()));
    }

    /** Descripción: Ejecuta la operación «addEnum» correspondiente a la responsabilidad de MutableClass. Entradas: name: dato de entrada necesario para la operación; literals: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ void addEnum(String name, List<String> literals) {
        String enumName = visibleName(name, "RecoveredEnum");
        List<MetaEnumLiteral> values = new ArrayList<>();
        Set<String> unique = new LinkedHashSet<>(literals == null ? List.of() : literals);
        int index = 0;
        for(String literal : unique) {
            values.add(new MetaEnumLiteral(id("literal", enumName, literal), visibleName(literal, "VALUE"), index++, Map.of()));
        }
        enums.putIfAbsent(enumName, new MetaEnum(id("enum", enumName), enumName, values, Map.of()));
    }

    /** Descripción: Crea o prepara el resultado de «build» dentro de MutableClass. Entradas: requestedId: identificador que delimita el recurso objetivo; name: dato de entrada necesario para la operación; annotations: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ MetaModel build(String requestedId, String name, Map<String, String> annotations) {
        String modelId = requestedId == null || requestedId.isBlank() ? id("recovered", name) : documentId(requestedId);
        List<MetaClass> immutableClasses = classes.values().stream().map(MutableClass::freeze)
                .sorted(Comparator.comparing(MetaClass::id)).toList();
        return new MetaModel(MetaModel.CURRENT_SCHEMA_VERSION, modelId, visibleName(name, modelId), immutableClasses,
                enums.values().stream().sorted(Comparator.comparing(MetaEnum::id)).toList(), List.of(),
                relations.values().stream().sorted(Comparator.comparing(MetaRelation::id)).toList(), annotations);
    }

    /** Descripción: Ejecuta la operación «type» correspondiente a la responsabilidad de MutableClass. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private MetaTypeReference type(String value) {
        String type = value == null ? "" : value.trim();
        String lower = type.toLowerCase(Locale.ROOT).replace("java.lang.", "");
        if(lower.endsWith("[]")) lower = lower.substring(0, lower.length() - 2);
        return switch(lower) {
            case "string", "charsequence", "char", "character" -> MetaTypeReference.primitive(MetaPrimitiveType.STRING);
            case "boolean", "bool" -> MetaTypeReference.primitive(MetaPrimitiveType.BOOLEAN);
            case "byte" -> MetaTypeReference.primitive(MetaPrimitiveType.BYTE);
            case "short" -> MetaTypeReference.primitive(MetaPrimitiveType.SHORT);
            case "int", "integer" -> MetaTypeReference.primitive(MetaPrimitiveType.INTEGER);
            case "long" -> MetaTypeReference.primitive(MetaPrimitiveType.LONG);
            case "float" -> MetaTypeReference.primitive(MetaPrimitiveType.FLOAT);
            case "double", "number" -> MetaTypeReference.primitive(MetaPrimitiveType.DOUBLE);
            case "decimal", "bigdecimal" -> MetaTypeReference.primitive(MetaPrimitiveType.DECIMAL);
            case "biginteger" -> MetaTypeReference.primitive(MetaPrimitiveType.BIG_INTEGER);
            case "date", "localdate", "instant", "datetime" -> MetaTypeReference.primitive(MetaPrimitiveType.DATE);
            case "bytes", "bytebuffer", "binary" -> MetaTypeReference.primitive(MetaPrimitiveType.BINARY);
            case "object", "any", "json" -> MetaTypeReference.primitive(MetaPrimitiveType.OBJECT);
            default -> MetaTypeReference.external(type.isBlank() ? "Object" : type);
        };
    }

    /** Descripción: Evalúa la condición «isVoid» sin modificar el estado externo. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean isVoid(String value) {
        return value == null || value.isBlank() || value.equals("V") || value.equalsIgnoreCase("void");
    }

    /** Descripción: Ejecuta la operación «visibleName» correspondiente a la responsabilidad de MutableClass. Entradas: value: valor de entrada que debe procesarse; fallback: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String visibleName(String value, String fallback) {
        String text = value == null ? "" : value.trim();
        if(text.contains("/")) text = text.substring(text.lastIndexOf('/') + 1);
        if(text.contains(".")) text = text.substring(text.lastIndexOf('.') + 1);
        text = text.replaceAll("[^A-Za-z0-9_]", "_");
        if(text.isBlank()) text = fallback;
        if(Character.isDigit(text.charAt(0))) text = "_" + text;
        return text;
    }

    /** Descripción: Ejecuta la operación «id» correspondiente a la responsabilidad de MutableClass. Entradas: parts: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String id(String... parts) {
        return java.util.Arrays.stream(parts).map(part -> visibleName(part, "element")).collect(java.util.stream.Collectors.joining(":"));
    }

    /** Descripción: Ejecuta la operación «documentId» correspondiente a la responsabilidad de MutableClass. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String documentId(String value) {
        String result = value.replaceAll("[^A-Za-z0-9._-]", "-");
        if(result.isBlank()) return "recovered-metamodel";
        if(result.length() > 120) result = result.substring(0, 120);
        return result;
    }

    record ParameterSpec(String name, String type, boolean many) { }

    final class MutableClass {
        private final String name;
        private MetaClassKind kind;
        private final Map<String, MetaAttribute> attributes = new LinkedHashMap<>();
        private final Map<String, MetaOperation> operations = new LinkedHashMap<>();

        /** Descripción: Inicializa una instancia de MutableClass con sus dependencias y datos de entrada. Entradas: name: dato de entrada necesario para la operación; kind: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ private MutableClass(String name, MetaClassKind kind) {
            this.name = name;
            this.kind = kind == null ? MetaClassKind.CLASS : kind;
        }

        /** Descripción: Ejecuta la operación «id» correspondiente a la responsabilidad de MutableClass. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ String id() { return RecoveredMetamodelBuilder.this.id("class", name); }

        /** Descripción: Actualiza el estado gestionado por MutableClass mediante la operación «mergeKind». Entradas: next: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ void mergeKind(MetaClassKind next) {
            if(next == MetaClassKind.INTERFACE || next == MetaClassKind.ABSTRACT_CLASS && kind == MetaClassKind.CLASS) kind = next;
        }

        /** Descripción: Ejecuta la operación «freeze» correspondiente a la responsabilidad de MutableClass. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ MetaClass freeze() {
            return new MetaClass(id(), name, kind,
                    attributes.values().stream().sorted(Comparator.comparing(MetaAttribute::id)).toList(),
                    operations.values().stream().sorted(Comparator.comparing(MetaOperation::id)).toList(), Map.of());
        }
    }
}
