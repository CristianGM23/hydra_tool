import { layoutHydraElkStyle } from '../../diagram/elkStyleLayout';
import { layoutEventFlow } from './eventFlowDesignerSupport';
import type { EventFlowDiagramDocument, FlowDefinition } from './types';

/** Applies the EventFlow auto-layout while preserving the diagram document. */
export function layoutEventFlowAutomatic(flow: FlowDefinition, diagram: EventFlowDiagramDocument): EventFlowDiagramDocument {
    const positions = layoutEventFlow(flow);
    return { ...diagram, nodes: diagram.nodes.map(node => positions.has(node.nodeId) ? { ...node, ...positions.get(node.nodeId)! } : node) };
}

/** Applies HYDRA's shared ELK-style layered layout to one EventFlow. */
export function layoutEventFlowElk(flow: FlowDefinition, diagram: EventFlowDiagramDocument): EventFlowDiagramDocument {
    const visibleIds = new Set(flow.nodes.map(node => node.id));
    const views = new Map(diagram.nodes.map(node => [node.nodeId, node]));
    const placements = layoutHydraElkStyle(
        flow.nodes.map(node => { const view = views.get(node.id); return { id: node.id, width: view?.width ?? 180, height: view?.height ?? 72 }; }),
        flow.transitions.filter(edge => visibleIds.has(edge.sourceNodeId) && visibleIds.has(edge.targetNodeId))
            .map(edge => ({ id: edge.id, source: edge.sourceNodeId, target: edge.targetNodeId })),
        { direction: 'RIGHT', nodeGap: 58, layerGap: 125 }
    );
    return { ...diagram, nodes: diagram.nodes.map(node => {
        const placement = placements.get(node.nodeId);
        return placement ? { ...node, x: placement.x + 60, y: placement.y + 60, width: placement.width, height: placement.height } : node;
    }) };
}
