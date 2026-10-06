import { DesignerStructureTree } from './DesignerStructureTree';
import type { MetaModel } from '../metamodel-designer/types';
import { buildMetamodelStructure, semanticNodeId, semanticName, friendlyKind } from './metamodelStructureModel';

import type { MetamodelStructureSelection } from './metamodelStructureTypes';
export type { MetamodelStructureSelection } from './metamodelStructureTypes';

interface Props {
    metamodel: MetaModel | null;
    filter: string;
    selection: MetamodelStructureSelection | null;
    onSelect: (selection: MetamodelStructureSelection) => void;
}

export function MetamodelStructureTree({ metamodel, filter, selection, onSelect }: Props) {
    if (metamodel === null) {
        return <div className="designer-structure-empty" aria-label="Metamodel structure unavailable"><strong>Metamodel unavailable</strong><span>Save or load the metamodel to inspect its semantic structure.</span></div>;
    }
    return <DesignerStructureTree
        ariaLabel="Semantic metamodel structure tree"
        nodes={buildMetamodelStructure(metamodel)}
        selectedId={selection === null ? '' : semanticNodeId(selection)}
        filter={filter}
        defaultExpandedIds={['semantic-model-root', 'semantic-classes-root', 'semantic-relations-root']}
        onSelect={node => { if (node.data !== undefined) onSelect(node.data); }}
    />;
}

export function MetamodelSelectionProperties({ selection, metamodel }: { selection: MetamodelStructureSelection; metamodel: MetaModel }) {
    const relations = selection.kind === 'CLASS'
        ? metamodel.relations.filter(relation => relation.sourceTypeId === selection.id || relation.targetTypeId === selection.id)
        : [];
    return <section className="designer-semantic-properties">
        <header><div><h3>{selection.label}</h3><p>{friendlyKind(selection.kind)}</p></div><span>{selection.badge}</span></header>
        <dl>
            <dt>Identifier</dt><dd>{selection.id}</dd>
            {selection.ownerId !== undefined && <><dt>Owner</dt><dd>{semanticName(metamodel, selection.ownerId)}</dd></>}
            {selection.detail !== undefined && selection.detail !== '' && <><dt>Definition</dt><dd>{selection.detail}</dd></>}
            {selection.kind === 'CLASS' && <><dt>Connected relations</dt><dd>{relations.length}</dd></>}
        </dl>
        <p className="designer-semantic-properties-note">This semantic element is read-only here. Edit it in Metamodel designer; use the definition tree to configure events or gamification.</p>
    </section>;
}
