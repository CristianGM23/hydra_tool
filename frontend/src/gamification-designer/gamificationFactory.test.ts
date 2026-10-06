import { describe, expect, it } from 'vitest';
import { createAchievement, createChallenge, createEmptyGamificationModels, createLevel, createPointType, createReward, createRule } from './gamificationFactory';

describe('gamification factory', () => {
    it('creates matching definition and binding documents', () => { const value = createEmptyGamificationModels('people'); expect(value.bindings.definitionModelId).toBe(value.definitions.id); });
    it('keeps gamification optional by creating empty catalogs', () => { const value = createEmptyGamificationModels('people'); expect(value.definitions.pointTypes).toEqual([]); expect(value.bindings.rules).toEqual([]); });
    it('creates point types with stable IDs', () => { expect(createPointType().id).toMatch(/^points:/); });
    it('prevents negative balances by default', () => { expect(createPointType().minimumBalance).toBe(0); });
    it('creates rewards with portable payloads', () => { expect(createReward().payload).toEqual({}); });
    it('creates levels for a point type', () => { expect(createLevel('points:quality').pointTypeId).toBe('points:quality'); });
    it('starts levels at zero points', () => { expect(createLevel('points:quality').minimumPoints).toBe(0); });
    it('creates visible achievements', () => { expect(createAchievement().hidden).toBe(false); });
    it('creates positive challenges', () => { expect(createChallenge().targetCount).toBe(1); });
    it('defaults rules to verified validation evidence', () => { expect(createRule('', 'points:quality').eventTypeId).toBe('core.validation.violation-resolved'); });
    it('creates award rules by default', () => { expect(createRule('core.validation.violation-resolved', 'points:quality').effectKind).toBe('AWARD'); });
    it('uses once-per-event idempotence by default', () => { expect(createRule('core.validation.violation-resolved', 'points:quality').repeatPolicy).toBe('ONCE_PER_EVENT'); });
    it('does not create an implicit anti-farming bypass', () => { const rule = createRule('core.validation.violation-resolved', 'points:quality'); expect(rule.cooldownSeconds).toBe(0); expect(rule.maxAwardsPerActor).toBe(0); });
    it('keeps the requested point type', () => { expect(createRule('core.validation.violation-resolved', 'points:quality').pointsTypeId).toBe('points:quality'); });
});
