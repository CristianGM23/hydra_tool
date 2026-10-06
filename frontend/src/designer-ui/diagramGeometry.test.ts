import { describe, expect, it } from 'vitest';
import { sameDiagramGeometry } from './diagramGeometry';

const node = { nodeId: 'n1', x: 10, y: 20, width: 100, height: 60, collapsed: false };

describe('diagram geometry equality', () => {
    it('recognizes identical geometry independently from object identity', () => {
        expect(sameDiagramGeometry([node], [{ ...node }])).toBe(true);
    });

    it('detects position and size changes', () => {
        expect(sameDiagramGeometry([node], [{ ...node, x: 11 }])).toBe(false);
        expect(sameDiagramGeometry([node], [{ ...node, width: 101 }])).toBe(false);
    });
});
