import { HydraButton } from '../design-system/HydraButton';
import type { ReactNode } from 'react';
import { HydraTooltip } from '../design-system/HydraTooltip';
import './designer-ui.css';

interface ResizableInfoTooltipProps {
    label: string;
    title: string;
    children: ReactNode;
    className?: string;
}

/** Compatibilidad histórica delegada al tooltip estable del sistema de diseño. */
export function ResizableInfoTooltip({ label, title, children, className = '' }: ResizableInfoTooltipProps) {
    return <HydraTooltip
        className={`hydra-resizable-info-tooltip${className === '' ? '' : ` ${className}`}`}
        label={label}
        title={title}
        trigger={<HydraButton className="hydra-context-help-button hydra-resizable-info-button" type="button">i</HydraButton>}
    >
        {children}
    </HydraTooltip>;
}
