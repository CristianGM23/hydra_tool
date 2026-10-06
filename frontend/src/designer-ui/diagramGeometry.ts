export interface DiagramGeometryNode {
    nodeId: string;
    x: number;
    y: number;
    width: number;
    height: number;
    collapsed: boolean;
}

/** Avoids rewriting a visual document when React Flow reports identical geometry. */
export function sameDiagramGeometry(current: readonly DiagramGeometryNode[], next: readonly DiagramGeometryNode[]): boolean {
    if (current.length !== next.length) return false;
    const byId = new Map(current.map(node => [node.nodeId, node]));
    return next.every(node => {
        const previous = byId.get(node.nodeId);
        return previous !== undefined && previous.x === node.x && previous.y === node.y
            && previous.width === node.width && previous.height === node.height && previous.collapsed === node.collapsed;
    });
}
