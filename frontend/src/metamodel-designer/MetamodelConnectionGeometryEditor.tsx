import { HydraButton } from '../design-system/HydraButton';
import { useState } from 'react';
import { HydraAdaptiveSelect } from '../components/HydraAdaptiveSelect';

import { buildMetamodelPortHandle, calculateDiagramHandlePoint, createEditableOrthogonalRoute, moveEditableOrthogonalRoutePoint, normalizeEditableOrthogonalRoute, parseMetamodelPortHandle, readInternalRoutePoints } from './connectionGeometry';
import type { MetamodelPortSide, OrthogonalRouteStrategy } from './connectionGeometry';
import { ValidatedNumberInput } from './ValidatedNumberInput';
import type { MetamodelDiagramDocument, MetamodelDiagramEdge, MetamodelHandleRole } from './types';

interface MetamodelConnectionGeometryEditorProps {
    relationId: string;
    sourceClassId: string;
    targetClassId: string;
    sourceLabel: string;
    targetLabel: string;
    diagram: MetamodelDiagramDocument;
    onChange: (diagram: MetamodelDiagramDocument) => void;
}

type ConnectionRoutingMode = 'NORMAL' | 'ORTHOGONAL';

/**
 * Edita la forma de una conexión sin mezclarla con sus propiedades semánticas.
 * Permite elegir una curva normal o una ruta ortogonal, cambiar ambos puertos y ajustar puntos intermedios.
 */
export function MetamodelConnectionGeometryEditor(props: MetamodelConnectionGeometryEditorProps) {
    const [strategy, setStrategy] = useState<OrthogonalRouteStrategy>('HORIZONTAL_FIRST');
    const edge = readEffectiveEdge(props);
    const sourceNode = props.diagram.nodes.find(node => node.elementId === props.sourceClassId);
    const targetNode = props.diagram.nodes.find(node => node.elementId === props.targetClassId);
    const routePoints = edge.routePoints ?? [];
    const routingMode: ConnectionRoutingMode = routePoints.length >= 2 ? 'ORTHOGONAL' : 'NORMAL';
    const sourcePort = parseMetamodelPortHandle(edge.sourceHandle, 'SOURCE');
    const targetPort = parseMetamodelPortHandle(edge.targetHandle, 'TARGET');
    const internalPoints = readInternalRoutePoints(routePoints);

    function changeRoutingMode(mode: ConnectionRoutingMode): void {
        if(mode === 'NORMAL') {
            updateEdge(props, { routePoints: [], labelPosition: undefined });
            return;
        }

        if(sourceNode === undefined || targetNode === undefined) {
            return;
        }

        updateEdge(props, { routePoints: createEditableOrthogonalRoute(sourceNode, targetNode, edge.sourceHandle, edge.targetHandle, strategy), labelPosition: undefined });
    }

    function rebuildRoute(nextStrategy: OrthogonalRouteStrategy): void {
        setStrategy(nextStrategy);

        if(sourceNode === undefined || targetNode === undefined) {
            return;
        }

        updateEdge(props, { routePoints: createEditableOrthogonalRoute(sourceNode, targetNode, edge.sourceHandle, edge.targetHandle, nextStrategy), labelPosition: undefined });
    }

    function changePort(role: MetamodelHandleRole, side: MetamodelPortSide, percent: number): void {
        const sourceHandle = role === 'SOURCE' ? buildMetamodelPortHandle(side, percent, role) : edge.sourceHandle;
        const targetHandle = role === 'TARGET' ? buildMetamodelPortHandle(side, percent, role) : edge.targetHandle;
        const patch: Partial<MetamodelDiagramEdge> = role === 'SOURCE' ? { sourceHandle } : { targetHandle };

        if(routingMode === 'ORTHOGONAL' && sourceNode !== undefined && targetNode !== undefined) {
            patch.routePoints = createEditableOrthogonalRoute(sourceNode, targetNode, sourceHandle, targetHandle, strategy);
            patch.labelPosition = undefined;
        }

        updateEdge(props, patch);
    }

    function updateInternalPoint(index: number, coordinate: 'x' | 'y', value: number): void {
        if(sourceNode === undefined || targetNode === undefined || routePoints.length < 3) {
            return;
        }

        const synchronizedRoute = routePoints.map(point => ({ ...point }));
        synchronizedRoute[0] = calculateDiagramHandlePoint(sourceNode, edge.sourceHandle, 'SOURCE');
        synchronizedRoute[synchronizedRoute.length - 1] = calculateDiagramHandlePoint(targetNode, edge.targetHandle, 'TARGET');
        const point = synchronizedRoute[index + 1];
        const nextRoute = moveEditableOrthogonalRoutePoint(synchronizedRoute, index, { ...point, [coordinate]: value });
        updateEdge(props, { routePoints: nextRoute, labelPosition: undefined });
    }

    function addRoutingPoint(): void {
        if(sourceNode === undefined || targetNode === undefined) {
            return;
        }

        const baseRoute = routePoints.length >= 2 ? routePoints : createEditableOrthogonalRoute(sourceNode, targetNode, edge.sourceHandle, edge.targetHandle, strategy);
        const source = baseRoute[0];
        const target = baseRoute[baseRoute.length - 1];
        const point = { x: (source.x + target.x) / 2, y: (source.y + target.y) / 2 };
        const normalized = normalizeEditableOrthogonalRoute([source, ...readInternalRoutePoints(baseRoute), point, target], strategy);
        updateEdge(props, { routePoints: normalized, labelPosition: undefined });
    }

    function removeRoutingPoint(index: number): void {
        if(sourceNode === undefined || targetNode === undefined || routePoints.length < 2) {
            return;
        }

        const nextInternalPoints = internalPoints.filter((_point, pointIndex) => pointIndex !== index);
        const normalized = normalizeEditableOrthogonalRoute([routePoints[0], ...nextInternalPoints, routePoints[routePoints.length - 1]], strategy);
        updateEdge(props, { routePoints: normalized, labelPosition: undefined });
    }

    return (
        <section className="metamodel-geometry-editor metamodel-connection-geometry-editor">
            <div className="metamodel-geometry-heading"><div><h3>Connection geometry</h3><span>{props.sourceLabel} → {props.targetLabel}</span></div></div>
            <label>Routing<HydraAdaptiveSelect ariaLabel="Connection routing" value={routingMode} options={[
                { value: 'NORMAL', label: 'Normal curve', description: 'Draws the relation as a direct smooth connection without editable intermediate points.', category: 'Simple routing' },
                { value: 'ORTHOGONAL', label: 'Orthogonal', description: 'Uses horizontal and vertical segments whose intermediate points can be edited.', category: 'Editable routing' }
            ]} onChange={next => changeRoutingMode(next as ConnectionRoutingMode)} /></label>
            <div className="metamodel-port-editor-grid">
                <PortEditor label="Source port" percent={sourcePort.percent} side={sourcePort.side} onChange={(side, percent) => changePort('SOURCE', side, percent)} />
                <PortEditor label="Target port" percent={targetPort.percent} side={targetPort.side} onChange={(side, percent) => changePort('TARGET', side, percent)} />
            </div>
            {routingMode === 'ORTHOGONAL' && <>
                <div className="metamodel-route-actions">
                    <HydraButton type="button" onClick={() => rebuildRoute('HORIZONTAL_FIRST')}>Horizontal first</HydraButton>
                    <HydraButton type="button" onClick={() => rebuildRoute('VERTICAL_FIRST')}>Vertical first</HydraButton>
                    <HydraButton type="button" onClick={addRoutingPoint}>Add routing point</HydraButton>
                </div>
                <div className="metamodel-route-point-list">
                    {internalPoints.length === 0 && <p className="metamodel-empty-property">No intermediate routing points.</p>}
                    {internalPoints.map((point, index) => <div className="metamodel-route-point-row" key={`${index}-${point.x}-${point.y}`}>
                        <strong>Point {index + 1}</strong>
                        <ValidatedNumberInput label="X (px)" value={point.x} onChange={value => updateInternalPoint(index, 'x', value)} />
                        <ValidatedNumberInput label="Y (px)" value={point.y} onChange={value => updateInternalPoint(index, 'y', value)} />
                        <HydraButton className="metamodel-delete-inline-button" type="button" onClick={() => removeRoutingPoint(index)}>Remove</HydraButton>
                    </div>)}
                </div>
                <p className="metamodel-field-help">Routing points use canvas coordinates in pixels. Diagonal changes are automatically expanded into horizontal and vertical segments.</p>
            </>}
            {(sourceNode === undefined || targetNode === undefined) && <p className="metamodel-validation-error">Both endpoint classes need a diagram position before an orthogonal route can be generated.</p>}
        </section>
    );
}

interface PortEditorProps {
    label: string;
    side: MetamodelPortSide;
    percent: number;
    onChange: (side: MetamodelPortSide, percent: number) => void;
}

function PortEditor(props: PortEditorProps) {
    return (
        <fieldset className="metamodel-port-editor">
            <legend>{props.label}</legend>
            <label>Side<HydraAdaptiveSelect ariaLabel={`${props.label} side`} value={props.side} options={[
                { value: 'top', label: 'Top', description: 'Places the connection port on the upper edge of the node.', category: 'Node sides' },
                { value: 'right', label: 'Right', description: 'Places the connection port on the right edge of the node.', category: 'Node sides' },
                { value: 'bottom', label: 'Bottom', description: 'Places the connection port on the lower edge of the node.', category: 'Node sides' },
                { value: 'left', label: 'Left', description: 'Places the connection port on the left edge of the node.', category: 'Node sides' }
            ]} onChange={next => props.onChange(next as MetamodelPortSide, props.percent)} /></label>
            <ValidatedNumberInput decimals={2} helpText="0 is the start of the side and 100 is its end." label="Position (%)" max={100} min={0} value={props.percent} onChange={percent => props.onChange(props.side, percent)} />
        </fieldset>
    );
}

function readEffectiveEdge(props: MetamodelConnectionGeometryEditorProps): MetamodelDiagramEdge {
    const stored = props.diagram.edges.find(edge => edge.relationId === props.relationId);

    if(stored !== undefined) {
        return stored;
    }

    return { relationId: props.relationId, sourceHandle: 'right-25-source', targetHandle: 'left-75-target', sourceClassId: props.sourceClassId, targetClassId: props.targetClassId, routePoints: [] };
}

function updateEdge(props: MetamodelConnectionGeometryEditorProps, patch: Partial<MetamodelDiagramEdge>): void {
    const existing = props.diagram.edges.find(edge => edge.relationId === props.relationId);
    const base: MetamodelDiagramEdge = existing ?? { relationId: props.relationId, sourceHandle: 'right-25-source', targetHandle: 'left-75-target', sourceClassId: props.sourceClassId, targetClassId: props.targetClassId, routePoints: [] };
    const updated = { ...base, sourceClassId: props.sourceClassId, targetClassId: props.targetClassId, ...patch };
    const edges = existing === undefined ? [...props.diagram.edges, updated] : props.diagram.edges.map(edge => edge.relationId === props.relationId ? updated : edge);
    props.onChange({ ...props.diagram, edges });
}
