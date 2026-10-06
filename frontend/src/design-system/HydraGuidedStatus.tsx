import { useHydraLanguage } from '../i18n/useHydraLanguage';

export type HydraGuidedVisualState = 'complete' | 'error' | 'ready' | 'pending';

const STATUS_SYMBOL: Record<HydraGuidedVisualState, string> = {
    complete: '✓',
    error: '!',
    ready: '●',
    pending: '○'
};

const STATUS_LABEL: Record<HydraGuidedVisualState, string> = {
    complete: 'Step complete',
    error: 'Step incomplete',
    ready: 'Step ready for confirmation',
    pending: 'Step pending'
};

/** Single status vocabulary for every guided/process surface in HYDRA. */
export function HydraGuidedStatusIcon({ state, className = '', ariaLabel }: { state: HydraGuidedVisualState; className?: string; ariaLabel?: string }) {
    const { t } = useHydraLanguage();
    return <span className={`hydra-guided-status-icon ${state}${className === '' ? '' : ` ${className}`}`} aria-label={t(ariaLabel ?? STATUS_LABEL[state])}>{STATUS_SYMBOL[state]}</span>;
}

