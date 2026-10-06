import { resolveHydraDiagramPanPolicy, resolveHydraDiagramTranslateExtent } from '../designer-ui/boundedDiagramViewport';
import { useCallback, useMemo, useState } from 'react';
import type { DragEvent } from 'react';
import { Background, Controls, ConnectionMode, MiniMap, ReactFlow } from '@xyflow/react';
import type { Connection, Edge, EdgeChange, EdgeTypes, Node, NodeChange, NodeTypes, OnSelectionChangeFunc, ReactFlowInstance } from '@xyflow/react';
import { getModelDownloadUrl } from '../api/editorApi';
import { MarkerDefinitions } from '../components/MarkerDefinitions';
import { MODEL_PALETTE_DRAG_TYPE, Palette, type ModelPaletteDragPayload } from '../components/Palette';
import { PropertyInspector } from '../components/PropertyInspector';
import { HydraButton, HydraErrorBoundary, HydraModal, HydraOperationProgress, HydraSelect } from '../design-system';
import { DiagramInteractionToolbar, type DiagramInteractionMode } from '../designer-ui/DiagramInteractionToolbar';
import { DesignerCommandBar, DesignerCommandGroup } from '../designer-ui/DesignerCommandBar';
import { useHydraFlowPerformance } from '../diagram/useHydraFlowPerformance';
import { useResizableDesignerPanelHeight } from '../designer-ui/useResizableDesignerPanelHeight';
import { useResizableDesignerSidebar } from '../designer-ui/useResizableDesignerSidebar';
import { useHydraLanguage } from '../i18n';
import { layoutHydraElkStyle } from '../diagram/elkStyleLayout';
import { OperationRuntimePanel } from '../operation-runtime/OperationRuntimePanel';
import { ProblemsPanel } from '../problems/ProblemsPanel';
import { ContextualModelEditor } from './ContextualModelEditor';
import { ModelCodeGenerationDialog } from '../model-codegen/ModelCodeGenerationDialog';
import { generateModelCode, listModelCodeGenerationTargets, previewModelCode } from '../model-codegen/api';
import type { EditorBootstrap, ModelDocument, NotationDefinition, NotationSummary, PaletteEntry, SavedModelSummary } from '../types/editor';
import type { ModelValidationReport, OperationRuntimeCatalog } from '../validation/types';

interface Props {
    selectedMetamodelId: string;
    selectedSavedFileName: string;
    savedModels: SavedModelSummary[];
    selectedNotationId: string;
    notations: NotationSummary[];
    document: ModelDocument | null;
    notation: NotationDefinition | null;
    bootstrap: EditorBootstrap | null;
    selectedElementId: string;
    diagramDirty: boolean;
    operationCatalog: OperationRuntimeCatalog | null;
    nodes: Node[];
    edges: Edge[];
    nodeTypes: NodeTypes;
    edgeTypes: EdgeTypes;
    setSelectedSavedFileName: (value: string) => void;
    setSelectedNotationId: (value: string) => void;
    setSelectedElementId: (value: string) => void;
    setDiagramDirty: (value: boolean) => void;
    setError: (value: string) => void;
    setMessage: (value: string) => void;
    openCurrentSavedModel: () => Promise<void>;
    createSemanticModel: () => Promise<void>;
    reloadMetamodels: () => Promise<void>;
    saveCurrentDiagram: () => Promise<void>;
    loadCurrentDiagram: () => Promise<void>;
    saveCurrentModel: (onStage?: (stage: 'PERSIST' | 'REFRESH' | 'COMPLETE') => void) => Promise<void>;
    deleteSelectedSavedModel: () => Promise<boolean>;
    deleteSelectedElements: (elementIds: string[]) => Promise<void>;
    addSemanticElement: (entry: PaletteEntry, nodeStyleId?: string, position?: { x: number; y: number }) => Promise<void>;
    handleBeforeDelete: (deletion: { nodes: Node[]; edges: Edge[] }) => Promise<boolean>;
    connectSemanticElements: (connection: Connection) => Promise<void>;
    onEdgesChange: (changes: EdgeChange<Edge>[]) => void;
    handleNodesChange: (changes: NodeChange<Node>[]) => void;
    applyAttribute: (attributeName: string, value: string) => Promise<void>;
    applyOperationDocument: (document: ModelDocument) => void;
    applyProblemsReport: (report: ModelValidationReport | null) => void;
}

/** Presentación del Model Editor separada del shell principal para mantener App acotado. */
export function ModelEditorWorkspace(props: Props) {
    const { t } = useHydraLanguage();
    const [inspectorTab, setInspectorTab] = useState<'PROPERTIES' | 'OPERATIONS' | 'PROBLEMS'>('PROPERTIES');
    const paletteWidth = useResizableDesignerSidebar({
        storageKey: 'hydra:model-editor:palette-width', ariaLabel: t('Resize model palette'), initialWidth: 260,
        minimumWidth: 190, maximumWidth: 360, cssVariableName: '--model-editor-palette-width',
        separatorClassName: 'model-editor-column-resizer palette-resizer'
    });
    const inspectorHeight = useResizableDesignerPanelHeight({
        storageKey: 'hydra:model-editor:inspector-height', ariaLabel: t('Resize model inspector'), initialHeight: 230,
        minimumHeight: 150, maximumHeight: 520, cssVariableName: '--model-editor-inspector-height',
        separatorClassName: 'model-editor-row-resizer inspector-resizer'
    });
    const workspaceStyle = { ...paletteWidth.layoutStyle, ...inspectorHeight.layoutStyle };
    const [interactionMode, setInteractionMode] = useState<DiagramInteractionMode>('SELECT');
    const [deleteModelOpen, setDeleteModelOpen] = useState(false);
    const [codeGenerationOpen, setCodeGenerationOpen] = useState(false);
    const [codeGenerationSelectionIds, setCodeGenerationSelectionIds] = useState<string[]>([]);
    const [selectedElementIdsState, setSelectedElementIds] = useState<string[]>([]);
    const [deletingModel, setDeletingModel] = useState(false);
    const [savingModel, setSavingModel] = useState(false);
    const [modelSaveStage, setModelSaveStage] = useState<'PERSIST' | 'REFRESH' | 'COMPLETE'>('PERSIST');
    const [flowInstance, setFlowInstance] = useState<ReactFlowInstance<Node, Edge> | null>(null);
    const currentDocument = props.document;
    const performanceProfile = useHydraFlowPerformance(`model:${currentDocument?.id ?? 'empty'}`, props.nodes.length, props.edges.length);
    const [runtimeAvailability,setRuntimeAvailability]=useState<{metamodelId:string;available:boolean}>({metamodelId:'',available:false});
    const configuredRuntimeResolved=runtimeAvailability.metamodelId===props.selectedMetamodelId;
    const configuredRuntimeAvailable=configuredRuntimeResolved&&runtimeAvailability.available;
    const {setError,setMessage}=props;
    const reportConfiguredAvailability=useCallback((available:boolean)=>setRuntimeAvailability({metamodelId:props.selectedMetamodelId,available}),[props.selectedMetamodelId]);
    const reportContextualError=useCallback((value:string)=>setError(value),[setError]);
    const reportContextualMessage=useCallback((value:string)=>setMessage(value),[setMessage]);
    const availableNodeIds=useMemo(()=>new Set(props.nodes.map(node=>node.id)),[props.nodes]);
    const selectedElementIds=useMemo(()=>selectedElementIdsState.filter(id=>availableNodeIds.has(id)),[availableNodeIds,selectedElementIdsState]);

    function dropModelElement(event: DragEvent<HTMLDivElement>): void {
        event.preventDefault();
        if (flowInstance === null) return;
        const raw = event.dataTransfer.getData(MODEL_PALETTE_DRAG_TYPE);
        if (raw === '') return;
        try {
            const payload = JSON.parse(raw) as ModelPaletteDragPayload;
            const position = flowInstance.screenToFlowPosition({ x: event.clientX, y: event.clientY });
            void props.addSemanticElement(payload.entry, payload.nodeStyleId, position);
        } catch {
            props.setError(t('The palette item could not be placed on the canvas.'));
        }
    }



    const selectionChange: OnSelectionChangeFunc = ({ nodes }) => {
        const ids = nodes.map(node => node.id);
        setSelectedElementIds(ids);
        props.setSelectedElementId(ids[0] ?? '');
    };

    function openFullCodeGeneration(): void { setCodeGenerationSelectionIds([]); setCodeGenerationOpen(true); }
    function openSelectedCodePreview(): void { if(selectedElementIds.length===0)return; setCodeGenerationSelectionIds(selectedElementIds); setCodeGenerationOpen(true); }
    async function deleteCanvasSelection(): Promise<void> {
        if(selectedElementIds.length===0)return;
        await props.deleteSelectedElements(selectedElementIds);
        setSelectedElementIds([]);
    }

    function elkLayoutModel(): void {
        if (props.nodes.length === 0) return;
        const placements = layoutHydraElkStyle(
            props.nodes.map(node => ({ id: node.id, width: node.measured?.width ?? node.width ?? 180, height: node.measured?.height ?? node.height ?? 90, parentId: node.parentId })),
            props.edges.map(edge => ({ id: edge.id, source: edge.source, target: edge.target })),
            { direction: 'RIGHT', nodeGap: 58, layerGap: 120 }
        );
        const changes: NodeChange<Node>[] = props.nodes.flatMap(node => {
            const placement = placements.get(node.id);
            return placement === undefined ? [] : [{ id: node.id, type: 'position' as const, position: { x: placement.x + (placement.parentId ? 0 : 60), y: placement.y + (placement.parentId ? 0 : 60) }, dragging: false }];
        });
        props.handleNodesChange(changes);
        props.setDiagramDirty(true);
        requestAnimationFrame(() => void flowInstance?.fitView({ padding: 0.18, duration: 250 }));
    }

    async function confirmDeleteSavedModel(): Promise<void> {
        const fileName = props.selectedSavedFileName;
        setDeletingModel(true);
        try {
            const deleted = await props.deleteSelectedSavedModel();
            if (deleted) {
                props.setMessage(`${t('Deleted model')}: ${fileName}`);
                setDeleteModelOpen(false);
            }
        } finally { setDeletingModel(false); }
    }

    async function saveModelWithProgress(): Promise<void> {
        if (currentDocument === null || savingModel) return;
        setSavingModel(true);
        setModelSaveStage('PERSIST');
        try { await props.saveCurrentModel(setModelSaveStage); }
        finally { setSavingModel(false); }
    }

    return <HydraErrorBoundary scope="Model editor" resetKey={`${props.selectedMetamodelId}:${currentDocument?.id ?? 'empty'}`} onError={exception => props.setError(`Model editor interface error: ${exception.message}`)}>
        <section className="model-editor-workbench">
            <ContextualModelEditor metamodelId={props.selectedMetamodelId} sourceModelId={currentDocument?.id??''}
                onAvailabilityChange={reportConfiguredAvailability} onError={reportContextualError} onMessage={reportContextualMessage}/>
            {(configuredRuntimeResolved&&!configuredRuntimeAvailable)&&<>
            <DesignerCommandBar
                className="model-editor-command-surface model-editor-designer-toolbar"
                title={t('Model editor')}
                description={t('Open, create and edit models with the same compact command layout used by the other HYDRA layers.')}
                context={<div className="model-editor-model-context">
                    <span className="model-editor-command-label">{t('Model')}</span>
                    <HydraSelect className="hydra-command-context-select model-editor-saved-model-select" ariaLabel={t('Saved model')} showFilter={false} value={props.selectedSavedFileName} placeholder={t('Select saved model')} options={props.savedModels.map(savedModel => ({ value: savedModel.fileName, label: savedModel.fileName, description: `${savedModel.size} bytes`, category: t('Current project') }))} onChange={props.setSelectedSavedFileName} />
                </div>}
                actions={<>
                    <DesignerCommandGroup className="model-editor-command-group model-file-group" label={t('Model')}>
                        <HydraButton disabled={props.selectedSavedFileName === ''} size="small" onClick={() => void props.openCurrentSavedModel()}>{t('Open XMI')}</HydraButton>
                        <HydraButton size="small" variant="primary" onClick={() => void props.createSemanticModel()}>{t('New model')}</HydraButton>
                        <HydraButton disabled={props.selectedSavedFileName === ''} size="small" variant="danger" onClick={() => setDeleteModelOpen(true)}>{t('Delete model')}</HydraButton>
                        <HydraButton disabled={currentDocument === null} size="small" onClick={openFullCodeGeneration}>{t('Generate code')}</HydraButton>{selectedElementIds.length>0&&<HydraButton disabled={currentDocument===null} size="small" onClick={openSelectedCodePreview}>{t('Preview code')} ({selectedElementIds.length})</HydraButton>}
                        {currentDocument !== null && <a className="download-link model-editor-download-link" href={getModelDownloadUrl(currentDocument.fileName)}>{t('Download XMI')}</a>}
                    </DesignerCommandGroup>
                    <DesignerCommandGroup className="model-editor-command-group model-notation-group" label={t('Visual representation')}>
                        <HydraSelect ariaLabel={t('Visual representation')} showFilter={false} value={props.selectedNotationId} placeholder={t('Select visual representation')} options={props.notations.filter(summary => summary.metamodelId === props.selectedMetamodelId).map(summary => ({ value: summary.id, label: summary.name, description: t('Basic visual representation available for the selected metamodel.'), category: t('Basic representations') }))} onChange={next => { props.setSelectedNotationId(next); if (currentDocument !== null) props.setDiagramDirty(true); }} />
                        <HydraButton size="small" onClick={() => void props.reloadMetamodels()}>{t('Reload')}</HydraButton>
                    </DesignerCommandGroup>
                    <DesignerCommandGroup className="model-editor-command-group model-layout-group" label={t('Layout')}>
                        <HydraButton disabled={currentDocument === null || props.selectedNotationId === '' || !props.diagramDirty} size="small" onClick={() => void props.saveCurrentDiagram()}>{t('Save layout')}</HydraButton>
                        <HydraButton disabled={currentDocument === null} size="small" onClick={() => void props.loadCurrentDiagram()}>{t('Load layout')}</HydraButton>
                    </DesignerCommandGroup>
                    <DesignerCommandGroup className="model-editor-command-group model-change-group" label={t('Changes')}>
                        <HydraButton loading={savingModel} disabled={currentDocument === null || !currentDocument.dirty} size="small" onClick={() => void saveModelWithProgress()}>{t('Save XMI')}</HydraButton>
                        <HydraButton disabled={currentDocument === null || selectedElementIds.length === 0 || selectedElementIds.includes(currentDocument.rootElementId)} size="small" variant="danger" onClick={() => void deleteCanvasSelection()}>{selectedElementIds.length>1?`${t('Delete selected')} (${selectedElementIds.length})`:t('Delete selected')}</HydraButton>
                    </DesignerCommandGroup>
                </>}
            />
            <HydraOperationProgress active={savingModel} compact title={t('Saving model')} detail={modelSaveStage === 'PERSIST' ? t('Writing the model document…') : modelSaveStage === 'REFRESH' ? t('Refreshing the saved model list…') : t('Save completed.')} currentStageId={modelSaveStage === 'PERSIST' ? 'persist' : modelSaveStage === 'REFRESH' ? 'refresh' : 'complete'} completed={modelSaveStage === 'COMPLETE'} stages={[{ id: 'persist', label: t('Write model document') }, { id: 'refresh', label: t('Refresh saved models') }, { id: 'complete', label: t('Confirm save') }]} />
            <div className="model-editor-status-strip">
                <span>{currentDocument !== null ? `${t('Document')}: ${currentDocument.fileName} · ${currentDocument.dirty ? t('Unsaved changes') : t('Saved')}` : t('No model document is open.')}</span>
                <span>{props.selectedNotationId !== '' ? `${t('Visual representation')}: ${props.selectedNotationId}` : t('No visual representation selected.')}</span>
                <span>{props.diagramDirty ? t('Layout has pending changes.') : t('Layout is synchronized.')}</span>
            </div>
            <section className="workspace model-editor-workspace" style={workspaceStyle}>
                <Palette entries={props.bootstrap?.palette ?? []} notation={props.notation} onCreate={(entry, nodeStyleId) => void props.addSemanticElement(entry, nodeStyleId)} />
                <div {...paletteWidth.separatorProps} />
                <section className="model-editor-canvas-region">
                    <DiagramInteractionToolbar
                        performanceId={`model:${currentDocument?.id ?? 'empty'}`}
                        mode={interactionMode}
                        onModeChange={setInteractionMode}
                        allowConnect
                        onElkLayout={currentDocument === null || props.nodes.length === 0 ? undefined : elkLayoutModel}
                        onFit={() => void flowInstance?.fitView({ padding: 0.18, duration: 250 })}
                    />
                    <div className="canvas" onDrop={dropModelElement} onDragOver={event => { event.preventDefault(); event.dataTransfer.dropEffect = 'copy'; }}>
                        <ReactFlow onlyRenderVisibleElements={performanceProfile.onlyRenderVisibleElements} connectionMode={ConnectionMode.Loose} edgeTypes={props.edgeTypes} edges={props.edges} fitView nodeTypes={props.nodeTypes} nodes={props.nodes}
                            onInit={setFlowInstance} onBeforeDelete={props.handleBeforeDelete} onConnect={connection => void props.connectSemanticElements(connection)} onEdgesChange={props.onEdgesChange}
                            onNodeClick={(_event, node) => props.setSelectedElementId(node.id)} onSelectionChange={selectionChange} onNodesChange={props.handleNodesChange}
                            elementsSelectable={interactionMode === 'SELECT'} nodesDraggable={interactionMode === 'SELECT'} nodesConnectable={interactionMode === 'CONNECT'}
                            {...resolveHydraDiagramPanPolicy(interactionMode)} translateExtent={resolveHydraDiagramTranslateExtent(props.nodes)} selectionOnDrag={interactionMode === 'SELECT'} multiSelectionKeyCode={['Control', 'Meta']} selectionKeyCode={null}>
                            <MarkerDefinitions /><MiniMap pannable zoomable /><Controls /><Background />
                        </ReactFlow>
                    </div>
                </section>
                <div {...inspectorHeight.separatorProps} />
                <aside className="model-inspector-stack" aria-label={t('Model inspector')}>
                    <nav className="model-inspector-tabs" aria-label={t('Model inspector tabs')}>
                        <HydraButton className={inspectorTab === 'PROPERTIES' ? 'active' : ''} type="button" onClick={() => setInspectorTab('PROPERTIES')}>{t('Properties')}</HydraButton>
                        <HydraButton className={inspectorTab === 'OPERATIONS' ? 'active' : ''} type="button" onClick={() => setInspectorTab('OPERATIONS')}>{t('Operations')}</HydraButton>
                        <HydraButton className={inspectorTab === 'PROBLEMS' ? 'active' : ''} type="button" onClick={() => setInspectorTab('PROBLEMS')}>{t('Problems')}</HydraButton>
                    </nav>
                    <div className="model-inspector-content">
                        {inspectorTab === 'PROPERTIES' && <PropertyInspector descriptor={props.bootstrap?.descriptor ?? null} document={currentDocument} selectedElementId={props.selectedElementId} onApply={props.applyAttribute} />}
                        {inspectorTab === 'OPERATIONS' && <OperationRuntimePanel catalog={props.operationCatalog} document={currentDocument} selectedElementId={props.selectedElementId} onDocumentChange={props.applyOperationDocument} onError={props.setError} onMessage={value => { props.setMessage(value); props.setError(''); }} />}
                        {inspectorTab === 'PROBLEMS' && <ProblemsPanel key={currentDocument?.id ?? 'no-model'} document={currentDocument} selectedElementId={props.selectedElementId} onSelectElement={props.setSelectedElementId} onDocumentChange={props.applyOperationDocument} onReportChange={props.applyProblemsReport} onError={props.setError} onMessage={value => { props.setMessage(value); props.setError(''); }} />}
                    </div>
                </aside>
            </section>
            </>}
            <ModelCodeGenerationDialog open={codeGenerationOpen && currentDocument !== null} modelName={currentDocument?.fileName.replace(/\.xmi$/i,'') ?? 'GeneratedModel'} selectionElementIds={codeGenerationSelectionIds} loadTargets={() => currentDocument === null ? Promise.resolve([]) : listModelCodeGenerationTargets(currentDocument.id)} generate={(targetId,options,selection) => currentDocument === null ? Promise.resolve() : generateModelCode(currentDocument.id,targetId,options,selection)} preview={(targetId,options,selection) => currentDocument === null ? Promise.reject(new Error('No model is open')) : previewModelCode(currentDocument.id,targetId,options,selection)} onClose={() => setCodeGenerationOpen(false)} onError={props.setError} onMessage={value => { props.setMessage(value); props.setError(''); }} />
            <HydraModal open={deleteModelOpen} onClose={() => { if (!deletingModel) setDeleteModelOpen(false); }} title={t('Delete saved model?')} description={t('This permanently removes the selected XMI file and its saved visual layout.')} size="small" footer={<><HydraButton disabled={deletingModel} variant="quiet" onClick={() => setDeleteModelOpen(false)}>{t('Cancel')}</HydraButton><HydraButton disabled={deletingModel} variant="danger" onClick={() => void confirmDeleteSavedModel()}>{deletingModel ? t('Deleting…') : t('Delete model')}</HydraButton></>}>
                <p><strong>{props.selectedSavedFileName}</strong></p>
                {currentDocument?.fileName === props.selectedSavedFileName && currentDocument.dirty && <p>{t('This model has unsaved changes. Deleting the saved file will discard them.')}</p>}
            </HydraModal>
        </section>
    </HydraErrorBoundary>;
}
