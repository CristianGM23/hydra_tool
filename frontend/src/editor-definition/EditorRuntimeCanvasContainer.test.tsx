import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import type { EditorRenderedEdge, EditorRenderedNode, EditorSessionDiagramDocument } from './types';
import { EditorRuntimeCanvas } from './EditorRuntimeCanvas';
import { buildEditorRuntimeFlowProjection, hasSameRuntimeSelection } from './editorRuntimeFlowProjection';

const nodes: EditorRenderedNode[] = [
    { elementId: 'department-1', classId: 'class:Department', className: 'Department', mappingId: 'node:department', representationName: 'Department', shape: 'PROJECT_IMAGE', label: 'Engineering', width: 420, height: 300, attributes: {}, styles: {}, imageAssetPath: 'assets/notation/department.svg', imageFit: 'COVER', container: true, containmentRelationIds: ['relation:Department:employees'], containerPadding: 24 },
    { elementId: 'employee-1', classId: 'class:Employee', className: 'Employee', mappingId: 'node:employee', representationName: 'Employee', shape: 'ELLIPSE', label: 'Ada', width: 140, height: 90, attributes: {}, styles: {}, container: false, containmentRelationIds: [], containerPadding: 18 }
];
const edges: EditorRenderedEdge[] = [
    { relationInstanceId: 'relation-instance:contains', relationId: 'relation:Department:employees', relationName: 'employees', mappingId: '', sourceElementId: 'department-1', targetElementId: 'employee-1', lineType: 'ORTHOGONAL', label: '', styles: {} },
    { relationInstanceId: 'relation-instance:mentor', relationId: 'relation:Employee:mentor', relationName: 'mentor', mappingId: 'edge:mentor', sourceElementId: 'employee-1', targetElementId: 'department-1', lineType: 'STRAIGHT', label: 'mentor', styles: {} }
];
const diagram: EditorSessionDiagramDocument = {
    schemaVersion: '1.0', id: 'session-one', metamodelId: 'organization', editorId: 'organization-editor', representationId: 'representation:main',
    nodes: [
        { elementId: 'department-1', x: 60, y: 50, width: 420, height: 300 },
        { elementId: 'employee-1', x: 500, y: 500, width: 140, height: 90 }
    ], edges: []
};

function renderCanvas(): void {
    render(<EditorRuntimeCanvas nodes={nodes} edges={edges} diagram={diagram} selectedIds={[]} editable onSelectNode={vi.fn()} onConnect={vi.fn()} onNodeDragStop={vi.fn()} onNodeResizeStop={vi.fn()} />);
}

function projection() {
    return buildEditorRuntimeFlowProjection(nodes,edges,diagram,[],true,vi.fn());
}

describe('editor runtime visual containment', () => {
    it('makes the contained model element a React Flow child of its semantic container', () => {
        const child=projection().nodes.find(node => node.id === 'employee-1');
        expect(child).toMatchObject({ parentId: 'department-1' });
        expect(child).not.toHaveProperty('extent');
    });

    it('clamps the child position inside the visual container padding', () => {
        const child = projection().nodes.find(node => node.id === 'employee-1');
        expect(child).toBeDefined();
        expect(child!.position.x).toBeLessThanOrEqual(420 - Number(child!.width) - 24);
        expect(child!.position.y).toBeLessThanOrEqual(300 - Number(child!.height) - 24);
        expect(child!.width).toBeLessThanOrEqual(140);
        expect(child!.height).toBeLessThanOrEqual(90);
    });

    it('does not draw a duplicate edge for visual containment', () => {
        expect(projection().edges.map(edge => edge.id)).toEqual(['relation-instance:mentor']);
    });

    it('renders project SVG or PNG assets through the active project endpoint', () => {
        renderCanvas();
        const image=screen.getByRole('img',{name:'Department representation'});
        expect(image).toHaveAttribute('src', '/api/projects/active/visual?path=assets%2Fnotation%2Fdepartment.svg');
        expect(image).toHaveStyle({ objectFit: 'cover' });
    });

    it('marks configured mappings as visual containers', () => {
        renderCanvas();
        expect(screen.getByText('Drop compatible elements inside')).toBeInTheDocument();
    });

    it('renders only the semantic element label inside the graphical representation', () => {
        renderCanvas();
        expect(screen.getByText('Engineering')).toBeInTheDocument();
        expect(screen.queryByText('Department')).not.toBeInTheDocument();
        expect(screen.queryByText('Employee')).not.toBeInTheDocument();
    });

    it('shows contextual selection actions with code preview and persistent rotation', () => {
        const onPreviewCode=vi.fn();
        const onDiagramNodesChange=vi.fn();
        render(<EditorRuntimeCanvas nodes={nodes} edges={edges} diagram={diagram} selectedIds={['employee-1']} editable canPreviewCode onSelectNode={vi.fn()} onSelectionChange={vi.fn()} onPreviewCode={onPreviewCode} onDeleteSelection={vi.fn()} onConnect={vi.fn()} onNodeDragStop={vi.fn()} onNodeResizeStop={vi.fn()} onDiagramNodesChange={onDiagramNodesChange}/>);
        expect(screen.getByRole('dialog',{name:'Selected model elements'})).toBeInTheDocument();
        fireEvent.click(screen.getByRole('button',{name:'Preview code'}));
        expect(onPreviewCode).toHaveBeenCalledWith(['employee-1']);
        fireEvent.click(screen.getByRole('button',{name:'90°'}));
        expect(onDiagramNodesChange).toHaveBeenCalledWith(expect.arrayContaining([expect.objectContaining({elementId:'employee-1',rotationDegrees:90})]));
    });

    it('does not feed an unchanged controlled selection back into the session state', () => {
        expect(hasSameRuntimeSelection([],[])).toBe(true);
        expect(hasSameRuntimeSelection(['employee-1'],['department-1'])).toBe(false);
        const onSelectionChange=vi.fn();
        render(<EditorRuntimeCanvas nodes={nodes} edges={edges} diagram={diagram} selectedIds={[]} editable onSelectNode={vi.fn()} onSelectionChange={onSelectionChange} onConnect={vi.fn()} onNodeDragStop={vi.fn()} onNodeResizeStop={vi.fn()} />);
        expect(screen.getByRole('application')).toBeInTheDocument();
        expect(onSelectionChange).not.toHaveBeenCalled();
    });
});
