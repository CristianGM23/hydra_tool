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
import com.example.editor.metamodel.core.MetaEnum;
import com.example.editor.metamodel.core.MetaRelation;
import com.example.editor.metamodel.core.MetaOperation;
import com.example.editor.metamodel.core.MetaParameter;
import com.example.editor.metamodel.core.MetaTypeReference;
import com.example.editor.metamodel.core.MetaRelationKind;
import com.example.editor.metamodel.core.Multiplicity;
import com.example.editor.metamodel.design.MetamodelDesignException;
import com.example.editor.metamodel.design.MetamodelDesignException.Reason;

/**
 * Genera código Java neutral documentado y preparado para su uso directo.
 */
@Component
public class JavaMetamodelExporter implements MetamodelBinaryExporter {

    @Override
    /** Descripción: Ejecuta la operación «format» correspondiente a la responsabilidad de JavaMetamodelExporter. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public MetamodelExportFormat format() {
        return MetamodelExportFormat.JAVA;
    }

    @Override
    /** Descripción: Ejecuta la operación «export» correspondiente a la responsabilidad de JavaMetamodelExporter. Entradas: context: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public byte[] export(NeutralMetamodelExportContext context) {
        String packageName = MetamodelExportSupport.safePackageName(context.options().codePackageName(), context.neutralMetamodel());

        try(ByteArrayOutputStream output = new ByteArrayOutputStream(); ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            String directory = packageName.isBlank() ? "" : packageName.replace('.', '/') + "/";

            for(MetaEnum enumDesign : context.neutralMetamodel().enums()) {
                add(zip, directory + className(enumDesign.name()), buildEnum(packageName, enumDesign));
            }

            for(MetaDataType dataType : context.neutralMetamodel().dataTypes()) {
                add(zip, directory + className(dataType.name()), buildDataType(packageName, dataType));
            }

            for(MetaClass metaClass : context.neutralMetamodel().classes()) {
                add(zip, directory + className(metaClass.name()), buildClass(context, packageName, metaClass));
            }

            zip.finish();
            return output.toByteArray();
        } catch(IOException exception) {
            throw new MetamodelDesignException(Reason.STORAGE_ERROR, "Could not generate Java export", exception);
        }
    }

    /** Descripción: Crea o prepara el resultado de «buildEnum» dentro de JavaMetamodelExporter. Entradas: packageName: dato de entrada necesario para la operación; enumDesign: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String buildEnum(String packageName, MetaEnum enumDesign) {
        String name = MetamodelExportSupport.pascal(enumDesign.name(), "GeneratedEnum");
        StringBuilder result = new StringBuilder();
        appendPackage(result, packageName);
        appendJavadoc(result, 0, "Generated enumeration for the metamodel classifier \"" + enumDesign.name() + "\".");
        result.append("public enum ").append(name).append(" {\n");

        if(enumDesign.literals().isEmpty()) {
            result.append("    ;\n");
        }

        for(int index = 0; index < enumDesign.literals().size(); index += 1) {
            var literal = enumDesign.literals().get(index);
            appendJavadoc(result, 1, "Literal \"" + literal.name() + "\" with numeric value " + literal.value() + ".");
            result.append("    ").append(literal.name()).append("(").append(literal.value()).append(index < enumDesign.literals().size() - 1 ? ")," : ");").append("\n");
        }

        result.append("\n");
        appendJavadoc(result, 1, "Numeric value associated with the enumeration literal.");
        result.append("    private final int value;\n\n");
        appendJavadoc(result, 1, "Creates an enumeration literal.\n\n@param value numeric value associated with the literal");
        result.append("    ").append(name).append("(int value) {\n        this.value = value;\n    }\n\n");
        appendJavadoc(result, 1, "Returns the numeric value associated with this literal.\n\n@return numeric value of the literal");
        result.append("    public int getValue() {\n        return value;\n    }\n");
        result.append("}\n");
        return result.toString();
    }

    /** Descripción: Crea o prepara el resultado de «buildDataType» dentro de JavaMetamodelExporter. Entradas: packageName: dato de entrada necesario para la operación; dataType: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String buildDataType(String packageName, MetaDataType dataType) {
        String name = MetamodelExportSupport.pascal(dataType.name(), "GeneratedType");
        StringBuilder result = new StringBuilder();
        appendPackage(result, packageName);
        result.append("import java.util.Objects;\n\n");
        appendJavadoc(result, 0, "Generated value object for the metamodel data type \"" + dataType.name() + "\".");
        result.append("public class ").append(name).append(" {\n\n");
        appendJavadoc(result, 1, "Serialized value represented by this data type.");
        result.append("    private String value;\n\n");
        appendJavadoc(result, 1, "Creates an empty value object.");
        result.append("    public ").append(name).append("() {\n    }\n\n");
        appendJavadoc(result, 1, "Creates a value object.\n\n@param value serialized value");
        result.append("    public ").append(name).append("(String value) {\n        this.value = value;\n    }\n\n");
        appendJavadoc(result, 1, "Returns the serialized value.\n\n@return serialized value");
        result.append("    public String getValue() {\n        return value;\n    }\n\n");
        appendJavadoc(result, 1, "Updates the serialized value.\n\n@param value new serialized value");
        result.append("    public void setValue(String value) {\n        this.value = value;\n    }\n\n");
        appendJavadoc(result, 1, "Returns a readable representation of this value object.\n\n@return readable representation");
        result.append("    @Override\n    public String toString() {\n        return \"").append(name).append("{value=\" + value + '}';\n    }\n\n");
        appendJavadoc(result, 1, "Compares this value object with another object.\n\n@param object object to compare\n@return true when both values are equal");
        result.append("    @Override\n    public boolean equals(Object object) {\n        if(this == object) {\n            return true;\n        }\n\n        if(object == null || getClass() != object.getClass()) {\n            return false;\n        }\n\n        ").append(name).append(" other = (").append(name).append(") object;\n        return Objects.equals(value, other.value);\n    }\n\n");
        appendJavadoc(result, 1, "Returns a hash code consistent with equals.\n\n@return hash code of this value object");
        result.append("    @Override\n    public int hashCode() {\n        return Objects.hash(value);\n    }\n");
        result.append("}\n");
        return result.toString();
    }

    /** Descripción: Crea o prepara el resultado de «buildClass» dentro de JavaMetamodelExporter. Entradas: context: dato de entrada necesario para la operación; packageName: dato de entrada necesario para la operación; metaClass: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String buildClass(NeutralMetamodelExportContext context, String packageName, MetaClass metaClass) {
        String name = MetamodelExportSupport.pascal(metaClass.name(), "GeneratedClass");
        List<JavaMember> members = members(context, metaClass);
        StringBuilder result = new StringBuilder();
        appendPackage(result, packageName);
        result.append("import java.util.ArrayList;\nimport java.util.Arrays;\nimport java.util.List;\nimport java.util.Objects;\n\n");
        appendJavadoc(result, 0, classDocumentation(metaClass, members));
        result.append(metaClass.kind() == MetaClassKind.INTERFACE ? "public interface " : metaClass.kind() == MetaClassKind.ABSTRACT_CLASS ? "public abstract class " : "public class ").append(name);
        appendInheritance(result, context, metaClass);
        result.append(" {\n");

        if(metaClass.kind() == MetaClassKind.INTERFACE) {
            appendInterfaceMembers(result, members);
            appendOperationMethods(result, context, metaClass, true);
            result.append("}\n");
            return result.toString();
        }

        for(JavaMember member : members) {
            result.append("\n");
            appendJavadoc(result, 1, member.documentation());
            result.append("    private ").append(member.type()).append(" ").append(member.name()).append(member.initializer()).append(";\n");
        }

        for(JavaMember member : members) {
            appendAccessorMethods(result, member);
        }

        appendOperationMethods(result, context, metaClass, metaClass.kind() == MetaClassKind.ABSTRACT_CLASS);
        appendToString(result, name, members, hasClassSuperType(context, metaClass));
        appendEquals(result, name, members, hasClassSuperType(context, metaClass));
        appendHashCode(result, members, hasClassSuperType(context, metaClass));
        result.append("}\n");
        return result.toString();
    }

    /** Descripción: Ejecuta la operación «classDocumentation» correspondiente a la responsabilidad de JavaMetamodelExporter. Entradas: metaClass: dato de entrada necesario para la operación; members: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String classDocumentation(MetaClass metaClass, List<JavaMember> members) {
        StringBuilder documentation = new StringBuilder("Generated representation of the metamodel ")
                .append(metaClass.kind() == MetaClassKind.INTERFACE ? "interface" : "class")
                .append(" \"").append(metaClass.name()).append("\".");

        if(!members.isEmpty()) {
            documentation.append("\n\n<p>Generated elements:</p>\n<ul>");

            for(JavaMember member : members) {
                documentation.append("\n<li>").append(member.name()).append(" - ").append(member.documentation()).append("</li>");
            }

            documentation.append("\n</ul>");
        }

        return documentation.toString();
    }

    /** Descripción: Ejecuta la operación «members» correspondiente a la responsabilidad de JavaMetamodelExporter. Entradas: context: dato de entrada necesario para la operación; metaClass: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<JavaMember> members(NeutralMetamodelExportContext context, MetaClass metaClass) {
        Map<String, JavaMember> result = new LinkedHashMap<>();
        appendDeclaredMembers(context, metaClass, result);

        // Interface attributes and references are structural requirements. A concrete
        // implementing class needs real storage and accessors for them; an abstract
        // class may leave those methods abstract.
        if(metaClass.kind() == MetaClassKind.CLASS) {
            for(MetaClass contract : interfaceContracts(context, metaClass)) {
                appendDeclaredMembers(context, contract, result);
            }
        }

        return List.copyOf(result.values());
    }

    /** Descripción: Ejecuta la operación «appendDeclaredMembers» correspondiente a la responsabilidad de JavaMetamodelExporter. Entradas: context: dato de entrada necesario para la operación; metaClass: dato de entrada necesario para la operación; result: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void appendDeclaredMembers(NeutralMetamodelExportContext context, MetaClass metaClass, Map<String, JavaMember> result) {
        for(MetaAttribute attribute : metaClass.attributes()) {
            String memberName = MetamodelExportSupport.camel(attribute.name(), "value");
            String documentation = "Metamodel attribute \"" + attribute.name() + "\" of type " + MetamodelExportSupport.resolveTypeName(context, attribute.type()) + " with multiplicity " + printableMultiplicity(attribute.multiplicity()) + ".";
            result.putIfAbsent(memberName, new JavaMember(memberName, javaType(context, attribute), initializer(attribute.multiplicity()), documentation));
        }

        for(MetaRelation relation : MetamodelExportSupport.structuralRelationsFrom(context, metaClass.id())) {
            String targetName = MetamodelExportSupport.pascal(MetamodelExportSupport.relationTargetName(context, relation), "Object");
            String type = isMany(relation.targetMultiplicity()) ? "List<" + targetName + ">" : targetName;
            String memberName = MetamodelExportSupport.camel(MetamodelExportSupport.text(relation.name(), "reference"), "reference");
            String documentation = "Structural relation \"" + MetamodelExportSupport.text(relation.name(), "reference") + "\" targeting " + targetName + " with multiplicity " + printableMultiplicity(relation.targetMultiplicity()) + ".";
            result.putIfAbsent(memberName, new JavaMember(memberName, type, initializer(relation.targetMultiplicity()), documentation));
        }
    }

    /** Descripción: Ejecuta la operación «appendInterfaceMembers» correspondiente a la responsabilidad de JavaMetamodelExporter. Entradas: result: dato de entrada necesario para la operación; members: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void appendInterfaceMembers(StringBuilder result, List<JavaMember> members) {
        if(members.isEmpty()) {
            result.append("\n");
            appendJavadoc(result, 1, "Marker interface without declared structural elements.");
            result.append("    // No structural elements declared.\n");
            return;
        }

        for(JavaMember member : members) {
            String suffix = MetamodelExportSupport.pascal(member.name(), "Value");
            result.append("\n");
            appendJavadoc(result, 1, "Returns " + member.documentation() + "\n\n@return current value");
            result.append("    ").append(member.type()).append(" get").append(suffix).append("();\n\n");
            appendJavadoc(result, 1, "Updates " + member.documentation() + "\n\n@param value new value");
            result.append("    void set").append(suffix).append("(").append(member.type()).append(" value);\n");
        }
    }

    /** Descripción: Ejecuta la operación «appendAccessorMethods» correspondiente a la responsabilidad de JavaMetamodelExporter. Entradas: result: dato de entrada necesario para la operación; member: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void appendAccessorMethods(StringBuilder result, JavaMember member) {
        String suffix = MetamodelExportSupport.pascal(member.name(), "Value");
        result.append("\n");
        appendJavadoc(result, 1, "Returns " + member.documentation() + "\n\n@return current value");
        result.append("    public ").append(member.type()).append(" get").append(suffix).append("() {\n        return ").append(member.name()).append(";\n    }\n\n");
        appendJavadoc(result, 1, "Updates " + member.documentation() + "\n\n@param value new value");
        result.append("    public void set").append(suffix).append("(").append(member.type()).append(" value) {\n        this.").append(member.name()).append(" = value;\n    }\n");
    }

    /** Descripción: Ejecuta la operación «appendOperationMethods» correspondiente a la responsabilidad de JavaMetamodelExporter. Entradas: result: dato de entrada necesario para la operación; context: dato de entrada necesario para la operación; owner: dato de entrada necesario para la operación; abstractDeclaration: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void appendOperationMethods(StringBuilder result, NeutralMetamodelExportContext context, MetaClass owner, boolean abstractDeclaration) {
        for(MetaOperation operation : operationsForGeneration(context, owner)) {
            result.append("\n");
            String returnType = operation.returnType() == null ? "void" : javaOperationType(context, operation.returnType(), operation.returnMultiplicity());
            StringBuilder documentation = new StringBuilder("Generated metamodel operation \"").append(operation.name()).append("\".");
            for(MetaParameter parameter : operation.parameters()) {
                documentation.append("\n\n@param ").append(MetamodelExportSupport.camel(parameter.name(), "value")).append(" value for parameter ").append(parameter.name());
            }
            if(operation.returnType() != null) documentation.append("\n@return generated operation result");
            appendJavadoc(result, 1, documentation.toString());
            if(abstractDeclaration) {
                result.append("    public ");
                if(owner.kind() == MetaClassKind.ABSTRACT_CLASS) result.append("abstract ");
            } else {
                result.append("    public ");
            }
            result.append(returnType).append(" ").append(MetamodelExportSupport.camel(operation.name(), "operation")).append("(");
            for(int index = 0; index < operation.parameters().size(); index += 1) {
                MetaParameter parameter = operation.parameters().get(index);
                if(index > 0) result.append(", ");
                result.append(javaOperationType(context, parameter.type(), parameter.multiplicity())).append(" ").append(MetamodelExportSupport.camel(parameter.name(), "value"));
            }
            result.append(")");
            if(!operation.exceptionTypes().isEmpty()) {
                result.append(" throws ");
                for(int index = 0; index < operation.exceptionTypes().size(); index += 1) {
                    if(index > 0) result.append(", ");
                    result.append(javaOperationType(context, operation.exceptionTypes().get(index), Multiplicity.one()));
                }
            }
            if(abstractDeclaration) {
                result.append(";\n");
            } else {
                result.append(" {\n        throw new UnsupportedOperationException(\"Operation ").append(operation.name()).append(" has no generated implementation\");\n    }\n");
            }
        }
    }

    /** Descripción: Ejecuta la operación «operationsForGeneration» correspondiente a la responsabilidad de JavaMetamodelExporter. Entradas: context: dato de entrada necesario para la operación; owner: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<MetaOperation> operationsForGeneration(NeutralMetamodelExportContext context, MetaClass owner) {
        Map<String, MetaOperation> operations = new LinkedHashMap<>();
        for(MetaOperation operation : owner.operations()) operations.put(operationSignature(operation), operation);
        if(owner.kind() == MetaClassKind.CLASS) {
            for(MetaClass parent : allSuperTypes(context, owner)) {
                for(MetaOperation operation : parent.operations()) operations.putIfAbsent(operationSignature(operation), operation);
            }
        }
        return List.copyOf(operations.values());
    }

    /** Descripción: Ejecuta la operación «operationSignature» correspondiente a la responsabilidad de JavaMetamodelExporter. Entradas: operation: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String operationSignature(MetaOperation operation) {
        StringBuilder result = new StringBuilder(operation.name()).append('(');
        for(MetaParameter parameter : operation.parameters()) {
            result.append(parameter.type().kind()).append(':').append(parameter.type().targetId()).append(':').append(parameter.type().externalName()).append(';');
        }
        return result.append(')').toString();
    }

    /** Descripción: Ejecuta la operación «interfaceContracts» correspondiente a la responsabilidad de JavaMetamodelExporter. Entradas: context: dato de entrada necesario para la operación; owner: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<MetaClass> interfaceContracts(NeutralMetamodelExportContext context, MetaClass owner) {
        return allSuperTypes(context, owner).stream().filter(item -> item.kind() == MetaClassKind.INTERFACE).toList();
    }

    /** Descripción: Ejecuta la operación «allSuperTypes» correspondiente a la responsabilidad de JavaMetamodelExporter. Entradas: context: dato de entrada necesario para la operación; owner: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<MetaClass> allSuperTypes(NeutralMetamodelExportContext context, MetaClass owner) {
        Map<String, MetaClass> classes = new LinkedHashMap<>();
        for(MetaClass item : context.neutralMetamodel().classes()) classes.put(item.id(), item);
        List<MetaClass> result = new ArrayList<>();
        collectSuperTypes(context, owner.id(), classes, new LinkedHashSet<>(), result);
        return result;
    }

    /** Descripción: Ejecuta la operación «collectSuperTypes» correspondiente a la responsabilidad de JavaMetamodelExporter. Entradas: context: dato de entrada necesario para la operación; classId: identificador que delimita el recurso objetivo; classes: dato de entrada necesario para la operación; visited: dato de entrada necesario para la operación; result: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void collectSuperTypes(NeutralMetamodelExportContext context, String classId, Map<String, MetaClass> classes, Set<String> visited, List<MetaClass> result) {
        for(MetaRelation relation : MetamodelExportSupport.superTypeRelationsFrom(context, classId)) {
            if(!visited.add(relation.targetTypeId())) continue;
            MetaClass parent = classes.get(relation.targetTypeId());
            if(parent == null) continue;
            result.add(parent);
            collectSuperTypes(context, parent.id(), classes, visited, result);
        }
    }

    /** Descripción: Ejecuta la operación «javaOperationType» correspondiente a la responsabilidad de JavaMetamodelExporter. Entradas: context: dato de entrada necesario para la operación; type: dato de entrada necesario para la operación; multiplicity: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String javaOperationType(NeutralMetamodelExportContext context, MetaTypeReference type, Multiplicity multiplicity) {
        String base = switch(type.kind()) {
            case PRIMITIVE -> switch(type.targetId()) {
                case "STRING" -> "String";
                case "BOOLEAN" -> "Boolean";
                case "BYTE" -> "Byte";
                case "SHORT" -> "Short";
                case "INTEGER" -> "Integer";
                case "LONG" -> "Long";
                case "FLOAT" -> "Float";
                case "DOUBLE" -> "Double";
                case "DECIMAL" -> "java.math.BigDecimal";
                case "BIG_INTEGER" -> "java.math.BigInteger";
                case "CHARACTER" -> "Character";
                case "DATE" -> "java.time.LocalDate";
                case "BINARY" -> "byte[]";
                default -> "Object";
            };
            case EXTERNAL -> type.externalName().isBlank() ? "Object" : type.externalName();
            default -> MetamodelExportSupport.pascal(MetamodelExportSupport.resolveTypeName(context, type), "Object");
        };
        return isMany(multiplicity) ? "List<" + base + ">" : base;
    }

    /** Descripción: Ejecuta la operación «appendToString» correspondiente a la responsabilidad de JavaMetamodelExporter. Entradas: result: dato de entrada necesario para la operación; className: dato de entrada necesario para la operación; members: dato de entrada necesario para la operación; hasClassSuperType: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void appendToString(StringBuilder result, String className, List<JavaMember> members, boolean hasClassSuperType) {
        result.append("\n");
        appendJavadoc(result, 1, "Returns a readable representation containing the generated class elements.\n\n@return readable representation of this instance");
        result.append("    @Override\n    public String toString() {\n");

        if(members.isEmpty() && !hasClassSuperType) {
            result.append("        return \"").append(className).append("{}\";\n");
        } else {
            result.append("        return \"").append(className).append("{\" +\n");
            List<String> expressions = new ArrayList<>();

            for(JavaMember member : members) {
                expressions.add("                \"" + member.name() + "=\" + " + member.name());
            }

            if(hasClassSuperType) {
                expressions.add("                \"inherited=\" + super.toString()");
            }

            for(int index = 0; index < expressions.size(); index += 1) {
                result.append(expressions.get(index));

                if(index < expressions.size() - 1) {
                    result.append(" + \", \" +\n");
                } else {
                    result.append(" +\n");
                }
            }

            result.append("                '}';\n");
        }

        result.append("    }\n");
    }

    /** Descripción: Ejecuta la operación «appendEquals» correspondiente a la responsabilidad de JavaMetamodelExporter. Entradas: result: dato de entrada necesario para la operación; className: dato de entrada necesario para la operación; members: dato de entrada necesario para la operación; hasClassSuperType: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void appendEquals(StringBuilder result, String className, List<JavaMember> members, boolean hasClassSuperType) {
        result.append("\n");
        appendJavadoc(result, 1, "Compares all generated class elements.\n\n@param object object to compare\n@return true when both instances contain equal values");
        result.append("    @Override\n    public boolean equals(Object object) {\n        if(this == object) {\n            return true;\n        }\n\n        if(object == null || getClass() != object.getClass()) {\n            return false;\n        }\n\n");

        if(hasClassSuperType) {
            result.append("        if(!super.equals(object)) {\n            return false;\n        }\n\n");
        }

        result.append("        ").append(className).append(" other = (").append(className).append(") object;\n");

        if(members.isEmpty()) {
            result.append("        return true;\n");
        } else {
            result.append("        return ");

            for(int index = 0; index < members.size(); index += 1) {
                JavaMember member = members.get(index);
                result.append("Objects.deepEquals(").append(member.name()).append(", other.").append(member.name()).append(")");
                result.append(index < members.size() - 1 ? " &&\n                " : ";\n");
            }
        }

        result.append("    }\n");
    }

    /** Descripción: Ejecuta la operación «appendHashCode» correspondiente a la responsabilidad de JavaMetamodelExporter. Entradas: result: dato de entrada necesario para la operación; members: dato de entrada necesario para la operación; hasClassSuperType: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void appendHashCode(StringBuilder result, List<JavaMember> members, boolean hasClassSuperType) {
        result.append("\n");
        appendJavadoc(result, 1, "Returns a hash code consistent with equals.\n\n@return hash code of this instance");
        result.append("    @Override\n    public int hashCode() {\n");

        if(members.isEmpty()) {
            result.append(hasClassSuperType ? "        return super.hashCode();\n" : "        return 1;\n");
        } else {
            result.append("        int localHash = Arrays.deepHashCode(new Object[] { ");

            for(int index = 0; index < members.size(); index += 1) {
                result.append(members.get(index).name());

                if(index < members.size() - 1) {
                    result.append(", ");
                }
            }

            result.append(" });\n");
            result.append(hasClassSuperType ? "        return 31 * super.hashCode() + localHash;\n" : "        return localHash;\n");
        }

        result.append("    }\n");
    }

    /** Descripción: Ejecuta la operación «appendInheritance» correspondiente a la responsabilidad de JavaMetamodelExporter. Entradas: result: dato de entrada necesario para la operación; context: dato de entrada necesario para la operación; metaClass: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void appendInheritance(StringBuilder result, NeutralMetamodelExportContext context, MetaClass metaClass) {
        List<MetaRelation> superTypes = MetamodelExportSupport.superTypeRelationsFrom(context, metaClass.id());
        String classSuper = null;
        StringBuilder interfaces = new StringBuilder();

        for(MetaRelation relation : superTypes) {
            String target = MetamodelExportSupport.pascal(MetamodelExportSupport.relationTargetName(context, relation), "GeneratedClass");

            if(relation.kind() == MetaRelationKind.IMPLEMENTATION || metaClass.kind() == MetaClassKind.INTERFACE) {
                if(!interfaces.isEmpty()) {
                    interfaces.append(", ");
                }

                interfaces.append(target);
            } else if(classSuper == null) {
                classSuper = target;
            }
        }

        if(metaClass.kind() == MetaClassKind.INTERFACE) {
            if(!interfaces.isEmpty()) {
                result.append(" extends ").append(interfaces);
            }
        } else {
            if(classSuper != null) {
                result.append(" extends ").append(classSuper);
            }

            if(!interfaces.isEmpty()) {
                result.append(" implements ").append(interfaces);
            }
        }
    }

    /** Descripción: Evalúa la condición «hasClassSuperType» sin modificar el estado externo. Entradas: context: dato de entrada necesario para la operación; metaClass: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean hasClassSuperType(NeutralMetamodelExportContext context, MetaClass metaClass) {
        return MetamodelExportSupport.superTypeRelationsFrom(context, metaClass.id()).stream().anyMatch(relation -> relation.kind() == MetaRelationKind.INHERITANCE);
    }

    /** Descripción: Ejecuta la operación «javaType» correspondiente a la responsabilidad de JavaMetamodelExporter. Entradas: context: dato de entrada necesario para la operación; attribute: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String javaType(NeutralMetamodelExportContext context, MetaAttribute attribute) {
        String base = switch(attribute.type().kind()) {
            case PRIMITIVE -> switch(attribute.type().targetId()) {
                case "STRING", "CHARACTER" -> "String";
                case "BOOLEAN" -> "Boolean";
                case "BYTE" -> "Byte";
                case "SHORT" -> "Short";
                case "INTEGER" -> "Integer";
                case "LONG", "BIG_INTEGER" -> "Long";
                case "FLOAT" -> "Float";
                case "DOUBLE", "DECIMAL" -> "Double";
                case "DATE" -> "java.time.Instant";
                case "BINARY" -> "byte[]";
                default -> "Object";
            };
            default -> MetamodelExportSupport.pascal(MetamodelExportSupport.resolveTypeName(context, attribute.type()), "Object");
        };

        return isMany(attribute.multiplicity()) ? "List<" + base + ">" : base;
    }

    /** Descripción: Evalúa la condición «isMany» sin modificar el estado externo. Entradas: multiplicity: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean isMany(Multiplicity multiplicity) {
        return multiplicity != null && (multiplicity.unbounded() || multiplicity.upperBound() > 1);
    }

    /** Descripción: Crea o prepara el resultado de «initializer» dentro de JavaMetamodelExporter. Entradas: multiplicity: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String initializer(Multiplicity multiplicity) {
        return isMany(multiplicity) ? " = new ArrayList<>()" : "";
    }

    /** Descripción: Ejecuta la operación «printableMultiplicity» correspondiente a la responsabilidad de JavaMetamodelExporter. Entradas: multiplicity: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String printableMultiplicity(Multiplicity multiplicity) {
        String value = MetamodelExportSupport.multiplicity(multiplicity);
        return value.isBlank() ? "unspecified" : value;
    }

    /** Descripción: Ejecuta la operación «appendPackage» correspondiente a la responsabilidad de JavaMetamodelExporter. Entradas: result: dato de entrada necesario para la operación; packageName: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void appendPackage(StringBuilder result, String packageName) {
        if(!packageName.isBlank()) {
            result.append("package ").append(packageName).append(";\n\n");
        }
    }

    /** Descripción: Ejecuta la operación «appendJavadoc» correspondiente a la responsabilidad de JavaMetamodelExporter. Entradas: result: dato de entrada necesario para la operación; indentation: dato de entrada necesario para la operación; documentation: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void appendJavadoc(StringBuilder result, int indentation, String documentation) {
        String prefix = "    ".repeat(indentation);
        result.append(prefix).append("/**\n");

        for(String line : documentation.replace("*/", "* /").split("\\n", -1)) {
            result.append(prefix).append(" *");

            if(!line.isEmpty()) {
                result.append(" ").append(line);
            }

            result.append("\n");
        }

        result.append(prefix).append(" */\n");
    }

    /** Descripción: Ejecuta la operación «className» correspondiente a la responsabilidad de JavaMetamodelExporter. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String className(String value) {
        return MetamodelExportSupport.pascal(value, "Generated") + ".java";
    }

    /** Descripción: Ejecuta la operación «add» correspondiente a la responsabilidad de JavaMetamodelExporter. Entradas: zip: dato de entrada necesario para la operación; name: dato de entrada necesario para la operación; content: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void add(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private record JavaMember(String name, String type, String initializer, String documentation) {
    }
}
