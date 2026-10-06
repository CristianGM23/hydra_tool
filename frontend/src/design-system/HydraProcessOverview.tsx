import type { ReactNode } from 'react';
import { useHydraLanguage } from '../i18n/useHydraLanguage';

export interface HydraProcessStep {
    title: string;
    description: string;
    status?: 'READY' | 'CURRENT' | 'COMPLETE' | 'BLOCKED';
}

interface HydraProcessOverviewProps {
    eyebrow?: string;
    title: string;
    summary: string;
    outcome: string;
    recommendation?: string;
    /** Retained for source compatibility. Step-by-step progress belongs to HydraGuidedWorkflow. */
    steps?: HydraProcessStep[];
    aside?: ReactNode;
}

/**
 * Compact orientation card for Full workspace. It deliberately does not render
 * progress or step cards: Guided mode is the single owner of step-by-step flow.
 */
export function HydraProcessOverview({ eyebrow = 'At a glance', title, summary, outcome, recommendation, aside }: HydraProcessOverviewProps) {
    const { t } = useHydraLanguage();
    return <section className="hydra-process-overview hydra-process-overview-compact" aria-label={t(title)}>
        <header className="hydra-process-overview-heading">
            <div className="hydra-process-overview-copy">
                <span className="hydra-process-eyebrow">{t(eyebrow)}</span>
                <h2>{t(title)}</h2>
                <p>{t(summary)}</p>
            </div>
            <div className="hydra-process-outcome">
                <span>{t('Expected result')}</span>
                <strong>{t(outcome)}</strong>
            </div>
        </header>
        {(recommendation !== undefined || aside !== undefined) && <footer className="hydra-process-overview-footer">
            {recommendation !== undefined && <div className="hydra-process-recommendation"><span aria-hidden="true">✦</span><div><strong>{t('Recommended path')}</strong><p>{t(recommendation)}</p></div></div>}
            {aside}
        </footer>}
    </section>;
}
