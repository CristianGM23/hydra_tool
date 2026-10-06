import type { EventBinding, EventBindingModel, EventDefinitionModel, EventFieldDefinition, EventTypeDefinition } from './types';
let sequence = 0;
export function createEventDesignerId(prefix: string): string { sequence += 1; return `${prefix}:${Date.now().toString(36)}-${sequence.toString(36)}`; }
export function createEmptyEventModels(metamodelId: string): { definitions: EventDefinitionModel; bindings: EventBindingModel } {
    const definitionId = `${metamodelId}-events`;
    return {
        definitions: { schemaVersion: '1.0', id: definitionId, metamodelId, namespace: `editor.${metamodelId.replaceAll('-', '_')}`, eventTypes: [], annotations: {} },
        bindings: { schemaVersion: '1.0', id: `${metamodelId}-event-bindings`, metamodelId, eventDefinitionModelId: definitionId, expressionModelId: '', bindings: [], annotations: {} }
    };
}
export function createDefaultEventType(namespace: string): EventTypeDefinition {
    return { id: createEventDesignerId('event'), name: 'NewEvent', namespace, description: '', category: 'DOMAIN', version: '1.0', fields: [], allowedSources: [], allowedSubjects: [], persistencePolicy: 'TRANSIENT', abstractType: false, deprecated: false, enabled: true, annotations: {} };
}
export function createDefaultEventField(): EventFieldDefinition {
    return { id: createEventDesignerId('field'), name: 'value', type: 'STRING', required: false, many: false, enumTypeId: '', objectTypeId: '', description: '' };
}
export function createDefaultEventBinding(sourceEventTypeId: string, targetEventTypeId: string): EventBinding {
    return { id: createEventDesignerId('binding'), name: 'New event binding', sourceEventTypeId, targetEventTypeId, conditionExpressionId: '', payloadMappings: [], enabled: true, errorPolicy: 'REPORT', annotations: {} };
}
