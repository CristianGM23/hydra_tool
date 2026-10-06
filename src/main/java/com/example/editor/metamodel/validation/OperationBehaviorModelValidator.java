package com.example.editor.metamodel.validation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.example.editor.metamodel.core.MetaClass;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.core.MetaOperation;
import com.example.editor.metamodel.core.MetaOperationBehaviorKind;
import com.example.editor.metamodel.core.OperationBehaviorModel;
import com.example.editor.metamodel.core.OperationBinding;
import com.example.editor.metamodel.core.OperationImplementationKind;

/** Valida que las implementaciones de operaciones permanezcan separadas y coherentes con el metamodelo. */
@Component
public class OperationBehaviorModelValidator {

    /** Comprueba identificadores, destinos y requisitos de cada clase de implementación. */
    /** Descripción: Valida las condiciones necesarias para «validate» y rechaza entradas no válidas. Entradas: metamodel: dato de entrada necesario para la operación; behaviorModel: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ValidationReport validate(MetaModel metamodel, OperationBehaviorModel behaviorModel) {
        List<ValidationIssue> issues = new ArrayList<>();
        if(metamodel == null) {
            issues.add(issue(ValidationSeverity.ERROR, "OPERATION_BEHAVIOR_METAMODEL_NULL", "$.metamodelId", "Metamodel must not be null"));
            return new ValidationReport(issues);
        }
        if(behaviorModel == null) {
            issues.add(issue(ValidationSeverity.ERROR, "OPERATION_BEHAVIOR_MODEL_NULL", "$", "Operation behavior model must not be null"));
            return new ValidationReport(issues);
        }
        if(!metamodel.id().equals(behaviorModel.metamodelId())) {
            issues.add(issue(ValidationSeverity.ERROR, "OPERATION_BEHAVIOR_METAMODEL_MISMATCH", "$.metamodelId", "Operation behavior model targets " + behaviorModel.metamodelId() + " instead of " + metamodel.id()));
        }

        Map<String, MetaOperation> operations = new HashMap<>();
        for(MetaClass owner : metamodel.classes()) {
            for(MetaOperation operation : owner.operations()) operations.put(operation.id(), operation);
        }
        Set<String> boundOperations = new HashSet<>();
        for(int index = 0; index < behaviorModel.bindings().size(); index += 1) {
            OperationBinding binding = behaviorModel.bindings().get(index);
            String path = "$.bindings[" + index + "]";
            if(binding.operationId().isBlank()) {
                issues.add(issue(ValidationSeverity.ERROR, "OPERATION_BINDING_ID_EMPTY", path + ".operationId", "Operation id must not be empty"));
                continue;
            }
            if(!boundOperations.add(binding.operationId())) {
                issues.add(issue(ValidationSeverity.ERROR, "OPERATION_BINDING_DUPLICATE", path + ".operationId", "Operation has more than one authoritative binding: " + binding.operationId()));
            }
            MetaOperation operation = operations.get(binding.operationId());
            if(operation == null) {
                issues.add(issue(ValidationSeverity.ERROR, "OPERATION_BINDING_UNKNOWN", path + ".operationId", "Unknown operation id: " + binding.operationId()));
                continue;
            }
            validateImplementation(binding, operation, path, issues);
        }
        return new ValidationReport(issues);
    }

    /** Descripción: Valida las condiciones necesarias para «validateImplementation» y rechaza entradas no válidas. Entradas: binding: dato de entrada necesario para la operación; operation: dato de entrada necesario para la operación; path: ruta o ubicación utilizada por la operación; issues: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void validateImplementation(OperationBinding binding, MetaOperation operation, String path, List<ValidationIssue> issues) {
        OperationImplementationKind kind = binding.implementationKind();
        if(kind == OperationImplementationKind.EXPRESSION && binding.expressionId().isBlank()) {
            issues.add(issue(ValidationSeverity.ERROR, "OPERATION_EXPRESSION_ID_EMPTY", path + ".expressionId", "Expression implementation requires an expression id"));
        }
        if(kind == OperationImplementationKind.ACTION && binding.actionId().isBlank()) {
            issues.add(issue(ValidationSeverity.ERROR, "OPERATION_ACTION_ID_EMPTY", path + ".actionId", "Action implementation requires an action id"));
        }
        if(kind == OperationImplementationKind.EXTERNAL && binding.externalReference().isBlank()) {
            issues.add(issue(ValidationSeverity.ERROR, "OPERATION_EXTERNAL_REFERENCE_EMPTY", path + ".externalReference", "External implementation requires a reference"));
        }
        if(operation.behaviorKind() == MetaOperationBehaviorKind.QUERY && kind == OperationImplementationKind.ACTION) {
            issues.add(issue(ValidationSeverity.ERROR, "QUERY_OPERATION_ACTION_BINDING", path + ".implementationKind", "A QUERY operation cannot be implemented by a mutating action"));
        }
        if(operation.behaviorKind() == MetaOperationBehaviorKind.COMMAND && kind == OperationImplementationKind.EXPRESSION) {
            issues.add(issue(ValidationSeverity.ERROR, "COMMAND_OPERATION_EXPRESSION_BINDING", path + ".implementationKind", "A COMMAND operation cannot be implemented by a non-mutating expression"));
        }
    }

    /** Descripción: Evalúa la condición «issue» sin modificar el estado externo. Entradas: severity: dato de entrada necesario para la operación; code: dato de entrada necesario para la operación; path: ruta o ubicación utilizada por la operación; message: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private ValidationIssue issue(ValidationSeverity severity, String code, String path, String message) {
        return new ValidationIssue(severity, code, path, message);
    }
}
