import { HydraButton } from '../design-system/HydraButton';
import { useCallback, useEffect, useId, useLayoutEffect, useRef, useState, type CSSProperties, type MouseEvent as ReactMouseEvent, type ReactNode } from 'react';
import { HydraOverlayPortal } from '../design-system/HydraOverlayPortal';
import './designer-dropdown.css';

interface DesignerDropdownProps {
    label: ReactNode;
    children: ReactNode;
    className?: string;
    summary?: ReactNode;
    align?: 'START' | 'END';
    closeOnAction?: boolean;
}

/**
 * Desplegable ligero compartido por las barras de herramientas de HYDRA.
 *
 * El popup se renderiza en un portal fijado al viewport. Así las barras pueden
 * desplazarse horizontalmente sin recortar menús ni ensanchar el lienzo.
 */
export function DesignerDropdown({ label, children, className = '', summary, align = 'START', closeOnAction = true }: DesignerDropdownProps) {
    const [open, setOpen] = useState(false);
    const [popupStyle, setPopupStyle] = useState<CSSProperties>({});
    const rootRef = useRef<HTMLDivElement | null>(null);
    const popupRef = useRef<HTMLDivElement | null>(null);
    const triggerRef = useRef<HTMLButtonElement | null>(null);
    const popupId = useId();
    const renderedSummary = typeof summary === 'string' && /^[A-Z0-9_]+$/.test(summary)
        ? summary.toLowerCase().split('_').map((part, index) => index === 0 ? `${part.charAt(0).toUpperCase()}${part.slice(1)}` : part).join(' ')
        : summary;
    const triggerLabel = typeof label === 'string'
        ? `${label}${typeof renderedSummary === 'string' ? `: ${renderedSummary}` : ''}`
        : undefined;

    const reposition = useCallback((): void => {
        const trigger = triggerRef.current;
        if (trigger === null) return;
        const rect = trigger.getBoundingClientRect();
        const viewportPadding = 8;
        const preferredWidth = Math.max(205, Math.min(440, window.innerWidth - viewportPadding * 2));
        const left = align === 'END'
            ? Math.max(viewportPadding, Math.min(window.innerWidth - preferredWidth - viewportPadding, rect.right - preferredWidth))
            : Math.max(viewportPadding, Math.min(window.innerWidth - preferredWidth - viewportPadding, rect.left));
        const availableBelow = window.innerHeight - rect.bottom - viewportPadding;
        const openUpward = availableBelow < 220 && rect.top > availableBelow;
        setPopupStyle(openUpward
            ? { position: 'fixed', left, bottom: Math.max(viewportPadding, window.innerHeight - rect.top + 3), width: preferredWidth }
            : { position: 'fixed', left, top: rect.bottom + 3, width: preferredWidth });
    }, [align]);

    useLayoutEffect(() => {
        if (open) reposition();
    }, [open, reposition]);

    useEffect(() => {
        if (!open) return;
        function handlePointerDown(event: PointerEvent): void {
            const target = event.target as Node;
            if (rootRef.current?.contains(target) || popupRef.current?.contains(target)) return;
            setOpen(false);
        }
        function handleKeyDown(event: KeyboardEvent): void {
            if (event.key !== 'Escape') return;
            setOpen(false);
            triggerRef.current?.focus();
        }
        function repositionOpenPopup(): void { reposition(); }
        document.addEventListener('pointerdown', handlePointerDown);
        document.addEventListener('keydown', handleKeyDown);
        window.addEventListener('resize', repositionOpenPopup, { passive: true });
        window.addEventListener('scroll', repositionOpenPopup, { passive: true, capture: true });
        return () => {
            document.removeEventListener('pointerdown', handlePointerDown);
            document.removeEventListener('keydown', handleKeyDown);
            window.removeEventListener('resize', repositionOpenPopup);
            window.removeEventListener('scroll', repositionOpenPopup, true);
        };
    }, [open, reposition]);

    function handlePopupClick(event: ReactMouseEvent<HTMLDivElement>): void {
        if (!closeOnAction) return;
        const target = event.target as HTMLElement;
        if (target.closest('[data-hydra-menu-keep-open="true"]') !== null) return;
        if (target.closest('button') !== null) setOpen(false);
    }

    const popup = open ? <HydraOverlayPortal
        aria-label={typeof label === 'string' ? label : undefined}
        className="hydra-designer-dropdown-popup hydra-designer-dropdown-portal"
        id={popupId}
        layer="DROPDOWN"
        role="group"
        ref={popupRef}
        style={popupStyle}
        onClick={handlePopupClick}
    >{children}</HydraOverlayPortal> : null;

    return <div className={`hydra-designer-dropdown align-${align.toLowerCase()} ${className}`.trim()} ref={rootRef}>
        <HydraButton aria-controls={popupId} aria-expanded={open} aria-haspopup="true" aria-label={triggerLabel} className="hydra-designer-dropdown-trigger" ref={triggerRef} type="button" onClick={() => setOpen(value => !value)}>
            <span>{label}</span>{renderedSummary !== undefined && <small>{renderedSummary}</small>}<span aria-hidden="true" className="hydra-designer-dropdown-chevron">▾</span>
        </HydraButton>
        {popup}
    </div>;
}
