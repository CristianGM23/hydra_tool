import { Position } from '@xyflow/react';
import type { Edge, Node } from '@xyflow/react';
import { presentEventType } from '../event-designer/eventTypePresentation';
import { createHydraConnectionHandles, DEFAULT_HYDRA_SOURCE_PORT, DEFAULT_HYDRA_TARGET_PORT, normalizeHydraConnectionVisual } from '../designer-ui/HydraConnectionVisual';
import type { EventTypeDefinition } from '../event-designer/types';
import type {
    GamificationBindingModel,
    GamificationDefinitionModel,
    GamificationDiagramDocument,
    GamificationDiagramNodeKind,
    GamificationRuntimeSnapshot
} from './types';

export interface GamificationNodeData extends Record<string, unknown> {
    kind: GamificationDiagramNodeKind;
    elementId: string;
    label: string;
    subtitle: string;
    invalid?: boolean;
}

export interface GamificationEdgeData extends Record<string, unknown> {
    relation: string;
    sourceKind: GamificationDiagramNodeKind;
    targetKind: GamificationDiagramNodeKind;
    sourceElementId: string;
    targetElementId: string;
}

export function gamificationNodeId(kind: GamificationDiagramNodeKind, elementId: string): string {
    return `${kind.toLowerCase()}:${elementId}`;
}

export function parseGamificationNodeId(nodeId: string): { kind: GamificationDiagramNodeKind; elementId: string } | null {
    const separator = nodeId.indexOf(':');
    if (separator <= 0) return null;
    const kind = nodeId.slice(0, separator).toUpperCase() as GamificationDiagramNodeKind;
    if (!['EVIDENCE', 'RULE', 'POINT', 'LEVEL', 'ACHIEVEMENT', 'CHALLENGE', 'REWARD', 'LEDGER'].includes(kind)) return null;
    return { kind, elementId: nodeId.slice(separator + 1) };
}

export function createEmptyGamificationDiagram(definitions: GamificationDefinitionModel, bindings: GamificationBindingModel): GamificationDiagramDocument {
    return {
        schemaVersion: '1.0',
        id: `${definitions.metamodelId}-gamification-diagram`,
        metamodelId: definitions.metamodelId,
        definitionModelId: definitions.id,
        bindingModelId: bindings.id,
        nodes: [],
        edges: []
    };
}

function semanticNodes(definitions: GamificationDefinitionModel, bindings: GamificationBindingModel, runtime: GamificationRuntimeSnapshot | null, additionalEvidenceIds: string[] = [], customEventTypes: EventTypeDefinition[] = []): GamificationNodeData[] {
    const evidenceIds = [...new Set([...bindings.rules.map(rule => rule.eventTypeId).filter(Boolean), ...additionalEvidenceIds.filter(Boolean)])];
    const pointById = new Map(definitions.pointTypes.map(item => [item.id, item]));
    return [
        ...evidenceIds.map(eventTypeId => { const event = presentEventType(eventTypeId, customEventTypes); return { kind: 'EVIDENCE' as const, elementId: eventTypeId, label: event.label, subtitle: event.description, invalid: eventTypeId === 'core.quickfix.applied' }; }),
        ...bindings.rules.map(rule => { const point = pointById.get(rule.pointsTypeId); return { kind: 'RULE' as const, elementId: rule.id, label: rule.name, subtitle: point === undefined ? 'Verified evidence rule' : `${rule.effectKind === 'PENALTY' || rule.points < 0 ? 'Penalty' : 'Award'} · ${Math.abs(rule.points)} ${point.name}`, invalid: rule.eventTypeId === 'core.quickfix.applied' }; }),
        ...definitions.pointTypes.map(item => ({ kind: 'POINT' as const, elementId: item.id, label: item.name, subtitle: item.description || 'Named progression scale.' })),
        ...definitions.levels.map(item => { const point = pointById.get(item.pointTypeId); return { kind: 'LEVEL' as const, elementId: item.id, label: item.name, subtitle: `Reached at ${item.minimumPoints} ${point?.name ?? 'points'}` }; }),
        ...definitions.achievements.map(item => ({ kind: 'ACHIEVEMENT' as const, elementId: item.id, label: item.name, subtitle: item.hidden ? 'Hidden achievement' : 'Achievement' })),
        ...definitions.challenges.map(item => ({ kind: 'CHALLENGE' as const, elementId: item.id, label: item.name, subtitle: `Target ${item.targetCount}` })),
        ...definitions.rewards.map(item => ({ kind: 'REWARD' as const, elementId: item.id, label: item.name, subtitle: 'Reward' })),
        ...(runtime === null ? [] : [{ kind: 'LEDGER' as const, elementId: 'runtime-ledger', label: 'Runtime ledger', subtitle: `${runtime.ledger.entries.length} entries · ${Object.keys(runtime.state.actors).length} actors` }])
    ];
}

interface SemanticEdge {
    id: string;
    source: string;
    target: string;
    label: string;
    data: GamificationEdgeData;
}

function semanticEdges(definitions: GamificationDefinitionModel, bindings: GamificationBindingModel, runtime: GamificationRuntimeSnapshot | null): SemanticEdge[] {
    const result: SemanticEdge[] = [];
    for (const rule of bindings.rules) {
        if (rule.eventTypeId) result.push(edge(`evidence-rule:${rule.id}`, 'EVIDENCE', rule.eventTypeId, 'RULE', rule.id, 'triggers'));
        if (rule.pointsTypeId) result.push(edge(`rule-points:${rule.id}`, 'RULE', rule.id, 'POINT', rule.pointsTypeId, `${rule.effectKind === 'PENALTY' || rule.points < 0 ? '−' : '+'}${Math.abs(rule.points)}`));
        if (rule.achievementId) result.push(edge(`rule-achievement:${rule.id}`, 'RULE', rule.id, 'ACHIEVEMENT', rule.achievementId, 'awards'));
        if (rule.challengeId) result.push(edge(`rule-challenge:${rule.id}`, 'RULE', rule.id, 'CHALLENGE', rule.challengeId, 'progresses'));
        if (rule.rewardId) result.push(edge(`rule-reward:${rule.id}`, 'RULE', rule.id, 'REWARD', rule.rewardId, 'grants'));
    }
    for (const level of definitions.levels) {
        if (level.pointTypeId) result.push(edge(`point-level:${level.id}`, 'POINT', level.pointTypeId, 'LEVEL', level.id, `≥ ${level.minimumPoints}`));
        if (level.rewardId) result.push(edge(`level-reward:${level.id}`, 'LEVEL', level.id, 'REWARD', level.rewardId, 'unlocks'));
    }
    for (const item of definitions.achievements) if (item.rewardId) result.push(edge(`achievement-reward:${item.id}`, 'ACHIEVEMENT', item.id, 'REWARD', item.rewardId, 'unlocks'));
    for (const item of definitions.challenges) if (item.rewardId) result.push(edge(`challenge-reward:${item.id}`, 'CHALLENGE', item.id, 'REWARD', item.rewardId, 'unlocks'));
    if (runtime !== null) {
        for (const rule of bindings.rules) result.push(edge(`rule-ledger:${rule.id}`, 'RULE', rule.id, 'LEDGER', 'runtime-ledger', 'records'));
    }
    return result;
}

function edge(id: string, sourceKind: GamificationDiagramNodeKind, sourceElementId: string,
        targetKind: GamificationDiagramNodeKind, targetElementId: string, label: string): SemanticEdge {
    return {
        id,
        source: gamificationNodeId(sourceKind, sourceElementId),
        target: gamificationNodeId(targetKind, targetElementId),
        label,
        data: { relation: label, sourceKind, targetKind, sourceElementId, targetElementId }
    };
}


export function addGamificationEvidenceNode(diagram: GamificationDiagramDocument, eventTypeId: string,
        position?: { x: number; y: number }): GamificationDiagramDocument {
    const nodeId = gamificationNodeId('EVIDENCE', eventTypeId);
    if(diagram.nodes.some(item => item.nodeId === nodeId)) return diagram;
    const evidenceCount = diagram.nodes.filter(item => parseGamificationNodeId(item.nodeId)?.kind === 'EVIDENCE').length;
    return {
        ...diagram,
        nodes: [...diagram.nodes, {
            nodeId,
            x: position?.x ?? 70,
            y: position?.y ?? 70 + evidenceCount * 130,
            width: 210,
            height: 84,
            collapsed: false
        }]
    };
}

export function synchronizeGamificationDiagram(definitions: GamificationDefinitionModel, bindings: GamificationBindingModel,
        runtime: GamificationRuntimeSnapshot | null, current: GamificationDiagramDocument,
        availableEvidenceIds: string[] = [], customEventTypes: EventTypeDefinition[] = []): GamificationDiagramDocument {
    const available = new Set(availableEvidenceIds);
    const manuallyPlacedEvidenceIds = current.nodes.map(item => parseGamificationNodeId(item.nodeId))
        .filter((item): item is { kind: GamificationDiagramNodeKind; elementId: string } => item !== null && item.kind === 'EVIDENCE' && (available.size === 0 || available.has(item.elementId)))
        .map(item => item.elementId);
    const semantic = semanticNodes(definitions, bindings, runtime, manuallyPlacedEvidenceIds, customEventTypes);
    const validNodeIds = new Set(semantic.map(item => gamificationNodeId(item.kind, item.elementId)));
    const existing = new Map(current.nodes.filter(node => validNodeIds.has(node.nodeId)).map(node => [node.nodeId, node]));
    const positioned = defaultPositions(semantic);
    const nodes = semantic.map(item => existing.get(gamificationNodeId(item.kind, item.elementId)) ?? positioned.get(gamificationNodeId(item.kind, item.elementId))!);
    const semanticEdgeValues = semanticEdges(definitions, bindings, runtime);
    const validEdgeIds = new Set(semanticEdgeValues.map(item => item.id));
    const currentEdgeIds = new Set(current.edges.map(item => item.edgeId));
    return {
        ...current,
        id: `${definitions.metamodelId}-gamification-diagram`,
        metamodelId: definitions.metamodelId,
        definitionModelId: definitions.id,
        bindingModelId: bindings.id,
        nodes,
        edges: [
            ...current.edges.filter(item => validEdgeIds.has(item.edgeId)),
            ...semanticEdgeValues.filter(item => !currentEdgeIds.has(item.id)).map(item => ({ edgeId: item.id, points: [], sourceHandle: DEFAULT_HYDRA_SOURCE_PORT, targetHandle: DEFAULT_HYDRA_TARGET_PORT, label: '', note: '', routeStyle: 'ORTHOGONAL' as const, lineStyle: 'SOLID' as const, sourceTerminator: 'NONE' as const, targetTerminator: 'ARROW_CLOSED' as const }))
        ]
    };
}

export function toGamificationFlowElements(definitions: GamificationDefinitionModel, bindings: GamificationBindingModel,
        runtime: GamificationRuntimeSnapshot | null, diagram: GamificationDiagramDocument,
        selectedNodeId: string, selectedEdgeId: string, customEventTypes: EventTypeDefinition[] = []): { nodes: Node<GamificationNodeData>[]; edges: Edge<GamificationEdgeData>[] } {
    const diagramEvidenceIds = diagram.nodes.map(item => parseGamificationNodeId(item.nodeId)).filter((item): item is { kind: GamificationDiagramNodeKind; elementId: string } => item !== null && item.kind === 'EVIDENCE').map(item => item.elementId);
    const semantic = semanticNodes(definitions, bindings, runtime, diagramEvidenceIds, customEventTypes);
    const views = new Map(diagram.nodes.map(item => [item.nodeId, item]));
    const nodes: Node<GamificationNodeData>[] = semantic.map(item => {
        const id = gamificationNodeId(item.kind, item.elementId);
        const view = views.get(id);
        return {
            id,
            type: 'gamificationNode',
            position: { x: view?.x ?? 80, y: view?.y ?? 80 },
            // Match Model Editor and avoid a competing controlled width/height
            // while the shared NodeResizer is producing dimension changes.
            style: { width: view?.width ?? 210, height: view?.height ?? 84 },
            handles: createHydraConnectionHandles({ width: view?.width ?? 210, height: view?.height ?? 84 }),
            sourcePosition: Position.Right,
            targetPosition: Position.Left,
            data: item,
            selected: id === selectedNodeId,
            className: `gamification-visual-node kind-${item.kind.toLowerCase()}${item.invalid ? ' invalid' : ''}`
        };
    });
    const edgeViews = new Map(diagram.edges.map(edge => [edge.edgeId, edge]));
    const edges: Edge<GamificationEdgeData>[] = semanticEdges(definitions, bindings, runtime).map(item => {
        const edgeView = edgeViews.get(item.id);
        const appearance = normalizeHydraConnectionVisual({ ...(edgeView ?? {}), connectionLabel: edgeView?.label, connectionNote: edgeView?.note });
        return {
        id: item.id,
        source: item.source,
        target: item.target,
        label: edgeView?.label || item.label,
        data: { ...item.data, ...appearance },
        type: 'hydraDirectedEdge',
        sourceHandle: edgeView?.sourceHandle || DEFAULT_HYDRA_SOURCE_PORT,
        targetHandle: edgeView?.targetHandle || DEFAULT_HYDRA_TARGET_PORT,
        zIndex: 8,
        interactionWidth: 30,
        selected: item.id === selectedEdgeId,
        animated: item.data.targetKind === 'LEDGER',
        style: { strokeWidth: 2.2 }
    };
    });
    return { nodes, edges };
}

export function autoLayoutGamificationDiagram(definitions: GamificationDefinitionModel, bindings: GamificationBindingModel,
        runtime: GamificationRuntimeSnapshot | null, current: GamificationDiagramDocument, customEventTypes: EventTypeDefinition[] = []): GamificationDiagramDocument {
    const diagramEvidenceIds = current.nodes.map(item => parseGamificationNodeId(item.nodeId)).filter((item): item is { kind: GamificationDiagramNodeKind; elementId: string } => item !== null && item.kind === 'EVIDENCE').map(item => item.elementId);
    const semantic = semanticNodes(definitions, bindings, runtime, diagramEvidenceIds, customEventTypes);
    const positions = defaultPositions(semantic);
    return { ...current, nodes: semantic.map(item => positions.get(gamificationNodeId(item.kind, item.elementId))!) };
}

function defaultPositions(nodes: GamificationNodeData[]): Map<string, GamificationDiagramDocument['nodes'][number]> {
    const columns: GamificationDiagramNodeKind[][] = [
        ['EVIDENCE'], ['RULE'], ['POINT'], ['LEVEL', 'ACHIEVEMENT', 'CHALLENGE'], ['REWARD'], ['LEDGER']
    ];
    const result = new Map<string, GamificationDiagramDocument['nodes'][number]>();
    columns.forEach((kinds, columnIndex) => {
        const columnNodes = nodes.filter(node => kinds.includes(node.kind));
        columnNodes.forEach((node, rowIndex) => {
            const id = gamificationNodeId(node.kind, node.elementId);
            result.set(id, { nodeId: id, x: 70 + columnIndex * 280, y: 70 + rowIndex * 130, width: 210, height: 84, collapsed: false });
        });
    });
    return result;
}
