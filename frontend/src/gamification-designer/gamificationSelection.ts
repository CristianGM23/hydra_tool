import type { GamificationEdgeData } from './gamificationDiagram';
import type { GamificationBindingModel, GamificationDefinitionModel, GamificationDiagramNodeKind } from './types';

export interface GamificationSelectionMutation {
    definitions: GamificationDefinitionModel;
    bindings: GamificationBindingModel;
    ignoredLedgerItems: number;
}

/** Aplica en lote el borrado de nodos seleccionados manteniendo referencias canónicas coherentes. */
export function removeSelectedGamificationNodes(definitions: GamificationDefinitionModel, bindings: GamificationBindingModel, nodeIds: readonly string[]): GamificationSelectionMutation {
    const pointIds = new Set<string>();
    const levelIds = new Set<string>();
    const achievementIds = new Set<string>();
    const challengeIds = new Set<string>();
    const rewardIds = new Set<string>();
    const ruleIds = new Set<string>();
    const evidenceIds = new Set<string>();
    let ignoredLedgerItems = 0;

    for (const nodeId of nodeIds) {
        const parsed = parseNodeId(nodeId);
        if (parsed === null) continue;
        if (parsed.kind === 'POINT') pointIds.add(parsed.elementId);
        else if (parsed.kind === 'LEVEL') levelIds.add(parsed.elementId);
        else if (parsed.kind === 'ACHIEVEMENT') achievementIds.add(parsed.elementId);
        else if (parsed.kind === 'CHALLENGE') challengeIds.add(parsed.elementId);
        else if (parsed.kind === 'REWARD') rewardIds.add(parsed.elementId);
        else if (parsed.kind === 'RULE') ruleIds.add(parsed.elementId);
        else if (parsed.kind === 'EVIDENCE') evidenceIds.add(parsed.elementId);
        else if (parsed.kind === 'LEDGER') ignoredLedgerItems++;
    }

    const removedLevelIds = new Set(levelIds);
    for (const level of definitions.levels) if (pointIds.has(level.pointTypeId)) removedLevelIds.add(level.id);

    const nextDefinitions: GamificationDefinitionModel = {
        ...definitions,
        pointTypes: definitions.pointTypes.filter(item => !pointIds.has(item.id)),
        levels: definitions.levels.filter(item => !removedLevelIds.has(item.id)).map(item => rewardIds.has(item.rewardId) ? { ...item, rewardId: '' } : item),
        achievements: definitions.achievements.filter(item => !achievementIds.has(item.id)).map(item => rewardIds.has(item.rewardId) ? { ...item, rewardId: '' } : item),
        challenges: definitions.challenges.filter(item => !challengeIds.has(item.id)).map(item => rewardIds.has(item.rewardId) ? { ...item, rewardId: '' } : item),
        rewards: definitions.rewards.filter(item => !rewardIds.has(item.id))
    };
    const nextBindings: GamificationBindingModel = {
        ...bindings,
        rules: bindings.rules.filter(rule => !ruleIds.has(rule.id)).map(rule => ({
            ...rule,
            eventTypeId: evidenceIds.has(rule.eventTypeId) ? '' : rule.eventTypeId,
            pointsTypeId: pointIds.has(rule.pointsTypeId) ? '' : rule.pointsTypeId,
            points: pointIds.has(rule.pointsTypeId) ? 0 : rule.points,
            achievementId: achievementIds.has(rule.achievementId) ? '' : rule.achievementId,
            challengeId: challengeIds.has(rule.challengeId) ? '' : rule.challengeId,
            rewardId: rewardIds.has(rule.rewardId) ? '' : rule.rewardId
        }))
    };
    return { definitions: nextDefinitions, bindings: nextBindings, ignoredLedgerItems };
}

/** Aplica en lote el borrado de relaciones seleccionadas sin ejecutar varias mutaciones de estado consecutivas. */
export function removeSelectedGamificationRelations(definitions: GamificationDefinitionModel, bindings: GamificationBindingModel, edges: readonly { data?: GamificationEdgeData }[]): GamificationSelectionMutation {
    let nextDefinitions = definitions;
    let nextBindings = bindings;
    let ignoredLedgerItems = 0;
    for (const edge of edges) {
        const data = edge.data;
        if (data === undefined) continue;
        if (data.targetKind === 'LEDGER') { ignoredLedgerItems++; continue; }
        if (data.sourceKind === 'EVIDENCE' && data.targetKind === 'RULE') nextBindings = { ...nextBindings, rules: nextBindings.rules.map(rule => rule.id === data.targetElementId ? { ...rule, eventTypeId: '' } : rule) };
        else if (data.sourceKind === 'RULE' && data.targetKind === 'POINT') nextBindings = { ...nextBindings, rules: nextBindings.rules.map(rule => rule.id === data.sourceElementId ? { ...rule, pointsTypeId: '', points: 0 } : rule) };
        else if (data.sourceKind === 'RULE' && data.targetKind === 'ACHIEVEMENT') nextBindings = { ...nextBindings, rules: nextBindings.rules.map(rule => rule.id === data.sourceElementId ? { ...rule, achievementId: '' } : rule) };
        else if (data.sourceKind === 'RULE' && data.targetKind === 'CHALLENGE') nextBindings = { ...nextBindings, rules: nextBindings.rules.map(rule => rule.id === data.sourceElementId ? { ...rule, challengeId: '' } : rule) };
        else if (data.sourceKind === 'RULE' && data.targetKind === 'REWARD') nextBindings = { ...nextBindings, rules: nextBindings.rules.map(rule => rule.id === data.sourceElementId ? { ...rule, rewardId: '' } : rule) };
        else if (data.sourceKind === 'POINT' && data.targetKind === 'LEVEL') nextDefinitions = { ...nextDefinitions, levels: nextDefinitions.levels.map(level => level.id === data.targetElementId ? { ...level, pointTypeId: '' } : level) };
        else if (data.targetKind === 'REWARD' && data.sourceKind === 'LEVEL') nextDefinitions = { ...nextDefinitions, levels: nextDefinitions.levels.map(level => level.id === data.sourceElementId ? { ...level, rewardId: '' } : level) };
        else if (data.targetKind === 'REWARD' && data.sourceKind === 'ACHIEVEMENT') nextDefinitions = { ...nextDefinitions, achievements: nextDefinitions.achievements.map(item => item.id === data.sourceElementId ? { ...item, rewardId: '' } : item) };
        else if (data.targetKind === 'REWARD' && data.sourceKind === 'CHALLENGE') nextDefinitions = { ...nextDefinitions, challenges: nextDefinitions.challenges.map(item => item.id === data.sourceElementId ? { ...item, rewardId: '' } : item) };
    }
    return { definitions: nextDefinitions, bindings: nextBindings, ignoredLedgerItems };
}


function parseNodeId(nodeId: string): { kind: GamificationDiagramNodeKind; elementId: string } | null {
    const separator = nodeId.indexOf(':');
    if (separator <= 0) return null;
    const kind = nodeId.slice(0, separator).toUpperCase() as GamificationDiagramNodeKind;
    if (!['EVIDENCE','RULE','POINT','LEVEL','ACHIEVEMENT','CHALLENGE','REWARD','LEDGER'].includes(kind)) return null;
    return { kind, elementId: nodeId.slice(separator + 1) };
}
