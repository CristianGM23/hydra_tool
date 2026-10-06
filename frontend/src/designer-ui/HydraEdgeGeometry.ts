import { Position } from '@xyflow/react';
import type { NodeHandle } from '@xyflow/react';

const DEFAULT_NODE_WIDTH = 210;
const DEFAULT_NODE_HEIGHT = 84;
const DEFAULT_HANDLE_SIZE = 14;

export interface HydraHorizontalHandlesOptions {
    width?: number;
    height?: number;
    source?: boolean;
    target?: boolean;
    handleSize?: number;
}

/**
 * Provides deterministic handle geometry before ResizeObserver finishes its
 * first browser measurement. React Flow can therefore calculate existing
 * edges immediately instead of temporarily or permanently omitting them.
 */
export function createHydraHorizontalHandles(options: HydraHorizontalHandlesOptions = {}): NodeHandle[] {
    const width = finitePositive(options.width, DEFAULT_NODE_WIDTH);
    const height = finitePositive(options.height, DEFAULT_NODE_HEIGHT);
    const handleSize = finitePositive(options.handleSize, DEFAULT_HANDLE_SIZE);
    const y = Math.max(0, (height - handleSize) / 2);
    const result: NodeHandle[] = [];

    if (options.target !== false) result.push({
        id: 'target',
        type: 'target',
        position: Position.Left,
        x: -handleSize / 2,
        y,
        width: handleSize,
        height: handleSize
    });
    if (options.source !== false) result.push({
        id: 'source',
        type: 'source',
        position: Position.Right,
        x: width - handleSize / 2,
        y,
        width: handleSize,
        height: handleSize
    });

    return result;
}

function finitePositive(value: number | undefined, fallback: number): number {
    return typeof value === 'number' && Number.isFinite(value) && value > 0 ? value : fallback;
}
