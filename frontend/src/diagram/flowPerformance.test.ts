import { describe, expect, it } from 'vitest';
import { resolveHydraFlowPerformanceProfile } from './flowPerformance';

describe('HYDRA flow performance profile', () => {
    it('keeps small diagrams fully rendered', () => {
        expect(resolveHydraFlowPerformanceProfile(40, 60)).toEqual({
            scale: 'SMALL', nodeCount: 40, edgeCount: 60, onlyRenderVisibleElements: false
        });
    });

    it('enables viewport rendering for medium diagrams', () => {
        expect(resolveHydraFlowPerformanceProfile(120, 20).onlyRenderVisibleElements).toBe(true);
        expect(resolveHydraFlowPerformanceProfile(20, 240).scale).toBe('MEDIUM');
    });

    it('classifies large diagrams independently by nodes or edges', () => {
        expect(resolveHydraFlowPerformanceProfile(400, 0).scale).toBe('LARGE');
        expect(resolveHydraFlowPerformanceProfile(1, 800).scale).toBe('LARGE');
    });

    it('normalizes invalid counts without throwing', () => {
        expect(resolveHydraFlowPerformanceProfile(Number.NaN, -4)).toEqual({
            scale: 'SMALL', nodeCount: 0, edgeCount: 0, onlyRenderVisibleElements: false
        });
    });
});

describe('HYDRA representative scale budgets', () => {
    for (const size of [100, 500, 2_000, 5_000]) {
        it(`classifies ${size} nodes deterministically`, () => {
            const profile = resolveHydraFlowPerformanceProfile(size, Math.max(0, size - 1));
            expect(profile.nodeCount).toBe(size);
            expect(profile.edgeCount).toBe(Math.max(0, size - 1));
            expect(profile.onlyRenderVisibleElements).toBe(size >= 500);
            expect(profile.scale).toBe(size === 100 ? 'SMALL' : 'LARGE');
        });
    }
});
