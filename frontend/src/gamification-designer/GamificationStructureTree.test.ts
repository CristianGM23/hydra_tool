import { describe, expect, it } from 'vitest';
import { createEmptyGamificationModels, createLevel, createPointType, createReward, createRule } from './gamificationFactory';
import { buildGamificationStructure } from './gamificationStructureModel';

describe('gamification structure tree', () => {
    it('shows progression and reward relationships from canonical properties', () => { const value=createEmptyGamificationModels('people'); const point={...createPointType(),id:'points:q'}; const reward={...createReward(),id:'reward:r'}; value.definitions.pointTypes=[point]; value.definitions.rewards=[reward]; value.definitions.levels=[{...createLevel(point.id),id:'level:l',rewardId:reward.id}]; const json=JSON.stringify(buildGamificationStructure(value.definitions,value.bindings)); expect(json).toContain('Point type'); expect(json).toContain('points:q'); expect(json).toContain('reward:r'); });
    it('shows evidence and every rule outcome link', () => { const value=createEmptyGamificationModels('people'); value.bindings.rules=[{...createRule('core.model.saved','points:q'),id:'rule:r',achievementId:'achievement:a',challengeId:'challenge:c',rewardId:'reward:r'}]; const json=JSON.stringify(buildGamificationStructure(value.definitions,value.bindings)); expect(json).toContain('Verified event'); expect(json).toContain('Achievement'); expect(json).toContain('Challenge'); expect(json).toContain('Reward'); });
});
