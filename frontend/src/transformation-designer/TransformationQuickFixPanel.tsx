import { useState } from 'react';
import { HydraButton } from '../design-system';
import { useHydraLanguage } from '../i18n';
import { transformationQuickFixes } from './api';
import type { TransformationCheckReport, TransformationDefinitionDocument, TransformationQuickFix } from './types';

export function TransformationQuickFixPanel({ document, report, onApply }:{document:TransformationDefinitionDocument;report:TransformationCheckReport|null;onApply:(next:TransformationDefinitionDocument)=>void}){
    const {t}=useHydraLanguage();const [fixes,setFixes]=useState<TransformationQuickFix[]>([]);const [busy,setBusy]=useState(false);const [error,setError]=useState('');
    async function discover(){setBusy(true);setError('');try{const result=await transformationQuickFixes(document);setFixes(result.fixes);}catch(reason){setError(reason instanceof Error?reason.message:String(reason));}finally{setBusy(false);}}
    if(report===null)return null;
    return <section className="transformation-quickfix-panel"><header><div><strong>{t('Transformation quick fixes')}</strong><span>{t('Review explicit repair proposals without changing the draft automatically.')}</span></div><HydraButton disabled={busy} type="button" onClick={()=>void discover()}>{t('Find quick fixes')}</HydraButton></header>
        {fixes.length===0?<p>{t('No quick fixes have been calculated yet.')}</p>:<div className="transformation-quickfix-list">{fixes.map(fix=><article key={fix.id}><div><strong>{t(fix.title)}</strong><span>{t(fix.description)}</span><small>{fix.diagnosticCode}{fix.safeAutomatic?` · ${t('Safe automatic')}`:` · ${t('Review required')}`}</small></div><HydraButton type="button" onClick={()=>{onApply(fix.proposedTransformation);setFixes([]);}}>{t('Apply proposal')}</HydraButton></article>)}</div>}
        {error!==''&&<div className="transformation-preview-error" role="alert">{t(error)}</div>}
    </section>;
}
