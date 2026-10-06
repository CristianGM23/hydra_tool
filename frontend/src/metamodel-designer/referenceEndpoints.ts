import type { MetaModel, MetaRelation, MetamodelDiagramDocument, MetamodelDiagramEdge, MetamodelSelection } from './types';

export type ReferenceEndpoint = 'SOURCE' | 'TARGET';
export interface IncomingReferenceDescriptor {
    sourceClassId: string;
    sourceClassName: string;
    referenceId: string;
    referenceName: string;
    targetClassId: string;
}
export interface ReferenceEndpointUpdateSuccess {
    valid: true;
    model: MetaModel;
    diagram: MetamodelDiagramDocument;
    selection: Extract<MetamodelSelection, { kind: 'RELATION' }>;
}
export interface ReferenceEndpointUpdateFailure { valid: false; error: string; }
export type ReferenceEndpointUpdateResult = ReferenceEndpointUpdateSuccess | ReferenceEndpointUpdateFailure;

export function collectIncomingReferences(model: MetaModel, targetClassId: string): IncomingReferenceDescriptor[] {
    return model.relations
        .filter(relation => relation.kind === 'REFERENCE' && relation.targetTypeId === targetClassId)
        .map(relation => ({
            sourceClassId: relation.sourceTypeId,
            sourceClassName: model.classes.find(item => item.id === relation.sourceTypeId)?.name ?? relation.sourceTypeId,
            referenceId: relation.id,
            referenceName: relation.name,
            targetClassId
        }));
}

export function updateReferenceEndpoint(model: MetaModel, diagram: MetamodelDiagramDocument, relationId: string, endpoint: ReferenceEndpoint, nextClassId: string): ReferenceEndpointUpdateResult {
    const main = model.relations.find(item => item.id === relationId);
    if (main === undefined || !isReferenceLike(main)) return { valid: false, error: 'Selected reference was not found' };
    if (!model.classes.some(item => item.id === nextClassId)) return { valid: false, error: 'Selected endpoint class was not found' };
    const nextSource = endpoint === 'SOURCE' ? nextClassId : main.sourceTypeId;
    const nextTarget = endpoint === 'TARGET' ? nextClassId : main.targetTypeId;
    if (nextSource === main.sourceTypeId && nextTarget === main.targetTypeId) return { valid: true, model, diagram, selection: { kind: 'RELATION', id: relationId } };

    const oppositeResult = findLinkedOpposite(model, main);
    if (oppositeResult.valid === false) return oppositeResult;
    const opposite = oppositeResult.opposite;
    const movingIds = new Set([main.id, ...(opposite === null ? [] : [opposite.id])]);
    const mainConflict = findFeatureNameConflict(model, nextSource, main.name, movingIds);
    if (mainConflict !== '') return { valid: false, error: `Source class already contains a feature named ${main.name}: ${mainConflict}` };
    if (opposite !== null) {
        const oppositeConflict = findFeatureNameConflict(model, nextTarget, opposite.name, movingIds);
        if (oppositeConflict !== '') return { valid: false, error: `Target class already contains a feature named ${opposite.name}: ${oppositeConflict}` };
        if (nextSource === nextTarget && main.name === opposite.name) return { valid: false, error: 'Opposite references cannot have the same name when both belong to the same class' };
    }

    const relations = model.relations.map(relation => {
        if (relation.id === main.id) return { ...relation, sourceTypeId: nextSource, targetTypeId: nextTarget, oppositeRelationId: opposite?.id ?? '' };
        if (opposite !== null && relation.id === opposite.id) return { ...relation, sourceTypeId: nextTarget, targetTypeId: nextSource, oppositeRelationId: main.id };
        return relation;
    });
    let nextDiagram = synchronizeDiagramEndpoint(diagram, main, nextSource, nextTarget);
    if (opposite !== null) nextDiagram = synchronizeDiagramEndpoint(nextDiagram, opposite, nextTarget, nextSource, false);
    return { valid: true, model: { ...model, relations }, diagram: nextDiagram, selection: { kind: 'RELATION', id: relationId } };
}

function isReferenceLike(relation: MetaRelation): boolean {
    return relation.kind === 'REFERENCE' || relation.kind === 'CONTAINMENT' || relation.kind === 'AGGREGATION';
}

function findLinkedOpposite(model: MetaModel, main: MetaRelation): { valid: true; opposite: MetaRelation | null } | ReferenceEndpointUpdateFailure {
    if (main.oppositeRelationId !== '') {
        const direct = model.relations.find(item => item.id === main.oppositeRelationId);
        if (direct === undefined) return { valid: false, error: 'The configured opposite reference does not exist' };
        if (direct.id === main.id) return { valid: false, error: 'A reference cannot be its own opposite' };
        return { valid: true, opposite: direct };
    }
    const reverse = model.relations.filter(item => item.oppositeRelationId === main.id);
    if (reverse.length > 1) return { valid: false, error: 'More than one reference points to the selected reference as its opposite' };
    return { valid: true, opposite: reverse[0] ?? null };
}

function findFeatureNameConflict(model: MetaModel, classId: string, name: string, movingIds: Set<string>): string {
    const metaClass = model.classes.find(item => item.id === classId);
    if (metaClass === undefined) return 'class was not found';
    const attribute = metaClass.attributes.find(item => item.name === name);
    if (attribute !== undefined) return `attribute ${attribute.name}`;
    const relation = model.relations.find(item => item.sourceTypeId === classId && item.name === name && !movingIds.has(item.id));
    return relation === undefined ? '' : `relation ${relation.name}`;
}

function synchronizeDiagramEndpoint(diagram: MetamodelDiagramDocument, relation: MetaRelation, sourceClassId: string, targetClassId: string, createWhenMissing: boolean = true): MetamodelDiagramDocument {
    const existing = diagram.edges.find(edge => edge.relationId === relation.id);
    const updated: MetamodelDiagramEdge = {
        ...(existing ?? { relationId: relation.id, sourceHandle: 'right-25-source', targetHandle: 'left-75-target', label: relation.name }),
        sourceClassId,
        targetClassId,
        kind: relation.kind,
        routePoints: [],
        labelPosition: undefined
    };
    if (existing === undefined) return createWhenMissing ? { ...diagram, edges: [...diagram.edges, updated] } : diagram;
    if (existing.sourceClassId === sourceClassId && existing.targetClassId === targetClassId) return diagram;
    return { ...diagram, edges: diagram.edges.map(edge => edge.relationId === relation.id ? updated : edge) };
}
