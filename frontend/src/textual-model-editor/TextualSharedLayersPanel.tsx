import { HydraButton } from '../design-system';
import { useHydraLanguage } from '../i18n';
import type { TextualEditorDefinitionModel, TextualSharedLayerOptions } from './types';

interface Props {
    definition: TextualEditorDefinitionModel;
    options: TextualSharedLayerOptions | null;
    loading: boolean;
    error: string;
    onApplyAll: () => void;
    onClear: () => void;
}

const rows: Array<{key:keyof Pick<TextualEditorDefinitionModel,'validationModelIds'|'actionModelIds'|'eventDefinitionIds'|'eventFlowIds'|'gamificationDefinitionIds'>; label:string; help:string}> = [
    {key:'validationModelIds',label:'Validations',help:'Checks the model and shows understandable problems while editing.'},
    {key:'actionModelIds',label:'Quick fixes and actions',help:'Offers safe repairs and model actions without leaving the editor.'},
    {key:'eventDefinitionIds',label:'Events',help:'Connects semantic editing events with the event architecture defined for this model.'},
    {key:'eventFlowIds',label:'Event flows',help:'Runs compatible event flows after confirmed semantic changes, never on each keystroke.'},
    {key:'gamificationDefinitionIds',label:'Gamification',help:'Applies compatible progression, achievements and rewards from verified editor events.'},
];

export function TextualSharedLayersPanel({definition,options,loading,error,onApplyAll,onClear}:Props){
    const {t}=useHydraLanguage();
    const total=options?rows.reduce((sum,row)=>sum+options[row.key].length,0):0;
    return <section className="textual-shared-layers">
        <header><div><strong>{t('Editor capabilities')}</strong><p>{t('HYDRA finds the compatible capabilities for this grammar automatically. You do not need to enter technical identifiers or mappings.')}</p></div>
            <div><HydraButton disabled={loading||!options||total===0} onClick={onApplyAll}>{t('Apply compatible capabilities')}</HydraButton><HydraButton disabled={loading} onClick={onClear}>{t('Use only the basic editor')}</HydraButton></div>
        </header>
        {error&&<div className="textual-shared-layer-warning" role="status">{error}</div>}
        <div className="textual-shared-layer-grid">{rows.map(row=>{
            const available=options?.[row.key].length??0; const selected=definition[row.key].length;
            return <article key={row.key}><div><strong>{t(row.label)}</strong><span>{t(row.help)}</span></div><small>{loading?t('Checking compatibility…'):available===0?t('Not configured for this model'):`${selected}/${available} ${t('applied')}`}</small></article>;
        })}</div>
    </section>;
}
