import { useRef } from 'react';
import type { MouseEvent as ReactMouseEvent, PointerEvent as ReactPointerEvent } from 'react';
import type { Edge, Node } from '@xyflow/react';

import { resolveEdgeStyle, resolveStrokeDasharray } from '../visual-style/visualStyleUtils';
import type { MetamodelRelationEdgeData } from './diagram';
import type { MetamodelConnectionKind, MetamodelSelection } from './types';
import { buildRelationGeometry, calculateHandlePoint } from './relationGeometry';
import type { MetamodelRelationGeometry } from './relationGeometry';

interface MetamodelRelationLayerProps {
    edges: Edge[];
    nodes: Node[];
    selection: MetamodelSelection;
    focusSelected: boolean;
    hideDependencies: boolean;
    selectedRelationIds?: string[];
    onSelect: (selection: MetamodelSelection) => void;
    onRoutePointMove?: (relationId: string, internalIndex: number, clientX: number, clientY: number) => void;
}

/**
 * Dibuja las relaciones directamente en el sistema de coordenadas del lienzo.
 *
 * @param props contiene nodos, relaciones y selección.
 * @returns capa SVG visible y seleccionable.
 */
export function MetamodelRelationLayer(props: MetamodelRelationLayerProps) {
    return (
        <svg className="metamodel-relation-layer" aria-label="Metamodel relationships" overflow="visible">
            <MetamodelRelationDefinitions />
            {props.edges.map(edge => {
                const sourceNode = props.nodes.find(node => node.id === edge.source);
                const targetNode = props.nodes.find(node => node.id === edge.target);

                if (sourceNode === undefined || targetNode === undefined) {
                    return null;
                }

                const sourcePoint = calculateHandlePoint(sourceNode, edge.sourceHandle, true);
                const targetPoint = calculateHandlePoint(targetNode, edge.targetHandle, false);
                const data = edge.data as MetamodelRelationEdgeData | undefined;

                if (props.hideDependencies && data?.kind === 'DEPENDENCY') {
                    return null;
                }

                const geometry = buildRelationGeometry(sourcePoint, targetPoint, data?.routePoints, data?.labelPosition);
                const selected = (props.selectedRelationIds ?? []).includes(edge.id) || isSelectedRelation(props.selection, edge);
                const dimmed = props.focusSelected && !isFocusedRelation(props.selection, edge);

                return <MetamodelRelationSvg data={data} dimmed={dimmed} edgeId={edge.id} geometry={geometry} key={edge.id} selected={selected} sourceHandle={edge.sourceHandle ?? ''} targetHandle={edge.targetHandle ?? ''} onRoutePointMove={props.onRoutePointMove === undefined ? undefined : (internalIndex, clientX, clientY) => props.onRoutePointMove?.(edge.id, internalIndex, clientX, clientY)} onSelect={() => selectRelation(edge, props.onSelect)} />;
            })}
        </svg>
    );
}

interface MetamodelRelationSvgProps {
    edgeId: string;
    data: MetamodelRelationEdgeData | undefined;
    geometry: MetamodelRelationGeometry;
    selected: boolean;
    dimmed: boolean;
    sourceHandle: string;
    targetHandle: string;
    onSelect: () => void;
    onRoutePointMove?: (internalIndex: number, clientX: number, clientY: number) => void;
}

/**
 * Renderiza una relación individual como SVG.
 *
 * @param props contiene la geometría y los datos semánticos.
 * @returns relación SVG.
 */
export function MetamodelRelationSvg(props: MetamodelRelationSvgProps) {
    const draggingPoint = useRef<{ pointerId: number; internalIndex: number } | null>(null);
    const kind = props.data?.kind ?? 'REFERENCE';
    const name = props.data?.name ?? '';
    const multiplicity = props.data?.multiplicity ?? '';
    const label = buildRelationLabel(kind, name, multiplicity);
    const visualStyle = resolveEdgeStyle(props.data?.visualStyle);
    const labelWidth = Math.max(54, label.length * visualStyle.fontSize * 0.64 + 16);
    const style = readRelationStyle(kind);
    const strokeDasharray = resolveStrokeDasharray(visualStyle.lineStyle, style.strokeDasharray);
    const selectedWidth = visualStyle.strokeWidth + 1;
    const pathClassName = kind === 'DEPENDENCY' ? 'metamodel-relation-path metamodel-dependency-path' : 'metamodel-relation-path';

    function selectFromPointer(event: ReactPointerEvent<SVGElement>): void {
        event.preventDefault();
        event.stopPropagation();
        props.onSelect();
    }

    function stopClickPropagation(event: ReactMouseEvent<SVGElement>): void {
        event.preventDefault();
        event.stopPropagation();
    }

    function startRoutePointDrag(event: ReactPointerEvent<SVGCircleElement>, internalIndex: number): void {
        event.preventDefault();
        event.stopPropagation();
        props.onSelect();
        draggingPoint.current = { pointerId: event.pointerId, internalIndex };
        capturePointer(event.currentTarget, event.pointerId);
    }

    function moveRoutePoint(event: ReactPointerEvent<SVGCircleElement>): void {
        if(draggingPoint.current === null || draggingPoint.current.pointerId !== event.pointerId) {
            return;
        }

        event.preventDefault();
        event.stopPropagation();
        props.onRoutePointMove?.(draggingPoint.current.internalIndex, event.clientX, event.clientY);
    }

    function finishRoutePointDrag(event: ReactPointerEvent<SVGCircleElement>): void {
        if(draggingPoint.current === null || draggingPoint.current.pointerId !== event.pointerId) {
            return;
        }

        event.preventDefault();
        event.stopPropagation();
        props.onRoutePointMove?.(draggingPoint.current.internalIndex, event.clientX, event.clientY);
        releasePointer(event.currentTarget, event.pointerId);
        draggingPoint.current = null;
    }

    return (
        <g className={props.selected ? 'metamodel-rendered-relation selected' : props.dimmed ? 'metamodel-rendered-relation dimmed' : 'metamodel-rendered-relation'} data-relation-id={props.edgeId} data-relation-kind={kind} data-source-handle={props.sourceHandle} data-target-handle={props.targetHandle}>
            <path aria-label={`Select relationship ${label || kind}`} className="metamodel-relation-hitbox" d={props.geometry.path} data-metamodel-relation-interaction="true" fill="none" onClick={stopClickPropagation} onPointerDown={selectFromPointer} stroke="transparent" strokeWidth={Math.max(18, visualStyle.strokeWidth + 14)} />
            {props.selected && <path className="metamodel-relation-selection-halo" d={props.geometry.path} fill="none" opacity={0.35} stroke="#2563eb" strokeLinecap="round" strokeWidth={selectedWidth + 4} vectorEffect="non-scaling-stroke" />}
            <path className={pathClassName} d={props.geometry.path} data-visible-path="true" fill="none" markerEnd={style.markerEnd} markerStart={style.markerStart} opacity={1} stroke={visualStyle.strokeColor} strokeDasharray={strokeDasharray} strokeLinecap="round" strokeWidth={visualStyle.strokeWidth} vectorEffect="non-scaling-stroke" visibility="visible" />
            {props.selected && props.geometry.points.slice(1, -1).map((point, index) => <circle aria-label={`Move routing point ${index + 1}`} className="metamodel-route-point-marker" cx={point.x} cy={point.y} data-metamodel-relation-interaction="true" data-route-point-index={index} key={`${props.edgeId}-route-point-${index}`} onClick={stopClickPropagation} onPointerCancel={finishRoutePointDrag} onPointerDown={event => startRoutePointDrag(event, index)} onPointerMove={moveRoutePoint} onPointerUp={finishRoutePointDrag} r={6} role="slider" tabIndex={0} />)}
            {label !== '' && (
                <g aria-label={`Select relationship label ${label}`} className="metamodel-relation-label" data-metamodel-relation-interaction="true" onClick={stopClickPropagation} onPointerDown={selectFromPointer} transform={`translate(${props.geometry.labelX} ${props.geometry.labelY})`}>
                    <rect height="22" rx="4" width={labelWidth} x={-labelWidth / 2} y="-11" />
                    <text dominantBaseline="middle" fill={visualStyle.textColor} fontFamily={visualStyle.fontFamily} fontSize={visualStyle.fontSize} fontWeight={visualStyle.fontWeight} textAnchor="middle">{label}</text>
                </g>
            )}
        </g>
    );
}

function capturePointer(element: SVGCircleElement, pointerId: number): void {
    const target = element as SVGCircleElement & { setPointerCapture?: (id: number) => void };
    target.setPointerCapture?.(pointerId);
}

function releasePointer(element: SVGCircleElement, pointerId: number): void {
    const target = element as SVGCircleElement & { hasPointerCapture?: (id: number) => boolean; releasePointerCapture?: (id: number) => void };

    if(target.hasPointerCapture === undefined || target.hasPointerCapture(pointerId)) {
        target.releasePointerCapture?.(pointerId);
    }
}

/**
 * Define flechas, triángulos y rombos utilizados por las relaciones.
 *
 * @returns definiciones SVG.
 */
export function MetamodelRelationDefinitions() {
    return (
        <defs>
            <marker id="metamodel-reference-arrow" markerHeight="10" markerUnits="userSpaceOnUse" markerWidth="10" orient="auto" refX="9" refY="5" viewBox="0 0 10 10">
                <path d="M 0 0 L 10 5 L 0 10 z" fill="context-stroke" />
            </marker>
            <marker id="metamodel-open-arrow" markerHeight="12" markerUnits="userSpaceOnUse" markerWidth="12" orient="auto" refX="11" refY="6" viewBox="0 0 12 12">
                <path d="M 1 1 L 11 6 L 1 11" fill="none" stroke="context-stroke" strokeWidth="1.5" />
            </marker>
            <marker id="metamodel-inheritance-triangle" markerHeight="14" markerUnits="userSpaceOnUse" markerWidth="14" orient="auto" refX="13" refY="7" viewBox="0 0 14 14">
                <path d="M 1 1 L 13 7 L 1 13 z" fill="#ffffff" stroke="context-stroke" strokeWidth="1.5" />
            </marker>
            <marker id="metamodel-containment-diamond" markerHeight="14" markerUnits="userSpaceOnUse" markerWidth="14" orient="auto" refX="1" refY="7" viewBox="0 0 14 14">
                <path d="M 1 7 L 7 1 L 13 7 L 7 13 z" fill="context-stroke" stroke="context-stroke" />
            </marker>
            <marker id="metamodel-aggregation-diamond" markerHeight="14" markerUnits="userSpaceOnUse" markerWidth="14" orient="auto" refX="1" refY="7" viewBox="0 0 14 14">
                <path d="M 1 7 L 7 1 L 13 7 L 7 13 z" fill="#ffffff" stroke="context-stroke" strokeWidth="1.5" />
            </marker>
        </defs>
    );
}

interface RelationStyle {
    markerStart?: string;
    markerEnd?: string;
    strokeDasharray?: string;
}

/**
 * Devuelve los marcadores y línea de cada relación.
 *
 * @param kind identifica la relación.
 * @returns estilo SVG.
 */
function readRelationStyle(kind: MetamodelConnectionKind): RelationStyle {
    if (kind === 'AGGREGATION') {
        return { markerStart: 'url(#metamodel-aggregation-diamond)', markerEnd: 'url(#metamodel-reference-arrow)' };
    }

    if (kind === 'CONTAINMENT') {
        return { markerStart: 'url(#metamodel-containment-diamond)', markerEnd: 'url(#metamodel-reference-arrow)' };
    }

    if (kind === 'DEPENDENCY') {
        return { markerEnd: 'url(#metamodel-open-arrow)', strokeDasharray: '8 5' };
    }

    if (kind === 'INHERITANCE') {
        return { markerEnd: 'url(#metamodel-inheritance-triangle)' };
    }

    if (kind === 'IMPLEMENTATION') {
        return { markerEnd: 'url(#metamodel-inheritance-triangle)', strokeDasharray: '8 5' };
    }

    return { markerEnd: 'url(#metamodel-reference-arrow)' };
}

/**
 * Selecciona la relación pulsada.
 *
 * @param edge contiene la relación.
 * @param data contiene los metadatos.
 * @param onSelect informa de la selección.
 * @returns no devuelve ningún valor.
 */
function selectRelation(edge: Edge, onSelect: (selection: MetamodelSelection) => void): void {
    onSelect({ kind: 'RELATION', id: edge.id });
}

function isSelectedRelation(selection: MetamodelSelection, edge: Edge): boolean {
    return selection.kind === 'RELATION' && selection.id === edge.id;
}

function isFocusedRelation(selection: MetamodelSelection, edge: Edge): boolean {
    if (selection.kind === 'CLASS') return edge.source === selection.id || edge.target === selection.id;
    if (selection.kind === 'RELATION') return edge.id === selection.id;
    return true;
}

/**
 * Construye el texto visible de una relación.
 *
 * @param kind identifica la relación.
 * @param name contiene el nombre.
 * @param multiplicity contiene la cardinalidad.
 * @returns etiqueta compuesta.
 */
function buildRelationLabel(kind: MetamodelConnectionKind, name: string, multiplicity: string): string {
    if (kind === 'INHERITANCE' || kind === 'IMPLEMENTATION') {
        return '';
    }

    if (name === '') {
        return multiplicity;
    }

    if (multiplicity === '') {
        return name;
    }

    return `${name} ${multiplicity}`;
}

