import { HydraTree, type HydraTreeNode } from '../design-system';
import './designer-structure.css';

export type DesignerStructureNode<T = unknown> = HydraTreeNode<T>;

interface Props<T> {
    ariaLabel: string;
    nodes: DesignerStructureNode<T>[];
    selectedId?: string;
    filter?: string;
    defaultExpandedIds?: string[];
    onSelect?: (node: DesignerStructureNode<T>) => void;
}

/** Adaptador de compatibilidad: todos los árboles de diseñador usan HydraTree. */
export function DesignerStructureTree<T>(props: Props<T>) {
    return <div className="designer-structure-tree-adapter"><HydraTree {...props} /></div>;
}
