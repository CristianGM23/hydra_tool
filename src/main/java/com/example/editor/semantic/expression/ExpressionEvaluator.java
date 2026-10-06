package com.example.editor.semantic.expression;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.example.editor.metamodel.core.MetaRelation;
import com.example.editor.metamodel.core.Multiplicity;
import com.example.editor.validation.model.CanonicalModelDocument;
import com.example.editor.validation.model.CanonicalModelElement;
import com.example.editor.validation.model.CanonicalModelRelation;

/** Evalúa expresiones sobre documentos de modelo canónicos sin usar EMF. */
@Service
public class ExpressionEvaluator {

    private final ExpressionTypeChecker typeChecker;

    /** Descripción: Inicializa una instancia de ExpressionEvaluator con sus dependencias y datos de entrada. Entradas: typeChecker: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public ExpressionEvaluator(ExpressionTypeChecker typeChecker) {
        this.typeChecker = typeChecker;
    }

    /** Descripción: Ejecuta la operación «evaluate» correspondiente a la responsabilidad de ExpressionEvaluator. Entradas: expression: dato de entrada necesario para la operación; context: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ExpressionEvaluationResult evaluate(ExpressionDefinition expression, ExpressionEvaluationContext context) {
        return evaluate(expression, context, ExpressionOperationInvoker.unsupported());
    }

    /** Descripción: Ejecuta la operación «evaluate» correspondiente a la responsabilidad de ExpressionEvaluator. Entradas: expression: dato de entrada necesario para la operación; context: dato de entrada necesario para la operación; operationInvoker: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ExpressionEvaluationResult evaluate(ExpressionDefinition expression, ExpressionEvaluationContext context, ExpressionOperationInvoker operationInvoker) {
        if(expression == null) throw new ExpressionEvaluationException("Expression must not be null");
        ExpressionCheckResult check = typeChecker.check(expression, context.metamodel());
        if(check.hasErrors()) {
            String codes = check.diagnostics().stream().map(ExpressionDiagnostic::code).distinct().sorted().reduce((left, right) -> left + ", " + right).orElse("UNKNOWN");
            throw new ExpressionEvaluationException("Expression is not executable because type checking failed: " + codes);
        }
        Object value = evaluateNode(expression.root(), context, operationInvoker);
        return new ExpressionEvaluationResult(expression.id(), check.inferredType(), value);
    }

    /** Descripción: Ejecuta la operación «evaluateNode» correspondiente a la responsabilidad de ExpressionEvaluator. Entradas: node: dato de entrada necesario para la operación; context: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public Object evaluateNode(ExpressionNode node, ExpressionEvaluationContext context) {
        return evaluateNode(node, context, ExpressionOperationInvoker.unsupported());
    }

    /** Descripción: Ejecuta la operación «evaluateNode» correspondiente a la responsabilidad de ExpressionEvaluator. Entradas: node: dato de entrada necesario para la operación; context: dato de entrada necesario para la operación; operationInvoker: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public Object evaluateNode(ExpressionNode node, ExpressionEvaluationContext context, ExpressionOperationInvoker operationInvoker) {
        return switch(node) {
            case LiteralExpressionNode literal -> literal.value();
            case SelfExpressionNode ignored -> context.current();
            case ModelExpressionNode ignored -> context.model();
            case ClassInstancesExpressionNode classInstances -> context.model().elements().stream().filter(element -> isInstanceOf(context, element.classId(), classInstances.classId())).toList();
            case AttributeAccessExpressionNode access -> readAttribute(evaluateNode(access.target(), context, operationInvoker), access.attributeId());
            case RelationNavigationExpressionNode navigation -> navigate(context, evaluateNode(navigation.target(), context, operationInvoker), navigation.relationId());
            case UnaryExpressionNode unary -> evaluateUnary(unary.operator(), evaluateNode(unary.operand(), context, operationInvoker));
            case BinaryExpressionNode binary -> evaluateBinary(binary.operator(), evaluateNode(binary.left(), context, operationInvoker), evaluateNode(binary.right(), context, operationInvoker));
            case CollectionExpressionNode collection -> evaluateCollection(collection, context, operationInvoker);
            case ConditionalExpressionNode conditional -> asBoolean(evaluateNode(conditional.condition(), context, operationInvoker)) ? evaluateNode(conditional.whenTrue(), context, operationInvoker) : evaluateNode(conditional.whenFalse(), context, operationInvoker);
            case FunctionExpressionNode function -> evaluateFunction(function, context, operationInvoker);
            case VariableExpressionNode variable -> context.variable(variable.variableName());
            case OperationCallExpressionNode operationCall -> evaluateOperationCall(operationCall, context, operationInvoker);
        };
    }

    /** Descripción: Obtiene la información correspondiente a «readAttribute» dentro de ExpressionEvaluator. Entradas: target: dato de entrada necesario para la operación; attributeId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private Object readAttribute(Object target, String attributeId) {
        if(target == null) return null;
        if(target instanceof CanonicalModelElement element) return element.attributes().get(attributeId);
        if(target instanceof Collection<?> collection) return collection.stream().map(item -> readAttribute(item, attributeId)).toList();
        throw new ExpressionEvaluationException("Attribute access requires a model element or collection");
    }

    /** Descripción: Ejecuta la operación «navigate» correspondiente a la responsabilidad de ExpressionEvaluator. Entradas: context: dato de entrada necesario para la operación; target: dato de entrada necesario para la operación; relationId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private Object navigate(ExpressionEvaluationContext context, Object target, String relationId) {
        if(target == null) return null;
        if(target instanceof Collection<?> collection) {
            List<CanonicalModelElement> result = new ArrayList<>();
            for(Object item : collection) addNavigated(result, context, item, relationId);
            return List.copyOf(result);
        }
        if(!(target instanceof CanonicalModelElement element)) throw new ExpressionEvaluationException("Relation navigation requires a model element or collection");
        List<CanonicalModelElement> result = navigateFrom(context.model(), element.id(), relationId);
        MetaRelation relation = context.metamodel().relations().stream().filter(item -> item.id().equals(relationId)).findFirst().orElseThrow(() -> new ExpressionEvaluationException("Relation was not found: " + relationId));
        if(isMany(relation.targetMultiplicity())) return result;
        return result.isEmpty() ? null : result.getFirst();
    }

    /** Descripción: Ejecuta la operación «addNavigated» correspondiente a la responsabilidad de ExpressionEvaluator. Entradas: result: dato de entrada necesario para la operación; context: dato de entrada necesario para la operación; item: dato de entrada necesario para la operación; relationId: identificador que delimita el recurso objetivo. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void addNavigated(List<CanonicalModelElement> result, ExpressionEvaluationContext context, Object item, String relationId) {
        if(!(item instanceof CanonicalModelElement element)) throw new ExpressionEvaluationException("Relation navigation collection contains a non-model value");
        result.addAll(navigateFrom(context.model(), element.id(), relationId));
    }

    /** Descripción: Ejecuta la operación «navigateFrom» correspondiente a la responsabilidad de ExpressionEvaluator. Entradas: model: dato de entrada necesario para la operación; sourceId: identificador que delimita el recurso objetivo; relationId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private List<CanonicalModelElement> navigateFrom(CanonicalModelDocument model, String sourceId, String relationId) {
        List<CanonicalModelElement> result = new ArrayList<>();
        for(CanonicalModelRelation relation : model.relations()) {
            if(relation.relationId().equals(relationId) && relation.sourceElementId().equals(sourceId)) {
                result.add(model.findElement(relation.targetElementId()).orElseThrow(() -> new ExpressionEvaluationException("Relation target element was not found: " + relation.targetElementId())));
            }
        }
        return List.copyOf(result);
    }

    /** Descripción: Ejecuta la operación «evaluateUnary» correspondiente a la responsabilidad de ExpressionEvaluator. Entradas: operator: dato de entrada necesario para la operación; value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private Object evaluateUnary(UnaryExpressionOperator operator, Object value) {
        return switch(operator) {
            case NOT -> !asBoolean(value);
            case IS_NULL -> value == null;
            case IS_NOT_NULL -> value != null;
            case IS_EMPTY -> isEmpty(value);
            case IS_NOT_EMPTY -> !isEmpty(value);
            case NEGATE -> numeric(value).negate();
            case SIZE -> size(value);
            case TO_UPPER -> value == null ? null : value.toString().toUpperCase(java.util.Locale.ROOT);
            case TO_LOWER -> value == null ? null : value.toString().toLowerCase(java.util.Locale.ROOT);
        };
    }

    /** Descripción: Ejecuta la operación «evaluateBinary» correspondiente a la responsabilidad de ExpressionEvaluator. Entradas: operator: dato de entrada necesario para la operación; left: dato de entrada necesario para la operación; right: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Object evaluateBinary(BinaryExpressionOperator operator, Object left, Object right) {
        return switch(operator) {
            case AND -> asBoolean(left) && asBoolean(right);
            case OR -> asBoolean(left) || asBoolean(right);
            case EQUALS -> valuesEqual(left, right);
            case NOT_EQUALS -> !valuesEqual(left, right);
            case GREATER_THAN -> compare(left, right) > 0;
            case GREATER_OR_EQUAL -> compare(left, right) >= 0;
            case LESS_THAN -> compare(left, right) < 0;
            case LESS_OR_EQUAL -> compare(left, right) <= 0;
            case ADD -> add(left, right);
            case SUBTRACT -> numeric(left).subtract(numeric(right));
            case MULTIPLY -> numeric(left).multiply(numeric(right));
            case DIVIDE -> divide(left, right);
            case CONTAINS -> contains(left, right);
            case STARTS_WITH -> string(left).startsWith(string(right));
            case ENDS_WITH -> string(left).endsWith(string(right));
            case MATCHES -> Pattern.compile(string(right)).matcher(string(left)).matches();
        };
    }

    /** Descripción: Ejecuta la operación «evaluateCollection» correspondiente a la responsabilidad de ExpressionEvaluator. Entradas: node: dato de entrada necesario para la operación; context: dato de entrada necesario para la operación; operationInvoker: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Object evaluateCollection(CollectionExpressionNode node, ExpressionEvaluationContext context, ExpressionOperationInvoker operationInvoker) {
        List<?> source = toList(evaluateNode(node.source(), context, operationInvoker));
        return switch(node.operator()) {
            case EXISTS -> source.stream().anyMatch(item -> evaluatePredicate(node, context, item, operationInvoker));
            case FOR_ALL -> source.stream().allMatch(item -> evaluatePredicate(node, context, item, operationInvoker));
            case NONE -> source.stream().noneMatch(item -> evaluatePredicate(node, context, item, operationInvoker));
            case IS_UNIQUE -> uniqueValues(node, context, source, operationInvoker);
            case SELECT -> source.stream().filter(item -> evaluatePredicate(node, context, item, operationInvoker)).toList();
            case COUNT -> source.size();
            case SUM -> source.stream().map(item -> evaluateProjection(node, context, item, operationInvoker)).map(this::numeric).reduce(BigDecimal.ZERO, BigDecimal::add);
            case MIN -> source.stream().map(item -> evaluateProjection(node, context, item, operationInvoker)).filter(Objects::nonNull).min(this::compare).orElse(null);
            case MAX -> source.stream().map(item -> evaluateProjection(node, context, item, operationInvoker)).filter(Objects::nonNull).max(this::compare).orElse(null);
        };
    }

    /** Descripción: Ejecuta la operación «evaluatePredicate» correspondiente a la responsabilidad de ExpressionEvaluator. Entradas: node: dato de entrada necesario para la operación; context: dato de entrada necesario para la operación; item: dato de entrada necesario para la operación; operationInvoker: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean evaluatePredicate(CollectionExpressionNode node, ExpressionEvaluationContext context, Object item, ExpressionOperationInvoker operationInvoker) {
        if(node.expression() == null) return asBoolean(item);
        return asBoolean(evaluateNode(node.expression(), contextForItem(context, item), operationInvoker));
    }

    /** Descripción: Ejecuta la operación «evaluateProjection» correspondiente a la responsabilidad de ExpressionEvaluator. Entradas: node: dato de entrada necesario para la operación; context: dato de entrada necesario para la operación; item: dato de entrada necesario para la operación; operationInvoker: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Object evaluateProjection(CollectionExpressionNode node, ExpressionEvaluationContext context, Object item, ExpressionOperationInvoker operationInvoker) {
        if(node.expression() == null) return item;
        return evaluateNode(node.expression(), contextForItem(context, item), operationInvoker);
    }

    /** Descripción: Ejecuta la operación «uniqueValues» correspondiente a la responsabilidad de ExpressionEvaluator. Entradas: node: dato de entrada necesario para la operación; context: dato de entrada necesario para la operación; source: dato de entrada necesario para la operación; operationInvoker: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean uniqueValues(CollectionExpressionNode node, ExpressionEvaluationContext context, List<?> source, ExpressionOperationInvoker operationInvoker) {
        Set<Object> values = new LinkedHashSet<>();
        for(Object item : source) {
            Object value = evaluateProjection(node, context, item, operationInvoker);
            if(!values.add(normalizeEquality(value))) return false;
        }
        return true;
    }

    /** Descripción: Ejecuta la operación «contextForItem» correspondiente a la responsabilidad de ExpressionEvaluator. Entradas: context: dato de entrada necesario para la operación; item: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private ExpressionEvaluationContext contextForItem(ExpressionEvaluationContext context, Object item) {
        return context.withCurrent(item);
    }

    /** Descripción: Ejecuta la operación «evaluateFunction» correspondiente a la responsabilidad de ExpressionEvaluator. Entradas: function: dato de entrada necesario para la operación; context: dato de entrada necesario para la operación; operationInvoker: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Object evaluateFunction(FunctionExpressionNode function, ExpressionEvaluationContext context, ExpressionOperationInvoker operationInvoker) {
        List<Object> args = function.arguments().stream().map(argument -> evaluateNode(argument, context, operationInvoker)).toList();
        return switch(function.functionName()) {
            case "length" -> size(requiredArg(function, args, 0));
            case "lower" -> string(requiredArg(function, args, 0)).toLowerCase(java.util.Locale.ROOT);
            case "upper" -> string(requiredArg(function, args, 0)).toUpperCase(java.util.Locale.ROOT);
            case "trim" -> string(requiredArg(function, args, 0)).trim();
            case "abs" -> numeric(requiredArg(function, args, 0)).abs();
            case "coalesce" -> args.stream().filter(Objects::nonNull).findFirst().orElse(null);
            default -> throw new ExpressionEvaluationException("Function is not registered: " + function.functionName());
        };
    }


    /** Descripción: Ejecuta la operación «evaluateOperationCall» correspondiente a la responsabilidad de ExpressionEvaluator. Entradas: call: dato de entrada necesario para la operación; context: dato de entrada necesario para la operación; operationInvoker: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Object evaluateOperationCall(OperationCallExpressionNode call, ExpressionEvaluationContext context, ExpressionOperationInvoker operationInvoker) {
        Object target = evaluateNode(call.target(), context, operationInvoker);
        if(target instanceof Collection<?> collection) {
            List<Object> values = new ArrayList<>();
            for(Object item : collection) {
                List<Object> arguments = call.arguments().stream().map(argument -> evaluateNode(argument, context.withCurrent(item), operationInvoker)).toList();
                values.add(operationInvoker.invoke(call, item, arguments, context.withCurrent(item)));
            }
            return List.copyOf(values);
        }
        List<Object> arguments = call.arguments().stream().map(argument -> evaluateNode(argument, context, operationInvoker)).toList();
        return operationInvoker.invoke(call, target, arguments, context);
    }

    /** Descripción: Valida las condiciones necesarias para «requiredArg» y rechaza entradas no válidas. Entradas: function: dato de entrada necesario para la operación; args: dato de entrada necesario para la operación; index: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Object requiredArg(FunctionExpressionNode function, List<Object> args, int index) {
        if(index >= args.size()) throw new ExpressionEvaluationException("Function " + function.functionName() + " is missing argument " + index);
        return args.get(index);
    }

    /** Descripción: Evalúa la condición «isInstanceOf» sin modificar el estado externo. Entradas: context: dato de entrada necesario para la operación; actualClassId: identificador que delimita el recurso objetivo; expectedClassId: identificador que delimita el recurso objetivo. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean isInstanceOf(ExpressionEvaluationContext context, String actualClassId, String expectedClassId) {
        if(actualClassId.equals(expectedClassId)) return true;
        Set<String> visited = new java.util.HashSet<>();
        List<String> pending = new ArrayList<>();
        pending.add(actualClassId);
        while(!pending.isEmpty()) {
            String current = pending.removeLast();
            if(!visited.add(current)) continue;
            for(MetaRelation relation : context.metamodel().relations()) {
                if((relation.kind() == com.example.editor.metamodel.core.MetaRelationKind.INHERITANCE || relation.kind() == com.example.editor.metamodel.core.MetaRelationKind.IMPLEMENTATION) && relation.sourceTypeId().equals(current)) {
                    if(relation.targetTypeId().equals(expectedClassId)) return true;
                    pending.add(relation.targetTypeId());
                }
            }
        }
        return false;
    }

    /** Descripción: Evalúa la condición «isMany» sin modificar el estado externo. Entradas: multiplicity: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean isMany(Multiplicity multiplicity) {
        return multiplicity != null && (multiplicity.upperBound() == null || multiplicity.upperBound() > 1);
    }

    /** Descripción: Ejecuta la operación «asBoolean» correspondiente a la responsabilidad de ExpressionEvaluator. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean asBoolean(Object value) {
        if(value instanceof Boolean booleanValue) return booleanValue;
        throw new ExpressionEvaluationException("Expected boolean value but found: " + typeName(value));
    }

    /** Descripción: Evalúa la condición «isEmpty» sin modificar el estado externo. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean isEmpty(Object value) {
        if(value == null) return true;
        if(value instanceof CharSequence sequence) return sequence.isEmpty();
        if(value instanceof Collection<?> collection) return collection.isEmpty();
        if(value instanceof java.util.Map<?, ?> map) return map.isEmpty();
        if(value.getClass().isArray()) return java.lang.reflect.Array.getLength(value) == 0;
        return false;
    }

    /** Descripción: Ejecuta la operación «size» correspondiente a la responsabilidad de ExpressionEvaluator. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private int size(Object value) {
        if(value == null) return 0;
        if(value instanceof CharSequence sequence) return sequence.length();
        if(value instanceof Collection<?> collection) return collection.size();
        if(value instanceof java.util.Map<?, ?> map) return map.size();
        if(value.getClass().isArray()) return java.lang.reflect.Array.getLength(value);
        throw new ExpressionEvaluationException("SIZE requires a string, collection, map or array");
    }

    /** Descripción: Ejecuta la operación «add» correspondiente a la responsabilidad de ExpressionEvaluator. Entradas: left: dato de entrada necesario para la operación; right: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Object add(Object left, Object right) {
        if(left instanceof String || right instanceof String) return string(left) + string(right);
        return numeric(left).add(numeric(right));
    }

    /** Descripción: Ejecuta la operación «divide» correspondiente a la responsabilidad de ExpressionEvaluator. Entradas: left: dato de entrada necesario para la operación; right: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private BigDecimal divide(Object left, Object right) {
        BigDecimal divisor = numeric(right);
        if(BigDecimal.ZERO.compareTo(divisor) == 0) throw new ExpressionEvaluationException("Division by zero");
        return numeric(left).divide(divisor, java.math.MathContext.DECIMAL128);
    }

    /** Descripción: Evalúa la condición «contains» sin modificar el estado externo. Entradas: left: dato de entrada necesario para la operación; right: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean contains(Object left, Object right) {
        if(left instanceof CharSequence sequence) return sequence.toString().contains(string(right));
        if(left instanceof Collection<?> collection) return collection.stream().anyMatch(item -> valuesEqual(item, right));
        throw new ExpressionEvaluationException("CONTAINS requires a string or collection");
    }

    /** Descripción: Ejecuta la operación «valuesEqual» correspondiente a la responsabilidad de ExpressionEvaluator. Entradas: left: dato de entrada necesario para la operación; right: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean valuesEqual(Object left, Object right) {
        if(left instanceof Number && right instanceof Number) return numeric(left).compareTo(numeric(right)) == 0;
        return Objects.equals(normalizeEquality(left), normalizeEquality(right));
    }

    /** Descripción: Ejecuta la operación «normalizeEquality» correspondiente a la responsabilidad de ExpressionEvaluator. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private Object normalizeEquality(Object value) {
        if(value instanceof CanonicalModelElement element) return element.id();
        if(value instanceof Number) return numeric(value).stripTrailingZeros();
        return value;
    }

    /** Descripción: Ejecuta la operación «compare» correspondiente a la responsabilidad de ExpressionEvaluator. Entradas: left: dato de entrada necesario para la operación; right: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private int compare(Object left, Object right) {
        if(left == null || right == null) throw new ExpressionEvaluationException("Ordered comparison does not accept null values");
        if(left instanceof Number && right instanceof Number) return numeric(left).compareTo(numeric(right));
        if(left instanceof LocalDate leftDate && right instanceof LocalDate rightDate) return leftDate.compareTo(rightDate);
        if(left instanceof LocalDateTime leftDateTime && right instanceof LocalDateTime rightDateTime) return leftDateTime.compareTo(rightDateTime);
        if(left instanceof String leftString && right instanceof String rightString) return leftString.compareTo(rightString);
        if(left instanceof Character leftCharacter && right instanceof Character rightCharacter) return leftCharacter.compareTo(rightCharacter);
        if(left instanceof Boolean leftBoolean && right instanceof Boolean rightBoolean) return leftBoolean.compareTo(rightBoolean);
        throw new ExpressionEvaluationException("Values cannot be compared: " + typeName(left) + " and " + typeName(right));
    }

    /** Descripción: Ejecuta la operación «numeric» correspondiente a la responsabilidad de ExpressionEvaluator. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private BigDecimal numeric(Object value) {
        if(value instanceof BigDecimal decimal) return decimal;
        if(value instanceof BigInteger integer) return new BigDecimal(integer);
        if(value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long) return BigDecimal.valueOf(((Number) value).longValue());
        if(value instanceof Float || value instanceof Double) return BigDecimal.valueOf(((Number) value).doubleValue());
        if(value instanceof Number number) return new BigDecimal(number.toString());
        throw new ExpressionEvaluationException("Expected numeric value but found: " + typeName(value));
    }

    /** Descripción: Ejecuta la operación «string» correspondiente a la responsabilidad de ExpressionEvaluator. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String string(Object value) {
        if(value == null) return "";
        return value.toString();
    }

    /** Descripción: Ejecuta la operación «toList» correspondiente a la responsabilidad de ExpressionEvaluator. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private List<?> toList(Object value) {
        if(value == null) return List.of();
        if(value instanceof List<?> list) return list;
        if(value instanceof Collection<?> collection) return List.copyOf(collection);
        throw new ExpressionEvaluationException("Expected collection but found: " + typeName(value));
    }

    /** Descripción: Ejecuta la operación «typeName» correspondiente a la responsabilidad de ExpressionEvaluator. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String typeName(Object value) {
        return value == null ? "null" : value.getClass().getSimpleName();
    }
}
