import type { AchievementDefinition, ChallengeDefinition, GamificationBindingModel, GamificationDefinitionModel, GamificationRule, LevelDefinition, PointTypeDefinition, RewardDefinition } from './types';
let sequence = 0;
export function createGamificationId(prefix: string): string { sequence += 1; return `${prefix}:${Date.now().toString(36)}-${sequence.toString(36)}`; }
export function createEmptyGamificationModels(metamodelId: string): { definitions: GamificationDefinitionModel; bindings: GamificationBindingModel } {
    const definitionId = `${metamodelId}-gamification`;
    return {
        definitions: { schemaVersion: '1.0', id: definitionId, metamodelId, name: 'Optional gamification', pointTypes: [], levels: [], achievements: [], challenges: [], rewards: [], annotations: {} },
        bindings: { schemaVersion: '1.0', id: `${metamodelId}-gamification-bindings`, metamodelId, definitionModelId: definitionId, rules: [], annotations: {} }
    };
}
export function createPointType(): PointTypeDefinition { return { id: createGamificationId('points'), name: 'Quality points', description: '', minimumBalance: 0 }; }
export function createReward(): RewardDefinition { return { id: createGamificationId('reward'), name: 'New reward', description: '', payload: {} }; }
export function createLevel(pointTypeId: string): LevelDefinition { return { id: createGamificationId('level'), name: 'New level', pointTypeId, minimumPoints: 0, rewardId: '' }; }
export function createAchievement(): AchievementDefinition { return { id: createGamificationId('achievement'), name: 'New achievement', description: '', rewardId: '', hidden: false }; }
export function createChallenge(): ChallengeDefinition { return { id: createGamificationId('challenge'), name: 'New challenge', description: '', targetCount: 1, rewardId: '' }; }
export function createRule(eventTypeId: string, pointTypeId: string): GamificationRule { return { id: createGamificationId('rule'), name: 'Verified evidence rule', eventTypeId: eventTypeId || 'core.validation.violation-resolved', conditionPath: '', operator: 'EQUALS', pointsTypeId: pointTypeId, points: 1, achievementId: '', challengeId: '', rewardId: '', effectKind: 'AWARD', repeatPolicy: 'ONCE_PER_EVENT', cooldownSeconds: 0, maxAwardsPerActor: 0, maxAwardsPerDay: 0, enabled: true }; }
