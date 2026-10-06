import type { HydraGuidedCheck, HydraGuidedStep } from './HydraGuidedWorkflowTypes';

/** Un check incompleto bloquea salvo que esté marcado explícitamente como informativo. */
export function isGuidedCheckBlocking(check: HydraGuidedCheck): boolean {
    return check.blocking !== false;
}

/** Devuelve los requisitos bloqueantes que siguen sin resolver en un paso. */
export function guidedBlockingChecks(step: HydraGuidedStep): readonly HydraGuidedCheck[] {
    return step.checks.filter(check => isGuidedCheckBlocking(check) && !check.complete);
}

/** Returns true when the data represented by the step currently satisfies every blocking check. */
export function isGuidedStepReady(step: HydraGuidedStep): boolean {
    // `optional` ya no permite saltarse errores. Un requisito opcional debe
    // expresarse de forma explícita con blocking=false para no ocultar fallos.
    return guidedBlockingChecks(step).length === 0;
}

/** Alias semántico: un paso está completo cuando todos sus datos bloqueantes están listos. */
export function isGuidedStepComplete(step: HydraGuidedStep): boolean {
    return isGuidedStepReady(step);
}

/** A step is confirmed only after the user explicitly leaves it through Next/Finish. */
export function isGuidedStepConfirmed(step: HydraGuidedStep, confirmedStepIds: ReadonlySet<string>): boolean {
    return confirmedStepIds.has(step.id) && isGuidedStepReady(step);
}

/** Un paso posterior solo se desbloquea tras validar y confirmar explícitamente todos los pasos anteriores. */
export function isGuidedStepUnlocked(steps: readonly HydraGuidedStep[], index: number, confirmedStepIds: ReadonlySet<string> = new Set<string>()): boolean {
    if (index <= 0) return true;
    return steps.slice(0, index).every(step => isGuidedStepConfirmed(step, confirmedStepIds));
}

/** Localiza el primer paso anterior que dejó de ser válido después de haber avanzado. */
export function firstInvalidConfirmedStep(steps: readonly HydraGuidedStep[], currentIndex: number, confirmedStepIds: ReadonlySet<string>): HydraGuidedStep | null {
    return steps.slice(0, Math.max(0, currentIndex)).find(step => !isGuidedStepConfirmed(step, confirmedStepIds)) ?? null;
}
