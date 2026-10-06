package com.example.editor.metamodel.validation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.example.editor.metamodel.core.MetaAttribute;
import com.example.editor.metamodel.core.MetaClass;
import com.example.editor.metamodel.core.MetaClassKind;
import com.example.editor.metamodel.core.MetaEnum;
import com.example.editor.metamodel.core.MetaEnumLiteral;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.core.MetaOperation;
import com.example.editor.metamodel.core.MetaParameter;
import com.example.editor.metamodel.core.MetaTypeReference;
import com.example.editor.metamodel.core.MetaRelation;
import com.example.editor.metamodel.core.MetaRelationKind;
import com.example.editor.metamodel.core.MetaTypeReferenceKind;
import com.example.editor.metamodel.core.Multiplicity;

/**
 * Valida el núcleo neutral sin depender de EMF.
 */
@Component
public class NeutralMetamodelValidator {

    /**
     * Descripción:
     * Valida un metamodelo neutral.
     *
     * Entrada:
     * @param metamodel contiene el metamodelo.
     *
     * Salida:
     * @return el informe completo.
     */
    /** Descripción: Valida las condiciones necesarias para «validate» y rechaza entradas no válidas. Entradas: metamodel: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ValidationReport validate(MetaModel metamodel) {
        List<ValidationIssue> issues = new ArrayList<>();

        if (metamodel == null) {
            issues.add(error("MODEL_NULL", "$", "Neutral metamodel must not be null"));
            return new ValidationReport(issues);
        }

        requireText(metamodel.id(), "$.id", "MODEL_ID_EMPTY", "Metamodel id must not be empty", issues);
        requireText(metamodel.name(), "$.name", "MODEL_NAME_EMPTY", "Metamodel name must not be empty", issues);

        if (!MetaModel.CURRENT_SCHEMA_VERSION.equals(metamodel.schemaVersion())) {
            issues.add(error("MODEL_SCHEMA_VERSION_UNSUPPORTED", "$.schemaVersion", "Unsupported MetaModel schema version: " + metamodel.schemaVersion()));
        }
        validateClassifiers(metamodel, issues);
        validateAttributes(metamodel, issues);
        validateOperations(metamodel, issues);
        validateEnums(metamodel, issues);
        validateRelations(metamodel, issues);
        validateInheritanceCycles(metamodel, issues);
        return new ValidationReport(issues);
    }

    /** Descripción: Valida las condiciones necesarias para «validateClassifiers» y rechaza entradas no válidas. Entradas: metamodel: dato de entrada necesario para la operación; issues: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void validateClassifiers(MetaModel metamodel, List<ValidationIssue> issues) {
        Set<String> ids = new HashSet<>();
        Set<String> names = new HashSet<>();

        for (MetaClass item : metamodel.classes()) {
            validateClassifier(item.id(), item.name(), "$.classes", ids, names, issues);
        }

        for (MetaEnum item : metamodel.enums()) {
            validateClassifier(item.id(), item.name(), "$.enums", ids, names, issues);
        }

        for (var item : metamodel.dataTypes()) {
            validateClassifier(item.id(), item.name(), "$.dataTypes", ids, names, issues);
        }
    }

    /** Descripción: Valida las condiciones necesarias para «validateClassifier» y rechaza entradas no válidas. Entradas: id: identificador que delimita el recurso objetivo; name: dato de entrada necesario para la operación; path: ruta o ubicación utilizada por la operación; ids: dato de entrada necesario para la operación; names: dato de entrada necesario para la operación; issues: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void validateClassifier(String id, String name, String path, Set<String> ids, Set<String> names, List<ValidationIssue> issues) {
        requireText(id, path, "CLASSIFIER_ID_EMPTY", "Classifier id must not be empty", issues);
        requireText(name, path, "CLASSIFIER_NAME_EMPTY", "Classifier name must not be empty", issues);

        if (id != null && !id.isBlank() && !ids.add(id)) {
            issues.add(error("CLASSIFIER_ID_DUPLICATE", path, "Duplicate classifier id: " + id));
        }

        if (name != null && !name.isBlank() && !names.add(name)) {
            issues.add(error("CLASSIFIER_NAME_DUPLICATE", path, "Duplicate classifier name: " + name));
        }
    }

    /** Descripción: Valida las condiciones necesarias para «validateAttributes» y rechaza entradas no válidas. Entradas: metamodel: dato de entrada necesario para la operación; issues: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void validateAttributes(MetaModel metamodel, List<ValidationIssue> issues) {
        Set<String> enumIds = metamodel.enums().stream().map(MetaEnum::id).collect(java.util.stream.Collectors.toSet());
        Set<String> dataTypeIds = metamodel.dataTypes().stream().map(item -> item.id()).collect(java.util.stream.Collectors.toSet());

        for (MetaClass classDesign : metamodel.classes()) {
            Set<String> featureIds = new HashSet<>();
            Set<String> featureNames = new HashSet<>();

            for (MetaAttribute attribute : classDesign.attributes()) {
                String path = "$.classes[" + classDesign.id() + "].attributes[" + attribute.id() + "]";
                requireText(attribute.id(), path, "ATTRIBUTE_ID_EMPTY", "Attribute id must not be empty", issues);
                requireText(attribute.name(), path, "ATTRIBUTE_NAME_EMPTY", "Attribute name must not be empty", issues);

                if (attribute.id() != null && !attribute.id().isBlank() && !featureIds.add(attribute.id())) {
                    issues.add(error("ATTRIBUTE_ID_DUPLICATE", path, "Duplicate attribute id in class: " + attribute.id()));
                }

                if (attribute.name() != null && !attribute.name().isBlank() && !featureNames.add(attribute.name())) {
                    issues.add(error("ATTRIBUTE_NAME_DUPLICATE", path, "Duplicate attribute name in class: " + attribute.name()));
                }

                validateMultiplicity(attribute.multiplicity(), path + ".multiplicity", issues);

                if (attribute.type() == null || attribute.type().kind() == null) {
                    issues.add(error("ATTRIBUTE_TYPE_EMPTY", path + ".type", "Attribute type must not be empty"));
                } else if (attribute.type().kind() == MetaTypeReferenceKind.ENUM && !enumIds.contains(attribute.type().targetId())) {
                    issues.add(error("ATTRIBUTE_ENUM_UNKNOWN", path + ".type", "Unknown enumeration id: " + attribute.type().targetId()));
                } else if (attribute.type().kind() == MetaTypeReferenceKind.DATATYPE && !dataTypeIds.contains(attribute.type().targetId())) {
                    issues.add(error("ATTRIBUTE_DATATYPE_UNKNOWN", path + ".type", "Unknown data type id: " + attribute.type().targetId()));
                } else if (attribute.type().kind() == MetaTypeReferenceKind.CLASS) {
                    issues.add(error("ATTRIBUTE_CLASS_TYPE", path + ".type", "Attributes cannot use a class type; use a relation instead"));
                } else if (attribute.type().kind() == MetaTypeReferenceKind.EXTERNAL) {
                    issues.add(warning("ATTRIBUTE_EXTERNAL_TYPE", path + ".type", "External attribute type requires an adapter mapping: " + attribute.type().externalName()));
                }
            }
        }
    }

    /** Descripción: Valida las condiciones necesarias para «validateOperations» y rechaza entradas no válidas. Entradas: metamodel: dato de entrada necesario para la operación; issues: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void validateOperations(MetaModel metamodel, List<ValidationIssue> issues) {
        Set<String> classIds = metamodel.classes().stream().map(MetaClass::id).collect(java.util.stream.Collectors.toSet());
        Set<String> enumIds = metamodel.enums().stream().map(MetaEnum::id).collect(java.util.stream.Collectors.toSet());
        Set<String> dataTypeIds = metamodel.dataTypes().stream().map(item -> item.id()).collect(java.util.stream.Collectors.toSet());
        Set<String> globalOperationIds = new HashSet<>();

        for(MetaClass owner : metamodel.classes()) {
            Set<String> signatures = new HashSet<>();

            for(MetaOperation operation : owner.operations()) {
                String path = "$.classes[" + owner.id() + "].operations[" + operation.id() + "]";
                requireText(operation.id(), path, "OPERATION_ID_EMPTY", "Operation id must not be empty", issues);
                requireText(operation.name(), path, "OPERATION_NAME_EMPTY", "Operation name must not be empty", issues);

                if(operation.id() != null && !operation.id().isBlank() && !globalOperationIds.add(operation.id())) {
                    issues.add(error("OPERATION_ID_DUPLICATE", path, "Duplicate operation id: " + operation.id()));
                }

                String signature = operation.name() + "(" + operation.parameters().stream().map(parameter -> parameter.type() == null ? "?" : parameter.type().kind() + ":" + parameter.type().targetId() + ":" + parameter.type().externalName()).collect(java.util.stream.Collectors.joining(",")) + ")";
                if(!operation.name().isBlank() && !signatures.add(signature)) {
                    issues.add(error("OPERATION_SIGNATURE_DUPLICATE", path, "Duplicate operation signature in class: " + signature));
                }

                if(operation.returnType() == null) {
                    if(operation.returnMultiplicity() != null) {
                        issues.add(error("OPERATION_VOID_MULTIPLICITY", path + ".returnMultiplicity", "Void operations cannot define a return multiplicity"));
                    }
                } else {
                    validateTypeReference(operation.returnType(), path + ".returnType", classIds, enumIds, dataTypeIds, "OPERATION_RETURN", issues);
                    validateMultiplicity(operation.returnMultiplicity(), path + ".returnMultiplicity", issues);
                }

                Set<String> parameterIds = new HashSet<>();
                Set<String> parameterNames = new HashSet<>();
                for(MetaParameter parameter : operation.parameters()) {
                    String parameterPath = path + ".parameters[" + parameter.id() + "]";
                    requireText(parameter.id(), parameterPath, "PARAMETER_ID_EMPTY", "Parameter id must not be empty", issues);
                    requireText(parameter.name(), parameterPath, "PARAMETER_NAME_EMPTY", "Parameter name must not be empty", issues);
                    if(parameter.id() != null && !parameter.id().isBlank() && !parameterIds.add(parameter.id())) {
                        issues.add(error("PARAMETER_ID_DUPLICATE", parameterPath, "Duplicate parameter id in operation: " + parameter.id()));
                    }
                    if(parameter.name() != null && !parameter.name().isBlank() && !parameterNames.add(parameter.name())) {
                        issues.add(error("PARAMETER_NAME_DUPLICATE", parameterPath, "Duplicate parameter name in operation: " + parameter.name()));
                    }
                    validateTypeReference(parameter.type(), parameterPath + ".type", classIds, enumIds, dataTypeIds, "PARAMETER", issues);
                    validateMultiplicity(parameter.multiplicity(), parameterPath + ".multiplicity", issues);
                }

                for(int index = 0; index < operation.exceptionTypes().size(); index += 1) {
                    validateTypeReference(operation.exceptionTypes().get(index), path + ".exceptionTypes[" + index + "]", classIds, enumIds, dataTypeIds, "OPERATION_EXCEPTION", issues);
                }
            }
        }
    }

    /** Descripción: Valida las condiciones necesarias para «validateTypeReference» y rechaza entradas no válidas. Entradas: type: dato de entrada necesario para la operación; path: ruta o ubicación utilizada por la operación; classIds: dato de entrada necesario para la operación; enumIds: dato de entrada necesario para la operación; dataTypeIds: dato de entrada necesario para la operación; codePrefix: dato de entrada necesario para la operación; issues: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void validateTypeReference(MetaTypeReference type, String path, Set<String> classIds, Set<String> enumIds, Set<String> dataTypeIds, String codePrefix, List<ValidationIssue> issues) {
        if(type == null || type.kind() == null) {
            issues.add(error(codePrefix + "_TYPE_EMPTY", path, "Type must not be empty"));
            return;
        }

        if(type.kind() == MetaTypeReferenceKind.CLASS && !classIds.contains(type.targetId())) {
            issues.add(error(codePrefix + "_CLASS_UNKNOWN", path, "Unknown class id: " + type.targetId()));
        } else if(type.kind() == MetaTypeReferenceKind.ENUM && !enumIds.contains(type.targetId())) {
            issues.add(error(codePrefix + "_ENUM_UNKNOWN", path, "Unknown enumeration id: " + type.targetId()));
        } else if(type.kind() == MetaTypeReferenceKind.DATATYPE && !dataTypeIds.contains(type.targetId())) {
            issues.add(error(codePrefix + "_DATATYPE_UNKNOWN", path, "Unknown data type id: " + type.targetId()));
        } else if(type.kind() == MetaTypeReferenceKind.EXTERNAL && (type.externalName() == null || type.externalName().isBlank())) {
            issues.add(error(codePrefix + "_EXTERNAL_EMPTY", path, "External type name must not be empty"));
        }
    }

    /** Descripción: Valida las condiciones necesarias para «validateEnums» y rechaza entradas no válidas. Entradas: metamodel: dato de entrada necesario para la operación; issues: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void validateEnums(MetaModel metamodel, List<ValidationIssue> issues) {
        for (MetaEnum enumDesign : metamodel.enums()) {
            Set<String> ids = new HashSet<>();
            Set<String> names = new HashSet<>();
            Set<Integer> values = new HashSet<>();

            if (enumDesign.literals().isEmpty()) {
                issues.add(error("ENUM_EMPTY", "$.enums[" + enumDesign.id() + "]", "Enumeration must contain at least one literal"));
            }

            for (MetaEnumLiteral literal : enumDesign.literals()) {
                String path = "$.enums[" + enumDesign.id() + "].literals[" + literal.id() + "]";

                if (!ids.add(literal.id())) {
                    issues.add(error("ENUM_LITERAL_ID_DUPLICATE", path, "Duplicate enum literal id: " + literal.id()));
                }

                if (!names.add(literal.name())) {
                    issues.add(error("ENUM_LITERAL_NAME_DUPLICATE", path, "Duplicate enum literal name: " + literal.name()));
                }

                if (!values.add(literal.value())) {
                    issues.add(error("ENUM_LITERAL_VALUE_DUPLICATE", path, "Duplicate enum literal value: " + literal.value()));
                }
            }
        }
    }

    /** Descripción: Valida las condiciones necesarias para «validateRelations» y rechaza entradas no válidas. Entradas: metamodel: dato de entrada necesario para la operación; issues: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void validateRelations(MetaModel metamodel, List<ValidationIssue> issues) {
        Map<String, MetaClass> classes = new HashMap<>();

        for (MetaClass item : metamodel.classes()) {
            classes.put(item.id(), item);
        }

        Map<String, MetaRelation> relations = new HashMap<>();

        for (MetaRelation relation : metamodel.relations()) {
            String path = "$.relations[" + relation.id() + "]";
            requireText(relation.id(), path, "RELATION_ID_EMPTY", "Relation id must not be empty", issues);

            if (relations.put(relation.id(), relation) != null) {
                issues.add(error("RELATION_ID_DUPLICATE", path, "Duplicate relation id: " + relation.id()));
            }

            MetaClass source = classes.get(relation.sourceTypeId());
            MetaClass target = classes.get(relation.targetTypeId());

            if (source == null) {
                issues.add(error("RELATION_SOURCE_UNKNOWN", path, "Unknown relation source type: " + relation.sourceTypeId()));
            }

            if (target == null) {
                issues.add(error("RELATION_TARGET_UNKNOWN", path, "Unknown relation target type: " + relation.targetTypeId()));
            }

            if (relation.targetMultiplicity() != null) {
                validateMultiplicity(relation.targetMultiplicity(), path + ".targetMultiplicity", issues);
            }

            if (source != null && target != null) {
                validateRelationKinds(relation, source, target, path, issues);
            }
        }

        for (MetaRelation relation : metamodel.relations()) {
            if (relation.oppositeRelationId().isBlank()) {
                continue;
            }

            MetaRelation opposite = relations.get(relation.oppositeRelationId());

            if (opposite == null) {
                issues.add(error("RELATION_OPPOSITE_UNKNOWN", "$.relations[" + relation.id() + "]", "Unknown opposite relation id: " + relation.oppositeRelationId()));
            } else if (!relation.sourceTypeId().equals(opposite.targetTypeId()) || !relation.targetTypeId().equals(opposite.sourceTypeId())) {
                issues.add(error("RELATION_OPPOSITE_INCOMPATIBLE", "$.relations[" + relation.id() + "]", "Opposite relations must exchange source and target types"));
            }
        }
    }

    /** Descripción: Valida las condiciones necesarias para «validateRelationKinds» y rechaza entradas no válidas. Entradas: relation: dato de entrada necesario para la operación; source: dato de entrada necesario para la operación; target: dato de entrada necesario para la operación; path: ruta o ubicación utilizada por la operación; issues: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void validateRelationKinds(MetaRelation relation, MetaClass source, MetaClass target, String path, List<ValidationIssue> issues) {
        if (relation.kind() == MetaRelationKind.IMPLEMENTATION) {
            if (source.kind() == MetaClassKind.INTERFACE || target.kind() != MetaClassKind.INTERFACE) {
                issues.add(error("IMPLEMENTATION_INVALID", path, "Implementation must start at a class and end at an interface"));
            }
        }

        if (relation.kind() == MetaRelationKind.INHERITANCE) {
            boolean bothInterfaces = source.kind() == MetaClassKind.INTERFACE && target.kind() == MetaClassKind.INTERFACE;
            boolean bothClasses = source.kind() != MetaClassKind.INTERFACE && target.kind() != MetaClassKind.INTERFACE;

            if (!bothInterfaces && !bothClasses) {
                issues.add(error("INHERITANCE_INVALID", path, "Inheritance must connect two classes or two interfaces"));
            }
        }

        if ((relation.kind() == MetaRelationKind.REFERENCE || relation.kind() == MetaRelationKind.CONTAINMENT || relation.kind() == MetaRelationKind.AGGREGATION) && target.kind() == MetaClassKind.INTERFACE) {
            issues.add(error("STRUCTURAL_RELATION_TO_INTERFACE", path, "Structural relations cannot target an interface"));
        }
    }

    /** Descripción: Valida las condiciones necesarias para «validateInheritanceCycles» y rechaza entradas no válidas. Entradas: metamodel: dato de entrada necesario para la operación; issues: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void validateInheritanceCycles(MetaModel metamodel, List<ValidationIssue> issues) {
        Map<String, List<String>> graph = new HashMap<>();

        for (MetaRelation relation : metamodel.relations()) {
            if (relation.kind() == MetaRelationKind.INHERITANCE || relation.kind() == MetaRelationKind.IMPLEMENTATION) {
                graph.computeIfAbsent(relation.sourceTypeId(), key -> new ArrayList<>()).add(relation.targetTypeId());
            }
        }

        Set<String> visited = new HashSet<>();
        Set<String> active = new HashSet<>();

        for (MetaClass classDesign : metamodel.classes()) {
            if (detectCycle(classDesign.id(), graph, visited, active)) {
                issues.add(error("INHERITANCE_CYCLE", "$.relations", "Inheritance or implementation cycle detected at: " + classDesign.id()));
                return;
            }
        }
    }

    /** Descripción: Ejecuta la operación «detectCycle» correspondiente a la responsabilidad de NeutralMetamodelValidator. Entradas: current: dato de entrada necesario para la operación; graph: dato de entrada necesario para la operación; visited: dato de entrada necesario para la operación; active: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean detectCycle(String current, Map<String, List<String>> graph, Set<String> visited, Set<String> active) {
        if (active.contains(current)) {
            return true;
        }

        if (!visited.add(current)) {
            return false;
        }

        active.add(current);

        for (String target : graph.getOrDefault(current, List.of())) {
            if (detectCycle(target, graph, visited, active)) {
                return true;
            }
        }

        active.remove(current);
        return false;
    }

    /** Descripción: Valida las condiciones necesarias para «validateMultiplicity» y rechaza entradas no válidas. Entradas: multiplicity: dato de entrada necesario para la operación; path: ruta o ubicación utilizada por la operación; issues: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void validateMultiplicity(Multiplicity multiplicity, String path, List<ValidationIssue> issues) {
        if (multiplicity == null) {
            issues.add(error("MULTIPLICITY_EMPTY", path, "Multiplicity must not be empty"));
            return;
        }

        if (multiplicity.lowerBound() < 0) {
            issues.add(error("MULTIPLICITY_LOWER_INVALID", path, "Multiplicity lower bound must not be negative"));
        }

        if (multiplicity.upperBound() != null && multiplicity.upperBound() < multiplicity.lowerBound()) {
            issues.add(error("MULTIPLICITY_UPPER_INVALID", path, "Multiplicity upper bound must be greater than or equal to the lower bound"));
        }
    }

    /** Descripción: Valida las condiciones necesarias para «requireText» y rechaza entradas no válidas. Entradas: value: valor de entrada que debe procesarse; path: ruta o ubicación utilizada por la operación; code: dato de entrada necesario para la operación; message: dato de entrada necesario para la operación; issues: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void requireText(String value, String path, String code, String message, List<ValidationIssue> issues) {
        if (value == null || value.isBlank()) {
            issues.add(error(code, path, message));
        }
    }

    /** Descripción: Ejecuta la operación «error» correspondiente a la responsabilidad de NeutralMetamodelValidator. Entradas: code: dato de entrada necesario para la operación; path: ruta o ubicación utilizada por la operación; message: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private ValidationIssue error(String code, String path, String message) {
        return new ValidationIssue(ValidationSeverity.ERROR, code, path, message);
    }

    /** Descripción: Ejecuta la operación «warning» correspondiente a la responsabilidad de NeutralMetamodelValidator. Entradas: code: dato de entrada necesario para la operación; path: ruta o ubicación utilizada por la operación; message: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private ValidationIssue warning(String code, String path, String message) {
        return new ValidationIssue(ValidationSeverity.WARNING, code, path, message);
    }
}
