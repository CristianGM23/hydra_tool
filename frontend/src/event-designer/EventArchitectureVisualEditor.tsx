import { HydraButton } from '../design-system/HydraButton';
import { resolveHydraDiagramPanPolicy, resolveHydraDiagramTranslateExtent } from '../designer-ui/boundedDiagramViewport';
import {
    Background,
    Controls,
    MiniMap,
    ReactFlow,
    ReactFlowProvider,
    useReactFlow
} from '@xyflow/react';
import type { Connection, Edge, Node, NodeProps, OnEdgesChange, OnNodesChange } from '@xyflow/react';
import { memo, useCallback, useEffect, useMemo, useRef, useState } from 'react';
import type { DragEvent, KeyboardEvent, ReactNode } from 'react';
import type { EventArchitectureEdgeData, EventArchitectureNodeData } from './eventArchitectureDiagram';
import { filterEventArchitecturePalette, groupEventArchitecturePalette } from './eventArchitecturePalette';
import type { EventArchitecturePaletteEntry } from './eventArchitecturePalette';
import { ResizableInfoTooltip } from '../designer-ui/ResizableInfoTooltip';
import { hydraDirectedEdgeTypes } from '../designer-ui/hydraDirectedEdgeTypes';
import { HydraConnectionPorts } from '../designer-ui/HydraConnectionPorts';
import { HydraNodeResizer } from '../designer-ui/HydraNodeResizer';
import { DiagramInteractionToolbar, type DiagramInteractionMode } from '../designer-ui/DiagramInteractionToolbar';
import { useResizableDesignerSidebar } from '../designer-ui/useResizableDesignerSidebar';
import { useHydraLanguage } from '../i18n';
import { useHydraFlowPerformance } from '../diagram/useHydraFlowPerformance';
import { useHydraModelEditorNodeResize } from '../designer-ui/useHydraModelEditorNodeResize';
import '@xyflow/react/dist/style.css';

interface Props {
    nodes: Node<EventArchitectureNodeData>[];
    edges: Edge<EventArchitectureEdgeData>[];
    catalog: EventArchitecturePaletteEntry[];
    inspector: ReactNode;
    canUndo: boolean;
    canRedo: boolean;
    onNodesChange: OnNodesChange<Node<EventArchitectureNodeData>>;
    onEdgesChange: OnEdgesChange<Edge<EventArchitectureEdgeData>>;
    onConnect: (connection: Connection) => void;
    onSelectNode: (node: Node<EventArchitectureNodeData>) => void;
    onSelectEdge: (edge: Edge<EventArchitectureEdgeData>) => void;
    onAddCatalogEvent: (eventTypeId: string, position?: { x: number; y: number }) => void;
    onCreateCustomEvent: (position?: { x: number; y: number }) => void;
    onRemoveFromCanvas: () => void;
    onDuplicate: () => void;
    onClearCanvas: () => void;
    onAutoLayout: () => void;
    onElkLayout: () => void;
    onUndo: () => void;
    onRedo: () => void;
    onNodeDragStart: () => void;
    onNodeDragStop: () => void;
}


interface InteractiveEventArchitectureNodeData extends EventArchitectureNodeData {
    connectionMode: boolean;
    connectionSource: boolean;
    activeSourceHandleId: string;
    onPortClick: (nodeId: string, handleId: string, role: 'SOURCE' | 'TARGET') => void;
}

const nodeTypes = { eventArchitectureNode: memo(EventArchitectureNode) };
const DRAG_TYPE = 'application/x-hydra-event-architecture-item';

export function EventArchitectureVisualEditor(props: Props) {
    return <ReactFlowProvider><EventArchitectureVisualEditorInner {...props} /></ReactFlowProvider>;
}

function EventArchitectureVisualEditorInner(props: Props) {
    const performanceProfile = useHydraFlowPerformance('event-architecture', props.nodes.length, props.edges.length);
    const { t } = useHydraLanguage();
    const { screenToFlowPosition, fitView, getViewport, setCenter } = useReactFlow<Node<EventArchitectureNodeData>, Edge<EventArchitectureEdgeData>>();
    const paletteWidth = useResizableDesignerSidebar({
        storageKey: 'hydra:event-architecture:palette-width', ariaLabel: 'Resize event architecture palette', initialWidth: 280,
        minimumWidth: 220, maximumWidth: 480, cssVariableName: '--event-architecture-palette-width',
        separatorClassName: 'event-architecture-column-resizer palette-resizer'
    });
    const inspectorWidth = useResizableDesignerSidebar({
        storageKey: 'hydra:event-architecture:inspector-width', ariaLabel: 'Resize event architecture inspector', initialWidth: 360,
        minimumWidth: 280, maximumWidth: 580, cssVariableName: '--event-architecture-inspector-width', resizeDirection: 'INCREASE_LEFT',
        separatorClassName: 'event-architecture-column-resizer inspector-resizer'
    });
    const layoutStyle = { ...paletteWidth.layoutStyle, ...inspectorWidth.layoutStyle };
    const [query, setQuery] = useState('');
    const [interactionMode, setInteractionMode] = useState<DiagramInteractionMode>('SELECT');
    const connectionMode = interactionMode === 'CONNECT';
    const [connectionSourceId, setConnectionSourceId] = useState('');
    const [connectionSourceHandleId, setConnectionSourceHandleId] = useState('');
    const { nodes: canvasNodes, onNodesChange: resizeNodes } = useHydraModelEditorNodeResize(props.nodes, props.onNodesChange);
    const connectionSourceRef = useRef<{ nodeId: string; handleId: string } | null>(null);
    const canvasRef = useRef<HTMLDivElement | null>(null);
    const normalizedQuery = query.trim().toLowerCase();
    const filtered = useMemo(() => filterEventArchitecturePalette(props.catalog, query), [props.catalog, query]);
    const grouped = useMemo(() => groupEventArchitecturePalette(filtered), [filtered]);

    function startDrag(event: DragEvent<HTMLButtonElement>, value: string): void {
        event.dataTransfer.setData(DRAG_TYPE, value);
        event.dataTransfer.effectAllowed = 'copy';
    }

    /**
     * A palette click must create the card inside the viewport the user is
     * currently looking at. Previously click insertion fell back to a global
     * diagram coordinate, so a panned canvas could make the new event look as
     * if it had not been rendered at all. Drag and drop keeps the exact pointer
     * position, while click insertion uses the visible canvas centre.
     */
    function visibleCanvasPlacement(seed: number): { x: number; y: number } | undefined {
        const canvas = canvasRef.current;
        if (canvas === null) return undefined;
        const bounds = canvas.getBoundingClientRect();
        if (bounds.width <= 0 || bounds.height <= 0) return undefined;
        const center = screenToFlowPosition({ x: bounds.left + bounds.width / 2, y: bounds.top + bounds.height / 2 });
        if (!Number.isFinite(center.x) || !Number.isFinite(center.y)) return undefined;
        const offset = (seed % 5) * 18;
        return { x: center.x - 115 + offset, y: center.y - 46 + offset };
    }

    function placeCatalogEvent(eventTypeId: string): void {
        props.onAddCatalogEvent(eventTypeId, visibleCanvasPlacement(props.nodes.length));
    }

    function placeCustomEvent(): void {
        props.onCreateCustomEvent(visibleCanvasPlacement(props.nodes.length));
    }

    function drop(event: DragEvent<HTMLDivElement>): void {
        event.preventDefault();
        const value = event.dataTransfer.getData(DRAG_TYPE);
        if (value === '') return;
        const position = screenToFlowPosition({ x: event.clientX, y: event.clientY });
        const safePosition = Number.isFinite(position.x) && Number.isFinite(position.y) ? position : undefined;
        if (value === 'NEW_CUSTOM_EVENT') props.onCreateCustomEvent(safePosition);
        else props.onAddCatalogEvent(value, safePosition);
    }

    function keyDown(event: KeyboardEvent<HTMLDivElement>): void {
        const modifier = event.ctrlKey || event.metaKey;
        if (modifier && event.key.toLowerCase() === 'z') { event.preventDefault(); if (event.shiftKey) props.onRedo(); else props.onUndo(); return; }
        if (modifier && event.key.toLowerCase() === 'y') { event.preventDefault(); props.onRedo(); return; }
        if (modifier && event.key.toLowerCase() === 'd') { event.preventDefault(); props.onDuplicate(); return; }
        if (event.key === 'Delete' || event.key === 'Backspace') { event.preventDefault(); props.onRemoveFromCanvas(); }
    }

    const selectPort = useCallback((nodeId: string, handleId: string, role: 'SOURCE' | 'TARGET'): void => {
        if (!connectionMode) return;
        if (role === 'SOURCE') {
            connectionSourceRef.current = { nodeId, handleId };
            setConnectionSourceId(nodeId);
            setConnectionSourceHandleId(handleId);
            const node = canvasNodes.find(item => item.id === nodeId);
            if (node !== undefined) props.onSelectNode(node);
            return;
        }
        const source = connectionSourceRef.current;
        if (source === null || source.nodeId === nodeId) return;
        props.onConnect({ source: source.nodeId, target: nodeId, sourceHandle: source.handleId, targetHandle: handleId });
        connectionSourceRef.current = null;
        setConnectionSourceId('');
        setConnectionSourceHandleId('');
        setInteractionMode('SELECT');
    }, [canvasNodes, connectionMode, props]);

    const interactiveNodes = useMemo(() => canvasNodes.map(node => ({
        ...node,
        data: {
            ...node.data,
            connectionMode,
            connectionSource: connectionSourceId === node.id,
            activeSourceHandleId: connectionSourceId === node.id ? connectionSourceHandleId : '',
            onPortClick: selectPort
        } satisfies InteractiveEventArchitectureNodeData
    })), [canvasNodes, connectionMode, connectionSourceId, connectionSourceHandleId, selectPort]);
    const renderedNodeSignature = useMemo(() => interactiveNodes.map(node => node.id).sort().join('|'), [interactiveNodes]);
    const lastFramedNodeSignature = useRef('');
    const knownRenderedNodeIds = useRef<Set<string>>(new Set());

    /**
     * React Flow applies the fitView prop only during initialization. Event
     * definitions and persisted diagram nodes arrive asynchronously, so the
     * document could contain nodes while the viewport still looked empty.
     *
     * A newly inserted palette event is centred explicitly from its canonical
     * geometry, which does not depend on a DOM measurement. Initial/multi-node
     * loads and removals frame the complete node set after React has committed
     * the wrappers. Geometry-only drag/resize changes keep the same identity
     * signature and therefore never steal the user's viewport.
     */
    useEffect(() => {
        if (interactiveNodes.length === 0) {
            lastFramedNodeSignature.current = '';
            knownRenderedNodeIds.current = new Set();
            return undefined;
        }
        if (lastFramedNodeSignature.current === renderedNodeSignature) return undefined;
        const previousIds = knownRenderedNodeIds.current;
        const addedNodes = interactiveNodes.filter(node => !previousIds.has(node.id));
        knownRenderedNodeIds.current = new Set(interactiveNodes.map(node => node.id));
        lastFramedNodeSignature.current = renderedNodeSignature;
        let secondFrame = 0;
        const firstFrame = requestAnimationFrame(() => {
            secondFrame = requestAnimationFrame(() => {
                const canvas = canvasRef.current;
                if (canvas === null) return;
                const bounds = canvas.getBoundingClientRect();
                if (bounds.width <= 0 || bounds.height <= 0) return;
                if (addedNodes.length === 1) {
                    const node = addedNodes[0];
                    const width = readFlowNodeDimension(node.width, node.style?.width, 230);
                    const height = readFlowNodeDimension(node.height, node.style?.height, 92);
                    const zoom = Math.min(1.15, Math.max(0.7, getViewport().zoom));
                    void setCenter(node.position.x + width / 2, node.position.y + height / 2, { zoom, duration: 0 });
                    return;
                }
                void fitView({ padding: 0.18, duration: 0, maxZoom: 1.15 });
            });
        });
        return () => {
            cancelAnimationFrame(firstFrame);
            if (secondFrame !== 0) cancelAnimationFrame(secondFrame);
        };
    }, [fitView, getViewport, interactiveNodes, renderedNodeSignature, setCenter]);

    function completeConnection(connection: Connection): void {
        if (!connectionMode) return;
        props.onConnect(connection);
        connectionSourceRef.current = null;
        setConnectionSourceId('');
        setConnectionSourceHandleId('');
        setInteractionMode('SELECT');
    }


    return <div className="event-architecture-visual-editor" style={layoutStyle} tabIndex={0} onKeyDown={keyDown}>
        <aside className="event-architecture-palette" aria-label={t('Event architecture diagram palette')}>
            <header>
                <strong>{t('Event palette')}</strong>
                <small>{t('Choose only the events needed by this architecture. The complete catalog remains available here without filling the canvas.')}</small>
            </header>
            <div className="event-architecture-create-row">
                <HydraButton type="button" draggable className="event-architecture-create-item" onDragStart={event => startDrag(event, 'NEW_CUSTOM_EVENT')} onClick={placeCustomEvent}>
                    <span className="event-palette-icon" aria-hidden="true">＋</span>
                    <span><strong>{t('New custom event')}</strong><small>{t('Create a portable event contract and place it on the canvas.')}</small></span>
                </HydraButton>
                <ResizableInfoTooltip label={t('Show help for this custom event')} title={t('New custom event')}>
                    <div className="event-palette-help">
                        <section><strong>{t('What it does')}</strong><p>{t('Creates a domain event contract owned by this metamodel and places it directly in the visible diagram area. Repeat this action whenever you need another custom event.')}</p></section>
                        <section><strong>{t('When to use it')}</strong><p>{t('Use a custom event when the fact belongs to your domain and no HYDRA system event expresses it precisely enough. Create a separate event for each distinct fact you want to model.')}</p></section>
                        <section><strong>{t('Example')}</strong><p>{t('Example: create “Payment approved” and connect it to a shipping event flow.')}</p></section>
                    </div>
                </ResizableInfoTooltip>
            </div>
            <label className="event-architecture-palette-search">
                <span>{t('Search catalog')}</span>
                <input aria-label={t('Search event palette')} placeholder={t('Search events...')} value={query} onChange={event => setQuery(event.target.value)} />
            </label>
            <div className="event-architecture-palette-scroll">
                {grouped.map(group => <details className="event-architecture-palette-group" key={group.category} open={normalizedQuery !== ''}>
                    <summary>
                        <span className="event-palette-category-heading"><span>{t(group.category)}</span><ResizableInfoTooltip label={t('Show help for this event category')} title={`${t(group.category)} · ${t('Events')}`}><EventCategoryHelp category={group.category} /></ResizableInfoTooltip></span>
                        <small>{group.entries.filter(item => !item.onCanvas).length} {t('available')}</small>
                    </summary>
                    <div>
                        {group.entries.map(item => <div className="event-architecture-catalog-row" key={item.eventTypeId}>
                            <HydraButton
                                className={`event-architecture-catalog-item${item.onCanvas ? ' on-canvas' : ''}`}
                                disabled={item.onCanvas}
                                draggable={!item.onCanvas}
                                type="button"
                                onClick={() => placeCatalogEvent(item.eventTypeId)}
                                onDragStart={event => startDrag(event, item.eventTypeId)}
                            >
                                <span className={`event-palette-icon ${item.kind === 'RESERVED_EVENT' ? 'system' : 'custom'}`} aria-hidden="true">{item.kind === 'RESERVED_EVENT' ? '◆' : '◇'}</span>
                                <span><strong>{t(item.label)}</strong><small>{t(item.description)}</small></span>
                                {item.onCanvas && <em>{t('On canvas')}</em>}
                            </HydraButton>
                            <ResizableInfoTooltip label={t('Show help for this event')} title={t(item.label)}>
                                <EventPaletteEntryHelp entry={item} />
                            </ResizableInfoTooltip>
                        </div>)}
                    </div>
                </details>)}
                {filtered.length === 0 && <div className="event-architecture-palette-empty"><strong>{t('No events found')}</strong><small>{t('Change the search text to browse the full catalog.')}</small></div>}
            </div>
        </aside>
        <div {...paletteWidth.separatorProps} />
        <section className="event-architecture-workspace">
            <DiagramInteractionToolbar
                performanceId="event-architecture"
                mode={interactionMode}
                onModeChange={next => { setInteractionMode(next); connectionSourceRef.current = null; setConnectionSourceId(''); setConnectionSourceHandleId(''); }}
                allowConnect
                canUndo={props.canUndo}
                canRedo={props.canRedo}
                onUndo={props.onUndo}
                onRedo={props.onRedo}
                onDuplicate={props.onDuplicate}
                onDelete={props.onRemoveFromCanvas}
                onAutoLayout={props.nodes.length === 0 ? undefined : props.onAutoLayout}
                onElkLayout={props.nodes.length === 0 ? undefined : props.onElkLayout}
                onFit={props.nodes.length === 0 ? undefined : () => void fitView({ padding: 0.18, duration: 250 })}
                trailing={<><HydraButton type="button" disabled={props.nodes.length === 0} onClick={props.onClearCanvas}>{t('Clear canvas')}</HydraButton><span className="event-architecture-toolbar-summary">{props.nodes.length} {t('event(s)')} · {props.edges.length} {t('binding(s)')}</span></>}
            />
            <div className="event-architecture-canvas" ref={canvasRef} onDrop={drop} onDragOver={event => { event.preventDefault(); event.dataTransfer.dropEffect = 'copy'; }}>
                <ReactFlow<Node<EventArchitectureNodeData>, Edge<EventArchitectureEdgeData>>
                    onlyRenderVisibleElements={performanceProfile.onlyRenderVisibleElements}
                    nodes={interactiveNodes}
                    edges={props.edges}
                    nodeTypes={nodeTypes}
                    edgeTypes={hydraDirectedEdgeTypes}
                    defaultEdgeOptions={{ type: 'hydraDirectedEdge', zIndex: 8, interactionWidth: 30 }}
                    elevateEdgesOnSelect
                    deleteKeyCode={null}
                    elementsSelectable={interactionMode === 'SELECT'}
                    nodesDraggable={interactionMode === 'SELECT'}
                    {...resolveHydraDiagramPanPolicy(interactionMode)}
                    translateExtent={resolveHydraDiagramTranslateExtent(interactiveNodes)}
                    selectionOnDrag={interactionMode === 'SELECT'}
                    multiSelectionKeyCode={['Control', 'Meta']}
                    selectionKeyCode={null}
                    nodesConnectable={connectionMode}
                    onNodesChange={resizeNodes}
                    onEdgesChange={props.onEdgesChange}
                    onConnect={completeConnection}
                    onNodeClick={(_, node) => props.onSelectNode(node)}
                    onEdgeClick={(_, edge) => props.onSelectEdge(edge)}
                    onNodeDragStart={props.onNodeDragStart}
                    onNodeDragStop={props.onNodeDragStop}
                >
                    <Background gap={20} size={1} />
                    <MiniMap pannable zoomable />
                    <Controls />
                </ReactFlow>
                {props.nodes.length === 0 && <div className="event-architecture-empty-canvas" aria-label={t('Empty event architecture canvas')}>
                    <strong>{t('Start with an empty architecture')}</strong>
                    <span>{t('Drag system or custom events from the palette. Connect them only when one event derives another.')}</span>
                </div>}
            </div>
        </section>
        <div {...inspectorWidth.separatorProps} />
        <aside className="event-architecture-inspector" aria-label={t('Event architecture diagram inspector')}>{props.inspector}</aside>
    </div>;
}


function readFlowNodeDimension(direct: number | undefined, styleValue: string | number | undefined, fallback: number): number {
    if (direct !== undefined && Number.isFinite(direct) && direct > 0) return direct;
    if (typeof styleValue === 'number' && Number.isFinite(styleValue) && styleValue > 0) return styleValue;
    if (typeof styleValue === 'string') {
        const parsed = Number.parseFloat(styleValue);
        if (Number.isFinite(parsed) && parsed > 0) return parsed;
    }
    return fallback;
}

function EventArchitectureNode({ data, selected, id }: NodeProps<Node<EventArchitectureNodeData>>) {
    const { t } = useHydraLanguage();
    const interactive = data as InteractiveEventArchitectureNodeData;
    const targetAvailable = data.kind === 'CUSTOM_EVENT';
    return <div className={`event-architecture-node kind-${data.kind.toLowerCase()}${selected ? ' selected' : ''}${!data.enabled ? ' disabled' : ''}${interactive.connectionMode ? ' relationship-mode' : ''}${interactive.connectionSource ? ' connection-source-selected' : ''}`}>
        <HydraNodeResizer visible={selected} minWidth={180} minHeight={80} />
        <HydraConnectionPorts source target={targetAvailable} connectionMode={interactive.connectionMode} activeSourceHandleId={interactive.activeSourceHandleId} label={data.label} onPortClick={(handleId, role) => interactive.onPortClick(id, handleId, role)} />
        <span className="event-architecture-node-kind">{data.kind === 'RESERVED_EVENT' ? t(data.category) : `${t('Custom')} · ${t(data.category)}`}</span>
        <strong>{t(data.label)}</strong>
        <small>{t(data.description)}</small>
        {data.kind === 'CUSTOM_EVENT' && <em>{data.fieldCount} {t('event data field(s)')}</em>}
    </div>;
}


function EventPaletteEntryHelp({ entry }: { entry: EventArchitecturePaletteEntry }) {
    const { t } = useHydraLanguage();
    const example = eventCategoryExample(entry.category);
    return <div className="event-palette-help">
        <section><strong>{t('What it does')}</strong><p>{t(entry.description)}</p></section>
        <section><strong>{t('When to use it')}</strong><p>{t(eventCategoryUsage(entry.category))}</p></section>
        <section><strong>{t('Example')}</strong><p>{t(example)}</p></section>
    </div>;
}

function EventCategoryHelp({ category }: { category: string }) {
    const { t } = useHydraLanguage();
    return <div className="event-palette-help">
        <section><strong>{t('What it contains')}</strong><p>{t(eventCategoryDescription(category))}</p></section>
        <section><strong>{t('When to use it')}</strong><p>{t(eventCategoryUsage(category))}</p></section>
        <section><strong>{t('Example')}</strong><p>{t(eventCategoryExample(category))}</p></section>
    </div>;
}

function normalizedEventCategory(category: string): string {
    return category.trim().toUpperCase().replace(/[_-]+/g, ' ');
}

function eventCategoryDescription(category: string): string {
    const descriptions: Record<string, string> = {
        METAMODEL: 'Facts produced while creating or changing the language structure: classes, attributes, relations, operations and metamodel persistence.',
        MODEL: 'Facts produced while users create, edit, save or remove model elements and their values.',
        VALIDATION: 'Quality evidence emitted when validation starts, finishes, detects a problem or confirms that a previous problem was resolved.',
        'QUICK FIX': 'Facts about previewing, applying, undoing or failing a model repair. Use verified resolution events when a downstream rule needs evidence.',
        OPERATION: 'Facts produced by invoking model operations or commands, including successful completion and execution failure.',
        'IMPORT AND EXPORT': 'Facts emitted after HYDRA completes an import or export operation.',
        'EVENT FLOW': 'Facts emitted when an event flow starts, completes, fails or derives another event.',
        GAMIFICATION: 'Facts emitted by the optional gamification runtime after evidence has been verified and recorded in its ledger.',
        PROJECT: 'Facts about project lifecycle, files, synchronization and workspace activity.',
        EDITOR: 'Facts related to editor sessions, representations, palettes, selection and visual interaction.',
        SYSTEM: 'Infrastructure and application lifecycle facts that are not tied to one model element.',
        'CUSTOM EVENT': 'Domain facts defined specifically for the active metamodel and its applications.',
        CUSTOM: 'Domain facts defined specifically for the active metamodel and its applications.'
    };
    return descriptions[normalizedEventCategory(category)] ?? 'Events grouped by a common functional purpose. Add only the facts that participate in this architecture.';
}

function eventCategoryUsage(category: string): string {
    const usages: Record<string, string> = {
        METAMODEL: 'Use these events when an automated behavior must react to a structural change in the metamodel rather than to instance data.',
        MODEL: 'Use these events when a validation, flow or integration must react to changes in model instances or to model persistence.',
        VALIDATION: 'Use these events to trigger remediation, reporting or downstream automation from a concrete validation result.',
        'QUICK FIX': 'Use these events when another action must run because a correction was started, applied, reverted or failed.',
        OPERATION: 'Use these events when downstream behavior depends on the result of invoking an operation defined by the metamodel.',
        'IMPORT AND EXPORT': 'Use these events to validate, transform or publish information after data enters or leaves HYDRA.',
        'EVENT FLOW': 'Use these events to chain flows, recover from flow failures or react to a derived event.',
        GAMIFICATION: 'Use these events only when behavior must react to a verified reward, penalty or compensation result.',
        PROJECT: 'Use these events for automation tied to project files, synchronization or project lifecycle.',
        EDITOR: 'Use these events for behavior tied to user interaction with an editor or visual representation.',
        SYSTEM: 'Use these events for application-level automation that is not owned by one model element.',
        'CUSTOM EVENT': 'Use a custom event when the fact belongs to your domain and no HYDRA system event expresses it precisely enough. Create a separate event for each distinct fact you want to model.',
        CUSTOM: 'Use a custom event when the fact belongs to your domain and no HYDRA system event expresses it precisely enough. Create a separate event for each distinct fact you want to model.'
    };
    return usages[normalizedEventCategory(category)] ?? 'Use one of these events only when its fact is the real trigger for the next validation, flow, integration or model action.';
}

function eventCategoryExample(category: string): string {
    const examples: Record<string, string> = {
        METAMODEL: 'Example: after “Metamodel attribute created”, launch a naming-rule check for the new attribute.',
        MODEL: 'Example: after “Model saved”, run the validation profile and export only if no blocking problem remains.',
        VALIDATION: 'Example: after “Problem detected”, offer the compatible quick fix and record the diagnostic in the activity log.',
        'QUICK FIX': 'Example: after “Quick fix applied”, run validation again to confirm that the original problem disappeared.',
        OPERATION: 'Example: after “Operation completed”, update a dependent model or notify an external integration.',
        'IMPORT AND EXPORT': 'Example: after “Import completed”, validate the imported model before it is opened for editing.',
        'EVENT FLOW': 'Example: after “Event flow failed”, route execution to an error-recovery flow.',
        GAMIFICATION: 'Example: after “Gamification award granted”, refresh the user progress panel from the verified ledger result.',
        PROJECT: 'Example: after a project synchronization event, refresh generated artifacts that depend on the changed files.',
        EDITOR: 'Example: after a representation changes, recalculate the tools that are valid for the active metamodel.',
        SYSTEM: 'Example: after an application lifecycle event, refresh only the services that depend on that state.',
        'CUSTOM EVENT': 'Example: create “Payment approved” and connect it to a shipping event flow.',
        CUSTOM: 'Example: create “Payment approved” and connect it to a shipping event flow.'
    };
    return examples[normalizedEventCategory(category)] ?? 'Example: connect the selected event to the validation, event flow or integration that must run immediately afterwards.';
}
