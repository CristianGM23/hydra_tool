package com.example.editor.semantic.expression;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.example.editor.metamodel.core.MetaAttribute;
import com.example.editor.metamodel.core.MetaClass;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.core.MetaOperation;
import com.example.editor.metamodel.core.MetaOperationBehaviorKind;
import com.example.editor.metamodel.core.MetaParameter;
import com.example.editor.metamodel.core.MetaPrimitiveType;
import com.example.editor.metamodel.core.MetaRelation;
import com.example.editor.metamodel.core.MetaRelationKind;
import com.example.editor.metamodel.core.MetaTypeReference;
import com.example.editor.metamodel.core.MetaTypeReferenceKind;
import com.example.editor.metamodel.core.Multiplicity;

/** Comprueba referencias y tipos sin ejecutar las expresiones. */
@Service
public class ExpressionTypeChecker {

    private final ExpressionDependencyAnalyzer dependencyAnalyzer;

    /** Descripción: Inicializa una instancia de ExpressionTypeChecker con sus dependencias y datos de entrada. Entradas: dependencyAnalyzer: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public ExpressionTypeChecker(ExpressionDependencyAnalyzer dependencyAnalyzer) {
        this.dependencyAnalyzer = dependencyAnalyzer;
    }

    /** Descripción: Valida un conjunto neutral de expresiones contra un metamodelo. Entradas: ownerId: identificador del artefacto propietario; declaredMetamodelId: metamodelo declarado; expressions: expresiones a comprobar; metamodel: metamodelo canónico. Salida: diagnóstico agregado y resultados individuales. */ public ExpressionBatchCheckResult checkBatch(String ownerId, String declaredMetamodelId, List<ExpressionDefinition> expressions, MetaModel metamodel) {
        List<ExpressionDiagnostic> global = new ArrayList<>();
        List<ExpressionCheckResult> results = new ArrayList<>();
        String normalizedOwnerId = ownerId == null ? "" : ownerId;
        List<ExpressionDefinition> safeExpressions = expressions == null ? List.of() : expressions;
        if(metamodel == null) {
            global.add(error("METAMODEL_REQUIRED", "", "", "$", "Metamodel must not be null"));
            return new ExpressionBatchCheckResult(normalizedOwnerId, global, results);
        }
        if(declaredMetamodelId != null && !declaredMetamodelId.isBlank() && !declaredMetamodelId.equals(metamodel.id())) {
            global.add(error("METAMODEL_ID_MISMATCH", "", "", "$.metamodelId", "Expression owner metamodelId does not match the canonical metamodel"));
        }

        MetaIndex index = new MetaIndex(metamodel);
        Set<String> expressionIds = new HashSet<>();
        for(int i = 0; i < safeExpressions.size(); i++) {
            ExpressionDefinition expression = safeExpressions.get(i);
            if(expression == null) {
                global.add(error("EXPRESSION_REQUIRED", "", "", "$.expressions[" + i + "]", "Expression must not be null"));
                continue;
            }
            if(!expressionIds.add(expression.id())) {
                global.add(error("DUPLICATE_EXPRESSION_ID", expression.id(), "", "$.expressions[" + i + "].id", "Expression id is duplicated: " + expression.id()));
            }
            results.add(check(expression, index));
        }
        return new ExpressionBatchCheckResult(normalizedOwnerId, global, results);
    }

    /** Descripción: Valida las condiciones necesarias para «check» y rechaza entradas no válidas. Entradas: expression: dato de entrada necesario para la operación; metamodel: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ExpressionCheckResult check(ExpressionDefinition expression, MetaModel metamodel) {
        return check(expression, new MetaIndex(metamodel));
    }

    /** Descripción: Valida las condiciones necesarias para «check» y rechaza entradas no válidas. Entradas: expression: dato de entrada necesario para la operación; index: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private ExpressionCheckResult check(ExpressionDefinition expression, MetaIndex index) {
        List<ExpressionDiagnostic> diagnostics = new ArrayList<>();
        Set<String> nodeIds = new HashSet<>();
        ExpressionValueType selfType = ExpressionValueType.scalar(ExpressionValueKind.ANY);
        if(!expression.contextClassId().isBlank()) {
            if(index.classes.containsKey(expression.contextClassId())) {
                selfType = ExpressionValueType.object(expression.contextClassId());
            } else {
                diagnostics.add(error("UNKNOWN_CONTEXT_CLASS", expression.id(), "", "$.contextClassId", "Context class does not exist: " + expression.contextClassId()));
            }
        }
        ExpressionValueType inferred = infer(expression.id(), expression.root(), selfType, index, diagnostics, nodeIds, "$.root");
        if(expression.declaredReturnType() != null && !assignable(expression.declaredReturnType(), inferred, index)) {
            diagnostics.add(error("DECLARED_RETURN_TYPE_MISMATCH", expression.id(), expression.root().nodeId(), "$.declaredReturnType", "Declared return type " + expression.declaredReturnType().kind() + " is not compatible with inferred type " + inferred.kind()));
        }
        return new ExpressionCheckResult(expression.id(), inferred, diagnostics, dependencyAnalyzer.analyze(expression));
    }

    /** Descripción: Ejecuta la operación «infer» correspondiente a la responsabilidad de MetaIndex. Entradas: expressionId: identificador que delimita el recurso objetivo; node: dato de entrada necesario para la operación; selfType: dato de entrada necesario para la operación; index: dato de entrada necesario para la operación; diagnostics: dato de entrada necesario para la operación; nodeIds: dato de entrada necesario para la operación; path: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private ExpressionValueType infer(String expressionId, ExpressionNode node, ExpressionValueType selfType, MetaIndex index, List<ExpressionDiagnostic> diagnostics, Set<String> nodeIds, String path) {
        if(!nodeIds.add(node.nodeId())) {
            diagnostics.add(error("DUPLICATE_NODE_ID", expressionId, node.nodeId(), path + ".nodeId", "Node id is duplicated inside the expression: " + node.nodeId()));
        }
        return switch(node) {
            case LiteralExpressionNode literal -> inferLiteral(expressionId, literal, diagnostics, path);
            case SelfExpressionNode ignored -> selfType;
            case ModelExpressionNode ignored -> ExpressionValueType.scalar(ExpressionValueKind.MODEL);
            case ClassInstancesExpressionNode classInstances -> {
                if(!index.classes.containsKey(classInstances.classId())) {
                    diagnostics.add(error("UNKNOWN_CLASS", expressionId, node.nodeId(), path + ".classId", "Class does not exist: " + classInstances.classId()));
                }
                yield ExpressionValueType.collection(ExpressionValueType.object(classInstances.classId()));
            }
            case AttributeAccessExpressionNode access -> inferAttribute(expressionId, access, selfType, index, diagnostics, nodeIds, path);
            case RelationNavigationExpressionNode navigation -> inferRelation(expressionId, navigation, selfType, index, diagnostics, nodeIds, path);
            case UnaryExpressionNode unary -> inferUnary(expressionId, unary, selfType, index, diagnostics, nodeIds, path);
            case BinaryExpressionNode binary -> inferBinary(expressionId, binary, selfType, index, diagnostics, nodeIds, path);
            case CollectionExpressionNode collection -> inferCollection(expressionId, collection, selfType, index, diagnostics, nodeIds, path);
            case ConditionalExpressionNode conditional -> inferConditional(expressionId, conditional, selfType, index, diagnostics, nodeIds, path);
            case FunctionExpressionNode function -> inferFunction(expressionId, function, selfType, index, diagnostics, nodeIds, path);
            case VariableExpressionNode ignored -> ExpressionValueType.scalar(ExpressionValueKind.ANY);
            case OperationCallExpressionNode operationCall -> inferOperationCall(expressionId, operationCall, selfType, index, diagnostics, nodeIds, path);
        };
    }

    /** Descripción: Ejecuta la operación «inferLiteral» correspondiente a la responsabilidad de MetaIndex. Entradas: expressionId: identificador que delimita el recurso objetivo; literal: dato de entrada necesario para la operación; diagnostics: dato de entrada necesario para la operación; path: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private ExpressionValueType inferLiteral(String expressionId, LiteralExpressionNode literal, List<ExpressionDiagnostic> diagnostics, String path) {
        ExpressionValueType type = literal.valueType();
        Object value = literal.value();
        boolean valid = switch(type.kind()) {
            case NULL -> value == null;
            case BOOLEAN -> value instanceof Boolean;
            case INTEGER -> value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long || value instanceof java.math.BigInteger;
            case DECIMAL -> value instanceof Number;
            case STRING, DATE, DATETIME, ENUM -> value instanceof String;
            case BINARY -> value instanceof String || value instanceof byte[];
            case COLLECTION -> value instanceof List<?>;
            case ANY, OBJECT, MODEL -> true;
        };
        if(!valid) diagnostics.add(error("INVALID_LITERAL_VALUE", expressionId, literal.nodeId(), path + ".value", "Literal value is not compatible with " + type.kind()));
        return type;
    }

    /** Descripción: Ejecuta la operación «inferAttribute» correspondiente a la responsabilidad de MetaIndex. Entradas: expressionId: identificador que delimita el recurso objetivo; access: dato de entrada necesario para la operación; selfType: dato de entrada necesario para la operación; index: dato de entrada necesario para la operación; diagnostics: dato de entrada necesario para la operación; nodeIds: dato de entrada necesario para la operación; path: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private ExpressionValueType inferAttribute(String expressionId, AttributeAccessExpressionNode access, ExpressionValueType selfType, MetaIndex index, List<ExpressionDiagnostic> diagnostics, Set<String> nodeIds, String path) {
        ExpressionValueType target = infer(expressionId, access.target(), selfType, index, diagnostics, nodeIds, path + ".target");
        MetaAttribute attribute = index.attributes.get(access.attributeId());
        String ownerId = index.attributeOwners.get(access.attributeId());
        if(attribute == null) {
            diagnostics.add(error("UNKNOWN_ATTRIBUTE", expressionId, access.nodeId(), path + ".attributeId", "Attribute does not exist: " + access.attributeId()));
            return ExpressionValueType.scalar(ExpressionValueKind.ANY);
        }
        ExpressionValueType valueType = index.attributeType(attribute);
        if(target.isCollection()) {
            ExpressionValueType element = target.elementType();
            if(element != null && element.kind() == ExpressionValueKind.OBJECT && !index.isAssignableClass(ownerId, element.classId())) {
                diagnostics.add(error("ATTRIBUTE_TARGET_TYPE_MISMATCH", expressionId, access.nodeId(), path + ".target", "Attribute " + access.attributeId() + " does not belong to class " + element.classId()));
            }
            return ExpressionValueType.collection(valueType);
        }
        if(target.kind() != ExpressionValueKind.OBJECT && target.kind() != ExpressionValueKind.ANY) {
            diagnostics.add(error("ATTRIBUTE_TARGET_NOT_OBJECT", expressionId, access.nodeId(), path + ".target", "Attribute access requires an object target"));
        } else if(target.kind() == ExpressionValueKind.OBJECT && !index.isAssignableClass(ownerId, target.classId())) {
            diagnostics.add(error("ATTRIBUTE_TARGET_TYPE_MISMATCH", expressionId, access.nodeId(), path + ".target", "Attribute " + access.attributeId() + " does not belong to class " + target.classId()));
        }
        return valueType;
    }

    /** Descripción: Ejecuta la operación «inferRelation» correspondiente a la responsabilidad de MetaIndex. Entradas: expressionId: identificador que delimita el recurso objetivo; navigation: dato de entrada necesario para la operación; selfType: dato de entrada necesario para la operación; index: dato de entrada necesario para la operación; diagnostics: dato de entrada necesario para la operación; nodeIds: dato de entrada necesario para la operación; path: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private ExpressionValueType inferRelation(String expressionId, RelationNavigationExpressionNode navigation, ExpressionValueType selfType, MetaIndex index, List<ExpressionDiagnostic> diagnostics, Set<String> nodeIds, String path) {
        ExpressionValueType target = infer(expressionId, navigation.target(), selfType, index, diagnostics, nodeIds, path + ".target");
        MetaRelation relation = index.relations.get(navigation.relationId());
        if(relation == null) {
            diagnostics.add(error("UNKNOWN_RELATION", expressionId, navigation.nodeId(), path + ".relationId", "Relation does not exist: " + navigation.relationId()));
            return ExpressionValueType.scalar(ExpressionValueKind.ANY);
        }
        ExpressionValueType source = target.isCollection() ? target.elementType() : target;
        if(source != null && source.kind() == ExpressionValueKind.OBJECT && !index.isAssignableClass(relation.sourceTypeId(), source.classId())) {
            diagnostics.add(error("RELATION_SOURCE_TYPE_MISMATCH", expressionId, navigation.nodeId(), path + ".target", "Relation " + relation.id() + " cannot be navigated from class " + source.classId()));
        } else if(source != null && source.kind() != ExpressionValueKind.OBJECT && source.kind() != ExpressionValueKind.ANY) {
            diagnostics.add(error("RELATION_TARGET_NOT_OBJECT", expressionId, navigation.nodeId(), path + ".target", "Relation navigation requires an object target"));
        }
        ExpressionValueType result = ExpressionValueType.object(relation.targetTypeId());
        boolean many = isMany(relation.targetMultiplicity());
        if(target.isCollection() || many) return ExpressionValueType.collection(result);
        return result;
    }

    /** Descripción: Ejecuta la operación «inferUnary» correspondiente a la responsabilidad de MetaIndex. Entradas: expressionId: identificador que delimita el recurso objetivo; unary: dato de entrada necesario para la operación; selfType: dato de entrada necesario para la operación; index: dato de entrada necesario para la operación; diagnostics: dato de entrada necesario para la operación; nodeIds: dato de entrada necesario para la operación; path: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private ExpressionValueType inferUnary(String expressionId, UnaryExpressionNode unary, ExpressionValueType selfType, MetaIndex index, List<ExpressionDiagnostic> diagnostics, Set<String> nodeIds, String path) {
        ExpressionValueType operand = infer(expressionId, unary.operand(), selfType, index, diagnostics, nodeIds, path + ".operand");
        return switch(unary.operator()) {
            case NOT -> require(expressionId, unary.nodeId(), path, diagnostics, operand.kind() == ExpressionValueKind.BOOLEAN || operand.kind() == ExpressionValueKind.ANY, "UNARY_BOOLEAN_REQUIRED", "NOT requires a boolean", ExpressionValueType.scalar(ExpressionValueKind.BOOLEAN));
            case IS_NULL, IS_NOT_NULL, IS_EMPTY, IS_NOT_EMPTY -> ExpressionValueType.scalar(ExpressionValueKind.BOOLEAN);
            case NEGATE -> require(expressionId, unary.nodeId(), path, diagnostics, operand.isNumeric() || operand.kind() == ExpressionValueKind.ANY, "UNARY_NUMERIC_REQUIRED", "NEGATE requires a numeric value", operand);
            case SIZE -> require(expressionId, unary.nodeId(), path, diagnostics, operand.isCollection() || operand.kind() == ExpressionValueKind.STRING || operand.kind() == ExpressionValueKind.ANY, "SIZE_TARGET_INVALID", "SIZE requires a string or collection", ExpressionValueType.scalar(ExpressionValueKind.INTEGER));
            case TO_UPPER, TO_LOWER -> require(expressionId, unary.nodeId(), path, diagnostics, operand.kind() == ExpressionValueKind.STRING || operand.kind() == ExpressionValueKind.ANY, "STRING_REQUIRED", unary.operator() + " requires a string", ExpressionValueType.scalar(ExpressionValueKind.STRING));
        };
    }

    /** Descripción: Ejecuta la operación «inferBinary» correspondiente a la responsabilidad de MetaIndex. Entradas: expressionId: identificador que delimita el recurso objetivo; binary: dato de entrada necesario para la operación; selfType: dato de entrada necesario para la operación; index: dato de entrada necesario para la operación; diagnostics: dato de entrada necesario para la operación; nodeIds: dato de entrada necesario para la operación; path: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private ExpressionValueType inferBinary(String expressionId, BinaryExpressionNode binary, ExpressionValueType selfType, MetaIndex index, List<ExpressionDiagnostic> diagnostics, Set<String> nodeIds, String path) {
        ExpressionValueType left = infer(expressionId, binary.left(), selfType, index, diagnostics, nodeIds, path + ".left");
        ExpressionValueType right = infer(expressionId, binary.right(), selfType, index, diagnostics, nodeIds, path + ".right");
        return switch(binary.operator()) {
            case AND, OR -> require(expressionId, binary.nodeId(), path, diagnostics, booleanLike(left) && booleanLike(right), "BOOLEAN_OPERANDS_REQUIRED", binary.operator() + " requires boolean operands", ExpressionValueType.scalar(ExpressionValueKind.BOOLEAN));
            case EQUALS, NOT_EQUALS -> {
                if(!comparable(left, right, index)) diagnostics.add(error("INCOMPATIBLE_EQUALITY_OPERANDS", expressionId, binary.nodeId(), path, "Equality operands are incompatible: " + left.kind() + " and " + right.kind()));
                yield ExpressionValueType.scalar(ExpressionValueKind.BOOLEAN);
            }
            case GREATER_THAN, GREATER_OR_EQUAL, LESS_THAN, LESS_OR_EQUAL -> require(expressionId, binary.nodeId(), path, diagnostics, orderedComparable(left, right), "ORDERED_OPERANDS_REQUIRED", binary.operator() + " requires compatible numeric, string or date operands", ExpressionValueType.scalar(ExpressionValueKind.BOOLEAN));
            case ADD -> {
                if(left.kind() == ExpressionValueKind.STRING && right.kind() == ExpressionValueKind.STRING) yield ExpressionValueType.scalar(ExpressionValueKind.STRING);
                /** Descripción: Valida las condiciones necesarias para «require» y rechaza entradas no válidas. Entradas: expressionId: identificador que delimita el recurso objetivo; nodeId: identificador que delimita el recurso objetivo; path: ruta o ubicación utilizada por la operación; diagnostics: dato de entrada necesario para la operación; isNumeric: dato de entrada necesario para la operación; NUMERIC_OPERANDS_REQUIRED: dato de entrada necesario para la operación; strings: dato de entrada necesario para la operación; right: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ yield require(expressionId, binary.nodeId(), path, diagnostics, left.isNumeric() && right.isNumeric(), "NUMERIC_OPERANDS_REQUIRED", "ADD requires numeric operands or two strings", numericResult(left, right));
            }
            case SUBTRACT, MULTIPLY, DIVIDE -> require(expressionId, binary.nodeId(), path, diagnostics, left.isNumeric() && right.isNumeric(), "NUMERIC_OPERANDS_REQUIRED", binary.operator() + " requires numeric operands", numericResult(left, right));
            case CONTAINS -> require(expressionId, binary.nodeId(), path, diagnostics, (left.kind() == ExpressionValueKind.STRING && right.kind() == ExpressionValueKind.STRING) || left.isCollection() || left.kind() == ExpressionValueKind.ANY, "CONTAINS_TARGET_INVALID", "CONTAINS requires a string or collection on the left", ExpressionValueType.scalar(ExpressionValueKind.BOOLEAN));
            case STARTS_WITH, ENDS_WITH, MATCHES -> require(expressionId, binary.nodeId(), path, diagnostics, stringLike(left) && stringLike(right), "STRING_OPERANDS_REQUIRED", binary.operator() + " requires string operands", ExpressionValueType.scalar(ExpressionValueKind.BOOLEAN));
        };
    }

    /** Descripción: Ejecuta la operación «inferCollection» correspondiente a la responsabilidad de MetaIndex. Entradas: expressionId: identificador que delimita el recurso objetivo; collection: dato de entrada necesario para la operación; selfType: dato de entrada necesario para la operación; index: dato de entrada necesario para la operación; diagnostics: dato de entrada necesario para la operación; nodeIds: dato de entrada necesario para la operación; path: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private ExpressionValueType inferCollection(String expressionId, CollectionExpressionNode collection, ExpressionValueType selfType, MetaIndex index, List<ExpressionDiagnostic> diagnostics, Set<String> nodeIds, String path) {
        ExpressionValueType source = infer(expressionId, collection.source(), selfType, index, diagnostics, nodeIds, path + ".source");
        if(!source.isCollection() && source.kind() != ExpressionValueKind.ANY) {
            diagnostics.add(error("COLLECTION_SOURCE_REQUIRED", expressionId, collection.nodeId(), path + ".source", collection.operator() + " requires a collection source"));
        }
        ExpressionValueType elementType = source.elementType() == null ? ExpressionValueType.scalar(ExpressionValueKind.ANY) : source.elementType();
        ExpressionValueType nested = collection.expression() == null ? elementType : infer(expressionId, collection.expression(), elementType, index, diagnostics, nodeIds, path + ".expression");
        return switch(collection.operator()) {
            case EXISTS, FOR_ALL, NONE -> require(expressionId, collection.nodeId(), path, diagnostics, booleanLike(nested), "COLLECTION_PREDICATE_BOOLEAN_REQUIRED", collection.operator() + " requires a boolean predicate", ExpressionValueType.scalar(ExpressionValueKind.BOOLEAN));
            case IS_UNIQUE -> ExpressionValueType.scalar(ExpressionValueKind.BOOLEAN);
            case SELECT -> require(expressionId, collection.nodeId(), path, diagnostics, booleanLike(nested), "COLLECTION_PREDICATE_BOOLEAN_REQUIRED", "SELECT requires a boolean predicate", source);
            case COUNT -> ExpressionValueType.scalar(ExpressionValueKind.INTEGER);
            case SUM -> require(expressionId, collection.nodeId(), path, diagnostics, nested.isNumeric() || nested.kind() == ExpressionValueKind.ANY, "COLLECTION_NUMERIC_REQUIRED", "SUM requires numeric values", nested.kind() == ExpressionValueKind.ANY ? ExpressionValueType.scalar(ExpressionValueKind.DECIMAL) : nested);
            case MIN, MAX -> nested;
        };
    }

    /** Descripción: Ejecuta la operación «inferConditional» correspondiente a la responsabilidad de MetaIndex. Entradas: expressionId: identificador que delimita el recurso objetivo; conditional: dato de entrada necesario para la operación; selfType: dato de entrada necesario para la operación; index: dato de entrada necesario para la operación; diagnostics: dato de entrada necesario para la operación; nodeIds: dato de entrada necesario para la operación; path: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private ExpressionValueType inferConditional(String expressionId, ConditionalExpressionNode conditional, ExpressionValueType selfType, MetaIndex index, List<ExpressionDiagnostic> diagnostics, Set<String> nodeIds, String path) {
        ExpressionValueType condition = infer(expressionId, conditional.condition(), selfType, index, diagnostics, nodeIds, path + ".condition");
        if(!booleanLike(condition)) diagnostics.add(error("CONDITION_BOOLEAN_REQUIRED", expressionId, conditional.nodeId(), path + ".condition", "Conditional expression requires a boolean condition"));
        ExpressionValueType whenTrue = infer(expressionId, conditional.whenTrue(), selfType, index, diagnostics, nodeIds, path + ".whenTrue");
        ExpressionValueType whenFalse = infer(expressionId, conditional.whenFalse(), selfType, index, diagnostics, nodeIds, path + ".whenFalse");
        if(comparable(whenTrue, whenFalse, index)) return merge(whenTrue, whenFalse);
        diagnostics.add(error("CONDITIONAL_BRANCH_TYPE_MISMATCH", expressionId, conditional.nodeId(), path, "Conditional branches have incompatible types"));
        return ExpressionValueType.scalar(ExpressionValueKind.ANY);
    }

    /** Descripción: Ejecuta la operación «inferOperationCall» correspondiente a la responsabilidad de MetaIndex. Entradas: expressionId: identificador que delimita el recurso objetivo; call: dato de entrada necesario para la operación; selfType: dato de entrada necesario para la operación; index: dato de entrada necesario para la operación; diagnostics: dato de entrada necesario para la operación; nodeIds: dato de entrada necesario para la operación; path: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private ExpressionValueType inferOperationCall(String expressionId, OperationCallExpressionNode call, ExpressionValueType selfType, MetaIndex index, List<ExpressionDiagnostic> diagnostics, Set<String> nodeIds, String path) {
        ExpressionValueType target = infer(expressionId, call.target(), selfType, index, diagnostics, nodeIds, path + ".target");
        MetaOperation operation = index.operations.get(call.operationId());
        String ownerId = index.operationOwners.get(call.operationId());
        if(operation == null) {
            diagnostics.add(error("UNKNOWN_OPERATION", expressionId, call.nodeId(), path + ".operationId", "Operation does not exist: " + call.operationId()));
            call.arguments().forEach(argument -> infer(expressionId, argument, selfType, index, diagnostics, nodeIds, path + ".arguments"));
            return ExpressionValueType.scalar(ExpressionValueKind.ANY);
        }
        if(operation.behaviorKind() == MetaOperationBehaviorKind.COMMAND) {
            diagnostics.add(error("COMMAND_OPERATION_NOT_ALLOWED_IN_EXPRESSION", expressionId, call.nodeId(), path + ".operationId", "COMMAND operations cannot be used inside validation expressions: " + operation.name()));
        }
        ExpressionValueType targetElement = target.isCollection() ? target.elementType() : target;
        if(targetElement != null && targetElement.kind() == ExpressionValueKind.OBJECT) {
            if(!index.isAssignableClass(ownerId, targetElement.classId())) {
                diagnostics.add(error("OPERATION_TARGET_TYPE_MISMATCH", expressionId, call.nodeId(), path + ".target", "Operation " + operation.name() + " does not belong to class " + targetElement.classId()));
            }
        } else if(targetElement != null && targetElement.kind() != ExpressionValueKind.ANY) {
            diagnostics.add(error("OPERATION_TARGET_NOT_OBJECT", expressionId, call.nodeId(), path + ".target", "Operation calls require an object target"));
        }
        if(call.arguments().size() != operation.parameters().size()) {
            diagnostics.add(error("OPERATION_ARGUMENT_COUNT_MISMATCH", expressionId, call.nodeId(), path + ".arguments", "Operation " + operation.name() + " expects " + operation.parameters().size() + " argument(s)"));
        }
        int count = Math.max(call.arguments().size(), operation.parameters().size());
        for(int i = 0; i < count; i++) {
            if(i >= call.arguments().size()) continue;
            ExpressionValueType actual = infer(expressionId, call.arguments().get(i), selfType, index, diagnostics, nodeIds, path + ".arguments[" + i + "]");
            if(i < operation.parameters().size()) {
                ExpressionValueType expected = index.parameterType(operation.parameters().get(i));
                if(!assignable(expected, actual, index)) {
                    diagnostics.add(error("OPERATION_ARGUMENT_TYPE_MISMATCH", expressionId, call.nodeId(), path + ".arguments[" + i + "]", "Argument " + (i + 1) + " is not compatible with parameter " + operation.parameters().get(i).name()));
                }
            }
        }
        if(operation.returnsVoid()) {
            diagnostics.add(error("VOID_OPERATION_IN_EXPRESSION", expressionId, call.nodeId(), path + ".operationId", "An operation used in an expression must declare a return type"));
            return ExpressionValueType.scalar(ExpressionValueKind.ANY);
        }
        ExpressionValueType result = index.operationReturnType(operation);
        return target.isCollection() ? ExpressionValueType.collection(result) : result;
    }

    /** Descripción: Ejecuta la operación «inferFunction» correspondiente a la responsabilidad de MetaIndex. Entradas: expressionId: identificador que delimita el recurso objetivo; function: dato de entrada necesario para la operación; selfType: dato de entrada necesario para la operación; index: dato de entrada necesario para la operación; diagnostics: dato de entrada necesario para la operación; nodeIds: dato de entrada necesario para la operación; path: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private ExpressionValueType inferFunction(String expressionId, FunctionExpressionNode function, ExpressionValueType selfType, MetaIndex index, List<ExpressionDiagnostic> diagnostics, Set<String> nodeIds, String path) {
        List<ExpressionValueType> arguments = new ArrayList<>();
        for(int i = 0; i < function.arguments().size(); i++) arguments.add(infer(expressionId, function.arguments().get(i), selfType, index, diagnostics, nodeIds, path + ".arguments[" + i + "]"));
        return switch(function.functionName()) {
            case "length" -> {
                functionArity(expressionId, function, diagnostics, path, arguments, 1, ExpressionValueType.scalar(ExpressionValueKind.INTEGER));
                if(arguments.size() == 1 && !stringOrCollection(arguments.getFirst())) {
                    diagnostics.add(error("FUNCTION_ARGUMENT_TYPE_MISMATCH", expressionId, function.nodeId(), path + ".arguments[0]", "Function length expects a string or collection"));
                }
                yield ExpressionValueType.scalar(ExpressionValueKind.INTEGER);
            }
            case "lower", "upper", "trim" -> {
                functionArity(expressionId, function, diagnostics, path, arguments, 1, ExpressionValueType.scalar(ExpressionValueKind.STRING));
                if(arguments.size() == 1 && !stringLike(arguments.getFirst())) {
                    diagnostics.add(error("FUNCTION_ARGUMENT_TYPE_MISMATCH", expressionId, function.nodeId(), path + ".arguments[0]", "Function " + function.functionName() + " expects a string"));
                }
                yield ExpressionValueType.scalar(ExpressionValueKind.STRING);
            }
            case "abs" -> {
                functionArity(expressionId, function, diagnostics, path, arguments, 1, ExpressionValueType.scalar(ExpressionValueKind.DECIMAL));
                if(arguments.size() == 1 && !arguments.getFirst().isNumeric() && arguments.getFirst().kind() != ExpressionValueKind.ANY) {
                    diagnostics.add(error("FUNCTION_ARGUMENT_TYPE_MISMATCH", expressionId, function.nodeId(), path + ".arguments[0]", "Function abs expects a numeric value"));
                }
                yield arguments.isEmpty() ? ExpressionValueType.scalar(ExpressionValueKind.DECIMAL) : arguments.getFirst();
            }
            case "coalesce" -> {
                if(arguments.isEmpty()) {
                    diagnostics.add(error("FUNCTION_ARITY_MISMATCH", expressionId, function.nodeId(), path + ".arguments", "Function coalesce expects at least one argument"));
                    yield ExpressionValueType.scalar(ExpressionValueKind.ANY);
                }
                ExpressionValueType result = arguments.getFirst();
                for(int i = 1; i < arguments.size(); i++) {
                    if(!comparable(result, arguments.get(i), index)) {
                        diagnostics.add(error("FUNCTION_ARGUMENT_TYPE_MISMATCH", expressionId, function.nodeId(), path + ".arguments[" + i + "]", "Function coalesce requires compatible argument types"));
                        result = ExpressionValueType.scalar(ExpressionValueKind.ANY);
                        break;
                    }
                    result = merge(result, arguments.get(i));
                }
                yield result;
            }
            default -> {
                diagnostics.add(error("UNKNOWN_FUNCTION", expressionId, function.nodeId(), path + ".functionName", "Function is not registered: " + function.functionName()));
                yield ExpressionValueType.scalar(ExpressionValueKind.ANY);
            }
        };
    }

    /** Descripción: Ejecuta la operación «functionArity» correspondiente a la responsabilidad de MetaIndex. Entradas: expressionId: identificador que delimita el recurso objetivo; function: dato de entrada necesario para la operación; diagnostics: dato de entrada necesario para la operación; path: ruta o ubicación utilizada por la operación; arguments: dato de entrada necesario para la operación; expected: dato de entrada necesario para la operación; result: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private ExpressionValueType functionArity(String expressionId, FunctionExpressionNode function, List<ExpressionDiagnostic> diagnostics, String path, List<ExpressionValueType> arguments, int expected, ExpressionValueType result) {
        if(arguments.size() != expected) diagnostics.add(error("FUNCTION_ARITY_MISMATCH", expressionId, function.nodeId(), path + ".arguments", "Function " + function.functionName() + " expects " + expected + " argument(s)"));
        return result;
    }

    /** Descripción: Valida las condiciones necesarias para «require» y rechaza entradas no válidas. Entradas: expressionId: identificador que delimita el recurso objetivo; nodeId: identificador que delimita el recurso objetivo; path: ruta o ubicación utilizada por la operación; diagnostics: dato de entrada necesario para la operación; condition: dato de entrada necesario para la operación; code: dato de entrada necesario para la operación; message: dato de entrada necesario para la operación; result: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private ExpressionValueType require(String expressionId, String nodeId, String path, List<ExpressionDiagnostic> diagnostics, boolean condition, String code, String message, ExpressionValueType result) {
        if(!condition) diagnostics.add(error(code, expressionId, nodeId, path, message));
        return result;
    }

    /** Descripción: Ejecuta la operación «booleanLike» correspondiente a la responsabilidad de MetaIndex. Entradas: type: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean booleanLike(ExpressionValueType type) {
        return type.kind() == ExpressionValueKind.BOOLEAN || type.kind() == ExpressionValueKind.ANY;
    }

    /** Descripción: Ejecuta la operación «stringLike» correspondiente a la responsabilidad de MetaIndex. Entradas: type: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean stringLike(ExpressionValueType type) {
        return type.kind() == ExpressionValueKind.STRING || type.kind() == ExpressionValueKind.ANY;
    }

    /** Descripción: Ejecuta la operación «stringOrCollection» correspondiente a la responsabilidad de MetaIndex. Entradas: type: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean stringOrCollection(ExpressionValueType type) {
        return stringLike(type) || type.isCollection();
    }

    /** Descripción: Ejecuta la operación «orderedComparable» correspondiente a la responsabilidad de MetaIndex. Entradas: left: dato de entrada necesario para la operación; right: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean orderedComparable(ExpressionValueType left, ExpressionValueType right) {
        if(left.kind() == ExpressionValueKind.ANY || right.kind() == ExpressionValueKind.ANY) return true;
        if(left.isNumeric() && right.isNumeric()) return true;
        return left.kind() == right.kind() && Set.of(ExpressionValueKind.STRING, ExpressionValueKind.DATE, ExpressionValueKind.DATETIME).contains(left.kind());
    }

    /** Descripción: Ejecuta la operación «comparable» correspondiente a la responsabilidad de MetaIndex. Entradas: left: dato de entrada necesario para la operación; right: dato de entrada necesario para la operación; index: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean comparable(ExpressionValueType left, ExpressionValueType right, MetaIndex index) {
        if(left.kind() == ExpressionValueKind.ANY || right.kind() == ExpressionValueKind.ANY || left.kind() == ExpressionValueKind.NULL || right.kind() == ExpressionValueKind.NULL) return true;
        if(left.isNumeric() && right.isNumeric()) return true;
        if(left.kind() != right.kind()) return false;
        if(left.kind() == ExpressionValueKind.OBJECT) return index.isAssignableClass(left.classId(), right.classId()) || index.isAssignableClass(right.classId(), left.classId());
        if(left.kind() == ExpressionValueKind.ENUM) return left.enumId().equals(right.enumId());
        if(left.kind() == ExpressionValueKind.COLLECTION) return comparable(left.elementType(), right.elementType(), index);
        return true;
    }

    /** Descripción: Ejecuta la operación «assignable» correspondiente a la responsabilidad de MetaIndex. Entradas: expected: dato de entrada necesario para la operación; actual: dato de entrada necesario para la operación; index: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean assignable(ExpressionValueType expected, ExpressionValueType actual, MetaIndex index) {
        if(expected == null || expected.kind() == ExpressionValueKind.ANY || actual.kind() == ExpressionValueKind.ANY) return true;
        if(expected.isNumeric() && actual.isNumeric()) return true;
        if(expected.kind() != actual.kind()) return false;
        if(expected.kind() == ExpressionValueKind.OBJECT) return index.isAssignableClass(expected.classId(), actual.classId());
        if(expected.kind() == ExpressionValueKind.ENUM) return expected.enumId().equals(actual.enumId());
        if(expected.kind() == ExpressionValueKind.COLLECTION) return assignable(expected.elementType(), actual.elementType(), index);
        return true;
    }

    /** Descripción: Actualiza el estado gestionado por MetaIndex mediante la operación «merge». Entradas: left: dato de entrada necesario para la operación; right: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private ExpressionValueType merge(ExpressionValueType left, ExpressionValueType right) {
        if(left.kind() == ExpressionValueKind.NULL) return right;
        if(right.kind() == ExpressionValueKind.NULL) return left;
        if(left.isNumeric() && right.isNumeric()) return numericResult(left, right);
        return left;
    }

    /** Descripción: Ejecuta la operación «numericResult» correspondiente a la responsabilidad de MetaIndex. Entradas: left: dato de entrada necesario para la operación; right: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private ExpressionValueType numericResult(ExpressionValueType left, ExpressionValueType right) {
        return left.kind() == ExpressionValueKind.DECIMAL || right.kind() == ExpressionValueKind.DECIMAL ? ExpressionValueType.scalar(ExpressionValueKind.DECIMAL) : ExpressionValueType.scalar(ExpressionValueKind.INTEGER);
    }

    /** Descripción: Evalúa la condición «isMany» sin modificar el estado externo. Entradas: multiplicity: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean isMany(Multiplicity multiplicity) {
        return multiplicity != null && (multiplicity.upperBound() == null || multiplicity.upperBound() > 1);
    }

    /** Descripción: Ejecuta la operación «error» correspondiente a la responsabilidad de MetaIndex. Entradas: code: dato de entrada necesario para la operación; expressionId: identificador que delimita el recurso objetivo; nodeId: identificador que delimita el recurso objetivo; path: ruta o ubicación utilizada por la operación; message: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private ExpressionDiagnostic error(String code, String expressionId, String nodeId, String path, String message) {
        return new ExpressionDiagnostic(ExpressionDiagnosticSeverity.ERROR, code, expressionId, nodeId, path, message);
    }

    private static final class MetaIndex {
        private final Map<String, MetaClass> classes = new HashMap<>();
        private final Map<String, MetaAttribute> attributes = new HashMap<>();
        private final Map<String, String> attributeOwners = new HashMap<>();
        private final Map<String, MetaRelation> relations = new HashMap<>();
        private final Map<String, MetaOperation> operations = new HashMap<>();
        private final Map<String, String> operationOwners = new HashMap<>();
        private final Map<String, Set<String>> directParents = new HashMap<>();

        /** Descripción: Inicializa una instancia de MetaIndex con sus dependencias y datos de entrada. Entradas: metamodel: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ private MetaIndex(MetaModel metamodel) {
            if(metamodel == null) return;
            for(MetaClass metaClass : metamodel.classes()) {
                classes.put(metaClass.id(), metaClass);
                for(MetaAttribute attribute : metaClass.attributes()) {
                    attributes.put(attribute.id(), attribute);
                    attributeOwners.put(attribute.id(), metaClass.id());
                }
                for(MetaOperation operation : metaClass.operations()) {
                    operations.put(operation.id(), operation);
                    operationOwners.put(operation.id(), metaClass.id());
                }
            }
            for(MetaRelation relation : metamodel.relations()) {
                relations.put(relation.id(), relation);
                if(relation.kind() == MetaRelationKind.INHERITANCE || relation.kind() == MetaRelationKind.IMPLEMENTATION) {
                    directParents.computeIfAbsent(relation.sourceTypeId(), ignored -> new LinkedHashSet<>()).add(relation.targetTypeId());
                }
            }
        }

        /** Descripción: Evalúa la condición «isAssignableClass» sin modificar el estado externo. Entradas: expectedParent: dato de entrada necesario para la operación; actualChild: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean isAssignableClass(String expectedParent, String actualChild) {
            if(expectedParent == null || actualChild == null || expectedParent.isBlank() || actualChild.isBlank()) return false;
            if(expectedParent.equals(actualChild)) return true;
            Set<String> visited = new HashSet<>();
            List<String> pending = new ArrayList<>();
            pending.add(actualChild);
            while(!pending.isEmpty()) {
                String current = pending.removeLast();
                if(!visited.add(current)) continue;
                for(String parent : directParents.getOrDefault(current, Set.of())) {
                    if(expectedParent.equals(parent)) return true;
                    pending.add(parent);
                }
            }
            return false;
        }

        /** Descripción: Ejecuta la operación «attributeType» correspondiente a la responsabilidad de MetaIndex. Entradas: attribute: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private ExpressionValueType attributeType(MetaAttribute attribute) {
            ExpressionValueType scalar = referenceType(attribute.type());
            Multiplicity multiplicity = attribute.multiplicity();
            if(multiplicity != null && (multiplicity.upperBound() == null || multiplicity.upperBound() > 1)) return ExpressionValueType.collection(scalar);
            return scalar;
        }


        /** Descripción: Ejecuta la operación «parameterType» correspondiente a la responsabilidad de MetaIndex. Entradas: parameter: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private ExpressionValueType parameterType(MetaParameter parameter) {
            ExpressionValueType scalar = referenceType(parameter.type());
            return isMany(parameter.multiplicity()) ? ExpressionValueType.collection(scalar) : scalar;
        }

        /** Descripción: Ejecuta la operación «operationReturnType» correspondiente a la responsabilidad de MetaIndex. Entradas: operation: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private ExpressionValueType operationReturnType(MetaOperation operation) {
            ExpressionValueType scalar = referenceType(operation.returnType());
            return isMany(operation.returnMultiplicity()) ? ExpressionValueType.collection(scalar) : scalar;
        }

        /** Descripción: Evalúa la condición «isMany» sin modificar el estado externo. Entradas: multiplicity: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean isMany(Multiplicity multiplicity) {
            return multiplicity != null && (multiplicity.upperBound() == null || multiplicity.upperBound() > 1);
        }
        /** Descripción: Ejecuta la operación «referenceType» correspondiente a la responsabilidad de MetaIndex. Entradas: reference: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private ExpressionValueType referenceType(MetaTypeReference reference) {
            if(reference == null || reference.kind() == null) return ExpressionValueType.scalar(ExpressionValueKind.ANY);
            if(reference.kind() == MetaTypeReferenceKind.ENUM) return ExpressionValueType.enumeration(reference.targetId());
            if(reference.kind() == MetaTypeReferenceKind.CLASS) return ExpressionValueType.object(reference.targetId());
            if(reference.kind() != MetaTypeReferenceKind.PRIMITIVE) return ExpressionValueType.scalar(ExpressionValueKind.ANY);
            try {
                MetaPrimitiveType primitive = MetaPrimitiveType.valueOf(reference.targetId());
                return switch(primitive) {
                    case BOOLEAN -> ExpressionValueType.scalar(ExpressionValueKind.BOOLEAN);
                    case BYTE, SHORT, INTEGER, LONG, BIG_INTEGER -> ExpressionValueType.scalar(ExpressionValueKind.INTEGER);
                    case FLOAT, DOUBLE, DECIMAL -> ExpressionValueType.scalar(ExpressionValueKind.DECIMAL);
                    case STRING, CHARACTER -> ExpressionValueType.scalar(ExpressionValueKind.STRING);
                    case DATE -> ExpressionValueType.scalar(ExpressionValueKind.DATE);
                    case BINARY -> ExpressionValueType.scalar(ExpressionValueKind.BINARY);
                    case OBJECT -> ExpressionValueType.scalar(ExpressionValueKind.ANY);
                };
            } catch(IllegalArgumentException exception) {
                return ExpressionValueType.scalar(ExpressionValueKind.ANY);
            }
        }
    }
}
