import type { AchievementDefinition, ChallengeDefinition, GamificationBindingModel, GamificationDefinitionModel, GamificationRule, LevelDefinition, PointTypeDefinition, RewardDefinition } from './types';

export type GamificationSelection = { kind: 'POINT' | 'LEVEL' | 'ACHIEVEMENT' | 'CHALLENGE' | 'REWARD' | 'RULE'; id: string } | null;

const KINDS: NonNullable<GamificationSelection>['kind'][] = ['POINT', 'LEVEL', 'ACHIEVEMENT', 'CHALLENGE', 'REWARD', 'RULE'];

export const GAMIFICATION_KIND_LABELS: Record<NonNullable<GamificationSelection>['kind'], string> = {
    POINT: 'Point types', LEVEL: 'Levels', ACHIEVEMENT: 'Achievements', CHALLENGE: 'Challenges', REWARD: 'Rewards', RULE: 'Evidence rules'
};

export interface GamificationKindGuidance {
    purpose: string;
    creationRequirements: string;
    dependencies: string;
    runtimeUse: string;
}

/** Ayuda funcional de alto nivel para cada pieza de la paleta de gamificación. */
export const GAMIFICATION_KIND_GUIDANCE: Record<NonNullable<GamificationSelection>['kind'], GamificationKindGuidance> = {
    POINT: {
        purpose: 'Defines a named point balance or score used to measure verified behaviour. Point types are the base currency of progression and penalties.',
        creationRequirements: 'No previous gamification element is required. Create point types before levels and before any evidence rule that changes points.',
        dependencies: 'Levels require a point type. Evidence rules require a point type only when they add or subtract points. Removing a point type also removes its levels and clears point references from rules.',
        runtimeUse: 'Runtime evidence rules update the selected point balance. The minimum balance can prevent penalties from reducing the balance below an allowed floor.'
    },
    LEVEL: {
        purpose: 'Defines a progression threshold reached when an actor accumulates enough points of one selected point type.',
        creationRequirements: 'Requires at least one point type. HYDRA disables level creation until a point type exists.',
        dependencies: 'A level must reference an existing point type. A reward is optional and can be attached later.',
        runtimeUse: 'The runtime derives the actor level from the selected point balance and its minimum-points threshold. Levels do not need a separate evidence rule because rules change the underlying points.'
    },
    ACHIEVEMENT: {
        purpose: 'Defines a named milestone or badge that can be granted when verified evidence proves that a condition was achieved.',
        creationRequirements: 'No point type, level, challenge or reward is required to create an achievement.',
        dependencies: 'A reward is optional. To grant the achievement automatically, at least one evidence rule must reference it and that rule must use a verified event.',
        runtimeUse: 'When a matching award rule is applied, the achievement is added to the actor state. Hidden achievements can exist without being revealed in advance by the generated UI.'
    },
    CHALLENGE: {
        purpose: 'Defines a repeatable objective that progresses toward a target count when verified evidence is received.',
        creationRequirements: 'No other gamification definition is required to create a challenge. Its target count must describe the amount of verified progress needed.',
        dependencies: 'A reward is optional. To make progress automatically, at least one evidence rule must reference the challenge.',
        runtimeUse: 'Each matching award rule advances challenge progress. Penalty rules cannot grant or advance challenges.'
    },
    REWARD: {
        purpose: 'Defines a portable reward or entitlement that other gamification elements can grant. Its payload carries target-specific reward data without changing the metamodel.',
        creationRequirements: 'No other gamification element is required. A reward can be defined first and referenced later.',
        dependencies: 'Levels, achievements, challenges and award evidence rules can optionally reference a reward. Deleting a reward clears those references instead of leaving broken links.',
        runtimeUse: 'The runtime records granted reward IDs in actor state and the ledger. How a target application presents or redeems the payload is handled by its integration.'
    },
    RULE: {
        purpose: 'Connects a verified HYDRA event to a gamification effect: award or subtract points, grant an achievement or reward, or advance a challenge.',
        creationRequirements: 'A rule must reference a known verified event. HYDRA includes reserved system events; custom events must first be created and saved in Event Designer.',
        dependencies: 'A point type is required only when the rule changes points. Achievements, challenges and rewards are optional targets, but every referenced target must already exist. Penalty rules can only subtract points.',
        runtimeUse: 'The rule is evaluated when its event arrives. Repeat policy, cooldown and per-actor/per-day limits control idempotence and anti-farming behaviour. A rule with no effect is valid only as a warning and should normally be completed or removed.'
    }
};

/** Texto estable para el estado vacío de una categoría. */
export function emptyGamificationKindLabel(kind: NonNullable<GamificationSelection>['kind']): string {
    if (kind === 'POINT') return 'No point types defined.';
    if (kind === 'LEVEL') return 'No levels defined.';
    if (kind === 'ACHIEVEMENT') return 'No achievements defined.';
    if (kind === 'CHALLENGE') return 'No challenges defined.';
    if (kind === 'REWARD') return 'No rewards defined.';
    return 'No evidence rules defined.';
}


/** Fusiona elementos de gamificación por identificador estable; los importados prevalecen. */
export function mergeGamificationItems<T extends { id: string }>(current: readonly T[], imported: readonly T[]): T[] {
    const merged = new Map(current.map(item => [item.id, item]));
    for (const item of imported) merged.set(item.id, item);
    return [...merged.values()];
}

/** Devuelve los elementos correspondientes a una categoría de gamificación. */
export function items(kind: NonNullable<GamificationSelection>['kind'], definitions: GamificationDefinitionModel, bindings: GamificationBindingModel): Array<{ id: string; name: string }> {
    if (kind === 'POINT') return definitions.pointTypes;
    if (kind === 'LEVEL') return definitions.levels;
    if (kind === 'ACHIEVEMENT') return definitions.achievements;
    if (kind === 'CHALLENGE') return definitions.challenges;
    if (kind === 'REWARD') return definitions.rewards;
    return bindings.rules;
}

/** Localiza el elemento actualmente seleccionado. */
export function findSelected(selection: GamificationSelection, definitions: GamificationDefinitionModel, bindings: GamificationBindingModel): PointTypeDefinition | LevelDefinition | AchievementDefinition | ChallengeDefinition | RewardDefinition | GamificationRule | null {
    if (selection === null) return null;
    const selected = items(selection.kind, definitions, bindings).find(item => item.id === selection.id) as PointTypeDefinition | LevelDefinition | AchievementDefinition | ChallengeDefinition | RewardDefinition | GamificationRule | undefined;
    return selected ?? null;
}

/** Selecciona el primer elemento disponible siguiendo el orden funcional. */
export function firstSelection(definitions: GamificationDefinitionModel, bindings: GamificationBindingModel): GamificationSelection {
    for (const kind of KINDS) {
        const item = items(kind, definitions, bindings)[0];
        if (item !== undefined) return { kind, id: item.id };
    }
    return null;
}

/** Cuenta todas las definiciones funcionales de gamificación. */
export function totalItems(definitions: GamificationDefinitionModel, bindings: GamificationBindingModel): number {
    return definitions.pointTypes.length + definitions.levels.length + definitions.achievements.length + definitions.challenges.length + definitions.rewards.length + bindings.rules.length;
}

/** Obtiene la etiqueta singular legible de una categoría de gamificación. */
export function singularLabel(kind: NonNullable<GamificationSelection>['kind']): string {
    if (kind === 'POINT') return 'Point type';
    if (kind === 'LEVEL') return 'Level';
    if (kind === 'ACHIEVEMENT') return 'Achievement';
    if (kind === 'CHALLENGE') return 'Challenge';
    if (kind === 'REWARD') return 'Reward';
    return 'Evidence rule';
}

/** Localiza una selección por ID siguiendo la semántica de las categorías. */
export function firstSelectionById(definitions: GamificationDefinitionModel, bindings: GamificationBindingModel, id: string): GamificationSelection {
    for (const kind of ['RULE','POINT','LEVEL','ACHIEVEMENT','CHALLENGE','REWARD'] as const) {
        if (items(kind, definitions, bindings).some(item => item.id === id)) return { kind, id };
    }
    return null;
}
