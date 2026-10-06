import { createDefaultEventType } from './eventDesignerFactory';
import type { EventBinding, EventBindingModel, EventDefinitionModel, EventFieldDefinition, EventTypeDefinition } from './types';

export function replaceEventType(definitions: EventDefinitionModel, type: EventTypeDefinition): EventDefinitionModel {
    return { ...definitions, eventTypes: definitions.eventTypes.map(item => item.id === type.id ? type : item) };
}

export function renameEventType(definitions: EventDefinitionModel, bindings: EventBindingModel, previousId: string, nextId: string): { definitions: EventDefinitionModel; bindings: EventBindingModel } {
    if (previousId === nextId) return { definitions, bindings };
    return {
        definitions: { ...definitions, eventTypes: definitions.eventTypes.map(item => item.id === previousId ? { ...item, id: nextId } : item) },
        bindings: { ...bindings, bindings: bindings.bindings.map(binding => ({
            ...binding,
            sourceEventTypeId: binding.sourceEventTypeId === previousId ? nextId : binding.sourceEventTypeId,
            targetEventTypeId: binding.targetEventTypeId === previousId ? nextId : binding.targetEventTypeId
        })) }
    };
}

export function replaceEventField(type: EventTypeDefinition, field: EventFieldDefinition): EventTypeDefinition {
    return { ...type, fields: type.fields.map(item => item.id === field.id ? field : item) };
}

export function renameEventField(type: EventTypeDefinition, bindings: EventBindingModel, previousId: string, nextId: string): { type: EventTypeDefinition; bindings: EventBindingModel } {
    if (previousId === nextId) return { type, bindings };
    return {
        type: { ...type, fields: type.fields.map(item => item.id === previousId ? { ...item, id: nextId } : item) },
        bindings: { ...bindings, bindings: bindings.bindings.map(binding => binding.targetEventTypeId === type.id
            ? { ...binding, payloadMappings: binding.payloadMappings.map(mapping => mapping.targetFieldId === previousId ? { ...mapping, targetFieldId: nextId } : mapping) }
            : binding) }
    };
}

export function replaceEventBinding(bindings: EventBindingModel, binding: EventBinding): EventBindingModel {
    return { ...bindings, bindings: bindings.bindings.map(item => item.id === binding.id ? binding : item) };
}

export function renameEventBinding(bindings: EventBindingModel, previousId: string, nextId: string): EventBindingModel {
    if (previousId === nextId) return bindings;
    return { ...bindings, bindings: bindings.bindings.map(item => item.id === previousId ? { ...item, id: nextId } : item) };
}

export function appendEventType(definitions: EventDefinitionModel): { definitions: EventDefinitionModel; type: EventTypeDefinition } {
    const type = createDefaultEventType(definitions.namespace);
    const existingNames = new Set(definitions.eventTypes.map(item => item.name.trim()).filter(Boolean));
    let nextName = 'NewEvent';
    let suffix = 2;
    while (existingNames.has(nextName)) {
        nextName = `NewEvent ${suffix}`;
        suffix += 1;
    }
    type.name = nextName;
    return { definitions: { ...definitions, eventTypes: [...definitions.eventTypes, type] }, type };
}

export function removeEventType(definitions: EventDefinitionModel, bindings: EventBindingModel, typeId: string): { definitions: EventDefinitionModel; bindings: EventBindingModel } {
    return {
        definitions: { ...definitions, eventTypes: definitions.eventTypes.filter(type => type.id !== typeId) },
        bindings: { ...bindings, bindings: bindings.bindings.filter(binding => binding.targetEventTypeId !== typeId && binding.sourceEventTypeId !== typeId) }
    };
}

export function removeEventBinding(bindings: EventBindingModel, bindingId: string): EventBindingModel {
    return { ...bindings, bindings: bindings.bindings.filter(binding => binding.id !== bindingId) };
}
