import { describe, expect, it } from 'vitest';

import { buildMetamodelPortHandle, calculateDiagramHandlePoint, createEditableOrthogonalRoute, isOrthogonalRoute, moveEditableOrthogonalRoutePoint, normalizeEditableOrthogonalRoute, parseMetamodelPortHandle, readInternalRoutePoints } from './connectionGeometry';
import type { MetamodelDiagramNode } from './types';

const source: MetamodelDiagramNode = { elementId: 'source', x: 10, y: 20, width: 200, height: 100 };
const target: MetamodelDiagramNode = { elementId: 'target', x: 400, y: 220, width: 180, height: 120 };

describe('connectionGeometry', () => {
    it('reads current legacy and default ports', () => {
        expect(parseMetamodelPortHandle('bottom-35-source', 'SOURCE')).toEqual({ side: 'bottom', percent: 35 });
        expect(parseMetamodelPortHandle('left-target', 'TARGET')).toEqual({ side: 'left', percent: 25 });
        expect(parseMetamodelPortHandle(undefined, 'SOURCE')).toEqual({ side: 'right', percent: 25 });
    });

    it('builds stable dynamic handles and clamps percentages', () => {
        expect(buildMetamodelPortHandle('top', 33.33333, 'SOURCE')).toBe('top-33.333-source');
        expect(buildMetamodelPortHandle('right', 150, 'TARGET')).toBe('right-100-target');
        expect(buildMetamodelPortHandle('left', -8, 'SOURCE')).toBe('left-0-source');
    });

    it('calculates absolute port positions on every side', () => {
        expect(calculateDiagramHandlePoint(source, 'top-50-source', 'SOURCE')).toEqual({ x: 110, y: 20 });
        expect(calculateDiagramHandlePoint(source, 'right-25-source', 'SOURCE')).toEqual({ x: 210, y: 45 });
        expect(calculateDiagramHandlePoint(source, 'bottom-75-source', 'SOURCE')).toEqual({ x: 160, y: 120 });
        expect(calculateDiagramHandlePoint(source, 'left-50-source', 'SOURCE')).toEqual({ x: 10, y: 70 });
    });

    it('creates an orthogonal route whose endpoints follow the selected ports', () => {
        const route = createEditableOrthogonalRoute(source, target, 'bottom-75-source', 'top-25-target', 'VERTICAL_FIRST');

        expect(route[0]).toEqual({ x: 160, y: 120 });
        expect(route[route.length - 1]).toEqual({ x: 445, y: 220 });
        expect(isOrthogonalRoute(route)).toBe(true);
        expect(route.length).toBeGreaterThanOrEqual(4);
    });

    it('expands diagonal user points and exposes only internal points', () => {
        const route = normalizeEditableOrthogonalRoute([{ x: 0, y: 0 }, { x: 40, y: 20 }, { x: 80, y: 60 }], 'HORIZONTAL_FIRST');

        expect(isOrthogonalRoute(route)).toBe(true);
        expect(route[0]).toEqual({ x: 0, y: 0 });
        expect(route[route.length - 1]).toEqual({ x: 80, y: 60 });
        expect(readInternalRoutePoints(route)).toEqual(route.slice(1, -1));
    });

    it('moves a horizontal-to-vertical corner and keeps every segment orthogonal', () => {
        const route = [{ x: 0, y: 0 }, { x: 40, y: 0 }, { x: 40, y: 60 }, { x: 100, y: 60 }];
        const moved = moveEditableOrthogonalRoutePoint(route, 0, { x: 65, y: 25 });

        expect(moved[1]).toEqual({ x: 65, y: 0 });
        expect(moved[2].x).toBe(65);
        expect(isOrthogonalRoute(moved)).toBe(true);
    });

    it('moves a vertical-to-horizontal corner and adjusts its following point', () => {
        const route = [{ x: 0, y: 0 }, { x: 40, y: 0 }, { x: 40, y: 60 }, { x: 100, y: 60 }, { x: 100, y: 100 }, { x: 150, y: 100 }];
        const moved = moveEditableOrthogonalRoutePoint(route, 1, { x: 70, y: 80 });

        expect(moved[2]).toEqual({ x: 70, y: 80 });
        expect(moved[1].x).toBe(70);
        expect(moved[3].y).toBe(80);
        expect(isOrthogonalRoute(moved)).toBe(true);
    });

    it('keeps a source endpoint fixed while moving the first internal point', () => {
        const route = [{ x: 10, y: 10 }, { x: 50, y: 10 }, { x: 50, y: 80 }, { x: 120, y: 80 }];
        const moved = moveEditableOrthogonalRoutePoint(route, 0, { x: 75, y: 45 });

        expect(moved[0]).toEqual(route[0]);
        expect(moved[1].y).toBe(10);
        expect(isOrthogonalRoute(moved)).toBe(true);
    });

    it('keeps a target endpoint fixed while moving the last internal point', () => {
        const route = [{ x: 0, y: 0 }, { x: 40, y: 0 }, { x: 40, y: 80 }, { x: 120, y: 80 }];
        const moved = moveEditableOrthogonalRoutePoint(route, 1, { x: 85, y: 35 });

        expect(moved[moved.length - 1]).toEqual(route[route.length - 1]);
        expect(moved[2].y).toBe(80);
        expect(isOrthogonalRoute(moved)).toBe(true);
    });

    it('moves a collinear horizontal point only along its horizontal segment', () => {
        const route = [{ x: 0, y: 20 }, { x: 40, y: 20 }, { x: 80, y: 20 }, { x: 120, y: 20 }];
        const moved = moveEditableOrthogonalRoutePoint(route, 1, { x: 65, y: 90 });

        expect(moved[2]).toEqual({ x: 65, y: 20 });
        expect(isOrthogonalRoute(moved)).toBe(true);
    });

    it('moves a collinear vertical point only along its vertical segment', () => {
        const route = [{ x: 30, y: 0 }, { x: 30, y: 40 }, { x: 30, y: 80 }, { x: 30, y: 120 }];
        const moved = moveEditableOrthogonalRoutePoint(route, 1, { x: 95, y: 65 });

        expect(moved[2]).toEqual({ x: 30, y: 65 });
        expect(isOrthogonalRoute(moved)).toBe(true);
    });

    it('does not mutate the original route or change its number of explicit points', () => {
        const route = [{ x: 0, y: 0 }, { x: 50, y: 0 }, { x: 50, y: 50 }, { x: 100, y: 50 }];
        const snapshot = structuredClone(route);
        const moved = moveEditableOrthogonalRoutePoint(route, 0, { x: 70, y: 20 });

        expect(route).toEqual(snapshot);
        expect(moved).toHaveLength(route.length);
    });

    it('returns an independent unchanged copy for an invalid point index', () => {
        const route = [{ x: 0, y: 0 }, { x: 40, y: 0 }, { x: 40, y: 50 }];
        const moved = moveEditableOrthogonalRoutePoint(route, 8, { x: 90, y: 90 });

        expect(moved).toEqual(route);
        expect(moved).not.toBe(route);
        expect(moved[0]).not.toBe(route[0]);
    });

});
