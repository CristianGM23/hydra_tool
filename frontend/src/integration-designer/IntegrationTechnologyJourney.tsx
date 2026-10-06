import { HydraTooltip } from '../design-system';
import { useHydraLanguage } from '../i18n';
import type { IntegrationSupportLevel, TechnologyWorkflowDescriptor, TechnologyWorkflowMaturity } from './types';
import './integration-technology-journey.css';

interface Props { workflow: TechnologyWorkflowDescriptor | null; compact?: boolean; }

const SUPPORT_LABEL:Record<IntegrationSupportLevel,string>={FULL:'Full',PARTIAL:'Partial',EXTERNAL:'External',UNSUPPORTED:'Not available'};
const MATURITY_LABEL:Record<TechnologyWorkflowMaturity,string>={FULL_WORKFLOW:'Full workflow',OPERATIONAL:'Operational',PARTIAL:'Partial workflow',DECLARED_ONLY:'Declared only',PROJECTION_ONLY:'Projection only'};

export function IntegrationTechnologyJourney({workflow,compact=false}:Props){
    const {t}=useHydraLanguage();
    if(workflow===null)return <section className="integration-technology-journey pending"><strong>{t('Technology workflow')}</strong><span>{t('Workflow maturity information is loading.')}</span></section>;
    return <section className={`integration-technology-journey${compact?' compact':''}`} aria-label={t('Technology workflow maturity')}>
        <header>
            <div><span className="integration-technology-journey-kicker">{t('End-to-end technology workflow')}</span><strong>{t(workflow.displayName)}</strong><p>{t('HYDRA derives this view from the technology catalogue plus the adapters and validators that are actually registered.')}</p></div>
            <div className="integration-technology-journey-score"><strong>{workflow.parityScore}%</strong><span>{t(MATURITY_LABEL[workflow.maturity])}</span></div>
            <HydraTooltip label={t('About workflow maturity')} title={t('How is this maturity calculated?')}>
                <p>{t('The score is based on the complete process: configure, analyze, generate, verify, preview, apply, execute, round-trip and export. Non-applicable phases do not reduce the score.')}</p>
                <p>{t('Declared catalogue support is not enough: generation and verification are only reported as available when HYDRA has the corresponding implementation registered.')}</p>
            </HydraTooltip>
        </header>
        <div className="integration-technology-stage-grid">
            {workflow.stages.map(stage=><article className={`integration-technology-stage support-${stage.support.toLowerCase()}${stage.applicable?'':' not-applicable'}`} key={stage.stage}>
                <div><strong>{t(stage.title)}</strong><span>{stage.applicable?t(SUPPORT_LABEL[stage.support]):t('Not applicable')}</span></div>
                {!compact&&<><p>{t(stage.description)}</p><small><strong>{t('Outcome')}:</strong> {t(stage.outcome)}</small>{stage.prerequisites.length>0&&<ul>{stage.prerequisites.map(item=><li key={item}>{t(item)}</li>)}</ul>}</>}
            </article>)}
        </div>
        {workflow.blockers.length>0&&<aside className="integration-technology-journey-blockers"><strong>{t('Current blockers')}</strong><ul>{workflow.blockers.map(item=><li key={item}>{t(item)}</li>)}</ul></aside>}
        {!compact&&workflow.recommendations.length>0&&<aside className="integration-technology-journey-recommendations"><strong>{t('Recommended next steps')}</strong><ul>{workflow.recommendations.map(item=><li key={item}>{t(item)}</li>)}</ul></aside>}
    </section>;
}
