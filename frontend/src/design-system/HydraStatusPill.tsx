import type { ReactNode } from 'react';

export type HydraStatusTone = 'neutral' | 'info' | 'success' | 'warning' | 'danger';

interface Props {
    children: ReactNode;
    tone?: HydraStatusTone;
    className?: string;
    title?: string;
}

/**
 * Shared semantic status pill. Severity is conveyed by text and shape in addition
 * to colour so the same treatment can be reused by designers, imports and logs.
 */
export function HydraStatusPill({ children, tone = 'neutral', className = '', title }: Props) {
    return <span className={`hydra-status-pill tone-${tone}${className === '' ? '' : ` ${className}`}`} title={title}>{children}</span>;
}
