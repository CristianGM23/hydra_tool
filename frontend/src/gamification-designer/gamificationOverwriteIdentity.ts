import { hydraContentSignature, type HydraPersistedIdentity } from '../hooks/hydraOverwritePolicy';
import type { GamificationBindingModel, GamificationDefinitionModel } from './types';

function identity(entityKind: string, value: { id: string; name: string }): HydraPersistedIdentity {
    return {
        layer: 'Gamification',
        entityKind,
        stableId: value.id,
        uniqueName: value.name,
        contentSignature: hydraContentSignature(value),
        displayLabel: value.name || value.id
    };
}

export function gamificationOverwriteIdentities(definitions: GamificationDefinitionModel, bindings: GamificationBindingModel): HydraPersistedIdentity[] {
    return [
        ...definitions.pointTypes.map(value => identity('POINT_TYPE', value)),
        ...definitions.levels.map(value => identity('LEVEL', value)),
        ...definitions.achievements.map(value => identity('ACHIEVEMENT', value)),
        ...definitions.challenges.map(value => identity('CHALLENGE', value)),
        ...definitions.rewards.map(value => identity('REWARD', value)),
        ...bindings.rules.map(value => identity('GAMIFICATION_RULE', value))
    ];
}
