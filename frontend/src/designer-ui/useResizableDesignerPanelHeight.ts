import { useMemo, useState } from 'react';
import type { CSSProperties, KeyboardEvent as ReactKeyboardEvent, PointerEvent as ReactPointerEvent } from 'react';

interface Options {
    storageKey: string;
    ariaLabel: string;
    initialHeight?: number;
    minimumHeight?: number;
    maximumHeight?: number;
    cssVariableName?: `--${string}`;
    separatorClassName?: string;
}

function clamp(value: number, minimum: number, maximum: number): number { return Math.min(maximum, Math.max(minimum, value)); }
function stored(key: string, fallback: number, minimum: number, maximum: number): number {
    if (typeof window === 'undefined') return fallback;
    const parsed = Number(window.localStorage.getItem(key));
    return Number.isFinite(parsed) ? clamp(parsed, minimum, maximum) : fallback;
}

/** Separador horizontal accesible y persistente para paneles inferiores/superiores. */
export function useResizableDesignerPanelHeight(options: Options) {
    const minimum = options.minimumHeight ?? 150;
    const maximum = options.maximumHeight ?? 520;
    const initial = clamp(options.initialHeight ?? 230, minimum, maximum);
    const variable = options.cssVariableName ?? '--hydra-panel-height';
    const [height, setHeight] = useState(() => stored(options.storageKey, initial, minimum, maximum));

    function commit(next: number): void {
        const value = clamp(next, minimum, maximum);
        setHeight(value);
        if (typeof window !== 'undefined') window.localStorage.setItem(options.storageKey, String(value));
    }
    function pointerDown(event: ReactPointerEvent<HTMLDivElement>): void {
        event.preventDefault();
        const startY = event.clientY;
        const startHeight = height;
        const move = (next: PointerEvent) => commit(startHeight - (next.clientY - startY));
        const stop = () => { window.removeEventListener('pointermove', move); window.removeEventListener('pointerup', stop); };
        window.addEventListener('pointermove', move);
        window.addEventListener('pointerup', stop, { once: true });
    }
    function keyDown(event: ReactKeyboardEvent<HTMLDivElement>): void {
        if (event.key === 'ArrowUp') { event.preventDefault(); commit(height + 16); }
        if (event.key === 'ArrowDown') { event.preventDefault(); commit(height - 16); }
        if (event.key === 'Home') { event.preventDefault(); commit(minimum); }
        if (event.key === 'End') { event.preventDefault(); commit(maximum); }
    }
    const layoutStyle = useMemo(() => ({ [variable]: `${height}px` } as CSSProperties), [height, variable]);
    return {
        height,
        layoutStyle,
        separatorProps: {
            className: options.separatorClassName ?? 'hydra-horizontal-panel-resizer',
            role: 'separator' as const,
            tabIndex: 0,
            'aria-label': options.ariaLabel,
            'aria-orientation': 'horizontal' as const,
            'aria-valuemin': minimum,
            'aria-valuemax': maximum,
            'aria-valuenow': height,
            onPointerDown: pointerDown,
            onKeyDown: keyDown
        }
    };
}
