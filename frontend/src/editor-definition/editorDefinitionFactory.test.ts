import { describe, expect, it } from 'vitest';
import { createAdditionalEditorDefinition, createDefaultRepresentation, createEmptyEditorDefinition, createNodeTool } from './editorDefinitionFactory';

describe('editor definition factory', () => {
    it('creates one configurable model editor', () => {
        const model = createEmptyEditorDefinition('people');
        expect(model.metamodelId).toBe('people');
        expect(model.representations).toHaveLength(1);
    });

    it('keeps the palette inside the editor definition', () => {
        const model = createEmptyEditorDefinition('people');
        expect(model.palette.groups[0]?.name).toBe('Elements');
    });

    it('creates several independent representations', () => {
        const first = createDefaultRepresentation();
        const second = createDefaultRepresentation();
        expect(first.id).not.toBe(second.id);
    });

    it('scopes creation tools to a representation', () => {
        const tool = createNodeTool('class:Person', 'representation:main', 'Person');
        expect(tool.representationIds).toEqual(['representation:main']);
        expect(tool.targetClassId).toBe('class:Person');
    });

    it('creates several editor documents for the same metamodel', () => {
        const first = createAdditionalEditorDefinition('people');
        const second = createAdditionalEditorDefinition('people');
        expect(first.metamodelId).toBe('people');
        expect(second.metamodelId).toBe('people');
        expect(first.id).not.toBe(second.id);
    });
});
