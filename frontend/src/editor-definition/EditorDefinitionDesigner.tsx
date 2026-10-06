import { HydraButton } from '../design-system/HydraButton';
import { useEffect, useEffectEvent, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import { DesignerCommandBar, DesignerCommandGroup } from '../designer-ui/DesignerCommandBar';
import { DesignerGuideButton } from '../designer-ui/DesignerFieldHelp';
import { HydraAdaptiveSelect } from '../components/HydraAdaptiveSelect';
import { useResizableDesignerSidebar } from '../designer-ui/useResizableDesignerSidebar';
import { HydraGuidedWorkflow } from '../design-system';
import type { MetaModel } from '../metamodel-designer/types';
import { compileEditorRuntime, createEditorDefinition, listEditorDefinitions, loadEditorDefinition, replaceEditorDefinition } from './api';
import { createExpressionModel, loadExpressionModel, replaceExpressionModel } from '../validation/api';
import { loadActionModel } from '../validation/actionApi';
import { isMissingValidationResource } from '../validation/http';
import { createEmptyActionModel, createEmptyExpressionModel } from '../validation-designer/validationDesignerFactory';
import type { ActionModel, ExpressionModel } from '../validation/types';
import { createAdditionalEditorDefinition, createDefaultRepresentation, createEditorDefinitionId, createEmptyEditorDefinition } from './editorDefinitionFactory';
import { findEditorDefinitionBlockingError } from './editorDefinitionValidation';
import { buildEditorDefinitionGuidedSteps } from './EditorDefinitionGuidedSteps';
import { EditorRuntimePreview } from './EditorRuntimePreview';
import { EditorDefinitionTree } from './EditorDefinitionTree';
import { ElementRepresentationDesigner } from './ElementRepresentationDesigner';
import { CodeGenerationDesigner } from './CodeGenerationDesigner';
import { EditorDefinitionRemoteStorage } from './EditorDefinitionRemoteStorage';
import type { EditorDefinitionTreeInspection } from './EditorDefinitionTree';
import {
    EdgeMappingForm,
    EditorDocumentForm,
    FormEditor,
    NodeMappingForm,
    PaletteGroupForm,
    RepresentationForm,
    StructureInspectionSummary,
    ToolForm
} from './EditorDefinitionDesignerParts';
import { normalizeEditorDefinition } from './editorDefinitionNormalization';
import { detectMetamodelRoots } from './metamodelRootDetection';
import type { EditorCapability, EditorDefinitionCompilationResult, EditorDefinitionModel, EditorDefinitionSelection, EdgeMapping, EditorTool, FormDefinition, NodeMapping, PaletteGroup, RepresentationDefinition } from './types';
import type { PaletteActionKind } from './EditorPaletteToolForm';
import { createPaletteActionToolDraft } from './editorPaletteActionFactory';
import { addClassMappingAndTool, addRelationMappingAndTool } from './editorDefinitionMappingMutations';
import './editor-definition.css';
import './editor-definition-progressive.css';
import { useHydraOverwriteConfirmation } from '../hooks/useHydraOverwriteConfirmation';

interface Props {
    metamodel: MetaModel;
    persisted: boolean;
    onError: (message: string) => void;
    onMessage: (message: string) => void;
}

const CAPABILITIES: EditorCapability[] = [
    'MODEL_EDITING', 'MULTIPLE_REPRESENTATIONS', 'OPERATIONS', 'VALIDATION_RUNTIME', 'QUICK_FIX_RUNTIME',
    'EVENTS', 'EVENT_FLOWS', 'GAMIFICATION', 'IMPORT', 'EXPORT', 'CODE_GENERATION', 'UNDO_REDO'
];


export function EditorDefinitionDesigner(props: Props) {
    return <EditorDefinitionDesignerSession key={`${props.metamodel.id}:${props.persisted}`} {...props} />;
}

function EditorDefinitionDesignerSession({ metamodel, persisted, onError, onMessage }: Props) {
    const sidebar = useResizableDesignerSidebar({ storageKey: 'hydra:editor-definition:sidebar-width', ariaLabel: 'Resize editor definition palette', initialWidth: 300, minimumWidth: 250, maximumWidth: 460 });
    const structureTree = useResizableDesignerSidebar({ storageKey: 'hydra:editor-definition:structure-tree-width', ariaLabel: 'Resize structure tree and properties', initialWidth: 350, minimumWidth: 260, maximumWidth: 520, cssVariableName: '--editor-structure-tree-width' });

    const initialModel = useMemo(() => createEmptyEditorDefinition(metamodel), [metamodel]);
    const initialRepresentation = initialModel.representations[0];
    const [model, setModel] = useState<EditorDefinitionModel>(initialModel);
    const [etag, setEtag] = useState('');
    const [selectedRepresentationId, setSelectedRepresentationId] = useState(initialRepresentation?.id ?? '');
    const [selection, setSelection] = useState<EditorDefinitionSelection>(initialRepresentation === undefined ? null : { kind: 'REPRESENTATION', id: initialRepresentation.id });
    const [filter, setFilter] = useState('');
    const [dirty, setDirty] = useState(false);
    const [availableEditors, setAvailableEditors] = useState<Array<{ id: string; name: string }>>([]);
    const { confirmOverwrite, overwriteDialogs } = useHydraOverwriteConfirmation({
        current: [{ layer: 'Model editor', entityKind: 'EDITOR_DEFINITION', stableId: model.id, uniqueName: model.name, displayLabel: model.name || model.id }],
        persisted: availableEditors.map(item => ({ layer: 'Model editor', entityKind: 'EDITOR_DEFINITION', stableId: item.id, uniqueName: item.name, displayLabel: item.name || item.id }))
    });
    const [designerView, setDesignerView] = useState<'GUIDED' | 'ELEMENTS' | 'DESIGN' | 'STRUCTURE' | 'CODE' | 'RUNTIME'>('DESIGN');
    const [guidedStep, setGuidedStep] = useState('identity');
    const [treeDetailLevel, setTreeDetailLevel] = useState<'ESSENTIAL' | 'FULL'>('ESSENTIAL');
    const [documentSettingsOpen, setDocumentSettingsOpen] = useState(false);
    const [structureTabOpen, setStructureTabOpen] = useState(true);
    const [structureCollapsed, setStructureCollapsed] = useState(false);
    const [structurePropertiesOpen, setStructurePropertiesOpen] = useState(true);
    const [structurePropertiesCollapsed, setStructurePropertiesCollapsed] = useState(false);
    const [structureInspection, setStructureInspection] = useState<EditorDefinitionTreeInspection | null>(null);
    const [runtimeResult, setRuntimeResult] = useState<EditorDefinitionCompilationResult | null>(null);
    const [compilingRuntime, setCompilingRuntime] = useState(false);
    const [expressionModel, setExpressionModel] = useState<ExpressionModel>(() => createEmptyExpressionModel(metamodel.id));
    const [expressionEtag, setExpressionEtag] = useState('');
    const [expressionDirty, setExpressionDirty] = useState(false);
    const [actionModel, setActionModel] = useState<ActionModel>(() => createEmptyActionModel(metamodel.id, metamodel.id));

    const loadPersistedEditor = useEffectEvent(async (isActive: () => boolean) => {
        try {
            const [items, expressions, actions] = await Promise.all([
                listEditorDefinitions(metamodel.id),
                loadExpressionModel(metamodel.id).catch(error => {
                    if (isMissingValidationResource(error)) return { expressionModel:createEmptyExpressionModel(metamodel.id), etag:'' };
                    throw error;
                }),
                loadActionModel(metamodel.id).catch(error => {
                    if (isMissingValidationResource(error)) return { actionModel:createEmptyActionModel(metamodel.id, metamodel.id), etag:'' };
                    throw error;
                })
            ]);
            if (!isActive()) return;
            setAvailableEditors(items);
            setExpressionModel(expressions.expressionModel);
            setExpressionEtag(expressions.etag);
            setActionModel(actions.actionModel);
            setExpressionDirty(false);
            const first = items[0];
            if (first === undefined) return;
            const result = await loadEditorDefinition(metamodel.id, first.id);
            if (isActive()) setLoadedModel(result.model, result.etag);
        } catch (error) {
            if (isActive()) onError(error instanceof Error ? error.message : 'Could not load editor definitions');
        }
    });

    useEffect(() => {
        if (!persisted) return undefined;
        let active = true;
        void Promise.resolve().then(() => loadPersistedEditor(() => active));
        return () => { active = false; };
    }, [persisted]);

    const selectedRepresentation = model.representations.find(item => item.id === selectedRepresentationId) ?? null;
    const selectedNodeMapping = selection?.kind === 'NODE_MAPPING'
        ? selectedRepresentation?.nodeMappings.find(item => item.id === selection.id) ?? null
        : null;
    const selectedEdgeMapping = selection?.kind === 'EDGE_MAPPING'
        ? selectedRepresentation?.edgeMappings.find(item => item.id === selection.id) ?? null
        : null;
    const selectedPaletteGroup = selection?.kind === 'PALETTE_GROUP' ? model.palette.groups.find(item => item.id === selection.id) ?? null : null;
    const selectedTool = selection?.kind === 'TOOL' ? model.palette.tools.find(item => item.id === selection.id) ?? null : null;
    const selectedForm = selection?.kind === 'FORM' ? model.forms.find(item => item.id === selection.id) ?? null : null;
    const concreteClasses = metamodel.classes.filter(metaClass => metaClass.kind === 'CLASS');
    const detectedRoots = useMemo(() => detectMetamodelRoots(metamodel), [metamodel]);
    const currentEditorSummaries = useMemo(() => {
        const current = { id: model.id, name: model.name };
        return [current, ...availableEditors.filter(item => item.id !== model.id)].filter((item, index, values) => values.findIndex(candidate => candidate.id === item.id) === index);
    }, [availableEditors, model.id, model.name]);

    function setLoadedModel(next: EditorDefinitionModel, nextEtag: string): void {
        const normalized = normalizeEditorDefinition(next, metamodel);
        const firstRepresentation = normalized.representations[0];
        setModel(normalized);
        setEtag(nextEtag);
        setSelectedRepresentationId(firstRepresentation?.id ?? '');
        setSelection(firstRepresentation === undefined ? null : { kind: 'REPRESENTATION', id: firstRepresentation.id });
        setDirty(false);
        setRuntimeResult(null);
        setDesignerView('DESIGN');
    }

    function changeModel(next: EditorDefinitionModel): void {
        setModel(next);
        setRuntimeResult(null);
        setDirty(true);
    }

    function changeExpressionModel(next: ExpressionModel): void {
        setExpressionModel(next);
        setRuntimeResult(null);
        setExpressionDirty(true);
    }

    function updateRepresentation(value: RepresentationDefinition): void {
        changeModel({ ...model, representations: model.representations.map(item => item.id === value.id ? value : item) });
    }

    function renameRepresentationId(previousId: string, nextId: string): void {
        if (previousId === nextId) return;
        changeModel({
            ...model,
            representations: model.representations.map(item => item.id === previousId ? { ...item, id: nextId } : item),
            palette: {
                ...model.palette,
                tools: model.palette.tools.map(tool => ({ ...tool, representationIds: tool.representationIds.map(id => id === previousId ? nextId : id) }))
            }
        });
        setSelectedRepresentationId(nextId);
        setSelection({ kind: 'REPRESENTATION', id: nextId });
    }

    function newEditor(): void {
        const empty = createAdditionalEditorDefinition(metamodel);
        setLoadedModel(empty, '');
        setDocumentSettingsOpen(true);
        setDirty(true);
    }

    function addRepresentation(): void {
        const detectedRoot = detectMetamodelRoots(metamodel).primaryClassId;
        const representation = createDefaultRepresentation(detectedRoot === '' ? [] : [detectedRoot]);
        changeModel({ ...model, representations: [...model.representations, representation] });
        setSelectedRepresentationId(representation.id);
        setSelection({ kind: 'REPRESENTATION', id: representation.id });
    }

    function deleteSelectedRepresentation(): void {
        if (selectedRepresentation === null || model.representations.length === 1) return;
        const removedId = selectedRepresentation.id;
        const remainingRepresentations = model.representations.filter(item => item.id !== removedId);
        const updatedTools = model.palette.tools
            .map(tool => ({ ...tool, representationIds: tool.representationIds.filter(id => id !== removedId) }))
            .filter(tool => tool.representationIds.length > 0);
        const remainingToolIds = new Set(updatedTools.map(tool => tool.id));
        changeModel({
            ...model,
            representations: remainingRepresentations.map(representation => ({ ...representation, toolIds: representation.toolIds.filter(id => remainingToolIds.has(id)) })),
            palette: {
                groups: model.palette.groups.map(group => ({ ...group, toolIds: group.toolIds.filter(id => remainingToolIds.has(id)) })),
                tools: updatedTools
            }
        });
        const next = remainingRepresentations[0];
        setSelectedRepresentationId(next?.id ?? '');
        setSelection(next === undefined ? null : { kind: 'REPRESENTATION', id: next.id });
    }

    function addClassTool(classId: string, _className: string, representationId = selectedRepresentation?.id ?? ''): void {
        if (representationId === '') return;
        const result = addClassMappingAndTool(model, metamodel, representationId, classId, selectedPaletteGroup?.id ?? '');
        if (result.mappingId === '') return;
        setSelectedRepresentationId(representationId);
        setStructureInspection(null);
        changeModel(result.model);
        setSelection({ kind: 'NODE_MAPPING', id: result.mappingId });
    }

    function addRelationTool(relationId: string, representationId = selectedRepresentation?.id ?? ''): void {
        if (representationId === '') return;
        const result = addRelationMappingAndTool(model, metamodel, representationId, relationId, selectedPaletteGroup?.id ?? '');
        if (result.mappingId === '') return;
        setSelectedRepresentationId(representationId);
        setStructureInspection(null);
        changeModel(result.model);
        setSelection({ kind: 'EDGE_MAPPING', id: result.mappingId });
    }

    function openMapping(representationId: string, next: Exclude<EditorDefinitionSelection, null>): void {
        setSelectedRepresentationId(representationId); setSelection(next); setTreeDetailLevel('FULL'); setDesignerView('DESIGN');
    }

    function updateNodeMapping(mapping: NodeMapping): void {
        if (selectedRepresentation === null) return;
        updateRepresentation({ ...selectedRepresentation, nodeMappings: selectedRepresentation.nodeMappings.map(item => item.id === mapping.id ? mapping : item) });
    }

    function renameNodeMappingId(previousId: string, nextId: string): void {
        if (selectedRepresentation === null || previousId === nextId) return;
        updateRepresentation({ ...selectedRepresentation, nodeMappings: selectedRepresentation.nodeMappings.map(item => item.id === previousId ? { ...item, id: nextId } : item) });
        setSelection({ kind: 'NODE_MAPPING', id: nextId });
    }

    function deleteNodeMapping(): void {
        if (selectedRepresentation === null || selectedNodeMapping === null) return;
        updateRepresentation({ ...selectedRepresentation, nodeMappings: selectedRepresentation.nodeMappings.filter(item => item.id !== selectedNodeMapping.id) });
        setSelection({ kind: 'REPRESENTATION', id: selectedRepresentation.id });
    }

    function updateEdgeMapping(mapping: EdgeMapping): void {
        if (selectedRepresentation === null) return;
        updateRepresentation({ ...selectedRepresentation, edgeMappings: selectedRepresentation.edgeMappings.map(item => item.id === mapping.id ? mapping : item) });
    }

    function renameEdgeMappingId(previousId: string, nextId: string): void {
        if (selectedRepresentation === null || previousId === nextId) return;
        changeModel({
            ...model,
            representations: model.representations.map(rep => rep.id === selectedRepresentation.id
                ? { ...rep, edgeMappings: rep.edgeMappings.map(item => item.id === previousId ? { ...item, id: nextId } : item) }
                : rep),
            palette: { ...model.palette, tools: model.palette.tools.map(tool => tool.kind === 'CREATE_EDGE' && tool.edgeMappingId === previousId ? { ...tool, edgeMappingId: nextId } : tool) }
        });
        setSelection({ kind: 'EDGE_MAPPING', id: nextId });
    }

    function deleteEdgeMapping(): void {
        if (selectedRepresentation === null || selectedEdgeMapping === null) return;
        const removedToolIds = new Set(model.palette.tools.filter(tool => tool.kind === 'CREATE_EDGE' && tool.edgeMappingId === selectedEdgeMapping.id).map(tool => tool.id));
        changeModel({
            ...model,
            representations: model.representations.map(rep => rep.id === selectedRepresentation.id
                ? { ...rep, edgeMappings: rep.edgeMappings.filter(item => item.id !== selectedEdgeMapping.id), toolIds: rep.toolIds.filter(id => !removedToolIds.has(id)) }
                : rep),
            palette: {
                tools: model.palette.tools.filter(tool => !removedToolIds.has(tool.id)),
                groups: model.palette.groups.map(group => ({ ...group, toolIds: group.toolIds.filter(id => !removedToolIds.has(id)) }))
            }
        });
        setSelection({ kind: 'REPRESENTATION', id: selectedRepresentation.id });
    }

    function updateTool(tool: EditorTool): void {
        changeModel({ ...model, palette: { ...model.palette, tools: model.palette.tools.map(item => item.id === tool.id ? tool : item) } });
    }

    function renameToolId(previousId: string, nextId: string): void {
        if (previousId === nextId) return;
        changeModel({
            ...model,
            palette: {
                tools: model.palette.tools.map(item => item.id === previousId ? { ...item, id: nextId } : item),
                groups: model.palette.groups.map(group => ({ ...group, toolIds: group.toolIds.map(id => id === previousId ? nextId : id) }))
            },
            representations: model.representations.map(rep => ({ ...rep, toolIds: rep.toolIds.map(id => id === previousId ? nextId : id) }))
        });
        setSelection({ kind: 'TOOL', id: nextId });
    }

    function deleteTool(): void {
        if (selectedTool === null) return;
        const id = selectedTool.id;
        changeModel({
            ...model,
            palette: {
                tools: model.palette.tools.filter(tool => tool.id !== id),
                groups: model.palette.groups.map(group => ({ ...group, toolIds: group.toolIds.filter(toolId => toolId !== id) }))
            },
            representations: model.representations.map(rep => ({ ...rep, toolIds: rep.toolIds.filter(toolId => toolId !== id) }))
        });
        setSelection(selectedRepresentation === null ? null : { kind: 'REPRESENTATION', id: selectedRepresentation.id });
    }

    function handleTreeSelection(next: Exclude<EditorDefinitionSelection, null>, representationId?: string): void {
        if (representationId !== undefined) setSelectedRepresentationId(representationId);
        setSelection(next);
    }

    function inspectStructure(item: EditorDefinitionTreeInspection): void {
        setStructureInspection(item);
        setStructurePropertiesOpen(true);
        setStructurePropertiesCollapsed(false);
        if (item.selection === undefined) setSelection(null);
    }

    function selectedElementProperties(includeDocumentWhenEmpty = false): ReactNode {
        return <>
            {includeDocumentWhenEmpty && selection === null && structureInspection === null && <EditorDocumentForm model={model} etag={etag} capabilities={CAPABILITIES} onChange={changeModel} />}
            {selection === null && structureInspection !== null && structureInspection.selection === undefined && <StructureInspectionSummary item={structureInspection} />}
            {selection === null && structureInspection === null && !includeDocumentWhenEmpty && <div className="hydra-empty-state"><strong>No editor element selected</strong><span>Select a representation, mapping, palette group, tool or form from the tree.</span></div>}
            {selection?.kind === 'REPRESENTATION' && selectedRepresentation !== null && <RepresentationForm representation={selectedRepresentation} metamodel={metamodel} model={model} detectedRoots={detectedRoots} onChange={updateRepresentation} onRenameId={renameRepresentationId} onAddClassTool={addClassTool} onAddRelationTool={addRelationTool} onEditNodeMapping={mappingId => openMapping(selectedRepresentation.id, { kind: 'NODE_MAPPING', id: mappingId })} onEditEdgeMapping={mappingId => openMapping(selectedRepresentation.id, { kind: 'EDGE_MAPPING', id: mappingId })} onDelete={deleteSelectedRepresentation} />}
            {selectedNodeMapping !== null && <NodeMappingForm mapping={selectedNodeMapping} metamodel={metamodel} expressionModel={expressionModel} onChange={updateNodeMapping} onExpressionModelChange={changeExpressionModel} onRenameId={renameNodeMappingId} onDelete={deleteNodeMapping} />}
            {selectedEdgeMapping !== null && <EdgeMappingForm mapping={selectedEdgeMapping} metamodel={metamodel} expressionModel={expressionModel} onChange={updateEdgeMapping} onExpressionModelChange={setExpressionModel} onRenameId={renameEdgeMappingId} onDelete={deleteEdgeMapping} />}
            {selectedPaletteGroup !== null && <PaletteGroupForm group={selectedPaletteGroup} toolCount={selectedPaletteGroup.toolIds.length} availableOperations={metamodel.classes.reduce((total, item) => total + item.operations.length, 0)} availableActions={actionModel.actions.filter(item => item.enabled).length} onAddTool={addPaletteActionTool} onChange={updatePaletteGroup} onRenameId={renamePaletteGroupId} onDelete={deletePaletteGroup} />}
            {selectedTool !== null && <ToolForm tool={selectedTool} model={model} metamodel={metamodel} expressions={expressionModel} actions={actionModel} onChange={updateTool} onRenameId={renameToolId} onDelete={deleteTool} />}
            {selectedForm !== null && <FormEditor form={selectedForm} metamodel={metamodel} expressionModel={expressionModel} onChange={updateForm} onRenameId={renameFormId} onDelete={deleteForm} />}
        </>;
    }

    function addPaletteGroup(): void {
        const group: PaletteGroup = { id: createEditorDefinitionId('palette-group'), name: 'New tool group', order: model.palette.groups.length, toolIds: [], initiallyCollapsed: false };
        changeModel({ ...model, palette: { ...model.palette, groups: [...model.palette.groups, group] } });
        setSelection({ kind: 'PALETTE_GROUP', id: group.id });
    }

    function updatePaletteGroup(group: PaletteGroup): void {
        changeModel({ ...model, palette: { ...model.palette, groups: model.palette.groups.map(item => item.id === group.id ? group : item) } });
    }

    function addPaletteActionTool(kind: PaletteActionKind): void {
        if (selectedPaletteGroup === null) return;
        const representationId = selectedRepresentation?.id ?? model.representations[0]?.id ?? '';
        const draft = createPaletteActionToolDraft(kind, metamodel, actionModel, representationId);
        if (draft.ok === false) { onError(draft.message); return; }
        const tool = draft.tool;
        changeModel({
            ...model,
            palette: { tools: [...model.palette.tools, tool], groups: model.palette.groups.map(group => group.id === selectedPaletteGroup.id ? { ...group, toolIds: [...group.toolIds, tool.id] } : group) },
            representations: model.representations.map(rep => rep.id === representationId ? { ...rep, toolIds: rep.toolIds.includes(tool.id) ? rep.toolIds : [...rep.toolIds, tool.id] } : rep)
        });
        setSelectedRepresentationId(representationId);
        setSelection({ kind: 'TOOL', id: tool.id });
    }

    function renamePaletteGroupId(previousId: string, nextId: string): void {
        if (previousId === nextId) return;
        changeModel({ ...model, palette: { ...model.palette, groups: model.palette.groups.map(item => item.id === previousId ? { ...item, id: nextId } : item) } });
        setSelection({ kind: 'PALETTE_GROUP', id: nextId });
    }

    function deletePaletteGroup(): void {
        if (selectedPaletteGroup === null) return;
        changeModel({ ...model, palette: { ...model.palette, groups: model.palette.groups.filter(item => item.id !== selectedPaletteGroup.id) } });
        setSelection(selectedRepresentation === null ? null : { kind: 'REPRESENTATION', id: selectedRepresentation.id });
    }

    function addForm(): void {
        const target = concreteClasses[0];
        if (target === undefined) { onError('A form requires at least one concrete metamodel class.'); return; }
        const form: FormDefinition = { id: createEditorDefinitionId('form'), name: `${target.name} form`, targetClassId: target.id, sections: [] };
        changeModel({ ...model, forms: [...model.forms, form] });
        setSelection({ kind: 'FORM', id: form.id });
    }

    function updateForm(form: FormDefinition): void {
        changeModel({ ...model, forms: model.forms.map(item => item.id === form.id ? form : item) });
    }

    function renameFormId(previousId: string, nextId: string): void {
        if (previousId === nextId) return;
        changeModel({
            ...model,
            forms: model.forms.map(item => item.id === previousId ? { ...item, id: nextId } : item),
            palette: { ...model.palette, tools: model.palette.tools.map(tool => tool.kind === 'CREATE_NODE' && tool.formId === previousId ? { ...tool, formId: nextId } : tool) }
        });
        setSelection({ kind: 'FORM', id: nextId });
    }

    function deleteForm(): void {
        if (selectedForm === null) return;
        changeModel({
            ...model,
            forms: model.forms.filter(item => item.id !== selectedForm.id),
            palette: { ...model.palette, tools: model.palette.tools.map(tool => tool.kind === 'CREATE_NODE' && tool.formId === selectedForm.id ? { ...tool, formId: '' } : tool) }
        });
        setSelection(selectedRepresentation === null ? null : { kind: 'REPRESENTATION', id: selectedRepresentation.id });
    }

    async function selectEditor(editorId: string): Promise<void> {
        const summary = availableEditors.find(item => item.id === editorId);
        if (summary === undefined || summary.id === model.id) return;
        try {
            const result = await loadEditorDefinition(metamodel.id, summary.id);
            setLoadedModel(result.model, result.etag);
        } catch (exception) {
            onError(exception instanceof Error ? exception.message : 'Could not load editor definition');
        }
    }

    async function save(): Promise<boolean> {
        if (!persisted) { onError('Save the metamodel before defining its model editors.'); return false; }
        const error = findEditorDefinitionBlockingError(model);
        if (error !== '') { onError(error); return false; }
        if (!await confirmOverwrite()) return false;
        try {
            if (expressionDirty) {
                const expressionResult = expressionEtag === '' ? await createExpressionModel(expressionModel) : await replaceExpressionModel(expressionModel, expressionEtag);
                setExpressionModel(expressionResult.expressionModel);
                setExpressionEtag(expressionResult.etag);
                setExpressionDirty(false);
            }
            const result = etag === '' ? await createEditorDefinition(model) : await replaceEditorDefinition(model, etag);
            const savedModel = normalizeEditorDefinition(result.editorDefinition, metamodel);
            setModel(savedModel);
            setEtag(result.etag);
            setDirty(false);
            setAvailableEditors(current => {
                const summary = { id: savedModel.id, name: savedModel.name };
                return [...current.filter(item => item.id !== summary.id), summary].sort((left, right) => left.name.localeCompare(right.name));
            });
            onMessage('Model editor definition saved');
            return true;
        } catch (exception) {
            onError(exception instanceof Error ? exception.message : 'Could not save editor definition');
            return false;
        }
    }

    async function compileRuntime(): Promise<void> {
        if (!persisted || etag === '') { onError('Save the editor definition before compiling its runtime.'); return; }
        if (dirty || expressionDirty) { onError('Save the pending editor and conditional-style changes before compiling its runtime.'); return; }
        setCompilingRuntime(true);
        try {
            const result = await compileEditorRuntime(metamodel.id, model.id);
            setRuntimeResult(result);
            setDesignerView('RUNTIME');
            onMessage(result.cacheHit ? 'Compiled editor runtime loaded from cache' : 'Editor runtime compiled');
        } catch (exception) {
            onError(exception instanceof Error ? exception.message : 'Could not compile editor runtime');
        } finally {
            setCompilingRuntime(false);
        }
    }

    const definitionBlockingError = findEditorDefinitionBlockingError(model);
    const guidedSteps = buildEditorDefinitionGuidedSteps({
        model, metamodel, detectedRoots, etag, dirty, expressionDirty, compilingRuntime, runtimeResult, definitionBlockingError, capabilities: CAPABILITIES,
        changeModel, updateRepresentation, addRepresentation, addPaletteGroup, addForm, save, compileRuntime, setDesignerView,
        openAdvancedRepresentation: id => { setSelectedRepresentationId(id); setSelection({ kind: 'REPRESENTATION', id }); setTreeDetailLevel('FULL'); setDesignerView('DESIGN'); },
    });

    return (
        <section className="editor-definition-designer">
            <DesignerCommandBar className="editor-definition-header hydra-unified-workbench-toolbar" title="Model editor designer" description="Design Sirius-style viewpoints, mappings, tools, forms, containers and project image representations over the main metamodel." actions={<><DesignerCommandGroup label="Interchange"><EditorDefinitionRemoteStorage model={model} metamodel={metamodel} onImported={next => { setLoadedModel(next, ''); setDirty(true); }} /></DesignerCommandGroup><DesignerCommandGroup label="Changes"><HydraButton type="button" disabled={!dirty} onClick={() => void save()}>Save editor</HydraButton></DesignerCommandGroup><DesignerCommandGroup label="Generation"><HydraButton type="button" disabled={dirty || expressionDirty || etag === '' || compilingRuntime} onClick={() => void compileRuntime()}>{compilingRuntime ? 'Compiling…' : 'Compile runtime'}</HydraButton></DesignerCommandGroup></>} viewsLabel="Workspace" viewsSummary={designerView} views={<nav className="editor-definition-subtabs" aria-label="Model editor designer views"> <HydraButton className={designerView === 'GUIDED' ? 'active' : ''} type="button" onClick={() => setDesignerView('GUIDED')}>Guided</HydraButton> <HydraButton className={designerView === 'ELEMENTS' ? 'active' : ''} type="button" onClick={() => setDesignerView('ELEMENTS')}>Element representations</HydraButton> <HydraButton className={designerView === 'DESIGN' ? 'active' : ''} type="button" onClick={() => setDesignerView('DESIGN')}>Definition</HydraButton> <HydraButton className={designerView === 'CODE' ? 'active' : ''} type="button" onClick={() => setDesignerView('CODE')}>Code generation</HydraButton> {structureTabOpen ? <span className={`editor-definition-subtab-entry${designerView === 'STRUCTURE' ? ' active' : ''}`}> <HydraButton className={designerView === 'STRUCTURE' ? 'active' : ''} type="button" onClick={() => setDesignerView('STRUCTURE')}>Structure tree</HydraButton> <HydraButton className="editor-definition-subtab-close" type="button" aria-label="Close structure tree tab" onClick={() => { setStructureTabOpen(false); if (designerView === 'STRUCTURE') setDesignerView('DESIGN'); }}>×</HydraButton> </span> : <HydraButton type="button" onClick={() => { setStructureTabOpen(true); setDesignerView('STRUCTURE'); }}>+ Structure tree</HydraButton>} <HydraButton className={designerView === 'RUNTIME' ? 'active' : ''} type="button" onClick={() => setDesignerView('RUNTIME')}>Runtime preview</HydraButton> </nav>} />
            {designerView === 'ELEMENTS' && <ElementRepresentationDesigner key={metamodel.id} model={model} metamodel={metamodel} onAddClassRepresentation={addClassTool} onAddRelationRepresentation={addRelationTool} onEditNodeRepresentation={(representationId, mappingId) => openMapping(representationId, { kind: 'NODE_MAPPING', id: mappingId })} onEditEdgeRepresentation={(representationId, mappingId) => openMapping(representationId, { kind: 'EDGE_MAPPING', id: mappingId })} onCreateRepresentation={addRepresentation} />}
            {designerView === 'GUIDED' && <div className="editor-definition-guided-workflow"><HydraGuidedWorkflow key={model.id} title="Create a model editor step by step" description="The assistant edits the same editor definition used by Definition, Structure tree and Runtime preview. Detailed mappings and visual configuration are never removed." steps={guidedSteps} currentStepId={guidedStep} onStepChange={setGuidedStep} onFinish={() => save()} finishLabel="Save model editor" /></div>}
            {designerView === 'DESIGN' && <div className="hydra-designer-master-detail editor-definition-grid" style={sidebar.layoutStyle}>
                <aside className="hydra-designer-sidebar">
                    <div className="hydra-sidebar-heading"><div><h3>Editor definition tree</h3><small>{model.representations.length} representation(s) · {model.palette.tools.length} tool(s)</small></div><div className="hydra-sidebar-heading-actions"><DesignerGuideButton kind="EDITOR" /><HydraButton type="button" onClick={newEditor}>+ New editor</HydraButton></div></div>
                    <p className="editor-definition-palette-policy">Start with the editor, its views and forms. Switch to All details only when you need mappings, palette tools or technical metamodel structure.</p>
                    <div className="editor-definition-editor-select"><HydraAdaptiveSelect id="editor-definition-selector" ariaLabel="Model editor" value={model.id} options={currentEditorSummaries.map(item => ({ value: item.id, label: item.name, category: item.id === model.id ? 'Current editor' : 'Other editors', description: item.id }))} onChange={editorId => void selectEditor(editorId)} /></div>
                    <div className="editor-detail-level-switch" role="group" aria-label="Editor tree detail"><HydraButton className={treeDetailLevel === 'ESSENTIAL' ? 'active' : ''} type="button" onClick={() => setTreeDetailLevel('ESSENTIAL')}>Overview</HydraButton><HydraButton className={treeDetailLevel === 'FULL' ? 'active' : ''} type="button" onClick={() => setTreeDetailLevel('FULL')}>All details</HydraButton></div>
                    <div className="hydra-sidebar-filter"><input aria-label="Search editor definition tree" placeholder={treeDetailLevel === 'ESSENTIAL' ? 'Search views and forms...' : 'Search metamodel, mappings, tools...'} value={filter} onChange={event => setFilter(event.target.value)} /></div>
                    <div className="editor-definition-tree-actions"><HydraButton type="button" onClick={addRepresentation}>+ Representation</HydraButton><HydraButton type="button" onClick={addForm}>+ Form</HydraButton>{treeDetailLevel === 'FULL' && <HydraButton type="button" onClick={addPaletteGroup}>+ Palette group</HydraButton>}</div>
                    <div className="hydra-sidebar-scroll"><EditorDefinitionTree model={model} metamodel={metamodel} selection={selection} selectedRepresentationId={selectedRepresentationId} filter={filter} detailLevel={treeDetailLevel} showAddActions={treeDetailLevel === 'FULL'} onSelect={handleTreeSelection} onAddClassMapping={addClassTool} onAddRelationMapping={addRelationTool} /></div>
                </aside>
                <div {...sidebar.separatorProps} />
                <main className="hydra-designer-form editor-definition-form-surface">
                    <section className="editor-document-disclosure"><div><strong>Editor-wide settings</strong><span>{model.name} · {model.capabilityManifest.capabilities.length} capability(ies)</span></div><HydraButton type="button" onClick={() => setDocumentSettingsOpen(value => !value)}>{documentSettingsOpen ? 'Hide' : 'Show'}</HydraButton></section>
                    {documentSettingsOpen && <EditorDocumentForm model={model} etag={etag} capabilities={CAPABILITIES} onChange={changeModel} showHeader={false} />}
                    <div className="editor-selected-properties">{selectedElementProperties(false)}</div>
                </main>
            </div>}
            {designerView === 'STRUCTURE' && structureTabOpen && <section className={`editor-structure-panel${structureCollapsed ? ' collapsed' : ''}`}>
                <header><div><h3>Generated editor structure</h3><p>{treeDetailLevel === 'ESSENTIAL' ? 'A high-level map of the editor, its views, forms and tools.' : 'Complete semantic metamodel plus every representation, mapping, palette group, tool and form.'}</p></div><div className="editor-structure-header-controls"><HydraAdaptiveSelect id="editor-structure-selector" ariaLabel="Editor shown in structure tree" value={model.id} options={currentEditorSummaries.map(item => ({ value: item.id, label: item.name, category: item.id === model.id ? 'Current editor' : 'Other editors', description: item.id }))} onChange={editorId => void selectEditor(editorId)} /><div className="editor-detail-level-switch" role="group" aria-label="Structure detail"><HydraButton className={treeDetailLevel === 'ESSENTIAL' ? 'active' : ''} type="button" onClick={() => setTreeDetailLevel('ESSENTIAL')}>Overview</HydraButton><HydraButton className={treeDetailLevel === 'FULL' ? 'active' : ''} type="button" onClick={() => setTreeDetailLevel('FULL')}>All details</HydraButton></div><HydraButton type="button" onClick={() => setStructureCollapsed(value => !value)}>{structureCollapsed ? 'Expand tree' : 'Collapse tree'}</HydraButton></div></header>
                {!structureCollapsed && <div className={`editor-structure-edit-layout${structurePropertiesOpen ? '' : ' properties-closed'}`} style={structureTree.layoutStyle}>
                    <div className="editor-structure-tree-scroll"><EditorDefinitionTree model={model} metamodel={metamodel} selection={selection} selectedRepresentationId={selectedRepresentationId} detailLevel={treeDetailLevel} interactive showAddActions={false} onSelect={handleTreeSelection} onInspect={inspectStructure} /></div>
                    {structurePropertiesOpen && <div {...structureTree.separatorProps} />}
                    {structurePropertiesOpen ? <aside className={`editor-structure-properties${structurePropertiesCollapsed ? ' collapsed' : ''}`} aria-label="Editor structure properties">
                        <header><h4>Properties</h4><div><HydraButton type="button" onClick={() => setStructurePropertiesCollapsed(value => !value)}>{structurePropertiesCollapsed ? 'Expand properties' : 'Collapse properties'}</HydraButton><HydraButton type="button" aria-label="Close editor structure properties" className="hydra-form-close-button" onClick={() => setStructurePropertiesOpen(false)}>×</HydraButton></div></header>
                        {!structurePropertiesCollapsed && <div className="editor-structure-properties-scroll">{selectedElementProperties(true)}</div>}
                    </aside> : <HydraButton className="editor-structure-properties-reopen" type="button" onClick={() => setStructurePropertiesOpen(true)}>+ Properties</HydraButton>}
                </div>}
            </section>}
            {designerView === 'CODE' && <CodeGenerationDesigner model={model} onChange={changeModel} />}
            {overwriteDialogs}
            {designerView === 'RUNTIME' && <EditorRuntimePreview result={runtimeResult} metamodel={metamodel} compiling={compilingRuntime} onCompile={() => void compileRuntime()} onError={onError} onMessage={onMessage} />}
        </section>
    );

}

