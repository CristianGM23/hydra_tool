import { removeClass, removeDataType, removeEnum, removeRelation } from './metaModelOperations';
import type { MetaModel, MetamodelDiagramDocument } from './types';

export interface MetamodelBatchDeleteResult {
    model: MetaModel;
    diagram: MetamodelDiagramDocument;
    deletedElementIds: string[];
    preservedElementIds: string[];
    preservedReason: 'CONTAINMENT_ROOT' | 'FIRST_CANVAS_ELEMENT' | 'LAST_CLASS' | 'NONE';
}

export function deleteMetamodelElementsInBatch(model: MetaModel, diagram: MetamodelDiagramDocument, requestedElementIds: string[]): MetamodelBatchDeleteResult {
    const existing = allElementIds(model);
    const requested = unique(requestedElementIds).filter(id => existing.has(id));
    const preserved = new Set<string>();
    let preservedReason: MetamodelBatchDeleteResult['preservedReason'] = 'NONE';
    const containmentRoot = findPrimaryContainmentRoot(model);
    if (containmentRoot !== '' && requested.includes(containmentRoot)) {
        preserved.add(containmentRoot);
        preservedReason = 'CONTAINMENT_ROOT';
    }
    const hasContainment = model.relations.some(item => item.kind === 'CONTAINMENT');
    if (!hasContainment && requested.length === existing.size) {
        const first = firstElementId(model, diagram);
        if (first !== '') {
            preserved.add(first);
            preservedReason = 'FIRST_CANVAS_ELEMENT';
        }
    }
    const requestedClasses = requested.filter(id => model.classes.some(item => item.id === id));
    if (model.classes.length - requestedClasses.filter(id => !preserved.has(id)).length <= 0 && model.classes.length > 0) {
        const fallback = firstClassId(model, diagram, containmentRoot);
        if (fallback !== '') {
            preserved.add(fallback);
            if (preservedReason === 'NONE') preservedReason = 'LAST_CLASS';
        }
    }
    const deletedElementIds = requested.filter(id => !preserved.has(id));
    let nextModel = model;
    for (const id of deletedElementIds) {
        if (nextModel.classes.some(item => item.id === id)) nextModel = removeClass(nextModel, id);
        else if (nextModel.enums.some(item => item.id === id)) nextModel = removeEnum(nextModel, id);
        else if (nextModel.dataTypes.some(item => item.id === id)) nextModel = removeDataType(nextModel, id);
    }
    const deletedSet = new Set(deletedElementIds);
    const validRelations = new Set(nextModel.relations.map(item => item.id));
    const nextDiagram = {
        ...diagram,
        nodes: diagram.nodes.filter(node => !deletedSet.has(node.elementId)),
        edges: diagram.edges.filter(edge => !deletedSet.has(edge.sourceClassId ?? '') && !deletedSet.has(edge.targetClassId ?? '') && validRelations.has(edge.relationId))
    };
    return { model: nextModel, diagram: nextDiagram, deletedElementIds, preservedElementIds: [...preserved], preservedReason };
}

export function deleteMetamodelRelationsInBatch(model: MetaModel, diagram: MetamodelDiagramDocument, relationIds: string[]): { model: MetaModel; diagram: MetamodelDiagramDocument; deletedRelationIds: string[] } {
    const requested = unique(relationIds).filter(id => model.relations.some(item => item.id === id));
    let nextModel = model;
    for (const id of requested) nextModel = removeRelation(nextModel, id);
    const deleted = new Set(requested);
    return { model: nextModel, diagram: { ...diagram, edges: diagram.edges.filter(edge => !deleted.has(edge.relationId)) }, deletedRelationIds: requested };
}

export function findPrimaryContainmentRoot(model: MetaModel): string {
    const classIds = new Set(model.classes.map(item => item.id));
    const participatingClassIds = new Set<string>();
    const incoming = new Map<string, number>();
    const outgoing = new Map<string, string[]>();
    for (const metaClass of model.classes) { incoming.set(metaClass.id, 0); outgoing.set(metaClass.id, []); }
    for (const relation of model.relations) {
        if (relation.kind !== 'CONTAINMENT' || !classIds.has(relation.sourceTypeId) || !classIds.has(relation.targetTypeId)) continue;
        participatingClassIds.add(relation.sourceTypeId);
        participatingClassIds.add(relation.targetTypeId);
        outgoing.get(relation.sourceTypeId)?.push(relation.targetTypeId);
        incoming.set(relation.targetTypeId, (incoming.get(relation.targetTypeId) ?? 0) + 1);
    }
    if (participatingClassIds.size === 0) return '';
    let selected = '';
    let selectedReach = 0;
    for (const candidateId of participatingClassIds) {
        if ((incoming.get(candidateId) ?? 0) !== 0) continue;
        const reach = reachable(candidateId, outgoing).size;
        if (reach > selectedReach) { selected = candidateId; selectedReach = reach; }
    }
    return selectedReach === participatingClassIds.size ? selected : '';
}

function firstElementId(model: MetaModel, diagram: MetamodelDiagramDocument): string {
    const existing = allElementIds(model);
    return diagram.nodes.find(node => existing.has(node.elementId))?.elementId ?? model.classes[0]?.id ?? model.enums[0]?.id ?? model.dataTypes[0]?.id ?? '';
}
function firstClassId(model: MetaModel, diagram: MetamodelDiagramDocument, preferred: string): string {
    if (preferred !== '' && model.classes.some(item => item.id === preferred)) return preferred;
    return diagram.nodes.find(node => model.classes.some(item => item.id === node.elementId))?.elementId ?? model.classes[0]?.id ?? '';
}
function reachable(rootId: string, outgoing: Map<string, string[]>): Set<string> {
    const result = new Set<string>();
    const pending = [rootId];
    while (pending.length > 0) {
        const current = pending.shift();
        if (current === undefined || result.has(current)) continue;
        result.add(current); pending.push(...(outgoing.get(current) ?? []));
    }
    return result;
}
function allElementIds(model: MetaModel): Set<string> {
    return new Set([...model.classes.map(item => item.id), ...model.enums.map(item => item.id), ...model.dataTypes.map(item => item.id)]);
}
function unique(values: string[]): string[] { return [...new Set(values)]; }
