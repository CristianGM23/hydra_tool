import { describe, expect, it } from 'vitest';
import type { MetaModel } from '../metamodel-designer/types';
import { compatibleContainmentRelations, detectMetamodelRoots, isSameOrSubtype, rootDetectionDescription } from './metamodelRootDetection';

function model(overrides: Partial<MetaModel> = {}): MetaModel {
    return {
        schemaVersion: '2.1', id: 'organization', name: 'Organization',
        classes: [
            { id: 'class:Organization', name: 'Organization', kind: 'CLASS', attributes: [], operations: [], annotations: {} },
            { id: 'class:Department', name: 'Department', kind: 'CLASS', attributes: [], operations: [], annotations: {} },
            { id: 'class:Employee', name: 'Employee', kind: 'CLASS', attributes: [], operations: [], annotations: {} }
        ], enums: [], dataTypes: [],
        relations: [
            { id: 'relation:Organization:departments', name: 'departments', sourceTypeId: 'class:Organization', targetTypeId: 'class:Department', kind: 'CONTAINMENT', sourceMultiplicity: null, targetMultiplicity: null, oppositeRelationId: '', ordered: true, unique: true, annotations: {} },
            { id: 'relation:Department:employees', name: 'employees', sourceTypeId: 'class:Department', targetTypeId: 'class:Employee', kind: 'CONTAINMENT', sourceMultiplicity: null, targetMultiplicity: null, oppositeRelationId: '', ordered: true, unique: true, annotations: {} }
        ], annotations: {}, ...overrides
    };
}

describe('metamodel root detection', () => {
    it('detects the class without incoming containment', () => {
        expect(detectMetamodelRoots(model()).primaryClassId).toBe('class:Organization');
    });

    it('prioritizes an explicit root annotation', () => {
        const source = model();
        source.classes[1] = { ...source.classes[1], annotations: { 'hydra:root': 'true' } };
        expect(detectMetamodelRoots(source)).toMatchObject({ primaryClassId: 'class:Department', reason: 'EXPLICIT_ANNOTATION' });
    });

    it('ignores abstract classes as automatic roots', () => {
        const source = model({ classes: [{ id: 'class:AbstractRoot', name: 'AbstractRoot', kind: 'ABSTRACT_CLASS', attributes: [], operations: [], annotations: { 'hydra:root': 'true' } }, ...model().classes] });
        expect(detectMetamodelRoots(source).primaryClassId).toBe('class:Organization');
    });

    it('returns no root when no concrete class exists', () => {
        expect(detectMetamodelRoots(model({ classes: [{ id: 'class:Contract', name: 'Contract', kind: 'INTERFACE', attributes: [], operations: [], annotations: {} }], relations: [] }))).toMatchObject({ primaryClassId: '', reason: 'NO_CONCRETE_CLASS' });
    });

    it('finds compatible containment relations for a container class', () => {
        expect(compatibleContainmentRelations(model(), 'class:Department').map(item => item.id)).toEqual(['relation:Department:employees']);
    });

    it('resolves inheritance when matching a containment source', () => {
        const source = model();
        source.classes.push({ id: 'class:SpecialDepartment', name: 'SpecialDepartment', kind: 'CLASS', attributes: [], operations: [], annotations: {} });
        source.relations.push({ id: 'relation:SpecialDepartment:extends', name: '', sourceTypeId: 'class:SpecialDepartment', targetTypeId: 'class:Department', kind: 'INHERITANCE', sourceMultiplicity: null, targetMultiplicity: null, oppositeRelationId: '', ordered: true, unique: true, annotations: {} });
        expect(isSameOrSubtype(source, 'class:SpecialDepartment', 'class:Department')).toBe(true);
        expect(compatibleContainmentRelations(source, 'class:SpecialDepartment')).toHaveLength(1);
    });

    it('describes why the root was proposed', () => {
        expect(rootDetectionDescription(detectMetamodelRoots(model()), model())).toMatch(/Organization.*not contained/i);
    });
});
