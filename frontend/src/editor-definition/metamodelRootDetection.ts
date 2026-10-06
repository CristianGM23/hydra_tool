import type { MetaClass, MetaModel, MetaRelation } from '../metamodel-designer/types';

export interface MetamodelRootDetection {
    primaryClassId: string;
    candidateClassIds: string[];
    explicitClassIds: string[];
    reason: 'EXPLICIT_ANNOTATION' | 'NO_INCOMING_CONTAINMENT' | 'CONTAINMENT_OWNER' | 'FIRST_CONCRETE_CLASS' | 'NO_CONCRETE_CLASS';
}

const ROOT_ANNOTATIONS = ['hydra:root', 'root', 'ecore:root', 'sirius:root', 'model:root'];
const ROOT_NAME_HINTS = /(^|[-_:])(root|model|project|workspace|system|application)($|[-_:])/i;

export function detectMetamodelRoots(metamodel: MetaModel): MetamodelRootDetection {
    const concrete = metamodel.classes.filter(item => item.kind === 'CLASS');
    if (concrete.length === 0) return { primaryClassId: '', candidateClassIds: [], explicitClassIds: [], reason: 'NO_CONCRETE_CLASS' };

    const explicit = concrete.filter(item => ROOT_ANNOTATIONS.some(key => truthy(item.annotations[key])));
    if (explicit.length > 0) {
        const ordered = rankCandidates(explicit, metamodel);
        return { primaryClassId: ordered[0].id, candidateClassIds: ordered.map(item => item.id), explicitClassIds: ordered.map(item => item.id), reason: 'EXPLICIT_ANNOTATION' };
    }

    const incomingContainmentTargets = new Set(metamodel.relations.filter(item => item.kind === 'CONTAINMENT').map(item => item.targetTypeId));
    const withoutIncomingContainment = concrete.filter(item => !incomingContainmentTargets.has(item.id));
    if (withoutIncomingContainment.length > 0) {
        const ordered = rankCandidates(withoutIncomingContainment, metamodel);
        return { primaryClassId: ordered[0].id, candidateClassIds: ordered.map(item => item.id), explicitClassIds: [], reason: 'NO_INCOMING_CONTAINMENT' };
    }

    const containmentOwners = concrete.filter(item => compatibleContainmentRelations(metamodel, item.id).length > 0);
    if (containmentOwners.length > 0) {
        const ordered = rankCandidates(containmentOwners, metamodel);
        return { primaryClassId: ordered[0].id, candidateClassIds: ordered.map(item => item.id), explicitClassIds: [], reason: 'CONTAINMENT_OWNER' };
    }

    return { primaryClassId: concrete[0].id, candidateClassIds: concrete.map(item => item.id), explicitClassIds: [], reason: 'FIRST_CONCRETE_CLASS' };
}

export function compatibleContainmentRelations(metamodel: MetaModel, containerClassId: string): MetaRelation[] {
    return metamodel.relations.filter(relation => relation.kind === 'CONTAINMENT' && isSameOrSubtype(metamodel, containerClassId, relation.sourceTypeId));
}

export function rootDetectionDescription(result: MetamodelRootDetection, metamodel: MetaModel): string {
    const name = metamodel.classes.find(item => item.id === result.primaryClassId)?.name ?? 'No root';
    const reason = ({
        EXPLICIT_ANNOTATION: 'marked explicitly as a root',
        NO_INCOMING_CONTAINMENT: 'not contained by another class',
        CONTAINMENT_OWNER: 'owns contained elements',
        FIRST_CONCRETE_CLASS: 'is the first concrete class available',
        NO_CONCRETE_CLASS: 'cannot be detected because there are no concrete classes'
    } as const)[result.reason];
    return `${name} ${reason}.`;
}

function rankCandidates(classes: MetaClass[], metamodel: MetaModel): MetaClass[] {
    return [...classes].sort((left, right) => score(right, metamodel) - score(left, metamodel) || left.name.localeCompare(right.name) || left.id.localeCompare(right.id));
}

function score(metaClass: MetaClass, metamodel: MetaModel): number {
    const containmentCount = compatibleContainmentRelations(metamodel, metaClass.id).length;
    const rootHint = ROOT_NAME_HINTS.test(metaClass.name) || ROOT_NAME_HINTS.test(metaClass.id) ? 5 : 0;
    return containmentCount * 10 + rootHint;
}

export function isSameOrSubtype(metamodel: MetaModel, candidateClassId: string, expectedClassId: string): boolean {
    if (candidateClassId === expectedClassId) return true;
    const visited = new Set<string>();
    const pending = [candidateClassId];
    while (pending.length > 0) {
        const current = pending.shift()!;
        if (!visited.add(current)) continue;
        const parents = metamodel.relations.filter(relation => relation.sourceTypeId === current && (relation.kind === 'INHERITANCE' || relation.kind === 'IMPLEMENTATION')).map(relation => relation.targetTypeId);
        if (parents.includes(expectedClassId)) return true;
        pending.push(...parents);
    }
    return false;
}

function truthy(value: string | undefined): boolean {
    return value !== undefined && ['true', 'yes', '1', 'root'].includes(value.trim().toLowerCase());
}
