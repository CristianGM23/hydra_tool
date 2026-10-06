import type { MetaAttribute, MetaClass, MetaDataType, MetaEnum, MetaModel, MetaOperation, MetaParameter, MetaRelation, MetaTypeReference } from './types';

export function updateClass(model: MetaModel, classId: string, updater: (value: MetaClass) => MetaClass): MetaModel {
    return { ...model, classes: model.classes.map(item => item.id === classId ? updater(item) : item) };
}

export function updateAttribute(model: MetaModel, classId: string, attributeId: string, updater: (value: MetaAttribute) => MetaAttribute): MetaModel {
    return updateClass(model, classId, metaClass => ({ ...metaClass, attributes: metaClass.attributes.map(item => item.id === attributeId ? updater(item) : item) }));
}


export function updateOperation(model: MetaModel, classId: string, operationId: string, updater: (value: MetaOperation) => MetaOperation): MetaModel {
    return updateClass(model, classId, metaClass => ({ ...metaClass, operations: metaClass.operations.map(item => item.id === operationId ? updater(item) : item) }));
}

export function updateParameter(model: MetaModel, classId: string, operationId: string, parameterId: string, updater: (value: MetaParameter) => MetaParameter): MetaModel {
    return updateOperation(model, classId, operationId, operation => ({ ...operation, parameters: operation.parameters.map(item => item.id === parameterId ? updater(item) : item) }));
}

export function updateEnum(model: MetaModel, enumId: string, updater: (value: MetaEnum) => MetaEnum): MetaModel {
    return { ...model, enums: model.enums.map(item => item.id === enumId ? updater(item) : item) };
}

export function updateDataType(model: MetaModel, dataTypeId: string, updater: (value: MetaDataType) => MetaDataType): MetaModel {
    return { ...model, dataTypes: model.dataTypes.map(item => item.id === dataTypeId ? updater(item) : item) };
}

export function updateRelation(model: MetaModel, relationId: string, updater: (value: MetaRelation) => MetaRelation): MetaModel {
    return { ...model, relations: model.relations.map(item => item.id === relationId ? updater(item) : item) };
}

export function removeClass(model: MetaModel, classId: string): MetaModel {
    const removedRelations = new Set(model.relations.filter(item => item.sourceTypeId === classId || item.targetTypeId === classId).map(item => item.id));
    const relations = model.relations
        .filter(item => !removedRelations.has(item.id))
        .map(item => removedRelations.has(item.oppositeRelationId) ? { ...item, oppositeRelationId: '' } : item);
    return {
        ...model,
        classes: model.classes
            .filter(item => item.id !== classId)
            .map(item => ({ ...item, operations: item.operations.filter(operation => !operationUsesType(operation, 'CLASS', classId)) })),
        relations
    };
}

export function removeRelation(model: MetaModel, relationId: string): MetaModel {
    return {
        ...model,
        relations: model.relations
            .filter(item => item.id !== relationId)
            .map(item => item.oppositeRelationId === relationId ? { ...item, oppositeRelationId: '' } : item)
    };
}

export function removeEnum(model: MetaModel, enumId: string): MetaModel {
    return {
        ...model,
        enums: model.enums.filter(item => item.id !== enumId),
        classes: model.classes.map(metaClass => ({ ...metaClass, attributes: metaClass.attributes.filter(attribute => !(attribute.type.kind === 'ENUM' && attribute.type.targetId === enumId)), operations: metaClass.operations.filter(operation => !operationUsesType(operation, 'ENUM', enumId)) }))
    };
}

export function removeDataType(model: MetaModel, dataTypeId: string): MetaModel {
    return {
        ...model,
        dataTypes: model.dataTypes.filter(item => item.id !== dataTypeId),
        classes: model.classes.map(metaClass => ({ ...metaClass, attributes: metaClass.attributes.filter(attribute => !(attribute.type.kind === 'DATATYPE' && attribute.type.targetId === dataTypeId)), operations: metaClass.operations.filter(operation => !operationUsesType(operation, 'DATATYPE', dataTypeId)) }))
    };
}

export function addRelation(model: MetaModel, relation: MetaRelation): MetaModel {
    return { ...model, relations: [...model.relations, relation] };
}

export function createUniqueRelationName(model: MetaModel, sourceClassId: string, targetName: string): string {
    const used = new Set(model.relations.filter(item => item.sourceTypeId === sourceClassId).map(item => item.name));
    let base = targetName.trim();
    if (base === '') {
        base = 'target';
    } else {
        base = base.charAt(0).toLowerCase() + base.slice(1);
    }
    let candidate = base;
    let suffix = 2;
    while (used.has(candidate)) {
        candidate = `${base}${suffix++}`;
    }
    return candidate;
}

function operationUsesType(operation: MetaOperation, kind: MetaTypeReference['kind'], targetId: string): boolean {
    const references = [operation.returnType, ...operation.parameters.map(parameter => parameter.type), ...operation.exceptionTypes];
    return references.some(reference => reference !== null && reference.kind === kind && reference.targetId === targetId);
}
