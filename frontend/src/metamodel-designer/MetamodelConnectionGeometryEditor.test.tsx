import { fireEvent, render, screen } from '@testing-library/react';
import { useState } from 'react';
import { describe, expect, it, vi } from 'vitest';

import { MetamodelConnectionGeometryEditor } from './MetamodelConnectionGeometryEditor';
import type { MetamodelDiagramDocument } from './types';

const initialDiagram: MetamodelDiagramDocument = {
    metamodelId: 'sample',
    nodes: [
        { elementId: 'source', x: 20, y: 30, width: 200, height: 120 },
        { elementId: 'target', x: 420, y: 220, width: 180, height: 100 }
    ],
    edges: [{ relationId: 'reference', sourceHandle: 'right-25-source', targetHandle: 'left-75-target', sourceClassId: 'source', targetClassId: 'target', routePoints: [] }]
};

function GeometryHarness(props: { onChange: (diagram: MetamodelDiagramDocument) => void }) {
    const [diagram, setDiagram] = useState(initialDiagram);
    return <MetamodelConnectionGeometryEditor diagram={diagram} relationId="reference" sourceClassId="source" sourceLabel="Source" targetClassId="target" targetLabel="Target" onChange={next => { setDiagram(next); props.onChange(next); }} />;
}

describe('MetamodelConnectionGeometryEditor', () => {
    it('switches between normal and orthogonal routing', () => {
        const onChange = vi.fn();
        render(<GeometryHarness onChange={onChange} />);

        fireEvent.change(screen.getByLabelText('Connection routing'), { target: { value: 'ORTHOGONAL' } });
        const orthogonal = onChange.mock.calls.at(-1)?.[0] as MetamodelDiagramDocument;
        expect(orthogonal.edges[0].routePoints?.length).toBeGreaterThanOrEqual(4);

        fireEvent.change(screen.getByLabelText('Connection routing'), { target: { value: 'NORMAL' } });
        const normal = onChange.mock.calls.at(-1)?.[0] as MetamodelDiagramDocument;
        expect(normal.edges[0].routePoints).toEqual([]);
    });

    it('changes source and target port locations and rebuilds an active route', () => {
        const onChange = vi.fn();
        render(<GeometryHarness onChange={onChange} />);
        fireEvent.change(screen.getByLabelText('Connection routing'), { target: { value: 'ORTHOGONAL' } });

        const sideSelectors = screen.getAllByLabelText('Side');
        fireEvent.change(sideSelectors[0], { target: { value: 'bottom' } });
        const positions = screen.getAllByLabelText('Position (%)');
        fireEvent.change(positions[1], { target: { value: '40.5' } });

        const diagram = onChange.mock.calls.at(-1)?.[0] as MetamodelDiagramDocument;
        expect(diagram.edges[0].sourceHandle).toBe('bottom-25-source');
        expect(diagram.edges[0].targetHandle).toBe('left-40.5-target');
        expect(diagram.edges[0].routePoints?.length).toBeGreaterThanOrEqual(4);
    });

    it('adds edits and removes intermediate routing points', () => {
        const onChange = vi.fn();
        render(<GeometryHarness onChange={onChange} />);
        fireEvent.change(screen.getByLabelText('Connection routing'), { target: { value: 'ORTHOGONAL' } });
        fireEvent.click(screen.getByRole('button', { name: 'Add routing point' }));

        const xInput = screen.getAllByLabelText('X (px)')[0];
        fireEvent.change(xInput, { target: { value: '315.5' } });
        let diagram = onChange.mock.calls.at(-1)?.[0] as MetamodelDiagramDocument;
        expect(diagram.edges[0].routePoints?.some(point => point.x === 315.5)).toBe(true);

        fireEvent.click(screen.getAllByRole('button', { name: 'Remove' })[0]);
        diagram = onChange.mock.calls.at(-1)?.[0] as MetamodelDiagramDocument;
        expect(diagram.edges[0].labelPosition).toBeUndefined();
    });

    it('rejects invalid port percentages', () => {
        const onChange = vi.fn();
        render(<GeometryHarness onChange={onChange} />);
        const input = screen.getAllByLabelText('Position (%)')[0];

        fireEvent.change(input, { target: { value: '101' } });

        expect(onChange).not.toHaveBeenCalled();
        expect(screen.getByRole('alert')).toBeTruthy();
    });
});
