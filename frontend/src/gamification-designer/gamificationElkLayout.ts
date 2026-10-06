import { layoutHydraElkStyle } from '../diagram/elkStyleLayout';
import type { EventTypeDefinition } from '../event-designer/types';
import { toGamificationFlowElements } from './gamificationDiagram';
import type { GamificationBindingModel, GamificationDefinitionModel, GamificationDiagramDocument, GamificationRuntimeSnapshot } from './types';

/** Applies HYDRA's shared ELK-style layered layout to the gamification diagram. */
export function layoutGamificationElk(definitions: GamificationDefinitionModel, bindings: GamificationBindingModel,
        runtime: GamificationRuntimeSnapshot | null, diagram: GamificationDiagramDocument,
        selectedNodeId: string, selectedEdgeId: string, customEventTypes: EventTypeDefinition[]): GamificationDiagramDocument {
    if (diagram.nodes.length === 0) return diagram;
    const visible = new Set(diagram.nodes.map(node => node.nodeId));
    const visual = toGamificationFlowElements(definitions, bindings, runtime, diagram, selectedNodeId, selectedEdgeId, customEventTypes);
    const placements = layoutHydraElkStyle(
        diagram.nodes.map(node => ({ id: node.nodeId, width: node.width ?? 210, height: node.height ?? 84 })),
        visual.edges.filter(edge => visible.has(edge.source) && visible.has(edge.target)).map(edge => ({ id: edge.id, source: edge.source, target: edge.target })),
        { direction: 'RIGHT', nodeGap: 54, layerGap: 115 }
    );
    return { ...diagram, nodes: diagram.nodes.map(node => {
        const placement = placements.get(node.nodeId);
        return placement ? { ...node, x: placement.x + 60, y: placement.y + 60, width: placement.width, height: placement.height } : node;
    }) };
}
