import type { EventBindingModel, EventDefinitionModel } from './types';
const documentId = /^[A-Za-z0-9][A-Za-z0-9._-]{0,119}$/;
const elementId = /^[A-Za-z0-9][A-Za-z0-9._:-]{0,239}$/;
export function findEventDesignerBlockingError(definitions: EventDefinitionModel, bindings: EventBindingModel): string {
    if (!documentId.test(definitions.id) || !documentId.test(bindings.id)) return 'Event document identifiers are invalid.';
    if (definitions.metamodelId !== bindings.metamodelId) return 'Definitions and bindings must belong to the same metamodel.';
    if (bindings.eventDefinitionModelId !== definitions.id) return 'The binding model must reference the event definition model.';
    if (!/^[A-Za-z][A-Za-z0-9_.-]*$/.test(definitions.namespace)) return 'The event namespace is invalid.';
    const typeIds = new Set<string>();
    for (const type of definitions.eventTypes) {
        if (!elementId.test(type.id)) return `Event type "${type.name}" has an invalid identifier.`;
        if (type.name.trim() === '') return 'Every event type requires a name.';
        if (typeIds.has(type.id)) return `Event type identifier "${type.id}" is duplicated.`;
        typeIds.add(type.id);
        const fieldIds = new Set<string>();
        for (const field of type.fields) {
            if (fieldIds.has(field.id)) return `Event field identifier "${field.id}" is duplicated.`;
            fieldIds.add(field.id);
        }
    }
    const bindingIds = new Set<string>();
    for (const binding of bindings.bindings) {
        if (bindingIds.has(binding.id)) return `Event binding identifier "${binding.id}" is duplicated.`;
        bindingIds.add(binding.id);
        if (!typeIds.has(binding.targetEventTypeId)) return `Binding "${binding.name}" must target a custom event type.`;
        const target = definitions.eventTypes.find(type => type.id === binding.targetEventTypeId);
        const mapped = new Set(binding.payloadMappings.map(mapping => mapping.targetFieldId));
        const missing = target?.fields.find(field => field.required && !mapped.has(field.id));
        if (missing !== undefined) return `Binding "${binding.name}" must map required field "${missing.name}".`;
    }
    return '';
}
