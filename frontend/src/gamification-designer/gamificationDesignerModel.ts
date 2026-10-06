import { createAchievement, createChallenge, createGamificationId, createLevel, createPointType, createReward, createRule } from './gamificationFactory';
import type { GamificationSelection } from './GamificationDesignerParts';
import type { AchievementDefinition, ChallengeDefinition, GamificationBindingModel, GamificationDefinitionModel, GamificationRule, LevelDefinition, PointTypeDefinition, RewardDefinition } from './types';

export type GamificationItemKind = NonNullable<GamificationSelection>['kind'];
export type GamificationItem = PointTypeDefinition | LevelDefinition | AchievementDefinition | ChallengeDefinition | RewardDefinition | GamificationRule;

export interface GamificationModelMutation {
    definitions: GamificationDefinitionModel;
    bindings: GamificationBindingModel;
    selection: NonNullable<GamificationSelection>;
}

export function addGamificationItem(kind: GamificationItemKind, definitions: GamificationDefinitionModel,
        bindings: GamificationBindingModel): GamificationModelMutation | null {
    if (kind === 'LEVEL' && definitions.pointTypes[0] === undefined) return null;
    if (kind === 'POINT') {
        const item = createPointType();
        return { definitions: { ...definitions, pointTypes: [...definitions.pointTypes, item] }, bindings, selection: { kind, id: item.id } };
    }
    if (kind === 'REWARD') {
        const item = createReward();
        return { definitions: { ...definitions, rewards: [...definitions.rewards, item] }, bindings, selection: { kind, id: item.id } };
    }
    if (kind === 'LEVEL') {
        const item = createLevel(definitions.pointTypes[0]!.id);
        return { definitions: { ...definitions, levels: [...definitions.levels, item] }, bindings, selection: { kind, id: item.id } };
    }
    if (kind === 'ACHIEVEMENT') {
        const item = createAchievement();
        return { definitions: { ...definitions, achievements: [...definitions.achievements, item] }, bindings, selection: { kind, id: item.id } };
    }
    if (kind === 'CHALLENGE') {
        const item = createChallenge();
        return { definitions: { ...definitions, challenges: [...definitions.challenges, item] }, bindings, selection: { kind, id: item.id } };
    }
    const item = createRule('core.validation.violation-resolved', definitions.pointTypes[0]?.id ?? '');
    return { definitions, bindings: { ...bindings, rules: [...bindings.rules, item] }, selection: { kind, id: item.id } };
}

export function removeGamificationItem(target: NonNullable<GamificationSelection>, definitions: GamificationDefinitionModel,
        bindings: GamificationBindingModel): Pick<GamificationModelMutation, 'definitions' | 'bindings'> {
    const id = target.id;
    let nextDefinitions = definitions;
    let nextBindings = bindings;
    if (target.kind === 'POINT') {
        nextDefinitions = { ...definitions, pointTypes: definitions.pointTypes.filter(item => item.id !== id), levels: definitions.levels.filter(item => item.pointTypeId !== id) };
        nextBindings = { ...bindings, rules: bindings.rules.map(rule => rule.pointsTypeId === id ? { ...rule, pointsTypeId: '', points: 0 } : rule) };
    } else if (target.kind === 'LEVEL') {
        nextDefinitions = { ...definitions, levels: definitions.levels.filter(item => item.id !== id) };
    } else if (target.kind === 'ACHIEVEMENT') {
        nextDefinitions = { ...definitions, achievements: definitions.achievements.filter(item => item.id !== id) };
        nextBindings = { ...bindings, rules: bindings.rules.map(rule => rule.achievementId === id ? { ...rule, achievementId: '' } : rule) };
    } else if (target.kind === 'CHALLENGE') {
        nextDefinitions = { ...definitions, challenges: definitions.challenges.filter(item => item.id !== id) };
        nextBindings = { ...bindings, rules: bindings.rules.map(rule => rule.challengeId === id ? { ...rule, challengeId: '' } : rule) };
    } else if (target.kind === 'REWARD') {
        nextDefinitions = { ...definitions,
            rewards: definitions.rewards.filter(item => item.id !== id),
            levels: definitions.levels.map(level => level.rewardId === id ? { ...level, rewardId: '' } : level),
            achievements: definitions.achievements.map(item => item.rewardId === id ? { ...item, rewardId: '' } : item),
            challenges: definitions.challenges.map(item => item.rewardId === id ? { ...item, rewardId: '' } : item) };
        nextBindings = { ...bindings, rules: bindings.rules.map(rule => rule.rewardId === id ? { ...rule, rewardId: '' } : rule) };
    } else {
        nextBindings = { ...bindings, rules: bindings.rules.filter(item => item.id !== id) };
    }
    return { definitions: nextDefinitions, bindings: nextBindings };
}

export function renameGamificationItem(selection: NonNullable<GamificationSelection>, nextId: string,
        definitions: GamificationDefinitionModel, bindings: GamificationBindingModel): Pick<GamificationModelMutation, 'definitions' | 'bindings'> {
    const previousId = selection.id;
    let nextDefinitions = definitions;
    let nextBindings = bindings;
    if (selection.kind === 'POINT') {
        nextDefinitions = { ...definitions,
            pointTypes: definitions.pointTypes.map(item => item.id === previousId ? { ...item, id: nextId } : item),
            levels: definitions.levels.map(item => item.pointTypeId === previousId ? { ...item, pointTypeId: nextId } : item) };
        nextBindings = { ...bindings, rules: bindings.rules.map(item => item.pointsTypeId === previousId ? { ...item, pointsTypeId: nextId } : item) };
    } else if (selection.kind === 'LEVEL') {
        nextDefinitions = { ...definitions, levels: definitions.levels.map(item => item.id === previousId ? { ...item, id: nextId } : item) };
    } else if (selection.kind === 'ACHIEVEMENT') {
        nextDefinitions = { ...definitions, achievements: definitions.achievements.map(item => item.id === previousId ? { ...item, id: nextId } : item) };
        nextBindings = { ...bindings, rules: bindings.rules.map(item => item.achievementId === previousId ? { ...item, achievementId: nextId } : item) };
    } else if (selection.kind === 'CHALLENGE') {
        nextDefinitions = { ...definitions, challenges: definitions.challenges.map(item => item.id === previousId ? { ...item, id: nextId } : item) };
        nextBindings = { ...bindings, rules: bindings.rules.map(item => item.challengeId === previousId ? { ...item, challengeId: nextId } : item) };
    } else if (selection.kind === 'REWARD') {
        nextDefinitions = { ...definitions,
            rewards: definitions.rewards.map(item => item.id === previousId ? { ...item, id: nextId } : item),
            levels: definitions.levels.map(item => item.rewardId === previousId ? { ...item, rewardId: nextId } : item),
            achievements: definitions.achievements.map(item => item.rewardId === previousId ? { ...item, rewardId: nextId } : item),
            challenges: definitions.challenges.map(item => item.rewardId === previousId ? { ...item, rewardId: nextId } : item) };
        nextBindings = { ...bindings, rules: bindings.rules.map(item => item.rewardId === previousId ? { ...item, rewardId: nextId } : item) };
    } else {
        nextBindings = { ...bindings, rules: bindings.rules.map(item => item.id === previousId ? { ...item, id: nextId } : item) };
    }
    return { definitions: nextDefinitions, bindings: nextBindings };
}

export function duplicateGamificationItem(selection: NonNullable<GamificationSelection>, selected: GamificationItem,
        definitions: GamificationDefinitionModel, bindings: GamificationBindingModel): GamificationModelMutation {
    if (selection.kind === 'POINT') {
        const clone = { ...(selected as PointTypeDefinition), id: createGamificationId('points'), name: `${selected.name} copy` };
        return { definitions: { ...definitions, pointTypes: [...definitions.pointTypes, clone] }, bindings, selection: { kind: 'POINT', id: clone.id } };
    }
    if (selection.kind === 'LEVEL') {
        const clone = { ...(selected as LevelDefinition), id: createGamificationId('level'), name: `${selected.name} copy` };
        return { definitions: { ...definitions, levels: [...definitions.levels, clone] }, bindings, selection: { kind: 'LEVEL', id: clone.id } };
    }
    if (selection.kind === 'ACHIEVEMENT') {
        const clone = { ...(selected as AchievementDefinition), id: createGamificationId('achievement'), name: `${selected.name} copy` };
        return { definitions: { ...definitions, achievements: [...definitions.achievements, clone] }, bindings, selection: { kind: 'ACHIEVEMENT', id: clone.id } };
    }
    if (selection.kind === 'CHALLENGE') {
        const clone = { ...(selected as ChallengeDefinition), id: createGamificationId('challenge'), name: `${selected.name} copy` };
        return { definitions: { ...definitions, challenges: [...definitions.challenges, clone] }, bindings, selection: { kind: 'CHALLENGE', id: clone.id } };
    }
    if (selection.kind === 'REWARD') {
        const clone = { ...(selected as RewardDefinition), id: createGamificationId('reward'), name: `${selected.name} copy`, payload: { ...(selected as RewardDefinition).payload } };
        return { definitions: { ...definitions, rewards: [...definitions.rewards, clone] }, bindings, selection: { kind: 'REWARD', id: clone.id } };
    }
    const clone = { ...(selected as GamificationRule), id: createGamificationId('rule'), name: `${selected.name} copy` };
    return { definitions, bindings: { ...bindings, rules: [...bindings.rules, clone] }, selection: { kind: 'RULE', id: clone.id } };
}
