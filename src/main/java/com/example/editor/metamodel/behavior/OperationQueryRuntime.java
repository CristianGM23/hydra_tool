package com.example.editor.metamodel.behavior;

import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.core.MetaOperation;
import com.example.editor.metamodel.core.MetaOperationBehaviorKind;
import com.example.editor.metamodel.core.OperationBehaviorModel;
import com.example.editor.metamodel.core.OperationBinding;
import com.example.editor.metamodel.core.OperationImplementationKind;
import com.example.editor.semantic.expression.ExpressionDefinition;
import com.example.editor.semantic.expression.ExpressionEvaluationContext;
import com.example.editor.semantic.expression.ExpressionEvaluationException;
import com.example.editor.semantic.expression.ExpressionEvaluator;
import com.example.editor.validation.expression.ExpressionModel;
import com.example.editor.semantic.expression.ExpressionOperationInvoker;
import com.example.editor.semantic.expression.OperationCallExpressionNode;
import com.example.editor.validation.model.CanonicalModelElement;
import com.example.editor.validation.support.CanonicalMetamodelIndex;

/** Ejecuta bindings EXPRESSION de operaciones QUERY y protege frente a ciclos. */
@Service
public class OperationQueryRuntime {
    private final ExpressionEvaluator evaluator;
    /** Descripción: Inicializa una instancia de OperationQueryRuntime con sus dependencias y datos de entrada. Entradas: evaluator: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public OperationQueryRuntime(ExpressionEvaluator evaluator) { this.evaluator = evaluator; }

    /** Descripción: Crea o prepara el resultado de «create» dentro de BoundInvoker. Entradas: metamodel: dato de entrada necesario para la operación; expressions: dato de entrada necesario para la operación; behaviors: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ExpressionOperationInvoker create(MetaModel metamodel, ExpressionModel expressions, OperationBehaviorModel behaviors) {
        return new BoundInvoker(metamodel, expressions, behaviors);
    }

    private final class BoundInvoker implements ExpressionOperationInvoker {
        private final CanonicalMetamodelIndex index;
        private final Map<String, ExpressionDefinition> expressions;
        private final Map<String, OperationBinding> bindings;
        private final ArrayDeque<String> stack = new ArrayDeque<>();

        /** Descripción: Inicializa una instancia de BoundInvoker con sus dependencias y datos de entrada. Entradas: metamodel: dato de entrada necesario para la operación; expressionModel: dato de entrada necesario para la operación; behaviorModel: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ private BoundInvoker(MetaModel metamodel, ExpressionModel expressionModel, OperationBehaviorModel behaviorModel) {
            this.index = new CanonicalMetamodelIndex(metamodel);
            this.expressions = expressionModel == null ? Map.of() : Map.copyOf(expressionModel.expressions().stream().collect(Collectors.toMap(ExpressionDefinition::id, item -> item, (left, right) -> left, LinkedHashMap::new)));
            this.bindings = behaviorModel == null ? Map.of() : Map.copyOf(behaviorModel.bindings().stream().collect(Collectors.toMap(OperationBinding::operationId, item -> item, (left, right) -> left, LinkedHashMap::new)));
        }

        @Override
        /** Descripción: Ejecuta la operación «invoke» correspondiente a la responsabilidad de BoundInvoker. Entradas: call: dato de entrada necesario para la operación; target: dato de entrada necesario para la operación; arguments: dato de entrada necesario para la operación; context: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public Object invoke(OperationCallExpressionNode call, Object target, List<Object> arguments, ExpressionEvaluationContext context) {
            MetaOperation operation = index.findOperation(call.operationId()).orElseThrow(() -> new ExpressionEvaluationException("Operation was not found: " + call.operationId()));
            if(operation.behaviorKind() == MetaOperationBehaviorKind.COMMAND) throw new ExpressionEvaluationException("COMMAND operation cannot be used in an expression: " + operation.name());
            if(!(target instanceof CanonicalModelElement element)) throw new ExpressionEvaluationException("Operation target must be a model element: " + operation.name());
            String owner = index.operationOwner(operation.id());
            if(!index.isSameOrSubtype(element.classId(), owner)) throw new ExpressionEvaluationException("Operation target type is not compatible with " + owner);
            if(arguments.size() != operation.parameters().size()) throw new ExpressionEvaluationException("Operation " + operation.name() + " expects " + operation.parameters().size() + " argument(s)");
            OperationBinding binding = bindings.get(operation.id());
            if(binding == null || binding.implementationKind() == OperationImplementationKind.UNIMPLEMENTED) throw new ExpressionEvaluationException("Operation has no executable binding: " + operation.name());
            if(binding.implementationKind() != OperationImplementationKind.EXPRESSION) throw new ExpressionEvaluationException("QUERY operation requires an EXPRESSION binding: " + operation.name());
            ExpressionDefinition expression = expressions.get(binding.expressionId());
            if(expression == null) throw new ExpressionEvaluationException("Bound expression was not found: " + binding.expressionId());
            if(stack.contains(operation.id())) throw new ExpressionEvaluationException("Recursive operation call detected: " + String.join(" -> ", stack) + " -> " + operation.id());
            Map<String, Object> variables = new LinkedHashMap<>();
            for(int indexValue = 0; indexValue < operation.parameters().size(); indexValue++) variables.put(operation.parameters().get(indexValue).name(), arguments.get(indexValue));
            stack.addLast(operation.id());
            try {
                ExpressionEvaluationContext nested = new ExpressionEvaluationContext(context.metamodel(), context.model(), element.id(), variables);
                return evaluator.evaluate(expression, nested, this).value();
            } finally { stack.removeLast(); }
        }
    }
}
