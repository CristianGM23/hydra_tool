import { describe, expect, it } from 'vitest';
import { gamificationNodeId } from './gamificationDiagram';
import { removeSelectedGamificationNodes, removeSelectedGamificationRelations } from './gamificationSelection';
import type { GamificationBindingModel, GamificationDefinitionModel } from './types';

const definitions: GamificationDefinitionModel = {
    schemaVersion:'1.0', id:'g', metamodelId:'m', name:'Game',
    pointTypes:[{ id:'xp', name:'XP', description:'', minimumBalance:0 }],
    levels:[{ id:'l1', name:'Level', pointTypeId:'xp', minimumPoints:10, rewardId:'reward' }],
    achievements:[{ id:'a1', name:'Achievement', description:'', rewardId:'reward', hidden:false }],
    challenges:[], rewards:[{ id:'reward', name:'Reward', description:'', payload:{} }], annotations:{}
};
const bindings: GamificationBindingModel = { schemaVersion:'1.0', id:'b', metamodelId:'m', definitionModelId:'g', rules:[{
    id:'r1', name:'Rule', eventTypeId:'event:test', conditionPath:'', operator:'EQUALS', expectedValue:true,
    pointsTypeId:'xp', points:5, achievementId:'a1', challengeId:'', rewardId:'reward', effectKind:'AWARD', repeatPolicy:'UNLIMITED',
    cooldownSeconds:0, maxAwardsPerActor:0, maxAwardsPerDay:0, enabled:true
}], annotations:{} };

describe('gamification selection mutations', () => {
    it('deletes several semantic nodes and clears dependent references in one mutation', () => {
        const result = removeSelectedGamificationNodes(definitions, bindings, [gamificationNodeId('POINT','xp'), gamificationNodeId('REWARD','reward')]);
        expect(result.definitions.pointTypes).toEqual([]);
        expect(result.definitions.levels).toEqual([]);
        expect(result.definitions.rewards).toEqual([]);
        expect(result.bindings.rules[0].pointsTypeId).toBe('');
        expect(result.bindings.rules[0].rewardId).toBe('');
    });

    it('keeps derived ledger relations protected', () => {
        const result = removeSelectedGamificationRelations(definitions, bindings, [{ data: { relation:'records', sourceKind:'RULE', targetKind:'LEDGER', sourceElementId:'r1', targetElementId:'runtime-ledger' } }]);
        expect(result.ignoredLedgerItems).toBe(1);
        expect(result.bindings).toEqual(bindings);
    });
});
