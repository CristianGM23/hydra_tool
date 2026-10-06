import { HydraButton } from '../../design-system/HydraButton';
import { resolveHydraDiagramPanPolicy, resolveHydraDiagramTranslateExtent } from '../../designer-ui/boundedDiagramViewport';
import {
    Background,
    ConnectionLineType,
    Controls,
    MiniMap,
    ReactFlow,
    ReactFlowProvider,
    useReactFlow
} from '@xyflow/react';
import type { Connection, Edge, EdgeChange, Node, NodeChange, NodeProps, OnEdgesChange, OnNodesChange } from '@xyflow/react';
import { memo, useCallback, useMemo, useState } from 'react';
import type { DragEvent, KeyboardEvent, ReactNode } from 'react';
import type { FlowNodeKind } from './types';
import { FLOW_NODE_PRESENTATIONS } from './flowPresentation';
import { useHydraLanguage } from '../../i18n';
import { useHydraFlowPerformance } from '../../diagram/useHydraFlowPerformance';
import { ResizableInfoTooltip } from '../../designer-ui/ResizableInfoTooltip';
import { hydraDirectedEdgeTypes } from '../../designer-ui/hydraDirectedEdgeTypes';
import { HydraConnectionPorts } from '../../designer-ui/HydraConnectionPorts';
import { HydraNodeResizer } from '../../designer-ui/HydraNodeResizer';
import { DiagramInteractionToolbar } from '../../designer-ui/DiagramInteractionToolbar';
import type { DiagramInteractionMode } from '../../designer-ui/DiagramInteractionToolbar';
import { arrangeDiagramSelection, positionChangesForSelection } from '../../designer-ui/diagramSelection';
import { useResizableDesignerSidebar } from '../../designer-ui/useResizableDesignerSidebar';
import { useHydraModelEditorNodeResize } from '../../designer-ui/useHydraModelEditorNodeResize';
import type { DiagramSelectionArrangement } from '../../designer-ui/diagramSelection';
import '@xyflow/react/dist/style.css';

export interface EventFlowVisualNodeData extends Record<string, unknown> {
    name: string;
    kind: FlowNodeKind;
    kindLabel: string;
    description: string;
    referenceId: string;
    eventTypeId: string;
    severity?: 'ERROR' | 'WARNING' | 'INFO';
}

export interface EventFlowVisualEdgeData extends Record<string, unknown> {
    kind: string;
    severity?: 'ERROR' | 'WARNING' | 'INFO';
}

interface InteractiveEventFlowNodeData extends EventFlowVisualNodeData {
    connectionMode: boolean;
    connectionSource: boolean;
    activeSourceHandleId: string;
    onPortClick: (nodeId: string, handleId: string, role: 'SOURCE' | 'TARGET') => void;
}

interface Props {
    nodes: Node<EventFlowVisualNodeData>[];
    edges: Edge<EventFlowVisualEdgeData>[];
    inspector: ReactNode;
    canUndo: boolean;
    canRedo: boolean;
    onNodesChange: OnNodesChange<Node<EventFlowVisualNodeData>>;
    onEdgesChange: OnEdgesChange<Edge<EventFlowVisualEdgeData>>;
    onConnect: (connection: Connection) => void;
    onSelectNode: (node: Node<EventFlowVisualNodeData>) => void;
    onSelectEdge: (edge: Edge<EventFlowVisualEdgeData>) => void;
    onAddNode: (kind: FlowNodeKind, position?: { x: number; y: number }) => void;
    onDelete: () => void;
    onDeleteSelection: (nodeIds: string[], edgeIds: string[]) => void;
    onDuplicate: () => void;
    onAutoLayout: () => void;
    onElkLayout: () => void;
    onUndo: () => void;
    onRedo: () => void;
    onNodeDragStart: () => void;
    onNodeDragStop: () => void;
}

const nodeTypes = { eventFlowNode: memo(EventFlowNode) };
const paletteGroups: Array<{ title: string; description: string; nodes: FlowNodeKind[] }> = [
    { title: 'Lifecycle', description: 'Defines where a flow starts and how it finishes successfully or with an error. Every executable flow needs a clear lifecycle.', nodes: ['START', 'SUCCESS_END', 'ERROR_END'] },
    { title: 'Events and data', description: 'Receives, filters, maps, routes, waits for and emits events. Use these nodes for the main event-to-event transformation path.', nodes: ['SOURCE', 'EVENT_STEP', 'WAIT_FOR_EVENT', 'EMIT', 'MAP', 'FILTER', 'ROUTE'] },
    { title: 'CEP and state', description: 'Correlates several events over time, removes duplicates and detects windows, sequences, joins, patterns or meaningful absence.', nodes: ['MERGE', 'DEDUPLICATE', 'WINDOW', 'AGGREGATE', 'JOIN', 'SEQUENCE', 'PATTERN', 'ABSENCE'] },
    { title: 'Execution', description: 'Invokes declarative actions, metamodel operations, commands or a manual external step from the event flow.', nodes: ['ACTION_STEP', 'OPERATION_STEP', 'COMMAND_STEP', 'MANUAL_STEP'] },
    { title: 'Control and resilience', description: 'Branches, runs work in parallel and defines timeout, retry, fallback, error handling and compensation behaviour.', nodes: ['DECISION', 'PARALLEL_SPLIT', 'TIMEOUT', 'RETRY', 'FALLBACK', 'ERROR_HANDLER', 'COMPENSATION_STEP'] }
];

export function EventFlowVisualEditor(props: Props) {
    return <ReactFlowProvider><EventFlowVisualEditorInner {...props} /></ReactFlowProvider>;
}

function EventFlowVisualEditorInner(props: Props) {
    const performanceProfile = useHydraFlowPerformance('event-flow', props.nodes.length, props.edges.length);
    const { t } = useHydraLanguage();
    const { screenToFlowPosition, fitView } = useReactFlow<Node<EventFlowVisualNodeData>, Edge<EventFlowVisualEdgeData>>();
    const paletteWidth = useResizableDesignerSidebar({
        storageKey: 'hydra:event-flow-diagram:palette-width', ariaLabel: 'Resize event-flow diagram palette', initialWidth: 250,
        minimumWidth: 190, maximumWidth: 360, cssVariableName: '--event-flow-palette-width',
        separatorClassName: 'event-flow-column-resizer palette-resizer'
    });
    const inspectorWidth = useResizableDesignerSidebar({
        storageKey: 'hydra:event-flow-diagram:inspector-width', ariaLabel: 'Resize event-flow diagram inspector', initialWidth: 360,
        minimumWidth: 280, maximumWidth: 580, cssVariableName: '--event-flow-inspector-width', resizeDirection: 'INCREASE_LEFT',
        separatorClassName: 'event-flow-column-resizer inspector-resizer'
    });
    const layoutStyle = { ...paletteWidth.layoutStyle, ...inspectorWidth.layoutStyle };
    const [interactionMode, setInteractionMode] = useState<DiagramInteractionMode>('SELECT');
    const connectionMode = interactionMode === 'CONNECT';
    const [connectionSourceId, setConnectionSourceId] = useState('');
    const [connectionSourceHandleId, setConnectionSourceHandleId] = useState('');
    const [selectedNodeIds, setSelectedNodeIds] = useState<string[]>(() => props.nodes.filter(node => node.selected).map(node => node.id));
    const [selectedEdgeIds, setSelectedEdgeIds] = useState<string[]>(() => props.edges.filter(edge => edge.selected).map(edge => edge.id));
    const { nodes: canvasNodes, onNodesChange: resizeNodes } = useHydraModelEditorNodeResize(props.nodes, props.onNodesChange);
    const displayEdges = useMemo(() => {
        const selected = new Set(selectedEdgeIds);
        return props.edges.map(edge => ({ ...edge, selected: selected.has(edge.id) }));
    }, [props.edges, selectedEdgeIds]);

    function startDrag(event: DragEvent<HTMLButtonElement>, kind: FlowNodeKind): void {
        event.dataTransfer.setData('application/x-hydra-event-flow-node', kind);
        event.dataTransfer.effectAllowed = 'copy';
    }

    function drop(event: DragEvent<HTMLDivElement>): void {
        event.preventDefault();
        const kind = event.dataTransfer.getData('application/x-hydra-event-flow-node') as FlowNodeKind;
        if (!paletteGroups.some(group => group.nodes.includes(kind))) return;
        props.onAddNode(kind, screenToFlowPosition({ x: event.clientX, y: event.clientY }));
    }

    function keyDown(event: KeyboardEvent<HTMLDivElement>): void {
        const modifier = event.ctrlKey || event.metaKey;
        if (modifier && event.key.toLowerCase() === 'z') { event.preventDefault(); if (event.shiftKey) props.onRedo(); else props.onUndo(); return; }
        if (modifier && event.key.toLowerCase() === 'y') { event.preventDefault(); props.onRedo(); return; }
        if (modifier && event.key.toLowerCase() === 'd') { event.preventDefault(); props.onDuplicate(); return; }
        if (event.key === 'Delete' || event.key === 'Backspace') {
            event.preventDefault();
            if (selectedNodeIds.length + selectionEdgeIds().length > 0) props.onDeleteSelection(selectedNodeIds, selectionEdgeIds());
            else props.onDelete();
        }
    }

    function selectionEdgeIds(): string[] {
        const selected = new Set(selectedNodeIds);
        return Array.from(new Set([...selectedEdgeIds, ...props.edges.filter(edge => selected.has(edge.source) && selected.has(edge.target)).map(edge => edge.id)]));
    }

    function arrangeSelection(arrangement: DiagramSelectionArrangement): void {
        const arranged = arrangeDiagramSelection(canvasNodes, selectedNodeIds, arrangement);
        const changes = positionChangesForSelection(canvasNodes, arranged, selectedNodeIds);
        if (changes.length > 0) resizeNodes(changes);
    }

    function clearSelection(): void {
        setSelectedNodeIds([]);
        setSelectedEdgeIds([]);
    }

    function handleNodesChange(changes: NodeChange<Node<EventFlowVisualNodeData>>[]): void {
        const selectionChanges = changes.filter((change): change is Extract<NodeChange<Node<EventFlowVisualNodeData>>, { type: 'select' }> => change.type === 'select');
        if (selectionChanges.length > 0) setSelectedNodeIds(current => updateSelectedIds(current, selectionChanges));
        const persistentChanges = changes.filter(change => change.type !== 'select');
        if (persistentChanges.length > 0) resizeNodes(persistentChanges);
    }

    function handleEdgesChange(changes: EdgeChange<Edge<EventFlowVisualEdgeData>>[]): void {
        const selectionChanges = changes.filter((change): change is Extract<EdgeChange<Edge<EventFlowVisualEdgeData>>, { type: 'select' }> => change.type === 'select');
        if (selectionChanges.length > 0) setSelectedEdgeIds(current => updateSelectedIds(current, selectionChanges));
        const persistentChanges = changes.filter(change => change.type !== 'select');
        if (persistentChanges.length > 0) props.onEdgesChange(persistentChanges);
    }

    const selectPort = useCallback((nodeId: string, handleId: string, role: 'SOURCE' | 'TARGET'): void => {
        if (!connectionMode && role !== 'SOURCE') return;
        if (role === 'SOURCE') {
            if (!connectionMode) setInteractionMode('CONNECT');
            setConnectionSourceId(nodeId);
            setConnectionSourceHandleId(handleId);
            const node = canvasNodes.find(item => item.id === nodeId);
            if (node !== undefined) props.onSelectNode(node);
            return;
        }
        if (connectionSourceId === '' || connectionSourceId === nodeId) return;
        props.onConnect({ source: connectionSourceId, target: nodeId, sourceHandle: connectionSourceHandleId, targetHandle: handleId });
        setConnectionSourceId('');
        setConnectionSourceHandleId('');
        setInteractionMode('SELECT');
    }, [canvasNodes, connectionMode, connectionSourceHandleId, connectionSourceId, props]);

    const interactiveNodes = useMemo(() => {
        const selected = new Set(selectedNodeIds);
        return canvasNodes.map(node => ({
            ...node,
            selected: selected.has(node.id),
            data: { ...node.data, connectionMode, connectionSource: connectionSourceId === node.id,
                activeSourceHandleId: connectionSourceId === node.id ? connectionSourceHandleId : '', onPortClick: selectPort } satisfies InteractiveEventFlowNodeData
        }));
    }, [canvasNodes, selectedNodeIds, connectionMode, connectionSourceId, connectionSourceHandleId, selectPort]);

    function completeConnection(connection: Connection): void {
        if (!connectionMode) return;
        props.onConnect(connection);
        setConnectionSourceId('');
        setConnectionSourceHandleId('');
        setInteractionMode('SELECT');
    }

    function changeInteractionMode(next: DiagramInteractionMode): void {
        setInteractionMode(next);
        setConnectionSourceId('');
        setConnectionSourceHandleId('');
    }

    return <div className="event-flow-visual-editor" style={layoutStyle} tabIndex={0} onKeyDown={keyDown}>
        <aside className="event-flow-visual-palette" aria-label={t("Event flow diagram palette")}>
            <header><strong>{t("Event-flow palette")}</strong><small>{t("Drag a node to the canvas or click to add it.")}</small></header>
            {paletteGroups.map(group => <section key={group.title}><div className="event-flow-palette-category"><h5>{t(group.title)}</h5><ResizableInfoTooltip label={`${t("Information about")} ${t(group.title)}`} title={t(group.title)}>{t(group.description)}</ResizableInfoTooltip></div>{group.nodes.map(kind => <HydraButton key={kind} type="button" draggable className={`event-flow-palette-node kind-${kind.toLowerCase()}`} onDragStart={event => startDrag(event, kind)} onClick={() => props.onAddNode(kind)}><strong>{t(FLOW_NODE_PRESENTATIONS[kind].label)}</strong><small>{t(FLOW_NODE_PRESENTATIONS[kind].description)}</small></HydraButton>)}</section>)}
        </aside>
        <div {...paletteWidth.separatorProps} />
        <section className="event-flow-visual-workspace">
            <DiagramInteractionToolbar
                performanceId="event-flow"
                mode={interactionMode}
                onModeChange={changeInteractionMode}
                allowConnect
                canUndo={props.canUndo}
                canRedo={props.canRedo}
                onUndo={props.onUndo}
                onRedo={props.onRedo}
                onDuplicate={props.onDuplicate}
                onDelete={() => {
                    const edgeIds = selectionEdgeIds();
                    if (selectedNodeIds.length + edgeIds.length > 0) { props.onDeleteSelection(selectedNodeIds, edgeIds); clearSelection(); }
                    else props.onDelete();
                }}
                onAutoLayout={props.onAutoLayout}
                onElkLayout={props.onElkLayout}
                onFit={() => void fitView({ padding: 0.18, duration: 250 })}
                selection={connectionMode ? null : {
                    elementCount: selectedNodeIds.length, relationCount: selectionEdgeIds().length, onArrange: arrangeSelection, onClear: clearSelection,
                    onDeleteElements: () => { props.onDeleteSelection(selectedNodeIds, []); clearSelection(); },
                    onDeleteRelations: () => { props.onDeleteSelection([], selectionEdgeIds()); clearSelection(); }
                }}
            />
            <div className="designer-connection-help"><strong>{connectionMode ? (connectionSourceId === '' ? t('Choose the source node') : t('Choose the target node')) : t('Flow connections')}</strong><span>{connectionMode ? t('Choose the blue source point and then the green target point, or drag between them.') : t('Use Connect to create transitions explicitly. New flows no longer contain an automatic Start-to-End transition.')}</span></div>
            <div className="event-flow-visual-canvas" onDrop={drop} onDragOver={event => { event.preventDefault(); event.dataTransfer.dropEffect = 'copy'; }}>
                <ReactFlow<Node<EventFlowVisualNodeData>, Edge<EventFlowVisualEdgeData>>
                    onlyRenderVisibleElements={performanceProfile.onlyRenderVisibleElements}
                    nodes={interactiveNodes}
                    edges={displayEdges}
                    nodeTypes={nodeTypes}
                    edgeTypes={hydraDirectedEdgeTypes}
                    defaultEdgeOptions={{ type: 'hydraDirectedEdge', zIndex: 8, interactionWidth: 30 }}
                    elevateEdgesOnSelect
                    fitView
                    deleteKeyCode={null}
                    elementsSelectable={interactionMode === 'SELECT'}
                    nodesDraggable={interactionMode === 'SELECT'}
                    {...resolveHydraDiagramPanPolicy(interactionMode)}
                    translateExtent={resolveHydraDiagramTranslateExtent(interactiveNodes)}
                    selectionOnDrag={interactionMode === 'SELECT'}
                    multiSelectionKeyCode={['Control', 'Meta']}
                    selectionKeyCode={null}
                    onNodesChange={handleNodesChange}
                    onEdgesChange={handleEdgesChange}
                    onConnect={completeConnection}
                    nodesConnectable={connectionMode}
                    connectionLineType={ConnectionLineType.SmoothStep}
                    connectionLineStyle={{ strokeWidth: 2.2 }}
                    onNodeClick={(_, node) => props.onSelectNode(node)}
                    onEdgeClick={(_, edge) => props.onSelectEdge(edge)}
                    onNodeDragStart={props.onNodeDragStart}
                    onNodeDragStop={props.onNodeDragStop}
                >
                    <Background gap={20} size={1} />
                    <MiniMap pannable zoomable />
                    <Controls />
                </ReactFlow>
            </div>
        </section>
        <div {...inspectorWidth.separatorProps} />
        <aside className="event-flow-visual-inspector" aria-label="Event flow diagram inspector">{props.inspector}</aside>
    </div>;
}

function updateSelectedIds(current: string[], changes: Array<{ id: string; selected: boolean }>): string[] {
    const next = new Set(current);
    for (const change of changes) { if (change.selected) next.add(change.id); else next.delete(change.id); }
    const result = [...next];
    return result.length === current.length && result.every((id, index) => id === current[index]) ? current : result;
}

function EventFlowNode({ data, selected, id }: NodeProps<Node<EventFlowVisualNodeData>>) {
    const { t } = useHydraLanguage();
    const interactive = data as InteractiveEventFlowNodeData;
    const start = data.kind === 'START' || data.kind === 'SOURCE';
    const end = data.kind === 'SUCCESS_END' || data.kind === 'ERROR_END';
    return <div className={`event-flow-node-card kind-${data.kind.toLowerCase()}${selected ? ' selected' : ''}${data.severity ? ` severity-${data.severity.toLowerCase()}` : ''}${interactive.connectionMode ? ' relationship-mode' : ''}${interactive.connectionSource ? ' connection-source-selected' : ''}`}>
        <HydraNodeResizer visible={selected} minWidth={150} minHeight={70} />
        <HydraConnectionPorts source={!end} target={!start} connectionMode={interactive.connectionMode} clickToConnect activeSourceHandleId={interactive.activeSourceHandleId} label={data.name} onPortClick={(handleId, role) => interactive.onPortClick(id, handleId, role)} />
        <span className="event-flow-node-kind">{t(data.kindLabel)}</span>
        <strong>{data.name}</strong>
        <small>{t(data.description)}</small>
    </div>;
}

