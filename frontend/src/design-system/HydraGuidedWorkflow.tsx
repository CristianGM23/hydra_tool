import { HydraButton } from './HydraButton';
import { useState } from 'react';
import type { ReactNode } from 'react';
import type { HydraCreationMode, HydraGuidedStep } from './HydraGuidedWorkflowTypes';
export type { HydraCreationMode, HydraGuidedCheck, HydraGuidedStep } from './HydraGuidedWorkflowTypes';
import { firstInvalidConfirmedStep, isGuidedCheckBlocking, isGuidedStepConfirmed, isGuidedStepReady, isGuidedStepUnlocked } from './HydraGuidedWorkflowModel';
import { HydraGuidedStatusIcon, type HydraGuidedVisualState } from './HydraGuidedStatus';
import { useHydraLanguage } from '../i18n/useHydraLanguage';

export interface HydraCreationModeSwitchProps {
    mode: HydraCreationMode;
    onChange: (mode: HydraCreationMode) => void;
    guidedLabel?: string;
    advancedLabel?: string;
    className?: string;
}

/**
 * Selector común entre el asistente simplificado y el editor completo.
 * Ambos modos editan exactamente el mismo documento canónico.
 */
export function HydraCreationModeSwitch({ mode, onChange, guidedLabel = 'Guided', advancedLabel = 'Advanced / manual', className = '' }: HydraCreationModeSwitchProps) {
    const { t } = useHydraLanguage();
    return <div className={`hydra-creation-mode-switch${className === '' ? '' : ` ${className}`}`} aria-label={t('Creation mode')}>
        <HydraButton className={mode === 'GUIDED' ? 'active' : ''} type="button" onClick={() => onChange('GUIDED')}>{t(guidedLabel)}</HydraButton>
        <HydraButton className={mode === 'ADVANCED' ? 'active' : ''} type="button" onClick={() => onChange('ADVANCED')}>{t(advancedLabel)}</HydraButton>
    </div>;
}

export interface HydraGuidedWorkflowProps {
    title: string;
    description: string;
    steps: readonly HydraGuidedStep[];
    currentStepId: string;
    onStepChange: (stepId: string) => void;
    onFinish?: () => boolean | Promise<boolean>;
    finishLabel?: string;
    children?: ReactNode;
    className?: string;
    outcome?: string;
    recommendation?: string;
}

/**
 * Asistente reutilizable de HYDRA. Separa dos conceptos distintos:
 * un paso puede tener datos válidos desde el principio, pero solo
 * pasa a verde cuando el usuario lo ha revisado y confirmado con Siguiente.
 */
export function HydraGuidedWorkflow({ title, description, steps, currentStepId, onStepChange, onFinish, finishLabel = 'Finish', children, className = '', outcome, recommendation }: HydraGuidedWorkflowProps) {
    const { t } = useHydraLanguage();
    const [confirmedStepIds, setConfirmedStepIds] = useState<Set<string>>(() => new Set());
    const [finishing, setFinishing] = useState(false);
    const fallbackIndex = 0;
    const resolvedIndex = steps.findIndex(step => step.id === currentStepId);
    const currentIndex = resolvedIndex < 0 ? fallbackIndex : resolvedIndex;
    const current = steps[currentIndex];
    const currentReady = current === undefined ? false : isGuidedStepReady(current);
    const firstInvalidPrevious = firstInvalidConfirmedStep(steps, currentIndex, confirmedStepIds);
    const previousStepsReady = firstInvalidPrevious === null;
    const canAdvance = currentReady && previousStepsReady;
    const confirmedCount = steps.filter(step => isGuidedStepConfirmed(step, confirmedStepIds)).length;

    if (current === undefined) return null;

    function confirmCurrentAndMove(): void {
        if (!canAdvance) return;
        const nextIndex = currentIndex + 1;
        const nextConfirmed = new Set(confirmedStepIds);
        nextConfirmed.add(current.id);
        setConfirmedStepIds(nextConfirmed);
        if (nextIndex < steps.length) onStepChange(steps[nextIndex].id);
    }

    function moveBack(): void {
        if (currentIndex <= 0) return;
        onStepChange(steps[currentIndex - 1].id);
    }

    async function finish(): Promise<void> {
        if (!canAdvance || onFinish === undefined || finishing) return;
        setFinishing(true);
        try {
            const result = await onFinish();
            // El asistente solo confirma el último paso cuando el consumidor
            // declara éxito de forma explícita. Un callback que falle, se cancele
            // o no pueda demostrar la persistencia nunca deja el flujo en verde.
            if (result === true) setConfirmedStepIds(previous => new Set(previous).add(current.id));
        } finally {
            setFinishing(false);
        }
    }

    function stepVisualState(step: HydraGuidedStep, index: number): HydraGuidedVisualState {
        if (isGuidedStepConfirmed(step, confirmedStepIds)) return 'complete';
        if (index === currentIndex) return isGuidedStepReady(step) ? 'ready' : 'error';
        return 'pending';
    }

    return <section className={`hydra-guided-workflow${className === '' ? '' : ` ${className}`}`}>
        <header className="hydra-guided-workflow-header">
            <div><h4>{t(title)}</h4><p>{t(description)}</p></div>
            <span>{t(`${confirmedCount}/${steps.length} complete`)}</span>
        </header>
        {(outcome !== undefined || recommendation !== undefined) && <div className="hydra-guided-context">
            {outcome !== undefined && <div><span>{t('Expected result')}</span><strong>{t(outcome)}</strong></div>}
            {recommendation !== undefined && <div><span>{t('Recommended path')}</span><strong>{t(recommendation)}</strong></div>}
        </div>}
        <nav className="hydra-guided-stepper" aria-label={`${t(title)} ${t('steps')}`}>
            {steps.map((step, index) => {
                const state = stepVisualState(step, index);
                const unlocked = index <= currentIndex || isGuidedStepUnlocked(steps, index, confirmedStepIds);
                const active = index === currentIndex;
                return <HydraButton
                    aria-current={active ? 'step' : undefined}
                    aria-disabled={!unlocked}
                    className={`${state}${active ? ' active' : ''}${!unlocked ? ' locked' : ''}`}
                    disabled={!unlocked}
                    key={step.id}
                    type="button"
                    onClick={() => onStepChange(step.id)}
                >
                    <HydraGuidedStatusIcon state={state} className="hydra-guided-status-dot" />
                    <span><strong>{index + 1}. {t(step.title)}</strong><small>{t(step.description)}</small></span>
                </HydraButton>;
            })}
        </nav>
        <section className="hydra-guided-current-step">
            <header><div><span>{t(`Step ${currentIndex + 1} of ${steps.length}`)}</span><h4>{t(current.title)}</h4><p>{t(current.description)}</p></div></header>
            <div className="hydra-guided-checklist" aria-live="polite">
                {current.checks.length === 0 ? <p className="hydra-guided-check-empty">{t('This step has no blocking requirements.')}</p> : current.checks.map(check => <div className={check.complete ? 'complete' : isGuidedCheckBlocking(check) ? 'incomplete' : 'optional'} key={check.id}>
                    <HydraGuidedStatusIcon state={check.complete ? 'complete' : isGuidedCheckBlocking(check) ? 'error' : 'pending'} className="hydra-guided-check-dot" ariaLabel={check.complete ? 'Requirement complete' : isGuidedCheckBlocking(check) ? 'Requirement incomplete' : 'Optional requirement'} />
                    <span><strong>{t(check.label)}</strong>{check.detail !== undefined && <small>{t(check.detail)}</small>}</span>
                </div>)}
            </div>
            {current.content !== undefined && <div className="hydra-guided-step-content">{current.content}</div>}
            {children}
        </section>
        <footer className="hydra-guided-workflow-actions">
            <HydraButton disabled={currentIndex === 0} type="button" onClick={moveBack}>{t('Previous')}</HydraButton>
            <span>{!previousStepsReady ? `${t('An earlier step is no longer valid. Fix it before continuing.')}: ${t(firstInvalidPrevious?.title ?? '')}` : currentReady ? t('Step ready. Review it and press Next step to mark it complete.') : t('Complete the red checks before continuing.')}</span>
            {currentIndex < steps.length - 1
                ? <HydraButton className="primary" disabled={!canAdvance} type="button" onClick={confirmCurrentAndMove}>{t('Next step')}</HydraButton>
                : <HydraButton className="primary" aria-busy={finishing} disabled={finishing || !canAdvance || onFinish === undefined} type="button" onClick={() => void finish()}>{t(finishing ? 'Working…' : finishLabel)}</HydraButton>}
        </footer>
    </section>;
}
