import type { CSSProperties } from 'react';
import { BaseEdge, getBezierPath, getSmoothStepPath, getStraightPath } from '@xyflow/react';
import type { Edge, EdgeProps } from '@xyflow/react';

import { DEFAULT_EDGE_STYLE, getMarkerUrl } from '../notation/notationUtils';
import type { EdgeNotationStyle } from '../types/editor';

export interface SemanticEdgeData extends Record<string, unknown> {
    label: string;
    notation: EdgeNotationStyle;
}

export type SemanticFlowEdge = Edge<SemanticEdgeData, 'semantic'>;

/**
 * Representa una relación con trazado y extremos configurables.
 *
 * @param props contiene las coordenadas y los datos de la conexión.
 * @returns conexión gráfica personalizada.
 */
export function SemanticEdge(props: EdgeProps<SemanticFlowEdge>) {
    const notation = props.data?.notation ?? DEFAULT_EDGE_STYLE;
    const label = props.data?.label ?? '';
    let edgePath: string;

    if (notation.lineType === 'STRAIGHT') {
        [edgePath] = getStraightPath({ sourceX: props.sourceX, sourceY: props.sourceY, targetX: props.targetX, targetY: props.targetY });
    } else if (notation.lineType === 'STEP') {
        [edgePath] = getSmoothStepPath({ sourceX: props.sourceX, sourceY: props.sourceY, sourcePosition: props.sourcePosition, targetX: props.targetX, targetY: props.targetY, targetPosition: props.targetPosition, borderRadius: 0 });
    } else if (notation.lineType === 'SMOOTH_STEP') {
        [edgePath] = getSmoothStepPath({ sourceX: props.sourceX, sourceY: props.sourceY, sourcePosition: props.sourcePosition, targetX: props.targetX, targetY: props.targetY, targetPosition: props.targetPosition, borderRadius: 10 });
    } else {
        [edgePath] = getBezierPath({ sourceX: props.sourceX, sourceY: props.sourceY, sourcePosition: props.sourcePosition, targetX: props.targetX, targetY: props.targetY, targetPosition: props.targetPosition });
    }

    const style: CSSProperties = {
        stroke: notation.strokeColor,
        strokeWidth: notation.strokeWidth,
        strokeDasharray: notation.strokeDash
    };

    return <BaseEdge id={props.id} label={label} markerEnd={getMarkerUrl(notation.endMarker)} markerStart={getMarkerUrl(notation.startMarker)} path={edgePath} style={style} />;
}