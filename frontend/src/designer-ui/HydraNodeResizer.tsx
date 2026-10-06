import { NodeResizer } from '@xyflow/react';
import { HYDRA_NODE_RESIZE_DEFAULTS } from './hydraNodeResizeDefaults';

interface HydraNodeResizerProps {
    visible: boolean;
    minWidth?: number;
    minHeight?: number;
    maxWidth?: number;
    maxHeight?: number;
}

/**
 * Tiradores de tamaño comunes para todos los lienzos HYDRA basados en React Flow.
 * Mantiene mínimos legibles y deja que cada diagrama persista las dimensiones
 * mediante sus cambios `dimensions` persistidos.
 */
export function HydraNodeResizer({ visible, minWidth = HYDRA_NODE_RESIZE_DEFAULTS.minWidth, minHeight = HYDRA_NODE_RESIZE_DEFAULTS.minHeight, maxWidth, maxHeight }: HydraNodeResizerProps) {
    return <NodeResizer
        isVisible={visible}
        minWidth={minWidth}
        minHeight={minHeight}
        maxWidth={maxWidth}
        maxHeight={maxHeight}
        handleClassName="hydra-node-resize-handle"
        lineClassName="hydra-node-resize-line"
    />;
}
