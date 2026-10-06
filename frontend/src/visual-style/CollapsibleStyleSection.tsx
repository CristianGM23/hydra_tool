import { HydraButton } from '../design-system/HydraButton';
import type { ReactNode } from 'react';

interface CollapsibleStyleSectionProps {
    id: string;
    title: string;
    description: string;
    open: boolean;
    onToggle: (id: string) => void;
    children: ReactNode;
}

/**
 * Muestra una sección de personalización que permanece cerrada por defecto.
 */
export function CollapsibleStyleSection({ id, title, description, open, onToggle, children }: CollapsibleStyleSectionProps) {
    const contentId = `visual-style-section-${id}`;

    return (
        <section className={open ? 'visual-style-section open' : 'visual-style-section'}>
            <HydraButton aria-controls={contentId} aria-expanded={open} className="visual-style-section-toggle" onClick={() => onToggle(id)} type="button">
                <span><strong>{title}</strong><small>{description}</small></span>
                <span aria-hidden="true" className="visual-style-section-chevron">⌄</span>
            </HydraButton>
            {open && <div className="visual-style-section-content" id={contentId}>{children}</div>}
        </section>
    );
}
