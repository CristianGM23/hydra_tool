import { render } from '@testing-library/react';
import { Position } from '@xyflow/react';
import type { EdgeProps } from '@xyflow/react';
import { describe, expect, it } from 'vitest';
import { HydraDirectedEdge } from './HydraDirectedEdge';
import { createHydraHorizontalHandles } from './HydraEdgeGeometry';

const base = {
    id: 'binding-1', source: 'event-a', target: 'event-b', sourceX: 20, sourceY: 40,
    targetX: 240, targetY: 40, sourcePosition: Position.Right, targetPosition: Position.Left,
    data: {}, selected: false, animated: false, selectable: true, deletable: true,
    draggable: false, type: 'hydraDirectedEdge', zIndex: 0, isFocusable: true,
    label: 'Event transformation', labelStyle: {}, labelShowBg: true, labelBgStyle: {},
    labelBgPadding: [2, 4] as [number, number], labelBgBorderRadius: 2,
    markerStart: undefined, markerEnd: 'url(#arrowclosed)', interactionWidth: 20,
    sourceHandleId: 'source', targetHandleId: 'target'
} as unknown as EdgeProps;

describe('HydraDirectedEdge', () => {
    it('renders the path and a physical polygon without an HTML edge-label portal', () => {
        const { container, getByText } = render(<svg><HydraDirectedEdge {...base} /></svg>);
        expect(container.querySelector('[data-hydra-directed-edge-path="binding-1"]')?.tagName.toLowerCase()).toBe('path');
        expect(container.querySelector('[data-hydra-directed-edge-arrow="binding-1"]')?.tagName.toLowerCase()).toBe('polygon');
        expect(container.querySelector('.react-flow__edgelabel-renderer')).toBeNull();
        expect(getByText('Event transformation')).toBeTruthy();
    });

    it('uses safe positions when an edge arrives without explicit port positions', () => {
        const edge = { ...base, sourcePosition: undefined, targetPosition: undefined } as unknown as EdgeProps;
        const { container } = render(<svg><HydraDirectedEdge {...edge} /></svg>);
        expect(container.querySelector('[data-hydra-directed-edge-arrow="binding-1"]')).not.toBeNull();
    });

    it('accepts numeric stroke widths serialized as strings', () => {
        const edge = { ...base, style: { strokeWidth: '3.5' } } as unknown as EdgeProps;
        const { container } = render(<svg><HydraDirectedEdge {...edge} /></svg>);
        expect(container.querySelector('.react-flow__edge-path')?.getAttribute('style')).toContain('3.5');
    });

    it('provides deterministic left and right handle geometry before measurement', () => {
        const handles = createHydraHorizontalHandles({ width: 210, height: 84 });
        expect(handles).toEqual([
            expect.objectContaining({ id: 'target', type: 'target', position: Position.Left, x: -7, y: 35 }),
            expect.objectContaining({ id: 'source', type: 'source', position: Position.Right, x: 203, y: 35 })
        ]);
    });

    it('can describe source-only and target-only terminal nodes', () => {
        expect(createHydraHorizontalHandles({ target: false }).map(handle => handle.id)).toEqual(['source']);
        expect(createHydraHorizontalHandles({ source: false }).map(handle => handle.id)).toEqual(['target']);
    });

});
