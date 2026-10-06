import type {
    AchievementDefinition,
    ChallengeDefinition,
    GamificationBindingModel,
    GamificationDefinitionModel,
    GamificationDiagramDocument,
    GamificationDiagramLoadResult,
    GamificationLoadResult,
    GamificationRule,
    GamificationRuntimeSnapshot,
    LevelDefinition,
    PointTypeDefinition,
    RewardDefinition
} from './types';

/** Normaliza campos opcionales del contrato actual sin transformar esquemas anteriores. */
export function normalizeGamificationLoadResult(value: GamificationLoadResult): GamificationLoadResult {
    return {
        ...value,
        definitions: normalizeDefinitions(value.definitions),
        bindings: normalizeBindings(value.bindings),
        checkReport: { ...value.checkReport, diagnostics: value.checkReport?.diagnostics ?? [] }
    };
}

export function normalizeGamificationDiagramLoadResult(value: GamificationDiagramLoadResult): GamificationDiagramLoadResult {
    const diagram = value.diagram;
    return {
        ...value,
        etag: value.etag ?? '',
        diagram: {
            ...diagram,
            nodes: (diagram?.nodes ?? []).map(node => ({ ...node, width: number(node.width, 210), height: number(node.height, 84), collapsed: Boolean(node.collapsed) })),
            edges: (diagram?.edges ?? []).map(edge => ({
                ...edge,
                points: edge.points ?? [],
                sourceHandle: edge.sourceHandle ?? 'right-source',
                targetHandle: edge.targetHandle ?? 'left-target',
                label: edge.label ?? '',
                note: edge.note ?? '',
                routeStyle: edge.routeStyle ?? 'ORTHOGONAL',
                lineStyle: edge.lineStyle ?? 'SOLID',
                sourceTerminator: edge.sourceTerminator ?? 'NONE',
                targetTerminator: edge.targetTerminator ?? 'ARROW_CLOSED'
            }))
        } as GamificationDiagramDocument
    };
}

export function normalizeGamificationRuntimeSnapshot(value: GamificationRuntimeSnapshot): GamificationRuntimeSnapshot {
    return {
        ...value,
        stateEtag: value.stateEtag ?? '',
        ledgerEtag: value.ledgerEtag ?? '',
        state: {
            ...value.state,
            actors: value.state?.actors ?? {},
            processedAwardKeys: value.state?.processedAwardKeys ?? []
        },
        ledger: {
            ...value.ledger,
            entries: (value.ledger?.entries ?? []).map(entry => ({
                ...entry,
                pointsTypeId: entry.pointsTypeId ?? '',
                achievementId: entry.achievementId ?? '',
                challengeId: entry.challengeId ?? '',
                rewardId: entry.rewardId ?? '',
                compensationOf: entry.compensationOf ?? '',
                correlationId: entry.correlationId ?? '',
                metadata: entry.metadata ?? {}
            }))
        }
    };
}

function normalizeDefinitions(value: GamificationDefinitionModel): GamificationDefinitionModel {
    return {
        ...value,
        pointTypes: (value.pointTypes ?? []).map(normalizePointType),
        levels: (value.levels ?? []).map(normalizeLevel),
        achievements: (value.achievements ?? []).map(normalizeAchievement),
        challenges: (value.challenges ?? []).map(normalizeChallenge),
        rewards: (value.rewards ?? []).map(normalizeReward),
        annotations: value.annotations ?? {}
    };
}

function normalizeBindings(value: GamificationBindingModel): GamificationBindingModel {
    return { ...value, rules: (value.rules ?? []).map(normalizeRule), annotations: value.annotations ?? {} };
}

function normalizePointType(value: PointTypeDefinition): PointTypeDefinition {
    return { ...value, description: value.description ?? '', minimumBalance: number(value.minimumBalance, 0) };
}

function normalizeLevel(value: LevelDefinition): LevelDefinition {
    return { ...value, rewardId: value.rewardId ?? '', minimumPoints: number(value.minimumPoints, 0) };
}

function normalizeAchievement(value: AchievementDefinition): AchievementDefinition {
    return { ...value, description: value.description ?? '', rewardId: value.rewardId ?? '', hidden: value.hidden ?? false };
}

function normalizeChallenge(value: ChallengeDefinition): ChallengeDefinition {
    return { ...value, description: value.description ?? '', rewardId: value.rewardId ?? '', targetCount: number(value.targetCount, 1) };
}

function normalizeReward(value: RewardDefinition): RewardDefinition {
    return { ...value, description: value.description ?? '', payload: value.payload ?? {} };
}

function normalizeRule(value: GamificationRule): GamificationRule {
    const points = number(value.points, 0);
    return {
        ...value,
        conditionPath: value.conditionPath ?? '',
        operator: value.operator || 'EQUALS',
        pointsTypeId: value.pointsTypeId ?? '',
        points,
        achievementId: value.achievementId ?? '',
        challengeId: value.challengeId ?? '',
        rewardId: value.rewardId ?? '',
        effectKind: value.effectKind ?? (points < 0 ? 'PENALTY' : 'AWARD'),
        repeatPolicy: value.repeatPolicy ?? 'ONCE_PER_EVENT',
        cooldownSeconds: number(value.cooldownSeconds, 0),
        maxAwardsPerActor: number(value.maxAwardsPerActor, 0),
        maxAwardsPerDay: number(value.maxAwardsPerDay, 0),
        enabled: value.enabled ?? true
    };
}

function number(value: number | undefined, fallback: number): number {
    return Number.isFinite(value) ? Number(value) : fallback;
}
