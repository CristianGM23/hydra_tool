import { HydraButton } from './HydraButton';
import { useMemo } from 'react';
import { explainInputError, isActionableErrorMessage } from '../api/inputErrorExplanation';
import { useHydraLanguage } from '../i18n/useHydraLanguage';

interface Props {
    message: string;
    context?: string;
    className?: string;
    onDismiss?: () => void;
    onRetry?: () => void;
}

interface ErrorSections { what: string; field: string; why: string; impact: string; how: string; checks: string; recovery: string; retryable: boolean; operation: string; resource: string; correlation: string; technical: string; }

/** Consistent, actionable error surface used by every HYDRA workbench. */
export function HydraActionableErrorNotice({ message, context = 'the current form', className = '', onDismiss, onRetry }: Props) {
    const { t } = useHydraLanguage();
    const normalized = useMemo(() => isActionableErrorMessage(message) ? message : explainInputError(new Error(message), message, context), [context, message]);
    const sections = parseSections(normalized);
    const canLocate = sections.field !== '' && typeof document !== 'undefined' && findField(sections.field) !== null;
    const title = t('The operation could not be completed');
    const where = t('Where the problem is');
    const why = t('Why it happened');
    const how = t('How to fix it');
    const checks = t('Useful checks');
    const impact = t('Impact');
    const recovery = t('Recovery');

    return <section className={`hydra-actionable-error${className === '' ? '' : ` ${className}`}`} role="alert" aria-live="assertive" data-hydra-error-context={context}>
        <header><span aria-hidden="true" className="hydra-actionable-error-icon">!</span><div><strong>{title}</strong><p>{sections.what || t(message)}</p></div>{onDismiss !== undefined && <HydraButton aria-label={t('Close error')} className="hydra-window-close" type="button" onClick={onDismiss}>×</HydraButton>}</header>
        <div className="hydra-actionable-error-grid">
            <div><strong>{where}</strong><span>{sections.field !== '' ? sections.field : t(context)}</span>{canLocate && <HydraButton type="button" className="hydra-actionable-error-locate" onClick={() => locateField(sections.field)}>{t('Go to field')}</HydraButton>}</div>
            <div><strong>{why}</strong><span>{sections.why || t('A required condition was not satisfied, so HYDRA stopped the operation before saving incomplete changes.')}</span></div>
            {sections.impact !== '' && <div><strong>{impact}</strong><span>{sections.impact}</span></div>}
            <div className="hydra-actionable-error-solution"><strong>{how}</strong><span>{sections.how || t('Correct the indicated field, element or configuration and run the operation again.')}</span></div>
            {sections.checks !== '' && <div className="hydra-actionable-error-checks"><strong>{checks}</strong><span>{sections.checks}</span></div>}
            {sections.recovery !== '' && <div className="hydra-actionable-error-recovery"><strong>{recovery}</strong><span>{sections.recovery}</span>{sections.retryable && onRetry !== undefined && <HydraButton type="button" variant="primary" onClick={onRetry}>{t('Retry')}</HydraButton>}</div>}
        </div>
        {(sections.correlation !== '' || sections.operation !== '' || sections.resource !== '' || sections.technical !== '') && <details className="hydra-actionable-error-technical"><summary>{t('Technical details')}</summary><dl>{sections.operation !== '' && <div><dt>{t('Operation')}</dt><dd><code>{sections.operation}</code></dd></div>}{sections.resource !== '' && <div><dt>{t('Affected resource')}</dt><dd><code>{sections.resource}</code></dd></div>}{sections.correlation !== '' && <div><dt>{t('Correlation ID')}</dt><dd><code>{sections.correlation}</code></dd></div>}{sections.technical !== '' && <div><dt>{t('Details')}</dt><dd>{sections.technical}</dd></div>}</dl></details>}
    </section>;
}

function parseSections(message: string): ErrorSections {
    const result: ErrorSections = { what: '', field: '', why: '', impact: '', how: '', checks: '', recovery: '', retryable: false, operation: '', resource: '', correlation: '', technical: '' };
    for (const raw of message.split('\n').map(value => value.trim()).filter(Boolean)) {
        const entry = raw.match(/^([^:]+):\s*(.*)$/);
        if (entry === null) { if (result.what === '') result.what = raw; continue; }
        const label = entry[1].toLowerCase(); const value = entry[2].trim();
        if (label === 'what went wrong' || label === 'qué ha ocurrido') result.what = value;
        else if (label === 'field' || label === 'campo') result.field = value;
        else if (label === 'why it is invalid' || label === 'why' || label === 'por qué' || label === 'por qué no es válido') result.why = value;
        else if (label === 'impact' || label === 'impacto') result.impact = value;
        else if (label === 'how to fix it' || label === 'how to correct it' || label === 'cómo solucionarlo' || label === 'cómo corregirlo') result.how = value;
        else if (label === 'possible checks' || label === 'comprobaciones' || label === 'comprobaciones posibles') result.checks = value;
        else if (label === 'recovery' || label === 'recuperación') result.recovery = value;
        else if (label === 'retryable' || label === 'reintentable') result.retryable = value.toLowerCase() === 'yes' || value.toLowerCase() === 'sí';
        else if (label === 'operation' || label === 'operación') result.operation = value;
        else if (label === 'affected resource' || label === 'recurso afectado') result.resource = value;
        else if (label === 'correlation id' || label === 'id de correlación') result.correlation = value;
        else if (label === 'technical details' || label === 'detalles técnicos') result.technical = value;
    }
    return result;
}

function normalize(value: string): string { return value.toLowerCase().replaceAll(/[^a-z0-9áéíóúüñ]/g, ''); }
function findField(field: string): HTMLElement | null {
    if (typeof document === 'undefined') return null;
    const wanted = normalize(field.split('.').at(-1) ?? field);
    const candidates = Array.from(document.querySelectorAll<HTMLElement>('[data-hydra-field-id], input[id], textarea[id], select[id]'));
    return candidates.find(element => {
        const ids = [element.dataset.hydraFieldId ?? '', element.id, element.getAttribute('name') ?? '', element.querySelector('label')?.textContent ?? ''];
        return ids.some(id => { const candidate = normalize(id); return candidate !== '' && (candidate === wanted || candidate.endsWith(wanted) || wanted.endsWith(candidate)); });
    }) ?? null;
}
function locateField(field: string): void {
    const target = findField(field); if (target === null) return;
    const focusable = target.matches('input,textarea,select,button') ? target : target.querySelector<HTMLElement>('input,textarea,select,button');
    target.scrollIntoView({ behavior: 'smooth', block: 'center' });
    target.classList.add('hydra-error-location-flash');
    focusable?.focus({ preventScroll: true });
    window.setTimeout(() => target.classList.remove('hydra-error-location-flash'), 1800);
}
