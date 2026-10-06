import type {
    ActionDefinition,
    ActionModel,
    ActionModelCheckReport,
    ActionModelSaveResponse,
    ValidationModel,
    ValidationModelCheckReport,
    ValidationModelSaveResponse,
    ValidationRule,
    ValidationRuleSet
} from './types';

type JsonRecord = Record<string, unknown>;

/**
 * Canonical validation documents are normalized at the HTTP boundary.
 * Some backend DTOs deliberately omit empty collections in JSON; the UI contract,
 * however, keeps those collections as arrays so every workbench can iterate safely.
 * This also keeps the current frontend compatible with older persisted responses.
 */
export function normalizeActionModel(value: unknown, fallback?: ActionModel): ActionModel {
    const source = object(value) ?? (fallback as unknown as JsonRecord | undefined);
    if (source === undefined) throw new Error('Action model response is missing the action document.');
    const fallbackRecord = fallback as unknown as JsonRecord | undefined;
    const normalized = {
        ...fallback,
        ...source,
        actions: array(source.actions ?? fallbackRecord?.actions).map(normalizeActionDefinition),
        annotations: stringMap(source.annotations ?? fallbackRecord?.annotations)
    } as ActionModel;
    requireString(normalized.id, 'Action document ID');
    requireString(normalized.metamodelId, 'Action document metamodel ID');
    requireString(normalized.expressionModelId, 'Action document expression model ID');
    return normalized;
}

export function normalizeActionModelSaveResponse(value: unknown, fallback: ActionModel, headerEtag = ''): ActionModelSaveResponse {
    const source = object(value);
    const actionCandidate = source?.actionModel ?? value;
    const actionModel = normalizeActionModel(actionCandidate, fallback);
    const checkReport = normalizeActionCheckReport(source?.checkReport, actionModel.id);
    return {
        actionModel,
        checkReport,
        etag: headerEtag || string(source?.etag)
    };
}

export function normalizeValidationModel(value: unknown, fallback?: ValidationModel): ValidationModel {
    const source = object(value) ?? (fallback as unknown as JsonRecord | undefined);
    if (source === undefined) throw new Error('Validation model response is missing the validation document.');
    const fallbackRecord = fallback as unknown as JsonRecord | undefined;
    const normalized = {
        ...fallback,
        ...source,
        actionModelId: string(source.actionModelId ?? fallbackRecord?.actionModelId),
        ruleSets: array(source.ruleSets ?? fallbackRecord?.ruleSets).map(normalizeRuleSet),
        rules: array(source.rules ?? fallbackRecord?.rules).map(normalizeRule),
        importedSources: array(source.importedSources ?? fallbackRecord?.importedSources),
        annotations: stringMap(source.annotations ?? fallbackRecord?.annotations)
    } as ValidationModel;
    requireString(normalized.id, 'Validation document ID');
    requireString(normalized.metamodelId, 'Validation document metamodel ID');
    requireString(normalized.expressionModelId, 'Validation document expression model ID');
    return normalized;
}

export function normalizeValidationModelSaveResponse(value: unknown, fallback: ValidationModel, headerEtag = ''): ValidationModelSaveResponse {
    const source = object(value);
    const modelCandidate = source?.validationModel ?? value;
    const validationModel = normalizeValidationModel(modelCandidate, fallback);
    const checkReport = normalizeValidationCheckReport(source?.checkReport, validationModel.id);
    return {
        validationModel,
        checkReport,
        etag: headerEtag || string(source?.etag)
    };
}

function normalizeActionDefinition(value: unknown): ActionDefinition {
    const source = object(value);
    if (source === undefined) throw new Error('Action document contains an invalid action entry.');
    return {
        ...source,
        description: string(source.description),
        parameters: array(source.parameters),
        preconditionExpressionId: string(source.preconditionExpressionId),
        operations: array(source.operations),
        annotations: stringMap(source.annotations)
    } as unknown as ActionDefinition;
}

function normalizeRule(value: unknown): ValidationRule {
    const source = object(value);
    if (source === undefined) throw new Error('Validation document contains an invalid rule entry.');
    return {
        ...source,
        description: string(source.description),
        triggers: array(source.triggers),
        dependencies: array(source.dependencies),
        quickFixIds: array(source.quickFixIds).map(String),
        annotations: stringMap(source.annotations)
    } as unknown as ValidationRule;
}

function normalizeRuleSet(value: unknown): ValidationRuleSet {
    const source = object(value);
    if (source === undefined) throw new Error('Validation document contains an invalid rule-set entry.');
    return {
        ...source,
        description: string(source.description),
        ruleIds: array(source.ruleIds).map(String),
        annotations: stringMap(source.annotations)
    } as unknown as ValidationRuleSet;
}

export function normalizeActionCheckReport(value: unknown, actionModelId: string): ActionModelCheckReport {
    const source = object(value);
    return {
        actionModelId: string(source?.actionModelId) || actionModelId,
        diagnostics: array(source?.diagnostics),
        actions: array(source?.actions).map(item => {
            const entry = object(item) ?? {};
            return { ...entry, actionId: string(entry.actionId), diagnostics: array(entry.diagnostics) };
        })
    } as ActionModelCheckReport;
}

export function normalizeValidationCheckReport(value: unknown, validationModelId: string): ValidationModelCheckReport {
    const source = object(value);
    return {
        validationModelId: string(source?.validationModelId) || validationModelId,
        diagnostics: array(source?.diagnostics),
        rules: array(source?.rules).map(item => {
            const entry = object(item) ?? {};
            return { ...entry, ruleId: string(entry.ruleId), diagnostics: array(entry.diagnostics), inferredDependencies: array(entry.inferredDependencies) };
        })
    } as ValidationModelCheckReport;
}

function object(value: unknown): JsonRecord | undefined {
    return value !== null && typeof value === 'object' && !Array.isArray(value) ? value as JsonRecord : undefined;
}
function array(value: unknown): unknown[] { return Array.isArray(value) ? value : []; }
function string(value: unknown): string { return typeof value === 'string' ? value : ''; }
function stringMap(value: unknown): Record<string, string> {
    const source = object(value);
    if (source === undefined) return {};
    return Object.fromEntries(Object.entries(source).filter((entry): entry is [string, string] => typeof entry[1] === 'string'));
}
function requireString(value: string, label: string): void {
    if (value.trim() === '') throw new Error(`${label} is missing from the server response.`);
}
