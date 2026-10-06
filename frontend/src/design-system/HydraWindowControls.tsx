import { HydraButton } from './HydraButton';
import { useHydraLanguage } from '../i18n/useHydraLanguage';

export interface HydraWindowControlsProps {
    onClose: () => void;
    closeLabel?: string;
    closeDisabled?: boolean;
    maximized?: boolean;
    onToggleMaximize?: () => void;
    maximizeLabel?: string;
    restoreLabel?: string;
    className?: string;
}

/**
 * Controles comunes de ventana para diálogos y superficies flotantes de HYDRA.
 * Mantiene maximizar/restaurar y cerrar en la esquina superior derecha con una
 * geometría cuadrada común; el cierre usa siempre el affordance rojo canónico.
 */
export function HydraWindowControls({
    onClose,
    closeLabel = 'Close dialog',
    closeDisabled = false,
    maximized = false,
    onToggleMaximize,
    maximizeLabel = 'Maximize dialog',
    restoreLabel = 'Restore dialog',
    className = '',
}: HydraWindowControlsProps) {
    const { t } = useHydraLanguage();
    return <div className={`hydra-window-controls${className === '' ? '' : ` ${className}`}`}>
        {onToggleMaximize !== undefined && <HydraButton
            aria-label={t(maximized ? restoreLabel : maximizeLabel)}
            className="hydra-window-control hydra-window-maximize"
            compact
            size="small"
            title={t(maximized ? restoreLabel : maximizeLabel)}
            variant="ghost"
            onClick={onToggleMaximize}
        >
            {maximized
                ? <svg aria-hidden="true" focusable="false" height="14" viewBox="0 0 16 16" width="14"><path d="M3 1.5h9.5v9.5H11V3H3z" fill="currentColor"/><path d="M1.5 5H11v9.5H1.5z" fill="none" stroke="currentColor" strokeWidth="1.5"/></svg>
                : <svg aria-hidden="true" focusable="false" height="14" viewBox="0 0 16 16" width="14"><rect x="2" y="2" width="12" height="12" fill="none" stroke="currentColor" strokeWidth="1.5"/></svg>}
        </HydraButton>}
        <HydraButton
            aria-label={t(closeLabel)}
            className="hydra-window-control hydra-window-close"
            compact
            disabled={closeDisabled}
            size="small"
            title={t(closeLabel)}
            variant="danger"
            onClick={onClose}
        >
            <svg aria-hidden="true" focusable="false" height="14" viewBox="0 0 16 16" width="14"><path d="M3 3l10 10M13 3L3 13" fill="none" stroke="currentColor" strokeLinecap="round" strokeWidth="1.8"/></svg>
        </HydraButton>
    </div>;
}
