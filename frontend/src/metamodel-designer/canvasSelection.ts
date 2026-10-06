import type { Edge, Node } from '@xyflow/react';

/**
 * Describe un rectángulo dentro del lienzo.
 */
export interface CanvasSelectionRectangle {
    left: number;
    top: number;
    right: number;
    bottom: number;
    width: number;
    height: number;
}

/**
 * Describe una relación de composición utilizada por el árbol.
 */
export interface CanvasContainmentRelation {
    sourceId: string;
    targetId: string;
}

/**
 * Configura los márgenes de las operaciones de selección.
 */
export interface CanvasSelectionArrangementOptions {
    nodeGap: number;
    treeLevelGap: number;
}

/**
 * Acciones disponibles para ordenar una selección múltiple.
 */
export type CanvasSelectionArrangement = 'ALIGN_LEFT' | 'ALIGN_TOP' | 'DISTRIBUTE_HORIZONTAL' | 'DISTRIBUTE_VERTICAL' | 'TREE';

const DEFAULT_OPTIONS: CanvasSelectionArrangementOptions = { nodeGap: 50, treeLevelGap: 110 };

/**
 * Normaliza dos puntos para obtener un rectángulo positivo.
 *
 * @param startX contiene la coordenada horizontal inicial.
 * @param startY contiene la coordenada vertical inicial.
 * @param endX contiene la coordenada horizontal final.
 * @param endY contiene la coordenada vertical final.
 * @returns rectángulo normalizado.
 */
export function normalizeSelectionRectangle(startX: number, startY: number, endX: number, endY: number): CanvasSelectionRectangle {
    const left = Math.min(startX, endX);
    const top = Math.min(startY, endY);
    const right = Math.max(startX, endX);
    const bottom = Math.max(startY, endY);
    return { left, top, right, bottom, width: right - left, height: bottom - top };
}

/**
 * Busca los nodos que se solapan con la zona de selección.
 *
 * @param nodes contiene los nodos actuales.
 * @param rectangle contiene la zona en coordenadas del flujo.
 * @returns identificadores seleccionados.
 */
export function findNodesInsideSelection(nodes: Node[], rectangle: CanvasSelectionRectangle): string[] {
    return nodes.filter(node => intersects(rectangle, nodeRectangle(node))).map(node => node.id);
}

/**
 * Busca las relaciones cuyos dos extremos pertenecen a la selección.
 *
 * @param edges contiene las relaciones visuales.
 * @param selectedNodeIds contiene los nodos seleccionados.
 * @returns identificadores de relaciones internas.
 */
export function findInternalRelationIds(edges: Edge[], selectedNodeIds: string[]): string[] {
    const selected = new Set(selectedNodeIds);
    return edges.filter(edge => selected.has(edge.source) && selected.has(edge.target)).map(edge => edge.id);
}

/**
 * Aplica una alineación, distribución o árbol sin solapar los nodos.
 *
 * @param nodes contiene todos los nodos.
 * @param selectedNodeIds contiene los nodos afectados.
 * @param arrangement contiene la operación.
 * @param containmentRelations contiene las composiciones entre clases.
 * @param requestedOptions contiene los márgenes opcionales.
 * @returns nodos actualizados.
 */
export function arrangeSelectedNodes(nodes: Node[], selectedNodeIds: string[], arrangement: CanvasSelectionArrangement, containmentRelations: CanvasContainmentRelation[] = [], requestedOptions: Partial<CanvasSelectionArrangementOptions> = {}): Node[] {
    const selectedSet = new Set(selectedNodeIds);
    const selected = nodes.filter(node => selectedSet.has(node.id));

    if(selected.length < 2) {
        return nodes;
    }

    const options = { ...DEFAULT_OPTIONS, ...requestedOptions };
    const positions = new Map<string, { x: number; y: number }>();

    if(arrangement === 'ALIGN_LEFT') {
        alignVertically(selected, positions, options.nodeGap);
    } else if(arrangement === 'ALIGN_TOP') {
        alignHorizontally(selected, positions, options.nodeGap);
    } else if(arrangement === 'DISTRIBUTE_HORIZONTAL') {
        distribute(selected, positions, true, options.nodeGap);
    } else if(arrangement === 'DISTRIBUTE_VERTICAL') {
        distribute(selected, positions, false, options.nodeGap);
    } else {
        arrangeTree(selected, positions, containmentRelations, options);
    }

    return nodes.map(node => {
        const position = positions.get(node.id);

        if(position === undefined) {
            return node;
        }

        return { ...node, position, selected: true };
    });
}

/**
 * Indica si una selección tiene tamaño suficiente para considerarse un arrastre.
 *
 * @param rectangle contiene la zona.
 * @returns true cuando no es un simple clic.
 */
export function isMeaningfulSelectionRectangle(rectangle: CanvasSelectionRectangle): boolean {
    return rectangle.width >= 4 && rectangle.height >= 4;
}

function alignVertically(selected: Node[], positions: Map<string, { x: number; y: number }>, gap: number): void {
    const ordered = [...selected].sort((first, second) => first.position.y - second.position.y || first.position.x - second.position.x || first.id.localeCompare(second.id));
    const left = Math.min(...ordered.map(node => node.position.x));
    let currentY = Math.min(...ordered.map(node => node.position.y));

    for(const node of ordered) {
        positions.set(node.id, { x: left, y: currentY });
        currentY += nodeSize(node).height + gap;
    }
}

function alignHorizontally(selected: Node[], positions: Map<string, { x: number; y: number }>, gap: number): void {
    const ordered = [...selected].sort((first, second) => first.position.x - second.position.x || first.position.y - second.position.y || first.id.localeCompare(second.id));
    const top = Math.min(...ordered.map(node => node.position.y));
    let currentX = Math.min(...ordered.map(node => node.position.x));

    for(const node of ordered) {
        positions.set(node.id, { x: currentX, y: top });
        currentX += nodeSize(node).width + gap;
    }
}

function distribute(selected: Node[], positions: Map<string, { x: number; y: number }>, horizontal: boolean, minimumGap: number): void {
    if(selected.length < 3) {
        return;
    }

    const ordered = [...selected].sort((first, second) => horizontal ? first.position.x - second.position.x || first.id.localeCompare(second.id) : first.position.y - second.position.y || first.id.localeCompare(second.id));
    const first = ordered[0];
    const last = ordered[ordered.length - 1];
    const firstStart = horizontal ? first.position.x : first.position.y;
    const lastEnd = horizontal ? last.position.x + nodeSize(last).width : last.position.y + nodeSize(last).height;
    const totalSize = ordered.reduce((sum, node) => sum + (horizontal ? nodeSize(node).width : nodeSize(node).height), 0);
    const availableGap = (lastEnd - firstStart - totalSize) / (ordered.length - 1);
    const gap = Math.max(minimumGap, availableGap);
    let cursor = firstStart;

    for(const node of ordered) {
        if(horizontal) {
            positions.set(node.id, { x: cursor, y: node.position.y });
            cursor += nodeSize(node).width + gap;
        } else {
            positions.set(node.id, { x: node.position.x, y: cursor });
            cursor += nodeSize(node).height + gap;
        }
    }
}

function arrangeTree(selected: Node[], positions: Map<string, { x: number; y: number }>, containmentRelations: CanvasContainmentRelation[], options: CanvasSelectionArrangementOptions): void {
    const selectedIds = new Set(selected.map(node => node.id));
    const relations = uniqueContainmentRelations(containmentRelations, selectedIds);

    if(relations.length === 0) {
        arrangeFallbackTree(selected, positions, options);
        return;
    }

    const incoming = new Map<string, string[]>();
    const primaryParent = new Map<string, string>();
    const children = new Map<string, string[]>();

    for(const node of selected) {
        incoming.set(node.id, []);
        children.set(node.id, []);
    }

    for(const relation of relations) {
        incoming.get(relation.targetId)?.push(relation.sourceId);
    }

    for(const parents of incoming.values()) {
        parents.sort((first, second) => nodeOrder(selected, first) - nodeOrder(selected, second));
    }

    /* A metamodel can declare alternative containers. The visual tree chooses
       one deterministic primary parent and ignores only edges that would create
       a cycle. Semantic relations remain untouched. */
    for(const node of selected) {
        for(const candidate of incoming.get(node.id) ?? []) {
            if(!wouldCreatePrimaryCycle(candidate, node.id, primaryParent)) {
                primaryParent.set(node.id, candidate);
                children.get(candidate)?.push(node.id);
                break;
            }
        }
    }

    for(const childIds of children.values()) {
        childIds.sort((first, second) => nodeOrder(selected, first) - nodeOrder(selected, second));
    }

    const roots = selected.filter(node => !primaryParent.has(node.id)).sort((first, second) => nodeOrder(selected, first.id) - nodeOrder(selected, second.id));
    const safeRoots = roots.length > 0 ? roots : [selected[0]];
    const depths = new Map<string, number>();

    for(const root of safeRoots) {
        assignPrimaryDepth(root.id, 0, children, depths);
    }

    /* Any node disconnected by malformed or cyclic input remains a root-level
       component instead of being placed one level below an unrelated class. */
    for(const node of selected) {
        if(!depths.has(node.id)) assignPrimaryDepth(node.id, 0, children, depths);
    }

    const levelTops = calculateLevelTops(selected, depths, options.treeLevelGap);
    const widths = new Map<string, number>();

    for(const root of safeRoots) {
        calculateSubtreeWidth(root.id, selected, children, widths, options.nodeGap);
    }

    const minimumX = Math.min(...selected.map(node => node.position.x));
    let cursorX = minimumX;

    for(const root of safeRoots) {
        const width = widths.get(root.id) ?? nodeSize(root).width;
        placeSubtree(root.id, cursorX, selected, children, depths, levelTops, widths, positions, options.nodeGap);
        cursorX += width + options.nodeGap;
    }

    const placed = new Set(positions.keys());
    const remaining = selected.filter(node => !placed.has(node.id));

    for(const node of remaining) {
        positions.set(node.id, { x: cursorX, y: levelTops.get(depths.get(node.id) ?? 0) ?? Math.min(...selected.map(item => item.position.y)) });
        cursorX += nodeSize(node).width + options.nodeGap;
    }
}

function uniqueContainmentRelations(relations: CanvasContainmentRelation[], selectedIds: Set<string>): CanvasContainmentRelation[] {
    const seen = new Set<string>();
    const result: CanvasContainmentRelation[] = [];

    for(const relation of relations) {
        if(!selectedIds.has(relation.sourceId) || !selectedIds.has(relation.targetId) || relation.sourceId === relation.targetId) continue;
        const key = `${relation.sourceId}\u0000${relation.targetId}`;
        if(seen.has(key)) continue;
        seen.add(key);
        result.push(relation);
    }

    return result;
}

function arrangeFallbackTree(selected: Node[], positions: Map<string, { x: number; y: number }>, options: CanvasSelectionArrangementOptions): void {
    const root = selected[0];
    const children = selected.slice(1);
    const minimumX = Math.min(...selected.map(node => node.position.x));
    const minimumY = Math.min(...selected.map(node => node.position.y));
    const childrenWidth = levelWidth(children, options.nodeGap);
    const rootSize = nodeSize(root);
    const totalWidth = Math.max(rootSize.width, childrenWidth);
    positions.set(root.id, { x: minimumX + (totalWidth - rootSize.width) / 2, y: minimumY });
    let childX = minimumX + (totalWidth - childrenWidth) / 2;
    const childY = minimumY + rootSize.height + options.treeLevelGap;

    for(const child of children) {
        positions.set(child.id, { x: childX, y: childY });
        childX += nodeSize(child).width + options.nodeGap;
    }
}

function wouldCreatePrimaryCycle(parentId: string, childId: string, primaryParent: Map<string, string>): boolean {
    let current: string | undefined = parentId;
    const visited = new Set<string>();

    while(current !== undefined && !visited.has(current)) {
        if(current === childId) return true;
        visited.add(current);
        current = primaryParent.get(current);
    }

    return false;
}

function assignPrimaryDepth(nodeId: string, depth: number, children: Map<string, string[]>, depths: Map<string, number>): void {
    const current = depths.get(nodeId);
    if(current !== undefined && current <= depth) return;
    depths.set(nodeId, depth);
    for(const childId of children.get(nodeId) ?? []) assignPrimaryDepth(childId, depth + 1, children, depths);
}

function calculateLevelTops(selected: Node[], depths: Map<string, number>, levelGap: number): Map<number, number> {
    const maximumHeight = new Map<number, number>();
    const maximumDepth = Math.max(0, ...selected.map(node => depths.get(node.id) ?? 0));

    for(const node of selected) {
        const depth = depths.get(node.id) ?? 0;
        maximumHeight.set(depth, Math.max(maximumHeight.get(depth) ?? 0, nodeSize(node).height));
    }

    const result = new Map<number, number>();
    let y = Math.min(...selected.map(node => node.position.y));

    for(let depth = 0; depth <= maximumDepth; depth += 1) {
        result.set(depth, y);
        y += (maximumHeight.get(depth) ?? 1) + levelGap;
    }

    return result;
}

function calculateSubtreeWidth(nodeId: string, selected: Node[], children: Map<string, string[]>, widths: Map<string, number>, gap: number): number {
    const existing = widths.get(nodeId);
    if(existing !== undefined) return existing;
    const node = selected.find(item => item.id === nodeId);
    if(node === undefined) return 0;
    const childIds = children.get(nodeId) ?? [];
    const childrenWidth = childIds.length === 0 ? 0 : childIds.reduce((sum, childId) => sum + calculateSubtreeWidth(childId, selected, children, widths, gap), 0) + gap * (childIds.length - 1);
    const width = Math.max(nodeSize(node).width, childrenWidth);
    widths.set(nodeId, width);
    return width;
}

function placeSubtree(nodeId: string, left: number, selected: Node[], children: Map<string, string[]>, depths: Map<string, number>, levelTops: Map<number, number>, widths: Map<string, number>, positions: Map<string, { x: number; y: number }>, gap: number): void {
    const node = selected.find(item => item.id === nodeId);
    if(node === undefined) return;
    const subtreeWidth = widths.get(nodeId) ?? nodeSize(node).width;
    const size = nodeSize(node);
    const depth = depths.get(nodeId) ?? 0;
    positions.set(nodeId, { x: left + (subtreeWidth - size.width) / 2, y: levelTops.get(depth) ?? 0 });
    const childIds = children.get(nodeId) ?? [];
    const childrenWidth = childIds.length === 0 ? 0 : childIds.reduce((sum, childId) => sum + (widths.get(childId) ?? 0), 0) + gap * (childIds.length - 1);
    let childLeft = left + (subtreeWidth - childrenWidth) / 2;

    for(const childId of childIds) {
        placeSubtree(childId, childLeft, selected, children, depths, levelTops, widths, positions, gap);
        childLeft += (widths.get(childId) ?? 0) + gap;
    }
}

function nodeOrder(nodes: Node[], nodeId: string): number {
    const index = nodes.findIndex(node => node.id === nodeId);
    return index < 0 ? Number.MAX_SAFE_INTEGER : index;
}

function levelWidth(nodes: Node[], gap: number): number {
    if(nodes.length === 0) {
        return 0;
    }

    return nodes.reduce((sum, node) => sum + nodeSize(node).width, 0) + gap * (nodes.length - 1);
}

function nodeRectangle(node: Node): CanvasSelectionRectangle {
    const size = nodeSize(node);
    return { left: node.position.x, top: node.position.y, right: node.position.x + size.width, bottom: node.position.y + size.height, width: size.width, height: size.height };
}

function nodeSize(node: Node): { width: number; height: number } {
    const width = readDimension(node.measured?.width, node.width, node.style?.width, 1);
    const height = readDimension(node.measured?.height, node.height, node.style?.height, 1);
    return { width, height };
}

function intersects(first: CanvasSelectionRectangle, second: CanvasSelectionRectangle): boolean {
    return first.left <= second.right && first.right >= second.left && first.top <= second.bottom && first.bottom >= second.top;
}

function readDimension(measured: number | undefined, direct: number | undefined, styleValue: string | number | undefined, fallback: number): number {
    if(measured !== undefined && Number.isFinite(measured) && measured > 0) {
        return measured;
    }

    if(direct !== undefined && Number.isFinite(direct) && direct > 0) {
        return direct;
    }

    if(typeof styleValue === 'number' && Number.isFinite(styleValue) && styleValue > 0) {
        return styleValue;
    }

    if(typeof styleValue === 'string') {
        const parsed = Number.parseFloat(styleValue);

        if(Number.isFinite(parsed) && parsed > 0) {
            return parsed;
        }
    }

    return fallback;
}
