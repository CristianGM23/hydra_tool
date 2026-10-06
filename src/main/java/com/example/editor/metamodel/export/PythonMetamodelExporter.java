package com.example.editor.metamodel.export;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.springframework.stereotype.Component;

import com.example.editor.metamodel.core.MetaAttribute;
import com.example.editor.metamodel.core.MetaClass;
import com.example.editor.metamodel.core.MetaClassKind;
import com.example.editor.metamodel.core.MetaDataType;
import com.example.editor.metamodel.core.MetaRelation;
import com.example.editor.metamodel.core.MetaOperation;
import com.example.editor.metamodel.core.MetaParameter;
import com.example.editor.metamodel.core.MetaTypeReference;
import com.example.editor.metamodel.core.MetaRelationKind;
import com.example.editor.metamodel.core.Multiplicity;
import com.example.editor.metamodel.design.MetamodelDesignException;
import com.example.editor.metamodel.design.MetamodelDesignException.Reason;

/**
 * Genera código Python neutral con docstrings y operaciones de acceso.
 */
@Component
public class PythonMetamodelExporter implements MetamodelBinaryExporter {

    @Override
    /** Descripción: Ejecuta la operación «format» correspondiente a la responsabilidad de PythonMember. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public MetamodelExportFormat format() {
        return MetamodelExportFormat.PYTHON;
    }

    @Override
    /** Descripción: Ejecuta la operación «export» correspondiente a la responsabilidad de PythonMember. Entradas: context: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public byte[] export(NeutralMetamodelExportContext context) {
        String moduleName = MetamodelExportSupport.camel(context.neutralMetamodel().name(), "metamodel").toLowerCase(java.util.Locale.ROOT) + ".py";

        try(ByteArrayOutputStream output = new ByteArrayOutputStream(); ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            zip.putNextEntry(new ZipEntry(moduleName));
            zip.write(buildModule(context).getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.finish();
            return output.toByteArray();
        } catch(IOException exception) {
            throw new MetamodelDesignException(Reason.STORAGE_ERROR, "Could not generate Python export", exception);
        }
    }

    /** Descripción: Crea o prepara el resultado de «buildModule» dentro de PythonMember. Entradas: context: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String buildModule(NeutralMetamodelExportContext context) {
        StringBuilder result = new StringBuilder();
        result.append("\"\"\"Generated Python representation of the metamodel ")
                .append(pythonDoc(context.neutralMetamodel().name()))
                .append(".\"\"\"\n\n");
        result.append("from __future__ import annotations\n");
        result.append("from abc import ABC, abstractmethod\n");
        result.append("from dataclasses import dataclass, field\n");
        result.append("from enum import Enum\n");
        result.append("from typing import Optional\n\n");

        context.neutralMetamodel().enums().forEach(item -> {
            result.append("class ").append(MetamodelExportSupport.pascal(item.name(), "GeneratedEnum")).append("(Enum):\n");
            result.append("    \"\"\"Generated enumeration for ").append(pythonDoc(item.name())).append(".\"\"\"\n");

            if(item.literals().isEmpty()) {
                result.append("    pass\n");
            } else {
                item.literals().forEach(literal -> result.append("    ").append(literal.name()).append(" = ").append(literal.value()).append("\n"));
            }

            result.append("\n");
        });

        for(MetaDataType dataType : context.neutralMetamodel().dataTypes()) {
            appendDataType(result, dataType);
        }

        for(MetaClass metaClass : orderedClasses(context)) {
            appendClass(result, context, metaClass);
        }

        return result.toString();
    }

    /** Descripción: Ejecuta la operación «appendDataType» correspondiente a la responsabilidad de PythonMember. Entradas: result: dato de entrada necesario para la operación; dataType: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void appendDataType(StringBuilder result, MetaDataType dataType) {
        String name = MetamodelExportSupport.pascal(dataType.name(), "GeneratedType");
        result.append("@dataclass(eq=False, repr=False)\n");
        result.append("class ").append(name).append(":\n");
        result.append("    \"\"\"Generated value object for the metamodel data type ").append(pythonDoc(dataType.name())).append(".\n\n");
        result.append("    Attributes:\n        value: Serialized value represented by this data type.\n    \"\"\"\n\n");
        result.append("    value: Optional[str] = None\n\n");
        appendPythonGetter(result, "value", "Optional[str]", "serialized value");
        appendPythonSetter(result, "value", "Optional[str]", "serialized value");
        appendPythonStringAndEquals(result, name, List.of("value"), false);
    }

    /** Descripción: Ejecuta la operación «appendClass» correspondiente a la responsabilidad de PythonMember. Entradas: result: dato de entrada necesario para la operación; context: dato de entrada necesario para la operación; metaClass: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void appendClass(StringBuilder result, NeutralMetamodelExportContext context, MetaClass metaClass) {
        String name = MetamodelExportSupport.pascal(metaClass.name(), "GeneratedClass");
        List<PythonMember> members = members(context, metaClass);
        List<String> bases = baseClassNames(context, metaClass);

        if(metaClass.kind() == MetaClassKind.INTERFACE || metaClass.kind() == MetaClassKind.ABSTRACT_CLASS) {
            if(!bases.contains("ABC")) {
                bases.add("ABC");
            }
        }

        if(metaClass.kind() != MetaClassKind.INTERFACE) {
            result.append("@dataclass(kw_only=True, eq=False, repr=False)\n");
        }

        result.append("class ").append(name);

        if(!bases.isEmpty()) {
            result.append("(").append(String.join(", ", bases)).append(")");
        }

        result.append(":\n");
        appendClassDocstring(result, metaClass, members);

        if(metaClass.kind() == MetaClassKind.INTERFACE) {
            appendInterfaceMembers(result, members);
            appendOperationMethods(result, context, metaClass, true);
            result.append("\n");
            return;
        }

        if(members.isEmpty()) {
            result.append("\n    pass\n");
        } else {
            result.append("\n");

            for(PythonMember member : members) {
                result.append("    ").append(member.name()).append(": ").append(member.type()).append(member.defaultValue()).append("\n");
            }
        }

        for(PythonMember member : members) {
            appendPythonGetter(result, member.name(), member.type(), member.documentation());
            appendPythonSetter(result, member.name(), member.type(), member.documentation());
        }

        appendOperationMethods(result, context, metaClass, metaClass.kind() == MetaClassKind.ABSTRACT_CLASS);
        appendPythonStringAndEquals(result, name, members.stream().map(PythonMember::name).toList(), hasClassSuperType(context, metaClass));
    }

    /** Descripción: Ejecuta la operación «appendClassDocstring» correspondiente a la responsabilidad de PythonMember. Entradas: result: dato de entrada necesario para la operación; metaClass: dato de entrada necesario para la operación; members: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void appendClassDocstring(StringBuilder result, MetaClass metaClass, List<PythonMember> members) {
        result.append("    \"\"\"Generated representation of the metamodel ")
                .append(metaClass.kind() == MetaClassKind.INTERFACE ? "interface" : "class")
                .append(" ").append(pythonDoc(metaClass.name())).append(".");

        if(!members.isEmpty()) {
            result.append("\n\n    Attributes:");

            for(PythonMember member : members) {
                result.append("\n        ").append(member.name()).append(": ").append(pythonDoc(member.documentation()));
            }
        }

        result.append("\n    \"\"\"");
    }

    /** Descripción: Ejecuta la operación «appendInterfaceMembers» correspondiente a la responsabilidad de PythonMember. Entradas: result: dato de entrada necesario para la operación; members: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void appendInterfaceMembers(StringBuilder result, List<PythonMember> members) {
        if(members.isEmpty()) {
            result.append("\n\n    pass\n");
            return;
        }

        for(PythonMember member : members) {
            result.append("\n\n    @abstractmethod\n");
            result.append("    def get_").append(member.name()).append("(self) -> ").append(member.type()).append(":\n");
            result.append("        \"\"\"Return ").append(pythonDoc(member.documentation())).append("\"\"\"\n");
            result.append("        raise NotImplementedError\n\n");
            result.append("    @abstractmethod\n");
            result.append("    def set_").append(member.name()).append("(self, value: ").append(member.type()).append(") -> None:\n");
            result.append("        \"\"\"Update ").append(pythonDoc(member.documentation())).append("\"\"\"\n");
            result.append("        raise NotImplementedError\n");
        }
    }

    /** Descripción: Ejecuta la operación «appendPythonGetter» correspondiente a la responsabilidad de PythonMember. Entradas: result: dato de entrada necesario para la operación; name: dato de entrada necesario para la operación; type: dato de entrada necesario para la operación; documentation: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void appendPythonGetter(StringBuilder result, String name, String type, String documentation) {
        result.append("\n    def get_").append(name).append("(self) -> ").append(type).append(":\n");
        result.append("        \"\"\"Return ").append(pythonDoc(documentation)).append("\"\"\"\n");
        result.append("        return self.").append(name).append("\n");
    }

    /** Descripción: Ejecuta la operación «appendPythonSetter» correspondiente a la responsabilidad de PythonMember. Entradas: result: dato de entrada necesario para la operación; name: dato de entrada necesario para la operación; type: dato de entrada necesario para la operación; documentation: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void appendPythonSetter(StringBuilder result, String name, String type, String documentation) {
        result.append("\n    def set_").append(name).append("(self, value: ").append(type).append(") -> None:\n");
        result.append("        \"\"\"Update ").append(pythonDoc(documentation)).append("\"\"\"\n");
        result.append("        self.").append(name).append(" = value\n");
    }

    /** Descripción: Ejecuta la operación «appendOperationMethods» correspondiente a la responsabilidad de PythonMember. Entradas: result: dato de entrada necesario para la operación; context: dato de entrada necesario para la operación; owner: dato de entrada necesario para la operación; abstractDeclaration: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void appendOperationMethods(StringBuilder result, NeutralMetamodelExportContext context, MetaClass owner, boolean abstractDeclaration) {
        for(MetaOperation operation : operationsForGeneration(context, owner)) {
            result.append("\n");
            if(abstractDeclaration) result.append("    @abstractmethod\n");
            result.append("    def ").append(pythonName(operation.name(), "operation")).append("(self");
            for(MetaParameter parameter : operation.parameters()) {
                result.append(", ").append(pythonName(parameter.name(), "value")).append(": ").append(pythonOperationType(context, parameter.type(), parameter.multiplicity()));
            }
            String returnType = operation.returnType() == null ? "None" : pythonOperationType(context, operation.returnType(), operation.returnMultiplicity());
            result.append(") -> ").append(returnType).append(":\n");
            result.append("        \"\"\"Generated metamodel operation ").append(pythonDoc(operation.name())).append(".\"\"\"\n");
            result.append("        raise NotImplementedError(\"Operation ").append(pythonDoc(operation.name())).append(" has no generated implementation\")\n");
        }
    }

    /** Descripción: Ejecuta la operación «operationsForGeneration» correspondiente a la responsabilidad de PythonMember. Entradas: context: dato de entrada necesario para la operación; owner: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<MetaOperation> operationsForGeneration(NeutralMetamodelExportContext context, MetaClass owner) {
        Map<String, MetaOperation> operations = new LinkedHashMap<>();
        for(MetaOperation operation : owner.operations()) operations.put(operationSignature(operation), operation);
        if(owner.kind() == MetaClassKind.CLASS) {
            for(MetaClass parent : allSuperTypes(context, owner)) {
                for(MetaOperation operation : parent.operations()) operations.putIfAbsent(operationSignature(operation), operation);
            }
        }
        return List.copyOf(operations.values());
    }

    /** Descripción: Ejecuta la operación «operationSignature» correspondiente a la responsabilidad de PythonMember. Entradas: operation: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String operationSignature(MetaOperation operation) {
        StringBuilder result = new StringBuilder(operation.name()).append('(');
        for(MetaParameter parameter : operation.parameters()) result.append(parameter.type().kind()).append(':').append(parameter.type().targetId()).append(':').append(parameter.type().externalName()).append(';');
        return result.append(')').toString();
    }

    /** Descripción: Ejecuta la operación «interfaceContracts» correspondiente a la responsabilidad de PythonMember. Entradas: context: dato de entrada necesario para la operación; owner: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<MetaClass> interfaceContracts(NeutralMetamodelExportContext context, MetaClass owner) {
        return allSuperTypes(context, owner).stream().filter(item -> item.kind() == MetaClassKind.INTERFACE).toList();
    }

    /** Descripción: Ejecuta la operación «allSuperTypes» correspondiente a la responsabilidad de PythonMember. Entradas: context: dato de entrada necesario para la operación; owner: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<MetaClass> allSuperTypes(NeutralMetamodelExportContext context, MetaClass owner) {
        Map<String, MetaClass> classes = new LinkedHashMap<>();
        for(MetaClass item : context.neutralMetamodel().classes()) classes.put(item.id(), item);
        List<MetaClass> result = new ArrayList<>();
        collectSuperTypes(context, owner.id(), classes, new LinkedHashSet<>(), result);
        return result;
    }

    /** Descripción: Ejecuta la operación «collectSuperTypes» correspondiente a la responsabilidad de PythonMember. Entradas: context: dato de entrada necesario para la operación; classId: identificador que delimita el recurso objetivo; classes: dato de entrada necesario para la operación; visited: dato de entrada necesario para la operación; result: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void collectSuperTypes(NeutralMetamodelExportContext context, String classId, Map<String, MetaClass> classes, Set<String> visited, List<MetaClass> result) {
        for(MetaRelation relation : MetamodelExportSupport.superTypeRelationsFrom(context, classId)) {
            if(!visited.add(relation.targetTypeId())) continue;
            MetaClass parent = classes.get(relation.targetTypeId());
            if(parent == null) continue;
            result.add(parent);
            collectSuperTypes(context, parent.id(), classes, visited, result);
        }
    }

    /** Descripción: Ejecuta la operación «pythonName» correspondiente a la responsabilidad de PythonMember. Entradas: value: valor de entrada que debe procesarse; fallback: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String pythonName(String value, String fallback) {
        String base = MetamodelExportSupport.camel(value, fallback);
        String snake = base.replaceAll("([a-z0-9])([A-Z])", "$1_$2").replaceAll("[^A-Za-z0-9_]+", "_").toLowerCase(java.util.Locale.ROOT);
        return snake.isBlank() ? fallback : snake;
    }

    /** Descripción: Ejecuta la operación «pythonOperationType» correspondiente a la responsabilidad de PythonMember. Entradas: context: dato de entrada necesario para la operación; type: dato de entrada necesario para la operación; multiplicity: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String pythonOperationType(NeutralMetamodelExportContext context, MetaTypeReference type, Multiplicity multiplicity) {
        String base = switch(type.kind()) {
            case PRIMITIVE -> switch(type.targetId()) {
                case "STRING", "CHARACTER" -> "str";
                case "BOOLEAN" -> "bool";
                case "BYTE", "SHORT", "INTEGER", "LONG", "BIG_INTEGER" -> "int";
                case "FLOAT", "DOUBLE", "DECIMAL" -> "float";
                case "BINARY" -> "bytes";
                default -> "object";
            };
            default -> MetamodelExportSupport.pascal(MetamodelExportSupport.resolveTypeName(context, type), "object");
        };
        return wrap(base, multiplicity);
    }

    /** Descripción: Ejecuta la operación «appendPythonStringAndEquals» correspondiente a la responsabilidad de PythonMember. Entradas: result: dato de entrada necesario para la operación; className: dato de entrada necesario para la operación; memberNames: dato de entrada necesario para la operación; hasClassSuperType: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void appendPythonStringAndEquals(StringBuilder result, String className, List<String> memberNames, boolean hasClassSuperType) {
        result.append("\n    def __str__(self) -> str:\n");
        result.append("        \"\"\"Return a readable representation of all generated elements.\"\"\"\n");

        if(memberNames.isEmpty() && !hasClassSuperType) {
            result.append("        return \"").append(className).append("()\"\n");
        } else {
            List<String> representations = new ArrayList<>();

            for(String memberName : memberNames) {
                representations.add(memberName + "={self." + memberName + "!r}");
            }

            if(hasClassSuperType) {
                representations.add("inherited={super().__str__()}");
            }

            result.append("        return f\"").append(className).append("(").append(String.join(", ", representations)).append(")\"\n");
        }

        result.append("\n    def __eq__(self, other: object) -> bool:\n");
        result.append("        \"\"\"Compare all generated elements with another instance.\"\"\"\n");
        result.append("        if type(self) is not type(other):\n            return False\n");

        List<String> comparisons = new ArrayList<>();

        if(hasClassSuperType) {
            comparisons.add("super().__eq__(other)");
        }

        for(String memberName : memberNames) {
            comparisons.add("self." + memberName + " == other." + memberName);
        }

        result.append("        return ").append(comparisons.isEmpty() ? "True" : String.join(" and ", comparisons)).append("\n\n");
    }

    /** Descripción: Ejecuta la operación «members» correspondiente a la responsabilidad de PythonMember. Entradas: context: dato de entrada necesario para la operación; metaClass: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<PythonMember> members(NeutralMetamodelExportContext context, MetaClass metaClass) {
        Map<String, PythonMember> result = new LinkedHashMap<>();
        appendDeclaredMembers(context, metaClass, result);
        if(metaClass.kind() == MetaClassKind.CLASS) {
            for(MetaClass contract : interfaceContracts(context, metaClass)) {
                appendDeclaredMembers(context, contract, result);
            }
        }
        return List.copyOf(result.values());
    }

    /** Descripción: Ejecuta la operación «appendDeclaredMembers» correspondiente a la responsabilidad de PythonMember. Entradas: context: dato de entrada necesario para la operación; metaClass: dato de entrada necesario para la operación; result: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void appendDeclaredMembers(NeutralMetamodelExportContext context, MetaClass metaClass, Map<String, PythonMember> result) {
        for(MetaAttribute attribute : metaClass.attributes()) {
            String name = pythonName(attribute.name(), "value");
            String type = pythonType(context, attribute);
            String documentation = "metamodel attribute " + attribute.name() + " of type " + MetamodelExportSupport.resolveTypeName(context, attribute.type()) + " with multiplicity " + printableMultiplicity(attribute.multiplicity()) + ".";
            result.putIfAbsent(name, new PythonMember(name, type, defaultValue(attribute.multiplicity()), documentation));
        }

        for(MetaRelation relation : MetamodelExportSupport.structuralRelationsFrom(context, metaClass.id())) {
            String target = MetamodelExportSupport.pascal(MetamodelExportSupport.relationTargetName(context, relation), "object");
            String name = pythonName(MetamodelExportSupport.text(relation.name(), "reference"), "reference");
            String documentation = "structural relation " + MetamodelExportSupport.text(relation.name(), "reference") + " targeting " + target + " with multiplicity " + printableMultiplicity(relation.targetMultiplicity()) + ".";
            result.putIfAbsent(name, new PythonMember(name, wrap(target, relation.targetMultiplicity()), defaultValue(relation.targetMultiplicity()), documentation));
        }
    }

    /** Descripción: Ejecuta la operación «orderedClasses» correspondiente a la responsabilidad de PythonMember. Entradas: context: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<MetaClass> orderedClasses(NeutralMetamodelExportContext context) {
        Map<String, MetaClass> classes = new LinkedHashMap<>();

        for(MetaClass metaClass : context.neutralMetamodel().classes()) {
            classes.put(metaClass.id(), metaClass);
        }

        List<MetaClass> result = new ArrayList<>();
        Set<String> visited = new LinkedHashSet<>();
        Set<String> visiting = new LinkedHashSet<>();

        for(MetaClass metaClass : classes.values()) {
            visitClass(context, classes, metaClass, visited, visiting, result);
        }

        return result;
    }

    /** Descripción: Ejecuta la operación «visitClass» correspondiente a la responsabilidad de PythonMember. Entradas: context: dato de entrada necesario para la operación; classes: dato de entrada necesario para la operación; metaClass: dato de entrada necesario para la operación; visited: dato de entrada necesario para la operación; visiting: dato de entrada necesario para la operación; result: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void visitClass(NeutralMetamodelExportContext context, Map<String, MetaClass> classes, MetaClass metaClass, Set<String> visited, Set<String> visiting, List<MetaClass> result) {
        if(visited.contains(metaClass.id()) || !visiting.add(metaClass.id())) {
            return;
        }

        for(MetaRelation relation : MetamodelExportSupport.superTypeRelationsFrom(context, metaClass.id())) {
            MetaClass parent = classes.get(relation.targetTypeId());

            if(parent != null) {
                visitClass(context, classes, parent, visited, visiting, result);
            }
        }

        visiting.remove(metaClass.id());
        visited.add(metaClass.id());
        result.add(metaClass);
    }

    /** Descripción: Ejecuta la operación «baseClassNames» correspondiente a la responsabilidad de PythonMember. Entradas: context: dato de entrada necesario para la operación; metaClass: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<String> baseClassNames(NeutralMetamodelExportContext context, MetaClass metaClass) {
        List<String> result = new ArrayList<>();

        for(MetaRelation relation : MetamodelExportSupport.superTypeRelationsFrom(context, metaClass.id())) {
            String target = MetamodelExportSupport.pascal(MetamodelExportSupport.relationTargetName(context, relation), "GeneratedClass");

            if(!result.contains(target)) {
                result.add(target);
            }
        }

        return result;
    }

    /** Descripción: Evalúa la condición «hasClassSuperType» sin modificar el estado externo. Entradas: context: dato de entrada necesario para la operación; metaClass: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean hasClassSuperType(NeutralMetamodelExportContext context, MetaClass metaClass) {
        return MetamodelExportSupport.superTypeRelationsFrom(context, metaClass.id()).stream().anyMatch(relation -> relation.kind() == MetaRelationKind.INHERITANCE);
    }

    /** Descripción: Ejecuta la operación «pythonType» correspondiente a la responsabilidad de PythonMember. Entradas: context: dato de entrada necesario para la operación; attribute: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String pythonType(NeutralMetamodelExportContext context, MetaAttribute attribute) {
        String base = switch(attribute.type().kind()) {
            case PRIMITIVE -> switch(attribute.type().targetId()) {
                case "STRING", "CHARACTER" -> "str";
                case "BOOLEAN" -> "bool";
                case "BYTE", "SHORT", "INTEGER", "LONG", "BIG_INTEGER" -> "int";
                case "FLOAT", "DOUBLE", "DECIMAL" -> "float";
                case "BINARY" -> "bytes";
                default -> "object";
            };
            default -> MetamodelExportSupport.pascal(MetamodelExportSupport.resolveTypeName(context, attribute.type()), "object");
        };

        return wrap(base, attribute.multiplicity());
    }

    /** Descripción: Ejecuta la operación «wrap» correspondiente a la responsabilidad de PythonMember. Entradas: base: dato de entrada necesario para la operación; multiplicity: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String wrap(String base, Multiplicity multiplicity) {
        if(isMany(multiplicity)) {
            return "list[" + base + "]";
        }

        if(multiplicity != null && multiplicity.lowerBound() == 0) {
            return "Optional[" + base + "]";
        }

        return base;
    }

    /** Descripción: Ejecuta la operación «defaultValue» correspondiente a la responsabilidad de PythonMember. Entradas: multiplicity: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String defaultValue(Multiplicity multiplicity) {
        if(isMany(multiplicity)) {
            return " = field(default_factory=list)";
        }

        if(multiplicity != null && multiplicity.lowerBound() == 0) {
            return " = None";
        }

        return "";
    }

    /** Descripción: Evalúa la condición «isMany» sin modificar el estado externo. Entradas: multiplicity: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean isMany(Multiplicity multiplicity) {
        return multiplicity != null && (multiplicity.unbounded() || multiplicity.upperBound() > 1);
    }

    /** Descripción: Ejecuta la operación «printableMultiplicity» correspondiente a la responsabilidad de PythonMember. Entradas: multiplicity: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String printableMultiplicity(Multiplicity multiplicity) {
        String value = MetamodelExportSupport.multiplicity(multiplicity);
        return value.isBlank() ? "unspecified" : value;
    }

    /** Descripción: Ejecuta la operación «pythonDoc» correspondiente a la responsabilidad de PythonMember. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String pythonDoc(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"\"\"", "\\\"\\\"\\\"").replace("\n", " ");
    }

    private record PythonMember(String name, String type, String defaultValue, String documentation) {
    }
}
