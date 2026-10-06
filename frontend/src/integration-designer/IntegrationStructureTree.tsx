import { DesignerStructureTree } from '../designer-ui/DesignerStructureTree';
import type { IntegrationProfileModel, TechnologyInjectionPreview } from './types';
import { buildIntegrationStructure } from './integrationStructureModel';

import type { IntegrationStructureSelection } from './integrationStructureTypes';
export type { IntegrationStructureSelection } from './integrationStructureTypes';

interface Props {
    model: IntegrationProfileModel;
    preview: TechnologyInjectionPreview | null;
    selection: IntegrationStructureSelection;
    filter: string;
    onSelect: (selection: Exclude<IntegrationStructureSelection, null>) => void;
}

export function IntegrationStructureTree({ model, preview, selection, filter, onSelect }: Props) {
    return <DesignerStructureTree
        ariaLabel="Integration structure tree"
        nodes={buildIntegrationStructure(model, preview)}
        selectedId={selection === null ? '' : `integration:${selection.kind}:${selection.id}`}
        filter={filter}
        defaultExpandedIds={['integration-root', 'integration-artifacts', 'integration-mappings']}
        onSelect={node => { if (node.data !== null && node.data !== undefined) onSelect(node.data); }}
    />;
}
