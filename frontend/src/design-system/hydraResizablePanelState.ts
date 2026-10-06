import { useCallback, useMemo, useState, type CSSProperties, type KeyboardEvent as ReactKeyboardEvent, type PointerEvent as ReactPointerEvent } from 'react';

export interface HydraResizablePanelOptions {
    storageKey: string;
    ariaLabel: string;
    initialWidth?: number;
    minimumWidth?: number;
    maximumWidth?: number;
    keyboardStep?: number;
    resizeDirection?: 'INCREASE_RIGHT' | 'INCREASE_LEFT';
}

export interface HydraResizablePanelState {
    width: number;
    panelStyle: CSSProperties;
    separatorProps: {
        'aria-label': string;
        'aria-orientation': 'vertical';
        'aria-valuemin': number;
        'aria-valuemax': number;
        'aria-valuenow': number;
        role: 'separator';
        tabIndex: number;
        onDoubleClick: () => void;
        onKeyDown: (event: ReactKeyboardEvent<HTMLDivElement>) => void;
        onPointerDown: (event: ReactPointerEvent<HTMLDivElement>) => void;
    };
}

const DEFAULT_WIDTH = 310;
const DEFAULT_MINIMUM = 230;
const DEFAULT_MAXIMUM = 620;
const DEFAULT_STEP = 16;

export function clampHydraPanelWidth(width: number, minimumWidth = DEFAULT_MINIMUM, maximumWidth = DEFAULT_MAXIMUM): number {
    const safeMinimum = Math.max(180, Math.round(minimumWidth));
    const safeMaximum = Math.max(safeMinimum, Math.round(maximumWidth));
    if (!Number.isFinite(width)) return Math.min(Math.max(DEFAULT_WIDTH, safeMinimum), safeMaximum);
    return Math.min(Math.max(Math.round(width), safeMinimum), safeMaximum);
}

/** Estado común para paneles redimensionables mediante ratón y teclado. */
export function useHydraResizablePanel(options: HydraResizablePanelOptions): HydraResizablePanelState {
    const minimumWidth = options.minimumWidth ?? DEFAULT_MINIMUM;
    const maximumWidth = options.maximumWidth ?? DEFAULT_MAXIMUM;
    const initialWidth = clampHydraPanelWidth(options.initialWidth ?? DEFAULT_WIDTH, minimumWidth, maximumWidth);
    const keyboardStep = Math.max(4, options.keyboardStep ?? DEFAULT_STEP);
    const direction = options.resizeDirection === 'INCREASE_LEFT' ? -1 : 1;
    const [width, setWidth] = useState(() => readStoredWidth(options.storageKey, initialWidth, minimumWidth, maximumWidth));

    const updateWidth = useCallback((nextWidth: number): void => {
        const normalized = clampHydraPanelWidth(nextWidth, minimumWidth, maximumWidth);
        setWidth(normalized);
        try { window.localStorage.setItem(options.storageKey, String(normalized)); } catch { /* optional */ }
    }, [maximumWidth, minimumWidth, options.storageKey]);

    const reset = useCallback(() => updateWidth(initialWidth), [initialWidth, updateWidth]);
    const onKeyDown = useCallback((event: ReactKeyboardEvent<HTMLDivElement>): void => {
        if (event.key === 'ArrowLeft') { event.preventDefault(); updateWidth(width - keyboardStep * direction); }
        else if (event.key === 'ArrowRight') { event.preventDefault(); updateWidth(width + keyboardStep * direction); }
        else if (event.key === 'Home') { event.preventDefault(); updateWidth(minimumWidth); }
        else if (event.key === 'End') { event.preventDefault(); updateWidth(maximumWidth); }
    }, [direction, keyboardStep, maximumWidth, minimumWidth, updateWidth, width]);
    const onPointerDown = useCallback((event: ReactPointerEvent<HTMLDivElement>): void => {
        event.preventDefault();
        const startX = event.clientX;
        const startWidth = width;
        const ownerDocument = event.currentTarget.ownerDocument;
        const ownerWindow = ownerDocument.defaultView ?? window;
        ownerDocument.body.classList.add('hydra-ui-resizing');
        const resize = (pointerEvent: PointerEvent): void => updateWidth(startWidth + (pointerEvent.clientX - startX) * direction);
        const finish = (): void => {
            ownerWindow.removeEventListener('pointermove', resize);
            ownerWindow.removeEventListener('pointerup', finish);
            ownerWindow.removeEventListener('pointercancel', finish);
            ownerDocument.body.classList.remove('hydra-ui-resizing');
        };
        ownerWindow.addEventListener('pointermove', resize);
        ownerWindow.addEventListener('pointerup', finish, { once: true });
        ownerWindow.addEventListener('pointercancel', finish, { once: true });
    }, [direction, updateWidth, width]);

    return {
        width,
        panelStyle: useMemo(() => ({ width: `${width}px`, flexBasis: `${width}px` }), [width]),
        separatorProps: {
            'aria-label': options.ariaLabel,
            'aria-orientation': 'vertical',
            'aria-valuemin': minimumWidth,
            'aria-valuemax': maximumWidth,
            'aria-valuenow': width,
            role: 'separator',
            tabIndex: 0,
            onDoubleClick: reset,
            onKeyDown,
            onPointerDown
        }
    };
}

function readStoredWidth(storageKey: string, fallback: number, minimumWidth: number, maximumWidth: number): number {
    if (typeof window === 'undefined') return fallback;
    try {
        const value = window.localStorage.getItem(storageKey);
        return value === null ? fallback : clampHydraPanelWidth(Number(value), minimumWidth, maximumWidth);
    } catch { return fallback; }
}
