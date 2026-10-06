import { describe, expect, it } from 'vitest';
import { deleteMetamodelElementsInBatch, deleteMetamodelRelationsInBatch, findPrimaryContainmentRoot } from './batchDeletion';
import { createsSuperTypeCycle, reverseConnection, validateAndNormalizeConnection } from './connectionValidation';
import { collectIncomingReferences, updateReferenceEndpoint } from './referenceEndpoints';
import { canonicalDiagramFixture, canonicalMetaModelFixture } from './test-fixtures/canonicalMetaModelFixture';

describe('canonical batch deletion', () => {
    it('finds the root of a complete containment tree', () => expect(findPrimaryContainmentRoot(canonicalMetaModelFixture())).toBe('root'));
    it('preserves the containment root', () => { const r=deleteMetamodelElementsInBatch(canonicalMetaModelFixture(),canonicalDiagramFixture(),['root','customer']); expect(r.preservedElementIds).toContain('root'); expect(r.deletedElementIds).toContain('customer'); });
    it('removes deleted nodes and incident edges', () => { const r=deleteMetamodelElementsInBatch(canonicalMetaModelFixture(),canonicalDiagramFixture(),['order']); expect(r.diagram.nodes.some(n=>n.elementId==='order')).toBe(false); expect(r.diagram.edges.some(e=>e.sourceClassId==='order'||e.targetClassId==='order')).toBe(false); });
    it('ignores unknown ids', () => expect(deleteMetamodelElementsInBatch(canonicalMetaModelFixture(),canonicalDiagramFixture(),['missing']).deletedElementIds).toEqual([]));
    it('deduplicates requested ids', () => expect(deleteMetamodelElementsInBatch(canonicalMetaModelFixture(),canonicalDiagramFixture(),['order','order']).deletedElementIds).toEqual(['order']));
    it('deletes multiple global relations', () => { const r=deleteMetamodelRelationsInBatch(canonicalMetaModelFixture(),canonicalDiagramFixture(),['customer-orders','order-customer']); expect(r.model.relations.some(x=>x.id==='customer-orders')).toBe(false); expect(r.diagram.edges.some(x=>x.relationId==='order-customer')).toBe(false); });
    it('preserves one class when all elements are selected without containment', () => { const m={...canonicalMetaModelFixture(),relations:[]}; const ids=[...m.classes.map(x=>x.id),...m.enums.map(x=>x.id),...m.dataTypes.map(x=>x.id)]; const r=deleteMetamodelElementsInBatch(m,canonicalDiagramFixture(),ids); expect(r.model.classes.length).toBe(1); });
});

describe('canonical connection validation', () => {
    it('requires both endpoints', () => expect(validateAndNormalizeConnection(canonicalMetaModelFixture(),{source:null,target:'order',sourceHandle:null,targetHandle:null} as unknown as import('@xyflow/react').Connection,'REFERENCE').valid).toBe(false));
    it('rejects non-class endpoints', () => expect(validateAndNormalizeConnection(canonicalMetaModelFixture(),{source:'status',target:'order',sourceHandle:null,targetHandle:null},'REFERENCE').valid).toBe(false));
    it('accepts class references', () => expect(validateAndNormalizeConnection(canonicalMetaModelFixture(),{source:'customer',target:'order',sourceHandle:null,targetHandle:null},'REFERENCE').valid).toBe(true));
    it('rejects class-interface references', () => expect(validateAndNormalizeConnection(canonicalMetaModelFixture(),{source:'customer',target:'named',sourceHandle:null,targetHandle:null},'REFERENCE').valid).toBe(false));
    it('accepts implementation from class to interface', () => { const r=validateAndNormalizeConnection(canonicalMetaModelFixture(),{source:'customer',target:'named',sourceHandle:null,targetHandle:null},'IMPLEMENTATION'); expect(r.valid && r.value.sourceClass.id).toBe('customer'); });
    it('normalizes implementation drawn from interface to class', () => { const r=validateAndNormalizeConnection(canonicalMetaModelFixture(),{source:'named',target:'customer',sourceHandle:'a',targetHandle:'b'},'IMPLEMENTATION'); expect(r.valid && r.value.connection).toMatchObject({source:'customer',target:'named',sourceHandle:'b',targetHandle:'a'}); });
    it('rejects class-interface inheritance', () => expect(validateAndNormalizeConnection(canonicalMetaModelFixture(),{source:'customer',target:'named',sourceHandle:null,targetHandle:null},'INHERITANCE').valid).toBe(false));
    it('detects a direct supertype cycle', () => expect(createsSuperTypeCycle(canonicalMetaModelFixture(),'named','customer')).toBe(true));
    it('reverses handles together with endpoints', () => expect(reverseConnection({source:'a',target:'b',sourceHandle:'left',targetHandle:'right'})).toEqual({source:'b',target:'a',sourceHandle:'right',targetHandle:'left'}));
});

describe('canonical reference endpoints', () => {
    it('collects incoming references from global relations', () => expect(collectIncomingReferences(canonicalMetaModelFixture(),'customer').map(x=>x.referenceId)).toEqual(['order-customer']));
    it('moves a source endpoint while preserving relation identity', () => { const r=updateReferenceEndpoint(canonicalMetaModelFixture(),canonicalDiagramFixture(),'customer-orders','SOURCE','root'); expect(r.valid && r.model.relations.find(x=>x.id==='customer-orders')?.sourceTypeId).toBe('root'); });
    it('moves both ends of an opposite pair consistently', () => { const r=updateReferenceEndpoint(canonicalMetaModelFixture(),canonicalDiagramFixture(),'customer-orders','TARGET','root'); expect(r.valid).toBe(true); if(r.valid){ expect(r.model.relations.find(x=>x.id==='customer-orders')).toMatchObject({sourceTypeId:'customer',targetTypeId:'root'}); expect(r.model.relations.find(x=>x.id==='order-customer')).toMatchObject({sourceTypeId:'root',targetTypeId:'customer'}); } });
    it('clears stale routes after endpoint movement', () => { const r=updateReferenceEndpoint(canonicalMetaModelFixture(),canonicalDiagramFixture(),'customer-orders','TARGET','root'); expect(r.valid && r.diagram.edges.find(x=>x.relationId==='customer-orders')?.routePoints).toEqual([]); });
    it('rejects missing relations', () => expect(updateReferenceEndpoint(canonicalMetaModelFixture(),canonicalDiagramFixture(),'missing','TARGET','root')).toEqual({valid:false,error:'Selected reference was not found'}));
    it('rejects missing endpoint classes', () => expect(updateReferenceEndpoint(canonicalMetaModelFixture(),canonicalDiagramFixture(),'customer-orders','TARGET','missing').valid).toBe(false));
    it('returns the same model for unchanged endpoints', () => { const m=canonicalMetaModelFixture(); const d=canonicalDiagramFixture(); const r=updateReferenceEndpoint(m,d,'customer-orders','TARGET','order'); expect(r.valid && r.model).toBe(m); });
});
