import { HydraFormField, HydraSelect } from '../design-system';
import { useHydraLanguage } from '../i18n';
import type { GrammarSummary } from '../grammar-designer/types';
import type { TransformationDefinitionDocument, TransformationSummary } from './types';

export function TransformationTextToModelWorkspace({document,grammars,transformations,onChange}:{document:TransformationDefinitionDocument;grammars:GrammarSummary[];transformations:TransformationSummary[];onChange:(document:TransformationDefinitionDocument)=>void}){
    const {t}=useHydraLanguage();const config=document.textToModel??{grammarId:'',relativePath:'model.hydra',postTransformationId:''};
    const m2m=transformations.filter(item=>item.kind==='M2M'&&item.id!==document.id);
    const patch=(next:Partial<typeof config>)=>onChange({...document,textToModel:{...config,...next}});
    return <section className="transformation-t2m-workspace"><header><div><strong>{t('Text-to-model pipeline')}</strong><span>{t('Parse text with an active grammar snapshot and optionally project the parsed model through an existing M2M transformation.')}</span></div></header><div className="transformation-t2m-grid">
        <HydraFormField label="Grammar" targetId="transformation-t2m-grammar" required help="The grammar must have a valid active compilation snapshot." width="wide"><HydraSelect ariaLabel="Grammar" value={config.grammarId} options={[{value:'',label:t('Select grammar'),description:t('Choose the parser used by this T2M transformation.')},...grammars.map(item=>({value:item.id,label:`${item.name} · ${item.technology}`,description:item.id}))]} onChange={grammarId=>patch({grammarId})}/></HydraFormField>
        <HydraFormField label="Logical source path" targetId="transformation-t2m-path" help="Logical filename passed to the grammar runtime for language and extension resolution." width="wide"><input id="transformation-t2m-path" value={config.relativePath} onChange={event=>patch({relativePath:event.target.value})}/></HydraFormField>
        <HydraFormField label="Post-processing M2M" targetId="transformation-t2m-post" help="Optional canonical M2M transformation applied after parsing. Leave empty when the grammar already produces the desired domain model." width="full"><HydraSelect ariaLabel="Post-processing M2M" value={config.postTransformationId} options={[{value:'',label:t('No post-processing'),description:t('Use the parsed semantic model directly.')},...m2m.map(item=>({value:item.id,label:item.name,description:`${item.sourceMetamodelId} → ${item.targetMetamodelId}`}))]} onChange={postTransformationId=>patch({postTransformationId})}/></HydraFormField>
    </div><div className="transformation-scope-note"><strong>{t('Grammar-first T2M')}</strong><span>{t('Parsing stays owned by the grammar layer. This transformation only configures the parser gateway and optional semantic projection.')}</span></div></section>;
}
