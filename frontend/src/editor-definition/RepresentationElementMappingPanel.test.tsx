import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import type { MetaModel } from '../metamodel-designer/types';
import { RepresentationElementMappingPanel } from './RepresentationElementMappingPanel';
import type { RepresentationDefinition } from './types';

const metamodel = {
    id: 'people', name: 'People', schemaVersion: '1.0',
    classes: [
        { id: 'class:Person', name: 'Person', kind: 'CLASS' },
        { id: 'class:Named', name: 'Named', kind: 'INTERFACE' },
    ],
    relations: [{ id: 'relation:knows', name: 'knows', sourceTypeId: 'class:Person', targetTypeId: 'class:Person' }],
} as unknown as MetaModel;

const representation = {
    id: 'representation:main', name: 'Main', kind: 'DIAGRAM', editable: true, rootClassIds: [], toolIds: [], annotations: {},
    nodeMappings: [{ id: 'node:person', targetClassId: 'class:Person', representationName: 'Person', shape: 'RECTANGLE', defaultWidth: 120, defaultHeight: 80, minimumWidth: 80, minimumHeight: 50, labelExpressionId: '', imageAssetPath: '', imageFit: 'CONTAIN', container: false, containmentRelationIds: [], containerPadding: 10, styles: {}, conditionalStyles: [], annotations: {} }],
    edgeMappings: [{ id: 'edge:knows', relationId: 'relation:knows', sourceClassId: 'class:Person', targetClassId: 'class:Person', lineType: 'ORTHOGONAL', labelExpressionId: '', styles: {}, conditionalStyles: [], annotations: {} }],
} as unknown as RepresentationDefinition;

describe('representation element mapping panel', () => {
    it('edits existing class and relationship appearances and can add interface appearance', () => {
        const onAddClass = vi.fn(); const onAddRelation = vi.fn(); const onEditNode = vi.fn(); const onEditEdge = vi.fn();
        render(<RepresentationElementMappingPanel representation={representation} metamodel={metamodel} onAddClass={onAddClass} onAddRelation={onAddRelation} onEditNode={onEditNode} onEditEdge={onEditEdge} />);
        fireEvent.click(screen.getByRole('button', { name: /Person.*Edit appearance/i }));
        expect(onEditNode).toHaveBeenCalledWith('node:person');
        fireEvent.click(screen.getByRole('button', { name: /Named.*Show in this view/i }));
        expect(onAddClass).toHaveBeenCalledWith('class:Named', 'Named');
        fireEvent.click(screen.getByRole('button', { name: /knows.*Edit connection appearance/i }));
        expect(onEditEdge).toHaveBeenCalledWith('edge:knows');
    });
});
