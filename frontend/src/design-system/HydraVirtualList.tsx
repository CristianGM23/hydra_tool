import { Fragment, useMemo, useState, type AriaRole, type ReactNode, type UIEvent } from 'react';

interface HydraVirtualListProps<T> {
    items: readonly T[];
    itemKey: (item: T, index: number) => string;
    renderItem: (item: T, index: number) => ReactNode;
    itemHeight: number;
    height?: number;
    overscan?: number;
    threshold?: number;
    className?: string;
    ariaLabel: string;
    role?: AriaRole;
}

/** Lista virtual de altura fija, sin dependencias, para catálogos grandes. */
export function HydraVirtualList<T>({ items, itemKey, renderItem, itemHeight, height = 420, overscan = 6,
        threshold = 120, className = '', ariaLabel, role }: HydraVirtualListProps<T>) {
    const [scrollTop, setScrollTop] = useState(0);
    const virtual = items.length > threshold;
    const range = useMemo(() => {
        if(!virtual) return { start: 0, end: items.length };
        const start = Math.max(0, Math.floor(scrollTop / itemHeight) - overscan);
        const visible = Math.ceil(height / itemHeight) + overscan * 2;
        return { start, end: Math.min(items.length, start + visible) };
    }, [height, itemHeight, items.length, overscan, scrollTop, virtual]);
    const visibleItems = items.slice(range.start, range.end);
    function onScroll(event: UIEvent<HTMLDivElement>): void { setScrollTop(event.currentTarget.scrollTop); }
    if(!virtual) return <div aria-label={ariaLabel} className={className} role={role}>{items.map((item, index) => <Fragment key={itemKey(item, index)}>{renderItem(item, index)}</Fragment>)}</div>;
    return <div aria-label={ariaLabel} className={`hydra-virtual-list ${className}`.trim()} onScroll={onScroll} role={role}
        style={{ height, overflowY: 'auto', position: 'relative' }} data-virtualized="true">
        <div style={{ height: items.length * itemHeight, position: 'relative' }}>
            {visibleItems.map((item, offset) => {
                const index = range.start + offset;
                return <div key={itemKey(item, index)} style={{ position: 'absolute', top: index * itemHeight, left: 0, right: 0, height: itemHeight }}>
                    {renderItem(item, index)}
                </div>;
            })}
        </div>
    </div>;
}
