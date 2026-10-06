import { isStableDocumentId, isStableElementId } from '../validation/stableIdentifierPolicy';
import type {
    ActionDefinition,
    ActionModel,
    ActionOperation,
    ActionSelector,
    ActionValueSource,
    ExpressionDefinition,
    ExpressionModel,
    ExpressionNode,
    ValidationModel
} from '../validation/types';

export function findValidationDesignerBlockingError(
    expressions: ExpressionModel,
    actions: ActionModel,
    validations: ValidationModel
): string {
    const documentError = validationDocumentError(expressions, actions, validations);
    if (documentError !== '') return documentError;
    for (const expression of expressions.expressions) {
        const error = expressionError(expression);
        if (error !== '') return error;
    }
    for (const action of actions.actions) {
        const error = actionError(action);
        if (error !== '') return error;
    }
    for (const rule of validations.rules) {
        if (!validElementId(rule.id)) return `Validation "${rule.name || rule.code}" has an invalid stable identifier.`;
        if (rule.name.trim() === '') return 'Every validation must have a name.';
        if (!validElementId(rule.code)) return `Validation "${rule.name}" needs a valid stable problem code.`;
        if (!validElementId(rule.conditionExpressionId)) return `Validation "${rule.name}" must reference a condition expression.`;
        if (rule.messageTemplate.trim() === '') return `Validation "${rule.name}" needs a failure message.`;
        if ((rule.context.classId ?? '') !== '' && !validElementId(rule.context.classId ?? '')) return `Validation "${rule.name}" contains an invalid context class identifier.`;
        if ((rule.context.featureId ?? '') !== '' && !validElementId(rule.context.featureId ?? '')) return `Validation "${rule.name}" contains an invalid context feature identifier.`;
        for (const quickFixId of rule.quickFixIds) {
            if (!validElementId(quickFixId)) return `Validation "${rule.name}" contains an invalid quick-fix identifier.`;
        }
    }
    return '';
}

function validationDocumentError(expressions: ExpressionModel, actions: ActionModel, validations: ValidationModel): string {
    const actionModelId = validations.actionModelId ?? '';
    if (!isStableDocumentId(expressions.id)) return 'The expression document has an invalid stable document ID.';
    if (!isStableDocumentId(expressions.metamodelId)) return 'The expression document references an invalid metamodel ID.';
    if (!isStableDocumentId(actions.id)) return 'The quick-fix document has an invalid stable document ID.';
    if (!isStableDocumentId(actions.metamodelId)) return 'The quick-fix document references an invalid metamodel ID.';
    if (!isStableDocumentId(actions.expressionModelId)) return 'The quick-fix document references an invalid expression document ID.';
    if (!isStableDocumentId(validations.id)) return 'The validation document has an invalid stable document ID.';
    if (!isStableDocumentId(validations.metamodelId)) return 'The validation document references an invalid metamodel ID.';
    if (!isStableDocumentId(validations.expressionModelId)) return 'The validation document references an invalid expression document ID.';
    if (actionModelId !== '' && !isStableDocumentId(actionModelId)) return 'The validation document references an invalid quick-fix document ID.';
    if (actions.metamodelId !== expressions.metamodelId || validations.metamodelId !== expressions.metamodelId) return 'Validation layer documents must belong to the same metamodel.';
    if (actions.expressionModelId !== expressions.id || validations.expressionModelId !== expressions.id) return 'Validation layer documents do not reference the active expression document.';
    if (actionModelId !== '' && actionModelId !== actions.id) return 'The validation document does not reference the active quick-fix document.';
    return '';
}

export function findExpressionBlockingError(expression: ExpressionDefinition): string {
    return expressionError(expression);
}

function expressionError(expression: ExpressionDefinition): string {
    if (expression.name.trim() === '') return `Expression ${expression.id} needs a name.`;
    if (!validElementId(expression.id)) return `Expression "${expression.name}" has an invalid stable identifier.`;
    if ((expression.contextClassId ?? '') !== '' && !validElementId(expression.contextClassId ?? '')) return `Expression "${expression.name}" has an invalid context class identifier.`;
    return nodeError(expression.root, expression.name);
}

function nodeError(node: ExpressionNode, expressionName: string): string {
    if (!validElementId(node.nodeId)) return `Expression "${expressionName}" contains a node with an invalid identifier.`;
    switch (node.kind) {
        case 'LITERAL': return '';
        case 'SELF':
        case 'MODEL': return '';
        case 'VARIABLE': return requiredReference(node.variableName, `Expression "${expressionName}" must select a variable.`);
        case 'CLASS_INSTANCES': return requiredReference(node.classId, `Expression "${expressionName}" must select a class.`);
        case 'ATTRIBUTE_ACCESS': return requiredReference(node.attributeId, `Expression "${expressionName}" must select an attribute.`) || nodeError(node.target, expressionName);
        case 'RELATION_NAVIGATION': return requiredReference(node.relationId, `Expression "${expressionName}" must select a relation.`) || nodeError(node.target, expressionName);
        case 'UNARY': return nodeError(node.operand, expressionName);
        case 'BINARY': return nodeError(node.left, expressionName) || nodeError(node.right, expressionName);
        case 'COLLECTION': return nodeError(node.source, expressionName) || (node.expression === undefined ? '' : nodeError(node.expression, expressionName));
        case 'CONDITIONAL': return nodeError(node.condition, expressionName) || nodeError(node.whenTrue, expressionName) || nodeError(node.whenFalse, expressionName);
        case 'FUNCTION':
            if (node.functionName.trim() === '') return `Expression "${expressionName}" contains a function without a name.`;
            for (const argument of node.arguments) {
                const error = nodeError(argument, expressionName);
                if (error !== '') return error;
            }
            return '';
        case 'OPERATION_CALL':
            if (!validElementId(node.operationId)) return `Expression "${expressionName}" must select a query operation.`;
            {
                const targetError = nodeError(node.target, expressionName);
                if (targetError !== '') return targetError;
                for (const argument of node.arguments) {
                    const error = nodeError(argument, expressionName);
                    if (error !== '') return error;
                }
            }
            return '';
    }
}

export function findQuickFixBlockingError(action: ActionDefinition): string {
    return actionError(action);
}

function actionError(action: ActionDefinition): string {
    if (!validElementId(action.id)) return `Quick fix "${action.name || action.id}" has an invalid stable identifier.`;
    if (action.name.trim() === '') return `Quick fix ${action.id} needs a name.`;
    if ((action.preconditionExpressionId ?? '') !== '' && !validElementId(action.preconditionExpressionId ?? '')) return `Quick fix "${action.name}" has an invalid precondition expression identifier.`;
    const reviewOnly = action.annotations?.['hydra.import.reviewOnly'] === 'true';
    if (action.operations.length === 0 && !reviewOnly) return `Quick fix "${action.name}" needs at least one operation.`;
    if (reviewOnly && action.enabled) return `Imported review-only quick fix "${action.name}" must stay disabled until its external repair body is mapped to HYDRA operations.`;
    for (const parameter of action.parameters) {
        if (!validElementId(parameter.name)) return `Quick fix "${action.name}" contains an invalid parameter name.`;
    }
    for (const operation of action.operations) {
        const error = operationError(operation, action.name);
        if (error !== '') return error;
    }
    return '';
}

function operationError(operation: ActionOperation, actionName: string): string {
    if (!validElementId(operation.operationId)) return `Quick fix "${actionName}" contains an operation with an invalid identifier.`;
    switch (operation.kind) {
        case 'SET_ATTRIBUTE':
            return selectorError(operation.target, actionName)
                || requiredReference(operation.attributeId, `Quick fix "${actionName}" must select an attribute.`)
                || valueSourceError(operation.value, actionName);
        case 'CLEAR_ATTRIBUTE':
            return selectorError(operation.target, actionName) || requiredReference(operation.attributeId, `Quick fix "${actionName}" must select an attribute.`);
        case 'COPY_ATTRIBUTE':
            return selectorError(operation.source, actionName)
                || selectorError(operation.target, actionName)
                || requiredReference(operation.sourceAttributeId, `Quick fix "${actionName}" must select a source attribute.`)
                || requiredReference(operation.targetAttributeId, `Quick fix "${actionName}" must select a target attribute.`);
        case 'ADD_RELATION':
        case 'REMOVE_RELATION':
            return requiredReference(operation.relationId, `Quick fix "${actionName}" must select a relation.`)
                || selectorError(operation.source, actionName)
                || selectorError(operation.target, actionName);
        case 'CREATE_ELEMENT':
            return requiredReference(operation.classId, `Quick fix "${actionName}" must select the class to create.`)
                || requiredReference(operation.resultName, `Quick fix "${actionName}" must define the created-element name.`)
                || (operation.containmentRelationId !== undefined && operation.containmentRelationId !== '' && operation.container === undefined
                    ? `Quick fix "${actionName}" must select the containment container.` : '');
        case 'DELETE_ELEMENT': return selectorError(operation.target, actionName);
        case 'TRANSFORM_TEXT':
            return selectorError(operation.target, actionName)
                || requiredReference(operation.attributeId, `Quick fix "${actionName}" must select a text attribute.`);
        case 'EXECUTE_ACTION': {
            const referenceError = requiredReference(operation.actionId, `Quick fix "${actionName}" must select another action.`);
            if (referenceError !== '') return referenceError;
            for (const value of Object.values(operation.arguments ?? {})) {
                const error = valueSourceError(value, actionName);
                if (error !== '') return error;
            }
            return '';
        }
        case 'INVOKE_OPERATION': {
            const targetError = selectorError(operation.target, actionName);
            if (targetError !== '') return targetError;
            const referenceError = requiredReference(operation.metamodelOperationId, `Quick fix "${actionName}" must select a command operation.`);
            if (referenceError !== '') return referenceError;
            for (const value of operation.arguments) {
                const error = valueSourceError(value, actionName);
                if (error !== '') return error;
            }
            return '';
        }
    }
}

function valueSourceError(value: ActionValueSource, actionName: string): string {
    if (value.expressionId !== undefined) {
        return requiredReference(value.expressionId, `Quick fix "${actionName}" must select a value expression.`);
    }
    if (value.parameterName !== undefined) {
        return requiredReference(value.parameterName, `Quick fix "${actionName}" must select an action parameter.`);
    }
    if (!Object.prototype.hasOwnProperty.call(value, 'literal')) {
        return `Quick fix "${actionName}" must define a fixed value or an expression, or select a parameter.`;
    }
    return '';
}

function selectorError(selector: ActionSelector, actionName: string): string {
    if (selector.kind === 'PARAMETER' || selector.kind === 'CREATED_ELEMENT') {
        return requiredReference(selector.parameterName ?? '', `Quick fix "${actionName}" contains an incomplete element selector.`);
    }
    if (selector.kind === 'ELEMENT_ID') {
        return requiredReference(selector.elementId ?? '', `Quick fix "${actionName}" contains an incomplete element selector.`);
    }
    return '';
}

function requiredReference(value: string, message: string): string {
    return validElementId(value) ? '' : message;
}

function validElementId(value: string): boolean {
    return isStableElementId(value);
}
