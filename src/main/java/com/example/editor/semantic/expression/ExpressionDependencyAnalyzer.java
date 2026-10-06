package com.example.editor.semantic.expression;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

/** Extrae dependencias estructurales sin ejecutar la expresión. */
@Service
public class ExpressionDependencyAnalyzer {

    /** Descripción: Ejecuta la operación «analyze» correspondiente a la responsabilidad de ExpressionDependencyAnalyzer. Entradas: expression: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public List<ExpressionDependency> analyze(ExpressionDefinition expression) {
        Set<ExpressionDependency> dependencies = new LinkedHashSet<>();
        if(expression == null) return List.of();
        if(!expression.contextClassId().isBlank()) dependencies.add(new ExpressionDependency(ExpressionDependencyKind.CLASS, expression.contextClassId()));
        visit(expression.root(), dependencies);
        return List.copyOf(dependencies);
    }

    /** Descripción: Ejecuta la operación «visit» correspondiente a la responsabilidad de ExpressionDependencyAnalyzer. Entradas: node: dato de entrada necesario para la operación; dependencies: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void visit(ExpressionNode node, Set<ExpressionDependency> dependencies) {
        switch(node) {
            case LiteralExpressionNode ignored -> { }
            case SelfExpressionNode ignored -> { }
            case ModelExpressionNode ignored -> dependencies.add(new ExpressionDependency(ExpressionDependencyKind.MODEL, ""));
            case ClassInstancesExpressionNode classInstances -> dependencies.add(new ExpressionDependency(ExpressionDependencyKind.CLASS, classInstances.classId()));
            case AttributeAccessExpressionNode access -> {
                dependencies.add(new ExpressionDependency(ExpressionDependencyKind.ATTRIBUTE, access.attributeId()));
                visit(access.target(), dependencies);
            }
            case RelationNavigationExpressionNode navigation -> {
                dependencies.add(new ExpressionDependency(ExpressionDependencyKind.RELATION, navigation.relationId()));
                visit(navigation.target(), dependencies);
            }
            case UnaryExpressionNode unary -> visit(unary.operand(), dependencies);
            case BinaryExpressionNode binary -> {
                visit(binary.left(), dependencies);
                visit(binary.right(), dependencies);
            }
            case CollectionExpressionNode collection -> {
                visit(collection.source(), dependencies);
                if(collection.expression() != null) visit(collection.expression(), dependencies);
            }
            case ConditionalExpressionNode conditional -> {
                visit(conditional.condition(), dependencies);
                visit(conditional.whenTrue(), dependencies);
                visit(conditional.whenFalse(), dependencies);
            }
            case FunctionExpressionNode function -> function.arguments().forEach(argument -> visit(argument, dependencies));
            case VariableExpressionNode ignored -> { }
            case OperationCallExpressionNode operationCall -> {
                dependencies.add(new ExpressionDependency(ExpressionDependencyKind.OPERATION, operationCall.operationId()));
                visit(operationCall.target(), dependencies);
                operationCall.arguments().forEach(argument -> visit(argument, dependencies));
            }
        }
    }
}
