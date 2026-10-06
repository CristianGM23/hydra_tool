import { useState } from 'react';
import { HydraButton, HydraTooltip, HydraWindowControls } from '../design-system';
import { useHydraLanguage } from '../i18n';
import type { GrammarPlatformConversionPreview } from './types';

interface Props { preview:GrammarPlatformConversionPreview; onCancel:()=>void; onApply:()=>void; }

export function GrammarPlatformConversionDialog({preview,onCancel,onApply}:Props){
    const {t}=useHydraLanguage();
    const [maximized,setMaximized]=useState(false);
    const downloadTrace=()=>{const blob=new Blob([preview.traceMarkdown],{type:'text/markdown;charset=utf-8'});const url=URL.createObjectURL(blob);try{const anchor=document.createElement('a');anchor.href=url;anchor.download=`grammar-conversion-${preview.sourceTechnology.toLowerCase()}-to-${preview.targetTechnology.toLowerCase()}.md`;anchor.click();}finally{URL.revokeObjectURL(url);}};
    return <div className="grammar-conversion-backdrop" role="presentation">
        <section className={`grammar-conversion-dialog hydra-dialog-surface${maximized?' maximized':''}`} role="dialog" aria-modal="true" aria-labelledby="grammar-conversion-title">
            <header className="grammar-conversion-header hydra-window-header"><div><h3 id="grammar-conversion-title">{t('Safe grammar conversion')} · {preview.sourceTechnology} → {preview.targetTechnology}</h3><p>{t(preview.exactSourceRestored?'HYDRA recovered the previous version for this technology and verified that it still represents the same language.':'HYDRA prepared the equivalent grammar for the selected technology and checked it before allowing any change.')}</p></div><HydraWindowControls closeLabel={t('Keep current grammar')} maximizeLabel={t('Maximize conversion dialog')} restoreLabel={t('Restore conversion dialog')} maximized={maximized} onClose={onCancel} onToggleMaximize={()=>setMaximized(value=>!value)}/></header>
            <div className="grammar-conversion-state"><span className={preview.semanticallyEquivalent?'grammar-conversion-ok':'grammar-conversion-blocked'}>{t(preview.semanticallyEquivalent?'Equivalent structure':'Apply blocked')}</span><span>{t(preview.semanticallyEquivalent?'The semantic structure can be preserved in the selected technology.':'Review the indicated differences before changing technology.')}</span></div>
            <div className="grammar-conversion-overview">
                <article><div><strong>{t('Language structure')}</strong><HydraTooltip label={t('Information about language structure')} title={t('Language structure')}><p>{t('HYDRA compares the source and destination as semantic models, not only as grammar text, before enabling the conversion. This check verifies that the inferred concepts and structure still represent the same language even when the target technology uses different syntax.')}</p></HydraTooltip></div><span>{preview.semanticallyEquivalent?t('Preserved'):t('Needs review')}</span></article>
                <article><div><strong>{t('Entry rule')}</strong><HydraTooltip label={t('Information about entry rule')} title={t('Entry rule')}><p>{t('This is the rule used to start reading a model with the grammar. Check that it still points to the intended root concept after conversion, because changing the entry rule can alter which documents the generated parser accepts.')}</p></HydraTooltip></div><span>{preview.sourceEntryRule||'—'} → {preview.targetEntryRule||'—'}</span></article>
                <article><div><strong>{t('Recursive rules')}</strong><HydraTooltip label={t('Information about recursive rules')} title={t('Recursive rules')}><p>{t('HYDRA checks recursive rule paths before the conversion can be applied. Review any reported cycle carefully: legitimate recursion must still make progress, while unsafe cycles can prevent the generated parser from consuming input.')}</p></HydraTooltip></div><span>{preview.recursiveRules.length===0?t('None detected'):`${preview.recursiveRules.length}`}</span></article>
                <article><div><strong>{t('Elements preserved')}</strong><HydraTooltip label={t('Information about preserved elements')} title={t('Elements preserved')}><p>{t('These are the classes, attributes, relationships and other semantic elements that remain available after conversion. Use this count together with the report to confirm that the target technology preserves the concepts your models and HYDRA layers depend on.')}</p></HydraTooltip></div><span>{preview.preservedConcepts.length}</span></article>
            </div>
            <section className="grammar-conversion-preserved"><h4>{t('What will be preserved')}</h4><ul>{preview.preservedConcepts.map(item=><li key={item}>{preservedConceptLabel(item,t)}</li>)}</ul></section>
            {preview.warnings.length>0&&<section className="grammar-conversion-warnings"><h4>{t('What needs review')}</h4><ul>{preview.warnings.map((item,index)=><li key={`${index}-${item}`}>{t(item)}</li>)}</ul></section>}

            <footer className="grammar-conversion-footer">
                <div>
                    <div className="hydra-button-with-tooltip"><HydraButton type="button" onClick={downloadTrace}>{t('Download transformation report')}</HydraButton><HydraTooltip label={t('Information about Download transformation report')} title={t('Download transformation report')}><p>{t('Download a readable trace explaining what HYDRA preserved, compared and transformed during the conversion preview.')}</p></HydraTooltip></div>
                </div>
                <div>
                    <div className="hydra-button-with-tooltip"><HydraButton type="button" onClick={onCancel}>{t('Keep current grammar')}</HydraButton><HydraTooltip label={t('Information about Keep current grammar')} title={t('Keep current grammar')}><p>{t('Close the dialog without changing the current grammar. Your current grammar remains exactly as it is now.')}</p></HydraTooltip></div>
                    <div className="hydra-button-with-tooltip"><HydraButton variant="primary" type="button" disabled={!preview.semanticallyEquivalent} onClick={onApply}>{t('Apply converted grammar')}</HydraButton><HydraTooltip label={t('Information about Apply converted grammar')} title={t('Apply converted grammar')}><p>{t('Replace the current grammar with the converted version only when HYDRA has verified that the same language structure can be preserved.')}</p></HydraTooltip></div>
                </div>
            </footer>
        </section>
    </div>;
}

function preservedConceptLabel(value:string,t:(key:string)=>string):string{const match=value.trim().match(/^(\d+)\s+(classes|enumerations|data types|attributes|relations|operations)$/i);if(!match)return t(value);return `${match[1]} ${t(match[2].toLowerCase())}`;}
