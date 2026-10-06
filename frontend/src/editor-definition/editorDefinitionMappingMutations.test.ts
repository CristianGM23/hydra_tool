import { describe, expect, it } from 'vitest';
import type { MetaModel } from '../metamodel-designer/types';
import { createEmptyEditorDefinition } from './editorDefinitionFactory';
import { addClassMappingAndTool, addRelationMappingAndTool } from './editorDefinitionMappingMutations';

const metamodel: MetaModel = {
    schemaVersion: '2.1', id: 'people', name: 'People',
    classes: [
        { id: 'class:Party', name: 'Party', kind: 'ABSTRACT_CLASS', attributes: [], operations: [], annotations: {} },
        { id: 'class:Person', name: 'Person', kind: 'CLASS', attributes: [], operations: [], annotations: {} }
    ], enums: [], dataTypes: [],
    relations: [{ id: 'relation:Party:members', name: 'members', sourceTypeId: 'class:Party', targetTypeId: 'class:Person', kind: 'REFERENCE', sourceMultiplicity: null, targetMultiplicity: null, oppositeRelationId: '', ordered: true, unique: true, annotations: {} }], annotations: {}
};

describe('V4.32.39 representation mutations', () => {
    it('can define inherited appearance for an abstract class without inventing a creation tool', () => {
        const source = createEmptyEditorDefinition(metamodel);
        const result = addClassMappingAndTool(source, metamodel, source.representations[0].id, 'class:Party');
        expect(result.created).toBe(true);
        expect(result.model.representations[0].nodeMappings[0]).toMatchObject({ targetClassId: 'class:Party', representationName: 'Party' });
        expect(result.model.palette.tools).toHaveLength(0);
    });

    it('keeps concrete class and relationship shortcuts compatible with the existing palette/runtime contract', () => {
        const source = createEmptyEditorDefinition(metamodel);
        const representationId = source.representations[0].id;
        const withClass = addClassMappingAndTool(source, metamodel, representationId, 'class:Person').model;
        expect(withClass.palette.tools.some(tool => tool.kind === 'CREATE_NODE' && tool.targetClassId === 'class:Person')).toBe(true);
        const withRelation = addRelationMappingAndTool(withClass, metamodel, representationId, 'relation:Party:members').model;
        expect(withRelation.palette.tools.some(tool => tool.kind === 'CREATE_EDGE' && tool.relationId === 'relation:Party:members')).toBe(true);
        expect(withRelation.representations[0].edgeMappings[0]).toMatchObject({ relationId: 'relation:Party:members', lineType: 'ORTHOGONAL' });
    });
});
