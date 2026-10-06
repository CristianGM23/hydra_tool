import type { ReactNode } from 'react';

export interface HydraPaletteProps {
    title: string;
    description?: string;
    actions?: ReactNode;
    search?: ReactNode;
    children: ReactNode;
    footer?: ReactNode;
    className?: string;
    ariaLabel?: string;
}

/** Superficie común para paletas, catálogos y listas de creación. */
export function HydraPalette({ title, description, actions, search, children, footer, className = '', ariaLabel }: HydraPaletteProps) {
    return <aside aria-label={ariaLabel ?? title} className={`hydra-ui-palette${className === '' ? '' : ` ${className}`}`}>
        <header className="hydra-ui-palette-header"><div><h3>{title}</h3>{description !== undefined && <p>{description}</p>}</div>{actions !== undefined && <div className="hydra-ui-palette-actions">{actions}</div>}</header>
        {search !== undefined && <div className="hydra-ui-palette-search">{search}</div>}
        <div className="hydra-ui-palette-body">{children}</div>
        {footer !== undefined && <footer className="hydra-ui-palette-footer">{footer}</footer>}
    </aside>;
}
