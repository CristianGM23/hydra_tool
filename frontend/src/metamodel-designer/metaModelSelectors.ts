import type { MetaAttribute, MetaClass, MetaEnum, MetaDataType, MetaModel, MetaOperation, MetaParameter, MetaRelation, MetaRelationKind, MetaTypeReference } from './types';

export function findMetaClass(model: MetaModel, id: string): MetaClass | undefined {
    return model.classes.find(item => item.id === id);
}

export function findMetaEnum(model: MetaModel, id: string): MetaEnum | undefined {
    return model.enums.find(item => item.id === id);
}

export function findMetaDataType(model: MetaModel, id: string): MetaDataType | undefined {
    return model.dataTypes.find(item => item.id === id);
}

export function findMetaRelation(model: MetaModel, id: string): MetaRelation | undefined {
    return model.relations.find(item => item.id === id);
}

export function findMetaAttribute(model: MetaModel, classId: string, attributeId: string): MetaAttribute | undefined {
    return findMetaClass(model, classId)?.attributes.find(item => item.id === attributeId);
}


export function findMetaOperation(model: MetaModel, classId: string, operationId: string): MetaOperation | undefined {
    return findMetaClass(model, classId)?.operations.find(item => item.id === operationId);
}

export function findMetaParameter(model: MetaModel, classId: string, operationId: string, parameterId: string): MetaParameter | undefined {
    return findMetaOperation(model, classId, operationId)?.parameters.find(item => item.id === parameterId);
}

export function outgoingRelations(model: MetaModel, classId: string, kinds?: ReadonlySet<MetaRelationKind>): MetaRelation[] {
    return model.relations.filter(relation => relation.sourceTypeId === classId && (kinds === undefined || kinds.has(relation.kind)));
}

export function incomingRelations(model: MetaModel, classId: string, kinds?: ReadonlySet<MetaRelationKind>): MetaRelation[] {
    return model.relations.filter(relation => relation.targetTypeId === classId && (kinds === undefined || kinds.has(relation.kind)));
}

export function semanticReferenceRelations(model: MetaModel): MetaRelation[] {
    return model.relations.filter(relation => relation.kind === 'REFERENCE' || relation.kind === 'CONTAINMENT' || relation.kind === 'AGGREGATION');
}

export function superTypeRelations(model: MetaModel, sourceClassId: string): MetaRelation[] {
    return model.relations.filter(relation => relation.sourceTypeId === sourceClassId && (relation.kind === 'INHERITANCE' || relation.kind === 'IMPLEMENTATION'));
}

export function typeReferenceKey(type: MetaTypeReference): string {
    if (type.kind === 'EXTERNAL') {
        return `external:${type.externalName}`;
    }
    return `${type.kind.toLowerCase()}:${type.targetId}`;
}

export function typeReferenceFromKey(key: string): MetaTypeReference {
    const separator = key.indexOf(':');
    if (separator < 0) {
        return { kind: 'PRIMITIVE', targetId: key, externalName: '' };
    }
    const prefix = key.slice(0, separator).toUpperCase();
    const value = key.slice(separator + 1);
    if (prefix === 'ENUM' || prefix === 'DATATYPE' || prefix === 'CLASS' || prefix === 'PRIMITIVE') {
        return { kind: prefix, targetId: value, externalName: '' };
    }
    return { kind: 'EXTERNAL', targetId: '', externalName: value };
}

export function classifierName(model: MetaModel, id: string): string {
    return model.classes.find(item => item.id === id)?.name
        ?? model.enums.find(item => item.id === id)?.name
        ?? model.dataTypes.find(item => item.id === id)?.name
        ?? id;
}

export function typeReferenceName(model: MetaModel, type: MetaTypeReference): string {
    if (type.kind === 'EXTERNAL') {
        return type.externalName;
    }
    if (type.kind === 'PRIMITIVE') {
        return primitiveDisplayName(type.targetId);
    }
    return classifierName(model, type.targetId);
}

export function primitiveDisplayName(id: string): string {
    const words = id.toLowerCase().split('_');
    return words.map(word => word.charAt(0).toUpperCase() + word.slice(1)).join(' ');
}

export function relationKinds(...kinds: MetaRelationKind[]): ReadonlySet<MetaRelationKind> {
    return new Set(kinds);
}
