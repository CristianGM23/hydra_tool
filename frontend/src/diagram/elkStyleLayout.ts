/**
 * ELK-style layered layout shared by every HYDRA canvas.
 *
 * The engine follows the same high-level principles as ELK Layered/Sugiyama:
 * strongly-connected components are collapsed, the resulting DAG is layered,
 * neighbour barycentres reduce crossings, and every layer is spaced using the
 * actual node dimensions. It is dependency-free so HYDRA can use the same
 * deterministic layout in the browser, tests and exported runtimes.
 */
export type HydraElkLayoutDirection = 'RIGHT' | 'DOWN';

export interface HydraElkLayoutNode {
    id: string;
    width: number;
    height: number;
    parentId?: string;
}

export interface HydraElkLayoutEdge {
    id: string;
    source: string;
    target: string;
}

export interface HydraElkLayoutOptions {
    direction?: HydraElkLayoutDirection;
    nodeGap?: number;
    layerGap?: number;
    componentGap?: number;
    padding?: number;
    containerHeader?: number;
}

export interface HydraElkLayoutPlacement {
    x: number;
    y: number;
    width: number;
    height: number;
    parentId?: string;
}

const DEFAULTS: Required<HydraElkLayoutOptions> = {
    direction: 'RIGHT', nodeGap: 56, layerGap: 110, componentGap: 120, padding: 28, containerHeader: 30
};

/** Produces non-overlapping deterministic positions for a node-link graph. */
export function layoutHydraElkStyle(nodes: readonly HydraElkLayoutNode[], edges: readonly HydraElkLayoutEdge[], requested: HydraElkLayoutOptions = {}): Map<string, HydraElkLayoutPlacement> {
    const options = { ...DEFAULTS, ...requested };
    const normalized = nodes.map(node => ({ ...node, width: finiteSize(node.width, 180), height: finiteSize(node.height, 90) }));
    const byId = new Map(normalized.map(node => [node.id, node]));
    const childrenByParent = new Map<string, HydraElkLayoutNode[]>();
    for (const node of normalized) if (node.parentId && byId.has(node.parentId)) childrenByParent.set(node.parentId, [...(childrenByParent.get(node.parentId) ?? []), node]);

    const placements = new Map<string, HydraElkLayoutPlacement>();
    const prepared = new Map<string, HydraElkLayoutNode>();
    const prepare = (node: HydraElkLayoutNode): HydraElkLayoutNode => {
        const children = childrenByParent.get(node.id) ?? [];
        if (children.length === 0) { prepared.set(node.id, node); return node; }
        const childIds = new Set(children.map(child => child.id));
        const childEdges = edges.filter(edge => childIds.has(edge.source) && childIds.has(edge.target));
        const childLayout = layoutFlat(children.map(child => prepare(child)), childEdges, options);
        const bounds = placementBounds(childLayout);
        const width = Math.max(node.width, bounds.width + options.padding * 2);
        const height = Math.max(node.height, bounds.height + options.padding * 2 + options.containerHeader);
        for (const child of children) {
            const placed = childLayout.get(child.id);
            if (!placed) continue;
            placements.set(child.id, { ...placed, x: placed.x - bounds.minX + options.padding, y: placed.y - bounds.minY + options.padding + options.containerHeader, parentId: node.id });
        }
        const resized = { ...node, width, height };
        prepared.set(node.id, resized);
        return resized;
    };

    const roots = normalized.filter(node => !node.parentId || !byId.has(node.parentId)).map(prepare);
    const rootOf = (id: string): string => {
        let current = byId.get(id); const visited = new Set<string>();
        while (current?.parentId && byId.has(current.parentId) && !visited.has(current.id)) { visited.add(current.id); current = byId.get(current.parentId); }
        return current?.id ?? id;
    };
    const rootIds = new Set(roots.map(node => node.id));
    const rootEdges: HydraElkLayoutEdge[] = [];
    const seenRootEdges = new Set<string>();
    for (const edge of edges) {
        const source = rootOf(edge.source), target = rootOf(edge.target);
        if (source === target || !rootIds.has(source) || !rootIds.has(target)) continue;
        const key = `${source}\u0000${target}`;
        if (seenRootEdges.add(key)) rootEdges.push({ id: edge.id, source, target });
    }
    const rootLayout = layoutFlat(roots, rootEdges, options);
    for (const node of roots) {
        const placed = rootLayout.get(node.id);
        if (placed) placements.set(node.id, placed);
    }
    return placements;
}

function layoutFlat(nodes: readonly HydraElkLayoutNode[], edges: readonly HydraElkLayoutEdge[], options: Required<HydraElkLayoutOptions>): Map<string, HydraElkLayoutPlacement> {
    const byId = new Map(nodes.map(node => [node.id, node]));
    const validEdges = edges.filter(edge => byId.has(edge.source) && byId.has(edge.target) && edge.source !== edge.target);
    const components = weakComponents(nodes, validEdges);
    const result = new Map<string, HydraElkLayoutPlacement>();
    let componentOffset = 0;
    for (const component of components) {
        const ids = new Set(component.map(node => node.id));
        const componentEdges = validEdges.filter(edge => ids.has(edge.source) && ids.has(edge.target));
        const local = layeredComponent(component, componentEdges, options);
        const bounds = placementBounds(local);
        for (const [id, placement] of local) {
            const shifted = options.direction === 'RIGHT'
                ? { ...placement, x: placement.x - bounds.minX, y: placement.y - bounds.minY + componentOffset }
                : { ...placement, x: placement.x - bounds.minX + componentOffset, y: placement.y - bounds.minY };
            result.set(id, shifted);
        }
        componentOffset += (options.direction === 'RIGHT' ? bounds.height : bounds.width) + options.componentGap;
    }
    return result;
}

function layeredComponent(nodes: readonly HydraElkLayoutNode[], edges: readonly HydraElkLayoutEdge[], options: Required<HydraElkLayoutOptions>): Map<string, HydraElkLayoutPlacement> {
    const ids = nodes.map(node => node.id).sort();
    const sccs = stronglyConnectedComponents(ids, edges);
    const componentOf = new Map<string, number>();
    sccs.forEach((group, index) => group.forEach(id => componentOf.set(id, index)));
    const dagIncoming = new Map<number, Set<number>>(), dagOutgoing = new Map<number, Set<number>>();
    sccs.forEach((_group, index) => { dagIncoming.set(index, new Set()); dagOutgoing.set(index, new Set()); });
    for (const edge of edges) {
        const source = componentOf.get(edge.source), target = componentOf.get(edge.target);
        if (source === undefined || target === undefined || source === target) continue;
        dagOutgoing.get(source)!.add(target); dagIncoming.get(target)!.add(source);
    }
    const componentLayer = longestPathLayers(sccs.length, dagIncoming, dagOutgoing);
    const layerOf = new Map<string, number>();
    sccs.forEach((group, index) => group.forEach(id => layerOf.set(id, componentLayer.get(index) ?? 0)));
    const layers = new Map<number, HydraElkLayoutNode[]>();
    for (const node of nodes) { const layer = layerOf.get(node.id) ?? 0; layers.set(layer, [...(layers.get(layer) ?? []), node]); }
    const orderedLayers = [...layers.keys()].sort((a, b) => a - b);
    barycentricOrder(layers, orderedLayers, edges);

    const primaryOffsets = new Map<number, number>();
    let primary = 0;
    for (const layer of orderedLayers) {
        primaryOffsets.set(layer, primary);
        const maximum = Math.max(...(layers.get(layer) ?? []).map(node => options.direction === 'RIGHT' ? node.width : node.height), 0);
        primary += maximum + options.layerGap;
    }
    const result = new Map<string, HydraElkLayoutPlacement>();
    for (const layer of orderedLayers) {
        let secondary = 0;
        for (const node of layers.get(layer) ?? []) {
            const x = options.direction === 'RIGHT' ? primaryOffsets.get(layer)! : secondary;
            const y = options.direction === 'RIGHT' ? secondary : primaryOffsets.get(layer)!;
            result.set(node.id, { x, y, width: node.width, height: node.height, parentId: node.parentId });
            secondary += (options.direction === 'RIGHT' ? node.height : node.width) + options.nodeGap;
        }
    }
    avoidEdgeNodeIntersections(result, edges, options);
    return result;
}

function avoidEdgeNodeIntersections(placements: Map<string, HydraElkLayoutPlacement>, edges: readonly HydraElkLayoutEdge[], options: Required<HydraElkLayoutOptions>): void {
    if (placements.size < 3 || edges.length === 0) return;
    const clearance = Math.max(18, Math.min(42, Math.round(options.nodeGap * 0.42)));
    const maxPasses = Math.min(12, Math.max(4, Math.ceil(Math.log2(placements.size + 1)) + 3));
    for (let pass = 0; pass < maxPasses; pass += 1) {
        let changed = false;
        for (const edge of edges) {
            const source = placements.get(edge.source), target = placements.get(edge.target);
            if (!source || !target || edge.source === edge.target) continue;
            const segments = orthogonalSegments(source, target, options.direction);
            for (const [id, obstacle] of placements) {
                if (id === edge.source || id === edge.target) continue;
                const collision = segments.some(segment => segmentIntersectsRect(segment, obstacle, clearance));
                if (!collision) continue;
                const delta = minimumSecondaryEscape(obstacle, segments, options.direction, clearance);
                if (Math.abs(delta) < 0.5) continue;
                if (options.direction === 'RIGHT') obstacle.y = Math.max(0, obstacle.y + delta);
                else obstacle.x = Math.max(0, obstacle.x + delta);
                changed = true;
            }
        }
        if (changed) separateSecondaryOverlaps(placements, options);
        else break;
    }
}

type HydraOrthogonalSegment = { x1:number; y1:number; x2:number; y2:number };
function orthogonalSegments(source: HydraElkLayoutPlacement, target: HydraElkLayoutPlacement, direction: HydraElkLayoutDirection): HydraOrthogonalSegment[] {
    if (direction === 'RIGHT') {
        const x1 = source.x + source.width, y1 = source.y + source.height / 2;
        const x2 = target.x, y2 = target.y + target.height / 2;
        const middle = (x1 + x2) / 2;
        return [{x1,y1,x2:middle,y2:y1},{x1:middle,y1,x2:middle,y2},{x1:middle,y1:y2,x2,y2}];
    }
    const x1 = source.x + source.width / 2, y1 = source.y + source.height;
    const x2 = target.x + target.width / 2, y2 = target.y;
    const middle = (y1 + y2) / 2;
    return [{x1,y1,x2:x1,y2:middle},{x1,y1:middle,x2,y2:middle},{x1:x2,y1:middle,x2,y2}];
}

function segmentIntersectsRect(segment: HydraOrthogonalSegment, rect: HydraElkLayoutPlacement, clearance: number): boolean {
    const left = rect.x - clearance, right = rect.x + rect.width + clearance;
    const top = rect.y - clearance, bottom = rect.y + rect.height + clearance;
    if (Math.abs(segment.y1 - segment.y2) < 0.001) {
        const y = segment.y1, minX = Math.min(segment.x1, segment.x2), maxX = Math.max(segment.x1, segment.x2);
        return y >= top && y <= bottom && maxX >= left && minX <= right;
    }
    if (Math.abs(segment.x1 - segment.x2) < 0.001) {
        const x = segment.x1, minY = Math.min(segment.y1, segment.y2), maxY = Math.max(segment.y1, segment.y2);
        return x >= left && x <= right && maxY >= top && minY <= bottom;
    }
    return false;
}

function minimumSecondaryEscape(rect: HydraElkLayoutPlacement, segments: readonly HydraOrthogonalSegment[], direction: HydraElkLayoutDirection, clearance: number): number {
    let min = Number.POSITIVE_INFINITY, max = Number.NEGATIVE_INFINITY;
    for (const segment of segments) {
        if (direction === 'RIGHT') { min = Math.min(min, segment.y1, segment.y2); max = Math.max(max, segment.y1, segment.y2); }
        else { min = Math.min(min, segment.x1, segment.x2); max = Math.max(max, segment.x1, segment.x2); }
    }
    if (!Number.isFinite(min) || !Number.isFinite(max)) return 0;
    const start = direction === 'RIGHT' ? rect.y : rect.x;
    const size = direction === 'RIGHT' ? rect.height : rect.width;
    const before = min - clearance - size - start;
    const after = max + clearance - start;
    if (start + before >= 0 && Math.abs(before) <= Math.abs(after)) return before;
    return after;
}

function separateSecondaryOverlaps(placements: Map<string, HydraElkLayoutPlacement>, options: Required<HydraElkLayoutOptions>): void {
    const groups = new Map<number, HydraElkLayoutPlacement[]>();
    for (const placement of placements.values()) {
        const key = Math.round(options.direction === 'RIGHT' ? placement.x : placement.y);
        groups.set(key, [...(groups.get(key) ?? []), placement]);
    }
    for (const values of groups.values()) {
        values.sort((a,b) => (options.direction === 'RIGHT' ? a.y-b.y : a.x-b.x));
        let cursor = 0;
        for (const placement of values) {
            const current = options.direction === 'RIGHT' ? placement.y : placement.x;
            const next = Math.max(current, cursor);
            if (options.direction === 'RIGHT') placement.y = next; else placement.x = next;
            cursor = next + (options.direction === 'RIGHT' ? placement.height : placement.width) + options.nodeGap;
        }
    }
}

function weakComponents(nodes: readonly HydraElkLayoutNode[], edges: readonly HydraElkLayoutEdge[]): HydraElkLayoutNode[][] {
    const neighbours = new Map(nodes.map(node => [node.id, new Set<string>()]));
    for (const edge of edges) { neighbours.get(edge.source)?.add(edge.target); neighbours.get(edge.target)?.add(edge.source); }
    const byId = new Map(nodes.map(node => [node.id, node])), remaining = new Set(nodes.map(node => node.id));
    const result: HydraElkLayoutNode[][] = [];
    while (remaining.size) {
        const seed = [...remaining].sort()[0]!; const queue = [seed], group: HydraElkLayoutNode[] = []; remaining.delete(seed);
        while (queue.length) { const id = queue.shift()!; const node = byId.get(id); if (node) group.push(node); for (const next of neighbours.get(id) ?? []) if (remaining.delete(next)) queue.push(next); }
        result.push(group.sort((a, b) => a.id.localeCompare(b.id)));
    }
    return result;
}

function stronglyConnectedComponents(ids: readonly string[], edges: readonly HydraElkLayoutEdge[]): string[][] {
    // Iterative Kosaraju avoids overflowing the JavaScript stack on very deep
    // grammars/model graphs while keeping O(V+E) memory/time and deterministic ordering.
    const sortedIds = [...ids].sort();
    const outgoing = new Map(sortedIds.map(id => [id, [] as string[]]));
    const incoming = new Map(sortedIds.map(id => [id, [] as string[]]));
    for (const edge of edges) {
        if (!outgoing.has(edge.source) || !outgoing.has(edge.target)) continue;
        outgoing.get(edge.source)!.push(edge.target);
        incoming.get(edge.target)!.push(edge.source);
    }
    for (const values of outgoing.values()) values.sort();
    for (const values of incoming.values()) values.sort();

    const visited = new Set<string>();
    const finish: string[] = [];
    for (const seed of sortedIds) {
        if (visited.has(seed)) continue;
        visited.add(seed);
        const stack: Array<{ id: string; next: number }> = [{ id: seed, next: 0 }];
        while (stack.length > 0) {
            const frame = stack[stack.length - 1]!;
            const neighbours = outgoing.get(frame.id) ?? [];
            if (frame.next < neighbours.length) {
                const target = neighbours[frame.next]!;
                frame.next += 1;
                if (!visited.has(target)) { visited.add(target); stack.push({ id: target, next: 0 }); }
                continue;
            }
            finish.push(frame.id);
            stack.pop();
        }
    }

    const assigned = new Set<string>();
    const result: string[][] = [];
    for (let index = finish.length - 1; index >= 0; index -= 1) {
        const seed = finish[index]!;
        if (assigned.has(seed)) continue;
        const group: string[] = [];
        const stack = [seed];
        assigned.add(seed);
        while (stack.length > 0) {
            const id = stack.pop()!;
            group.push(id);
            const neighbours = incoming.get(id) ?? [];
            // Push in reverse order so lexical order is visited first after pop.
            for (let neighbourIndex = neighbours.length - 1; neighbourIndex >= 0; neighbourIndex -= 1) {
                const target = neighbours[neighbourIndex]!;
                if (!assigned.has(target)) { assigned.add(target); stack.push(target); }
            }
        }
        result.push(group.sort());
    }
    return result;
}

function longestPathLayers(count: number, incoming: Map<number, Set<number>>, outgoing: Map<number, Set<number>>): Map<number, number> {
    const indegree = new Map<number, number>(); for (let i = 0; i < count; i++) indegree.set(i, incoming.get(i)?.size ?? 0);
    const queue = [...indegree].filter(([, value]) => value === 0).map(([id]) => id).sort((a, b) => a - b), layers = new Map<number, number>();
    for (const id of queue) layers.set(id, 0);
    while (queue.length) {
        const source = queue.shift()!; const layer = layers.get(source) ?? 0;
        for (const target of [...(outgoing.get(source) ?? [])].sort((a, b) => a - b)) {
            layers.set(target, Math.max(layers.get(target) ?? 0, layer + 1)); indegree.set(target, (indegree.get(target) ?? 1) - 1);
            if (indegree.get(target) === 0) { queue.push(target); queue.sort((a, b) => a - b); }
        }
    }
    for (let i = 0; i < count; i++) if (!layers.has(i)) layers.set(i, 0);
    return layers;
}

function barycentricOrder(layers: Map<number, HydraElkLayoutNode[]>, orderedLayers: number[], edges: readonly HydraElkLayoutEdge[]): void {
    const incoming = new Map<string, string[]>(), outgoing = new Map<string, string[]>();
    for (const edge of edges) { incoming.set(edge.target, [...(incoming.get(edge.target) ?? []), edge.source]); outgoing.set(edge.source, [...(outgoing.get(edge.source) ?? []), edge.target]); }
    const sweep = (sequence: number[], neighbours: Map<string, string[]>): void => {
        // Keep one mutable position index for the whole sweep. Rebuilding the
        // complete index for every layer was O(V * layers) and became quadratic
        // for deep class hierarchies/grammars with thousands of layers.
        const pos = new Map<string, number>();
        for (const layer of layers.values()) layer.forEach((node, index) => pos.set(node.id, index));
        for (const layerId of sequence) {
            const current = layers.get(layerId) ?? [];
            current.sort((a, b) => barycentre(a.id, neighbours, pos) - barycentre(b.id, neighbours, pos) || a.id.localeCompare(b.id));
            current.forEach((node, index) => pos.set(node.id, index));
        }
    };
    sweep(orderedLayers.slice(1), incoming); sweep([...orderedLayers].reverse().slice(1), outgoing); sweep(orderedLayers.slice(1), incoming);
}

function barycentre(id: string, neighbours: Map<string, string[]>, positions: Map<string, number>): number {
    const values = (neighbours.get(id) ?? []).map(value => positions.get(value)).filter((value): value is number => value !== undefined);
    return values.length === 0 ? Number.MAX_SAFE_INTEGER / 2 : values.reduce((sum, value) => sum + value, 0) / values.length;
}

function placementBounds(placements: Map<string, HydraElkLayoutPlacement>): { minX: number; minY: number; width: number; height: number } {
    if (placements.size === 0) return { minX: 0, minY: 0, width: 0, height: 0 };
    const values = [...placements.values()], minX = Math.min(...values.map(item => item.x)), minY = Math.min(...values.map(item => item.y));
    const maxX = Math.max(...values.map(item => item.x + item.width)), maxY = Math.max(...values.map(item => item.y + item.height));
    return { minX, minY, width: maxX - minX, height: maxY - minY };
}

function finiteSize(value: number, fallback: number): number { return Number.isFinite(value) && value > 0 ? value : fallback; }
