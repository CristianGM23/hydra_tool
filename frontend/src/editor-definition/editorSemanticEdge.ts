import type { MetaModel } from '../metamodel-designer/types';
import type { EdgeCreationTool, EdgeMapping, SemanticEdgeDescriptor } from './types';

export function semanticEdgeClassName(metamodel:MetaModel, semantic:SemanticEdgeDescriptor|undefined):string {
    if (semantic === undefined) return '';
    return metamodel.classes.find(item => item.id === semantic.edgeClassId)?.name ?? semantic.edgeClassId;
}

export function edgeMappingDisplayName(metamodel:MetaModel, mapping:Pick<EdgeMapping,'relationId'|'semanticEdge'>):string {
    if (mapping.semanticEdge !== undefined) return semanticEdgeClassName(metamodel, mapping.semanticEdge) || 'Semantic connection';
    return metamodel.relations.find(item => item.id === mapping.relationId)?.name || mapping.relationId || 'Unnamed relationship';
}

export function edgeToolDisplayName(metamodel:MetaModel, tool:Pick<EdgeCreationTool,'relationId'|'semanticEdge'>):string {
    if (tool.semanticEdge !== undefined) return semanticEdgeClassName(metamodel, tool.semanticEdge) || 'Semantic connection';
    return metamodel.relations.find(item => item.id === tool.relationId)?.name || tool.relationId || 'relationship';
}

export function semanticEdgeFeatureSummary(metamodel:MetaModel, semantic:SemanticEdgeDescriptor):string {
    const source = metamodel.relations.find(item => item.id === semantic.sourceRelationId)?.name ?? semantic.sourceRelationId;
    const target = metamodel.relations.find(item => item.id === semantic.targetRelationId)?.name ?? semantic.targetRelationId;
    const containment = semantic.containmentRelationId === '' ? 'not resolved' : metamodel.relations.find(item => item.id === semantic.containmentRelationId)?.name ?? semantic.containmentRelationId;
    return `${source} / ${target} · containment: ${containment}`;
}
