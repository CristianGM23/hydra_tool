import type { HydraPersistedIdentity } from '../hooks/hydraOverwritePolicy';
import type { ActionModel, ExpressionModel, OperationBehaviorModel, ValidationModel } from '../validation/types';

function canonicalize(value: unknown, omitNodeIds = false): unknown {
    if (Array.isArray(value)) return value.map(item => canonicalize(item, omitNodeIds));
    if (value === null || typeof value !== 'object') return value;
    const record = value as Record<string, unknown>;
    return Object.fromEntries(Object.keys(record)
        .filter(key => !(omitNodeIds && key === 'nodeId'))
        .sort((left, right) => left.localeCompare(right))
        .map(key => [key, canonicalize(record[key], omitNodeIds)]));
}

function signature(value: unknown, omitNodeIds = false): string {
    return JSON.stringify(canonicalize(value, omitNodeIds));
}

/**
 * Construye la identidad de cada regla persistible. El documento ValidationModel no es la
 * identidad de una validación: contiene muchas reglas. La condición se normaliza sin nodeId
 * para poder reconocer expresiones semánticamente iguales aunque se hayan regenerado IDs de AST.
 */
export function validationOverwriteIdentities(
    validationModel: ValidationModel,
    expressionModel: ExpressionModel,
    actionModel?: ActionModel,
    behaviorModel?: OperationBehaviorModel
): HydraPersistedIdentity[] {
    const expressions = new Map(expressionModel.expressions.map(expression => [expression.id, expression]));
    const rules: HydraPersistedIdentity[] = validationModel.rules.map(rule => {
        const expression = expressions.get(rule.conditionExpressionId);
        const semanticSignature = expression === undefined ? '' : signature({
            context: rule.context,
            root: expression.root
        }, true);
        return {
            layer: 'Validations and quick fixes',
            entityKind: 'VALIDATION_RULE',
            stableId: rule.id,
            uniqueName: rule.name,
            semanticSignature,
            contentSignature: signature({ rule, expression }),
            displayLabel: rule.name.trim() === '' ? rule.code || rule.id : rule.name
        };
    });
    const actions: HydraPersistedIdentity[] = (actionModel?.actions ?? []).map(action => ({
        layer: 'Validations and quick fixes',
        entityKind: 'QUICK_FIX',
        stableId: action.id,
        uniqueName: action.name,
        contentSignature: signature(action),
        displayLabel: action.name.trim() === '' ? action.id : action.name
    }));
    const operations: HydraPersistedIdentity[] = (behaviorModel?.bindings ?? []).map(binding => ({
        layer: 'Validations and quick fixes',
        entityKind: 'OPERATION_BINDING',
        stableId: binding.operationId,
        contentSignature: signature(binding),
        displayLabel: binding.operationId
    }));
    return [...rules, ...actions, ...operations];
}
