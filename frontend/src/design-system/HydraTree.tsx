import { HydraButton } from './HydraButton';
import { useMemo, useState, type ReactNode } from 'react';
import { HydraVirtualList } from './HydraVirtualList';

export interface HydraTreeNode<T = unknown> {
    id: string;
    label: string;
    detail?: string;
    badge?: string;
    icon?: ReactNode;
    iconTone?: 'default' | 'success' | 'danger' | 'warning';
    iconLabel?: string;
    data?: T;
    children?: HydraTreeNode<T>[];
}

export interface HydraTreeProps<T> {
    ariaLabel: string;
    nodes: HydraTreeNode<T>[];
    selectedId?: string;
    filter?: string;
    defaultExpandedIds?: string[];
    onSelect?: (node: HydraTreeNode<T>) => void;
    emptyMessage?: string;
    virtualizationThreshold?: number;
    virtualizedHeight?: number;
}

interface VisibleTreeRow<T> { node: HydraTreeNode<T>; depth: number; hasChildren: boolean; open: boolean; }

/** Árbol común con filtrado, expansión tardía, selección accesible y virtualización adaptativa. */
export function HydraTree<T>({ ariaLabel, nodes, selectedId = '', filter = '', defaultExpandedIds = [], onSelect,
        emptyMessage = 'No elements available', virtualizationThreshold = 300, virtualizedHeight = 520 }: HydraTreeProps<T>) {
    const initialExpanded = useMemo(() => new Set(defaultExpandedIds), [defaultExpandedIds]);
    const [expanded, setExpanded] = useState<Set<string>>(initialExpanded);
    const normalizedFilter = filter.trim().toLowerCase();

    const requiredExpanded = useMemo(() => new Set([...defaultExpandedIds, ...ancestorIds(nodes, selectedId)]), [defaultExpandedIds, nodes, selectedId]);
    const effectiveExpanded = useMemo(() => new Set([...expanded, ...requiredExpanded]), [expanded, requiredExpanded]);
    const rows = useMemo(() => visibleRows(nodes, effectiveExpanded, normalizedFilter), [effectiveExpanded, nodes, normalizedFilter]);

    function toggle(id: string): void {
        setExpanded(current => {
            const next = new Set(current);
            if (next.has(id)) next.delete(id); else next.add(id);
            return next;
        });
    }

    function renderRow(row: VisibleTreeRow<T>): ReactNode {
        const node=row.node;
        return <div aria-expanded={row.hasChildren ? row.open : undefined} aria-level={row.depth + 1}
            aria-selected={selectedId === node.id} className={`hydra-ui-tree-row${selectedId === node.id ? ' selected' : ''}`}
            role="treeitem" style={{ paddingLeft: `${8 + row.depth * 16}px` }}>
            <HydraButton className="hydra-ui-tree-main" title={node.detail === undefined ? node.label : `${node.label} · ${node.detail}`}
                type="button" onClick={() => { onSelect?.(node); if (row.hasChildren) toggle(node.id); }}>
                <span className="hydra-ui-tree-expander">{row.hasChildren ? (row.open ? '▾' : '▸') : '·'}</span>
                <span aria-label={node.iconLabel} aria-hidden={node.iconLabel === undefined ? true : undefined} className={`hydra-ui-tree-icon${node.iconTone === undefined || node.iconTone === 'default' ? '' : ` tone-${node.iconTone}`}`} title={node.iconLabel}>{node.icon ?? node.label.charAt(0).toUpperCase()}</span>
                <span className="hydra-ui-tree-text"><strong>{node.label}</strong>{node.detail !== undefined && <small>{node.detail}</small>}</span>
                {node.badge !== undefined && <span className="hydra-ui-tree-badge">{node.badge}</span>}
            </HydraButton>
        </div>;
    }

    return <div className="hydra-ui-tree-container">
        <HydraVirtualList ariaLabel={ariaLabel} className="hydra-ui-tree" height={virtualizedHeight} itemHeight={46}
            itemKey={row => row.node.id} items={rows} renderItem={row => renderRow(row)} role="tree" threshold={virtualizationThreshold} />
        {nodes.length === 0 && <p className="hydra-ui-tree-empty">{emptyMessage}</p>}
    </div>;
}

function visibleRows<T>(nodes: HydraTreeNode<T>[], expanded: Set<string>, filter: string): VisibleTreeRow<T>[] {
    const result: VisibleTreeRow<T>[] = [];
    if (filter === '') {
        const visitUnfiltered = (items: HydraTreeNode<T>[], depth: number): void => {
            for (const node of items) {
                const children = node.children ?? [];
                const hasChildren = children.length > 0;
                const open = expanded.has(node.id);
                result.push({ node, depth, hasChildren, open });
                if (hasChildren && open) visitUnfiltered(children, depth + 1);
            }
        };
        visitUnfiltered(nodes, 0);
        return result;
    }

    // Build matching sets once. The previous recursive predicate re-walked large
    // subtrees for every ancestor and became quadratic while typing a filter.
    const directMatches = new Set<string>();
    const branchMatches = new Set<string>();
    const indexMatches = (node: HydraTreeNode<T>): boolean => {
        const direct = [node.label, node.detail ?? '', node.badge ?? ''].some(value => value.toLowerCase().includes(filter));
        if (direct) directMatches.add(node.id);
        let descendant = false;
        for (const child of node.children ?? []) descendant = indexMatches(child) || descendant;
        if (direct || descendant) branchMatches.add(node.id);
        return direct || descendant;
    };
    nodes.forEach(indexMatches);

    const visit = (items: HydraTreeNode<T>[], depth: number, includeCompleteSubtree = false): void => {
        for (const node of items) {
            if (!includeCompleteSubtree && !branchMatches.has(node.id)) continue;
            const includeChildren = includeCompleteSubtree || directMatches.has(node.id);
            const children = (node.children ?? []).filter(child => includeChildren || branchMatches.has(child.id));
            const hasChildren = children.length > 0;
            result.push({ node, depth, hasChildren, open: true });
            if (hasChildren) visit(children, depth + 1, includeChildren);
        }
    };
    visit(nodes, 0);
    return result;
}

function ancestorIds<T>(nodes: HydraTreeNode<T>[], selectedId: string): string[] {
    if (selectedId === '') return [];
    const path: string[] = [];
    function visit(items: HydraTreeNode<T>[]): boolean {
        for (const item of items) {
            path.push(item.id);
            if (item.id === selectedId) return true;
            if (visit(item.children ?? [])) return true;
            path.pop();
        }
        return false;
    }
    return visit(nodes) ? path.slice(0, -1) : [];
}
