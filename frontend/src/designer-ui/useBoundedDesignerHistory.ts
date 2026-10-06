import { useCallback, useRef, useState } from 'react';

export interface BoundedDesignerHistory<T> {
    remember: (value?: T) => void;
    undo: () => void;
    redo: () => void;
    clear: () => void;
    canUndo: boolean;
    canRedo: boolean;
}

/**
 * Historial undo/redo acotado para diseñadores de documentos grandes.
 *
 * Mantiene un máximo explícito de snapshots en ambas direcciones y conserva
 * las funciones de captura/restauración más recientes sin crear estado global.
 */
export function useBoundedDesignerHistory<T>(capture: () => T, restore: (value: T) => void, maximumEntries = 50): BoundedDesignerHistory<T> {
    const [past, setPast] = useState<T[]>([]);
    const [future, setFuture] = useState<T[]>([]);
    const pastRef = useRef<T[]>([]);
    const futureRef = useRef<T[]>([]);
    const maximum = Math.max(1, maximumEntries);

    function replacePast(next: T[]): void { pastRef.current = next; setPast(next); }
    function replaceFuture(next: T[]): void { futureRef.current = next; setFuture(next); }

    function remember(value: T = capture()): void {
        const previous = pastRef.current.at(-1);
        if (previous !== undefined && Object.is(previous, value)) { replaceFuture([]); return; }
        replacePast([...pastRef.current.slice(-(maximum - 1)), value]);
        replaceFuture([]);
    }

    function undo(): void {
        const previous = pastRef.current.at(-1);
        if (previous === undefined) return;
        replacePast(pastRef.current.slice(0, -1));
        replaceFuture([...futureRef.current.slice(-(maximum - 1)), capture()]);
        restore(previous);
    }

    function redo(): void {
        const next = futureRef.current.at(-1);
        if (next === undefined) return;
        replaceFuture(futureRef.current.slice(0, -1));
        replacePast([...pastRef.current.slice(-(maximum - 1)), capture()]);
        restore(next);
    }

    const clear = useCallback((): void => {
        pastRef.current = []; futureRef.current = []; setPast([]); setFuture([]);
    }, []);

    return { remember, undo, redo, clear, canUndo: past.length > 0, canRedo: future.length > 0 };
}
