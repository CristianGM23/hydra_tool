import { BaseEdge, EdgeLabelRenderer, getBezierPath } from '@xyflow/react';
import type { EdgeProps } from '@xyflow/react';

import { resolveEdgeStyle, resolveStrokeDasharray } from '../visual-style/visualStyleUtils';
import type { MetamodelRelationEdgeData } from './diagram';

/**
 * Representa referencias, containment y herencia con marcadores UML.
 * Este componente mantiene compatibilidad con integraciones que todavía usan las conexiones nativas de React Flow.
 */
export function MetamodelRelationEdge(props: EdgeProps) {
    const data = props.data as MetamodelRelationEdgeData | undefined;
    const kind = data?.kind ?? 'REFERENCE';
    const name = data?.name ?? '';
    const multiplicity = data?.multiplicity ?? '';
    const visualStyle = resolveEdgeStyle(data?.visualStyle);
    const pathResult = getBezierPath({ sourceX: props.sourceX, sourceY: props.sourceY, sourcePosition: props.sourcePosition, targetX: props.targetX, targetY: props.targetY, targetPosition: props.targetPosition });
    const edgePath = pathResult[0];
    const labelX = pathResult[1];
    const labelY = pathResult[2];
    let markerStart: string | undefined;
    let markerEnd = 'url(#metamodel-reference-arrow)';
    let defaultDash: string | undefined;

    if(kind === 'CONTAINMENT') {
        markerStart = 'url(#metamodel-containment-diamond)';
    } else if(kind === 'AGGREGATION') {
        markerStart = 'url(#metamodel-aggregation-diamond)';
    } else if(kind === 'INHERITANCE') {
        markerEnd = 'url(#metamodel-inheritance-triangle)';
    } else if(kind === 'IMPLEMENTATION') {
        markerEnd = 'url(#metamodel-inheritance-triangle)';
        defaultDash = '8 5';
    } else if(kind === 'DEPENDENCY') {
        markerEnd = 'url(#metamodel-open-arrow)';
        defaultDash = '8 5';
    }

    return (
        <>
            {props.selected && <BaseEdge id={`${props.id}-selection`} interactionWidth={0} path={edgePath} style={{ stroke: '#2563eb', strokeOpacity: 0.35, strokeWidth: visualStyle.strokeWidth + 5 }} />}
            <BaseEdge id={props.id} interactionWidth={22} markerEnd={markerEnd} markerStart={markerStart} path={edgePath} style={{ stroke: visualStyle.strokeColor, strokeDasharray: resolveStrokeDasharray(visualStyle.lineStyle, defaultDash), strokeWidth: visualStyle.strokeWidth }} />
            <EdgeLabelRenderer>
                <div className="metamodel-edge-label nodrag nopan" style={{ color: visualStyle.textColor, fontFamily: visualStyle.fontFamily, fontSize: visualStyle.fontSize, fontWeight: visualStyle.fontWeight, transform: `translate(-50%, -50%) translate(${labelX}px, ${labelY}px)` }}>
                    {name !== '' && <span className="metamodel-edge-name">{name}</span>}
                    {multiplicity !== '' && <span className="metamodel-edge-multiplicity">{multiplicity}</span>}
                </div>
            </EdgeLabelRenderer>
        </>
    );
}

/**
 * Define los marcadores SVG utilizados por las relaciones.
 */
export function MetamodelRelationMarkers() {
    return (
        <svg className="metamodel-marker-definitions" aria-hidden="true">
            <defs>
                <marker id="metamodel-reference-arrow" markerHeight="10" markerUnits="strokeWidth" markerWidth="10" orient="auto" refX="9" refY="5" viewBox="0 0 10 10"><path d="M 0 0 L 10 5 L 0 10 z" fill="context-stroke" /></marker>
                <marker id="metamodel-open-arrow" markerHeight="12" markerUnits="strokeWidth" markerWidth="12" orient="auto" refX="11" refY="6" viewBox="0 0 12 12"><path d="M 1 1 L 11 6 L 1 11" fill="none" stroke="context-stroke" strokeWidth="1.5" /></marker>
                <marker id="metamodel-inheritance-triangle" markerHeight="12" markerUnits="strokeWidth" markerWidth="12" orient="auto" refX="11" refY="6" viewBox="0 0 12 12"><path d="M 1 1 L 11 6 L 1 11 z" fill="#ffffff" stroke="context-stroke" strokeWidth="1.5" /></marker>
                <marker id="metamodel-containment-diamond" markerHeight="12" markerUnits="strokeWidth" markerWidth="12" orient="auto" refX="1" refY="6" viewBox="0 0 12 12"><path d="M 1 6 L 6 1 L 11 6 L 6 11 z" fill="context-stroke" stroke="context-stroke" strokeWidth="1" /></marker>
                <marker id="metamodel-aggregation-diamond" markerHeight="12" markerUnits="strokeWidth" markerWidth="12" orient="auto" refX="1" refY="6" viewBox="0 0 12 12"><path d="M 1 6 L 6 1 L 11 6 L 6 11 z" fill="#ffffff" stroke="context-stroke" strokeWidth="1" /></marker>
            </defs>
        </svg>
    );
}
