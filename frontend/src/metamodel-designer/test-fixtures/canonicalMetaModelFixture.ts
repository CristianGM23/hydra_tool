import type { MetaModel, MetamodelDiagramDocument } from '../types';

export function canonicalMetaModelFixture(): MetaModel {
    return {
        schemaVersion: '2.1', id: 'shop', name: 'Shop', annotations: {},
        classes: [
            { id: 'root', name: 'Shop', kind: 'CLASS', annotations: {}, attributes: [], operations: [] },
            { id: 'customer', name: 'Customer', kind: 'CLASS', annotations: {}, attributes: [
                { id: 'customer-name', name: 'name', type: { kind: 'PRIMITIVE', targetId: 'STRING', externalName: '' }, multiplicity: { lowerBound: 1, upperBound: 1 }, ordered: true, unique: true, defaultValue: '', annotations: {} },
                { id: 'customer-status', name: 'status', type: { kind: 'ENUM', targetId: 'status', externalName: '' }, multiplicity: { lowerBound: 0, upperBound: 1 }, ordered: true, unique: true, defaultValue: '', annotations: {} }
            ], operations: [] },
            { id: 'order', name: 'Order', kind: 'CLASS', annotations: {}, attributes: [], operations: [] },
            { id: 'named', name: 'Named', kind: 'INTERFACE', annotations: {}, attributes: [], operations: [] }
        ],
        enums: [{ id: 'status', name: 'Status', annotations: {}, literals: [
            { id: 'status-new', name: 'NEW', value: 0, annotations: {} },
            { id: 'status-paid', name: 'PAID', value: 1, annotations: {} }
        ] }],
        dataTypes: [{ id: 'money', name: 'Money', annotations: {} }],
        relations: [
            { id: 'contains-customers', name: 'customers', sourceTypeId: 'root', targetTypeId: 'customer', kind: 'CONTAINMENT', sourceMultiplicity: { lowerBound: 1, upperBound: 1 }, targetMultiplicity: { lowerBound: 0, upperBound: null }, oppositeRelationId: '', ordered: true, unique: true, annotations: {} },
            { id: 'customer-orders', name: 'orders', sourceTypeId: 'customer', targetTypeId: 'order', kind: 'REFERENCE', sourceMultiplicity: { lowerBound: 1, upperBound: 1 }, targetMultiplicity: { lowerBound: 0, upperBound: null }, oppositeRelationId: 'order-customer', ordered: true, unique: true, annotations: {} },
            { id: 'order-customer', name: 'customer', sourceTypeId: 'order', targetTypeId: 'customer', kind: 'REFERENCE', sourceMultiplicity: { lowerBound: 1, upperBound: 1 }, targetMultiplicity: { lowerBound: 1, upperBound: 1 }, oppositeRelationId: 'customer-orders', ordered: true, unique: true, annotations: {} },
            { id: 'customer-named', name: '', sourceTypeId: 'customer', targetTypeId: 'named', kind: 'IMPLEMENTATION', sourceMultiplicity: { lowerBound: 1, upperBound: 1 }, targetMultiplicity: { lowerBound: 1, upperBound: 1 }, oppositeRelationId: '', ordered: true, unique: true, annotations: {} }
        ]
    };
}

export function canonicalDiagramFixture(): MetamodelDiagramDocument {
    return {
        metamodelId: 'shop',
        nodes: [
            { elementId: 'root', x: 10, y: 10, width: 240, height: 120 },
            { elementId: 'customer', x: 320, y: 10, width: 240, height: 160 },
            { elementId: 'order', x: 630, y: 10, width: 240, height: 120 },
            { elementId: 'named', x: 320, y: 260, width: 240, height: 100 },
            { elementId: 'status', x: 10, y: 260, width: 220, height: 100 },
            { elementId: 'money', x: 630, y: 260, width: 220, height: 100 }
        ],
        edges: [
            { relationId: 'contains-customers', sourceClassId: 'root', targetClassId: 'customer', sourceHandle: 'right-source', targetHandle: 'left-target', kind: 'CONTAINMENT', routePoints: [{ x: 250, y: 70 }, { x: 320, y: 70 }] },
            { relationId: 'customer-orders', sourceClassId: 'customer', targetClassId: 'order', sourceHandle: 'right-source', targetHandle: 'left-target', kind: 'REFERENCE', routePoints: [{ x: 560, y: 80 }, { x: 630, y: 80 }] },
            { relationId: 'order-customer', sourceClassId: 'order', targetClassId: 'customer', sourceHandle: 'left-source', targetHandle: 'right-target', kind: 'REFERENCE' },
            { relationId: 'customer-named', sourceClassId: 'customer', targetClassId: 'named', sourceHandle: 'bottom-source', targetHandle: 'top-target', kind: 'IMPLEMENTATION' }
        ]
    };
}
