import { HydraButton } from '../design-system/HydraButton';
import { useState } from 'react';
import type { MetaModel } from '../metamodel-designer/types';
import { RemoteJsonDocumentDialog } from '../remote-storage';
import { normalizeEditorDefinition } from './editorDefinitionNormalization';
import type { EditorDefinitionModel } from './types';
import { VisualDefinitionImportButton } from './VisualDefinitionImportDialog';

interface Props { model: EditorDefinitionModel; metamodel: MetaModel; onImported: (model: EditorDefinitionModel) => void; }

/** Entrada compacta a almacenamiento remoto sin hacer crecer el diseñador principal. */
export function EditorDefinitionRemoteStorage({ model, metamodel, onImported }: Props) {
    const [open,setOpen]=useState(false);
    function importDocument(value: unknown): void {
        if(value===null||typeof value!=='object'||Array.isArray(value)) throw new Error('Remote editor definition is not a JSON object.');
        const candidate=value as Partial<EditorDefinitionModel>;
        if(candidate.metamodelId!==metamodel.id) throw new Error('Remote editor definition belongs to another metamodel.');
        if(!Array.isArray(candidate.representations)||candidate.palette===undefined||!Array.isArray(candidate.forms)) throw new Error('Remote editor definition does not contain the required HYDRA sections.');
        onImported(normalizeEditorDefinition(candidate as EditorDefinitionModel,metamodel));
    }
    return <><HydraButton data-hydra-menu-keep-open="true" type="button" onClick={()=>setOpen(true)}>Remote storage</HydraButton><VisualDefinitionImportButton metamodel={metamodel} onImported={onImported}/><RemoteJsonDocumentDialog key={`${model.id}:${open}`} open={open} title="Remote editor definition" description="Save or import this reusable model-editor definition independently from the complete HYDRA project." defaultFileName={`${model.id || 'editor'}.editor-definition.json`} document={model} onClose={()=>setOpen(false)} onImport={importDocument}/></>;
}
