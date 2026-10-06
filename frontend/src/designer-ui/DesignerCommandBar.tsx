import type { ReactNode } from 'react';
import { DesignerDropdown } from './DesignerDropdown';
import './designer-command-bar.css';

interface DesignerCommandBarProps {
    title: ReactNode;
    description: ReactNode;
    actions?: ReactNode;
    context?: ReactNode;
    views?: ReactNode;
    status?: ReactNode;
    className?: string;
    headingLevel?: 2 | 3;
    commandsLabel?: ReactNode;
    viewsLabel?: ReactNode;
    viewsSummary?: ReactNode;
}

/**
 * Barra transversal de una sola línea para los diseñadores de HYDRA.
 *
 * La identidad de la capa ya pertenece al shell. Aquí solo aparecen contexto,
 * comandos y vistas de trabajo. Todos los grupos se mantienen en una misma
 * franja horizontal, con scroll horizontal únicamente cuando el viewport no
 * puede contenerlos, sin partir textos ni crear una segunda fila.
 */
export function DesignerCommandBar({ title, description, actions, context, views, status, className = '', headingLevel = 2,
        commandsLabel = 'Commands', viewsLabel = 'Workspace', viewsSummary }: DesignerCommandBarProps) {
    const accessibleTitle = typeof title === 'string' ? title : undefined;
    return <header aria-label={accessibleTitle} className={`hydra-designer-command-bar hydra-unified-workbench-toolbar ${className}`.trim()}>
        <div className="hydra-designer-command-accessible-heading"><div role="heading" aria-level={headingLevel}>{title}</div><div>{description}</div></div>
        <div className="hydra-designer-command-strip">
            {context !== undefined && <div className="hydra-designer-command-context hydra-designer-command-meta-row">{context}</div>}
            {actions !== undefined && <div className="hydra-designer-command-section hydra-designer-command-actions-section hydra-designer-command-actions-row">
                <span className="hydra-designer-command-row-label">{commandsLabel}</span>
                <div className="hydra-designer-command-actions">{actions}</div>
            </div>}
            {views !== undefined && <div className="hydra-designer-command-section hydra-designer-command-views-section hydra-designer-command-view-row">
                <div className="hydra-designer-command-view-context">
                    <span className="hydra-designer-command-row-label">{viewsLabel}</span>
                    {viewsSummary !== undefined && <small className="hydra-designer-command-view-summary">{viewsSummary}</small>}
                </div>
                <div className="hydra-designer-command-views">{views}</div>
            </div>}
            {status !== undefined && <div className="hydra-designer-command-status">{status}</div>}
        </div>
    </header>;
}

/** Agrupa comandos secundarios por intención y los muestra bajo un desplegable. */
export function DesignerCommandGroup({ children, className = '', label }: { children: ReactNode; className?: string; label?: ReactNode }) {
    return <section className={`hydra-designer-command-group ${className}`.trim()}>
        <span aria-hidden="true" className="hydra-designer-command-group-label">{label}</span>
        <DesignerDropdown className="hydra-designer-command-group-dropdown" label={label ?? '⋯'}>{children}</DesignerDropdown>
    </section>;
}
