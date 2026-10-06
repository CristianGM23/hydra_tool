import { describe, expect, it } from 'vitest';
import { createAchievement, createChallenge, createEmptyGamificationModels, createLevel, createPointType, createReward, createRule } from './gamificationFactory';
import { applyGamificationConnection } from './gamificationConnections';

function fixture() {
    const value = createEmptyGamificationModels('people');
    const point = { ...createPointType(), id: 'points:quality' };
    const level = { ...createLevel(''), id: 'level:bronze' };
    const achievement = { ...createAchievement(), id: 'achievement:first' };
    const challenge = { ...createChallenge(), id: 'challenge:five' };
    const reward = { ...createReward(), id: 'reward:badge' };
    const rule = { ...createRule('', ''), id: 'rule:quality', eventTypeId: '', pointsTypeId: '', achievementId: '', challengeId: '', rewardId: '' };
    return {
        definitions: { ...value.definitions, pointTypes:[point], levels:[level], achievements:[achievement], challenges:[challenge], rewards:[reward] },
        bindings: { ...value.bindings, rules:[rule] }
    };
}

describe('visual gamification connections', () => {
    it('connects verified evidence to a rule and updates eventTypeId', () => { const value=fixture(); const result=applyGamificationConnection(value.definitions,value.bindings,'evidence:core.validation.violation-resolved','rule:rule:quality'); expect(result.accepted).toBe(true); expect(result.bindings.rules[0].eventTypeId).toBe('core.validation.violation-resolved'); expect(result.edgeId).toBe('evidence-rule:rule:quality'); });
    it('connects a rule to a point type', () => { const value=fixture(); const result=applyGamificationConnection(value.definitions,value.bindings,'rule:rule:quality','point:points:quality'); expect(result.bindings.rules[0].pointsTypeId).toBe('points:quality'); });
    it('connects a rule to an achievement', () => { const value=fixture(); const result=applyGamificationConnection(value.definitions,value.bindings,'rule:rule:quality','achievement:achievement:first'); expect(result.bindings.rules[0].achievementId).toBe('achievement:first'); });
    it('connects a rule to a challenge', () => { const value=fixture(); const result=applyGamificationConnection(value.definitions,value.bindings,'rule:rule:quality','challenge:challenge:five'); expect(result.bindings.rules[0].challengeId).toBe('challenge:five'); });
    it('connects a rule to a reward', () => { const value=fixture(); const result=applyGamificationConnection(value.definitions,value.bindings,'rule:rule:quality','reward:reward:badge'); expect(result.bindings.rules[0].rewardId).toBe('reward:badge'); });
    it('connects a point type to a level', () => { const value=fixture(); const result=applyGamificationConnection(value.definitions,value.bindings,'point:points:quality','level:level:bronze'); expect(result.definitions.levels[0].pointTypeId).toBe('points:quality'); });
    it('connects a level to a reward', () => { const value=fixture(); const result=applyGamificationConnection(value.definitions,value.bindings,'level:level:bronze','reward:reward:badge'); expect(result.definitions.levels[0].rewardId).toBe('reward:badge'); });
    it('connects an achievement to a reward', () => { const value=fixture(); const result=applyGamificationConnection(value.definitions,value.bindings,'achievement:achievement:first','reward:reward:badge'); expect(result.definitions.achievements[0].rewardId).toBe('reward:badge'); });
    it('connects a challenge to a reward', () => { const value=fixture(); const result=applyGamificationConnection(value.definitions,value.bindings,'challenge:challenge:five','reward:reward:badge'); expect(result.definitions.challenges[0].rewardId).toBe('reward:badge'); });
    it('rejects unsupported links without mutating either canonical document', () => { const value=fixture(); const result=applyGamificationConnection(value.definitions,value.bindings,'reward:reward:badge','point:points:quality'); expect(result.accepted).toBe(false); expect(result.definitions).toBe(value.definitions); expect(result.bindings).toBe(value.bindings); });
    it('rejects missing rule nodes instead of reporting a successful visual link', () => { const value=fixture(); const result=applyGamificationConnection(value.definitions,value.bindings,'evidence:core.model.saved','rule:rule:missing'); expect(result.accepted).toBe(false); expect(result.message).toContain('no longer exists'); });
});
