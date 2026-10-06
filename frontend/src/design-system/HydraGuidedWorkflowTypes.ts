import type { ReactNode } from 'react';

export type HydraCreationMode = 'GUIDED' | 'ADVANCED';

export interface HydraGuidedCheck {
    id: string;
    label: string;
    complete: boolean;
    detail?: string;
    /**
     * Los checks son bloqueantes por defecto. Solo los recordatorios puramente
     * informativos deben declarar blocking=false.
     */
    blocking?: boolean;
}

export interface HydraGuidedStep {
    id: string;
    title: string;
    description: string;
    checks: readonly HydraGuidedCheck[];
    /** @deprecated Usa checks con blocking=false para información opcional. */
    optional?: boolean;
    content?: ReactNode;
}
