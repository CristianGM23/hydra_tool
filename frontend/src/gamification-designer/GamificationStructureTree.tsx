import { useMemo } from 'react';
import { DesignerStructureTree } from '../designer-ui/DesignerStructureTree';
import type { GamificationBindingModel, GamificationDefinitionModel } from './types';
import { buildGamificationStructure } from './gamificationStructureModel';

import type { GamificationStructureSelection } from './gamificationStructureTypes';
export type { GamificationStructureSelection } from './gamificationStructureTypes';

interface Props {
    definitions: GamificationDefinitionModel;
    bindings: GamificationBindingModel;
    selection: GamificationStructureSelection;
    filter: string;
    onSelect: (selection: Exclude<GamificationStructureSelection, null>) => void;
}

export function GamificationStructureTree({ definitions, bindings, selection, filter, onSelect }: Props) {
    const nodes = useMemo(() => buildGamificationStructure(definitions, bindings), [bindings, definitions]);
    const defaultExpandedIds = useMemo(() => [
        'gamification-root', 'gamification-rules', 'gamification-progression', 'gamification-points',
        'gamification-levels', 'gamification-achievements', 'gamification-challenges', 'gamification-outcomes'
    ], []);
    return <DesignerStructureTree
        ariaLabel="Gamification structure tree"
        nodes={nodes}
        selectedId={selection === null ? '' : `gamification:${selection.kind}:${selection.id}`}
        filter={filter}
        defaultExpandedIds={defaultExpandedIds}
        onSelect={node => { if (node.data !== null && node.data !== undefined) onSelect(node.data); }}
    />;
}
