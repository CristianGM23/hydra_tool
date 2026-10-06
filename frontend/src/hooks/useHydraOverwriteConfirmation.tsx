import { useCallback } from 'react';
import { useHydraLanguage } from '../i18n';
import { useHydraDialogs } from './useHydraDialogs';
import {
    findHydraOverwriteConflicts,
    type HydraOverwriteConflict,
    type HydraOverwriteMatchKind,
    type HydraPersistedIdentity
} from './hydraOverwritePolicy';

export interface HydraOverwriteConfirmationOptions {
    current: readonly HydraPersistedIdentity[];
    persisted: readonly HydraPersistedIdentity[];
    /** Las validaciones activan esta comparación adicional para detectar condiciones equivalentes. */
    includeSemanticSignature?: boolean;
}

function matchReason(kind: HydraOverwriteMatchKind, t: (value: string) => string): string {
    if (kind === 'STABLE_ID') return t('same stable ID');
    if (kind === 'UNIQUE_NAME') return t('same unique name');
    return t('same validation condition');
}

function conflictDescription(conflicts: readonly HydraOverwriteConflict[], t: (value: string) => string): string {
    const first = conflicts[0];
    if (first === undefined) return '';
    const label = first.persisted.displayLabel?.trim()
        || first.persisted.uniqueName?.trim()
        || first.persisted.stableId.trim();
    const reasons = [...new Set(first.matchedBy.map(kind => matchReason(kind, t)))].join(', ');
    const more = conflicts.length > 1 ? ` ${t('Additional conflicts')}: ${conflicts.length - 1}.` : '';
    return `${t('HYDRA found an existing persisted element that conflicts with the element being saved. Confirm only if you want to replace that existing element.')} ${t('Existing element')}: ${label}. ${t('Match')}: ${reasons}.${more}`;
}

/**
 * Confirmación de sobrescritura basada en elementos persistidos reales, no en el ETag del
 * documento contenedor. Esto evita falsos positivos en documentos compuestos (validaciones,
 * eventos, EventFlow o gamificación) y permite detectar colisiones por ID/nombre.
 */
export function useHydraOverwriteConfirmation({ current, persisted, includeSemanticSignature = false }: HydraOverwriteConfirmationOptions) {
    const { t } = useHydraLanguage();
    const { requestDecision, dialogs } = useHydraDialogs();

    const confirmOverwrite = useCallback(async (): Promise<boolean> => {
        const conflicts = findHydraOverwriteConflicts(current, persisted, includeSemanticSignature);
        if (conflicts.length === 0) return true;
        return requestDecision({
            title: t('Overwrite the existing saved element?'),
            description: conflictDescription(conflicts, t),
            confirmLabel: t('Overwrite saved element'),
            cancelLabel: t('Cancel'),
            danger: true
        });
    }, [current, includeSemanticSignature, persisted, requestDecision, t]);

    return { confirmOverwrite, overwriteDialogs: dialogs };
}
