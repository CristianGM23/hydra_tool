import type { ReactNode } from 'react';

export interface HydraNavBarProps {
    brand: ReactNode;
    primary?: ReactNode;
    secondary?: ReactNode;
    status?: ReactNode;
    className?: string;
}

/** Barra principal común, adaptable y con grupos semánticos. */
export function HydraNavBar({ brand, primary, secondary, status, className = '' }: HydraNavBarProps) {
    return <header className={`hydra-ui-navbar${className === '' ? '' : ` ${className}`}`}>
        <div className="hydra-ui-navbar-brand">{brand}</div>
        {primary !== undefined && <nav aria-label="Primary application commands" className="hydra-ui-navbar-group primary">{primary}</nav>}
        {secondary !== undefined && <nav aria-label="Secondary application commands" className="hydra-ui-navbar-group secondary">{secondary}</nav>}
        {status !== undefined && <div className="hydra-ui-navbar-status">{status}</div>}
    </header>;
}
