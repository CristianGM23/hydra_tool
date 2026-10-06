import { describe, expect, it } from 'vitest';
import type { MetaModel } from '../metamodel-designer/types';
import { compareAssociatedMetamodel } from './metamodelAssociation';

const source: MetaModel = {
    schemaVersion: '2.1', id: 'source', name: 'Source', annotations: {}, dataTypes: [],
    classes: [{ id: 's-person', name: 'Person', kind: 'CLASS', annotations: {}, operations: [], attributes: [{ id: 's-name', name: 'name', type: { kind: 'PRIMITIVE', targetId: 'STRING', externalName: '' }, multiplicity: { lowerBound: 0, upperBound: 1 }, ordered: false, unique: true, defaultValue: '', annotations: {} }] }],
    enums: [{ id: 's-status', name: 'Status', literals: [], annotations: {} }],
    relations: []
};

it('compares an associated metamodel semantically instead of by imported IDs', () => {
    const target: MetaModel = structuredClone(source);
    target.id = 'target'; target.name = 'Target'; target.classes[0].id = 't-person'; target.classes[0].attributes[0].id = 't-name'; target.enums[0].id = 't-status';
    const report = compareAssociatedMetamodel(source, target);
    expect(report.missingClasses).toBe(0);
    expect(report.missingEnums).toBe(0);
    expect(report.missingFeatures).toBe(0);
});

describe('associated metamodel diagnostics', () => {
    it('reports source constructs that cannot be adapted to the active metamodel', () => {
        const target: MetaModel = { ...structuredClone(source), classes: [], enums: [] };
        const report = compareAssociatedMetamodel(source, target);
        expect(report.missingClasses).toBe(1);
        expect(report.missingEnums).toBe(1);
        expect(report.diagnostics.some(item => item.message.includes('Person'))).toBe(true);
        expect(report.metrics.find(item => item.id === 'associated-unresolved')?.value).toBeGreaterThan(0);
    });
});
