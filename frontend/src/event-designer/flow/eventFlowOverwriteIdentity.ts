import { hydraContentSignature, type HydraPersistedIdentity } from '../../hooks/hydraOverwritePolicy';
import type { EventFlowModel } from './types';

export function eventFlowOverwriteIdentities(model: EventFlowModel): HydraPersistedIdentity[] {
    return model.flows.map(flow => ({
        layer: 'Event flows',
        entityKind: 'EVENT_FLOW',
        stableId: flow.id,
        uniqueName: flow.name,
        contentSignature: hydraContentSignature(flow),
        displayLabel: flow.name || flow.id
    }));
}
