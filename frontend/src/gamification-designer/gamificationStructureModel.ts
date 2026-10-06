import type { DesignerStructureNode } from '../designer-ui/DesignerStructureTree';
import { presentEventType } from '../event-designer/eventTypePresentation';
import type { GamificationBindingModel, GamificationDefinitionModel } from './types';
import type { GamificationStructureSelection } from './gamificationStructureTypes';

export function buildGamificationStructure(definitions: GamificationDefinitionModel, bindings: GamificationBindingModel): DesignerStructureNode<GamificationStructureSelection>[] {
    const points = definitions.pointTypes.map(item => selectable('POINT', item.id, item.name, item.description || item.id, 'POINT TYPE'));
    const rewards = definitions.rewards.map(item => selectable('REWARD', item.id, item.name, item.description || item.id, 'REWARD'));
    const levels = definitions.levels.map(item => ({
        ...selectable('LEVEL', item.id, item.name, `Threshold ${item.minimumPoints}`, 'LEVEL'),
        children: [
            relationChild('Point type', item.pointTypeId, 'POINT', item.id, 'LEVEL'),
            relationChild('Reward', item.rewardId, 'REWARD', item.id, 'LEVEL')
        ].filter(hasDetail)
    }));
    const achievements = definitions.achievements.map(item => ({
        ...selectable('ACHIEVEMENT', item.id, item.name, item.description || item.id, item.hidden ? 'HIDDEN' : 'ACHIEVEMENT'),
        children: [relationChild('Reward', item.rewardId, 'REWARD', item.id, 'ACHIEVEMENT')].filter(hasDetail)
    }));
    const challenges = definitions.challenges.map(item => ({
        ...selectable('CHALLENGE', item.id, item.name, `Target ${item.targetCount}`, 'CHALLENGE'),
        children: [relationChild('Reward', item.rewardId, 'REWARD', item.id, 'CHALLENGE')].filter(hasDetail)
    }));
    const rules = bindings.rules.map(rule => ({
        ...selectable('RULE', rule.id, rule.name, `${rule.effectKind} ${Math.abs(rule.points)}`, rule.enabled ? 'RULE' : 'DISABLED'),
        children: [
            { id: `gamification-rule-evidence:${rule.id}`, label: 'Verified event', detail: rule.eventTypeId === '' ? 'Not connected' : presentEventType(rule.eventTypeId).label, badge: 'EVIDENCE', data: { kind: 'RULE' as const, id: rule.id } },
            relationChild('Point type', rule.pointsTypeId, 'POINT', rule.id, 'RULE'),
            relationChild('Achievement', rule.achievementId, 'ACHIEVEMENT', rule.id, 'RULE'),
            relationChild('Challenge', rule.challengeId, 'CHALLENGE', rule.id, 'RULE'),
            relationChild('Reward', rule.rewardId, 'REWARD', rule.id, 'RULE')
        ].filter(hasDetail)
    }));
    return [{
        id: 'gamification-root',
        label: definitions.name || 'Gamification',
        detail: definitions.id,
        badge: 'GAMIFICATION',
        children: [
            { id: 'gamification-rules', label: 'Evidence rules and bindings', badge: String(rules.length), children: rules },
            { id: 'gamification-progression', label: 'Progression', badge: String(points.length + levels.length + achievements.length + challenges.length), children: [
                { id: 'gamification-points', label: 'Point types', badge: String(points.length), children: points },
                { id: 'gamification-levels', label: 'Levels', badge: String(levels.length), children: levels },
                { id: 'gamification-achievements', label: 'Achievements', badge: String(achievements.length), children: achievements },
                { id: 'gamification-challenges', label: 'Challenges', badge: String(challenges.length), children: challenges }
            ] },
            { id: 'gamification-outcomes', label: 'Rewards', badge: String(rewards.length), children: rewards }
        ]
    }];
}

function selectable(kind: Exclude<GamificationStructureSelection, null>['kind'], id: string, label: string, detail: string, badge: string): DesignerStructureNode<GamificationStructureSelection> {
    return { id: `gamification:${kind}:${id}`, label, detail, badge, data: { kind, id } };
}

function relationChild(label: string, targetId: string, targetKind: string, ownerId: string, ownerKind: Exclude<GamificationStructureSelection, null>['kind']): DesignerStructureNode<GamificationStructureSelection> {
    return { id: `gamification-relation:${ownerKind}:${ownerId}:${targetKind}`, label, detail: targetId, badge: targetKind, data: { kind: ownerKind, id: ownerId } };
}

function hasDetail(node: DesignerStructureNode<GamificationStructureSelection>): boolean { return node.detail !== ''; }
