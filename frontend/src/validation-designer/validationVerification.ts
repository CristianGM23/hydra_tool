import type {
    ActionDefinition,
    ActionModel,
    ActionModelCheckReport,
    ActionOperation,
    ExpressionModel,
    ExpressionModelCheckReport,
    OperationBehaviorModel,
    OperationBehaviorSaveResponse,
    OperationBinding,
    ValidationModel,
    ValidationModelCheckReport,
    ValidationRule
} from '../validation/types';

interface VerificationIndex {
    actionsById: ReadonlyMap<string, ActionDefinition>;
    expressionsById: ReadonlyMap<string, ExpressionModel['expressions'][number]>;
    behaviorByOperationId: ReadonlyMap<string, OperationBinding>;
    behaviorIndexByOperationId: ReadonlyMap<string, number>;
}

interface RuleDependencies {
    actions: ActionDefinition[];
    expressions: ExpressionModel['expressions'];
    bindings: OperationBinding[];
    operationIds: string[];
    missingAction: boolean;
    missingExpression: boolean;
    missingBinding: boolean;
}

/**
 * Stable snapshot of only the editable inputs that can change one validation
 * rule. Dependency scoping keeps verification O(rule dependencies) instead of
 * serializing the complete action/behavior models for every rule.
 */
export function validationRuleVerificationSignature(
    rule: ValidationRule,
    expressions: ExpressionModel,
    actions: ActionModel,
    behaviors: OperationBehaviorModel
): string {
    return signatureFromDependencies(rule, collectRuleDependencies(rule, createIndex(expressions, actions, behaviors)));
}

/**
 * Resolves all currently verified rule IDs in one indexed pass. This is the
 * preferred render-path API because it avoids rebuilding maps for every rule.
 */
export function verifiedRuleIdsFromSignatures(
    validations: ValidationModel,
    expressions: ExpressionModel,
    actions: ActionModel,
    behaviors: OperationBehaviorModel,
    verifiedSignatures: Readonly<Record<string, string>>
): ReadonlySet<string> {
    const index = createIndex(expressions, actions, behaviors);
    const result = new Set<string>();
    for (const rule of validations.rules) {
        const expected = verifiedSignatures[rule.id];
        if (expected === undefined) continue;
        const dependencies = collectRuleDependencies(rule, index);
        if (!dependencies.missingAction && !dependencies.missingExpression && !dependencies.missingBinding
                && expected === signatureFromDependencies(rule, dependencies)) result.add(rule.id);
    }
    return result;
}

export function verifiedRuleSignaturesFromReports(
    validations: ValidationModel,
    expressions: ExpressionModel,
    actions: ActionModel,
    behaviors: OperationBehaviorModel,
    validationReport: ValidationModelCheckReport,
    expressionReport: ExpressionModelCheckReport,
    actionReport: ActionModelCheckReport,
    behaviorReport: OperationBehaviorSaveResponse['validation'] | null
): Readonly<Record<string, string>> {
    const result: Record<string, string> = {};
    const index = createIndex(expressions, actions, behaviors);
    const validationChecks = new Map(validationReport.rules.map(item => [item.ruleId, item]));
    const expressionChecks = new Map(expressionReport.expressions.map(item => [item.expressionId, item]));
    const actionChecks = new Map(actionReport.actions.map(item => [item.actionId, item]));
    const globalBehaviorError = (behaviorReport?.issues ?? []).some(issue => issue.severity === 'ERROR' && bindingIndexFromPath(issue.path) === null);
    const behaviorErrorIndexes = new Set((behaviorReport?.issues ?? [])
        .filter(issue => issue.severity === 'ERROR')
        .map(issue => bindingIndexFromPath(issue.path))
        .filter((value): value is number => value !== null));

    for (const rule of validations.rules) {
        const dependencies = collectRuleDependencies(rule, index);
        const ruleCheck = validationChecks.get(rule.id);
        const ruleHasErrors = ruleCheck === undefined || ruleCheck.diagnostics.some(item => item.severity === 'ERROR');
        const expressionHasErrors = dependencies.missingExpression || dependencies.expressions.some(expression => {
            const check = expressionChecks.get(expression.id);
            return check === undefined || check.diagnostics.some(item => item.severity === 'ERROR');
        });
        const actionHasErrors = dependencies.missingAction || dependencies.actions.some(action => {
            const check = actionChecks.get(action.id);
            return check === undefined || check.diagnostics.some(item => item.severity === 'ERROR');
        });
        const behaviorHasErrors = dependencies.operationIds.length > 0 && (dependencies.missingBinding || globalBehaviorError
            || dependencies.operationIds.some(operationId => {
                const bindingIndex = index.behaviorIndexByOperationId.get(operationId);
                return bindingIndex === undefined || behaviorErrorIndexes.has(bindingIndex);
            }));
        if (!ruleHasErrors && !expressionHasErrors && !actionHasErrors && !behaviorHasErrors) {
            result[rule.id] = signatureFromDependencies(rule, dependencies);
        }
    }
    return result;
}

function createIndex(expressions: ExpressionModel, actions: ActionModel, behaviors: OperationBehaviorModel): VerificationIndex {
    return {
        actionsById: new Map(actions.actions.map(action => [action.id, action])),
        expressionsById: new Map(expressions.expressions.map(expression => [expression.id, expression])),
        behaviorByOperationId: new Map(behaviors.bindings.map(binding => [binding.operationId, binding])),
        behaviorIndexByOperationId: new Map(behaviors.bindings.map((binding, index) => [binding.operationId, index]))
    };
}

function collectRuleDependencies(rule: ValidationRule, index: VerificationIndex): RuleDependencies {
    const actionIds = new Set<string>();
    const expressionIds = new Set<string>([rule.conditionExpressionId]);
    const operationIds = new Set<string>();
    let missingAction = false;
    let missingExpression = false;
    let missingBinding = false;

    const visitAction = (actionId: string): void => {
        if (actionIds.has(actionId)) return;
        const action = index.actionsById.get(actionId);
        if (action === undefined) { missingAction = true; return; }
        actionIds.add(actionId);
        if (action.preconditionExpressionId !== undefined && action.preconditionExpressionId !== '') expressionIds.add(action.preconditionExpressionId);
        for (const operation of action.operations) visitActionOperation(operation);
    };
    const visitOperationBinding = (operationId: string): void => {
        if (operationIds.has(operationId)) return;
        operationIds.add(operationId);
        const binding = index.behaviorByOperationId.get(operationId);
        if (binding === undefined) { missingBinding = true; return; }
        if (binding.expressionId !== undefined && binding.expressionId !== '') expressionIds.add(binding.expressionId);
        if (binding.actionId !== undefined && binding.actionId !== '') visitAction(binding.actionId);
    };
    const collectValueExpression = (value: unknown): void => {
        if (value !== null && typeof value === 'object' && 'expressionId' in value) {
            const expressionId = (value as { expressionId?: unknown }).expressionId;
            if (typeof expressionId === 'string' && expressionId !== '') expressionIds.add(expressionId);
        }
    };
    const visitActionOperation = (operation: ActionOperation): void => {
        if (operation.kind === 'EXECUTE_ACTION') {
            Object.values(operation.arguments).forEach(collectValueExpression);
            visitAction(operation.actionId);
            return;
        }
        if (operation.kind === 'INVOKE_OPERATION') {
            operation.arguments.forEach(collectValueExpression);
            visitOperationBinding(operation.metamodelOperationId);
            return;
        }
        if (operation.kind === 'SET_ATTRIBUTE') collectValueExpression(operation.value);
    };

    rule.quickFixIds.forEach(visitAction);
    const expressions = [...expressionIds].map(id => {
        const expression = index.expressionsById.get(id);
        if (expression === undefined) missingExpression = true;
        return expression;
    }).filter((value): value is ExpressionModel['expressions'][number] => value !== undefined).sort((left, right) => left.id.localeCompare(right.id));
    const actions = [...actionIds].map(id => index.actionsById.get(id)).filter((value): value is ActionDefinition => value !== undefined).sort((left, right) => left.id.localeCompare(right.id));
    const bindings = [...operationIds].map(id => index.behaviorByOperationId.get(id)).filter((value): value is OperationBinding => value !== undefined).sort((left, right) => left.operationId.localeCompare(right.operationId));
    return { actions, expressions, bindings, operationIds: [...operationIds].sort(), missingAction, missingExpression, missingBinding };
}

function signatureFromDependencies(rule: ValidationRule, dependencies: RuleDependencies): string {
    return JSON.stringify({ rule, expressions: dependencies.expressions, actions: dependencies.actions, bindings: dependencies.bindings });
}

function bindingIndexFromPath(path: string): number | null {
    const match = path.match(/^\$\.bindings\[(\d+)](?:\.|$)/);
    return match === null ? null : Number(match[1]);
}
