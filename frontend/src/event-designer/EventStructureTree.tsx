import { DesignerStructureTree } from '../designer-ui/DesignerStructureTree';
import type { EventBindingModel, EventDefinitionModel } from './types';
import { buildEventStructure } from './eventStructureModel';

import type { EventStructureSelection } from './eventStructureTypes';
export type { EventStructureSelection } from './eventStructureTypes';

interface Props {
    definitions: EventDefinitionModel;
    bindings: EventBindingModel;
    reservedTypes: string[];
    selection: EventStructureSelection;
    filter: string;
    onSelect: (selection: Exclude<EventStructureSelection, null>) => void;
}

export function EventStructureTree({ definitions, bindings, reservedTypes, selection, filter, onSelect }: Props) {
    const nodes = buildEventStructure(definitions, bindings, reservedTypes);
    const selectedId = selection === null ? '' : `${selection.kind.toLowerCase()}:${selection.id}`;
    return <DesignerStructureTree
        ariaLabel="Event designer structure tree"
        nodes={nodes}
        selectedId={selectedId}
        filter={filter}
        defaultExpandedIds={['event-document-root', 'custom-events-root', 'bindings-root', 'system-events-root']}
        onSelect={node => { if (node.data !== null && node.data !== undefined) onSelect(node.data); }}
    />;
}
