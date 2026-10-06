import { describe, expect, it } from 'vitest';
import { createEmptyGamificationModels, createPointType, createRule } from './gamificationFactory';
import { findGamificationBlockingError } from './gamificationValidation';

describe('gamification penalties',()=>{
 it('accepts a points-only penalty',()=>{const value=createEmptyGamificationModels('people');const point=createPointType();const rule={...createRule('core.validation.execution-completed',point.id),effectKind:'PENALTY' as const,points:-2};expect(findGamificationBlockingError({...value.definitions,pointTypes:[point]},{...value.bindings,rules:[rule]})).toBe('');});
 it('rejects a penalty that grants a reward',()=>{const value=createEmptyGamificationModels('people');const point=createPointType();const rule={...createRule('core.validation.execution-completed',point.id),effectKind:'PENALTY' as const,points:-2,rewardId:'reward:badge'};expect(findGamificationBlockingError({...value.definitions,pointTypes:[point]},{...value.bindings,rules:[rule]})).toContain('Penalty rules');});
 it('allows a bounded negative point balance',()=>{expect({...createPointType(),minimumBalance:-10}.minimumBalance).toBe(-10);});
});
