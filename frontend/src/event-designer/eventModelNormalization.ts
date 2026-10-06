import type {
    EventBinding,
    EventBindingModel,
    EventDefinitionModel,
    EventFieldDefinition,
    EventModelLoadResult,
    EventModelSaveResponse,
    EventPayloadMapping,
    EventTypeDefinition
} from './types';

/**
 * Restores the complete frontend contract when Jackson omits empty values.
 *
 * HYDRA persists canonical Java records with NON_EMPTY serialization. Empty
 * arrays, maps, strings and false booleans can therefore be absent in the JSON
 * returned to the browser even though the Java constructor has normalized
 * them. The designer must never render those transport omissions directly.
 */
export function normalizeEventModelLoadResult(result: EventModelLoadResult): EventModelLoadResult {
    return {
        ...result,
        definitions: normalizeEventDefinitionModel(result.definitions),
        bindings: normalizeEventBindingModel(result.bindings),
        checkReport: {
            ...result.checkReport,
            diagnostics: Array.isArray(result.checkReport?.diagnostics) ? result.checkReport.diagnostics : []
        }
    };
}

export function normalizeEventModelSaveResponse(result: EventModelSaveResponse): EventModelSaveResponse {
    return normalizeEventModelLoadResult(result);
}

export function normalizeEventDefinitionModel(model: EventDefinitionModel): EventDefinitionModel {
    return {
        ...model,
        eventTypes: Array.isArray(model.eventTypes) ? model.eventTypes.map(normalizeEventTypeDefinition) : [],
        annotations: normalizeStringMap(model.annotations)
    };
}

export function normalizeEventBindingModel(model: EventBindingModel): EventBindingModel {
    return {
        ...model,
        expressionModelId: model.expressionModelId ?? '',
        bindings: Array.isArray(model.bindings) ? model.bindings.map(normalizeEventBinding) : [],
        annotations: normalizeStringMap(model.annotations)
    };
}

export function normalizeEventTypeDefinition(type: EventTypeDefinition): EventTypeDefinition {
    return {
        ...type,
        description: type.description ?? '',
        category: type.category ?? '',
        version: type.version ?? '1.0',
        fields: Array.isArray(type.fields) ? type.fields.map(normalizeEventFieldDefinition) : [],
        allowedSources: Array.isArray(type.allowedSources) ? [...type.allowedSources] : [],
        allowedSubjects: Array.isArray(type.allowedSubjects) ? [...type.allowedSubjects] : [],
        persistencePolicy: type.persistencePolicy ?? 'TRANSIENT',
        abstractType: type.abstractType ?? false,
        deprecated: type.deprecated ?? false,
        enabled: type.enabled ?? false,
        annotations: normalizeStringMap(type.annotations)
    };
}

function normalizeEventFieldDefinition(field: EventFieldDefinition): EventFieldDefinition {
    return {
        ...field,
        type: field.type ?? 'STRING',
        required: field.required ?? false,
        many: field.many ?? false,
        enumTypeId: field.enumTypeId ?? '',
        objectTypeId: field.objectTypeId ?? '',
        description: field.description ?? ''
    };
}

function normalizeEventBinding(binding: EventBinding): EventBinding {
    return {
        ...binding,
        name: binding.name ?? binding.id,
        conditionExpressionId: binding.conditionExpressionId ?? '',
        payloadMappings: Array.isArray(binding.payloadMappings) ? binding.payloadMappings.map(normalizeEventPayloadMapping) : [],
        enabled: binding.enabled ?? false,
        errorPolicy: binding.errorPolicy ?? 'REPORT',
        annotations: normalizeStringMap(binding.annotations)
    };
}

function normalizeEventPayloadMapping(mapping: EventPayloadMapping): EventPayloadMapping {
    return {
        ...mapping,
        sourcePath: mapping.sourcePath ?? '',
        expressionId: mapping.expressionId ?? ''
    };
}

function normalizeStringMap(value: Record<string, string> | undefined): Record<string, string> {
    return value === undefined || value === null ? {} : { ...value };
}
