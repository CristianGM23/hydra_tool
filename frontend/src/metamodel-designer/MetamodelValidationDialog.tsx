import { useMemo, useState } from 'react';
import { HydraButton, HydraModal } from '../design-system';
import type { MetamodelValidationAnalysis } from './types';
import { useHydraLanguage } from '../i18n';
import './metamodel-validation-dialog.css';

interface Props {
    analysis: MetamodelValidationAnalysis;
    persisted: boolean;
    onClose: () => void;
    onValidateStored?: () => Promise<void>;
    busy?: boolean;
}

/**
 * Presents the exhaustive metamodel validation report on an opaque shared modal.
 * The dialog intentionally delegates backdrop, focus handling and window controls
 * to HydraModal so it cannot drift from the rest of HYDRA's window behaviour.
 */
export function MetamodelValidationDialog({ analysis, persisted, onClose, onValidateStored, busy = false }: Props) {
    const { t } = useHydraLanguage();
    const [filter, setFilter] = useState<'ALL' | 'ERROR' | 'WARNING' | 'INFO'>('ALL');
    const diagnostics = useMemo(
        () => analysis.diagnostics.filter(item => filter === 'ALL' || item.severity === filter),
        [analysis.diagnostics, filter]
    );
    const stateTitle = analysis.valid ? 'Metamodel is structurally valid' : 'Metamodel has blocking problems';
    const stateDescription = analysis.generationReady
        ? 'Complete project generation is enabled.'
        : persisted
            ? 'Complete project generation remains blocked until the saved metamodel passes validation.'
            : 'This is a draft analysis. Save and validate the metamodel before generating complete projects.';

    const footer = <>
        <span className="metamodel-validation-fingerprint">Fingerprint: <code>{analysis.fingerprint.slice(0, 16)}…</code></span>
        {persisted && onValidateStored && <HydraButton disabled={busy} variant="primary" onClick={() => void onValidateStored()}>
            {t(busy ? 'Validating…' : 'Validate stored metamodel again')}
        </HydraButton>}
        <HydraButton onClick={onClose}>{t('Close')}</HydraButton>
    </>;

    return <HydraModal
        className="metamodel-validation-dialog"
        description={analysis.metamodelName || analysis.metamodelId}
        footer={footer}
        onClose={onClose}
        open
        size="wide"
        title={t('Metamodel validation')}
    >
        <section aria-live="polite" className={`metamodel-validation-state ${analysis.valid ? 'valid' : 'invalid'}`}>
            <div className="metamodel-validation-state-icon" aria-hidden="true">{analysis.valid ? '✓' : '!'}</div>
            <div><strong>{t(stateTitle)}</strong><span>{t(stateDescription)}</span></div>
        </section>

        <section aria-label="Validation summary" className="metamodel-validation-summary">
            <article className="error"><strong>{analysis.summary.errors}</strong><span>{t('Errors')}</span></article>
            <article className="warning"><strong>{analysis.summary.warnings}</strong><span>{t('Warnings')}</span></article>
            <article className="total"><strong>{analysis.summary.total}</strong><span>{t('Total findings')}</span></article>
        </section>

        <nav aria-label="Validation finding filter" className="metamodel-validation-filters">
            {(['ALL', 'ERROR', 'WARNING', 'INFO'] as const).map(value => <HydraButton
                key={value}
                size="small"
                variant={filter === value ? 'primary' : 'quiet'}
                onClick={() => setFilter(value)}
            >{t(value)}</HydraButton>)}
        </nav>

        <section aria-label="Validation findings" className="metamodel-validation-findings">
            {diagnostics.length === 0
                ? <div className="metamodel-validation-empty"><strong>{t('No findings in this category.')}</strong><span>{t('The current filter has no diagnostics to show.')}</span></div>
                : diagnostics.map((item, index) => <article className={`metamodel-validation-finding ${item.severity.toLowerCase()}`} key={`${item.code}:${item.path}:${index}`}>
                    <header><span>{t(item.severity)}</span><div><strong>{t(item.title)}</strong><small>{t(item.category)} · {item.code}</small></div></header>
                    <dl>
                        <div><dt>{t('What happened')}</dt><dd>{t(item.whatHappened)}</dd></div>
                        <div><dt>{t('Where')}</dt><dd>{t(item.location)}</dd></div>
                        <div><dt>{t('Situation')}</dt><dd>{t(item.situation)}</dd></div>
                        <div><dt>{t('Elements involved')}</dt><dd>{item.involvedElements.length > 0 ? <span className="metamodel-validation-elements">{item.involvedElements.map(element => <code key={element}>{element}</code>)}</span> : t('The metamodel/document as a whole')}</dd></div>
                        <div><dt>{t('How to fix it')}</dt><dd><ul>{item.howToFix.map(fix => <li key={fix}>{t(fix)}</li>)}</ul></dd></div>
                        <div><dt>{t('Impact on generation')}</dt><dd>{t(item.exportImpact)}</dd></div>
                    </dl>
                </article>)}
        </section>
    </HydraModal>;
}
