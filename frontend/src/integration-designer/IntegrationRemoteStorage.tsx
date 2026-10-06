import { HydraButton } from '../design-system/HydraButton';
import { useState } from 'react';
import { RemoteJsonDocumentDialog } from '../remote-storage';
import { normalizeIntegrationProfile } from './integrationProfileNormalization';
import type { IntegrationProfileModel } from './types';

interface Props { model: IntegrationProfileModel; metamodelId: string; onImported: (model: IntegrationProfileModel) => void; }

/** Entrada compacta a almacenamiento remoto sin acoplar proveedores al diseñador. */
export function IntegrationRemoteStorage({ model, metamodelId, onImported }: Props) {
    const [open,setOpen]=useState(false);
    function importDocument(value: unknown): void {
        if(value===null||typeof value!=='object'||Array.isArray(value)) throw new Error('Remote integration profile is not a JSON object.');
        const normalized=normalizeIntegrationProfile(value as IntegrationProfileModel);
        if(normalized.metamodelId!==metamodelId) throw new Error('Remote integration profile belongs to another metamodel.');
        onImported(normalized);
    }
    return <><HydraButton type="button" onClick={()=>setOpen(true)}>Repository</HydraButton><RemoteJsonDocumentDialog key={`${model.id}:${open}`} open={open} title="Remote integration profile" description="Save or import this reusable technology-integration profile independently from the complete HYDRA project." defaultFileName={`${model.id || 'integration'}.integration-profile.json`} document={model} onClose={()=>setOpen(false)} onImport={importDocument}/></>;
}
