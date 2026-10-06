import type { Connection } from '@xyflow/react';
import type { MetaClass, MetaModel, MetamodelConnectionKind } from './types';

export interface ValidatedMetamodelConnection {
    connection: Connection;
    sourceClass: MetaClass;
    targetClass: MetaClass;
}
export type MetamodelConnectionValidationResult = { valid: true; value: ValidatedMetamodelConnection } | { valid: false; error: string };

export function validateAndNormalizeConnection(model: MetaModel, connection: Connection, kind: MetamodelConnectionKind): MetamodelConnectionValidationResult {
    if (connection.source === null || connection.target === null) return { valid: false, error: 'Connection source and target are required' };
    const initialSource = model.classes.find(item => item.id === connection.source);
    const initialTarget = model.classes.find(item => item.id === connection.target);
    if (initialSource === undefined || initialTarget === undefined) return { valid: false, error: 'Connections can only be created between classes' };
    const sourceInterface = initialSource.kind === 'INTERFACE';
    const targetInterface = initialTarget.kind === 'INTERFACE';
    const interfaceCount = Number(sourceInterface) + Number(targetInterface);
    if (interfaceCount === 1 && kind !== 'IMPLEMENTATION') return { valid: false, error: 'A class and an interface can only be connected using Implementation' };
    if (kind === 'IMPLEMENTATION') {
        if (interfaceCount !== 1) return { valid: false, error: 'Implementation requires one class and one interface' };
        if (sourceInterface) return { valid: true, value: { connection: reverseConnection(connection), sourceClass: initialTarget, targetClass: initialSource } };
    }
    if (kind === 'INHERITANCE' && sourceInterface !== targetInterface) return { valid: false, error: 'Inheritance must connect two classes or two interfaces' };
    return { valid: true, value: { connection, sourceClass: initialSource, targetClass: initialTarget } };
}

export function createsSuperTypeCycle(model: MetaModel, sourceClassId: string, targetClassId: string): boolean {
    const pending = [targetClassId];
    const visited = new Set<string>();
    while (pending.length > 0) {
        const currentId = pending.pop();
        if (currentId === undefined) continue;
        if (currentId === sourceClassId) return true;
        if (visited.has(currentId)) continue;
        visited.add(currentId);
        for (const relation of model.relations) {
            if (relation.sourceTypeId === currentId && (relation.kind === 'INHERITANCE' || relation.kind === 'IMPLEMENTATION')) pending.push(relation.targetTypeId);
        }
    }
    return false;
}

export function reverseConnection(connection: Connection): Connection {
    return { source: connection.target, target: connection.source, sourceHandle: connection.targetHandle, targetHandle: connection.sourceHandle };
}
