import { createPortal } from 'react-dom';
import { useEffect, useId, useRef, type ReactNode } from 'react';
import { HydraWindowControls } from './HydraWindowControls';

export interface HydraModalProps {
    open: boolean;
    title: string;
    description?: string;
    children: ReactNode;
    footer?: ReactNode;
    onClose: () => void;
    closeLabel?: string;
    size?: 'small' | 'medium' | 'large' | 'wide';
    className?: string;
}

/** Modal común con portal, Escape, restauración de foco y bloqueo del scroll. */
export function HydraModal({ open, title, description, children, footer, onClose, closeLabel = 'Close dialog', size = 'medium', className = '' }: HydraModalProps) {
    const titleId = useId();
    const descriptionId = useId();
    const dialogRef = useRef<HTMLElement>(null);
    const onCloseRef = useRef(onClose);

    useEffect(() => { onCloseRef.current = onClose; }, [onClose]);

    useEffect(() => {
        if (!open) return undefined;
        const previous = document.activeElement as HTMLElement | null;
        const previousOverflow = document.body.style.overflow;
        document.body.style.overflow = 'hidden';
        const backdrop = dialogRef.current?.closest('.hydra-ui-modal-backdrop');
        const background = Array.from(document.body.children).filter(element => element !== backdrop).map(element => ({
            element: element as HTMLElement,
            inert: (element as HTMLElement).inert
        }));
        background.forEach(({ element }) => { element.inert = true; });
        const frame = window.requestAnimationFrame(() => dialogRef.current?.focus());
        function onKeyDown(event: KeyboardEvent): void {
            if (event.key === 'Escape') onCloseRef.current();
            if (event.key !== 'Tab' || dialogRef.current === null) return;
            const focusable = Array.from(dialogRef.current.querySelectorAll<HTMLElement>('button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [href], [tabindex]:not([tabindex="-1"])'));
            if (focusable.length === 0) { event.preventDefault(); dialogRef.current.focus(); return; }
            const first = focusable[0];
            const last = focusable[focusable.length - 1];
            if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus(); }
            else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus(); }
        }
        document.addEventListener('keydown', onKeyDown);
        return () => {
            window.cancelAnimationFrame(frame);
            document.removeEventListener('keydown', onKeyDown);
            document.body.style.overflow = previousOverflow;
            background.forEach(({ element, inert }) => { element.inert = inert; });
            if (previous !== null && document.contains(previous)) previous.focus();
        };
    }, [open]);

    if (!open || typeof document === 'undefined') return null;

    return createPortal(
        <div className="hydra-ui-modal-backdrop" onMouseDown={event => { if (event.target === event.currentTarget) onClose(); }}>
            <section
                aria-describedby={description === undefined ? undefined : descriptionId}
                aria-labelledby={titleId}
                aria-modal="true"
                className={`hydra-ui-modal size-${size}${className === '' ? '' : ` ${className}`}`}
                ref={dialogRef}
                role="dialog"
                tabIndex={-1}
            >
                <div className="hydra-ui-modal-header">
                    <div><h2 id={titleId}>{title}</h2>{description !== undefined && <p id={descriptionId}>{description}</p>}</div>
                    <HydraWindowControls closeLabel={closeLabel} onClose={onClose} />
                </div>
                {children !== null && children !== undefined && <div className="hydra-ui-modal-content">{children}</div>}
                {footer !== undefined && <div className="hydra-ui-modal-footer">{footer}</div>}
            </section>
        </div>,
        document.body
    );
}
