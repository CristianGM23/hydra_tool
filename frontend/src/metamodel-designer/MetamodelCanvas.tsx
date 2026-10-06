import { HydraButton } from '../design-system/HydraButton';
import { Background, ConnectionMode, Controls, MiniMap, ReactFlow, ViewportPortal, applyNodeChanges } from '@xyflow/react';
import type { Connection, Edge, Node, NodeChange, ReactFlowInstance } from '@xyflow/react';
import { memo, useCallback, useEffect, useMemo, useRef, useState } from 'react';
import type { PointerEvent as ReactPointerEvent } from 'react';
import { useHydraFlowPerformance } from '../diagram/useHydraFlowPerformance';

import { arrangeSelectedNodes, findInternalRelationIds, findNodesInsideSelection, isMeaningfulSelectionRectangle, normalizeSelectionRectangle } from './canvasSelection';
import { clearMetamodelNodeSelection } from './canvasNodeSelection';
import { moveEditableOrthogonalRoutePoint } from './connectionGeometry';
import type { CanvasContainmentRelation, CanvasSelectionArrangement, CanvasSelectionRectangle } from './canvasSelection';
import { buildMetamodelDiagramDocument, buildMetamodelEdges, buildMetamodelNodes } from './diagram';
import type { MetamodelClassNodeData } from './diagram';
import { MetamodelClassNode } from './MetamodelClassNode';
import { MetamodelDataTypeNode } from './MetamodelDataTypeNode';
import { MetamodelEnumNode } from './MetamodelEnumNode';
import { MetamodelRelationLayer } from './MetamodelRelationLayer';
import { calculateHandlePoint } from './relationGeometry';
import { isAreaSelectionBackgroundTarget, shouldClearMetamodelRelationFocus } from './relationSelection';
import { MetamodelSelectionActionsDialog } from './MetamodelSelectionActionsDialog';
import type { MetamodelConnectionKind, MetaModel, MetamodelDiagramDocument, MetamodelHandleRole, MetamodelSelection } from './types';
import { hasHydraGeometryChange } from '../designer-ui/hydraNodeGeometry';
import { hasHydraCommittedGeometryChange } from '../designer-ui/useHydraModelEditorNodeResize';

type MetamodelCanvasInteractionMode = 'SELECT' | 'PAN';

interface MetamodelCanvasProps {
    draft: MetaModel;
    diagram: MetamodelDiagramDocument;
    selection: MetamodelSelection;
    focusSelected: boolean;
    hideDependencies: boolean;
    connectionKind: MetamodelConnectionKind | null;
    connectionSourceClassId: string;
    onConnectionSourceChange: (classId: string) => void;
    onCreateConnection: (connection: Connection, kind: MetamodelConnectionKind) => void;
    onDeleteElement: (elementId: string) => void;
    onDeleteElements?: (elementIds: string[]) => void;
    onDeleteRelation: (relationId: string) => void;
    onDeleteRelations?: (relationIds: string[]) => void;
    onDiagramChange: (diagram: MetamodelDiagramDocument) => void;
    onSelect: (selection: MetamodelSelection) => void;
}

interface PointerSelectionDraft {
    pointerId: number;
    startX: number;
    startY: number;
    currentX: number;
    currentY: number;
}

const nodeTypes = { metamodelClass: memo(MetamodelClassNode), metamodelDataType: memo(MetamodelDataTypeNode), metamodelEnum: memo(MetamodelEnumNode) };

/**
 * Muestra y edita visualmente las clases y relaciones del metamodelo.
 *
 * @param props contiene el metamodelo y sus operaciones.
 * @returns lienzo React Flow del diseñador.
 */
export function MetamodelCanvas(props: MetamodelCanvasProps) {
    const [nodes, setNodes] = useState<Node[]>([]);
    const [connectionSourceHandleId, setConnectionSourceHandleId] = useState('');
    const [interactionMode, setInteractionMode] = useState<MetamodelCanvasInteractionMode>('SELECT');
    const [reactFlowInstance, setReactFlowInstance] = useState<ReactFlowInstance<Node, Edge> | null>(null);
    const [pointerSelection, setPointerSelection] = useState<PointerSelectionDraft | null>(null);
    const [selectedNodeIds, setSelectedNodeIds] = useState<string[]>([]);
    const [selectedRelationIds, setSelectedRelationIds] = useState<string[]>([]);
    const [showSelectionActions, setShowSelectionActions] = useState(false);
    const canvasRef = useRef<HTMLDivElement | null>(null);
    const nodesRef = useRef<Node[]>([]);
    const diagramRef = useRef(props.diagram);
    const spacePressedRef = useRef(false);
    const suppressNextPaneClickRef = useRef(false);
    const { connectionKind, connectionSourceClassId, onConnectionSourceChange, onCreateConnection, onDeleteElement, onDeleteElements, onDeleteRelation, onSelect } = props;
    const activeConnectionSourceHandleId = connectionKind === null || connectionSourceClassId === '' ? '' : connectionSourceHandleId;

    const selectHandle = useCallback((classId: string, handleId: string, role: MetamodelHandleRole): void => {
        if(connectionKind === null) return;
        if(role === 'SOURCE') {
            onConnectionSourceChange(classId);
            setConnectionSourceHandleId(handleId);
            onSelect({ kind: 'CLASS', id: classId });
            return;
        }
        if(connectionSourceClassId === '' || activeConnectionSourceHandleId === '') return;
        onCreateConnection({ source: connectionSourceClassId, target: classId, sourceHandle: activeConnectionSourceHandleId, targetHandle: handleId }, connectionKind);
        setConnectionSourceHandleId('');
    }, [activeConnectionSourceHandleId, connectionKind, connectionSourceClassId, onConnectionSourceChange, onCreateConnection, onSelect]);

    const configureInteractiveNode = useCallback((node: Node): Node => {
        if(node.type !== 'metamodelClass') return applyConnectionSourceClass(node, connectionSourceClassId);
        const data = node.data as MetamodelClassNodeData;
        const activeSourceHandleId = node.id === connectionSourceClassId ? activeConnectionSourceHandleId : '';
        const configuredNode = { ...node, data: { ...data, relationshipMode: connectionKind !== null, activeSourceHandleId, onHandleClick: selectHandle } satisfies MetamodelClassNodeData };
        return applyConnectionSourceClass(configuredNode, connectionSourceClassId);
    }, [activeConnectionSourceHandleId, connectionKind, connectionSourceClassId, selectHandle]);

    useEffect(() => {
        diagramRef.current = props.diagram;
        let active = true;
        queueMicrotask(() => {
            if (!active) return;
            setNodes(currentNodes => {
                const nextNodes = buildMetamodelNodes(props.draft, props.diagram, currentNodes).map(configureInteractiveNode);
                nodesRef.current = nextNodes;
                return nextNodes;
            });
        });
        return () => { active = false; };
    }, [configureInteractiveNode, props.diagram, props.draft]);

    const edges = useMemo(() => buildMetamodelEdges(props.draft, props.diagram, nodes), [props.draft, props.diagram, nodes]);
    const performanceProfile = useHydraFlowPerformance(`metamodel:${props.draft.id}`, nodes.length, edges.length);
    const containmentRelations = useMemo<CanvasContainmentRelation[]>(() => props.draft.relations.filter(relation => relation.kind === 'CONTAINMENT').map(relation => ({ sourceId: relation.sourceTypeId, targetId: relation.targetTypeId })), [props.draft]);

    useEffect(() => {
        function updateSpaceState(event: KeyboardEvent): void {
            if(event.code === 'Space') {
                spacePressedRef.current = event.type === 'keydown';
            }
        }

        window.addEventListener('keydown', updateSpaceState);
        window.addEventListener('keyup', updateSpaceState);
        return () => {
            window.removeEventListener('keydown', updateSpaceState);
            window.removeEventListener('keyup', updateSpaceState);
        };
    }, []);

    useEffect(() => {
        function deleteSelectedRelation(event: KeyboardEvent): void {
            if(event.key !== 'Delete' && event.key !== 'Backspace') {
                return;
            }

            if(isEditableTarget(event.target)) {
                return;
            }

            if(showSelectionActions && selectedNodeIds.length > 0) {
                event.preventDefault();
                const ids = [...selectedNodeIds];
                if(onDeleteElements !== undefined) onDeleteElements(ids);
                else ids.forEach(onDeleteElement);
                setSelectedNodeIds([]);
                setSelectedRelationIds([]);
                setShowSelectionActions(false);
                setNodes(clearMetamodelNodeSelection);
                onSelect({ kind: 'MODEL', id: props.draft.id });
                return;
            }

            if(!isRelationSelection(props.selection)) {
                return;
            }

            event.preventDefault();
            onDeleteRelation(props.selection.id);
        }

        window.addEventListener('keydown', deleteSelectedRelation);
        return () => window.removeEventListener('keydown', deleteSelectedRelation);
        // The listener is intentionally rebound only when the current selection changes.
    }, [onDeleteElement, onDeleteElements, onDeleteRelation, onSelect, props.draft.id, props.selection, selectedNodeIds, showSelectionActions]);

    /**
     * Aplica movimientos, selecciones y redimensionamientos del lienzo.
     *
     * @param changes contiene los cambios de nodos.
     * @returns no devuelve ningún valor.
     */
    function handleNodesChange(changes: NodeChange<Node>[]): void {
        // React Flow may emit resize/drag frames faster than React commits a render.
        // The ref gives this canvas the same immediate geometry ownership as useNodesState in the Model Editor.
        const nextNodes = applyNodeChanges(changes, nodesRef.current);
        nodesRef.current = nextNodes;
        setNodes(nextNodes);

        // Persist only the settled frame. Intermediate pointer frames remain local and never rebuild the domain document.
        if(hasHydraGeometryChange(changes) && hasHydraCommittedGeometryChange(changes)) {
            const nextEdges = buildMetamodelEdges(props.draft, props.diagram, nextNodes);
            props.onDiagramChange(buildMetamodelDiagramDocument(props.diagram.metamodelId, nextNodes, nextEdges));
        }
    }

    function handleConnect(connection: Connection): void {
        if(props.connectionKind === null) {
            return;
        }

        props.onCreateConnection(connection, props.connectionKind);
        setConnectionSourceHandleId('');
    }

    /**
     * Ejecuta un borrado coherente desde el teclado o React Flow.
     * Cuando hay nodos, el borrado de elementos también elimina sus relaciones internas.
     *
     * @param deletion contiene nodos y relaciones solicitados.
     * @returns siempre false porque el estado lo controla el diseñador.
     */
    async function handleBeforeDelete(deletion: { nodes: Node[]; edges: Edge[] }): Promise<boolean> {
        if(deletion.nodes.length > 0) {
            const elementIds = deletion.nodes.map(node => node.id);

            if(props.onDeleteElements !== undefined) {
                props.onDeleteElements(elementIds);
            } else {
                elementIds.forEach(props.onDeleteElement);
            }

            return false;
        }

        const relationIds = deletion.edges.map(edge => edge.id);

        if(relationIds.length > 0 && props.onDeleteRelations !== undefined) {
            props.onDeleteRelations(relationIds);
        } else {
            relationIds.forEach(props.onDeleteRelation);
        }

        return false;
    }

    function selectNode(node: Node): void {
        clearAreaSelection(false);
        setNodes(current => current.map(item => ({ ...item, selected: item.id === node.id })));

        if(node.type === 'metamodelEnum') {
            props.onConnectionSourceChange('');
            setConnectionSourceHandleId('');
            props.onSelect({ kind: 'ENUM', id: node.id });
            return;
        }

        if(node.type === 'metamodelDataType') {
            props.onConnectionSourceChange('');
            setConnectionSourceHandleId('');
            props.onSelect({ kind: 'DATA_TYPE', id: node.id });
            return;
        }

        props.onSelect({ kind: 'CLASS', id: node.id });
    }


    /**
     * Selecciona una relación de forma exclusiva y elimina la selección visual anterior de clases.
     */
    function selectRelation(selection: MetamodelSelection): void {
        props.onConnectionSourceChange('');
        setConnectionSourceHandleId('');
        clearAreaSelection(true);
        props.onSelect(selection);
    }

    /**
     * Persiste el desplazamiento directo de un punto interior de una ruta ortogonal.
     */
    function moveRoutePoint(relationId: string, internalIndex: number, clientX: number, clientY: number): void {
        if(reactFlowInstance === null) {
            return;
        }

        const visualEdge = edges.find(edge => edge.id === relationId);
        const sourceNode = visualEdge === undefined ? undefined : nodes.find(node => node.id === visualEdge.source);
        const targetNode = visualEdge === undefined ? undefined : nodes.find(node => node.id === visualEdge.target);
        const currentDiagram = diagramRef.current;
        const diagramEdge = currentDiagram.edges.find(edge => edge.relationId === relationId);
        const routePoints = diagramEdge?.routePoints ?? [];

        if(visualEdge === undefined || sourceNode === undefined || targetNode === undefined || diagramEdge === undefined || routePoints.length < 3) {
            return;
        }

        const synchronizedRoute = routePoints.map(point => ({ ...point }));
        synchronizedRoute[0] = calculateHandlePoint(sourceNode, visualEdge.sourceHandle, true);
        synchronizedRoute[synchronizedRoute.length - 1] = calculateHandlePoint(targetNode, visualEdge.targetHandle, false);
        const flowPoint = reactFlowInstance.screenToFlowPosition({ x: clientX, y: clientY });
        const nextRoute = moveEditableOrthogonalRoutePoint(synchronizedRoute, internalIndex, flowPoint);
        const nextDiagram = {
            ...currentDiagram,
            edges: currentDiagram.edges.map(edge => edge.relationId === relationId ? { ...edge, routePoints: nextRoute, labelPosition: undefined } : edge)
        };
        diagramRef.current = nextDiagram;
        props.onDiagramChange(nextDiagram);
    }

    function selectPane(): void {
        if(suppressNextPaneClickRef.current) {
            suppressNextPaneClickRef.current = false;
            return;
        }

        props.onConnectionSourceChange('');
        setConnectionSourceHandleId('');
        clearAreaSelection(true);
        props.onSelect({ kind: 'MODEL', id: props.draft.id });
    }



    function readFlowClassName(): string {
        if(props.connectionKind !== null) {
            return 'metamodel-flow metamodel-relationship-mode';
        }

        if(interactionMode === 'PAN') {
            return 'metamodel-flow metamodel-pan-mode';
        }

        return 'metamodel-flow metamodel-select-mode';
    }

    /**
     * Inicia una selección propia sobre el fondo del lienzo.
     */
    function startAreaSelection(event: ReactPointerEvent<HTMLDivElement>): void {
        if(event.button === 0 && shouldClearMetamodelRelationFocus(isRelationSelection(props.selection), event.target)) {
            props.onConnectionSourceChange('');
            setConnectionSourceHandleId('');
            clearAreaSelection(true);
            props.onSelect({ kind: 'MODEL', id: props.draft.id });
        }

        if(!selectionModeActive || event.button !== 0 || spacePressedRef.current) {
            return;
        }

        const target = event.target;

        if(!isAreaSelectionBackgroundTarget(target)) {
            return;
        }

        const point = localPoint(event.clientX, event.clientY);
        clearAreaSelection(true);
        setPointerSelection({ pointerId: event.pointerId, startX: point.x, startY: point.y, currentX: point.x, currentY: point.y });
        event.currentTarget.setPointerCapture(event.pointerId);
        event.preventDefault();
        event.stopPropagation();
    }

    /**
     * Actualiza el rectángulo mientras el usuario arrastra.
     */
    function moveAreaSelection(event: ReactPointerEvent<HTMLDivElement>): void {
        if(pointerSelection === null || pointerSelection.pointerId !== event.pointerId) {
            return;
        }

        const point = localPoint(event.clientX, event.clientY);
        setPointerSelection(current => current === null ? null : { ...current, currentX: point.x, currentY: point.y });
        event.preventDefault();
        event.stopPropagation();
    }

    /**
     * Finaliza la zona, selecciona nodos y marca las relaciones internas.
     */
    function finishAreaSelection(event: ReactPointerEvent<HTMLDivElement>): void {
        if(pointerSelection === null || pointerSelection.pointerId !== event.pointerId) {
            return;
        }

        const rectangle = normalizeSelectionRectangle(pointerSelection.startX, pointerSelection.startY, pointerSelection.currentX, pointerSelection.currentY);
        setPointerSelection(null);
        event.currentTarget.releasePointerCapture(event.pointerId);
        event.preventDefault();
        event.stopPropagation();
        suppressNextPaneClickRef.current = true;

        if(!isMeaningfulSelectionRectangle(rectangle)) {
            clearAreaSelection(true);
            return;
        }

        const flowRectangle = toFlowRectangle(rectangle);
        const nodeIds = findNodesInsideSelection(nodes, flowRectangle);
        const relationIds = findInternalRelationIds(edges, nodeIds);
        setNodes(current => current.map(node => ({ ...node, selected: nodeIds.includes(node.id) })));
        setSelectedNodeIds(nodeIds);
        setSelectedRelationIds(relationIds);
        setShowSelectionActions(nodeIds.length > 0);
        props.onSelect(nodeIds.length === 1 ? selectionForNode(nodes.find(node => node.id === nodeIds[0])) : { kind: 'MODEL', id: props.draft.id });
    }

    function localPoint(clientX: number, clientY: number): { x: number; y: number } {
        const bounds = canvasRef.current?.getBoundingClientRect();

        if(bounds === undefined) {
            return { x: clientX, y: clientY };
        }

        return { x: clientX - bounds.left, y: clientY - bounds.top };
    }

    function toFlowRectangle(rectangle: CanvasSelectionRectangle): CanvasSelectionRectangle {
        const bounds = canvasRef.current?.getBoundingClientRect();

        if(bounds === undefined || reactFlowInstance === null) {
            return rectangle;
        }

        const first = reactFlowInstance.screenToFlowPosition({ x: bounds.left + rectangle.left, y: bounds.top + rectangle.top });
        const second = reactFlowInstance.screenToFlowPosition({ x: bounds.left + rectangle.right, y: bounds.top + rectangle.bottom });
        return normalizeSelectionRectangle(first.x, first.y, second.x, second.y);
    }

    function arrangeSelection(arrangement: CanvasSelectionArrangement): void {
        const nextNodes = arrangeSelectedNodes(nodes, selectedNodeIds, arrangement, containmentRelations, { nodeGap: 50, treeLevelGap: 110 });
        setNodes(nextNodes);
        props.onDiagramChange(buildMetamodelDiagramDocument(props.diagram.metamodelId, nextNodes, edges));
    }

    function deleteSelectedElements(): void {
        const ids = [...selectedNodeIds];
        if(props.onDeleteElements !== undefined) {
            props.onDeleteElements(ids);
        } else {
            ids.forEach(props.onDeleteElement);
        }

        clearAreaSelection(true);
        props.onSelect({ kind: 'MODEL', id: props.draft.id });
    }

    function deleteSelectedRelations(): void {
        const ids = [...selectedRelationIds];
        if(props.onDeleteRelations !== undefined) {
            props.onDeleteRelations(ids);
        } else {
            ids.forEach(props.onDeleteRelation);
        }

        setSelectedRelationIds([]);
    }

    function clearAreaSelection(clearNodeSelection: boolean): void {
        setSelectedNodeIds([]);
        setSelectedRelationIds([]);
        setShowSelectionActions(false);

        if(clearNodeSelection) {
            setNodes(clearMetamodelNodeSelection);
        }
    }

    const relationshipMode = props.connectionKind !== null;
    const selectionModeActive = !relationshipMode && interactionMode === 'SELECT';
    const panModeActive = !relationshipMode && interactionMode === 'PAN';
    const selectionRectangle = pointerSelection === null ? null : normalizeSelectionRectangle(pointerSelection.startX, pointerSelection.startY, pointerSelection.currentX, pointerSelection.currentY);

    return (
        <section className="metamodel-canvas-panel">
            <div className="metamodel-canvas" onPointerCancel={finishAreaSelection} onPointerDownCapture={startAreaSelection} onPointerMove={moveAreaSelection} onPointerUp={finishAreaSelection} ref={canvasRef}>
                {!relationshipMode && <div aria-label="Canvas interaction mode" className="metamodel-canvas-interaction-toolbar" role="group">
                    <HydraButton aria-pressed={selectionModeActive} className={selectionModeActive ? 'active' : ''} onClick={() => setInteractionMode('SELECT')} title="Drag over an empty area to select every contained element" type="button">Select</HydraButton>
                    <HydraButton aria-pressed={panModeActive} className={panModeActive ? 'active' : ''} onClick={() => setInteractionMode('PAN')} title="Drag the canvas to move the viewport" type="button">Pan</HydraButton>
                    <span>{selectionModeActive ? 'Drag on the background to select an area. Hold Space or use Pan to move the canvas.' : 'Drag on the background to move the canvas.'}</span>
                </div>}
                <ReactFlow onlyRenderVisibleElements={performanceProfile.onlyRenderVisibleElements} className={readFlowClassName()} connectionMode={ConnectionMode.Strict} edges={[]} elementsSelectable={!relationshipMode} fitView fitViewOptions={{ padding: 0.18, maxZoom: 1 }} multiSelectionKeyCode={['Control', 'Meta']} nodeTypes={nodeTypes} nodes={nodes} nodesConnectable={relationshipMode} nodesDraggable={!relationshipMode} onBeforeDelete={handleBeforeDelete} onConnect={handleConnect} onInit={setReactFlowInstance} onNodeClick={(_event, node) => selectNode(node)} onNodesChange={handleNodesChange} onPaneClick={selectPane} panActivationKeyCode="Space" panOnDrag={panModeActive ? true : [1, 2]} selectionOnDrag={false}>
                    <ViewportPortal>
                        <MetamodelRelationLayer edges={edges} focusSelected={props.focusSelected} hideDependencies={props.hideDependencies} nodes={nodes} onRoutePointMove={moveRoutePoint} onSelect={selectRelation} selectedRelationIds={selectedRelationIds} selection={props.selection} />
                    </ViewportPortal>
                    <MiniMap />
                    <Controls />
                    <Background />
                </ReactFlow>
                {selectionRectangle !== null && <div aria-hidden="true" className="metamodel-area-selection-rectangle" style={{ left: selectionRectangle.left, top: selectionRectangle.top, width: selectionRectangle.width, height: selectionRectangle.height }} />}
                {showSelectionActions && <MetamodelSelectionActionsDialog canDistribute={selectedNodeIds.length >= 3} elementCount={selectedNodeIds.length} onArrange={arrangeSelection} onClear={() => { clearAreaSelection(true); props.onSelect({ kind: 'MODEL', id: props.draft.id }); }} onDeleteElements={deleteSelectedElements} onDeleteRelations={deleteSelectedRelations} relationCount={selectedRelationIds.length} />}
            </div>
        </section>
    );
}


function selectionForNode(node: Node | undefined): MetamodelSelection {
    if(node?.type === 'metamodelEnum') {
        return { kind: 'ENUM', id: node.id };
    }

    if(node?.type === 'metamodelDataType') {
        return { kind: 'DATA_TYPE', id: node.id };
    }

    if(node !== undefined) {
        return { kind: 'CLASS', id: node.id };
    }

    return { kind: 'MODEL', id: 'model' };
}

function isRelationSelection(selection: MetamodelSelection): boolean {
    return selection.kind === 'RELATION';
}

function isEditableTarget(target: EventTarget | null): boolean {
    if(!(target instanceof HTMLElement)) {
        return false;
    }

    const tagName = target.tagName.toLowerCase();

    if(tagName === 'input' || tagName === 'textarea' || tagName === 'select') {
        return true;
    }

    return target.isContentEditable;
}

function applyConnectionSourceClass(node: Node, sourceClassId: string): Node {
    if(node.id === sourceClassId) {
        return { ...node, className: 'metamodel-connection-source' };
    }

    return { ...node, className: '' };
}
