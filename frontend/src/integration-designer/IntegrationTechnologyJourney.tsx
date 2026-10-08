import { HydraTooltip } from '../design-system';
import { useHydraLanguage } from '../i18n';
import type { IntegrationSupportLevel, TechnologyDeliveryActionState, TechnologyDeliveryReadiness, TechnologyWorkflowDescriptor, TechnologyWorkflowMaturity } from './types';
import './integration-technology-journey.css';

interface Props { workflow: TechnologyWorkflowDescriptor | null; readiness?: TechnologyDeliveryReadiness | null; compact?: boolean; }

const SUPPORT_LABEL:Record<IntegrationSupportLevel,string>={FULL:'Full',PARTIAL:'Partial',EXTERNAL:'External',UNSUPPORTED:'Not available'};
const MATURITY_LABEL:Record<TechnologyWorkflowMaturity,string>={FULL_WORKFLOW:'Full workflow',OPERATIONAL:'Operational',PARTIAL:'Partial workflow',DECLARED_ONLY:'Declared only',PROJECTION_ONLY:'Projection only'};
const READINESS_LABEL:Record<TechnologyDeliveryActionState,string>={READY:'Ready',READY_WITH_WARNINGS:'Ready with warnings',BLOCKED:'Blocked',EXTERNAL:'External',NOT_APPLICABLE:'Not applicable'};

export function IntegrationTechnologyJourney({workflow,readiness=null,compact=false}:Props){
    const {t}=useHydraLanguage();
    if(workflow===null)return <section className="integration-technology-journey pending"><strong>{t('Technology workflow')}</strong><span>{t('Workflow maturity information is loading.')}</span></section>;
    const byStage=new Map((readiness?.actions??[]).map(item=>[item.stage,item] as const));
    const generationState=byStage.get('GENERATE')?.state;
    const generationReady=generationState==='READY'||generationState==='READY_WITH_WARNINGS';
    return <section className={`integration-technology-journey${compact?' compact':''}`} aria-label={t('Technology workflow maturity')}>
        <header>
            <div><span className="integration-technology-journey-kicker">{t('End-to-end technology workflow')}</span><strong>{t(workflow.displayName)}</strong><p>{t('HYDRA derives this view from the technology catalogue plus the adapters and validators that are actually registered.')}</p></div>
            <div className="integration-technology-journey-score"><strong>{workflow.parityScore}%</strong><span>{t(MATURITY_LABEL[workflow.maturity])}</span></div>
            <HydraTooltip label={t('About workflow maturity')} title={t('How is this maturity calculated?')}>
                <p>{t('The score is based on the complete process: configure, analyze, generate, verify, preview, apply, execute, round-trip and export. Non-applicable phases do not reduce the score.')}</p>
                <p>{t('Declared catalogue support is not enough: generation and verification are only reported as available when HYDRA has the corresponding implementation registered.')}</p>
                <p>{t('When a saved profile is available, HYDRA also evaluates delivery readiness from its resolved inputs and a side-effect-free technology compilation.')}</p>
            </HydraTooltip>
        </header>
        <div className="integration-technology-stage-grid">
            {workflow.stages.map(stage=>{const action=byStage.get(stage.stage);return <article className={`integration-technology-stage support-${stage.support.toLowerCase()}${stage.applicable?'':' not-applicable'}${action?` readiness-${action.state.toLowerCase().replaceAll('_','-')}`:''}`} key={stage.stage}>
                <div><strong>{t(stage.title)}</strong><span>{action?t(READINESS_LABEL[action.state]):stage.applicable?t(SUPPORT_LABEL[stage.support]):t('Not applicable')}</span></div>
                {!compact&&<><p>{t(action?.reason||stage.description)}</p><small><strong>{t('Outcome')}:</strong> {t(stage.outcome)}</small>{action?.evidence.length?<small><strong>{t('Evidence')}:</strong> {action.evidence.map(item=>t(item)).join(' · ')}</small>:null}{stage.prerequisites.length>0&&<ul>{stage.prerequisites.map(item=><li key={item}>{t(item)}</li>)}</ul>}</>}
            </article>})}
        </div>
        {readiness&&<div className="integration-technology-delivery-summary"><strong>{t('Profile delivery readiness')}</strong><span>{generationReady?t('Technology delivery can be generated now.'):readiness.profileValid?t('Technology delivery generation is currently blocked.'):t('Fix profile diagnostics before generation.')}</span><small>{t('Generated files')}: {readiness.generatedFileCount} · {t('Native verification steps')}: {readiness.nativeVerificationStepCount}</small></div>}
        {(readiness?.blockers.length??0)>0&&<aside className="integration-technology-journey-blockers"><strong>{t('Profile delivery blockers')}</strong><ul>{readiness!.blockers.map(item=><li key={item}>{t(item)}</li>)}</ul></aside>}
        {workflow.blockers.length>0&&<aside className="integration-technology-journey-blockers"><strong>{t('Current blockers')}</strong><ul>{workflow.blockers.map(item=><li key={item}>{t(item)}</li>)}</ul></aside>}
        {!compact&&workflow.recommendations.length>0&&<aside className="integration-technology-journey-recommendations"><strong>{t('Recommended next steps')}</strong><ul>{workflow.recommendations.map(item=><li key={item}>{t(item)}</li>)}</ul></aside>}
    </section>;
}
