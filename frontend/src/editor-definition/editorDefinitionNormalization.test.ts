import { describe, expect, it } from 'vitest';
import type { MetaModel } from '../metamodel-designer/types';
import { createAdditionalEditorDefinition, createEmptyEditorDefinition } from './editorDefinitionFactory';
import { normalizeEditorDefinition, normalizeEditorDefinitionShape, normalizeNodeMapping } from './editorDefinitionNormalization';
import type { NodeMapping } from './types';

const metamodel: MetaModel = {
    schemaVersion: '2.1', id: 'organization', name: 'Organization',
    classes: [
        { id: 'class:Organization', name: 'Organization', kind: 'CLASS', attributes: [], operations: [], annotations: {} },
        { id: 'class:Employee', name: 'Employee', kind: 'CLASS', attributes: [], operations: [], annotations: {} }
    ], enums: [], dataTypes: [], relations: [
        { id: 'relation:Organization:employees', name: 'employees', sourceTypeId: 'class:Organization', targetTypeId: 'class:Employee', kind: 'CONTAINMENT', sourceMultiplicity: null, targetMultiplicity: null, oppositeRelationId: '', ordered: true, unique: true, annotations: {} }
    ], annotations: {}
};

const legacyMapping: NodeMapping = {
    id: 'node:organization', targetClassId: 'class:Organization', representationName: 'Organization', shape: 'ROUNDED_RECTANGLE',
    defaultWidth: 220, defaultHeight: 140, minimumWidth: 120, minimumHeight: 80, labelExpressionId: '', styles: {}, annotations: {}
};

describe('editor definition V2.4 normalization', () => {
    it('uses the detected metamodel root for a new editor', () => {
        expect(createEmptyEditorDefinition(metamodel).representations[0].rootClassIds).toEqual(['class:Organization']);
    });

    it('uses the detected root for additional viewpoints', () => {
        expect(createAdditionalEditorDefinition(metamodel).representations[0].rootClassIds).toEqual(['class:Organization']);
    });

    it('fills an empty root on an older persisted definition', () => {
        const source = createEmptyEditorDefinition('organization');
        expect(normalizeEditorDefinition(source, metamodel).representations[0].rootClassIds).toEqual(['class:Organization']);
    });

    it('preserves a root chosen manually by the user', () => {
        const source = createEmptyEditorDefinition('organization');
        source.representations[0].rootClassIds = ['class:Employee'];
        expect(normalizeEditorDefinition(source, metamodel).representations[0].rootClassIds).toEqual(['class:Employee']);
    });

    it('adds safe defaults to legacy node mappings', () => {
        expect(normalizeNodeMapping(legacyMapping)).toMatchObject({ imageAssetPath: '', imageFit: 'CONTAIN', container: false, containmentRelationIds: [], containerPadding: 18 });
    });

    it('restores omitted empty collections from a persisted server response before rendering', () => {
        const source = createEmptyEditorDefinition('organization') as ReturnType<typeof createEmptyEditorDefinition> & { representations?: ReturnType<typeof createEmptyEditorDefinition>['representations']; palette?: ReturnType<typeof createEmptyEditorDefinition>['palette']; forms?: ReturnType<typeof createEmptyEditorDefinition>['forms'] };
        const sparse = { ...source, representations: [{ ...source.representations[0], rootClassIds: undefined, nodeMappings: undefined, edgeMappings: undefined, toolIds: undefined, layers: undefined, filters: undefined, annotations: undefined }], palette: undefined, forms: undefined, annotations: undefined } as unknown as ReturnType<typeof createEmptyEditorDefinition>;
        const normalized = normalizeEditorDefinitionShape(sparse);
        expect(normalized.representations[0]).toMatchObject({ rootClassIds: [], nodeMappings: [], edgeMappings: [], toolIds: [], layers: [], filters: [], annotations: {} });
        expect(normalized.palette).toEqual({ groups: [], tools: [] });
        expect(normalized.forms).toEqual([]);
        expect(normalized.annotations).toEqual({});
    });

    it('does not discard explicit image and container configuration', () => {
        expect(normalizeNodeMapping({ ...legacyMapping, shape: 'PROJECT_IMAGE', imageAssetPath: 'assets/notation/root.svg', imageFit: 'COVER', container: true, containmentRelationIds: ['relation:Organization:employees'], containerPadding: 30 })).toMatchObject({ imageAssetPath: 'assets/notation/root.svg', imageFit: 'COVER', container: true, containmentRelationIds: ['relation:Organization:employees'], containerPadding: 30 });
    });
});
