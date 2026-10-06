import { describe, expect, it } from 'vitest';
import { createInitialMetaModel, createMetaAttribute, createMetaClass, createMetaDataType, createMetaEnum, createMetaOperation, createMetaParameter, createMetaRelation, dataType, enumType, externalType, manyMultiplicity, oneMultiplicity, optionalMultiplicity, primitiveType } from './metaModelFactory';
import { addRelation, createUniqueRelationName, removeClass, removeDataType, removeEnum, removeRelation, updateAttribute, updateClass, updateDataType, updateEnum, updateOperation, updateParameter, updateRelation } from './metaModelOperations';
import { classifierName, findMetaAttribute, findMetaClass, findMetaDataType, findMetaEnum, findMetaOperation, findMetaParameter, findMetaRelation, incomingRelations, outgoingRelations, primitiveDisplayName, relationKinds, semanticReferenceRelations, superTypeRelations, typeReferenceFromKey, typeReferenceKey, typeReferenceName } from './metaModelSelectors';
import { findMetamodelDataTypeError } from './dataTypeUtils';
import { createUniqueEnumLiteral, findEnumLiteralError, findMetamodelEnumError } from './enumUtils';
import { canonicalMetaModelFixture } from './test-fixtures/canonicalMetaModelFixture';

describe('canonical MetaModel factories', () => {
    it('creates schema version 2.1 directly', () => expect(createInitialMetaModel().schemaVersion).toBe('2.1'));
    it('creates one initial class', () => expect(createInitialMetaModel().classes).toHaveLength(1));
    it('creates classes with explicit kinds', () => expect(createMetaClass('Named', 'INTERFACE').kind).toBe('INTERFACE'));
    it('creates an enum with one valid literal', () => expect(createMetaEnum('State').literals[0]).toMatchObject({ name: 'VALUE', value: 0 }));
    it('creates a neutral datatype without Java options', () => expect(createMetaDataType('Money')).toEqual(expect.objectContaining({ name: 'Money', annotations: {} })));
    it('creates attributes with structured primitive types', () => expect(createMetaAttribute(primitiveType('INTEGER')).type).toEqual({ kind: 'PRIMITIVE', targetId: 'INTEGER', externalName: '' }));
    it('creates operations and parameters with neutral defaults', () => { const operation=createMetaOperation(); const parameter=createMetaParameter(primitiveType('INTEGER')); expect(operation).toMatchObject({name:'newOperation',returnType:null,behaviorKind:'UNSPECIFIED',parameters:[]}); expect(parameter).toMatchObject({name:'newParameter',type:{kind:'PRIMITIVE',targetId:'INTEGER'}}); });
    it('creates relations in the global collection shape', () => expect(createMetaRelation('a', 'b', 'CONTAINMENT')).toMatchObject({ sourceTypeId: 'a', targetTypeId: 'b', kind: 'CONTAINMENT' }));
    it.each([[optionalMultiplicity(), 0, 1], [oneMultiplicity(), 1, 1], [manyMultiplicity(), 0, null]])('creates multiplicity %#', (value, lower, upper) => expect(value).toEqual({ lowerBound: lower, upperBound: upper }));
    it('creates enum references', () => expect(enumType('state')).toEqual({ kind: 'ENUM', targetId: 'state', externalName: '' }));
    it('creates datatype references', () => expect(dataType('money')).toEqual({ kind: 'DATATYPE', targetId: 'money', externalName: '' }));
    it('creates external references without target ids', () => expect(externalType('java.net.URI')).toEqual({ kind: 'EXTERNAL', targetId: '', externalName: 'java.net.URI' }));
});

describe('canonical MetaModel operations', () => {
    it('updates a class immutably', () => { const m=canonicalMetaModelFixture(); const n=updateClass(m,'customer',c=>({...c,name:'Buyer'})); expect(n.classes[1].name).toBe('Buyer'); expect(m.classes[1].name).toBe('Customer'); });
    it('updates an attribute immutably', () => { const n=updateAttribute(canonicalMetaModelFixture(),'customer','customer-name',a=>({...a,name:'fullName'})); expect(findMetaAttribute(n,'customer','customer-name')?.name).toBe('fullName'); });
    it('updates operations and parameters immutably', () => { const m=canonicalMetaModelFixture(); const operation=createMetaOperation(); const parameter=createMetaParameter(); const withOperation=updateClass(m,'customer',c=>({...c,operations:[{...operation,id:'operation:customer:label',parameters:[{...parameter,id:'parameter:label:prefix'}]}]})); const updated=updateParameter(updateOperation(withOperation,'customer','operation:customer:label',o=>({...o,name:'label'})),'customer','operation:customer:label','parameter:label:prefix',p=>({...p,name:'prefix'})); expect(findMetaOperation(updated,'customer','operation:customer:label')?.name).toBe('label'); expect(findMetaParameter(updated,'customer','operation:customer:label','parameter:label:prefix')?.name).toBe('prefix'); });
    it('updates enums', () => expect(updateEnum(canonicalMetaModelFixture(),'status',e=>({...e,name:'OrderStatus'})).enums[0].name).toBe('OrderStatus'));
    it('updates datatypes', () => expect(updateDataType(canonicalMetaModelFixture(),'money',d=>({...d,name:'Amount'})).dataTypes[0].name).toBe('Amount'));
    it('updates global relations', () => expect(updateRelation(canonicalMetaModelFixture(),'customer-orders',r=>({...r,name:'purchases'})).relations[1].name).toBe('purchases'));
    it('adds a global relation', () => expect(addRelation(canonicalMetaModelFixture(),createMetaRelation('root','order')).relations).toHaveLength(5));
    it('removes a class and every incident relation', () => { const n=removeClass(canonicalMetaModelFixture(),'customer'); expect(n.classes.some(c=>c.id==='customer')).toBe(false); expect(n.relations.some(r=>r.sourceTypeId==='customer'||r.targetTypeId==='customer')).toBe(false); });
    it('removes a relation and clears reverse opposite ids', () => { const n=removeRelation(canonicalMetaModelFixture(),'customer-orders'); expect(findMetaRelation(n,'customer-orders')).toBeUndefined(); expect(findMetaRelation(n,'order-customer')?.oppositeRelationId).toBe(''); });
    it('removes enum attributes that reference the deleted enum', () => { const n=removeEnum(canonicalMetaModelFixture(),'status'); expect(findMetaClass(n,'customer')?.attributes.some(a=>a.type.targetId==='status')).toBe(false); });
    it('removes operations that use a deleted classifier type', () => { const m=canonicalMetaModelFixture(); const operation={...createMetaOperation(),id:'operation:customer:status',returnType:enumType('status'),returnMultiplicity:oneMultiplicity()}; m.classes[1]={...m.classes[1],operations:[operation]}; const n=removeEnum(m,'status'); expect(findMetaClass(n,'customer')?.operations).toHaveLength(0); });
    it('removes datatype attributes that reference the deleted datatype', () => { const m=canonicalMetaModelFixture(); m.classes[0].attributes=[createMetaAttribute(dataType('money'))]; const n=removeDataType(m,'money'); expect(n.classes[0].attributes).toHaveLength(0); });
    it('generates a lower camel relation name', () => expect(createUniqueRelationName(canonicalMetaModelFixture(),'root','Order')).toBe('order'));
    it('increments duplicate relation names', () => { const m={...canonicalMetaModelFixture(),relations:[createMetaRelation('root','order','REFERENCE','order')]}; expect(createUniqueRelationName(m,'root','Order')).toBe('order2'); });
});

describe('canonical selectors and validation helpers', () => {
    it('finds every classifier kind by id', () => { const m=canonicalMetaModelFixture(); expect(findMetaClass(m,'customer')?.name).toBe('Customer'); expect(findMetaEnum(m,'status')?.name).toBe('Status'); expect(findMetaDataType(m,'money')?.name).toBe('Money'); });
    it('finds relations by stable id', () => expect(findMetaRelation(canonicalMetaModelFixture(),'customer-orders')?.kind).toBe('REFERENCE'));
    it('selects outgoing relations', () => expect(outgoingRelations(canonicalMetaModelFixture(),'customer')).toHaveLength(2));
    it('selects incoming relations', () => expect(incomingRelations(canonicalMetaModelFixture(),'customer')).toHaveLength(2));
    it('filters relation kinds', () => expect(outgoingRelations(canonicalMetaModelFixture(),'customer',relationKinds('IMPLEMENTATION'))).toHaveLength(1));
    it('selects only structural references', () => expect(semanticReferenceRelations(canonicalMetaModelFixture()).every(r=>['REFERENCE','CONTAINMENT','AGGREGATION'].includes(r.kind))).toBe(true));
    it('selects supertype relations', () => expect(superTypeRelations(canonicalMetaModelFixture(),'customer').map(r=>r.id)).toEqual(['customer-named']));
    it.each([primitiveType('STRING'), enumType('status'), dataType('money'), externalType('java.net.URI')])('round trips type reference %#', value => expect(typeReferenceFromKey(typeReferenceKey(value))).toEqual(value));
    it('resolves classifier names', () => expect(classifierName(canonicalMetaModelFixture(),'customer')).toBe('Customer'));
    it('resolves primitive display names', () => expect(primitiveDisplayName('BIG_INTEGER')).toBe('Big Integer'));
    it('resolves external type names', () => expect(typeReferenceName(canonicalMetaModelFixture(),externalType('URI'))).toBe('URI'));
    it('detects duplicate classifier names', () => { const m=canonicalMetaModelFixture(); m.dataTypes.push({id:'other',name:'Customer',annotations:{}}); expect(findMetamodelDataTypeError(m)).toContain('Duplicate classifier name'); });
    it('creates unique enum literals', () => { const e=canonicalMetaModelFixture().enums[0]; expect(createUniqueEnumLiteral(e,()=> 'new')).toMatchObject({id:'new',name:'VALUE',value:2}); });
    it('detects duplicate enum literal values', () => { const e=canonicalMetaModelFixture().enums[0]; e.literals[1]={...e.literals[1],value:0}; expect(findEnumLiteralError(e)).toContain('Duplicate enumeration literal value'); });
    it('validates all enums from the model', () => expect(findMetamodelEnumError(canonicalMetaModelFixture())).toBe(''));
});
