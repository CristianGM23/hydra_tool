import { HydraButton } from '../../design-system/HydraButton';
import { explainInputError } from '../../api/inputErrorExplanation';
import { indexBy } from '../../utils/indexBy';
import { applyEdgeChanges, applyNodeChanges, Position } from '@xyflow/react';
import type { Connection, Edge, EdgeChange, Node, NodeChange } from '@xyflow/react';
import { useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { DesignerCommandBar, DesignerCommandGroup } from '../../designer-ui/DesignerCommandBar';
import { useHydraLanguage } from '../../i18n';
import { useBoundedDesignerHistory } from '../../designer-ui/useBoundedDesignerHistory';
import { sameDiagramGeometry } from '../../designer-ui/diagramGeometry';
import { DesignerDropdown } from '../../designer-ui/DesignerDropdown';
import {
    DesignerFieldLabel,
    DesignerGuideButton,
} from '../../designer-ui/DesignerFieldHelp';
import { HydraAdaptiveSelect } from '../../components/HydraAdaptiveSelect';
import { HydraVirtualList } from '../../design-system/HydraVirtualList';
import { HydraDiagnosticList } from '../../design-system/HydraDiagnosticList';
import { diagnosticAsActionableError } from '../../design-system/HydraDiagnosticSupport';
import { LayerInterchangeDialog } from '../../layer-io';
import type { LayerImportRequest } from '../../layer-io';
import type { MetaModel } from '../../metamodel-designer/types';
import { analyzeEventFlowImport, EVENT_FLOW_INTERCHANGE_FORMATS, exportEventFlowLayer, importEventFlowLayer, type EventFlowInterchangeFormat } from '../../layer-io/eventFlowInterchange';
import { useResizableDesignerSidebar } from '../../designer-ui/useResizableDesignerSidebar';
import { DEFAULT_HYDRA_SOURCE_PORT, DEFAULT_HYDRA_TARGET_PORT, normalizeHydraConnectionVisual } from '../../designer-ui/HydraConnectionVisual';
import { HydraConnectionVisualEditor } from '../../designer-ui/HydraConnectionVisualEditor';
import type { HydraEditableConnectionVisual } from '../../designer-ui/HydraConnectionVisualEditor';
import { isMissingValidationResource } from '../../validation/http';
import {
    checkEventFlows,
    createEventFlows,
    loadEventFlowDiagram,
    loadEventFlowRuntimeState,
    loadEventFlowRuntimeStatus,
    loadEventFlowRuntimeTraces,
    loadEventFlows,
    replaceEventFlows,
    saveEventFlowDiagram,
    startEventFlowRuntime,
    stopEventFlowRuntime,
    tickEventFlowRuntime,
    clearEventFlowRuntimeState
} from './api';
import {
    createEmptyEventFlowDiagram,
    createEmptyEventFlowModel,
    createFlowId,
    createFlowTransition,
    synchronizeEventFlowDiagram
} from './eventFlowFactory';
import { findEventFlowBlockingError } from './eventFlowValidation';
import { describeFlowNode, FLOW_NODE_PRESENTATIONS, FLOW_TRANSITION_PRESENTATIONS, flowNodeOptionHelp } from './flowPresentation';
import type { EventTypeDefinition } from '../types';
import { EventFlowVisualEditor } from './EventFlowVisualEditor';
import { removeEventFlowSelection } from './eventFlowSelection';
import { EventFlowRuntimePanel } from './EventFlowRuntimePanel';
import { EventFlowStructureTree } from './EventFlowStructureTree';
import type { EventFlowStructureSelection } from './EventFlowStructureTree';
import type { EventFlowVisualEdgeData, EventFlowVisualNodeData } from './EventFlowVisualEditor';
import type {
    EventFlowDiagramDocument,
    EventFlowModel,
    EventFlowRuntimeSnapshot,
    EventFlowRuntimeStatus,
    FlowDefinition,
    FlowDiagnostic,
    FlowNode,
    FlowNodeKind,
    FlowTransition,
    FlowTransitionKind,
    FlowExecutionTrace
} from './types';
import '@xyflow/react/dist/style.css';
import { FlowForm } from './EventFlowDesignerParts';
import { diagnosticSeverityFor, flowNodeCategory, mergeFlowsById } from './eventFlowDesignerSupport';
import { FlowNodeProperties, FlowTransitionProperties } from './EventFlowHighLevelProperties';
import { appendFlow, appendFlowCompensation, appendFlowNode, appendFlowTransition, appendFlowVariable, removeFlow, removeFlowNode, removeFlowTransition, renameFlow, renameFlowNode, renameFlowTransition, replaceFlow, replaceFlowNode, replaceFlowTransition } from './eventFlowDesignerModel';
import './event-flow-designer.css';
import { hydraGeometryChanges, readHydraNodeSize } from '../../designer-ui/hydraNodeGeometry';
import { layoutEventFlowAutomatic, layoutEventFlowElk } from './eventFlowElkLayout';
import { useHydraOverwriteConfirmation } from '../../hooks/useHydraOverwriteConfirmation';
import type { HydraPersistedIdentity } from '../../hooks/hydraOverwritePolicy';
import { eventFlowOverwriteIdentities } from './eventFlowOverwriteIdentity';
type ViewMode = 'FORM' | 'STRUCTURE' | 'DIAGRAM' | 'SOURCE' | 'RUNTIME';
type FlowFormSelection = 'FLOW' | 'NODE' | 'TRANSITION';
const nodeKinds: FlowNodeKind[] = [
    'START', 'SOURCE', 'EVENT_STEP', 'FILTER', 'MAP', 'ROUTE', 'MERGE', 'DEDUPLICATE', 'WINDOW', 'AGGREGATE',
    'JOIN', 'SEQUENCE', 'PATTERN', 'ABSENCE', 'ACTION_STEP', 'OPERATION_STEP', 'COMMAND_STEP', 'EMIT',
    'SUCCESS_END', 'ERROR_END', 'COMPENSATION_STEP', 'ERROR_HANDLER', 'DECISION', 'PARALLEL_SPLIT', 'TIMEOUT',
    'RETRY', 'WAIT_FOR_EVENT', 'MANUAL_STEP', 'FALLBACK'
];
interface EventFlowDesignerSnapshot {
    model: EventFlowModel;
    diagram: EventFlowDiagramDocument;
    selectedFlowId: string;
    selectedNodeId: string;
    selectedTransitionId: string;
    formSelection: FlowFormSelection;
}
interface Props {
    metamodel?: MetaModel;
    metamodelId: string;
    eventDefinitionModelId: string;
    eventTypeIds: string[];
    customEventTypes: EventTypeDefinition[];
    persisted: boolean;
    onError: (message: string) => void;
    onMessage: (message: string) => void;
}

export function EventFlowDesigner(props: Props) {
    return <EventFlowDesignerSession key={`${props.metamodelId}:${props.eventDefinitionModelId}:${props.persisted}`} {...props} />;
}

function EventFlowDesignerSession({ metamodel, metamodelId, eventDefinitionModelId, eventTypeIds, customEventTypes, persisted, onError, onMessage }: Props) {
    const { t, language } = useHydraLanguage();
    const sidebar = useResizableDesignerSidebar({ storageKey: 'hydra:event-flow-designer:sidebar-width', ariaLabel: 'Resize event-flow palette', initialWidth: 330 });

    const initial = createEmptyEventFlowModel(metamodelId, eventDefinitionModelId);
    const [model, setModel] = useState<EventFlowModel>(initial);
    const [etag, setEtag] = useState('');
    const [persistedFlowIdentities, setPersistedFlowIdentities] = useState<HydraPersistedIdentity[]>([]);
    const currentFlowIdentities = useMemo(() => eventFlowOverwriteIdentities(model), [model]);
    const { confirmOverwrite, overwriteDialogs } = useHydraOverwriteConfirmation({
        current: currentFlowIdentities,
        persisted: persistedFlowIdentities
    });
    const [diagram, setDiagram] = useState<EventFlowDiagramDocument>(() => createEmptyEventFlowDiagram(initial));
    const [diagramEtag, setDiagramEtag] = useState('');
    const [selectedFlowId, setSelectedFlowId] = useState('');
    const [selectedNodeId, setSelectedNodeId] = useState('');
    const [selectedTransitionId, setSelectedTransitionId] = useState('');
    const [formSelection, setFormSelection] = useState<FlowFormSelection>('FLOW');
    const [newNodeKind, setNewNodeKind] = useState<FlowNodeKind>('EVENT_STEP');
    const [filter, setFilter] = useState('');
    const [view, setView] = useState<ViewMode>('FORM');
    const [structureTabOpen, setStructureTabOpen] = useState(true);
    const [structureCollapsed, setStructureCollapsed] = useState(false);
    const [structurePropertiesOpen, setStructurePropertiesOpen] = useState(true);
    const [structurePropertiesCollapsed, setStructurePropertiesCollapsed] = useState(false);
    const [structureFilter, setStructureFilter] = useState('');
    const [structureInspectorSelection, setStructureInspectorSelection] = useState<EventFlowStructureSelection>(null);
    const [source, setSource] = useState(JSON.stringify(initial, null, 2));
    const [dirty, setDirty] = useState(false);
    const [runtime, setRuntime] = useState<EventFlowRuntimeStatus | null>(null);
    const [runtimeSnapshot, setRuntimeSnapshot] = useState<EventFlowRuntimeSnapshot | null>(null);
    const [runtimeTraces, setRuntimeTraces] = useState<FlowExecutionTrace[]>([]);
    const [selectedRuntimeTrace, setSelectedRuntimeTrace] = useState<FlowExecutionTrace | null>(null);
    const [diagnostics, setDiagnostics] = useState<FlowDiagnostic[]>([]);
    const [layerIoMode, setLayerIoMode] = useState<'IMPORT' | 'EXPORT' | null>(null);
    const dragSnapshot = useRef<EventFlowDesignerSnapshot | null>(null);
    const onErrorRef = useRef(onError);
    const { remember, undo, redo, clear: clearHistory, canUndo, canRedo } = useBoundedDesignerHistory(snapshot, restore, 50);
    useEffect(() => { onErrorRef.current = onError; }, [onError]);

    useEffect(() => {
        let active = true;
        const empty = createEmptyEventFlowModel(metamodelId, eventDefinitionModelId);
        if (!persisted) return () => { active = false; };
        void Promise.allSettled([
            loadEventFlows(metamodelId),
            loadEventFlowDiagram(metamodelId),
            loadEventFlowRuntimeStatus(metamodelId),
            loadEventFlowRuntimeTraces(metamodelId),
            loadEventFlowRuntimeState(metamodelId)
        ]).then(results => {
            if (!active) return;
            const flowResult = results[0];
            let loadedModel = empty;
            if (flowResult.status === 'fulfilled') {
                loadedModel = flowResult.value.model;
                setModel(loadedModel);
                setEtag(flowResult.value.etag);
                setPersistedFlowIdentities(eventFlowOverwriteIdentities(loadedModel));
                setSelectedFlowId(loadedModel.flows[0]?.id ?? '');
                setSource(JSON.stringify(loadedModel, null, 2));
            } else if (!isMissingValidationResource(flowResult.reason)) {
                onErrorRef.current(flowResult.reason instanceof Error ? flowResult.reason.message : 'Could not load event flows');
            }
            const diagramResult = results[1];
            if (diagramResult.status === 'fulfilled') {
                setDiagram(synchronizeEventFlowDiagram(loadedModel, diagramResult.value.diagram));
                setDiagramEtag(diagramResult.value.etag);
            } else if (isMissingValidationResource(diagramResult.reason)) {
                setDiagram(createEmptyEventFlowDiagram(loadedModel));
            } else {
                console.warn('Could not load saved EventFlow diagram; rebuilding it from the semantic flow.', diagramResult.reason); setDiagram(createEmptyEventFlowDiagram(loadedModel)); setDiagramEtag('');
            }
            const runtimeResult = results[2];
            if (runtimeResult.status === 'fulfilled') setRuntime(runtimeResult.value);
            const traceResult = results[3];
            if (traceResult.status === 'fulfilled') setRuntimeTraces(traceResult.value);
            const stateResult = results[4];
            if (stateResult.status === 'fulfilled') setRuntimeSnapshot(stateResult.value);
        });
        return () => { active = false; };
    }, [eventDefinitionModelId, metamodelId, persisted]);

    const flowById = useMemo(() => indexBy(model.flows, flow => flow.id), [model.flows]);
    const selectedFlow = flowById.get(selectedFlowId) ?? null;
    const selectedNodeById = useMemo(() => indexBy(selectedFlow?.nodes ?? [], node => node.id), [selectedFlow?.nodes]);
    const selectedTransitionById = useMemo(() => indexBy(selectedFlow?.transitions ?? [], transition => transition.id), [selectedFlow?.transitions]);
    const diagramNodeById = useMemo(() => indexBy(diagram.nodes, node => node.nodeId), [diagram.nodes]);
    const runtimeNodeById = useMemo(() => indexBy(selectedRuntimeTrace?.nodes ?? [], node => node.nodeId), [selectedRuntimeTrace?.nodes]);
    const selectedNode = selectedNodeById.get(selectedNodeId) ?? null;
    const selectedTransition = selectedTransitionById.get(selectedTransitionId) ?? null;
    const normalizedFilter = filter.trim().toLowerCase();
    const filteredFlows = model.flows.filter(flow => [flow.name, flow.id, flow.description]
        .some(value => value.toLowerCase().includes(normalizedFilter)));
    const flowNodeIds = new Set(selectedFlow?.nodes.map(node => node.id) ?? []);
    const nodes: Node<EventFlowVisualNodeData>[] = (selectedFlow?.nodes ?? []).map(node => {
        const viewNode = diagramNodeById.get(node.id);
        const traceStatus = runtimeNodeById.get(node.id)?.status;
        const severity = traceStatus === 'FAILED' ? 'ERROR' : diagnosticSeverityFor(node.id, diagnostics);
        return {
            id: node.id,
            type: 'eventFlowNode',
            position: { x: viewNode?.x ?? 80, y: viewNode?.y ?? 80 },
            // Persisted dimensions use style; runtime measurements remain local during drag.
            style: { width: viewNode?.width ?? 220, height: viewNode?.height ?? 92 },
            sourcePosition: Position.Right,
            targetPosition: Position.Left,
            data: { name: node.name, kind: node.kind, kindLabel: FLOW_NODE_PRESENTATIONS[node.kind].label, description: `${describeFlowNode(node, customEventTypes)}${traceStatus ? ` Last execution: ${traceStatus}.` : ''}`, referenceId: node.referenceId, eventTypeId: node.eventTypeId, severity },
            className: `event-flow-visual-node kind-${node.kind.toLowerCase()}${severity ? ` severity-${severity.toLowerCase()}` : ''}`,
            selected: node.id === selectedNodeId
        };
    });
    const diagramEdgeViews = new Map(diagram.edges.map(edge => [edge.transitionId, edge]));
    const edges: Edge<EventFlowVisualEdgeData>[] = (selectedFlow?.transitions ?? []).map(transition => {
        const severity = diagnosticSeverityFor(transition.id, diagnostics);
        const viewEdge = diagramEdgeViews.get(transition.id);
        const appearance = normalizeHydraConnectionVisual({ ...(viewEdge ?? {}), connectionLabel: viewEdge?.label, connectionNote: viewEdge?.note });
        return {
            id: transition.id,
            source: transition.sourceNodeId,
            target: transition.targetNodeId,
            label: viewEdge?.label || transition.label || FLOW_TRANSITION_PRESENTATIONS[transition.kind].label,
            data: { kind: transition.kind, severity, ...appearance },
            type: 'hydraDirectedEdge',
            sourceHandle: viewEdge?.sourceHandle || DEFAULT_HYDRA_SOURCE_PORT,
            targetHandle: viewEdge?.targetHandle || DEFAULT_HYDRA_TARGET_PORT,
            zIndex: 8,
            interactionWidth: 30,
            animated: transition.kind === 'ON_ERROR' || transition.kind === 'COMPENSATES',
                style: { strokeWidth: 2.2 },
            selected: transition.id === selectedTransitionId,
            className: severity ? `severity-${severity.toLowerCase()}` : undefined
        };
    });

    function snapshot(): EventFlowDesignerSnapshot {
        return { model, diagram, selectedFlowId, selectedNodeId, selectedTransitionId, formSelection };
    }

    function restore(value: EventFlowDesignerSnapshot): void {
        setModel(value.model);
        setDiagram(synchronizeEventFlowDiagram(value.model, value.diagram));
        setSelectedFlowId(value.selectedFlowId);
        setSelectedNodeId(value.selectedNodeId);
        setSelectedTransitionId(value.selectedTransitionId);
        setFormSelection(value.formSelection);
        setSource(JSON.stringify(value.model, null, 2));
        setDirty(true);
    }


    function updateModel(next: EventFlowModel): void {
        setModel(next);
        setSource(JSON.stringify(next, null, 2));
        setDiagram(current => synchronizeEventFlowDiagram(next, current));
        setDirty(true);
    }

    function updateFlow(flow: FlowDefinition): void { updateModel(replaceFlow(model, flow)); }

    function renameFlowId(previousId: string, nextId: string): void {
        if (previousId === nextId) return;
        updateModel(renameFlow(model, previousId, nextId));
        setSelectedFlowId(nextId);
    }

    function updateNode(node: FlowNode): void { if (selectedFlow !== null) updateFlow(replaceFlowNode(selectedFlow, node)); }

    function renameNodeId(previousId: string, nextId: string): void {
        if (selectedFlow === null || previousId === nextId) return;
        updateFlow(renameFlowNode(selectedFlow, previousId, nextId));
        setDiagram(current => ({ ...current, nodes: current.nodes.map(node => node.nodeId === previousId ? { ...node, nodeId: nextId } : node) }));
        setSelectedNodeId(nextId);
    }

    function updateTransition(transition: FlowTransition): void { if (selectedFlow !== null) updateFlow(replaceFlowTransition(selectedFlow, transition)); }

    function renameTransitionId(previousId: string, nextId: string): void {
        if (selectedFlow === null || previousId === nextId) return;
        updateFlow(renameFlowTransition(selectedFlow, previousId, nextId));
        setDiagram(current => ({ ...current, edges: current.edges.map(edge => edge.transitionId === previousId ? { ...edge, transitionId: nextId } : edge) }));
        setSelectedTransitionId(nextId);
    }

    function addFlow(): void {
        remember();
        const added = appendFlow(model);
        updateModel(added.model);
        setSelectedFlowId(added.flow.id); setSelectedNodeId(''); setSelectedTransitionId(''); setFormSelection('FLOW');
        setStructureInspectorSelection({ kind: 'FLOW', flowId: added.flow.id, id: added.flow.id }); setView('FORM');
    }

    function deleteFlow(): void {
        if (selectedFlow === null) return;
        remember(); updateModel(removeFlow(model, selectedFlow.id)); setSelectedFlowId(''); setSelectedNodeId(''); setSelectedTransitionId('');
    }

    function addNode(kind: FlowNodeKind, position?: { x: number; y: number }, stayInDiagram = false): void {
        if (selectedFlow === null) { onError('Create or select a flow first.'); return; }
        remember();
        const added = appendFlowNode(selectedFlow, kind);
        const nextModel = replaceFlow(model, added.flow);
        let nextDiagram = synchronizeEventFlowDiagram(nextModel, diagram);
        if (position !== undefined) nextDiagram = { ...nextDiagram, nodes: nextDiagram.nodes.map(item => item.nodeId === added.node.id ? { ...item, x: position.x, y: position.y } : item) };
        setModel(nextModel); setSource(JSON.stringify(nextModel, null, 2)); setDiagram(nextDiagram);
        setSelectedNodeId(added.node.id); setSelectedTransitionId(''); setFormSelection('NODE'); setView(stayInDiagram ? 'DIAGRAM' : 'FORM'); setDirty(true);
    }

    function deleteNode(): void {
        if (selectedFlow === null || selectedNode === null) return;
        remember(); updateFlow(removeFlowNode(selectedFlow, selectedNode.id)); setSelectedNodeId(''); setFormSelection('FLOW');
    }

    function addTransition(): void {
        if (selectedFlow === null) { onError('A transition requires at least two nodes.'); return; }
        const added = appendFlowTransition(selectedFlow, selectedNode);
        if (added === null) { onError('A transition requires at least two nodes.'); return; }
        remember(); updateFlow(added.flow); setSelectedTransitionId(added.transition.id); setSelectedNodeId(''); setFormSelection('TRANSITION'); setView('FORM');
    }

    function deleteTransition(): void {
        if (selectedFlow === null || selectedTransition === null) return;
        remember(); updateFlow(removeFlowTransition(selectedFlow, selectedTransition.id)); setSelectedTransitionId(''); setFormSelection('FLOW');
    }

    function addVariable(): void { if (selectedFlow !== null) { remember(); updateFlow(appendFlowVariable(selectedFlow)); } }

    function addCompensation(): void {
        if (selectedFlow === null) return;
        const added = appendFlowCompensation(selectedFlow);
        if (added !== null) { remember(); updateFlow(added.flow); }
    }

    function onNodesChange(changes: NodeChange<Node<EventFlowVisualNodeData>>[]): void {
        const selectedChange = changes.find(change => change.type === 'select' && change.selected);
        if (selectedChange !== undefined && 'id' in selectedChange) selectVisualNode(nodes.find(node => node.id === selectedChange.id));
        const geometryChanges = hydraGeometryChanges(changes);
        if (geometryChanges.length === 0) return;
        const next = applyNodeChanges(geometryChanges, nodes);
        const nextGeometry = next.map(node => { const size = readHydraNodeSize(node, 180, 72); return { nodeId: node.id, x: node.position.x, y: node.position.y,
            width: size.width, height: size.height, collapsed: false }; });
        setDiagram(current => sameDiagramGeometry(current.nodes.filter(viewNode => flowNodeIds.has(viewNode.nodeId)), nextGeometry)
            ? current : { ...current, nodes: [...current.nodes.filter(viewNode => !flowNodeIds.has(viewNode.nodeId)), ...nextGeometry] });
        setDirty(true);
    }

    function onEdgesChange(changes: EdgeChange<Edge<EventFlowVisualEdgeData>>[]): void {
        if (selectedFlow === null) return;
        const nextEdges = applyEdgeChanges(changes, edges);
        if (changes.some(change => change.type === 'remove')) {
            remember();
            updateFlow({ ...selectedFlow, transitions: selectedFlow.transitions.filter(transition => nextEdges.some(edge => edge.id === transition.id)) });
        }
        const selectedChange = changes.find(change => change.type === 'select' && change.selected);
        if (selectedChange !== undefined && 'id' in selectedChange) {
            setSelectedTransitionId(selectedChange.id);
            setSelectedNodeId('');
            setFormSelection('TRANSITION');
        }
    }

    function onConnect(connection: Connection): void {
        if (selectedFlow === null || connection.source === null || connection.target === null) return;
        const sourceKind = selectedFlow.nodes.find(node => node.id === connection.source)?.kind;
        const kind: FlowTransitionKind = sourceKind === 'DECISION' || sourceKind === 'ROUTE' ? 'TRUE' : 'NEXT';
        remember();
        const transition = createFlowTransition(connection.source, connection.target, kind);
        updateFlow({ ...selectedFlow, transitions: [...selectedFlow.transitions, transition] });
        setDiagram(current => ({ ...synchronizeEventFlowDiagram({ ...model, flows: model.flows.map(flow => flow.id === selectedFlow.id ? { ...selectedFlow, transitions: [...selectedFlow.transitions, transition] } : flow) }, current), edges: [
            ...synchronizeEventFlowDiagram({ ...model, flows: model.flows.map(flow => flow.id === selectedFlow.id ? { ...selectedFlow, transitions: [...selectedFlow.transitions, transition] } : flow) }, current).edges.filter(edge => edge.transitionId !== transition.id),
            { transitionId: transition.id, points: [], sourceHandle: connection.sourceHandle ?? DEFAULT_HYDRA_SOURCE_PORT, targetHandle: connection.targetHandle ?? DEFAULT_HYDRA_TARGET_PORT, label: '', note: '', routeStyle: 'ORTHOGONAL', lineStyle: 'SOLID', sourceTerminator: 'NONE', targetTerminator: 'ARROW_CLOSED' }
        ] }));
        setSelectedTransitionId(transition.id);
        setFormSelection('TRANSITION');
    }

    function applySource(): void {
        try {
            const parsed = JSON.parse(source) as EventFlowModel;
            if (parsed.metamodelId !== metamodelId) { onError('Source metamodelId must match the current metamodel.'); return; }
            const error = findEventFlowBlockingError(parsed);
            if (error !== '') { onError(error); return; }
            remember();
            setModel(parsed);
            setDiagram(current => synchronizeEventFlowDiagram(parsed, current));
            setSelectedFlowId(parsed.flows[0]?.id ?? '');
            setSelectedNodeId('');
            setSelectedTransitionId('');
            setFormSelection('FLOW');
            setDirty(true);
            onMessage('Event flow source applied to the HYDRA document');
        } catch (error) {
            onError(explainInputError(error, 'Invalid event-flow JSON', 'the EventFlow source tab'));
        }
    }

    async function validate(): Promise<void> {
        const localError = findEventFlowBlockingError(model);
        if (localError !== '') { onError(localError); return; }
        try {
            const report = await checkEventFlows(model);
            setDiagnostics(report.diagnostics);
            const errors = report.diagnostics.filter(item => item.severity === 'ERROR');
            if (errors.length > 0) onError(diagnosticAsActionableError(errors[0], language, 'event flow diagram', errors.length));
            else onMessage(`Event flows valid (${report.diagnostics.length} diagnostic(s))`);
        } catch (error) {
            onError(explainInputError(error, 'Could not check event flows', 'EventFlow Designer'));
        }
    }

    async function save(): Promise<boolean> {
        if (!persisted) { onError('Save the metamodel before configuring event flows.'); return false; }
        const localError = findEventFlowBlockingError(model);
        if (localError !== '') { onError(localError); return false; }
        if (!await confirmOverwrite()) return false;
        try {
            const saved = etag === '' ? await createEventFlows(model) : await replaceEventFlows(model, etag);
            const synchronized = synchronizeEventFlowDiagram(saved.model, diagram);
            const savedDiagram = await saveEventFlowDiagram(synchronized, diagramEtag);
            setModel(saved.model);
            setEtag(saved.etag);
            setPersistedFlowIdentities(eventFlowOverwriteIdentities(saved.model));
            setDiagram(savedDiagram.diagram);
            setDiagramEtag(savedDiagram.etag);
            setSource(JSON.stringify(saved.model, null, 2));
            setDiagnostics(saved.checkReport.diagnostics);
            clearHistory();
            setDirty(false);
            onMessage('Event flows and diagram saved');
            return true;
        } catch (error) {
            onError(explainInputError(error, 'Could not save event flows', 'EventFlow Designer'));
            return false;
        }
    }

    async function toggleRuntime(): Promise<void> {
        try {
            const status = runtime?.active ? await stopEventFlowRuntime(metamodelId) : await startEventFlowRuntime(metamodelId);
            setRuntime(status);
            onMessage(status.active ? 'Event-flow runtime started' : 'Event-flow runtime stopped');
        } catch (error) {
            onError(explainInputError(error, 'Could not change flow runtime', 'EventFlow Designer'));
        }
    }

    async function refreshRuntime(): Promise<void> {
        try { const [status, traces, state] = await Promise.all([loadEventFlowRuntimeStatus(metamodelId), loadEventFlowRuntimeTraces(metamodelId), loadEventFlowRuntimeState(metamodelId)]); setRuntime(status); setRuntimeTraces(traces); setRuntimeSnapshot(state); }
        catch(error){ onError(explainInputError(error, 'Could not load flow runtime', 'EventFlow Designer')); }
    }

    async function processRuntimeTimers(): Promise<void> {
        try { setRuntime(await tickEventFlowRuntime(metamodelId)); const [traces,state]=await Promise.all([loadEventFlowRuntimeTraces(metamodelId),loadEventFlowRuntimeState(metamodelId)]); setRuntimeTraces(traces); setRuntimeSnapshot(state); onMessage('Due waits, retries and timers processed'); }
        catch(error){ onError(explainInputError(error, 'Could not process runtime timers', 'EventFlow Designer')); }
    }

    async function clearRuntime(): Promise<void> {
        try { await clearEventFlowRuntimeState(metamodelId); setRuntimeTraces([]); setRuntimeSnapshot(null); setSelectedRuntimeTrace(null); setRuntime(await loadEventFlowRuntimeStatus(metamodelId)); onMessage('Event-flow runtime state cleared'); }
        catch(error){ onError(explainInputError(error, 'Could not clear flow runtime', 'EventFlow Designer')); }
    }

    function selectVisualNode(node: Node<EventFlowVisualNodeData> | undefined): void {
        if (node === undefined) return;
        setSelectedNodeId(node.id);
        setSelectedTransitionId('');
        setFormSelection('NODE');
    }

    function selectVisualEdge(edge: Edge<EventFlowVisualEdgeData> | undefined): void {
        if (edge === undefined) return;
        setSelectedTransitionId(edge.id);
        setSelectedNodeId('');
        setFormSelection('TRANSITION');
    }

    function deleteVisualSelection(): void {
        if (selectedTransition !== null) { deleteTransition(); return; }
        if (selectedNode !== null) deleteNode();
    }

    function deleteVisualItems(nodeIds: string[], transitionIds: string[]): void {
        if (selectedFlow === null || (nodeIds.length === 0 && transitionIds.length === 0)) return;
        remember(); updateFlow(removeEventFlowSelection(selectedFlow, nodeIds, transitionIds));
        setSelectedNodeId(''); setSelectedTransitionId(''); setFormSelection('FLOW');
    }

    function duplicateVisualSelection(): void {
        if (selectedFlow === null) return;
        if (selectedNode !== null) {
            remember();
            const clone: FlowNode = {
                ...selectedNode,
                id: createFlowId('node'),
                name: `${selectedNode.name} copy`,
                configuration: { ...selectedNode.configuration },
                retryPolicy: { ...selectedNode.retryPolicy },
                timeoutPolicy: { ...selectedNode.timeoutPolicy, timeoutTransitionId: '' }
            };
            const nextFlow = { ...selectedFlow, nodes: [...selectedFlow.nodes, clone] };
            const nextModel = { ...model, flows: model.flows.map(flow => flow.id === nextFlow.id ? nextFlow : flow) };
            const originalView = diagram.nodes.find(node => node.nodeId === selectedNode.id);
            let nextDiagram = synchronizeEventFlowDiagram(nextModel, diagram);
            if (originalView !== undefined) nextDiagram = { ...nextDiagram, nodes: nextDiagram.nodes.map(node => node.nodeId === clone.id ? { ...node, x: originalView.x + 40, y: originalView.y + 40 } : node) };
            setModel(nextModel);
            setDiagram(nextDiagram);
            setSource(JSON.stringify(nextModel, null, 2));
            setSelectedNodeId(clone.id);
            setSelectedTransitionId('');
            setFormSelection('NODE');
            setDirty(true);
            return;
        }
        if (selectedTransition !== null) {
            remember();
            const clone: FlowTransition = { ...selectedTransition, id: createFlowId('transition'), label: selectedTransition.label ? `${selectedTransition.label} copy` : '' };
            updateFlow({ ...selectedFlow, transitions: [...selectedFlow.transitions, clone] });
            setSelectedTransitionId(clone.id);
            setFormSelection('TRANSITION');
        }
    }

    function autoLayoutVisual(): void {
        if (selectedFlow === null) return;
        remember(); setDiagram(layoutEventFlowAutomatic(selectedFlow, diagram)); setDirty(true);
    }
    function elkLayoutVisual(): void {
        if (selectedFlow === null) return;
        remember(); setDiagram(layoutEventFlowElk(selectedFlow, diagram)); setDirty(true);
    }

    function beginVisualDrag(): void { dragSnapshot.current = snapshot(); }
    function endVisualDrag(): void {
        if (dragSnapshot.current === null) return;
        remember(dragSnapshot.current);
        dragSnapshot.current = null;
    }

    function structureSelection(): EventFlowStructureSelection {
        if (structureInspectorSelection !== null) return structureInspectorSelection;
        if (selectedFlow === null) return null;
        if (formSelection === 'NODE' && selectedNode !== null) return { kind: 'NODE', flowId: selectedFlow.id, id: selectedNode.id };
        if (formSelection === 'TRANSITION' && selectedTransition !== null) return { kind: 'TRANSITION', flowId: selectedFlow.id, id: selectedTransition.id };
        return { kind: 'FLOW', flowId: selectedFlow.id, id: selectedFlow.id };
    }

    function selectStructureItem(selection: Exclude<EventFlowStructureSelection, null>): void {
        setStructureInspectorSelection(selection);
        setSelectedFlowId(selection.flowId);
        if (selection.kind === 'NODE') { setSelectedNodeId(selection.id); setSelectedTransitionId(''); setFormSelection('NODE'); }
        else if (selection.kind === 'TRANSITION') { setSelectedTransitionId(selection.id); setSelectedNodeId(''); setFormSelection('TRANSITION'); }
        else { setSelectedNodeId(''); setSelectedTransitionId(''); setFormSelection('FLOW'); }
        setStructurePropertiesOpen(true);
        setStructurePropertiesCollapsed(false);
    }

    function structureInspector(): ReactNode {
        if (structureInspectorSelection === null) return visualInspector();
        const flow = model.flows.find(item => item.id === structureInspectorSelection.flowId) ?? null;
        if (flow === null) return <div className="event-flow-inspector-empty"><strong>Selection unavailable</strong><span>Select an existing flow element from the structure tree.</span></div>;
        if (structureInspectorSelection.kind === 'NODE') {
            const node = flow.nodes.find(item => item.id === structureInspectorSelection.id) ?? null;
            if (node !== null) return <FlowNodeProperties node={node} transitions={flow.transitions} eventTypeIds={eventTypeIds} customEventTypes={customEventTypes} onChange={next => updateFlow({ ...flow, nodes: flow.nodes.map(item => item.id === next.id ? next : item) })} onRenameId={renameNodeId} onDelete={deleteNode} />;
        }
        if (structureInspectorSelection.kind === 'TRANSITION') {
            const transition = flow.transitions.find(item => item.id === structureInspectorSelection.id) ?? null;
            if (transition !== null) return <FlowTransitionProperties transition={transition} nodes={flow.nodes} onChange={next => updateFlow({ ...flow, transitions: flow.transitions.map(item => item.id === next.id ? next : item) })} onRenameId={renameTransitionId} onDelete={deleteTransition} />;
        }
        return <FlowForm flow={flow} eventTypeIds={eventTypeIds} customEventTypes={customEventTypes} onChange={updateFlow} onRenameId={renameFlowId} onAddVariable={addVariable} onAddCompensation={addCompensation} onOpenDiagram={() => setView('DIAGRAM')} onDelete={deleteFlow} />;
    }

    function updateSelectedFlowConnectionVisual(next: HydraEditableConnectionVisual): void {
        if (selectedTransition === null) return;
        remember();
        setDiagram(current => ({ ...current, edges: current.edges.map(edge => edge.transitionId === selectedTransition.id ? { ...edge, ...next, sourceHandle: next.sourceHandle ?? DEFAULT_HYDRA_SOURCE_PORT, targetHandle: next.targetHandle ?? DEFAULT_HYDRA_TARGET_PORT, label: next.label ?? '', note: next.note ?? '', routeStyle: next.routeStyle ?? 'ORTHOGONAL', lineStyle: next.lineStyle ?? 'SOLID' } : edge) }));
        if ((next.label ?? '') !== selectedTransition.label) updateTransition({ ...selectedTransition, label: next.label ?? '' });
        setDirty(true);
    }

    function visualInspector(): ReactNode {
        if (selectedFlow === null) return <div className="event-flow-inspector-empty"><strong>No flow selected</strong><span>Create or select a flow before using the diagram.</span></div>;
        if (selectedNode !== null) return <FlowNodeProperties node={selectedNode} transitions={selectedFlow.transitions} eventTypeIds={eventTypeIds} customEventTypes={customEventTypes} onChange={updateNode} onRenameId={renameNodeId} onDelete={deleteNode} />;
        if (selectedTransition !== null) {
            const edgeView = diagram.edges.find(edge => edge.transitionId === selectedTransition.id);
            return <><FlowTransitionProperties transition={selectedTransition} nodes={selectedFlow.nodes} onChange={updateTransition} onRenameId={renameTransitionId} onDelete={deleteTransition} /><HydraConnectionVisualEditor value={{ ...edgeView, sourceHandle: edgeView?.sourceHandle, targetHandle: edgeView?.targetHandle, label: edgeView?.label ?? selectedTransition.label, note: edgeView?.note }} onChange={updateSelectedFlowConnectionVisual} /></>;
        }
        return <FlowForm flow={selectedFlow} eventTypeIds={eventTypeIds} customEventTypes={customEventTypes} onChange={updateFlow} onRenameId={renameFlowId} onAddVariable={addVariable} onAddCompensation={addCompensation} onOpenDiagram={() => setView('DIAGRAM')} onDelete={deleteFlow} />;
    }

    function transferEventFlows(request: LayerImportRequest<EventFlowInterchangeFormat>) {
        const imported = importEventFlowLayer(request.content, request.format, model, customEventTypes);
        const nextModel: EventFlowModel = request.strategy === 'REPLACE'
            ? { ...imported.model, id: model.id, metamodelId, eventDefinitionModelId, expressionModelId: model.expressionModelId }
            : { ...model, flows: mergeFlowsById(model.flows, imported.model.flows), annotations: { ...model.annotations, ...imported.model.annotations } };
        remember();
        setModel(nextModel);
        setDiagram(current => synchronizeEventFlowDiagram(nextModel, current));
        setSource(JSON.stringify(nextModel, null, 2));
        setSelectedFlowId(imported.model.flows[0]?.id ?? nextModel.flows[0]?.id ?? '');
        setSelectedNodeId('');
        setSelectedTransitionId('');
        setFormSelection('FLOW');
        setDirty(true);
        onMessage(`Imported ${imported.model.flows.length} event flow(s) from ${request.fileName}`);
        const nodeCount = imported.model.flows.reduce((sum, flow) => sum + flow.nodes.length, 0);
        const invalidCount = imported.model.flows.filter(flow => flow.nodes.length === 0 || !flow.nodes.some(node => node.kind === 'START') || !flow.nodes.some(node => node.kind === 'SUCCESS_END' || node.kind === 'ERROR_END')).length;
        return { summary: `Imported ${imported.model.flows.length} event flow(s)`, details: [`Strategy: ${request.strategy === 'MERGE' ? 'merge/upsert' : 'replace layer'}`, 'Imported nodes and transitions remain editable in Form, Structure tree, Diagram and Source views.'], warnings: imported.warnings, metrics: [{ id: 'flows', label: 'Flows imported', value: imported.model.flows.length, tone: 'GOOD' as const }, { id: 'nodes', label: 'Nodes imported', value: nodeCount, tone: 'NEUTRAL' as const }, { id: 'invalid', label: 'Erroneous flows', value: invalidCount, tone: invalidCount > 0 ? 'ERROR' as const : 'GOOD' as const }, { id: 'warnings', label: 'Warnings', value: imported.warnings.length, tone: imported.warnings.length > 0 ? 'WARNING' as const : 'GOOD' as const }] };
    }

    return (
        <section className="event-flow-designer">
            <DesignerCommandBar className="event-flow-toolbar hydra-unified-workbench-toolbar" headingLevel={3} title={t('Event flows')} description={t('HYDRA SAGA/CEP flows. Derived events remain neutral and independent from gamification.')} actions={<><DesignerCommandGroup label={t('Interchange')}><HydraButton type="button" onClick={() => setLayerIoMode('IMPORT')}>{t('Import flows')}</HydraButton><HydraButton type="button" onClick={() => setLayerIoMode('EXPORT')}>{t('Export flows')}</HydraButton></DesignerCommandGroup><DesignerCommandGroup label={t('Quality')}><HydraButton type="button" onClick={() => void validate()}>{t('Check')}</HydraButton></DesignerCommandGroup><DesignerCommandGroup label={t('Runtime')}><HydraButton type="button" onClick={() => void toggleRuntime()}>{runtime?.active ? t('Stop runtime') : t('Start runtime')}</HydraButton></DesignerCommandGroup><DesignerCommandGroup label={t('Changes')}><HydraButton type="button" disabled={!dirty} onClick={() => void save()}>{t('Save flows')}</HydraButton></DesignerCommandGroup></>} viewsLabel={t('Workspace')} viewsSummary={view} views={<nav className="event-subtabs" aria-label={t('Event flow views')}><HydraButton className={view === 'FORM' ? 'active' : ''} type="button" onClick={() => setView('FORM')}>{t('Form')}</HydraButton>{structureTabOpen ? <span className={`designer-structure-tab-entry${view === 'STRUCTURE' ? ' active' : ''}`}><HydraButton className={view === 'STRUCTURE' ? 'active' : ''} type="button" onClick={() => setView('STRUCTURE')}>{t('Structure tree')}</HydraButton><HydraButton aria-label={t('Close event-flow structure tree')} className="designer-structure-tab-close hydra-window-close" type="button" onClick={() => { setStructureTabOpen(false); if (view === 'STRUCTURE') setView('FORM'); }}>×</HydraButton></span> : <HydraButton type="button" onClick={() => { setStructureTabOpen(true); setView('STRUCTURE'); }}>{t('Structure tree')}</HydraButton>}<HydraButton className={view === 'DIAGRAM' ? 'active' : ''} type="button" onClick={() => setView('DIAGRAM')}>{t('Diagram')}</HydraButton><DesignerDropdown className="event-flow-more-views" label={t('More views')} summary={view === 'SOURCE' ? t('Source') : view === 'RUNTIME' ? t('Runtime') : undefined}><HydraButton className={view === 'SOURCE' ? 'active' : ''} type="button" onClick={() => setView('SOURCE')}>{t('Source')}</HydraButton><HydraButton className={view === 'RUNTIME' ? 'active' : ''} type="button" onClick={() => { setView('RUNTIME'); void refreshRuntime(); }}>{t('Runtime & traces')}</HydraButton></DesignerDropdown></nav>} />
            {diagnostics.length > 0 && <div className="event-flow-diagnostic-summary"><HydraDiagnosticList diagnostics={diagnostics} context="event-flow" title={t('Flow diagnostics')} onSelectElement={elementId => {
                const owner = model.flows.find(flow => flow.id === elementId || flow.nodes.some(node => node.id === elementId) || flow.transitions.some(transition => transition.id === elementId));
                if (owner !== undefined) { setSelectedFlowId(owner.id); if (owner.nodes.some(node => node.id === elementId)) { setSelectedNodeId(elementId); setSelectedTransitionId(''); setFormSelection('NODE'); } else if (owner.transitions.some(transition => transition.id === elementId)) { setSelectedTransitionId(elementId); setSelectedNodeId(''); setFormSelection('TRANSITION'); } else { setSelectedNodeId(''); setSelectedTransitionId(''); setFormSelection('FLOW'); } setView('DIAGRAM'); }
            }} /></div>}
            {layerIoMode !== null && <LayerInterchangeDialog
                mode={layerIoMode}
                layerName="Event flows"
                description="Import or export EventFlow independently from the event definitions. Node-RED wires and common Siddhi queries are mapped to editable HYDRA flow nodes; HYDRA metadata preserves richer SAGA/CEP semantics for round trips."
                formats={EVENT_FLOW_INTERCHANGE_FORMATS}
                defaultFormat={(model.annotations['hydra.interchange.format'] as EventFlowInterchangeFormat | undefined) ?? 'NODE_RED_JSON'}
                currentMetamodel={metamodel}
                onClose={() => setLayerIoMode(null)}
                onAnalyzeImport={request => analyzeEventFlowImport(request.content, request.format, model, customEventTypes)}
                onImport={transferEventFlows}
                onExport={format => exportEventFlowLayer(format, model, customEventTypes)}
            />}
            <div className="hydra-designer-master-detail event-flow-layout" style={sidebar.layoutStyle}>
                <aside className="hydra-designer-sidebar">
                    <div className="hydra-sidebar-heading">
                        <div><h4>Event flows</h4><small>{model.flows.length} flow(s)</small></div>
                        <div className="hydra-sidebar-heading-actions"><DesignerGuideButton kind="EVENT_FLOW" /><HydraButton type="button" onClick={addFlow}>+ Flow</HydraButton></div>
                    </div>
                    <div className="hydra-sidebar-filter"><input aria-label="Search event flows" placeholder="Search flows..." value={filter} onChange={event => setFilter(event.target.value)} /></div>
                    <div className="hydra-sidebar-scroll">
                        <section className="hydra-sidebar-section">
                            <HydraVirtualList items={filteredFlows} itemKey={flow => flow.id} itemHeight={54} height={360} threshold={120} ariaLabel="Event flow catalog" renderItem={flow => (
                                <HydraButton className={`hydra-designer-list-item${flow.id === selectedFlowId && formSelection === 'FLOW' ? ' selected' : ''}`} type="button" onClick={() => { setSelectedFlowId(flow.id); setSelectedNodeId(''); setSelectedTransitionId(''); setFormSelection('FLOW'); }}>
                                    <span><strong>{flow.name}</strong><small>{flow.id}</small></span><span className="hydra-designer-list-badge">{flow.nodes.length} nodes</span>
                                </HydraButton>)} />
                            {model.flows.length === 0 && <p className="event-flow-sidebar-empty">No flows yet.</p>}
                        </section>
                        {selectedFlow !== null && (
                            <>
                                <section className="hydra-sidebar-section">
                                    <div className="hydra-sidebar-section-title">
                                        <strong>Nodes</strong>
                                        <div className="event-flow-add-node"><div><DesignerFieldLabel label="New node kind" targetId="event-flow-new-node-kind" help="Type of processing step to add to the selected flow." example="Filter" options={flowNodeOptionHelp(nodeKinds)} /><HydraAdaptiveSelect showOptionHelp={false} id="event-flow-new-node-kind" ariaLabel="New node kind" value={newNodeKind} options={nodeKinds.map(kind => ({ value: kind, label: FLOW_NODE_PRESENTATIONS[kind].label, description: FLOW_NODE_PRESENTATIONS[kind].description, category: flowNodeCategory(kind) }))} onChange={value => setNewNodeKind(value as FlowNodeKind)} /></div><HydraButton type="button" onClick={() => addNode(newNodeKind)}>+ Node</HydraButton></div>
                                    </div>
                                    <HydraVirtualList items={selectedFlow.nodes} itemKey={node => node.id} itemHeight={54} height={420} threshold={160} ariaLabel="Event flow nodes" renderItem={node => (
                                        <HydraButton aria-label={`Edit ${node.name} node ${node.id}`} className={`hydra-designer-list-item${node.id === selectedNodeId && formSelection === 'NODE' ? ' selected' : ''}`} type="button" onClick={() => { setSelectedNodeId(node.id); setSelectedTransitionId(''); setFormSelection('NODE'); }}>
                                            <span><strong>{node.name}</strong><small>{node.id}</small></span><span className="hydra-designer-list-badge">{FLOW_NODE_PRESENTATIONS[node.kind].label}</span>
                                        </HydraButton>)} />
                                </section>
                                <section className="hydra-sidebar-section">
                                    <div className="hydra-sidebar-section-title"><strong>Transitions</strong><HydraButton type="button" onClick={addTransition}>+ Transition</HydraButton></div>
                                    <HydraVirtualList items={selectedFlow.transitions} itemKey={transition => transition.id} itemHeight={54} height={360} threshold={160} ariaLabel="Event flow transitions" renderItem={transition => (
                                        <HydraButton className={`hydra-designer-list-item${transition.id === selectedTransitionId && formSelection === 'TRANSITION' ? ' selected' : ''}`} type="button" onClick={() => { setSelectedTransitionId(transition.id); setSelectedNodeId(''); setFormSelection('TRANSITION'); }}>
                                            <span><strong>{transition.label || FLOW_TRANSITION_PRESENTATIONS[transition.kind].label}</strong><small>{transition.id}</small></span><span className="hydra-designer-list-badge">{FLOW_TRANSITION_PRESENTATIONS[transition.kind].label}</span>
                                        </HydraButton>)} />
                                </section>
                            </>
                        )}
                    </div>
                </aside>
                <div {...sidebar.separatorProps} />
                <main className="hydra-designer-form">

                    {view === 'STRUCTURE' && <div className={`designer-structure-workspace designer-structure-direct-layout${structureCollapsed ? ' collapsed' : ''}`}>
                        <section className="designer-structure-tree-pane">
                            <header><div><h3>Event-flow structure</h3><p>Flows, nodes, transitions, variables and compensations.</p></div><HydraButton type="button" onClick={() => setStructureCollapsed(value => !value)}>{structureCollapsed ? 'Expand tree' : 'Collapse tree'}</HydraButton></header>
                            {!structureCollapsed && <><input aria-label="Filter event-flow structure" placeholder="Filter structure..." value={structureFilter} onChange={event => setStructureFilter(event.target.value)} /><EventFlowStructureTree model={model} selection={structureSelection()} filter={structureFilter} onSelect={selectStructureItem} /></>}
                        </section>
                        {structurePropertiesOpen ? <aside className={`designer-structure-properties-pane${structurePropertiesCollapsed ? ' collapsed' : ''}`}>
                            <header><div><h3>Properties</h3><p>Edits update the same HYDRA flow document and diagram.</p></div><div><HydraButton type="button" onClick={() => setStructurePropertiesCollapsed(value => !value)}>{structurePropertiesCollapsed ? 'Expand' : 'Collapse'}</HydraButton><HydraButton aria-label="Close event-flow properties" className="hydra-form-close-button" type="button" onClick={() => setStructurePropertiesOpen(false)}>×</HydraButton></div></header>
                            {!structurePropertiesCollapsed && structureInspector()}
                        </aside> : <HydraButton className="designer-structure-properties-reopen" type="button" onClick={() => { setStructurePropertiesOpen(true); setStructurePropertiesCollapsed(false); }}>Open properties</HydraButton>}
                    </div>}
                    {view === 'FORM' && selectedFlow === null && <div className="hydra-empty-state"><strong>No flow selected</strong><span>Create or select an event flow from the left panel.</span></div>}
                    {view === 'FORM' && selectedFlow !== null && formSelection === 'FLOW' && (
                        <FlowForm flow={selectedFlow} eventTypeIds={eventTypeIds} customEventTypes={customEventTypes} onChange={updateFlow} onRenameId={renameFlowId} onAddVariable={addVariable} onAddCompensation={addCompensation} onOpenDiagram={() => setView('DIAGRAM')} onDelete={deleteFlow} />
                    )}
                    {view === 'FORM' && selectedFlow !== null && formSelection === 'NODE' && selectedNode !== null && (
                        <FlowNodeProperties node={selectedNode} transitions={selectedFlow.transitions} eventTypeIds={eventTypeIds} customEventTypes={customEventTypes} onChange={updateNode} onRenameId={renameNodeId} onDelete={deleteNode} />
                    )}
                    {view === 'FORM' && selectedFlow !== null && formSelection === 'TRANSITION' && selectedTransition !== null && (
                        <FlowTransitionProperties transition={selectedTransition} nodes={selectedFlow.nodes} onChange={updateTransition} onRenameId={renameTransitionId} onDelete={deleteTransition} />
                    )}
                    {view === 'DIAGRAM' && (selectedFlow === null
                        ? <div className="event-flow-inspector-empty"><strong>No flow selected</strong><span>Create or select a flow from the left panel.</span></div>
                        : <EventFlowVisualEditor
                            nodes={nodes}
                            edges={edges}
                            inspector={visualInspector()}
                            canUndo={canUndo}
                            canRedo={canRedo}
                            onNodesChange={onNodesChange}
                            onEdgesChange={onEdgesChange}
                            onConnect={onConnect}
                            onSelectNode={selectVisualNode}
                            onSelectEdge={selectVisualEdge}
                            onAddNode={(kind, position) => addNode(kind, position, true)}
                            onDelete={deleteVisualSelection}
                            onDeleteSelection={deleteVisualItems}
                            onDuplicate={duplicateVisualSelection}
                            onAutoLayout={autoLayoutVisual}
                            onElkLayout={elkLayoutVisual}
                            onUndo={undo}
                            onRedo={redo}
                            onNodeDragStart={beginVisualDrag}
                            onNodeDragStop={endVisualDrag}
                        />)}
                    {view === 'SOURCE' && <div className="event-flow-source"><textarea aria-label="Event flow JSON source" value={source} onChange={event => setSource(event.target.value)} /><HydraButton type="button" onClick={applySource}>Apply source</HydraButton></div>}
                    {overwriteDialogs}
                    {view === 'RUNTIME' && <EventFlowRuntimePanel status={runtime} snapshot={runtimeSnapshot} traces={runtimeTraces} onRefresh={refreshRuntime} onTick={processRuntimeTimers} onClear={clearRuntime} onSelectTrace={setSelectedRuntimeTrace} />}
                </main>
            </div>
        </section>
    );
}

