import { layoutHydraElkStyle } from '../diagram/elkStyleLayout';
import { autoLayoutEventArchitectureDiagram } from './eventArchitectureDiagram';
import type { EventArchitectureDiagramDocument, EventBindingModel, EventDefinitionModel } from './types';


/** Applies the Event Architecture module's specialized auto-layout. */
export function eventAutoLayout(definitions: EventDefinitionModel, bindings: EventBindingModel, reservedTypes: readonly string[], diagram: EventArchitectureDiagramDocument): EventArchitectureDiagramDocument {
    return autoLayoutEventArchitectureDiagram(definitions, bindings, reservedTypes, diagram);
}

/** Applies HYDRA's shared ELK-style layered layout to the event architecture diagram. */
export function eventElkLayout(diagram: EventArchitectureDiagramDocument, bindings: EventBindingModel): EventArchitectureDiagramDocument {
    if (diagram.nodes.length === 0) return diagram;
    const visible = new Set(diagram.nodes.map(node => node.nodeId));
    const placements = layoutHydraElkStyle(
        diagram.nodes.map(node => ({ id: node.nodeId, width: node.width ?? 230, height: node.height ?? 92 })),
        bindings.bindings.filter(binding => visible.has(binding.sourceEventTypeId) && visible.has(binding.targetEventTypeId))
            .map(binding => ({ id: binding.id, source: binding.sourceEventTypeId, target: binding.targetEventTypeId })),
        { direction: 'RIGHT', nodeGap: 56, layerGap: 120 }
    );
    return { ...diagram, nodes: diagram.nodes.map(node => {
        const placement = placements.get(node.nodeId);
        return placement ? { ...node, x: placement.x + 60, y: placement.y + 60, width: placement.width, height: placement.height } : node;
    }) };
}
