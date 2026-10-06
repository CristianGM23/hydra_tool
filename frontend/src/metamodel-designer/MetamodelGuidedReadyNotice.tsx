import { useHydraLanguage } from '../i18n';

interface Props {
    currentStepId: string;
    ready: boolean;
    hasError: boolean;
}

/**
 * Shows the guided save gate only on the fifth/final metamodel step.
 * Earlier steps deliberately have no "ready" banner even when their current
 * draft would already satisfy the structural checks.
 */
export function MetamodelGuidedReadyNotice({ currentStepId, ready, hasError }: Props) {
    const { t } = useHydraLanguage();
    if (currentStepId !== 'save') return null;
    const canSave = ready && !hasError;
    return <div className={`metamodel-guided-ready-actions${canSave ? ' ready' : ' blocked'}`} role="status" aria-live="polite">
        <span>
            <strong>{t(canSave ? 'Metamodel ready to finish' : 'Metamodel needs review before saving')}</strong>
            <small>{t(canSave
                ? 'All previous guided checks are complete and no active error remains. Use Finish to save the metamodel.'
                : 'Resolve the incomplete or invalid fields reported by the previous steps before saving the metamodel.')}</small>
        </span>
    </div>;
}
