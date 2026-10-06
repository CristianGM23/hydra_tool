import { HydraButton } from './HydraButton';
import { translateRuntimeUiText } from '../i18n/translationCatalog';
import { useHydraLanguage } from '../i18n/useHydraLanguage';
import { diagnosticAdvice, localizedSeverity, type HydraDiagnosticItem } from './HydraDiagnosticSupport';

interface Props {
    diagnostics: readonly HydraDiagnosticItem[];
    context: string;
    title?: string;
    emptyText?: string;
    onSelectElement?: (elementId: string) => void;
}

/** Shared diagnostic list that always exposes what/where/why/how at user level. */
export function HydraDiagnosticList({ diagnostics, context, title = 'Diagnostics', emptyText = 'No diagnostics available', onSelectElement }: Props) {
    const { language, t } = useHydraLanguage();
    if (diagnostics.length === 0) return <section className="hydra-diagnostic-list empty"><strong>{t(title)}</strong><span>{t(emptyText)}</span></section>;
    return <section className="hydra-diagnostic-list" aria-label={t(title)}>
        <header><strong>{t(title)}</strong><span>{diagnostics.length}</span></header>
        <div className="hydra-diagnostic-items">{diagnostics.map((diagnostic, index) => {
            const advice = diagnosticAdvice(diagnostic, language);
            const element = diagnostic.elementId?.trim() || '';
            const path = diagnostic.path?.trim() || '';
            const location = element || path || t('Current document');
            const severityLabel = localizedSeverity(diagnostic.severity, language);
            return <article className={`hydra-diagnostic-card severity-${diagnostic.severity.toLowerCase()}`} key={`${diagnostic.code}:${diagnostic.elementId ?? ''}:${diagnostic.path ?? ''}:${index}`} data-hydra-diagnostic-element={diagnostic.elementId || undefined}>
                <header><strong>{severityLabel} · {diagnostic.code}</strong>{diagnostic.source && <span>{t(diagnostic.source)}</span>}</header>
                <p data-hydra-runtime-message="true" data-hydra-message-context={context} data-hydra-message-code={diagnostic.code} data-hydra-message-severity={diagnostic.severity}>{translateRuntimeUiText(diagnostic.message, language, context, diagnostic.code, diagnostic.severity)}</p>
                <dl>
                    <div><dt>{t('Where')}</dt><dd><code>{location}</code>{element !== '' && path !== '' && <small>{t('Path')}: {path}</small>}{element !== '' && <small>{t('Element')}: {element}</small>}</dd></div>
                    <div><dt>{t('Why')}</dt><dd>{advice.reason}</dd></div>
                    <div><dt>{t('How to fix it')}</dt><dd>{advice.solution}</dd></div>
                </dl>
                {onSelectElement !== undefined && diagnostic.elementId?.trim() && <HydraButton type="button" onClick={() => onSelectElement(diagnostic.elementId!)}>{t('Select element')}</HydraButton>}
            </article>;
        })}</div>
    </section>;
}
