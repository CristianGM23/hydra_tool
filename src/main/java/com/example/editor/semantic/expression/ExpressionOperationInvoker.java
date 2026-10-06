package com.example.editor.semantic.expression;

import java.util.List;

/** Punto de extensión utilizado por el evaluador para resolver OPERATION_CALL. */
@FunctionalInterface
public interface ExpressionOperationInvoker {
    /** Descripción: Ejecuta la operación «invoke» correspondiente a la responsabilidad de ExpressionOperationInvoker. Entradas: call: dato de entrada necesario para la operación; target: dato de entrada necesario para la operación; arguments: dato de entrada necesario para la operación; context: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ Object invoke(OperationCallExpressionNode call, Object target, List<Object> arguments, ExpressionEvaluationContext context);

    /** Descripción: Ejecuta la operación «unsupported» correspondiente a la responsabilidad de ExpressionOperationInvoker. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ static ExpressionOperationInvoker unsupported() {
        return (call, target, arguments, context) -> {
            throw new ExpressionEvaluationException("Operation runtime is not available for: " + call.operationId());
        };
    }
}
