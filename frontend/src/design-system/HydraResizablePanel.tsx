import type { ReactNode } from 'react';
import { useHydraResizablePanel, type HydraResizablePanelOptions } from './hydraResizablePanelState';

export interface HydraResizablePanelProps extends HydraResizablePanelOptions {
    children: ReactNode;
    className?: string;
    side?: 'left' | 'right';
}

/** Panel común redimensionable con separador accesible, persistencia y límites estables. */
export function HydraResizablePanel({ children, className = '', side = 'left', ...options }: HydraResizablePanelProps) {
    const state = useHydraResizablePanel({ ...options, resizeDirection: options.resizeDirection ?? (side === 'right' ? 'INCREASE_LEFT' : 'INCREASE_RIGHT') });
    const separator = <div {...state.separatorProps} className="hydra-ui-panel-resizer" />;
    return <>{side === 'right' && separator}<aside className={`hydra-ui-resizable-panel${className === '' ? '' : ` ${className}`}`} style={state.panelStyle}>{children}</aside>{side === 'left' && separator}</>;
}
