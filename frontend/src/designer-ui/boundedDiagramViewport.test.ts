import { describe, expect, it } from 'vitest';
import { resolveHydraDiagramPanPolicy, resolveHydraDiagramTranslateExtent } from './boundedDiagramViewport';

describe('bounded diagram viewport', () => {
    it('allows panning only in explicit PAN mode', () => {
        expect(resolveHydraDiagramPanPolicy('SELECT')).toMatchObject({ panOnDrag:false, panActivationKeyCode:null, preventScrolling:true });
        expect(resolveHydraDiagramPanPolicy('PAN').panOnDrag).toBe(true);
        expect(resolveHydraDiagramPanPolicy('CONNECT').panOnDrag).toBe(false);
    });
    it('keeps a finite margin around diagram content', () => {
        const extent = resolveHydraDiagramTranslateExtent([{ position:{x:100,y:50}, width:200, height:100 }]);
        expect(extent[0][0]).toBeLessThan(100);
        expect(extent[1][0]).toBeGreaterThan(300);
        expect(Number.isFinite(extent[0][0]) && Number.isFinite(extent[1][1])).toBe(true);
    });
});
