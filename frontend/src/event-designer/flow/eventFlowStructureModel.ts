import type { DesignerStructureNode } from '../../designer-ui/DesignerStructureTree';
import { FLOW_NODE_PRESENTATIONS, FLOW_TRANSITION_PRESENTATIONS } from './flowPresentation';
import type { EventFlowModel } from './types';
import type { EventFlowStructureSelection } from './eventFlowStructureTypes';

export function buildEventFlowStructure(model: EventFlowModel): DesignerStructureNode<EventFlowStructureSelection>[] {
    return [{
        id: 'event-flow-root',
        label: 'Event flows',
        detail: model.id,
        badge: 'FLOW MODEL',
        children: model.flows.map(flow => ({
            id: `event-flow:FLOW:${flow.id}:${flow.id}`,
            label: flow.name || 'Unnamed flow',
            detail: flow.id,
            badge: flow.enabled ? 'FLOW' : 'DISABLED',
            data: { kind: 'FLOW' as const, flowId: flow.id, id: flow.id },
            children: [
                {
                    id: `event-flow-variables:${flow.id}`,
                    label: 'Variables',
                    badge: String(flow.variables.length),
                    data: { kind: 'FLOW' as const, flowId: flow.id, id: flow.id },
                    children: flow.variables.map(variable => ({
                        id: `event-flow-variable:${flow.id}:${variable.name}`,
                        label: variable.name,
                        detail: variable.type,
                        badge: 'VARIABLE',
                        data: { kind: 'FLOW' as const, flowId: flow.id, id: flow.id }
                    }))
                },
                {
                    id: `event-flow-nodes:${flow.id}`,
                    label: 'Nodes',
                    badge: String(flow.nodes.length),
                    children: flow.nodes.map(node => ({
                        id: `event-flow:NODE:${flow.id}:${node.id}`,
                        label: node.name || FLOW_NODE_PRESENTATIONS[node.kind].label,
                        detail: node.id,
                        badge: FLOW_NODE_PRESENTATIONS[node.kind].label,
                        data: { kind: 'NODE' as const, flowId: flow.id, id: node.id }
                    }))
                },
                {
                    id: `event-flow-transitions:${flow.id}`,
                    label: 'Transitions',
                    badge: String(flow.transitions.length),
                    children: flow.transitions.map(transition => ({
                        id: `event-flow:TRANSITION:${flow.id}:${transition.id}`,
                        label: transition.label || FLOW_TRANSITION_PRESENTATIONS[transition.kind].label,
                        detail: `${transition.sourceNodeId} → ${transition.targetNodeId}`,
                        badge: FLOW_TRANSITION_PRESENTATIONS[transition.kind].label,
                        data: { kind: 'TRANSITION' as const, flowId: flow.id, id: transition.id }
                    }))
                },
                {
                    id: `event-flow-compensations:${flow.id}`,
                    label: 'Compensations',
                    badge: String(flow.compensations.length),
                    data: { kind: 'FLOW' as const, flowId: flow.id, id: flow.id },
                    children: flow.compensations.map((link, index) => ({
                        id: `event-flow-compensation:${flow.id}:${index}`,
                        label: link.compensationNodeId || `Compensation ${index + 1}`,
                        detail: `${link.sourceNodeId} → ${link.compensationNodeId}`,
                        badge: 'COMPENSATION',
                        data: { kind: 'FLOW' as const, flowId: flow.id, id: flow.id }
                    }))
                }
            ]
        }))
    }];
}
