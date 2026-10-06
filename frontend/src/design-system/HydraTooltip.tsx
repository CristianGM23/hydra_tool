import { HydraButton } from './HydraButton';
import { createPortal } from 'react-dom';
import { Children, cloneElement, isValidElement, useCallback, useEffect, useId, useRef, useState, type ReactElement, type ReactNode } from 'react';
import { useHydraLanguage } from '../i18n/useHydraLanguage';

interface TooltipPosition { left: number; top: number; }

export interface HydraTooltipProps {
    label: string;
    title?: string;
    children: ReactNode;
    trigger?: ReactElement;
    triggerClassName?: string;
    resizable?: boolean;
    className?: string;
    onOpenChange?: (open: boolean) => void;
}

const VIEWPORT_MARGIN = 12;
const GAP = 8;
const DEFAULT_WIDTH = 360;
const DEFAULT_HEIGHT = 220;

/** Tooltip accesible y estable, renderizado en portal y reposicionado con el viewport. */
export function HydraTooltip({ label, title, children, trigger, triggerClassName = '', resizable = true, className = '', onOpenChange }: HydraTooltipProps) {
    const { t } = useHydraLanguage();
    const [open, setOpen] = useState(false);
    const [position, setPosition] = useState<TooltipPosition>({ left: VIEWPORT_MARGIN, top: VIEWPORT_MARGIN });
    const anchorRef = useRef<HTMLElement | null>(null);
    const tooltipRef = useRef<HTMLDivElement>(null);
    const tooltipId = useId();

    const setOpenState = useCallback((next: boolean): void => {
        setOpen(next);
        onOpenChange?.(next);
    }, [onOpenChange]);

    const updatePosition = useCallback((): void => {
        const anchor = anchorRef.current;
        if (anchor === null || typeof window === 'undefined') return;
        const rect = anchor.getBoundingClientRect();
        const width = tooltipRef.current?.offsetWidth ?? DEFAULT_WIDTH;
        const height = tooltipRef.current?.offsetHeight ?? DEFAULT_HEIGHT;
        const left = Math.max(VIEWPORT_MARGIN, Math.min(rect.left, window.innerWidth - width - VIEWPORT_MARGIN));
        const below = rect.bottom + GAP;
        const above = rect.top - height - GAP;
        const top = below + height <= window.innerHeight - VIEWPORT_MARGIN ? below : Math.max(VIEWPORT_MARGIN, above);
        setPosition({ left, top: Math.min(top, Math.max(VIEWPORT_MARGIN, window.innerHeight - height - VIEWPORT_MARGIN)) });
    }, []);

    useEffect(() => {
        if (!open) return undefined;
        const previous = document.activeElement as HTMLElement | null;
        const frame = window.requestAnimationFrame(updatePosition);
        function closeOutside(event: PointerEvent): void {
            const target = event.target as Node;
            if (anchorRef.current?.contains(target) || tooltipRef.current?.contains(target)) return;
            setOpenState(false);
        }
        function closeEscape(event: KeyboardEvent): void {
            if (event.key === 'Escape') setOpenState(false);
        }
        document.addEventListener('pointerdown', closeOutside);
        document.addEventListener('keydown', closeEscape);
        window.addEventListener('resize', updatePosition);
        window.addEventListener('scroll', updatePosition, true);
        const observer = typeof ResizeObserver === 'undefined' ? null : new ResizeObserver(() => updatePosition());
        if (tooltipRef.current !== null) observer?.observe(tooltipRef.current);
        return () => {
            window.cancelAnimationFrame(frame);
            observer?.disconnect();
            document.removeEventListener('pointerdown', closeOutside);
            document.removeEventListener('keydown', closeEscape);
            window.removeEventListener('resize', updatePosition);
            window.removeEventListener('scroll', updatePosition, true);
            if (previous !== null && document.contains(previous)) previous.focus();
        };
    }, [open, setOpenState, updatePosition]);

    const defaultTrigger = <HydraButton className={`hydra-context-help-button hydra-ui-tooltip-trigger${triggerClassName === '' ? '' : ` ${triggerClassName}`}`} type="button">i</HydraButton>;
    const sourceTrigger = trigger ?? defaultTrigger;
    const triggerElement = isValidElement(sourceTrigger)
        ? cloneElement(sourceTrigger as ReactElement<Record<string, unknown>>, {
            'aria-controls': tooltipId,
            'aria-expanded': open,
            'aria-label': t(label)
        })
        : defaultTrigger;

    const tooltip = open && typeof document !== 'undefined' ? createPortal(
        <div
            className={`hydra-ui-tooltip${resizable ? ' resizable' : ''}${className === '' ? '' : ` ${className}`}`}
            id={tooltipId}
            ref={tooltipRef}
            role="tooltip"
            style={{ left: position.left, top: position.top }}
            onPointerDown={event => event.stopPropagation()}
        >
            {title !== undefined && <strong className="hydra-ui-tooltip-title">{t(title)}</strong>}
            <div className="hydra-ui-tooltip-content">{translateTooltipNode(children, t)}</div>
            {resizable && <small className="hydra-ui-tooltip-hint">{t('Drag the lower-right corner to resize.')}</small>}
        </div>,
        document.body
    ) : null;

    return <span
        className="hydra-ui-tooltip-root"
        ref={node => { anchorRef.current = node; }}
        onClick={event => {
            event.preventDefault();
            event.stopPropagation();
            setOpenState(!open);
        }}
    >{triggerElement}{tooltip}</span>;
}

function translateTooltipNode(node: ReactNode, t: (value: string) => string): ReactNode {
    if (typeof node === 'string') return t(node);
    if (Array.isArray(node)) return Children.map(node, child => translateTooltipNode(child, t));
    if (!isValidElement(node)) return node;
    const element = node as ReactElement<{ children?: ReactNode; 'data-hydra-no-translate'?: boolean }>;
    if (element.type === 'code' || element.props['data-hydra-no-translate'] === true || element.props.children === undefined) return element;
    return cloneElement(element, undefined, translateTooltipNode(element.props.children, t));
}
