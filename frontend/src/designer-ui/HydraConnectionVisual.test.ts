import { Position } from '@xyflow/react';
import { describe, expect, it } from 'vitest';
import {
    createHydraConnectionHandles,
    DEFAULT_HYDRA_SOURCE_PORT,
    DEFAULT_HYDRA_TARGET_PORT,
    hydraConnectionPorts,
    normalizeHydraConnectionVisual
} from './HydraConnectionVisual';

describe('HYDRA metamodel-style connection ports', () => {
    it('exposes the same eight directional source/target ports used by the metamodel designer', () => {
        expect(hydraConnectionPorts()).toEqual([
            { id: 'top-source', type: 'source', position: Position.Top, percent: 25 },
            { id: 'top-target', type: 'target', position: Position.Top, percent: 75 },
            { id: 'right-source', type: 'source', position: Position.Right, percent: 25 },
            { id: 'right-target', type: 'target', position: Position.Right, percent: 75 },
            { id: 'bottom-target', type: 'target', position: Position.Bottom, percent: 25 },
            { id: 'bottom-source', type: 'source', position: Position.Bottom, percent: 75 },
            { id: 'left-target', type: 'target', position: Position.Left, percent: 25 },
            { id: 'left-source', type: 'source', position: Position.Left, percent: 75 }
        ]);
        expect(DEFAULT_HYDRA_SOURCE_PORT).toBe('right-source');
        expect(DEFAULT_HYDRA_TARGET_PORT).toBe('left-target');
    });

    it('computes physical 10 px handles on the node boundary', () => {
        const handles = createHydraConnectionHandles({ width: 200, height: 100 });
        const rightSource = handles.find(handle => handle.id === 'right-source');
        const leftTarget = handles.find(handle => handle.id === 'left-target');
        expect(rightSource).toMatchObject({ x: 195, y: 20, width: 10, height: 10 });
        expect(leftTarget).toMatchObject({ x: -5, y: 20, width: 10, height: 10 });
    });

    it('retains the existing HYDRA orthogonal closed-arrow appearance as the default', () => {
        expect(normalizeHydraConnectionVisual(undefined)).toMatchObject({
            connectionLabel: '',
            connectionNote: '',
            routeStyle: 'ORTHOGONAL',
            lineStyle: 'SOLID',
            sourceTerminator: 'NONE',
            targetTerminator: 'ARROW_CLOSED'
        });
        expect(normalizeHydraConnectionVisual({ connectionLabel: 'then', connectionNote: 'Audit only', routeStyle: 'SMOOTH', lineStyle: 'DOTTED', targetTerminator: 'ARROW_OPEN' })).toMatchObject({
            connectionLabel: 'then',
            connectionNote: 'Audit only',
            routeStyle: 'SMOOTH',
            lineStyle: 'DOTTED',
            sourceTerminator: 'NONE',
            targetTerminator: 'ARROW_OPEN'
        });
    });
});
