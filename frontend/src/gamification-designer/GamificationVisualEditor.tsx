import { HydraButton } from '../design-system/HydraButton';
import { resolveHydraDiagramPanPolicy, resolveHydraDiagramTranslateExtent } from '../designer-ui/boundedDiagramViewport';
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
import type { GamificationDiagramNodeKind } from './types';
import type { GamificationEdgeData, GamificationNodeData } from './gamificationDiagram';
import { ResizableInfoTooltip } from '../designer-ui/ResizableInfoTooltip';
import { hydraDirectedEdgeTypes } from '../designer-ui/hydraDirectedEdgeTypes';
import { HydraConnectionPorts } from '../designer-ui/HydraConnectionPorts';
import { HydraNodeResizer } from '../designer-ui/HydraNodeResizer';
import { useResizableDesignerSidebar } from '../designer-ui/useResizableDesignerSidebar';
import { useHydraModelEditorNodeResize } from '../designer-ui/useHydraModelEditorNodeResize';
import { useHydraLanguage } from '../i18n';
import { useHydraFlowPerformance } from '../diagram/useHydraFlowPerformance';
import { DiagramInteractionToolbar } from '../designer-ui/DiagramInteractionToolbar';
import type { DiagramInteractionMode } from '../designer-ui/DiagramInteractionToolbar';
import { arrangeDiagramSelection, positionChangesForSelection } from '../designer-ui/diagramSelection';
import type { DiagramSelectionArrangement } from '../designer-ui/diagramSelection';
import '@xyflow/react/dist/style.css';

export type GamificationPaletteKind = Exclude<GamificationDiagramNodeKind, 'EVIDENCE' | 'LEDGER'>;

export interface GamificationEvidencePaletteEntry {
    eventTypeId: string;
    label: string;
    description: string;
    onCanvas: boolean;
}

interface Props {
    nodes: Node<GamificationNodeData>[];
    edges: Edge<GamificationEdgeData>[];
    inspector: ReactNode;
    canUndo: boolean;
    canRedo: boolean;
    onNodesChange: OnNodesChange<Node<GamificationNodeData>>;
    onEdgesChange: OnEdgesChange<Edge<GamificationEdgeData>>;
    onConnect: (connection: Connection) => void;
    onSelectNode: (node: Node<GamificationNodeData>) => void;
    onSelectEdge: (edge: Edge<GamificationEdgeData>) => void;
    onAdd: (kind: GamificationPaletteKind, position?: { x: number; y: number }) => void;
    evidenceCatalog: GamificationEvidencePaletteEntry[];
    onAddEvidence: (eventTypeId: string, position?: { x: number; y: number }) => void;
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


interface InteractiveGamificationNodeData extends GamificationNodeData {
    connectionMode: boolean;
    connectionSource: boolean;
    activeSourceHandleId: string;
    onPortClick: (nodeId: string, handleId: string, role: 'SOURCE' | 'TARGET') => void;
}

const nodeTypes = { gamificationNode: memo(GamificationNode) };
const GAMIFICATION_KIND_LABELS: Record<GamificationDiagramNodeKind, string> = {
    EVIDENCE: 'Verified evidence',
    RULE: 'Evidence rule',
    POINT: 'Point type',
    LEVEL: 'Level',
    ACHIEVEMENT: 'Achievement',
    CHALLENGE: 'Challenge',
    REWARD: 'Reward',
    LEDGER: 'Runtime ledger'
};
const paletteGroups: Array<{ title: string; description: string; items: Array<{ kind: GamificationPaletteKind; label: string; description: string }> }> = [
    { title: 'Evidence rules', description: 'Rules listen to verified events and translate that evidence into controlled progression. They never change event-flow execution.', items: [
        { kind: 'RULE', label: 'Evidence rule', description: 'Consumes a verified event and grants progression.' }
    ] },
    { title: 'Progression', description: 'Point scales, levels, achievements and challenges describe how an actor advances over time.', items: [
        { kind: 'POINT', label: 'Point type', description: 'Defines a named point scale.' },
        { kind: 'LEVEL', label: 'Level', description: 'Unlocks after reaching a point threshold.' },
        { kind: 'ACHIEVEMENT', label: 'Achievement', description: 'One-off verifiable accomplishment.' },
        { kind: 'CHALLENGE', label: 'Challenge', description: 'Progresses towards a target count.' }
    ] },
    { title: 'Outcomes', description: 'Rewards are portable outcomes granted by rules, levels, achievements or completed challenges and recorded in the ledger.', items: [
        { kind: 'REWARD', label: 'Reward', description: 'Portable outcome granted by progression.' }
    ] }
];
const palette = paletteGroups.flatMap(group => group.items);

export function GamificationVisualEditor(props: Props) {
    return <ReactFlowProvider><GamificationVisualEditorInner {...props} /></ReactFlowProvider>;
}

function GamificationVisualEditorInner(props: Props) {
    const performanceProfile = useHydraFlowPerformance('gamification', props.nodes.length, props.edges.length);
    const { t } = useHydraLanguage();
    const { screenToFlowPosition, fitView } = useReactFlow<Node<GamificationNodeData>, Edge<GamificationEdgeData>>();
    const paletteWidth = useResizableDesignerSidebar({
        storageKey: 'hydra:gamification-diagram:palette-width',
        ariaLabel: 'Resize gamification palette',
        initialWidth: 250,
        minimumWidth: 190,
        maximumWidth: 360,
        cssVariableName: '--gamification-palette-width',
        separatorClassName: 'gamification-column-resizer palette-resizer'
    });
    const inspectorWidth = useResizableDesignerSidebar({
        storageKey: 'hydra:gamification-diagram:inspector-width',
        ariaLabel: 'Resize gamification properties',
        initialWidth: 320,
        minimumWidth: 270,
        maximumWidth: 560,
        cssVariableName: '--gamification-inspector-width',
        resizeDirection: 'INCREASE_LEFT',
        separatorClassName: 'gamification-column-resizer inspector-resizer'
    });
    const layoutStyle = { ...paletteWidth.layoutStyle, ...inspectorWidth.layoutStyle };
    const [interactionMode, setInteractionMode] = useState<DiagramInteractionMode>('SELECT');
    const connectionMode = interactionMode === 'CONNECT';
    const [selectedNodeIds, setSelectedNodeIds] = useState<string[]>(() => props.nodes.filter(node => node.selected).map(node => node.id));
    const [selectedEdgeIds, setSelectedEdgeIds] = useState<string[]>(() => props.edges.filter(edge => edge.selected).map(edge => edge.id));
    const { nodes: canvasNodes, onNodesChange: resizeNodes } = useHydraModelEditorNodeResize(props.nodes, props.onNodesChange);
    const [connectionSourceId, setConnectionSourceId] = useState('');
    const [connectionSourceHandleId, setConnectionSourceHandleId] = useState('');

    function startDrag(event: DragEvent<HTMLButtonElement>, value: string): void {
        event.dataTransfer.setData('application/x-hydra-gamification-node', value);
        event.dataTransfer.effectAllowed = 'copy';
    }

    function drop(event: DragEvent<HTMLDivElement>): void {
        event.preventDefault();
        const value = event.dataTransfer.getData('application/x-hydra-gamification-node');
        if (value.startsWith('EVIDENCE:')) {
            props.onAddEvidence(value.slice('EVIDENCE:'.length), screenToFlowPosition({ x: event.clientX, y: event.clientY }));
            return;
        }
        const kind = value.startsWith('KIND:') ? value.slice('KIND:'.length) as GamificationPaletteKind : value as GamificationPaletteKind;
        if (!palette.some(item => item.kind === kind)) return;
        props.onAdd(kind, screenToFlowPosition({ x: event.clientX, y: event.clientY }));
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

    function handleNodesChange(changes: NodeChange<Node<GamificationNodeData>>[]): void {
        const selectionChanges = changes.filter((change): change is Extract<NodeChange<Node<GamificationNodeData>>, { type: 'select' }> => change.type === 'select');
        if (selectionChanges.length > 0) setSelectedNodeIds(current => updateSelectedIds(current, selectionChanges));
        const persistentChanges = changes.filter(change => change.type !== 'select');
        if (persistentChanges.length > 0) resizeNodes(persistentChanges);
    }

    function handleEdgesChange(changes: EdgeChange<Edge<GamificationEdgeData>>[]): void {
        const selectionChanges = changes.filter((change): change is Extract<EdgeChange<Edge<GamificationEdgeData>>, { type: 'select' }> => change.type === 'select');
        if (selectionChanges.length > 0) setSelectedEdgeIds(current => updateSelectedIds(current, selectionChanges));
        const persistentChanges = changes.filter(change => change.type !== 'select');
        if (persistentChanges.length > 0) props.onEdgesChange(persistentChanges);
    }

    const selectPort = useCallback((nodeId: string, handleId: string, role: 'SOURCE' | 'TARGET'): void => {
        if (!connectionMode) return;
        if (role === 'SOURCE') {
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
            data: {
                ...node.data,
                connectionMode,
                connectionSource: connectionSourceId === node.id,
                activeSourceHandleId: connectionSourceId === node.id ? connectionSourceHandleId : '',
                onPortClick: selectPort
            } satisfies InteractiveGamificationNodeData
        }));
    }, [canvasNodes, selectedNodeIds, connectionMode, connectionSourceId, connectionSourceHandleId, selectPort]);
    const displayEdges = useMemo(() => {
        const selected = new Set(selectedEdgeIds);
        return props.edges.map(edge => ({ ...edge, selected: selected.has(edge.id) }));
    }, [props.edges, selectedEdgeIds]);

    function completeConnection(connection: Connection): void {
        if (!connectionMode) return;
        props.onConnect(connection);
        setConnectionSourceId('');
        setConnectionSourceHandleId('');
        setInteractionMode('SELECT');
    }

    return <div className="gamification-visual-editor" style={layoutStyle} tabIndex={0} onKeyDown={keyDown}>
        <aside className="gamification-visual-palette" aria-label={t('Gamification diagram palette')}>
            <header><strong>{t('Gamification palette')}</strong><small>{t('Drag an element to the canvas or click to add it.')}</small></header>
            <section className="gamification-palette-group gamification-connection-palette" aria-label={t('Connection tools')}>
                <div className="gamification-palette-category"><h5>{t('Connections')}</h5><ResizableInfoTooltip label={t('Information about connections')} title={t('Connections')}>{t('Use the connection command to choose a source and then a compatible target on the canvas. Start from the element that produces or owns the information and connect only to highlighted compatible targets; HYDRA rejects links that would create an invalid gamification dependency.')}</ResizableInfoTooltip></div>
                <HydraButton
                    type="button"
                    className={`gamification-palette-item kind-connection${connectionMode ? ' active' : ''}`}
                    aria-pressed={connectionMode}
                    onClick={() => {
                        setInteractionMode(connectionMode ? 'SELECT' : 'CONNECT');
                        setConnectionSourceId('');
                        setConnectionSourceHandleId('');
                    }}
                ><strong>{t('Connect elements')}</strong><small>{connectionMode ? t('Connection tool active.') : t('Choose a source and then a compatible target.')}</small></HydraButton>
            </section>
            <section className="gamification-palette-group gamification-evidence-palette" aria-label={t('Event evidence palette')}>
                <div className="gamification-palette-category"><h5>{t('Event evidence')}</h5><ResizableInfoTooltip label={t('Information about event evidence')} title={t('Event evidence')}>{t('Place an event on the canvas, then connect its blue source point to the green target point of an evidence rule. This writes the eventTypeId property of that rule.')}</ResizableInfoTooltip></div>
                {props.evidenceCatalog.map(item => <HydraButton
                    key={item.eventTypeId}
                    type="button"
                    draggable={!item.onCanvas}
                    disabled={item.onCanvas}
                    className={`gamification-palette-item kind-evidence${item.onCanvas ? ' on-canvas' : ''}`}
                    onDragStart={event => startDrag(event, `EVIDENCE:${item.eventTypeId}`)}
                    onClick={() => props.onAddEvidence(item.eventTypeId)}
                ><strong>{t(item.label)}</strong><small>{t(item.description)}</small>{item.onCanvas && <em>{t('On canvas')}</em>}</HydraButton>)}
                {props.evidenceCatalog.length === 0 && <p className="gamification-palette-empty">{t('Create or load an event definition before connecting event evidence to gamification rules.')}</p>}
            </section>
            {paletteGroups.map(group => <section className="gamification-palette-group" key={group.title}>
                <div className="gamification-palette-category"><h5>{t(group.title)}</h5><ResizableInfoTooltip label={t('Information about {title}').replace('{title}', t(group.title))} title={t(group.title)}>{t(group.description)}</ResizableInfoTooltip></div>
                {group.items.map(item => <HydraButton
                    key={item.kind}
                    type="button"
                    draggable
                    className={`gamification-palette-item kind-${item.kind.toLowerCase()}`}
                    onDragStart={event => startDrag(event, `KIND:${item.kind}`)}
                    onClick={() => props.onAdd(item.kind)}
                ><strong>{t(item.label)}</strong><small>{t(item.description)}</small></HydraButton>)}
            </section>)}
        </aside>
        <div {...paletteWidth.separatorProps} />
        <section className="gamification-visual-workspace">
            <DiagramInteractionToolbar
                performanceId="gamification"
                mode={interactionMode}
                onModeChange={next => { setInteractionMode(next); setConnectionSourceId(''); setConnectionSourceHandleId(''); }}
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
            <div className="designer-connection-help"><strong>{connectionMode ? (connectionSourceId === '' ? t('Choose the source element') : t('Choose the target element')) : t('Visual gamification links')}</strong><span>{connectionMode ? t('Choose the blue source point and then the green target point, or drag the arrow between both ports.') : t('Drag between connectors, or choose Connect from the diagram toolbar. Every valid link updates the corresponding rule, level or reward property.')}</span></div>
            <div className="gamification-visual-canvas" onDrop={drop} onDragOver={event => { event.preventDefault(); event.dataTransfer.dropEffect = 'copy'; }}>
                <ReactFlow<Node<GamificationNodeData>, Edge<GamificationEdgeData>>
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
        <aside className="gamification-visual-inspector" aria-label="Gamification diagram inspector">
            {props.inspector}
        </aside>
    </div>;
}

function updateSelectedIds(current: string[], changes: Array<{ id: string; selected: boolean }>): string[] {
    const next = new Set(current);
    for (const change of changes) { if (change.selected) next.add(change.id); else next.delete(change.id); }
    const result = [...next];
    return result.length === current.length && result.every((id, index) => id === current[index]) ? current : result;
}

function GamificationNode({ data, selected, id }: NodeProps<Node<GamificationNodeData>>) {
    const { t } = useHydraLanguage();
    const interactive = data as InteractiveGamificationNodeData;
    const terminal = data.kind === 'REWARD' || data.kind === 'LEDGER';
    const sourceOnly = data.kind === 'EVIDENCE';
    return <div className={`gamification-node-card kind-${data.kind.toLowerCase()}${selected ? ' selected' : ''}${data.invalid ? ' invalid' : ''}${interactive.connectionMode ? ' relationship-mode' : ''}${interactive.connectionSource ? ' connection-source-selected' : ''}`}>
        <HydraNodeResizer visible={selected} minWidth={150} minHeight={70} />
        <HydraConnectionPorts source={!terminal} target={!sourceOnly} connectionMode={interactive.connectionMode} activeSourceHandleId={interactive.activeSourceHandleId} label={data.label} onPortClick={(handleId, role) => interactive.onPortClick(id, handleId, role)} />
        <span className="gamification-node-kind">{t(GAMIFICATION_KIND_LABELS[data.kind])}</span>
        <strong>{data.label}</strong>
        <small>{t(data.subtitle)}</small>
    </div>;
}
