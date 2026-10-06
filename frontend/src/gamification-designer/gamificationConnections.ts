import type { GamificationBindingModel, GamificationDefinitionModel, GamificationDiagramNodeKind } from './types';

export interface GamificationConnectionResult {
    accepted: boolean;
    definitions: GamificationDefinitionModel;
    bindings: GamificationBindingModel;
    edgeId: string;
    message: string;
}

export function applyGamificationConnection(definitions: GamificationDefinitionModel, bindings: GamificationBindingModel, sourceNodeId: string, targetNodeId: string): GamificationConnectionResult {
    const source = parseGamificationConnectionNodeId(sourceNodeId);
    const target = parseGamificationConnectionNodeId(targetNodeId);
    const rejected = (message: string): GamificationConnectionResult => ({ accepted: false, definitions, bindings, edgeId: '', message });
    if (source === null || target === null || source.elementId === target.elementId) return rejected('Choose two different valid gamification elements.');
    if (source.kind === 'EVIDENCE' && target.kind === 'RULE') {
        return bindingUpdate(bindings.rules.some(rule => rule.id === target.elementId), definitions, bindings, { ...bindings, rules: bindings.rules.map(rule => rule.id === target.elementId ? { ...rule, eventTypeId: source.elementId } : rule) }, `evidence-rule:${target.elementId}`);
    }
    if (source.kind === 'RULE' && target.kind === 'POINT') {
        return bindingUpdate(bindings.rules.some(rule => rule.id === source.elementId), definitions, bindings, { ...bindings, rules: bindings.rules.map(rule => rule.id === source.elementId ? { ...rule, pointsTypeId: target.elementId } : rule) }, `rule-points:${source.elementId}`);
    }
    if (source.kind === 'RULE' && target.kind === 'ACHIEVEMENT') {
        return bindingUpdate(bindings.rules.some(rule => rule.id === source.elementId), definitions, bindings, { ...bindings, rules: bindings.rules.map(rule => rule.id === source.elementId ? { ...rule, achievementId: target.elementId } : rule) }, `rule-achievement:${source.elementId}`);
    }
    if (source.kind === 'RULE' && target.kind === 'CHALLENGE') {
        return bindingUpdate(bindings.rules.some(rule => rule.id === source.elementId), definitions, bindings, { ...bindings, rules: bindings.rules.map(rule => rule.id === source.elementId ? { ...rule, challengeId: target.elementId } : rule) }, `rule-challenge:${source.elementId}`);
    }
    if (source.kind === 'RULE' && target.kind === 'REWARD') {
        return bindingUpdate(bindings.rules.some(rule => rule.id === source.elementId), definitions, bindings, { ...bindings, rules: bindings.rules.map(rule => rule.id === source.elementId ? { ...rule, rewardId: target.elementId } : rule) }, `rule-reward:${source.elementId}`);
    }
    if (source.kind === 'POINT' && target.kind === 'LEVEL') {
        const exists = definitions.levels.some(level => level.id === target.elementId);
        return definitionUpdate(exists, definitions, { ...definitions, levels: definitions.levels.map(level => level.id === target.elementId ? { ...level, pointTypeId: source.elementId } : level) }, bindings, `point-level:${target.elementId}`);
    }
    if (source.kind === 'LEVEL' && target.kind === 'REWARD') {
        const exists = definitions.levels.some(level => level.id === source.elementId);
        return definitionUpdate(exists, definitions, { ...definitions, levels: definitions.levels.map(level => level.id === source.elementId ? { ...level, rewardId: target.elementId } : level) }, bindings, `level-reward:${source.elementId}`);
    }
    if (source.kind === 'ACHIEVEMENT' && target.kind === 'REWARD') {
        const exists = definitions.achievements.some(item => item.id === source.elementId);
        return definitionUpdate(exists, definitions, { ...definitions, achievements: definitions.achievements.map(item => item.id === source.elementId ? { ...item, rewardId: target.elementId } : item) }, bindings, `achievement-reward:${source.elementId}`);
    }
    if (source.kind === 'CHALLENGE' && target.kind === 'REWARD') {
        const exists = definitions.challenges.some(item => item.id === source.elementId);
        return definitionUpdate(exists, definitions, { ...definitions, challenges: definitions.challenges.map(item => item.id === source.elementId ? { ...item, rewardId: target.elementId } : item) }, bindings, `challenge-reward:${source.elementId}`);
    }
    return rejected('Connect evidence to rules, rules to outcomes, points to levels, or progression elements to rewards.');
}

function bindingUpdate(exists: boolean, definitions: GamificationDefinitionModel, original: GamificationBindingModel, updated: GamificationBindingModel, edgeId: string): GamificationConnectionResult {
    return exists ? { accepted: true, definitions, bindings: updated, edgeId, message: '' } : { accepted: false, definitions, bindings: original, edgeId: '', message: 'The selected rule no longer exists.' };
}

function definitionUpdate(exists: boolean, original: GamificationDefinitionModel, updated: GamificationDefinitionModel, bindings: GamificationBindingModel, edgeId: string): GamificationConnectionResult {
    return exists ? { accepted: true, definitions: updated, bindings, edgeId, message: '' } : { accepted: false, definitions: original, bindings, edgeId: '', message: 'The selected progression element no longer exists.' };
}

function parseGamificationConnectionNodeId(nodeId: string): { kind: GamificationDiagramNodeKind; elementId: string } | null {
    const separator = nodeId.indexOf(':');
    if (separator <= 0) return null;
    const kind = nodeId.slice(0, separator).toUpperCase() as GamificationDiagramNodeKind;
    if (!['EVIDENCE', 'RULE', 'POINT', 'LEVEL', 'ACHIEVEMENT', 'CHALLENGE', 'REWARD', 'LEDGER'].includes(kind)) return null;
    return { kind, elementId: nodeId.slice(separator + 1) };
}
