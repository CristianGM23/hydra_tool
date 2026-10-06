import type { Edge, Node } from '@xyflow/react';
import { Position } from '@xyflow/react';
import { presentEventType } from './eventTypePresentation';
import { DEFAULT_HYDRA_SOURCE_PORT, DEFAULT_HYDRA_TARGET_PORT, normalizeHydraConnectionVisual } from '../designer-ui/HydraConnectionVisual';
import type {
    EventArchitectureDiagramDocument,
    EventArchitectureDiagramNodeKind,
    EventBindingModel,
    EventDefinitionModel,
    EventTypeDefinition
} from './types';

export interface EventArchitectureNodeData extends Record<string, unknown> {
    kind: EventArchitectureDiagramNodeKind;
    eventTypeId: string;
    label: string;
    description: string;
    category: string;
    fieldCount: number;
    enabled: boolean;
}

export interface EventArchitectureEdgeData extends Record<string, unknown> {
    bindingId: string;
    enabled: boolean;
}

export function createEmptyEventArchitectureDiagram(definitions: EventDefinitionModel,
        bindings: EventBindingModel): EventArchitectureDiagramDocument {
    return {
        schemaVersion: '1.1',
        id: `${definitions.metamodelId}-event-architecture-diagram`,
        metamodelId: definitions.metamodelId,
        eventDefinitionModelId: definitions.id,
        eventBindingModelId: bindings.id,
        nodes: [],
        edges: []
    };
}

/**
 * Keeps only explicitly placed event cards. The event catalog is offered from the
 * palette and is not automatically copied to the canvas.
 */
export function synchronizeEventArchitectureDiagram(definitions: EventDefinitionModel, bindings: EventBindingModel,
        reservedTypes: readonly string[], current: EventArchitectureDiagramDocument): EventArchitectureDiagramDocument {
    const validEventIds = architectureEventIds(definitions, reservedTypes);
    const seen = new Set<string>();
    const validNodes = current.nodes.filter(node => validEventIds.has(node.nodeId) && !seen.has(node.nodeId) && seen.add(node.nodeId));
    const nodes = validNodes;
    const bindingById = new Map(bindings.bindings.map(binding => [binding.id, binding]));
    const visibleIds = new Set(nodes.map(node => node.nodeId));
    const retainedEdges = current.edges.filter(edge => {
        const binding = bindingById.get(edge.edgeId);
        return binding !== undefined && visibleIds.has(binding.sourceEventTypeId) && visibleIds.has(binding.targetEventTypeId);
    });
    const retainedEdgeIds = new Set(retainedEdges.map(edge => edge.edgeId));
    const missingEdges = bindings.bindings
        .filter(binding => visibleIds.has(binding.sourceEventTypeId) && visibleIds.has(binding.targetEventTypeId) && !retainedEdgeIds.has(binding.id))
        .map(binding => ({ edgeId: binding.id, points: [], sourceHandle: DEFAULT_HYDRA_SOURCE_PORT, targetHandle: DEFAULT_HYDRA_TARGET_PORT, label: '', note: '', routeStyle: 'ORTHOGONAL' as const, lineStyle: 'SOLID' as const, sourceTerminator: 'NONE' as const, targetTerminator: 'ARROW_CLOSED' as const }));
    return {
        ...current,
        schemaVersion: '1.1',
        id: `${definitions.metamodelId}-event-architecture-diagram`,
        metamodelId: definitions.metamodelId,
        eventDefinitionModelId: definitions.id,
        eventBindingModelId: bindings.id,
        nodes,
        edges: [...retainedEdges, ...missingEdges]
    };
}

export function addEventArchitectureNode(diagram: EventArchitectureDiagramDocument, eventTypeId: string,
        position?: { x: number; y: number }): EventArchitectureDiagramDocument {
    if (diagram.nodes.some(node => node.nodeId === eventTypeId)) return diagram;
    const fallback = nextCanvasPosition(diagram.nodes.length);
    const placement = finiteCanvasPosition(position, fallback);
    return {
        ...diagram,
        nodes: [...diagram.nodes, {
            nodeId: eventTypeId,
            x: placement.x,
            y: placement.y,
            width: 230,
            height: 92,
            collapsed: false
        }]
    };
}

export function removeEventArchitectureNode(diagram: EventArchitectureDiagramDocument, bindings: EventBindingModel,
        eventTypeId: string): EventArchitectureDiagramDocument {
    const bindingIds = new Set(bindings.bindings
        .filter(binding => binding.sourceEventTypeId === eventTypeId || binding.targetEventTypeId === eventTypeId)
        .map(binding => binding.id));
    return {
        ...diagram,
        nodes: diagram.nodes.filter(node => node.nodeId !== eventTypeId),
        edges: diagram.edges.filter(edge => !bindingIds.has(edge.edgeId))
    };
}

export function clearEventArchitectureDiagram(diagram: EventArchitectureDiagramDocument): EventArchitectureDiagramDocument {
    return { ...diagram, nodes: [], edges: [] };
}

export function autoLayoutEventArchitectureDiagram(definitions: EventDefinitionModel, bindings: EventBindingModel,
        reservedTypes: readonly string[], current: EventArchitectureDiagramDocument): EventArchitectureDiagramDocument {
    const synchronized = synchronizeEventArchitectureDiagram(definitions, bindings, reservedTypes, current);
    const customIds = new Set(definitions.eventTypes.map(type => type.id));
    const ordered = [...synchronized.nodes].sort((left, right) => {
        const leftCustom = customIds.has(left.nodeId);
        const rightCustom = customIds.has(right.nodeId);
        if (leftCustom !== rightCustom) return leftCustom ? 1 : -1;
        const leftPresentation = presentEventType(left.nodeId, definitions.eventTypes);
        const rightPresentation = presentEventType(right.nodeId, definitions.eventTypes);
        return leftPresentation.category.localeCompare(rightPresentation.category)
            || leftPresentation.label.localeCompare(rightPresentation.label);
    });
    const nodes = ordered.map((node, index) => {
        const column = index % 4;
        const row = Math.floor(index / 4);
        return { ...node, x: 70 + column * 290, y: 70 + row * 135 };
    });
    return { ...synchronized, nodes };
}

export function toEventArchitectureFlowElements(definitions: EventDefinitionModel, bindings: EventBindingModel,
        reservedTypes: string[], diagram: EventArchitectureDiagramDocument, selectedNodeId: string,
        selectedEdgeId: string): {
            nodes: Node<EventArchitectureNodeData>[];
            edges: Edge<EventArchitectureEdgeData>[];
        } {
    const customById = new Map(definitions.eventTypes.map(type => [type.id, type]));
    const validIds = architectureEventIds(definitions, reservedTypes);
    const views = diagram.nodes.filter(view => validIds.has(view.nodeId));
    const visibleIds = new Set(views.map(view => view.nodeId));
    const nodes: Node<EventArchitectureNodeData>[] = views.map(view => {
        const id = view.nodeId;
        const custom = customById.get(id);
        const presentation = presentEventType(id, definitions.eventTypes);
        return {
            id,
            type: 'eventArchitectureNode',
            position: finiteCanvasPosition({ x: view.x, y: view.y }, nextCanvasPosition(0)),
            // Canonical dimensions stay in style, matching the shared HYDRA
            // resize contract. Viewport framing reads these values directly, so
            // a newly inserted off-screen node can be centered before DOM measurement.
            style: { width: view.width, height: view.height },
            sourcePosition: Position.Right,
            targetPosition: Position.Left,
            selected: selectedNodeId === id,
            data: {
                kind: custom === undefined ? 'RESERVED_EVENT' : 'CUSTOM_EVENT',
                eventTypeId: id,
                label: presentation.label,
                description: presentation.description,
                category: presentation.category,
                fieldCount: custom?.fields?.length ?? 0,
                enabled: custom?.enabled ?? true
            }
        };
    });
    const edgeViews = new Map(diagram.edges.map(view => [view.edgeId, view]));
    const edges: Edge<EventArchitectureEdgeData>[] = bindings.bindings
        .filter(binding => visibleIds.has(binding.sourceEventTypeId) && visibleIds.has(binding.targetEventTypeId))
        .map(binding => {
            const view = edgeViews.get(binding.id);
            const appearance = normalizeHydraConnectionVisual({ ...(view ?? {}), connectionLabel: view?.label, connectionNote: view?.note });
            return {
                id: binding.id,
                source: binding.sourceEventTypeId,
                target: binding.targetEventTypeId,
                label: view?.label || binding.name || 'Event transformation',
                type: 'hydraDirectedEdge',
                sourceHandle: view?.sourceHandle || DEFAULT_HYDRA_SOURCE_PORT,
                targetHandle: view?.targetHandle || DEFAULT_HYDRA_TARGET_PORT,
                zIndex: 8,
                interactionWidth: 30,
                animated: binding.enabled,
                        style: { strokeWidth: 2 },
                selected: selectedEdgeId === binding.id,
                data: { bindingId: binding.id, enabled: binding.enabled, ...appearance }
            };
        });
    return { nodes, edges };
}


/**
 * The palette always exposes the model-element-created event as a safe core
 * fallback, even while the reserved-event catalog is loading or temporarily
 * unavailable. Keep that same fallback in the renderer/synchronizer so every
 * event that can be selected from the palette is renderable on the canvas.
 */
function architectureEventIds(definitions: EventDefinitionModel, reservedTypes: readonly string[]): Set<string> {
    return new Set(['core.model.element-created', ...reservedTypes, ...definitions.eventTypes.map(type => type.id)]);
}

function nextCanvasPosition(index: number): { x: number; y: number } {
    return { x: 70 + (index % 4) * 290, y: 70 + Math.floor(index / 4) * 135 };
}

function finiteCanvasPosition(position: { x: number; y: number } | undefined, fallback: { x: number; y: number }): { x: number; y: number } {
    return position !== undefined && Number.isFinite(position.x) && Number.isFinite(position.y) ? position : fallback;
}

export function cloneEventType(type: EventTypeDefinition, id: string): EventTypeDefinition {
    return {
        ...type,
        id,
        name: `${type.name} copy`,
        fields: type.fields.map(field => ({ ...field, id: `${field.id}-copy` })),
        annotations: { ...type.annotations }
    };
}
