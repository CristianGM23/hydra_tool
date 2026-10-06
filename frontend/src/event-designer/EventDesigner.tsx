import { HydraButton } from '../design-system/HydraButton';
import { explainInputError } from '../api/inputErrorExplanation';
import { indexBy } from '../utils/indexBy';
import { applyEdgeChanges, applyNodeChanges } from '@xyflow/react';
import type { Connection, Edge, EdgeChange, Node, NodeChange } from '@xyflow/react';
import { useEffect, useMemo, useRef, useState } from 'react';
import { DesignerCommandBar, DesignerCommandGroup } from '../designer-ui/DesignerCommandBar';
import { useHydraLanguage } from '../i18n';
import { useBoundedDesignerHistory } from '../designer-ui/useBoundedDesignerHistory';
import type { ChangeEvent } from 'react';
import {
    DesignerGuideButton,
    DesignerHelpedField,
} from '../designer-ui/DesignerFieldHelp';
import { HydraErrorBoundary } from '../design-system';
import { HydraVirtualList } from '../design-system/HydraVirtualList';
import { LayerInterchangeDialog } from '../layer-io';
import type { LayerImportRequest } from '../layer-io';
import { analyzeEventImport, EVENT_INTERCHANGE_FORMATS, exportEventLayer, importEventLayer, type EventInterchangeFormat } from '../layer-io/eventInterchange';
import { useResizableDesignerSidebar } from '../designer-ui/useResizableDesignerSidebar';
import { HydraConnectionVisualEditor } from '../designer-ui/HydraConnectionVisualEditor';
import type { HydraEditableConnectionVisual } from '../designer-ui/HydraConnectionVisualEditor';
import { DEFAULT_HYDRA_SOURCE_PORT, DEFAULT_HYDRA_TARGET_PORT } from '../designer-ui/HydraConnectionVisual';
import { MetamodelSelectionProperties, MetamodelStructureTree } from '../designer-ui/MetamodelStructureTree';
import type { MetamodelStructureSelection } from '../designer-ui/MetamodelStructureTree';
import { loadMetaModel } from '../metamodel-designer/api';
import type { MetaModel } from '../metamodel-designer/types';
import { isMissingValidationResource } from '../validation/http';
import {
    createEventModels,
    loadEventArchitectureDiagram,
    loadEventModels,
    loadReservedEventTypes,
    replaceEventModels,
    saveEventArchitectureDiagram
} from './api';
import {
    createEmptyEventModels,
    createEventDesignerId
} from './eventDesignerFactory';
import { applyEventConnection } from './eventConnections';
import { findEventDesignerBlockingError } from './eventDesignerValidation';
import { presentEventType } from './eventTypePresentation';
import { normalizeEventBindingModel, normalizeEventDefinitionModel, normalizeEventModelLoadResult, normalizeEventModelSaveResponse } from './eventModelNormalization';
import { EventArchitectureVisualEditor } from './EventArchitectureVisualEditor';
import { EventStructureTree } from './EventStructureTree';
import type { EventArchitecturePaletteEntry } from './eventArchitecturePalette';
import {
    addEventArchitectureNode,
    clearEventArchitectureDiagram,
    createEmptyEventArchitectureDiagram,
    removeEventArchitectureNode,
    synchronizeEventArchitectureDiagram,
    toEventArchitectureFlowElements
} from './eventArchitectureDiagram';
import type { EventArchitectureEdgeData, EventArchitectureNodeData } from './eventArchitectureDiagram';
import { EventBindingForm, EventConnectionSummary, EventTypeForm, type EventFormSelection } from './EventDesignerParts';
import { firstEventSelection, mergeByStableId } from './eventDesignerSupport';
import { appendEventType, removeEventBinding, removeEventType, renameEventBinding, renameEventField, renameEventType, replaceEventBinding, replaceEventField, replaceEventType } from './eventDesignerModel';
import type {
    EventArchitectureDiagramDocument,
    EventBinding,
    EventBindingModel,
    EventDefinitionModel,
    EventFieldDefinition,
    EventPayloadMapping,
    EventTypeDefinition
} from './types';
import './event-designer.css';
import { hasHydraGeometryChange, readHydraNodeSize } from '../designer-ui/hydraNodeGeometry';
import { eventAutoLayout, eventElkLayout } from './eventElkLayout';
import { useHydraOverwriteConfirmation } from '../hooks/useHydraOverwriteConfirmation';
import type { HydraPersistedIdentity } from '../hooks/hydraOverwritePolicy';
import { eventOverwriteIdentities } from './eventOverwriteIdentity';

interface Props {
    metamodelId: string;
    initialView?: EventDesignerView;
    metamodel?: MetaModel;
    persisted: boolean;
    onError: (message: string) => void;
    onMessage: (message: string) => void;
}

type EventDesignerView = 'FORM' | 'STRUCTURE' | 'ARCHITECTURE' | 'SOURCE';

interface EventDesignerSnapshot {
    definitions: EventDefinitionModel;
    bindings: EventBindingModel;
    diagram: EventArchitectureDiagramDocument;
    selection: EventFormSelection;
}

export function EventDesigner(props: Props) {
    const initialView = props.initialView ?? 'FORM';
    return <EventDesignerSession key={`${props.metamodelId}:${props.persisted}:${initialView}`} {...props} initialView={initialView} />;
}

function EventDesignerSession({ metamodelId, metamodel, persisted, initialView = 'FORM', onError, onMessage }: Props) {
    const { t } = useHydraLanguage();
    const sidebar = useResizableDesignerSidebar({ storageKey: 'hydra:event-designer:sidebar-width', ariaLabel: 'Resize event contracts palette', initialWidth: 330 });

    const initial = createEmptyEventModels(metamodelId);
    const [definitions, setDefinitions] = useState<EventDefinitionModel>(initial.definitions);
    const [bindings, setBindings] = useState<EventBindingModel>(initial.bindings);
    const [diagram, setDiagram] = useState<EventArchitectureDiagramDocument>(() => createEmptyEventArchitectureDiagram(initial.definitions, initial.bindings));
    const [definitionEtag, setDefinitionEtag] = useState('');
    const [bindingEtag, setBindingEtag] = useState('');
    const [persistedEventIdentities, setPersistedEventIdentities] = useState<HydraPersistedIdentity[]>([]);
    const currentEventIdentities = useMemo(() => eventOverwriteIdentities(definitions, bindings), [bindings, definitions]);
    const { confirmOverwrite, overwriteDialogs } = useHydraOverwriteConfirmation({
        current: currentEventIdentities,
        persisted: persistedEventIdentities
    });
    const [diagramEtag, setDiagramEtag] = useState('');
    const [reservedTypes, setReservedTypes] = useState<string[]>([]);
    const [selection, setSelection] = useState<EventFormSelection>(null);
    const [filter, setFilter] = useState('');
    const [dirty, setDirty] = useState(false);
    const [view, setView] = useState<EventDesignerView>(initialView);
    const [structureTabOpen, setStructureTabOpen] = useState(true);
    const [structureCollapsed, setStructureCollapsed] = useState(false);
    const [structurePropertiesOpen, setStructurePropertiesOpen] = useState(true);
    const [structurePropertiesCollapsed, setStructurePropertiesCollapsed] = useState(false);
    const [structureFilter, setStructureFilter] = useState('');
    const [semanticFilter, setSemanticFilter] = useState('');
    const [loadedSemanticMetamodel, setLoadedSemanticMetamodel] = useState<MetaModel | null>(metamodel ?? null);
    const semanticMetamodel = metamodel ?? loadedSemanticMetamodel;
    const [semanticSelection, setSemanticSelection] = useState<MetamodelStructureSelection | null>(null);
    const [source, setSource] = useState(JSON.stringify(initial, null, 2));
    const [selectedDiagramNodeId, setSelectedDiagramNodeId] = useState('');
    const [selectedDiagramEdgeId, setSelectedDiagramEdgeId] = useState('');
    const [layerIoMode, setLayerIoMode] = useState<'IMPORT' | 'EXPORT' | null>(null);
    const dragSnapshot = useRef<EventDesignerSnapshot | null>(null);
    const onErrorRef = useRef(onError);
    const { remember, undo, redo, clear: clearHistory, canUndo, canRedo } = useBoundedDesignerHistory(snapshot, restore, 50);
    useEffect(() => { onErrorRef.current = onError; }, [onError]);

    useEffect(() => {
        let active = true;
        const empty = createEmptyEventModels(metamodelId);
        const emptyDiagram = createEmptyEventArchitectureDiagram(empty.definitions, empty.bindings);
        const reservedPromise = loadReservedEventTypes();
        if (!persisted) {
            void reservedPromise.then(types => {
                if (!active) return;
                setReservedTypes(types);
            }).catch(() => undefined);
            return () => { active = false; };
        }

        void Promise.allSettled([loadEventModels(metamodelId), reservedPromise, loadEventArchitectureDiagram(metamodelId)])
            .then(results => {
                if (!active) return;
                let loadedDefinitions = empty.definitions;
                let loadedBindings = empty.bindings;
                let loadedReserved: string[] = [];
                const contract = results[0];
                if (contract.status === 'fulfilled') {
                    const normalizedContract = normalizeEventModelLoadResult(contract.value);
                    loadedDefinitions = normalizedContract.definitions;
                    loadedBindings = normalizedContract.bindings;
                    setDefinitions(loadedDefinitions);
                    setBindings(loadedBindings);
                    setDefinitionEtag(normalizedContract.definitionEtag);
                    setBindingEtag(normalizedContract.bindingEtag);
                    setPersistedEventIdentities(eventOverwriteIdentities(loadedDefinitions, loadedBindings));
                    setSelection(firstEventSelection(loadedDefinitions, loadedBindings));
                    setSource(JSON.stringify({ definitions: loadedDefinitions, bindings: loadedBindings }, null, 2));
                } else if (!isMissingValidationResource(contract.reason)) {
                    onErrorRef.current(contract.reason instanceof Error ? contract.reason.message : 'Could not load event contracts');
                }
                const reserved = results[1];
                if (reserved.status === 'fulfilled') {
                    loadedReserved = reserved.value;
                    setReservedTypes(loadedReserved);
                }
                const diagramResult = results[2];
                if (diagramResult.status === 'fulfilled') {
                    setDiagram(synchronizeEventArchitectureDiagram(loadedDefinitions, loadedBindings, loadedReserved, diagramResult.value.diagram));
                    setDiagramEtag(diagramResult.value.etag);
                } else if (isMissingValidationResource(diagramResult.reason)) {
                    setDiagram(synchronizeEventArchitectureDiagram(loadedDefinitions, loadedBindings, loadedReserved, emptyDiagram));
                } else {
                    console.warn('Could not load saved event architecture diagram; rebuilding it from event definitions and bindings.', diagramResult.reason); setDiagram(synchronizeEventArchitectureDiagram(loadedDefinitions, loadedBindings, loadedReserved, emptyDiagram)); setDiagramEtag('');
                }
            });
        return () => { active = false; };
    }, [metamodelId, persisted]);

    useEffect(() => {
        if (metamodel !== undefined) return undefined;
        let active = true;
        void loadMetaModel(metamodelId)
            .then(result => { if (active) setLoadedSemanticMetamodel(result.metamodel); })
            .catch(() => { if (active) setLoadedSemanticMetamodel(null); });
        return () => { active = false; };
    }, [metamodel, metamodelId]);

    const eventTypeById = useMemo(() => indexBy(definitions.eventTypes, type => type.id), [definitions.eventTypes]);
    const bindingById = useMemo(() => indexBy(bindings.bindings, binding => binding.id), [bindings.bindings]);
    const selectedType = selection?.kind === 'EVENT' ? eventTypeById.get(selection.id) ?? null : null;
    const selectedBinding = selection?.kind === 'BINDING' ? bindingById.get(selection.id) ?? null : null;
    const bindingTarget = selectedBinding === null ? null : eventTypeById.get(selectedBinding.targetEventTypeId) ?? null;
    const sourceTypes = useMemo(
        () => Array.from(new Set(['core.model.element-created', ...reservedTypes, ...definitions.eventTypes.map(type => type.id)])),
        [definitions.eventTypes, reservedTypes]
    );
    const eventArchitectureCatalog = useMemo<EventArchitecturePaletteEntry[]>(() => {
        const customIds = new Set(definitions.eventTypes.map(type => type.id));
        const visibleIds = new Set(diagram.nodes.map(node => node.nodeId));
        return sourceTypes.map(eventTypeId => {
            const presentation = presentEventType(eventTypeId, definitions.eventTypes);
            return {
                eventTypeId,
                label: presentation.label,
                description: presentation.description,
                category: presentation.category,
                kind: customIds.has(eventTypeId) ? 'CUSTOM_EVENT' : 'RESERVED_EVENT',
                onCanvas: visibleIds.has(eventTypeId)
            };
        });
    }, [definitions.eventTypes, diagram.nodes, sourceTypes]);
    const normalizedFilter = filter.trim().toLowerCase();
    const filteredTypes = definitions.eventTypes.filter(type => [type.name, type.id, type.category]
        .some(value => value.toLowerCase().includes(normalizedFilter)));
    const filteredBindings = bindings.bindings.filter(binding => [binding.name, binding.id, binding.sourceEventTypeId, binding.targetEventTypeId]
        .some(value => value.toLowerCase().includes(normalizedFilter)));
    const visual = useMemo(() => toEventArchitectureFlowElements(definitions, bindings, reservedTypes, diagram,
        selectedDiagramNodeId, selectedDiagramEdgeId),
    [bindings, definitions, diagram, reservedTypes, selectedDiagramEdgeId, selectedDiagramNodeId]);

    function snapshot(): EventDesignerSnapshot { return { definitions, bindings, diagram, selection }; }

    function restore(value: EventDesignerSnapshot): void {
        setDefinitions(value.definitions);
        setBindings(value.bindings);
        setDiagram(synchronizeEventArchitectureDiagram(value.definitions, value.bindings, reservedTypes, value.diagram));
        setSelection(value.selection);
        setSelectedDiagramNodeId(value.selection?.kind === 'EVENT' ? value.selection.id : '');
        setSelectedDiagramEdgeId(value.selection?.kind === 'BINDING' ? value.selection.id : '');
        setSource(JSON.stringify({ definitions: value.definitions, bindings: value.bindings }, null, 2));
        setDirty(true);
    }


    function selectView(view: EventDesignerView): void {
        if (view === 'SOURCE') setSource(JSON.stringify({ definitions, bindings }, null, 2));
        setView(view);
    }

    function updateNamespace(event: ChangeEvent<HTMLInputElement>): void {
        const namespace = event.target.value;
        const next = { ...definitions, namespace, eventTypes: definitions.eventTypes.map(type => ({ ...type, namespace })) };
        setDefinitions(next);
        setDiagram(current => synchronizeEventArchitectureDiagram(next, bindings, reservedTypes, current));
        setDirty(true);
    }

    function updateType(type: EventTypeDefinition): void {
        const next = replaceEventType(definitions, type);
        setDefinitions(next); setDiagram(current => synchronizeEventArchitectureDiagram(next, bindings, reservedTypes, current)); setDirty(true);
    }

    function renameTypeId(previousId: string, nextId: string): void {
        if (previousId === nextId) return;
        const renamed = renameEventType(definitions, bindings, previousId, nextId);
        const renamedDiagram = { ...diagram, nodes: diagram.nodes.map(node => node.nodeId === previousId ? { ...node, nodeId: nextId } : node) };
        setDefinitions(renamed.definitions); setBindings(renamed.bindings);
        setDiagram(synchronizeEventArchitectureDiagram(renamed.definitions, renamed.bindings, reservedTypes, renamedDiagram));
        setSelection({ kind: 'EVENT', id: nextId }); setSelectedDiagramNodeId(nextId); setDirty(true);
    }

    function updateField(type: EventTypeDefinition, field: EventFieldDefinition): void { updateType(replaceEventField(type, field)); }

    function renameFieldId(type: EventTypeDefinition, previousId: string, nextId: string): void {
        if (previousId === nextId) return;
        const renamed = renameEventField(type, bindings, previousId, nextId);
        updateType(renamed.type); setBindings(renamed.bindings); setDirty(true);
    }

    function updateBinding(binding: EventBinding): void {
        const next = replaceEventBinding(bindings, binding);
        setBindings(next); setDiagram(current => synchronizeEventArchitectureDiagram(definitions, next, reservedTypes, current)); setDirty(true);
    }

    function renameBindingId(previousId: string, nextId: string): void {
        if (previousId === nextId) return;
        const nextBindings = renameEventBinding(bindings, previousId, nextId);
        const renamedDiagram = { ...diagram, edges: diagram.edges.map(edge => edge.edgeId === previousId ? { ...edge, edgeId: nextId } : edge) };
        setBindings(nextBindings); setDiagram(synchronizeEventArchitectureDiagram(definitions, nextBindings, reservedTypes, renamedDiagram));
        setSelection({ kind: 'BINDING', id: nextId }); setSelectedDiagramEdgeId(nextId); setDirty(true);
    }

    function addType(position?: { x: number; y: number }, stayVisual = false): void {
        remember();
        const added = appendEventType(definitions);
        const synchronized = synchronizeEventArchitectureDiagram(added.definitions, bindings, reservedTypes, diagram);
        setDefinitions(added.definitions); setDiagram(stayVisual ? addEventArchitectureNode(synchronized, added.type.id, position) : synchronized);
        setSelection({ kind: 'EVENT', id: added.type.id }); setSelectedDiagramNodeId(stayVisual ? added.type.id : ''); setSelectedDiagramEdgeId('');
        setView(stayVisual ? 'ARCHITECTURE' : 'FORM'); setDirty(true);
    }

    function addCatalogEventToDiagram(eventTypeId: string, position?: { x: number; y: number }): void {
        if (!sourceTypes.includes(eventTypeId)) return;
        if (diagram.nodes.some(node => node.nodeId === eventTypeId)) {
            setSelectedDiagramNodeId(eventTypeId);
            setSelectedDiagramEdgeId('');
            if (definitions.eventTypes.some(type => type.id === eventTypeId)) setSelection({ kind: 'EVENT', id: eventTypeId });
            return;
        }
        remember();
        setDiagram(current => addEventArchitectureNode(current, eventTypeId, position));
        setSelectedDiagramNodeId(eventTypeId);
        setSelectedDiagramEdgeId('');
        if (definitions.eventTypes.some(type => type.id === eventTypeId)) setSelection({ kind: 'EVENT', id: eventTypeId });
        else setSelection(null);
        setDirty(true);
    }

    function deleteType(target: EventTypeDefinition | null = selectedType, recordHistory = true): void {
        if (target === null) return;
        if (recordHistory) remember();
        const removed = removeEventType(definitions, bindings, target.id);
        setDefinitions(removed.definitions); setBindings(removed.bindings);
        setDiagram(current => synchronizeEventArchitectureDiagram(removed.definitions, removed.bindings, reservedTypes, current));
        setSelection(null); setSelectedDiagramNodeId(''); setSelectedDiagramEdgeId(''); setDirty(true);
    }

    function addBinding(sourceId?: string, targetId?: string, stayVisual = false, sourceHandle = DEFAULT_HYDRA_SOURCE_PORT, targetHandle = DEFAULT_HYDRA_TARGET_PORT): void {
        const preferredTarget = targetId === undefined
            ? selectedType ?? definitions.eventTypes[0]
            : definitions.eventTypes.find(type => type.id === targetId);
        const sourceEvent = sourceId ?? reservedTypes[0] ?? 'core.model.element-created';
        if (preferredTarget === undefined) { onError('Create a custom event type first.'); return; }
        const result = applyEventConnection(definitions, bindings, sourceTypes, sourceEvent, preferredTarget.id);
        if (!result.accepted || result.binding === null) { onError(result.message); return; }
        remember();
        const binding = result.binding;
        const nextBindings = result.bindings;
        setBindings(nextBindings);
        setDiagram(current => {
            const synchronized = synchronizeEventArchitectureDiagram(definitions, nextBindings, reservedTypes, current);
            return { ...synchronized, edges: synchronized.edges.map(edge => edge.edgeId === binding.id ? { ...edge, sourceHandle, targetHandle } : edge) };
        });
        setSelection({ kind: 'BINDING', id: binding.id });
        setSelectedDiagramNodeId('');
        setSelectedDiagramEdgeId(binding.id);
        setView(stayVisual ? 'ARCHITECTURE' : 'FORM');
        setDirty(true);
    }

    function deleteBinding(target: EventBinding | null = selectedBinding, recordHistory = true): void {
        if (target === null) return;
        if (recordHistory) remember();
        const nextBindings = removeEventBinding(bindings, target.id);
        setBindings(nextBindings); setDiagram(current => synchronizeEventArchitectureDiagram(definitions, nextBindings, reservedTypes, current));
        setSelection(null); setSelectedDiagramEdgeId(''); setDirty(true);
    }

    function addMapping(): void {
        if (selectedBinding === null || bindingTarget === null) return;
        const unmapped = bindingTarget.fields.find(field => !selectedBinding.payloadMappings.some(mapping => mapping.targetFieldId === field.id));
        if (unmapped === undefined) { onError('Every target field already has a mapping.'); return; }
        updateBinding({ ...selectedBinding, payloadMappings: [...selectedBinding.payloadMappings, { targetFieldId: unmapped.id, sourcePath: `payload.${unmapped.name}`, expressionId: '' }] });
    }

    function replaceMapping(index: number, mapping: EventPayloadMapping): void {
        if (selectedBinding === null) return;
        updateBinding({ ...selectedBinding, payloadMappings: selectedBinding.payloadMappings.map((item, itemIndex) => itemIndex === index ? mapping : item) });
    }

    function deleteMapping(index: number): void {
        if (selectedBinding === null) return;
        updateBinding({ ...selectedBinding, payloadMappings: selectedBinding.payloadMappings.filter((_, itemIndex) => itemIndex !== index) });
    }

    function applySource(): void {
        try {
            const parsed = JSON.parse(source) as { definitions: EventDefinitionModel; bindings: EventBindingModel };
            const parsedDefinitions = normalizeEventDefinitionModel(parsed.definitions);
            const parsedBindings = normalizeEventBindingModel(parsed.bindings);
            const error = findEventDesignerBlockingError(parsedDefinitions, parsedBindings);
            if (error !== '') { onError(error); return; }
            if (parsedDefinitions.metamodelId !== metamodelId || parsedBindings.metamodelId !== metamodelId) {
                onError('Source metamodelId must match the selected metamodel.');
                return;
            }
            remember();
            const nextSelection = firstEventSelection(parsedDefinitions, parsedBindings);
            setDefinitions(parsedDefinitions);
            setBindings(parsedBindings);
            setDiagram(current => synchronizeEventArchitectureDiagram(parsedDefinitions, parsedBindings, reservedTypes, current));
            setSelection(nextSelection);
            setSelectedDiagramNodeId(nextSelection?.kind === 'EVENT' ? nextSelection.id : '');
            setSelectedDiagramEdgeId(nextSelection?.kind === 'BINDING' ? nextSelection.id : '');
            setDirty(true);
            onMessage('Event source applied to the HYDRA documents');
        } catch (error) { onError(explainInputError(error, 'Invalid event source JSON', 'the Event source tab')); }
    }

    async function save(): Promise<boolean> {
        if (!persisted) { onError('Save the metamodel before configuring events.'); return false; }
        const error = findEventDesignerBlockingError(definitions, bindings);
        if (error !== '') { onError(error); return false; }
        if (!await confirmOverwrite()) return false;
        try {
            const rawResult = definitionEtag === ''
                ? await createEventModels(definitions, bindings)
                : await replaceEventModels(definitions, definitionEtag, bindings, bindingEtag);
            const result = normalizeEventModelSaveResponse(rawResult);
            const synchronized = synchronizeEventArchitectureDiagram(result.definitions, result.bindings, reservedTypes, diagram);
            const savedDiagram = await saveEventArchitectureDiagram(metamodelId, synchronized, diagramEtag);
            setDefinitions(result.definitions);
            setBindings(result.bindings);
            setDiagram(savedDiagram.diagram);
            setDefinitionEtag(result.definitionEtag);
            setBindingEtag(result.bindingEtag);
            setPersistedEventIdentities(eventOverwriteIdentities(result.definitions, result.bindings));
            setDiagramEtag(savedDiagram.etag);
            setDirty(false);
            clearHistory();
            setSource(JSON.stringify({ definitions: result.definitions, bindings: result.bindings }, null, 2));
            onMessage('Event definitions, bindings and architecture diagram saved');
            return true;
        } catch (exception) {
            onError(explainInputError(exception, 'Could not save event contracts', 'Event Designer'));
            return false;
        }
    }

    function onVisualNodesChange(changes: NodeChange<Node<EventArchitectureNodeData>>[]): void {
        const next = applyNodeChanges(changes, visual.nodes);
        setDiagram(current => ({
            ...current,
            nodes: next.map(node => ({
                nodeId: node.id,
                x: node.position.x,
                y: node.position.y,
                width: readHydraNodeSize(node, 230, 92).width,
                height: readHydraNodeSize(node, 230, 92).height,
                collapsed: false
            }))
        }));
        const selectedChange = changes.find(change => change.type === 'select' && change.selected);
        if (selectedChange !== undefined && 'id' in selectedChange) selectVisualNode(visual.nodes.find(node => node.id === selectedChange.id));
        if (hasHydraGeometryChange(changes)) setDirty(true);
    }

    function onVisualEdgesChange(changes: EdgeChange<Edge<EventArchitectureEdgeData>>[]): void {
        const selectedChange = changes.find(change => change.type === 'select' && change.selected);
        if (selectedChange !== undefined && 'id' in selectedChange) selectVisualEdge(visual.edges.find(edge => edge.id === selectedChange.id));
        for (const change of changes.filter(change => change.type === 'remove')) {
            const edge = visual.edges.find(item => item.id === change.id);
            const binding = edge === undefined ? undefined : bindings.bindings.find(item => item.id === edge.id);
            if (binding !== undefined) deleteBinding(binding);
        }
        applyEdgeChanges(changes, visual.edges);
    }

    function selectVisualNode(node: Node<EventArchitectureNodeData> | undefined): void {
        if (node === undefined) return;
        setSelectedDiagramNodeId(node.id);
        setSelectedDiagramEdgeId('');
        if (node.data.kind === 'CUSTOM_EVENT') setSelection({ kind: 'EVENT', id: node.id });
        else setSelection(null);
    }

    function selectVisualEdge(edge: Edge<EventArchitectureEdgeData> | undefined): void {
        if (edge === undefined) return;
        setSelectedDiagramNodeId('');
        setSelectedDiagramEdgeId(edge.id);
        setSelection({ kind: 'BINDING', id: edge.id });
    }

    function connectVisual(connection: Connection): void {
        if (view !== 'ARCHITECTURE' || connection.source === null || connection.target === null) return;
        if (!definitions.eventTypes.some(type => type.id === connection.target)) {
            onError('A binding target must be a custom event type.');
            return;
        }
        addBinding(connection.source, connection.target, true, connection.sourceHandle ?? DEFAULT_HYDRA_SOURCE_PORT, connection.targetHandle ?? DEFAULT_HYDRA_TARGET_PORT);
    }

    function removeVisualSelection(): void {
        const binding = bindings.bindings.find(item => item.id === selectedDiagramEdgeId);
        if (binding !== undefined) { deleteBinding(binding); return; }
        if (selectedDiagramNodeId === '') return;
        remember();
        setDiagram(current => removeEventArchitectureNode(current, bindings, selectedDiagramNodeId));
        setSelectedDiagramNodeId('');
        setSelection(null);
        setDirty(true);
    }

    function clearVisualCanvas(): void {
        if (diagram.nodes.length === 0) return;
        remember();
        setDiagram(current => clearEventArchitectureDiagram(current));
        setSelectedDiagramNodeId('');
        setSelectedDiagramEdgeId('');
        setSelection(null);
        setDirty(true);
    }

    function duplicateVisualSelection(): void {
        const binding = bindings.bindings.find(item => item.id === selectedDiagramEdgeId);
        if (binding !== undefined) {
            remember();
            const clone = { ...binding, id: createEventDesignerId('binding'), name: `${binding.name} copy`, payloadMappings: binding.payloadMappings.map(item => ({ ...item })) };
            const nextBindings = { ...bindings, bindings: [...bindings.bindings, clone] };
            setBindings(nextBindings);
            setDiagram(current => synchronizeEventArchitectureDiagram(definitions, nextBindings, reservedTypes, current));
            setSelection({ kind: 'BINDING', id: clone.id });
            setSelectedDiagramEdgeId(clone.id);
            setDirty(true);
            return;
        }
        const type = definitions.eventTypes.find(item => item.id === selectedDiagramNodeId);
        if (type === undefined) return;
        remember();
        const clone = {
            ...type,
            id: createEventDesignerId('event'),
            name: `${type.name} copy`,
            fields: type.fields.map(field => ({ ...field, id: createEventDesignerId('field') })),
            allowedSources: [...type.allowedSources],
            allowedSubjects: [...type.allowedSubjects],
            annotations: { ...type.annotations }
        };
        const nextDefinitions = { ...definitions, eventTypes: [...definitions.eventTypes, clone] };
        const original = diagram.nodes.find(node => node.nodeId === type.id);
        let nextDiagram = synchronizeEventArchitectureDiagram(nextDefinitions, bindings, reservedTypes, diagram);
        nextDiagram = addEventArchitectureNode(nextDiagram, clone.id, original === undefined ? undefined : { x: original.x + 40, y: original.y + 40 });
        setDefinitions(nextDefinitions);
        setDiagram(nextDiagram);
        setSelection({ kind: 'EVENT', id: clone.id });
        setSelectedDiagramNodeId(clone.id);
        setSelectedDiagramEdgeId('');
        setDirty(true);
    }

    function autoLayout(): void {
        remember(); setDiagram(eventAutoLayout(definitions, bindings, reservedTypes, diagram)); setDirty(true);
    }
    function elkLayout(): void {
        if (diagram.nodes.length === 0) return;
        remember(); setDiagram(eventElkLayout(diagram, bindings)); setDirty(true);
    }

    function startNodeDrag(): void { dragSnapshot.current = snapshot(); }
    function stopNodeDrag(): void {
        if (dragSnapshot.current !== null) remember(dragSnapshot.current);
        dragSnapshot.current = null;
    }

    const reservedId = selectedDiagramNodeId !== '' && !eventTypeById.has(selectedDiagramNodeId) && sourceTypes.includes(selectedDiagramNodeId)
        ? selectedDiagramNodeId : '';
    const reservedPresentation = reservedId === '' ? null : presentEventType(reservedId);
    function selectBindingProperties(bindingId: string): void {
        setSelection({ kind: 'BINDING', id: bindingId });
        setSelectedDiagramNodeId('');
        setSelectedDiagramEdgeId(bindingId);
        setStructurePropertiesOpen(true);
        setStructurePropertiesCollapsed(false);
    }

    function updateSelectedEventConnectionVisual(next: HydraEditableConnectionVisual): void {
        if (selectedBinding === null) return;
        remember();
        setDiagram(current => ({ ...current, edges: current.edges.map(edge => edge.edgeId === selectedBinding.id ? {
            ...edge,
            ...next,
            sourceHandle: next.sourceHandle ?? DEFAULT_HYDRA_SOURCE_PORT,
            targetHandle: next.targetHandle ?? DEFAULT_HYDRA_TARGET_PORT,
            label: next.label ?? '', note: next.note ?? '',
            routeStyle: next.routeStyle ?? 'ORTHOGONAL', lineStyle: next.lineStyle ?? 'SOLID'
        } : edge) }));
        setDirty(true);
    }

    const selectedEventEdgeView = selectedBinding === null ? undefined : diagram.edges.find(edge => edge.edgeId === selectedBinding.id);

    const inspector = selectedType !== null ? <>
        <EventTypeForm
            type={selectedType}
            onChange={updateType}
            onRenameId={renameTypeId}
            onUpdateField={updateField}
            onRenameFieldId={renameFieldId}
            onDelete={() => deleteType()} onCreateAnother={() => addType()}
        />
        <EventConnectionSummary eventTypeId={selectedType.id} bindings={bindings.bindings} onSelectBinding={selectBindingProperties} />
    </> : selectedBinding !== null ? <>
        <EventBindingForm
            binding={selectedBinding}
            target={bindingTarget}
            sourceTypes={sourceTypes}
            customTypes={definitions.eventTypes}
            onChange={updateBinding}
            onRenameId={renameBindingId}
            onAddMapping={addMapping}
            onReplaceMapping={replaceMapping}
            onDeleteMapping={deleteMapping}
            onDelete={() => deleteBinding()}
        />
        {view === 'ARCHITECTURE' && <HydraConnectionVisualEditor value={{ ...selectedEventEdgeView, sourceHandle: selectedEventEdgeView?.sourceHandle, targetHandle: selectedEventEdgeView?.targetHandle, label: selectedEventEdgeView?.label ?? selectedBinding.name, note: selectedEventEdgeView?.note }} onChange={updateSelectedEventConnectionVisual} />}
    </> : reservedPresentation !== null ? <section className="event-reserved-inspector">
        <h3>{t(reservedPresentation.label)}</h3>
        <p>{t(reservedPresentation.description)}</p>
        <small>{t('System event · its technical contract is available in the properties and source views.')}</small>
        <DesignerGuideButton kind="EVENT_BINDING" />
        <EventConnectionSummary eventTypeId={reservedId} bindings={bindings.bindings} onSelectBinding={selectBindingProperties} />
    </section> : <div className="hydra-empty-state"><strong>No diagram element selected</strong><span>Select an event or binding to edit its model fields.</span></div>;

    function selectStructureItem(next: Exclude<EventFormSelection, null>): void {
        setSemanticSelection(null);
        setSelection(next);
        setStructurePropertiesOpen(true);
        setStructurePropertiesCollapsed(false);
        if (next.kind === 'EVENT') {
            setSelectedDiagramNodeId(next.id);
            setSelectedDiagramEdgeId('');
        } else {
            setSelectedDiagramNodeId('');
            setSelectedDiagramEdgeId(next.id);
        }
    }

    function transferEvents(request: LayerImportRequest<EventInterchangeFormat>) {
        if (semanticMetamodel === null) throw new Error('The main metamodel must be loaded before importing event contracts.');
        const imported = importEventLayer(request.content, request.format, semanticMetamodel, { definitions, bindings }, request.selection);
        const nextDefinitions: EventDefinitionModel = request.strategy === 'REPLACE'
            ? { ...imported.definitions, id: definitions.id, metamodelId, namespace: imported.definitions.namespace || definitions.namespace }
            : { ...definitions, eventTypes: mergeByStableId(definitions.eventTypes, imported.definitions.eventTypes), annotations: { ...definitions.annotations, ...imported.definitions.annotations } };
        const nextBindings: EventBindingModel = request.strategy === 'REPLACE'
            ? { ...imported.bindings, id: bindings.id, metamodelId, eventDefinitionModelId: nextDefinitions.id }
            : { ...bindings, eventDefinitionModelId: nextDefinitions.id, bindings: mergeByStableId(bindings.bindings, imported.bindings.bindings), annotations: { ...bindings.annotations, ...imported.bindings.annotations } };
        remember();
        setDefinitions(nextDefinitions);
        setBindings(nextBindings);
        setDiagram(current => synchronizeEventArchitectureDiagram(nextDefinitions, nextBindings, reservedTypes, current));
        const nextSelection = firstEventSelection(nextDefinitions, nextBindings);
        setSelection(nextSelection);
        setSelectedDiagramNodeId(nextSelection?.kind === 'EVENT' ? nextSelection.id : '');
        setSelectedDiagramEdgeId(nextSelection?.kind === 'BINDING' ? nextSelection.id : '');
        setDirty(true);
        onMessage(`Imported ${imported.definitions.eventTypes.length} event type(s) and ${imported.bindings.bindings.length} binding(s) from ${request.fileName}`);
        const unresolved = imported.warnings.filter(message => /not found|unresolved|could not/i.test(message)).length;
        return { summary: `Imported ${imported.definitions.eventTypes.length} event type(s)`, details: [`${imported.bindings.bindings.length} binding(s) imported`, `Strategy: ${request.strategy === 'MERGE' ? 'merge/upsert by stable ID' : 'replace event layer'}`, 'Structured event-data enum/object contracts were resolved against the selected metamodel.'], warnings: imported.warnings, metrics: [{ id: 'events', label: 'Events imported', value: imported.definitions.eventTypes.length, tone: 'GOOD' as const }, { id: 'bindings', label: 'Bindings imported', value: imported.bindings.bindings.length, tone: 'NEUTRAL' as const }, { id: 'unresolved', label: 'Unresolved references', value: unresolved, tone: unresolved > 0 ? 'WARNING' as const : 'GOOD' as const }, { id: 'warnings', label: 'Warnings', value: imported.warnings.length, tone: imported.warnings.length > 0 ? 'WARNING' as const : 'GOOD' as const }] };
    }

    return (
        <section className="event-designer">
            <DesignerCommandBar className="event-designer-header hydra-unified-workbench-toolbar" title={t('Event designer')} description={<>{t('HYDRA and custom events for')} {metamodelId}. {t('Events remain independent from optional gamification.')}</>} status={<span className="event-dirty-state">{dirty ? t('Unsaved changes') : t('Saved')}</span>} actions={<><DesignerCommandGroup label={t('Interchange')}><HydraButton type="button" disabled={semanticMetamodel === null} onClick={() => setLayerIoMode('IMPORT')}>{t('Import events')}</HydraButton><HydraButton type="button" disabled={semanticMetamodel === null} onClick={() => setLayerIoMode('EXPORT')}>{t('Export events')}</HydraButton></DesignerCommandGroup><DesignerCommandGroup label={t('Changes')}><HydraButton type="button" disabled={!dirty} onClick={() => void save()}>{t('Save events')}</HydraButton></DesignerCommandGroup></>} viewsLabel={t('Workspace')} viewsSummary={view} views={<nav className="event-subtabs" aria-label={t('Event designer views')}><HydraButton className={view === 'FORM' ? 'active' : ''} type="button" onClick={() => selectView('FORM')}>{t('Form')}</HydraButton>{structureTabOpen ? <span className="designer-structure-tab-entry"><HydraButton className={view === 'STRUCTURE' ? 'active' : ''} type="button" onClick={() => selectView('STRUCTURE')}>{t('Structure tree')}</HydraButton><HydraButton className="designer-structure-tab-close" type="button" aria-label={t('Close event structure tree tab')} onClick={() => { setStructureTabOpen(false); if (view === 'STRUCTURE') selectView('FORM'); }}>×</HydraButton></span> : <HydraButton type="button" onClick={() => { setStructureTabOpen(true); selectView('STRUCTURE'); }}>+ {t('Structure tree')}</HydraButton>}<HydraButton className={view === 'ARCHITECTURE' ? 'active' : ''} type="button" onClick={() => selectView('ARCHITECTURE')}>{t('Event architecture')}</HydraButton><HydraButton className={view === 'SOURCE' ? 'active' : ''} type="button" onClick={() => selectView('SOURCE')}>{t('Source')}</HydraButton></nav>} />
            {layerIoMode !== null && semanticMetamodel !== null && <LayerInterchangeDialog
                mode={layerIoMode}
                layerName="Events and bindings"
                description="Import or export this event layer independently from the metamodel. AsyncAPI messages are adapted to editable HYDRA event contracts; HYDRA extensions preserve publishing scope, subjects, persistence and event bindings."
                formats={EVENT_INTERCHANGE_FORMATS}
                defaultFormat={(definitions.annotations['hydra.interchange.format'] as EventInterchangeFormat | undefined) ?? 'ASYNCAPI_JSON'}
                currentMetamodel={semanticMetamodel}
                onClose={() => setLayerIoMode(null)}
                onAnalyzeImport={request => analyzeEventImport(request.content, request.format, semanticMetamodel, { definitions, bindings })}
                onImport={transferEvents}
                onExport={format => exportEventLayer(format, semanticMetamodel, definitions, bindings)}
            />}


            {view === 'FORM' && (
                <div className="hydra-designer-master-detail event-designer-layout" style={sidebar.layoutStyle}>
                    <aside className="hydra-designer-sidebar">
                        <div className="hydra-sidebar-heading"><div><h3>Event contracts</h3><small>{definitions.eventTypes.length} event(s) · {bindings.bindings.length} binding(s)</small></div></div>
                        <div className="hydra-sidebar-filter"><input aria-label="Search event contracts" placeholder="Search events and bindings..." value={filter} onChange={event => setFilter(event.target.value)} /></div>
                        <div className="hydra-sidebar-scroll">
                            <section className="hydra-sidebar-section">
                                <div className="hydra-sidebar-section-title"><strong>Custom event types</strong><div className="hydra-sidebar-section-actions"><DesignerGuideButton kind="EVENT" /><HydraButton type="button" onClick={() => addType()}>+ Event type</HydraButton></div></div>
                                <HydraVirtualList items={filteredTypes} itemKey={type => type.id} itemHeight={54} height={380} threshold={120} ariaLabel="Custom event catalog" renderItem={type => <HydraButton className={`hydra-designer-list-item${selection?.kind === 'EVENT' && selection.id === type.id ? ' selected' : ''}`} type="button" onClick={() => setSelection({ kind: 'EVENT', id: type.id })}><span><strong>{type.name || 'Unnamed event'}</strong><small>{type.id}</small></span>{!type.enabled && <span className="hydra-designer-list-badge">Off</span>}</HydraButton>} />
                                {definitions.eventTypes.length === 0 && <p className="event-sidebar-empty">No custom events yet.</p>}
                            </section>
                            <section className="hydra-sidebar-section">
                                <div className="hydra-sidebar-section-title"><strong>Bindings</strong><div className="hydra-sidebar-section-actions"><DesignerGuideButton kind="EVENT_BINDING" /><HydraButton type="button" onClick={() => addBinding()}>+ Binding</HydraButton></div></div>
                                <HydraVirtualList items={filteredBindings} itemKey={binding => binding.id} itemHeight={54} height={380} threshold={120} ariaLabel="Event binding catalog" renderItem={binding => <HydraButton className={`hydra-designer-list-item${selection?.kind === 'BINDING' && selection.id === binding.id ? ' selected' : ''}`} type="button" onClick={() => setSelection({ kind: 'BINDING', id: binding.id })}><span><strong>{binding.name || 'Unnamed binding'}</strong><small>{binding.id}</small></span>{!binding.enabled && <span className="hydra-designer-list-badge">Off</span>}</HydraButton>} />
                                {bindings.bindings.length === 0 && <p className="event-sidebar-empty">No bindings yet.</p>}
                            </section>
                        </div>
                    </aside>
                    <div {...sidebar.separatorProps} />
                    <main className="hydra-designer-form">
                        <section className="event-document-settings"><header><div><h3>Event document</h3><p>Shared configuration for event types and bindings.</p></div></header><div className="hydra-designer-fields">
                            <DesignerHelpedField label="Namespace" targetId="event-namespace" required help="Namespace shared by the custom event catalog. It prevents collisions between event families." example="quality.people"><input id="event-namespace" value={definitions.namespace} onChange={updateNamespace} /></DesignerHelpedField>
                            <DesignerHelpedField label="Definition model ID" targetId="event-definition-model-id" required help="Stable identifier of the complete event-type document." example="people-events"><input id="event-definition-model-id" value={definitions.id} readOnly /></DesignerHelpedField>
                        </div></section>
                        {selection === null && <div className="hydra-empty-state"><strong>No event element selected</strong><span>Create or select an event type or binding from the left panel.</span></div>}
                        {selectedType !== null && <><EventTypeForm type={selectedType} onChange={updateType} onRenameId={renameTypeId} onUpdateField={updateField} onRenameFieldId={renameFieldId} onDelete={() => deleteType()} onCreateAnother={() => addType()} /><EventConnectionSummary eventTypeId={selectedType.id} bindings={bindings.bindings} onSelectBinding={selectBindingProperties} /></>}
                        {selectedBinding !== null && <EventBindingForm binding={selectedBinding} target={bindingTarget} sourceTypes={sourceTypes} customTypes={definitions.eventTypes} onChange={updateBinding} onRenameId={renameBindingId} onAddMapping={addMapping} onReplaceMapping={replaceMapping} onDeleteMapping={deleteMapping} onDelete={() => deleteBinding()} />}
                    </main>
                </div>
            )}
            {view === 'STRUCTURE' && structureTabOpen && <section className="designer-structure-workspace">
                <header><div><h3>{t('Event structure')}</h3><p>{t('Custom events, event data, bindings, mappings and the system event catalog.')}</p></div><div className="designer-structure-header-actions"><HydraButton type="button" onClick={() => setStructureCollapsed(value => !value)}>{structureCollapsed ? t('Expand tree') : t('Collapse tree')}</HydraButton></div></header>
                {!structureCollapsed && <div className="designer-structure-layout designer-structure-triple-layout">
                    <div className="designer-structure-tree-pane designer-semantic-tree-pane">
                        <header className="designer-structure-pane-heading"><div><h4>{t('Metamodel')}</h4><p>{t('Semantic classes, features, operations and relations.')}</p></div></header>
                        <div className="designer-structure-tree-toolbar"><input aria-label={t('Search event metamodel tree')} placeholder={t('Search metamodel...')} value={semanticFilter} onChange={event => setSemanticFilter(event.target.value)} /></div>
                        <div className="designer-structure-tree-scroll"><MetamodelStructureTree metamodel={semanticMetamodel} filter={semanticFilter} selection={semanticSelection} onSelect={next => { setSemanticSelection(next); setStructurePropertiesOpen(true); setStructurePropertiesCollapsed(false); }} /></div>
                    </div>
                    <div className="designer-structure-tree-pane designer-defined-tree-pane">
                        <header className="designer-structure-pane-heading"><div><h4>{t('Defined event structure')}</h4><p>{t('Events, event data, bindings and catalog entries configured here.')}</p></div></header>
                        <div className="designer-structure-tree-toolbar"><input aria-label={t('Search event structure tree')} placeholder={t('Search events, fields and bindings...')} value={structureFilter} onChange={event => setStructureFilter(event.target.value)} /></div>
                        <div className="designer-structure-tree-scroll"><EventStructureTree definitions={definitions} bindings={bindings} reservedTypes={reservedTypes} selection={selection} filter={structureFilter} onSelect={selectStructureItem} /></div>
                    </div>
                    {structurePropertiesOpen ? <aside className={`designer-structure-properties${structurePropertiesCollapsed ? ' collapsed' : ''}`} aria-label={t('Event structure properties')}><header><h4>{t('Properties')}</h4><div><HydraButton type="button" onClick={() => setStructurePropertiesCollapsed(value => !value)}>{structurePropertiesCollapsed ? t('Expand properties') : t('Collapse properties')}</HydraButton><HydraButton type="button" aria-label={t('Close event structure properties')} className="hydra-form-close-button" onClick={() => setStructurePropertiesOpen(false)}>×</HydraButton></div></header>{!structurePropertiesCollapsed && <div className="designer-structure-properties-body">{semanticSelection !== null && semanticMetamodel !== null ? <MetamodelSelectionProperties selection={semanticSelection} metamodel={semanticMetamodel} /> : inspector}</div>}</aside> : <HydraButton className="editor-structure-properties-reopen" type="button" onClick={() => setStructurePropertiesOpen(true)}>+ {t('Properties')}</HydraButton>}
                </div>}
            </section>}
            {view === 'ARCHITECTURE' && <HydraErrorBoundary scope="Event architecture" resetKey={`${metamodelId}:${diagram.id}:${diagram.nodes.length}:${bindings.bindings.length}`} onError={exception => onError(`Event architecture interface error: ${exception.message}`)}><EventArchitectureVisualEditor
                nodes={visual.nodes}
                edges={visual.edges}
                catalog={eventArchitectureCatalog}
                inspector={inspector}
                canUndo={canUndo}
                canRedo={canRedo}
                onNodesChange={onVisualNodesChange}
                onEdgesChange={onVisualEdgesChange}
                onConnect={connectVisual}
                onSelectNode={selectVisualNode}
                onSelectEdge={selectVisualEdge}
                onAddCatalogEvent={addCatalogEventToDiagram}
                onCreateCustomEvent={position => addType(position, true)}
                onRemoveFromCanvas={removeVisualSelection}
                onDuplicate={duplicateVisualSelection}
                onClearCanvas={clearVisualCanvas}
                onAutoLayout={autoLayout}
                onElkLayout={elkLayout}
                onUndo={undo}
                onRedo={redo}
                onNodeDragStart={startNodeDrag}
                onNodeDragStop={stopNodeDrag}
            /></HydraErrorBoundary>}
            {overwriteDialogs}
            {view === 'SOURCE' && <div className="event-source-editor"><textarea aria-label="Event definitions and bindings JSON source" value={source} onChange={event => setSource(event.target.value)} /><HydraButton type="button" onClick={applySource}>Apply source</HydraButton></div>}
        </section>
    );
}
