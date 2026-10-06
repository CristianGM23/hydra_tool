import type { GamificationBindingModel, GamificationDefinitionModel } from './types';
export function findGamificationBlockingError(definitions: GamificationDefinitionModel, bindings: GamificationBindingModel): string {
    if(definitions.id.trim() === '' || bindings.id.trim() === '') return 'Gamification document IDs are required.';
    const allIds = [...definitions.pointTypes, ...definitions.levels, ...definitions.achievements, ...definitions.challenges, ...definitions.rewards].map(item => item.id);
    if(new Set(allIds).size !== allIds.length) return 'Gamification element IDs must be unique.';
    const pointTypes = new Set(definitions.pointTypes.map(item => item.id));
    const rewards = new Set(definitions.rewards.map(item => item.id));
    if(definitions.levels.some(level => !pointTypes.has(level.pointTypeId))) return 'Every level must reference an existing point type.';
    if(definitions.levels.some(level => level.rewardId !== '' && !rewards.has(level.rewardId))) return 'Every level reward must exist.';
    if(bindings.rules.some(rule => rule.eventTypeId === 'core.quickfix.applied' && (rule.points !== 0 || rule.rewardId !== '' || rule.achievementId !== '' || rule.challengeId !== ''))) return 'Quick-fix application is not verified evidence. Bind rewards to core.validation.violation-resolved.';
    if(bindings.rules.some(rule => rule.pointsTypeId !== '' && !pointTypes.has(rule.pointsTypeId))) return 'Every points rule must reference an existing point type.';
    if(bindings.rules.some(rule => rule.effectKind === 'PENALTY' && (rule.achievementId !== '' || rule.challengeId !== '' || rule.rewardId !== ''))) return 'Penalty rules can only reduce a point balance; they cannot grant achievements, challenges or rewards.';
    return '';
}
