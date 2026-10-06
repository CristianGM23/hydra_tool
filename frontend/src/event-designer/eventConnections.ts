import { createDefaultEventBinding } from './eventDesignerFactory';
import type { EventBinding, EventBindingModel, EventDefinitionModel } from './types';

export interface EventConnectionResult {
    accepted: boolean;
    bindings: EventBindingModel;
    binding: EventBinding | null;
    message: string;
}

export function applyEventConnection(definitions: EventDefinitionModel, bindings: EventBindingModel, knownSourceEventTypeIds: string[], sourceEventTypeId: string, targetEventTypeId: string): EventConnectionResult {
    if (!knownSourceEventTypeIds.includes(sourceEventTypeId)) return rejected(bindings, 'The selected source event is not present in the event catalog.');
    if (!definitions.eventTypes.some(type => type.id === targetEventTypeId)) return rejected(bindings, 'A binding target must be a custom event type.');
    if (sourceEventTypeId === targetEventTypeId) return rejected(bindings, 'Source and target event types must be different.');
    if (bindings.bindings.some(binding => binding.sourceEventTypeId === sourceEventTypeId && binding.targetEventTypeId === targetEventTypeId)) return rejected(bindings, 'A binding between those event types already exists.');
    const binding = createDefaultEventBinding(sourceEventTypeId, targetEventTypeId);
    return { accepted: true, bindings: { ...bindings, bindings: [...bindings.bindings, binding] }, binding, message: '' };
}

function rejected(bindings: EventBindingModel, message: string): EventConnectionResult {
    return { accepted: false, bindings, binding: null, message };
}
