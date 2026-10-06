import { hydraContentSignature, type HydraPersistedIdentity } from '../hooks/hydraOverwritePolicy';
import type { EventBindingModel, EventDefinitionModel } from './types';

export function eventOverwriteIdentities(definitions: EventDefinitionModel, bindings: EventBindingModel): HydraPersistedIdentity[] {
    const events = definitions.eventTypes.map(eventType => ({
        layer: 'Event definitions',
        entityKind: 'EVENT_TYPE',
        stableId: eventType.id,
        uniqueName: eventType.name,
        contentSignature: hydraContentSignature(eventType),
        displayLabel: eventType.name || eventType.id
    } satisfies HydraPersistedIdentity));
    const eventBindings = bindings.bindings.map(binding => ({
        layer: 'Event definitions',
        entityKind: 'EVENT_BINDING',
        stableId: binding.id,
        uniqueName: binding.name,
        contentSignature: hydraContentSignature(binding),
        displayLabel: binding.name || binding.id
    } satisfies HydraPersistedIdentity));
    return [...events, ...eventBindings];
}
