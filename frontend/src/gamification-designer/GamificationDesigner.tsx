import { HydraButton } from '../design-system/HydraButton';
import { explainInputError } from '../api/inputErrorExplanation';
import { applyEdgeChanges, applyNodeChanges } from '@xyflow/react';
import type { Connection, Edge, EdgeChange, Node, NodeChange } from '@xyflow/react';
import { useEffect, useMemo, useRef, useState } from 'react';
import { DesignerCommandBar, DesignerCommandGroup } from '../designer-ui/DesignerCommandBar';
import { useHydraLanguage } from '../i18n';
import { useBoundedDesignerHistory } from '../designer-ui/useBoundedDesignerHistory';
import { sameDiagramGeometry } from '../designer-ui/diagramGeometry';
import type { ReactNode } from 'react';
import { DesignerGuideButton, DesignerHelpedField } from '../designer-ui/DesignerFieldHelp';
import { useResizableDesignerSidebar } from '../designer-ui/useResizableDesignerSidebar';
import type { HydraEditableConnectionVisual } from '../designer-ui/HydraConnectionVisualEditor';
import { DEFAULT_HYDRA_SOURCE_PORT, DEFAULT_HYDRA_TARGET_PORT } from '../designer-ui/HydraConnectionVisual';
import { MetamodelSelectionProperties, MetamodelStructureTree } from '../designer-ui/MetamodelStructureTree';
import type { MetamodelStructureSelection } from '../designer-ui/MetamodelStructureTree';
import { loadMetaModel } from '../metamodel-designer/api';
import type { MetaModel } from '../metamodel-designer/types';
import { isMissingValidationResource } from '../validation/http';
import { loadEventModels, loadReservedEventTypes } from '../event-designer/api';
import type { EventTypeDefinition } from '../event-designer/types';
import { presentEventType } from '../event-designer/eventTypePresentation';
import {
    checkGamification,
    createGamification,
    loadGamification,
    loadGamificationDiagram,
    loadGamificationRuntime,
    loadGamificationFeedback,
    replaceGamification,
    resetGamificationRuntime,
    saveGamificationDiagram
} from './api';
import { createEmptyGamificationModels } from './gamificationFactory';
import { findGamificationBlockingError } from './gamificationValidation';
import { GamificationVisualEditor } from './GamificationVisualEditor';
import type { GamificationEvidencePaletteEntry } from './GamificationVisualEditor';
import { GamificationStructureTree } from './GamificationStructureTree';
import type { GamificationPaletteKind } from './GamificationVisualEditor';
import {
    addGamificationEvidenceNode,
    autoLayoutGamificationDiagram,
    createEmptyGamificationDiagram,
    gamificationNodeId,
    parseGamificationNodeId,
    synchronizeGamificationDiagram as synchronizeGamificationDiagramDocument,
    toGamificationFlowElements,
} from './gamificationDiagram';
import { applyGamificationConnection } from './gamificationConnections';
import type { GamificationEdgeData, GamificationNodeData } from './gamificationDiagram';
import type { GamificationBindingModel, GamificationDefinitionModel, GamificationDiagnostic, GamificationDiagramDocument, GamificationRule, GamificationRuntimeSnapshot, UserFeedbackEnvelope } from './types';
import { HydraDiagnosticList, HydraGuidedWorkflow, diagnosticAsActionableError } from '../design-system';
import { LayerInterchangeDialog } from '../layer-io';
import type { LayerImportRequest } from '../layer-io';
import { analyzeGamificationImport, GAMIFICATION_INTERCHANGE_FORMATS, exportGamificationLayer, importGamificationLayer, type GamificationInterchangeFormat } from '../layer-io/gamificationInterchange';
import { GamificationFeedbackPolicyEditor } from './GamificationFeedbackPolicyEditor';
import { GamificationConnectionSummary, GamificationElementForm, GamificationEvidenceInspector, GamificationLedgerInspector, GamificationLedgerView, GamificationRelationInspector, type GamificationSelection } from './GamificationDesignerParts';
import { emptyGamificationKindLabel, findSelected, firstSelection, firstSelectionById, GAMIFICATION_KIND_GUIDANCE, GAMIFICATION_KIND_LABELS, items, mergeGamificationItems, singularLabel, totalItems } from './gamificationDesignerSupport';
import { removeSelectedGamificationNodes, removeSelectedGamificationRelations } from './gamificationSelection';
import { addGamificationItem, duplicateGamificationItem, removeGamificationItem, renameGamificationItem } from './gamificationDesignerModel';
import './gamification-designer.css';
import { layoutGamificationElk } from './gamificationElkLayout';
import { hydraGeometryChanges, readHydraNodeSize } from '../designer-ui/hydraNodeGeometry';
import { ResizableInfoTooltip } from '../designer-ui/ResizableInfoTooltip';
import { useHydraOverwriteConfirmation } from '../hooks/useHydraOverwriteConfirmation';
import type { HydraPersistedIdentity } from '../hooks/hydraOverwritePolicy';
import { gamificationOverwriteIdentities } from './gamificationOverwriteIdentity';
interface Props {
    metamodelId: string;
    metamodel?: MetaModel;
    persisted: boolean;
    onError: (message: string) => void;
    onMessage: (message: string) => void;
}
type View = 'GUIDED' | 'FORM' | 'STRUCTURE' | 'DIAGRAM' | 'SOURCE' | 'LEDGER';
const KINDS: NonNullable<GamificationSelection>['kind'][] = ['POINT', 'LEVEL', 'ACHIEVEMENT', 'CHALLENGE', 'REWARD', 'RULE'];
interface GamificationDesignerSnapshot {
    definitions: GamificationDefinitionModel;
    bindings: GamificationBindingModel;
    diagram: GamificationDiagramDocument;
    selection: GamificationSelection;
}


export function GamificationDesigner(props: Props) {
    return <GamificationDesignerSession key={`${props.metamodelId}:${props.persisted}`} {...props} />;
}

function GamificationDesignerSession({ metamodelId, metamodel, persisted, onError, onMessage }: Props) {
    const { t, language } = useHydraLanguage();
    const sidebar = useResizableDesignerSidebar({ storageKey: 'hydra:gamification-designer:sidebar-width', ariaLabel: 'Resize gamification palette', initialWidth: 340 });

    const initial = createEmptyGamificationModels(metamodelId);
    const [definitions, setDefinitions] = useState<GamificationDefinitionModel>(initial.definitions);
    const [bindings, setBindings] = useState<GamificationBindingModel>(initial.bindings);
    const [diagram, setDiagram] = useState<GamificationDiagramDocument>(() => createEmptyGamificationDiagram(initial.definitions, initial.bindings));
    const [definitionEtag, setDefinitionEtag] = useState('');
    const [bindingEtag, setBindingEtag] = useState('');
    const [persistedGamificationIdentities, setPersistedGamificationIdentities] = useState<HydraPersistedIdentity[]>([]);
    const currentGamificationIdentities = useMemo(() => gamificationOverwriteIdentities(definitions, bindings), [bindings, definitions]);
    const { confirmOverwrite, overwriteDialogs } = useHydraOverwriteConfirmation({
        current: currentGamificationIdentities,
        persisted: persistedGamificationIdentities
    });
    const [diagramEtag, setDiagramEtag] = useState('');
    const [view, setView] = useState<View>('FORM');
    const [guidedStep, setGuidedStep] = useState('catalog');
    const [structureTabOpen, setStructureTabOpen] = useState(true);
    const [structureCollapsed, setStructureCollapsed] = useState(false);
    const [structurePropertiesOpen, setStructurePropertiesOpen] = useState(true);
    const [structurePropertiesCollapsed, setStructurePropertiesCollapsed] = useState(false);
    const [structureFilter, setStructureFilter] = useState('');
    const [semanticFilter, setSemanticFilter] = useState('');
    const [loadedSemanticMetamodel, setLoadedSemanticMetamodel] = useState<MetaModel | null>(metamodel ?? null);
    const semanticMetamodel = metamodel ?? loadedSemanticMetamodel;
    const [semanticSelection, setSemanticSelection] = useState<MetamodelStructureSelection | null>(null);
    const [selection, setSelection] = useState<GamificationSelection>(null);
    const [filter, setFilter] = useState('');
    const [dirty, setDirty] = useState(false);
    const [source, setSource] = useState(JSON.stringify(initial, null, 2));
    const [runtime, setRuntime] = useState<GamificationRuntimeSnapshot | null>(null);
    const [feedback, setFeedback] = useState<UserFeedbackEnvelope[]>([]);
    const [diagnostics, setDiagnostics] = useState<GamificationDiagnostic[]>([]);
    const [selectedDiagramNodeId, setSelectedDiagramNodeId] = useState('');
    const [selectedDiagramEdgeId, setSelectedDiagramEdgeId] = useState('');
    const [reservedEventTypes, setReservedEventTypes] = useState<string[]>([]);
    const [customEventTypes, setCustomEventTypes] = useState<EventTypeDefinition[]>([]);
    const [layerIoMode, setLayerIoMode] = useState<'IMPORT' | 'EXPORT' | null>(null);
    const dragSnapshot = useRef<GamificationDesignerSnapshot | null>(null);
    const onErrorRef = useRef(onError);
    const { remember, undo, redo, clear: clearHistory, canUndo, canRedo } = useBoundedDesignerHistory(snapshot, restore, 50);
    useEffect(() => { onErrorRef.current = onError; }, [onError]);

    useEffect(() => {
        let active = true;
        const empty = createEmptyGamificationModels(metamodelId);
        const emptyDiagram = createEmptyGamificationDiagram(empty.definitions, empty.bindings);
        if (!persisted) return () => { active = false; };
        void Promise.allSettled([
            loadGamification(metamodelId),
            loadGamificationDiagram(metamodelId),
            loadGamificationRuntime(metamodelId),
            loadGamificationFeedback(metamodelId)
        ]).then(results => {
            if (!active) return;
            const contract = results[0];
            let loadedDefinitions = empty.definitions;
            let loadedBindings = empty.bindings;
            let loadedRuntime: GamificationRuntimeSnapshot | null = null;
            if (contract.status === 'fulfilled') {
                loadedDefinitions = contract.value.definitions;
                loadedBindings = contract.value.bindings;
                setDefinitions(loadedDefinitions);
                setBindings(loadedBindings);
                setDefinitionEtag(contract.value.definitionEtag);
                setBindingEtag(contract.value.bindingEtag);
                setPersistedGamificationIdentities(gamificationOverwriteIdentities(loadedDefinitions, loadedBindings));
                setDiagnostics(contract.value.checkReport?.diagnostics ?? []);
                setSource(JSON.stringify({ definitions: loadedDefinitions, bindings: loadedBindings }, null, 2));
                setSelection(firstSelection(loadedDefinitions, loadedBindings));
            } else if (!isMissingValidationResource(contract.reason)) {
                onErrorRef.current(explainInputError(contract.reason, 'Could not load gamification', 'Gamification Designer'));
            }
            const state = results[2];
            if (state.status === 'fulfilled') {
                loadedRuntime = state.value;
                setRuntime(loadedRuntime);
            }
            const feedbackResult = results[3];
            if (feedbackResult.status === 'fulfilled') setFeedback(feedbackResult.value);
            const diagramResult = results[1];
            if (diagramResult.status === 'fulfilled') {
                setDiagram(synchronizeGamificationDiagramDocument(loadedDefinitions, loadedBindings, loadedRuntime, diagramResult.value.diagram));
                setDiagramEtag(diagramResult.value.etag);
            } else if (isMissingValidationResource(diagramResult.reason)) {
                setDiagram(synchronizeGamificationDiagramDocument(loadedDefinitions, loadedBindings, loadedRuntime, emptyDiagram));
            } else {
                console.warn('Could not load saved gamification diagram; rebuilding it from the semantic contract.', diagramResult.reason); setDiagram(synchronizeGamificationDiagramDocument(loadedDefinitions, loadedBindings, loadedRuntime, emptyDiagram)); setDiagramEtag('');
            }
        });
        return () => { active = false; };
    }, [metamodelId, persisted]);

    useEffect(() => {
        if (metamodel !== undefined) return undefined;
        let active = true;
        void loadMetaModel(metamodelId).then(result => { if (active) setLoadedSemanticMetamodel(result.metamodel); }).catch(() => { if (active) setLoadedSemanticMetamodel(null); });
        return () => { active = false; };
    }, [metamodel, metamodelId]);

    useEffect(() => {
        let active = true;
        void Promise.allSettled([loadReservedEventTypes(), loadEventModels(metamodelId)]).then(results => {
            if (!active) return;
            if (results[0].status === 'fulfilled') setReservedEventTypes(results[0].value);
            const eventModels = results[1];
            if (eventModels.status === 'fulfilled') setCustomEventTypes(eventModels.value.definitions.eventTypes);
            else if (isMissingValidationResource(eventModels.reason)) setCustomEventTypes([]);
        });
        return () => { active = false; };
    }, [metamodelId]);

    function selectView(view: View): void {
        if (view === 'SOURCE') setSource(JSON.stringify({ definitions, bindings }, null, 2));
        setView(view);
    }

    const availableEventTypeIds = useMemo(() => Array.from(new Set([
        ...reservedEventTypes,
        ...customEventTypes.map(type => type.id),
        ...bindings.rules.map(rule => rule.eventTypeId).filter(Boolean)
    ])), [bindings.rules, customEventTypes, reservedEventTypes]);

    function synchronizeGamificationDiagram(nextDefinitions: GamificationDefinitionModel, nextBindings: GamificationBindingModel,
            nextRuntime: GamificationRuntimeSnapshot | null, current: GamificationDiagramDocument): GamificationDiagramDocument {
        return synchronizeGamificationDiagramDocument(nextDefinitions, nextBindings, nextRuntime, current, availableEventTypeIds, customEventTypes);
    }

    const evidenceCatalog = useMemo<GamificationEvidencePaletteEntry[]>(() => {
        const onCanvas = new Set(diagram.nodes.map(node => parseGamificationNodeId(node.nodeId)).filter((item): item is NonNullable<ReturnType<typeof parseGamificationNodeId>> => item !== null && item.kind === 'EVIDENCE').map(item => item.elementId));
        return availableEventTypeIds.map(eventTypeId => {
            const presentation = presentEventType(eventTypeId, customEventTypes);
            return { eventTypeId, label: presentation.label, description: presentation.description, onCanvas: onCanvas.has(eventTypeId) };
        });
    }, [availableEventTypeIds, customEventTypes, diagram.nodes]);

    const selectedItem = findSelected(selection, definitions, bindings);
    const normalizedFilter = filter.trim().toLowerCase();
    const visual = useMemo(() => toGamificationFlowElements(definitions, bindings, runtime, diagram, selectedDiagramNodeId, selectedDiagramEdgeId, customEventTypes),
        [bindings, customEventTypes, definitions, diagram, runtime, selectedDiagramEdgeId, selectedDiagramNodeId]);

    function snapshot(): GamificationDesignerSnapshot { return { definitions, bindings, diagram, selection }; }

    function restore(value: GamificationDesignerSnapshot): void {
        setDefinitions(value.definitions);
        setBindings(value.bindings);
        setDiagram(synchronizeGamificationDiagram(value.definitions, value.bindings, runtime, value.diagram));
        setSelection(value.selection);
        setSelectedDiagramNodeId(value.selection === null ? '' : gamificationNodeId(value.selection.kind, value.selection.id));
        setSelectedDiagramEdgeId('');
        setSource(JSON.stringify({ definitions: value.definitions, bindings: value.bindings }, null, 2));
        setDirty(true);
    }


    function changeDefinitions(next: GamificationDefinitionModel): void {
        setDefinitions(next);
        setDiagram(current => synchronizeGamificationDiagram(next, bindings, runtime, current));
        setDirty(true);
    }

    function changeBindings(next: GamificationBindingModel): void {
        setBindings(next);
        setDiagram(current => synchronizeGamificationDiagram(definitions, next, runtime, current));
        setDirty(true);
    }

    function add(kind: NonNullable<GamificationSelection>['kind'], position?: { x: number; y: number }, stayInDiagram = false): void {
        const mutation = addGamificationItem(kind, definitions, bindings);
        if (mutation === null) { onError('Create a point type before adding a level.'); return; }
        remember();
        const selectedNode = gamificationNodeId(mutation.selection.kind, mutation.selection.id);
        const synchronized = synchronizeGamificationDiagram(mutation.definitions, mutation.bindings, runtime, diagram);
        const positioned = position === undefined ? synchronized : { ...synchronized,
            nodes: synchronized.nodes.map(node => node.nodeId === selectedNode ? { ...node, x: position.x, y: position.y } : node) };
        setDefinitions(mutation.definitions); setBindings(mutation.bindings); setDiagram(positioned); setSelection(mutation.selection);
        setSelectedDiagramNodeId(selectedNode); setSelectedDiagramEdgeId(''); setDirty(true); setView(stayInDiagram ? 'DIAGRAM' : 'FORM');
    }

    function addEvidenceToDiagram(eventTypeId: string, position?: { x: number; y: number }): void {
        if (!availableEventTypeIds.includes(eventTypeId)) {
            onError('What went wrong: The selected event is no longer available.\nWhy it is invalid: Gamification evidence must reference an event from the current metamodel.\nHow to fix it: Reload the event catalog or create and save the event in Event Designer first.');
            return;
        }
        remember();
        setDiagram(current => addGamificationEvidenceNode(current, eventTypeId, position));
        setSelectedDiagramNodeId(gamificationNodeId('EVIDENCE', eventTypeId));
        setSelectedDiagramEdgeId('');
        setDirty(true);
        selectView('DIAGRAM');
    }

    function removeSelection(recordHistory = true, target: GamificationSelection = selection): void {
        if (target === null) return;
        if (recordHistory) remember();
        const mutation = removeGamificationItem(target, definitions, bindings);
        setDefinitions(mutation.definitions); setBindings(mutation.bindings);
        setDiagram(current => synchronizeGamificationDiagram(mutation.definitions, mutation.bindings, runtime, current));
        setSelection(null); setSelectedDiagramNodeId(''); setSelectedDiagramEdgeId(''); setDirty(true);
    }

    function renameSelectionId(nextId: string): void {
        if (selection === null || selection.id === nextId) return;
        const previousNodeId = gamificationNodeId(selection.kind, selection.id);
        const nextNodeId = gamificationNodeId(selection.kind, nextId);
        const mutation = renameGamificationItem(selection, nextId, definitions, bindings);
        const renamedDiagram = { ...diagram, nodes: diagram.nodes.map(node => node.nodeId === previousNodeId ? { ...node, nodeId: nextNodeId } : node) };
        setDefinitions(mutation.definitions); setBindings(mutation.bindings);
        setDiagram(synchronizeGamificationDiagram(mutation.definitions, mutation.bindings, runtime, renamedDiagram));
        setSelection({ ...selection, id: nextId }); setSelectedDiagramNodeId(nextNodeId); setDirty(true);
    }

    function updateRule(rule: GamificationRule): void {
        changeBindings({ ...bindings, rules: bindings.rules.map(item => item.id === rule.id ? rule : item) });
    }

    function applySource(): void {
        try {
            const parsed = JSON.parse(source) as { definitions: GamificationDefinitionModel; bindings: GamificationBindingModel };
            const error = findGamificationBlockingError(parsed.definitions, parsed.bindings);
            if (error !== '') { onError(error); return; }
            if (parsed.definitions.metamodelId !== metamodelId || parsed.bindings.metamodelId !== metamodelId) {
                onError('Source metamodelId must match the selected metamodel.');
                return;
            }
            remember();
            const nextSelection = firstSelection(parsed.definitions, parsed.bindings);
            setDefinitions(parsed.definitions);
            setBindings(parsed.bindings);
            setDiagram(current => synchronizeGamificationDiagram(parsed.definitions, parsed.bindings, runtime, current));
            setSelection(nextSelection);
            setSelectedDiagramNodeId(nextSelection === null ? '' : gamificationNodeId(nextSelection.kind, nextSelection.id));
            setSelectedDiagramEdgeId('');
            setDirty(true);
            onMessage('Gamification source applied');
        } catch (error) {
            onError(explainInputError(error, 'Invalid gamification JSON', 'the Gamification source tab'));
        }
    }

    async function validate(): Promise<void> {
        const local = findGamificationBlockingError(definitions, bindings);
        if (local !== '') { onError(local); return; }
        try {
            const report = await checkGamification(definitions, bindings);
            setDiagnostics(report.diagnostics);
            const errors = report.diagnostics.filter(item => item.severity === 'ERROR');
            if (errors.length > 0) onError(diagnosticAsActionableError(errors[0], language, 'gamification diagram', errors.length));
            else onMessage(`Gamification valid (${report.diagnostics.length} diagnostic(s))`);
        } catch (error) {
            onError(explainInputError(error, 'Could not check gamification', 'Gamification Designer'));
        }
    }

    async function save(): Promise<boolean> {
        if (!persisted) { onError('Save the metamodel before configuring gamification.'); return false; }
        const local = findGamificationBlockingError(definitions, bindings);
        if (local !== '') { onError(local); return false; }
        if (!await confirmOverwrite()) return false;
        try {
            const result = definitionEtag === ''
                ? await createGamification(definitions, bindings)
                : await replaceGamification(definitions, definitionEtag, bindings, bindingEtag);
            const synchronized = synchronizeGamificationDiagram(result.definitions, result.bindings, runtime, diagram);
            const savedDiagram = await saveGamificationDiagram(synchronized, diagramEtag);
            setDefinitions(result.definitions);
            setBindings(result.bindings);
            setDiagram(savedDiagram.diagram);
            setDefinitionEtag(result.definitionEtag);
            setBindingEtag(result.bindingEtag);
            setPersistedGamificationIdentities(gamificationOverwriteIdentities(result.definitions, result.bindings));
            setDiagramEtag(savedDiagram.etag);
            setDirty(false);
            clearHistory();
            setSource(JSON.stringify({ definitions: result.definitions, bindings: result.bindings }, null, 2));
            onMessage('Gamification definitions, rules and diagram saved');
            return true;
        } catch (error) {
            onError(explainInputError(error, 'Could not save gamification', 'Gamification Designer'));
            return false;
        }
    }

    async function refreshRuntime(): Promise<void> {
        try {
            const [loaded,loadedFeedback] = await Promise.all([loadGamificationRuntime(metamodelId),loadGamificationFeedback(metamodelId)]);
            setRuntime(loaded);
            setFeedback(loadedFeedback);
            setDiagram(current => synchronizeGamificationDiagram(definitions, bindings, loaded, current));
        } catch (error) { onError(explainInputError(error, 'Could not load gamification runtime', 'Gamification Designer')); }
    }

    async function resetRuntime(): Promise<void> {
        try {
            await resetGamificationRuntime(metamodelId);
            await refreshRuntime();
            onMessage('Gamification runtime reset');
        } catch (error) {
            onError(explainInputError(error, 'Could not reset gamification runtime', 'Gamification Designer'));
        }
    }

    function onVisualNodesChange(changes: NodeChange<Node<GamificationNodeData>>[]): void {
        const selectedChange = changes.find(change => change.type === 'select' && change.selected);
        if (selectedChange !== undefined && 'id' in selectedChange) selectVisualNode(visual.nodes.find(node => node.id === selectedChange.id));
        const geometryChanges = hydraGeometryChanges(changes);
        if (geometryChanges.length === 0) return;
        const next = applyNodeChanges(geometryChanges, visual.nodes);
        const nextGeometry = next.map(node => { const size = readHydraNodeSize(node, 210, 84); return { nodeId: node.id, x: node.position.x, y: node.position.y,
            width: size.width, height: size.height, collapsed: false }; });
        setDiagram(current => sameDiagramGeometry(current.nodes, nextGeometry) ? current : { ...current, nodes: nextGeometry });
        setDirty(true);
    }

    function onVisualEdgesChange(changes: EdgeChange<Edge<GamificationEdgeData>>[]): void {
        const selectedChange = changes.find(change => change.type === 'select' && change.selected);
        if (selectedChange !== undefined && 'id' in selectedChange) selectVisualEdge(visual.edges.find(edge => edge.id === selectedChange.id));
        const removed = changes.filter(change => change.type === 'remove');
        for (const change of removed) {
            const edge = visual.edges.find(item => item.id === change.id);
            if (edge !== undefined) removeVisualRelation(edge);
        }
        applyEdgeChanges(changes, visual.edges);
    }

    function selectVisualNode(node: Node<GamificationNodeData> | undefined): void {
        if (node === undefined) return;
        setSelectedDiagramNodeId(node.id);
        setSelectedDiagramEdgeId('');
        const parsed = parseGamificationNodeId(node.id);
        if (parsed !== null && parsed.kind !== 'EVIDENCE' && parsed.kind !== 'LEDGER') {
            setSelection({ kind: parsed.kind, id: parsed.elementId } as NonNullable<GamificationSelection>);
        }
    }

    function selectVisualEdge(edge: Edge<GamificationEdgeData> | undefined): void {
        if (edge === undefined) return;
        setSelectedDiagramEdgeId(edge.id);
        setSelectedDiagramNodeId('');
    }

    function connectVisual(connection: Connection): void {
        if (connection.source === null || connection.target === null) return;
        const result = applyGamificationConnection(definitions, bindings, connection.source, connection.target);
        if (!result.accepted) {
            onError(result.message);
            return;
        }
        remember();
        setDefinitions(result.definitions);
        setBindings(result.bindings);
        setDiagram(current => {
            const synchronized = synchronizeGamificationDiagram(result.definitions, result.bindings, runtime, current);
            return { ...synchronized, edges: synchronized.edges.map(edge => edge.edgeId === result.edgeId ? { ...edge, sourceHandle: connection.sourceHandle ?? DEFAULT_HYDRA_SOURCE_PORT, targetHandle: connection.targetHandle ?? DEFAULT_HYDRA_TARGET_PORT } : edge) };
        });
        setSelectedDiagramNodeId('');
        setSelectedDiagramEdgeId(result.edgeId);
        setDirty(true);
    }

    function removeVisualRelation(edge: Edge<GamificationEdgeData>, recordHistory = true): void {
        const data = edge.data;
        if (data === undefined || data.targetKind === 'LEDGER') {
            if (data?.targetKind === 'LEDGER') onError('Ledger links are derived from runtime evidence and cannot be removed manually.');
            return;
        }
        if (recordHistory) remember();
        let nextDefinitions = definitions;
        let nextBindings = bindings;
        if (data.sourceKind === 'EVIDENCE' && data.targetKind === 'RULE') {
            nextBindings = { ...bindings, rules: bindings.rules.map(rule => rule.id === data.targetElementId ? { ...rule, eventTypeId: '' } : rule) };
        } else if (data.sourceKind === 'RULE' && data.targetKind === 'POINT') {
            nextBindings = { ...bindings, rules: bindings.rules.map(rule => rule.id === data.sourceElementId ? { ...rule, pointsTypeId: '', points: 0 } : rule) };
        } else if (data.sourceKind === 'RULE' && data.targetKind === 'ACHIEVEMENT') {
            nextBindings = { ...bindings, rules: bindings.rules.map(rule => rule.id === data.sourceElementId ? { ...rule, achievementId: '' } : rule) };
        } else if (data.sourceKind === 'RULE' && data.targetKind === 'CHALLENGE') {
            nextBindings = { ...bindings, rules: bindings.rules.map(rule => rule.id === data.sourceElementId ? { ...rule, challengeId: '' } : rule) };
        } else if (data.sourceKind === 'RULE' && data.targetKind === 'REWARD') {
            nextBindings = { ...bindings, rules: bindings.rules.map(rule => rule.id === data.sourceElementId ? { ...rule, rewardId: '' } : rule) };
        } else if (data.sourceKind === 'POINT' && data.targetKind === 'LEVEL') {
            nextDefinitions = { ...definitions, levels: definitions.levels.map(level => level.id === data.targetElementId ? { ...level, pointTypeId: '' } : level) };
        } else if (data.targetKind === 'REWARD' && data.sourceKind === 'LEVEL') {
            nextDefinitions = { ...definitions, levels: definitions.levels.map(level => level.id === data.sourceElementId ? { ...level, rewardId: '' } : level) };
        } else if (data.targetKind === 'REWARD' && data.sourceKind === 'ACHIEVEMENT') {
            nextDefinitions = { ...definitions, achievements: definitions.achievements.map(item => item.id === data.sourceElementId ? { ...item, rewardId: '' } : item) };
        } else if (data.targetKind === 'REWARD' && data.sourceKind === 'CHALLENGE') {
            nextDefinitions = { ...definitions, challenges: definitions.challenges.map(item => item.id === data.sourceElementId ? { ...item, rewardId: '' } : item) };
        }
        setDefinitions(nextDefinitions);
        setBindings(nextBindings);
        setDiagram(current => synchronizeGamificationDiagram(nextDefinitions, nextBindings, runtime, current));
        setSelectedDiagramEdgeId('');
        setDirty(true);
    }

    function deleteVisualSelection(): void {
        const selectedEdge = visual.edges.find(edge => edge.id === selectedDiagramEdgeId);
        if (selectedEdge !== undefined) { removeVisualRelation(selectedEdge); return; }
        const parsed = parseGamificationNodeId(selectedDiagramNodeId);
        if (parsed === null) return;
        if (parsed.kind === 'LEDGER') { onError('The runtime ledger is a derived view and cannot be deleted.'); return; }
        if (parsed.kind === 'EVIDENCE') {
            remember();
            const nextBindings = { ...bindings, rules: bindings.rules.map(rule => rule.eventTypeId === parsed.elementId ? { ...rule, eventTypeId: '' } : rule) };
            setBindings(nextBindings);
            setDiagram(current => synchronizeGamificationDiagram(definitions, nextBindings, runtime, { ...current, nodes: current.nodes.filter(node => node.nodeId !== selectedDiagramNodeId) }));
            setSelectedDiagramNodeId('');
            setDirty(true);
            return;
        }
        removeSelection(true, { kind: parsed.kind, id: parsed.elementId } as NonNullable<GamificationSelection>);
    }

    function deleteVisualItems(nodeIds: string[], edgeIds: string[]): void {
        if (nodeIds.length === 0 && edgeIds.length === 0) return;
        remember();
        const nodeMutation = removeSelectedGamificationNodes(definitions, bindings, nodeIds);
        const next = removeSelectedGamificationRelations(nodeMutation.definitions, nodeMutation.bindings, visual.edges.filter(edge => edgeIds.includes(edge.id)));
        const deletable = new Set(nodeIds.filter(id => parseGamificationNodeId(id)?.kind !== 'LEDGER'));
        setDefinitions(next.definitions); setBindings(next.bindings);
        setDiagram(current => synchronizeGamificationDiagram(next.definitions, next.bindings, runtime, { ...current, nodes: current.nodes.filter(node => !deletable.has(node.nodeId)) }));
        setSelection(null); setSelectedDiagramNodeId(''); setSelectedDiagramEdgeId(''); setDirty(true);
        if (nodeMutation.ignoredLedgerItems + next.ignoredLedgerItems > 0) onError('The runtime ledger is a derived view and cannot be deleted.');
    }

    function duplicateVisualSelection(): void {
        const parsed = parseGamificationNodeId(selectedDiagramNodeId);
        if (parsed === null || parsed.kind === 'EVIDENCE' || parsed.kind === 'LEDGER') return;
        const targetSelection = { kind: parsed.kind, id: parsed.elementId } as NonNullable<GamificationSelection>;
        const selected = findSelected(targetSelection, definitions, bindings);
        if (selected === null) return;
        remember();
        const originalView = diagram.nodes.find(node => node.nodeId === selectedDiagramNodeId);
        const mutation = duplicateGamificationItem(targetSelection, selected, definitions, bindings);
        const nodeId = gamificationNodeId(mutation.selection.kind, mutation.selection.id);
        let nextDiagram = synchronizeGamificationDiagram(mutation.definitions, mutation.bindings, runtime, diagram);
        if (originalView !== undefined) nextDiagram = { ...nextDiagram, nodes: nextDiagram.nodes.map(node => node.nodeId === nodeId ? { ...node, x: originalView.x + 40, y: originalView.y + 40 } : node) };
        setDefinitions(mutation.definitions); setBindings(mutation.bindings); setDiagram(nextDiagram); setSelection(mutation.selection);
        setSelectedDiagramNodeId(nodeId); setSelectedDiagramEdgeId(''); setDirty(true);
    }

    function autoLayoutVisual(): void {
        remember();
        setDiagram(autoLayoutGamificationDiagram(definitions, bindings, runtime, diagram, customEventTypes));
        setDirty(true);
    }

    function elkLayoutVisual(): void {
        if (diagram.nodes.length === 0) return;
        remember(); setDiagram(layoutGamificationElk(definitions, bindings, runtime, diagram, selectedDiagramNodeId, selectedDiagramEdgeId, customEventTypes)); setDirty(true);
    }

    function beginVisualDrag(): void { dragSnapshot.current = snapshot(); }
    function endVisualDrag(): void {
        if (dragSnapshot.current === null) return;
        remember(dragSnapshot.current);
        dragSnapshot.current = null;
    }

    function renameEvidence(previousEventTypeId: string, nextEventTypeId: string): void {
        const nextBindings = { ...bindings, rules: bindings.rules.map(rule => rule.eventTypeId === previousEventTypeId ? { ...rule, eventTypeId: nextEventTypeId } : rule) };
        setBindings(nextBindings);
        const nextNodeId = nextEventTypeId === '' ? '' : gamificationNodeId('EVIDENCE', nextEventTypeId);
        setDiagram(current => synchronizeGamificationDiagram(definitions, nextBindings, runtime, current));
        setSelectedDiagramNodeId(nextNodeId);
        setDirty(true);
    }

    function selectStructureItem(next: NonNullable<GamificationSelection>): void {
        setSemanticSelection(null);
        setSelection(next);
        setSelectedDiagramNodeId(gamificationNodeId(next.kind, next.id));
        setSelectedDiagramEdgeId('');
        setStructurePropertiesOpen(true);
        setStructurePropertiesCollapsed(false);
    }

    function selectGamificationRelation(edgeId: string): void {
        setSelectedDiagramNodeId('');
        setSelectedDiagramEdgeId(edgeId);
        selectView('DIAGRAM');
    }

    function updateGamificationConnectionVisual(edgeId: string, next: HydraEditableConnectionVisual): void {
        remember();
        setDiagram(current => ({ ...current, edges: current.edges.map(edge => edge.edgeId === edgeId ? { ...edge, ...next, sourceHandle: next.sourceHandle ?? DEFAULT_HYDRA_SOURCE_PORT, targetHandle: next.targetHandle ?? DEFAULT_HYDRA_TARGET_PORT, label: next.label ?? '', note: next.note ?? '', routeStyle: next.routeStyle ?? 'ORTHOGONAL', lineStyle: next.lineStyle ?? 'SOLID' } : edge) }));
        setDirty(true);
    }

    function diagramInspector(): ReactNode {
        const selectedEdge = visual.edges.find(edge => edge.id === selectedDiagramEdgeId);
        if (selectedEdge !== undefined) {
            const edgeView = diagram.edges.find(edge => edge.edgeId === selectedEdge.id);
            return <GamificationRelationInspector edge={selectedEdge} visual={{ ...edgeView, sourceHandle: edgeView?.sourceHandle, targetHandle: edgeView?.targetHandle, label: edgeView?.label ?? String(selectedEdge.label ?? ''), note: edgeView?.note }} onVisualChange={next => updateGamificationConnectionVisual(selectedEdge.id, next)} onDelete={() => removeVisualRelation(selectedEdge)} />;
        }
        const parsed = parseGamificationNodeId(selectedDiagramNodeId);
        if (parsed === null) return <div className="gamification-inspector-empty"><strong>No element selected</strong><span>Select a node or relation, or drag a new element from the palette.</span></div>;
        if (parsed.kind === 'EVIDENCE') return <><GamificationEvidenceInspector eventTypeId={parsed.elementId} ruleCount={bindings.rules.filter(rule => rule.eventTypeId === parsed.elementId).length} customEventTypes={customEventTypes} onRename={value => renameEvidence(parsed.elementId, value)} /><GamificationConnectionSummary nodeId={gamificationNodeId('EVIDENCE', parsed.elementId)} nodes={visual.nodes} edges={visual.edges} onSelectRelation={selectGamificationRelation} /></>;
        if (parsed.kind === 'LEDGER') return <GamificationLedgerInspector runtime={runtime} />;
        const targetSelection = { kind: parsed.kind, id: parsed.elementId } as NonNullable<GamificationSelection>;
        const targetItem = findSelected(targetSelection, definitions, bindings);
        if (targetItem === null) return <div className="gamification-inspector-empty"><strong>Element unavailable</strong></div>;
        return <><GamificationElementForm selection={targetSelection} item={targetItem} definitions={definitions} eventTypeIds={availableEventTypeIds} customEventTypes={customEventTypes} onDefinitionsChange={changeDefinitions} onRuleChange={updateRule} onRenameId={renameSelectionId} onDelete={() => removeSelection(true, targetSelection)} /><GamificationConnectionSummary nodeId={gamificationNodeId(targetSelection.kind, targetSelection.id)} nodes={visual.nodes} edges={visual.edges} onSelectRelation={selectGamificationRelation} /></>;
    }

    const gamificationBlockingError = findGamificationBlockingError(definitions, bindings);
    const pointDefinitionsValid = definitions.pointTypes.length > 0 && definitions.pointTypes.every(point => point.id.trim() !== '' && point.name.trim() !== '');
    const progressionReferencesValid = definitions.levels.every(level => level.id.trim() !== '' && level.name.trim() !== '' && definitions.pointTypes.some(point => point.id === level.pointTypeId))
        && definitions.achievements.every(item => item.id.trim() !== '' && item.name.trim() !== '')
        && definitions.challenges.every(item => item.id.trim() !== '' && item.name.trim() !== '' && item.targetCount > 0)
        && definitions.rewards.every(item => item.id.trim() !== '' && item.name.trim() !== '');
    const evidenceRulesValid = bindings.rules.length > 0 && bindings.rules.every(rule => rule.id.trim() !== '' && rule.name.trim() !== '' && rule.eventTypeId.trim() !== '' && (rule.pointsTypeId === '' || definitions.pointTypes.some(point => point.id === rule.pointsTypeId)));
    const openGamificationItem = (kind: NonNullable<GamificationSelection>['kind'], id: string): void => { setSelection({ kind, id }); selectView('FORM'); };
    const guidedSteps = [
        { id:'catalog', title:'Gamification catalog', description:'Name the configuration and establish its point scales.', checks:[
            { id:'document-name', label:'Gamification document has a name', complete:definitions.name.trim() !== '', detail:'This identifies the optional gamification configuration in the project.' },
            { id:'point-type', label:'At least one valid point type exists', complete:pointDefinitionsValid, detail:definitions.pointTypes.length === 0 ? 'Create a point scale before defining levels or scoring rules.' : `${definitions.pointTypes.length} point type(s) available.` }
        ], content:<div className="gamification-guided-block"><div className="hydra-designer-fields"><DesignerHelpedField label="Gamification name" targetId="gamification-guided-name" required help="Visible name for this optional gamification configuration. It groups point scales, progression elements and evidence rules without changing the semantics of EventFlow." example="Model quality gamification"><input id="gamification-guided-name" value={definitions.name} onChange={event => changeDefinitions({ ...definitions, name:event.target.value })} /></DesignerHelpedField><DesignerHelpedField label="Definition model ID" targetId="gamification-guided-id" required help="Stable identifier of the HYDRA gamification catalog document. It is generated and preserved by HYDRA." example="people-gamification"><input id="gamification-guided-id" value={definitions.id} readOnly /></DesignerHelpedField></div><section className="gamification-guided-cards"><header><div><h4>Point types</h4><p>Independent balances used by awards and penalties. Every scoring rule that changes points references one of these scales.</p></div><HydraButton type="button" onClick={() => add('POINT')}>+ Point type</HydraButton></header>{definitions.pointTypes.map(point => <HydraButton key={point.id} type="button" onClick={() => openGamificationItem('POINT', point.id)}><strong>{point.name}</strong><span>{point.id}</span><small>Minimum balance: {point.minimumBalance}</small></HydraButton>)}{definitions.pointTypes.length === 0 && <p>No point type has been created yet.</p>}</section></div> },
        { id:'progression', title:'Progression and rewards', description:'Define optional levels, achievements, challenges and rewards.', checks:[
            { id:'progression-valid', label:'Progression references are valid', complete:progressionReferencesValid, detail:'Levels must reference an existing point scale; all configured progression items need stable IDs and names.' },
            { id:'progression-reviewed', label:'Progression design has been reviewed', complete:true, detail:`${definitions.levels.length} level(s), ${definitions.achievements.length} achievement(s), ${definitions.challenges.length} challenge(s), ${definitions.rewards.length} reward(s). All are optional.` }
        ], content:<div className="gamification-guided-block"><p>Progression elements are optional. Add only the mechanisms the generated editor should actually expose.</p><div className="gamification-guided-category-grid">{(['LEVEL','ACHIEVEMENT','CHALLENGE','REWARD'] as const).map(kind => { const values=items(kind, definitions, bindings); return <section key={kind}><header><strong>{GAMIFICATION_KIND_LABELS[kind]}</strong><HydraButton type="button" onClick={() => add(kind)}>+ Add</HydraButton></header>{values.map(item => <HydraButton type="button" key={item.id} onClick={() => openGamificationItem(kind,item.id)}><span>{item.name}</span><small>{item.id}</small></HydraButton>)}{values.length===0&&<small>None defined.</small>}</section>; })}</div></div> },
        { id:'evidence', title:'Evidence rules', description:'Connect verified events to awards or penalties.', checks:[
            { id:'rule-count', label:'At least one evidence rule exists', complete:bindings.rules.length > 0, detail:'Without a rule the catalog is valid but no event can change gamification state.' },
            { id:'rules-valid', label:'Evidence rules reference valid events and point types', complete:evidenceRulesValid, detail:'Each rule needs a stable name/ID, a source event and valid scoring references.' }
        ], content:<div className="gamification-guided-block"><section className="gamification-guided-cards"><header><div><h4>Verified evidence rules</h4><p>Rules consume HYDRA events after the underlying action has succeeded. Use penalties to subtract points and awards for positive outcomes.</p></div><HydraButton type="button" onClick={() => add('RULE')}>+ Evidence rule</HydraButton></header>{bindings.rules.map(rule => <HydraButton key={rule.id} type="button" onClick={() => openGamificationItem('RULE',rule.id)}><strong>{rule.name}</strong><span>{presentEventType(rule.eventTypeId, customEventTypes).label}</span><small>{rule.effectKind === 'PENALTY' ? `Penalty ${Math.abs(rule.points)}` : `Award ${Math.abs(rule.points)}`} · {rule.repeatPolicy}</small></HydraButton>)}{bindings.rules.length===0&&<p>No evidence rule has been created yet.</p>}</section><p className="verified-evidence-note">Prefer verified events such as <strong>validation violation resolved</strong>. Do not reward merely pressing a button or applying a quick fix before the resulting model state has been verified.</p></div> },
        { id:'visual', title:'Architecture', description:'Review relationships in the existing diagram and forms.', checks:[
            { id:'model-valid', label:'HYDRA gamification references are structurally valid', complete:gamificationBlockingError === '', detail:gamificationBlockingError === '' ? 'No blocking reference error found.' : gamificationBlockingError },
            { id:'diagram-available', label:'Visual and advanced editors remain available', complete:true, detail:'The Diagram, Form, Structure tree and Source tabs edit the same HYDRA documents.' }
        ], content:<div className="gamification-guided-block gamification-guided-visual"><p>Use the existing diagram when you want to inspect or create relationships visually. Guided mode does not replace the visual editor.</p><div><HydraButton type="button" onClick={() => selectView('DIAGRAM')}>Open gamification diagram</HydraButton><HydraButton type="button" onClick={() => selectView('FORM')}>Open full forms</HydraButton><HydraButton type="button" onClick={() => selectView('STRUCTURE')}>Open structure tree</HydraButton></div></div> },
        { id:'review', title:'Review', description:'Verify the complete configuration before saving or using the runtime.', checks:[
            { id:'document-ready', label:'Catalog and point scales are ready', complete:definitions.name.trim() !== '' && pointDefinitionsValid, detail:`${definitions.pointTypes.length} point scale(s) configured.` },
            { id:'rules-ready', label:'At least one valid scoring or penalty rule is ready', complete:evidenceRulesValid, detail:`${bindings.rules.length} evidence rule(s) configured.` },
            { id:'global-check', label:'No blocking HYDRA reference error remains', complete:gamificationBlockingError === '', detail:gamificationBlockingError === '' ? 'Run Check for deeper diagnostics before saving.' : gamificationBlockingError }
        ], content:<div className="hydra-guided-review-summary"><strong>{definitions.name || 'Unnamed gamification'}</strong><span>{definitions.id}</span><dl><div><dt>Point types</dt><dd>{definitions.pointTypes.length}</dd></div><div><dt>Levels</dt><dd>{definitions.levels.length}</dd></div><div><dt>Achievements</dt><dd>{definitions.achievements.length}</dd></div><div><dt>Challenges</dt><dd>{definitions.challenges.length}</dd></div><div><dt>Rewards</dt><dd>{definitions.rewards.length}</dd></div><div><dt>Evidence rules</dt><dd>{bindings.rules.length}</dd></div></dl></div> }
    ];

    function transferGamification(request: LayerImportRequest<GamificationInterchangeFormat>) {
        const imported = importGamificationLayer(request.content, request.format, { definitions, bindings });
        const nextDefinitions: GamificationDefinitionModel = request.strategy === 'REPLACE'
            ? { ...imported.definitions, id: definitions.id, metamodelId, annotations: { ...imported.definitions.annotations, 'hydra.interchange.format': request.format } }
            : {
                ...definitions,
                pointTypes: mergeGamificationItems(definitions.pointTypes, imported.definitions.pointTypes),
                levels: mergeGamificationItems(definitions.levels, imported.definitions.levels),
                achievements: mergeGamificationItems(definitions.achievements, imported.definitions.achievements),
                challenges: mergeGamificationItems(definitions.challenges, imported.definitions.challenges),
                rewards: mergeGamificationItems(definitions.rewards, imported.definitions.rewards),
                annotations: { ...definitions.annotations, ...imported.definitions.annotations, 'hydra.interchange.format': request.format }
            };
        const nextBindings: GamificationBindingModel = request.strategy === 'REPLACE'
            ? { ...imported.bindings, id: bindings.id, metamodelId, definitionModelId: nextDefinitions.id, annotations: { ...imported.bindings.annotations, 'hydra.interchange.format': request.format } }
            : {
                ...bindings,
                definitionModelId: nextDefinitions.id,
                rules: mergeGamificationItems(bindings.rules, imported.bindings.rules),
                annotations: { ...bindings.annotations, ...imported.bindings.annotations, 'hydra.interchange.format': request.format }
            };
        remember();
        setDefinitions(nextDefinitions);
        setBindings(nextBindings);
        setDiagram(current => synchronizeGamificationDiagram(nextDefinitions, nextBindings, runtime, current));
        const nextSelection = firstSelection(nextDefinitions, nextBindings);
        setSelection(nextSelection);
        setSelectedDiagramNodeId(nextSelection === null ? '' : gamificationNodeId(nextSelection.kind, nextSelection.id));
        setSelectedDiagramEdgeId('');
        setSource(JSON.stringify({ definitions: nextDefinitions, bindings: nextBindings }, null, 2));
        setDirty(true);
        onMessage(`Imported gamification layer from ${request.fileName}`);
        return {
            summary: `Imported gamification layer`,
            details: [
                `${nextDefinitions.pointTypes.length} point type(s), ${nextDefinitions.levels.length} level(s), ${nextDefinitions.achievements.length} achievement(s)`,
                `${nextDefinitions.challenges.length} challenge(s), ${nextDefinitions.rewards.length} reward(s), ${nextBindings.rules.length} evidence rule(s)`,
                `Strategy: ${request.strategy === 'MERGE' ? 'merge/upsert by stable ID' : 'replace gamification layer'}`
            ],
            warnings: imported.warnings,
            metrics: [
                { id: 'definitions', label: 'Definitions imported', value: nextDefinitions.pointTypes.length + nextDefinitions.levels.length + nextDefinitions.achievements.length + nextDefinitions.challenges.length + nextDefinitions.rewards.length, tone: 'GOOD' as const },
                { id: 'evidence', label: 'Evidence rules imported', value: nextBindings.rules.length, tone: 'NEUTRAL' as const },
                { id: 'invalid', label: 'Erroneous evidence rules', value: nextBindings.rules.filter(rule => rule.id.trim() === '' || rule.name.trim() === '' || rule.eventTypeId.trim() === '').length, tone: nextBindings.rules.some(rule => rule.id.trim() === '' || rule.name.trim() === '' || rule.eventTypeId.trim() === '') ? 'ERROR' as const : 'GOOD' as const },
                { id: 'warnings', label: 'Warnings', value: imported.warnings.length, tone: imported.warnings.length > 0 ? 'WARNING' as const : 'GOOD' as const }
            ]
        };
    }

    return (
        <section className="gamification-designer">
            <DesignerCommandBar className="gamification-header hydra-unified-workbench-toolbar" title={t('Optional gamification')} description={t('Consumes verified HYDRA events. It never changes flow execution semantics.')} actions={<><DesignerCommandGroup label={t('Interchange')}><HydraButton type="button" onClick={() => setLayerIoMode('IMPORT')}>{t('Import gamification')}</HydraButton><HydraButton type="button" onClick={() => setLayerIoMode('EXPORT')}>{t('Export gamification')}</HydraButton></DesignerCommandGroup><DesignerCommandGroup label={t('Quality')}><HydraButton type="button" onClick={() => void validate()}>{t('Check')}</HydraButton></DesignerCommandGroup><DesignerCommandGroup label={t('Changes')}><HydraButton type="button" disabled={!dirty} onClick={() => void save()}>{t('Save gamification')}</HydraButton></DesignerCommandGroup></>} viewsLabel={t('Workspace')} viewsSummary={view} views={<nav className="event-subtabs"><HydraButton className={view === 'GUIDED' ? 'active' : ''} type="button" onClick={() => selectView('GUIDED')}>{t('Guided')}</HydraButton><HydraButton className={view === 'FORM' ? 'active' : ''} type="button" onClick={() => selectView('FORM')}>{t('Form')}</HydraButton>{structureTabOpen ? <span className="designer-structure-tab-entry"><HydraButton className={view === 'STRUCTURE' ? 'active' : ''} type="button" onClick={() => selectView('STRUCTURE')}>{t('Structure tree')}</HydraButton><HydraButton className="designer-structure-tab-close" type="button" aria-label={t('Close gamification structure tree tab')} onClick={() => { setStructureTabOpen(false); if (view === 'STRUCTURE') selectView('FORM'); }}>×</HydraButton></span> : <HydraButton type="button" onClick={() => { setStructureTabOpen(true); selectView('STRUCTURE'); }}>+ {t('Structure tree')}</HydraButton>}<HydraButton className={view === 'DIAGRAM' ? 'active' : ''} type="button" onClick={() => selectView('DIAGRAM')}>{t('Diagram')}</HydraButton><HydraButton className={view === 'SOURCE' ? 'active' : ''} type="button" onClick={() => selectView('SOURCE')}>{t('Source')}</HydraButton><HydraButton className={view === 'LEDGER' ? 'active' : ''} type="button" onClick={() => { setView('LEDGER'); void refreshRuntime(); }}>{t('Runtime & ledger')}</HydraButton></nav>} />
            {diagnostics.length > 0 && <div className="gamification-diagnostic-summary"><HydraDiagnosticList diagnostics={diagnostics} context="gamification" title={t('Gamification diagnostics')} onSelectElement={elementId => {
                const hit = firstSelectionById(definitions, bindings, elementId); if (hit !== null) { setSelection(hit); setSelectedDiagramNodeId(gamificationNodeId(hit.kind, hit.id)); setSelectedDiagramEdgeId(''); selectView('DIAGRAM'); }
            }} /></div>}
            {layerIoMode !== null && <LayerInterchangeDialog
                mode={layerIoMode}
                layerName="Gamification"
                description="Import or export scoring, penalties, levels, achievements, challenges, rewards and evidence rules independently from the metamodel and EventFlow. Blockly packages register this layer without replacing the block catalogue."
                formats={GAMIFICATION_INTERCHANGE_FORMATS}
                defaultFormat={(definitions.annotations['hydra.interchange.format'] as GamificationInterchangeFormat | undefined) ?? 'BLOCKLY_JAVASCRIPT'}
                currentMetamodel={semanticMetamodel ?? metamodel}
                onClose={() => setLayerIoMode(null)}
                onAnalyzeImport={request => analyzeGamificationImport(request.content, request.format, { definitions, bindings })}
                onImport={transferGamification}
                onExport={format => exportGamificationLayer(format, definitions, bindings)}
            />}
            {view === 'GUIDED' && <div className="gamification-guided-workspace"><HydraGuidedWorkflow key={`${definitions.id}:${bindings.id}`} title="Guided gamification" description="Create scoring, progression and verified evidence rules step by step. Advanced forms, diagrams, structure and source remain available at all times." steps={guidedSteps} currentStepId={guidedStep} onStepChange={setGuidedStep} finishLabel="Save gamification" onFinish={() => save()} /></div>}
            {view === 'FORM' && (
                <div className="hydra-designer-master-detail gamification-layout" style={sidebar.layoutStyle}>
                    <aside className="hydra-designer-sidebar">
                        <div className="hydra-sidebar-heading">
                            <div><h3>Gamification elements</h3><small>{totalItems(definitions, bindings)} definition(s)</small></div>
                            <div className="hydra-sidebar-heading-actions"><DesignerGuideButton kind="GAMIFICATION" /></div>
                        </div>
                        <div className="hydra-sidebar-filter"><input aria-label="Search gamification elements" placeholder="Search gamification..." value={filter} onChange={event => setFilter(event.target.value)} /></div>
                        <div className="hydra-sidebar-scroll">
                            {KINDS.map(kind => {
                                const values = items(kind, definitions, bindings).filter(item => [item.name, item.id].some(value => value.toLowerCase().includes(normalizedFilter)));
                                return <section className="hydra-sidebar-section" key={kind}>
                                    <div className="hydra-sidebar-section-title"><span className="gamification-kind-heading"><strong>{t(GAMIFICATION_KIND_LABELS[kind])}</strong><ResizableInfoTooltip label={`Information about ${singularLabel(kind)}`} title={singularLabel(kind)}><div className="gamification-kind-guidance"><p><strong>{t('Purpose')}</strong> {t(GAMIFICATION_KIND_GUIDANCE[kind].purpose)}</p><p><strong>{t('Requirements to create it')}</strong> {t(GAMIFICATION_KIND_GUIDANCE[kind].creationRequirements)}</p><p><strong>{t('Dependencies')}</strong> {t(GAMIFICATION_KIND_GUIDANCE[kind].dependencies)}</p><p><strong>{t('How it is used at runtime')}</strong> {t(GAMIFICATION_KIND_GUIDANCE[kind].runtimeUse)}</p>{kind === 'LEVEL' && <p className={definitions.pointTypes.length === 0 ? 'gamification-dependency-blocked' : 'gamification-dependency-ready'}><strong>{t('Current status')}</strong> {t(definitions.pointTypes.length === 0 ? 'Blocked: create at least one point type first.' : 'Ready: at least one point type is available.')}</p>}</div></ResizableInfoTooltip></span><HydraButton type="button" disabled={kind === 'LEVEL' && definitions.pointTypes.length === 0} onClick={() => add(kind)}>+ {t(singularLabel(kind))}</HydraButton></div>
                                    {values.map(item => <HydraButton className={`hydra-designer-list-item${selection?.kind === kind && selection.id === item.id ? ' selected' : ''}`} type="button" key={item.id} onClick={() => setSelection({ kind, id: item.id })}><span><strong>{item.name}</strong><small>{item.id}</small></span></HydraButton>)}
                                    {values.length === 0 && <p className="gamification-sidebar-empty">{t(emptyGamificationKindLabel(kind))}</p>}
                                </section>;
                            })}
                        </div>
                    </aside>
                    <div {...sidebar.separatorProps} />
                    <main className="hydra-designer-form">
                        <section className="gamification-document-settings">
                            <header><div><h3>Gamification document</h3><p>Shared catalog and evidence-rule configuration.</p></div></header>
                            <div className="hydra-designer-fields">
                                <DesignerHelpedField label="Name" targetId="gamification-document-name" required help="Visible name for this optional gamification configuration." example="Model quality gamification"><input id="gamification-document-name" value={definitions.name} onChange={event => changeDefinitions({ ...definitions, name: event.target.value })} /></DesignerHelpedField>
                                <DesignerHelpedField label="Definition model ID" targetId="gamification-document-id" required help="Stable identifier of the gamification catalog document." example="people-gamification"><input id="gamification-document-id" value={definitions.id} readOnly /></DesignerHelpedField>
                            </div>
                            <GamificationFeedbackPolicyEditor definitions={definitions} onChange={changeDefinitions} />
                        </section>
                        {selection === null && <div className="hydra-empty-state"><strong>No gamification element selected</strong><span>Create or select a point type, level, achievement, challenge, reward or evidence rule.</span></div>}
                        {selection !== null && selectedItem !== null && (
                            <>
                                <GamificationElementForm
                                    selection={selection}
                                    item={selectedItem}
                                    definitions={definitions}
                                    eventTypeIds={availableEventTypeIds}
                                    customEventTypes={customEventTypes}
                                    onDefinitionsChange={changeDefinitions}
                                    onRuleChange={updateRule}
                                    onRenameId={renameSelectionId}
                                    onDelete={removeSelection}
                                />
                                <GamificationConnectionSummary nodeId={gamificationNodeId(selection.kind, selection.id)} nodes={visual.nodes} edges={visual.edges} onSelectRelation={selectGamificationRelation} />
                            </>
                        )}
                    </main>
                </div>
            )}
            {view === 'STRUCTURE' && structureTabOpen && <section className="designer-structure-workspace">
                <header><div><h3>Gamification structure</h3><p>Evidence rules, point scales, levels, achievements, challenges, rewards and their model relationships.</p></div><div className="designer-structure-header-actions"><HydraButton type="button" onClick={() => setStructureCollapsed(value => !value)}>{structureCollapsed ? 'Expand tree' : 'Collapse tree'}</HydraButton></div></header>
                {!structureCollapsed && <div className="designer-structure-layout designer-structure-triple-layout">
                    <div className="designer-structure-tree-pane designer-semantic-tree-pane">
                        <header className="designer-structure-pane-heading"><div><h4>Metamodel</h4><p>Semantic classes, features, operations and relations.</p></div></header>
                        <div className="designer-structure-tree-toolbar"><input aria-label="Search gamification metamodel tree" placeholder="Search metamodel..." value={semanticFilter} onChange={event => setSemanticFilter(event.target.value)} /></div>
                        <div className="designer-structure-tree-scroll"><MetamodelStructureTree metamodel={semanticMetamodel} filter={semanticFilter} selection={semanticSelection} onSelect={next => { setSelection(null); setSelectedDiagramNodeId(''); setSelectedDiagramEdgeId(''); setSemanticSelection(next); setStructurePropertiesOpen(true); setStructurePropertiesCollapsed(false); }} /></div>
                    </div>
                    <div className="designer-structure-tree-pane designer-defined-tree-pane">
                        <header className="designer-structure-pane-heading"><div><h4>Defined gamification structure</h4><p>Rules, progression elements, rewards and their HYDRA links.</p></div></header>
                        <div className="designer-structure-tree-toolbar"><input aria-label="Search gamification structure tree" placeholder="Search rules, progression and rewards..." value={structureFilter} onChange={event => setStructureFilter(event.target.value)} /></div>
                        <div className="designer-structure-tree-scroll"><GamificationStructureTree definitions={definitions} bindings={bindings} selection={selection} filter={structureFilter} onSelect={selectStructureItem} /></div>
                    </div>
                    {structurePropertiesOpen ? <aside className={`designer-structure-properties${structurePropertiesCollapsed ? ' collapsed' : ''}`} aria-label="Gamification structure properties"><header><h4>Properties</h4><div><HydraButton type="button" onClick={() => setStructurePropertiesCollapsed(value => !value)}>{structurePropertiesCollapsed ? 'Expand properties' : 'Collapse properties'}</HydraButton><HydraButton type="button" aria-label="Close gamification structure properties" className="hydra-form-close-button" onClick={() => setStructurePropertiesOpen(false)}>×</HydraButton></div></header>{!structurePropertiesCollapsed && <div className="designer-structure-properties-body">{semanticSelection !== null && semanticMetamodel !== null ? <MetamodelSelectionProperties selection={semanticSelection} metamodel={semanticMetamodel} /> : diagramInspector()}</div>}</aside> : <HydraButton className="editor-structure-properties-reopen" type="button" onClick={() => setStructurePropertiesOpen(true)}>+ Properties</HydraButton>}
                </div>}
            </section>}
            {view === 'DIAGRAM' && <GamificationVisualEditor
                nodes={visual.nodes}
                edges={visual.edges}
                inspector={diagramInspector()}
                canUndo={canUndo}
                canRedo={canRedo}
                onNodesChange={onVisualNodesChange}
                onEdgesChange={onVisualEdgesChange}
                onConnect={connectVisual}
                onSelectNode={selectVisualNode}
                onSelectEdge={selectVisualEdge}
                onAdd={(kind: GamificationPaletteKind, position) => add(kind, position, true)}
                evidenceCatalog={evidenceCatalog}
                onAddEvidence={addEvidenceToDiagram}
                onDelete={deleteVisualSelection}
                onDeleteSelection={deleteVisualItems}
                onDuplicate={duplicateVisualSelection}
                onAutoLayout={autoLayoutVisual}
                onElkLayout={elkLayoutVisual}
                onUndo={undo}
                onRedo={redo}
                onNodeDragStart={beginVisualDrag}
                onNodeDragStop={endVisualDrag}
            />}
            {view === 'SOURCE' && <div className="gamification-source"><textarea aria-label="Gamification JSON source" value={source} onChange={event => setSource(event.target.value)} /><HydraButton type="button" onClick={applySource}>Apply source</HydraButton></div>}
            {overwriteDialogs}
            {view === 'LEDGER' && <GamificationLedgerView runtime={runtime} feedback={feedback} onRefresh={refreshRuntime} onReset={resetRuntime} />}
        </section>
    );
}
