import type { MetaAttribute, MetaClass, MetaClassKind, MetaDataType, MetaEnum, MetaModel, MetaOperation, MetaParameter, MetaRelation, MetaRelationKind, MetaTypeReference, Multiplicity } from './types';

export const META_MODEL_SCHEMA_VERSION = '2.1' as const;

export function createInitialMetaModel(): MetaModel {
    const id = createMetaModelId('metamodel');
    return {
        schemaVersion: META_MODEL_SCHEMA_VERSION,
        id,
        name: 'New metamodel',
        classes: [createMetaClass('NewClass')],
        enums: [],
        dataTypes: [],
        relations: [],
        annotations: {}
    };
}

export function createMetaClass(name: string, kind: MetaClassKind = 'CLASS'): MetaClass {
    return { id: createMetaModelId('class'), name, kind, attributes: [], operations: [], annotations: {} };
}

export function createMetaDataType(name: string): MetaDataType {
    return { id: createMetaModelId('datatype'), name, annotations: {} };
}

export function createMetaEnum(name: string): MetaEnum {
    return {
        id: createMetaModelId('enum'),
        name,
        literals: [{ id: createMetaModelId('literal'), name: 'VALUE', value: 0, annotations: {} }],
        annotations: {}
    };
}

export function createMetaAttribute(type: MetaTypeReference = primitiveType('STRING')): MetaAttribute {
    return {
        id: createMetaModelId('attribute'),
        name: 'newAttribute',
        type,
        multiplicity: optionalMultiplicity(),
        ordered: true,
        unique: true,
        defaultValue: '',
        annotations: {}
    };
}


export function createMetaParameter(type: MetaTypeReference = primitiveType('STRING')): MetaParameter {
    return { id: createMetaModelId('parameter'), name: 'newParameter', type, multiplicity: oneMultiplicity(), ordered: true, unique: true, annotations: {} };
}

export function createMetaOperation(): MetaOperation {
    return {
        id: createMetaModelId('operation'),
        name: 'newOperation',
        returnType: null,
        returnMultiplicity: null,
        ordered: true,
        unique: true,
        parameters: [],
        exceptionTypes: [],
        behaviorKind: 'UNSPECIFIED',
        annotations: {}
    };
}

export function createMetaRelation(sourceTypeId: string, targetTypeId: string, kind: MetaRelationKind = 'REFERENCE', name: string = 'newReference'): MetaRelation {
    return {
        id: createMetaModelId('relation'),
        name,
        sourceTypeId,
        targetTypeId,
        kind,
        sourceMultiplicity: oneMultiplicity(),
        targetMultiplicity: optionalMultiplicity(),
        oppositeRelationId: '',
        ordered: true,
        unique: true,
        annotations: {}
    };
}

export function primitiveType(primitive: string): MetaTypeReference {
    return { kind: 'PRIMITIVE', targetId: primitive, externalName: '' };
}

export function enumType(enumId: string): MetaTypeReference {
    return { kind: 'ENUM', targetId: enumId, externalName: '' };
}

export function dataType(dataTypeId: string): MetaTypeReference {
    return { kind: 'DATATYPE', targetId: dataTypeId, externalName: '' };
}

export function externalType(name: string): MetaTypeReference {
    return { kind: 'EXTERNAL', targetId: '', externalName: name };
}

export function optionalMultiplicity(): Multiplicity {
    return { lowerBound: 0, upperBound: 1 };
}

export function oneMultiplicity(): Multiplicity {
    return { lowerBound: 1, upperBound: 1 };
}

export function manyMultiplicity(): Multiplicity {
    return { lowerBound: 0, upperBound: null };
}

export function createMetaModelId(prefix: string): string {
    if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
        return crypto.randomUUID();
    }
    return `${prefix}-${Date.now()}-${Math.random().toString(16).slice(2)}`;
}
