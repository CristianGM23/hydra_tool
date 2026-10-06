import { useMemo } from 'react';
import { DesignerStructureTree } from '../../designer-ui/DesignerStructureTree';
import type { EventFlowModel } from './types';
import { buildEventFlowStructure } from './eventFlowStructureModel';

import type { EventFlowStructureSelection } from './eventFlowStructureTypes';
export type { EventFlowStructureSelection } from './eventFlowStructureTypes';

interface Props {
    model: EventFlowModel;
    selection: EventFlowStructureSelection;
    filter: string;
    onSelect: (selection: Exclude<EventFlowStructureSelection, null>) => void;
}

export function EventFlowStructureTree({ model, selection, filter, onSelect }: Props) {
    const nodes = useMemo(() => buildEventFlowStructure(model), [model]);
    const defaultExpandedIds = useMemo(() => [
        'event-flow-root',
        ...model.flows.flatMap(flow => [
            `event-flow:FLOW:${flow.id}:${flow.id}`,
            `event-flow-variables:${flow.id}`,
            `event-flow-nodes:${flow.id}`,
            `event-flow-transitions:${flow.id}`,
            `event-flow-compensations:${flow.id}`
        ])
    ], [model.flows]);
    return <DesignerStructureTree
        ariaLabel="Event-flow structure tree"
        nodes={nodes}
        selectedId={selection === null ? '' : `event-flow:${selection.kind}:${selection.flowId}:${selection.id}`}
        filter={filter}
        defaultExpandedIds={defaultExpandedIds}
        onSelect={node => { if (node.data !== null && node.data !== undefined) onSelect(node.data); }}
    />;
}
