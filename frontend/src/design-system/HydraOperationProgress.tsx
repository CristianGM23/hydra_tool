import { useMemo } from 'react';
import { useHydraLanguage } from '../i18n/useHydraLanguage';

export type HydraOperationStageState = 'PENDING' | 'ACTIVE' | 'COMPLETE';
export interface HydraOperationProgressStage { id: string; label: string; state?: HydraOperationStageState; }
export interface HydraOperationProgressProps {
    active: boolean;
    title: string;
    detail?: string;
    stages?: readonly HydraOperationProgressStage[];
    currentStageId?: string;
    progress?: number;
    completed?: boolean;
    compact?: boolean;
}

/**
 * Shared progress surface for operations whose backend or browser work can take
 * long enough to make the UI look frozen. It never invents time-based progress:
 * callers provide a real percentage or semantic stages. The optional completed
 * flag is set only after the underlying operation has actually finished, so semantic
 * progress can reach 100% without inventing timer-based percentages.
 */
export function HydraOperationProgress({ active, title, detail, stages = [], currentStageId = '', progress, completed = false, compact = false }: HydraOperationProgressProps) {
    const { t } = useHydraLanguage();
    const normalizedProgress = progress === undefined ? undefined : Math.max(0, Math.min(100, Math.round(progress)));
    const resolvedStages = useMemo(() => {
        const current = stages.findIndex(item => item.id === currentStageId);
        return stages.map((stage, index) => {
            if (stage.state !== undefined) return stage;
            const state: HydraOperationStageState = completed ? 'COMPLETE' : current < 0 ? 'PENDING' : index < current ? 'COMPLETE' : index === current ? 'ACTIVE' : 'PENDING';
            return { ...stage, state };
        });
    }, [completed, currentStageId, stages]);
    if (!active) return null;
    const completedCount = resolvedStages.filter(stage => stage.state === 'COMPLETE').length;
    const inferred = normalizedProgress ?? (resolvedStages.length > 0 ? Math.round((completedCount / resolvedStages.length) * 100) : undefined);
    return <section aria-busy="true" aria-live="polite" className={`hydra-operation-progress${compact ? ' compact' : ''}`} role="status">
        <div className="hydra-operation-progress-heading"><span aria-hidden="true" className="hydra-operation-spinner"/><div><strong>{t(title)}</strong>{detail !== undefined && <small>{t(detail)}</small>}</div>{inferred !== undefined && <span>{inferred}%</span>}</div>
        <div aria-label={t(`${title} progress`)} aria-valuemax={100} aria-valuemin={0} aria-valuenow={inferred} className={`hydra-operation-progress-track${inferred === undefined ? ' indeterminate' : ''}`} role="progressbar"><span style={inferred === undefined ? undefined : { width: `${inferred}%` }} /></div>
        {resolvedStages.length > 0 && <ol className="hydra-operation-progress-stages">{resolvedStages.map(stage => <li className={(stage.state ?? 'PENDING').toLowerCase()} key={stage.id}><span aria-hidden="true">{stage.state === 'COMPLETE' ? '✓' : stage.state === 'ACTIVE' ? '●' : '○'}</span><span>{t(stage.label)}</span></li>)}</ol>}
    </section>;
}
