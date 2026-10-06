import { useState } from 'react';
import { HydraButton, HydraGuidedStatusIcon, HydraTooltip } from '../design-system';
import { useHydraLanguage } from '../i18n';
import { downloadGeneratedGrammarProject } from './api';
import type { GrammarCompilationSnapshot, GrammarProviderDescriptor } from './types';

interface Props {
    snapshot: GrammarCompilationSnapshot | null;
    provider?: GrammarProviderDescriptor;
}

const STAGE_LABELS:Record<string,string>={
    FREEZE_DRAFT:'Freeze current draft',FULL_ANALYSIS:'Full grammar analysis',RESOLVE_IMPORTS:'Imports and references',
    VALIDATE_SEMANTIC_BINDING:'Semantic mapping',RECONCILE_SEMANTIC_CONTRACT:'Inferred model consistency',CHECK_HYDRA_LAYERS:'HYDRA features',
    GENERATE_PROJECT:'Project generation',GENERATE_SEMANTIC_ARTIFACTS:'Model and diagram generation',MATERIALIZE_RUNTIME:'Runtime generation',
    SMOKE_TEST:'Generated infrastructure check',REGISTER_ARTIFACTS:'Generated files registration',PROMOTE_SNAPSHOT:'Activate successful compilation'
};

const STAGE_TOOLTIPS:Record<string,string>={
    FREEZE_DRAFT:'HYDRA fixes one stable version of the current grammar so the rest of the compilation works against a consistent source.',
    FULL_ANALYSIS:'HYDRA reviews the complete grammar structure, not just the line currently being edited.',
    RESOLVE_IMPORTS:'HYDRA checks imported files, references and names that must be available across the whole language.',
    VALIDATE_SEMANTIC_BINDING:'HYDRA verifies how grammar constructs become classes, attributes and relations in the inferred model.',
    RECONCILE_SEMANTIC_CONTRACT:'HYDRA consolidates the inferred model so later steps use one coherent semantic contract.',
    CHECK_HYDRA_LAYERS:'HYDRA verifies validations, quick fixes, events, event flows and gamification linked to this language.',
    GENERATE_PROJECT:'HYDRA creates the generated project structure required by the selected technology.',
    GENERATE_SEMANTIC_ARTIFACTS:'HYDRA produces the inferred model artifacts and the visual class-diagram projection.',
    MATERIALIZE_RUNTIME:'HYDRA prepares runtime services such as parser bridges, workers or language servers.',
    SMOKE_TEST:'HYDRA performs a quick end-to-end check on the generated infrastructure to catch obvious failures early.',
    REGISTER_ARTIFACTS:'HYDRA records the generated outputs so they can be reopened, downloaded and traced later.',
    PROMOTE_SNAPSHOT:'HYDRA activates the successful compiled version only if every mandatory verification has passed.'
};

export function GrammarCompilationPanel({ snapshot, provider }: Props) {
    const { t } = useHydraLanguage();
    const [downloadError, setDownloadError] = useState('');
    if (!snapshot) return <section className="grammar-compilation-panel empty">
        <header className="grammar-compilation-summary"><div><strong>{t('Compilation result')}</strong><span className="grammar-compilation-badge pending">{t('Not compiled yet')}</span></div></header>
        <p>{t('You can compile from the moment the editor opens. HYDRA will check every layer automatically and show exactly where a problem occurs.')}</p>
    </section>;

    const snapshotId = snapshot.id;
    const grammarId = snapshot.grammarId;
    const generatedFiles = Object.keys(snapshot.generatedProject.files ?? {}).length;
    const generatedFeatures = provider?.capabilityDescriptor.generatedHostFeatures ?? [];
    const stages=snapshot.stages??[];
    const failedStage=stages.find(stage=>stage.status==='FAILED'||stage.status==='CANCELLED');
    async function download() {
        try { setDownloadError(''); await downloadGeneratedGrammarProject(snapshotId, grammarId); }
        catch (error) { setDownloadError(error instanceof Error ? error.message : String(error)); }
    }

    return <section className={`grammar-compilation-panel ${snapshot.status.toLowerCase()}`}>
        <header className="grammar-compilation-summary">
            <div><strong>{t('Compilation result')}</strong><span className={`grammar-compilation-badge ${snapshot.status.toLowerCase()}`}>{t(snapshot.status==='SUCCESS'?'Successful':'Failed')}</span></div>
            <p>{snapshot.status==='SUCCESS'?t('The grammar, inferred model and generated infrastructure passed all required checks.'):t('Nothing invalid was activated. Correct the indicated layer and compile again.')}</p>
        </header>
        <div className="grammar-compilation-kpis">
            <span><strong>{snapshot.technology}</strong><small>{t('Technology')}</small></span>
            <span><strong>{generatedFiles}</strong><small>{t('Generated files')}</small></span>
            <span><strong>{snapshot.classDiagram?.nodes?.length??0}</strong><small>{t('Inferred classes')}</small></span>
            <span><strong>{snapshot.classDiagram?.edges?.length??0}</strong><small>{t('Inferred relations')}</small></span>
        </div>
        {failedStage&&<div className="grammar-compilation-failure" role="alert"><strong>{t('Compilation stopped while checking')}: {t(STAGE_LABELS[failedStage.stage]??failedStage.stage)}</strong><span>{t('Open Problems to see the exact cause, affected element and correction.')}</span></div>}
        {stages.length>0&&<div className="grammar-compilation-stages"><strong>{t('Automatic checks performed')}</strong><div className="hydra-guided-stepper grammar-compilation-stage-grid">{stages.map(stage=>{const ok=stage.status==='SUCCESS'||stage.status==='SKIPPED';const visualState=ok?'complete':'error';return <article key={stage.stage} className={`grammar-compilation-stage-card ${stage.status.toLowerCase()} ${visualState}`}>
            <HydraGuidedStatusIcon state={visualState} className="grammar-compilation-stage-icon" ariaLabel={ok?'Step complete':'Step incomplete'}/>
            <div><strong>{t(STAGE_LABELS[stage.stage]??stage.stage)}</strong><small>{t(stage.status==='SUCCESS'?'Verified':stage.status==='SKIPPED'?'Not required':'Needs attention')}</small></div>
            <div className="grammar-compilation-stage-meta"><time>{stage.durationMillis} ms</time><HydraTooltip label={t('Information about compilation stage')} title={t(STAGE_LABELS[stage.stage]??stage.stage)} triggerClassName="grammar-stage-help-trigger" resizable={false}><p>{t(STAGE_TOOLTIPS[stage.stage]??'This card summarizes one automatic compilation verification performed by HYDRA.')}</p><p>{t(ok?'This verification finished correctly.':'Review this stage because it did not complete successfully.')}</p></HydraTooltip></div></article>;})}</div></div>}
        <div className="grammar-generated-services">
            <strong>{t('Infrastructure available after generation')}</strong>
            {generatedFeatures.length>0?<div className="grammar-capability-list">{generatedFeatures.map(feature=><span key={feature}>{t(feature)}</span>)}</div>:<span>{t('No additional generated editor services were declared by this technology.')}</span>}
            {generatedFeatures.length>0&&<small className="grammar-generated-services-note">{t('These capabilities are provided by the generated host when the project is built or deployed. HYDRA does not claim them as live until that host is running.')}</small>}
            {snapshot.status!=='SUCCESS'&&generatedFiles>0&&<span>{t('The project was generated but was not activated because a later verification failed. You can download it to inspect the exact generated files.')}</span>}
            <HydraButton className="grammar-download-project" disabled={generatedFiles===0} type="button" onClick={()=>void download()}><span className="grammar-download-project-icon" aria-hidden="true">↓</span><span>{t(snapshot.status==='SUCCESS'?'Download generated language project':'Download generated project for review')}</span></HydraButton>
            {downloadError!==''&&<small className="grammar-generated-project-error" role="alert">{downloadError}</small>}
        </div>
        <details className="grammar-compilation-technical"><summary>{t('Technical details')}</summary><dl><dt>{t('Compiled grammar version')}</dt><dd>{snapshot.id}</dd><dt>{t('Source hash')}</dt><dd>{snapshot.sourceHash}</dd><dt>{t('Runtime')}</dt><dd>{snapshot.runtimeDescriptor.mode||t('Not available')}</dd></dl></details>
    </section>;
}
