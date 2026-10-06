import { memo, Suspense, useEffect, useEffectEvent, useState } from 'react';
import { explainInputError } from './api/inputErrorExplanation';
import { HydraActionableErrorNotice } from './design-system';
import type { ReactNode } from 'react';
import { useEdgesState, useNodesState } from '@xyflow/react';
import type { Connection, Edge, Node, NodeChange } from '@xyflow/react';
import '@xyflow/react/dist/style.css';

import { addModelElement, createModel, deleteDiagram, deleteModelElements, deleteSavedModel, listMetamodels, listNotations, listSavedModels, loadDiagram, loadEditorBootstrap, loadNotation, openSavedModel, reloadMetamodelDirectory, saveDiagram, saveModel, setModelAttribute, setModelReference } from './api/editorApi';
import { SemanticEdge } from './components/SemanticEdge';
import { SemanticNode } from './components/SemanticNode';
import { buildDiagramDocument } from './diagram/diagramUtils';
import { findConnectionReference, findContainmentReference, findRootClass } from './model/modelUtils';
import type { DiagramDocument, EditorBootstrap, MetamodelSummary, ModelDocument, ModelElement, NotationDefinition, NotationSummary, PaletteEntry, SavedModelSummary } from './types/editor';

import { loadOperationRuntimeCatalog } from './operation-runtime/operationRuntimeApi';
import type { ModelValidationReport, OperationRuntimeCatalog, ValidationSeverity } from './validation/types';
import { ProjectExplorerWorkspace } from './project-explorer/ProjectExplorerWorkspace';
import { SettingsDialog } from './settings/SettingsDialog';
import { HydraApplicationShell } from './application-shell/HydraApplicationShell';
import { HydraHome } from './application-shell/HydraHome';
import { HydraModuleLoading } from './application-shell/HydraModuleLoading';
import { HydraCapabilityModule } from './application-shell/HydraCapabilityModule';
import { moduleDefinition, moduleFromHash, moduleHash } from './application-shell/moduleRegistry';
import type { HydraModuleId } from './application-shell/types';
import { ModelEditorWorkspace } from './model-editor/ModelEditorWorkspace';
import { buildModelEditorGraph, findModelElement } from './model-editor/modelEditorDocumentView';
import { hasHydraGeometryChange } from './designer-ui/hydraNodeGeometry';

import './App.css';
import './application-shell/application-shell.css';
import './application-shell/application-shell-home-dashboard.css';
import './workbench-visual-pass.css';



const nodeTypes = { semantic: memo(SemanticNode) };
const edgeTypes = { semantic: memo(SemanticEdge) };
/**
 * Representa la aplicación principal del editor.
 *
 * @returns interfaz gráfica conectada con EMF.
 */
function App() {
    const [metamodels, setMetamodels] = useState<MetamodelSummary[]>([]);
    const [selectedMetamodelId, setSelectedMetamodelId] = useState('');
    const [bootstrap, setBootstrap] = useState<EditorBootstrap | null>(null);
    const [document, setDocument] = useState<ModelDocument | null>(null);
    const [selectedElementId, setSelectedElementId] = useState('');
    const [error, setError] = useState('');
    const [message, setMessage] = useState('');
    const [operationCatalog, setOperationCatalog] = useState<OperationRuntimeCatalog | null>(null);
    const [nodes, setNodes, applyNodeChanges] = useNodesState<Node>([]);
    const [edges, setEdges, onEdgesChange] = useEdgesState<Edge>([]);
    const [savedModels, setSavedModels] = useState<SavedModelSummary[]>([]);
    const [selectedSavedFileName, setSelectedSavedFileName] = useState('');
    const [notations, setNotations] = useState<NotationSummary[]>([]);
    const [selectedNotationId, setSelectedNotationId] = useState('');
    const [notation, setNotation] = useState<NotationDefinition | null>(null);
    const [representationByElementId, setRepresentationByElementId] = useState<Record<string, string>>({});
    const [diagramDirty, setDiagramDirty] = useState(false);
    const [activeModule, setActiveModule] = useState<HydraModuleId>(() => moduleFromHash(window.location.hash));
    const [settingsOpen, setSettingsOpen] = useState(false);
    const [problemSeverityByElementId, setProblemSeverityByElementId] = useState<Record<string, ValidationSeverity>>({});

    /**
     * Carga los metamodelos, modelos y notaciones disponibles.
     *
     * @returns promesa sin resultado.
     */
    async function initialize(preferredMetamodelId = ''): Promise<void> {
        try {
            const loadedMetamodels = await listMetamodels();
            const loadedSavedModels = await listSavedModels();
            const loadedNotations = await listNotations();
            let firstMetamodelId = '';
            let firstNotationId = '';

            if (preferredMetamodelId !== '' && loadedMetamodels.some(item => item.id === preferredMetamodelId)) {
                firstMetamodelId = preferredMetamodelId;
            } else if (loadedMetamodels.length > 0) {
                firstMetamodelId = loadedMetamodels[0].id;
            }

            for (const notationSummary of loadedNotations) {
                if (notationSummary.metamodelId === firstMetamodelId) {
                    firstNotationId = notationSummary.id;
                    break;
                }
            }

            setMetamodels(loadedMetamodels);
            setSavedModels(loadedSavedModels);
            setNotations(loadedNotations);
            setSelectedMetamodelId(firstMetamodelId);
            setSelectedNotationId(firstNotationId);

            if (loadedSavedModels.length > 0) {
                setSelectedSavedFileName(loadedSavedModels[0].fileName);
            } else {
                setSelectedSavedFileName('');
            }

            setError('');
        } catch (exception) {
            handleError(exception, 'Could not initialize editor');
        }
    }
    

    /**
     * Actualiza el listado de archivos XMI.
     *
     * @returns promesa sin resultado.
     */
    async function refreshSavedModels(): Promise<void> {
        const loadedSavedModels = await listSavedModels();
        setSavedModels(loadedSavedModels);

        if (loadedSavedModels.length > 0 && selectedSavedFileName === '') {
            setSelectedSavedFileName(loadedSavedModels[0].fileName);
        }
    }

    /** Elimina un XMI guardado y su disposición visual asociada. */
    async function deleteSelectedSavedModel(): Promise<boolean> {
        const fileName = selectedSavedFileName;
        if (fileName === '') return false;
        try {
            await deleteSavedModel(fileName);
            let layoutWarning = '';
            try { await deleteDiagram(fileName); }
            catch (exception) { layoutWarning = explainInputError(exception, 'The model was deleted, but its saved visual layout could not be removed.'); }
            const remaining = await listSavedModels();
            setSavedModels(remaining);
            setSelectedSavedFileName(remaining[0]?.fileName ?? '');
            if (document?.fileName === fileName) {
                setDocument(null);
                setSelectedElementId('');
                setRepresentationByElementId({});
                setProblemSeverityByElementId({});
                setNodes([]);
                setEdges([]);
                setDiagramDirty(false);
            }
            setError(layoutWarning);
            return true;
        } catch (exception) {
            handleError(exception, 'Could not delete saved model');
            return false;
        }
    }

    /**
     * Abre el archivo XMI seleccionado.
     *
     * @returns promesa sin resultado.
     */
    async function openCurrentSavedModel(): Promise<void> {
        if (selectedMetamodelId === '' || selectedSavedFileName === '') {
            return;
        }

        try {
            const openedDocument = await openSavedModel(selectedMetamodelId, selectedSavedFileName);
            updateDocument(openedDocument);
            setSelectedElementId(openedDocument.rootElementId);
            setError('');
        } catch (exception) {
            handleError(exception, 'Could not open saved model');
        }
    }


    /** Elimina una selección de elementos en una sola operación backend. */
    async function deleteSelectedElements(elementIds: string[]): Promise<void> {
        if (document === null || elementIds.length === 0) return;
        if (elementIds.includes(document.rootElementId)) { setError('Root element cannot be deleted'); return; }
        try {
            const updatedDocument = await deleteModelElements(document.id, elementIds);
            updateDocument(updatedDocument);
            setSelectedElementId('');
            setDiagramDirty(true);
            setError('');
        } catch (exception) { handleError(exception, 'Could not delete selected elements'); }
    }

    /**
     * Valida y ejecuta en el backend las eliminaciones solicitadas por React Flow.
     *
     * @param deletion contiene los nodos y conexiones seleccionados.
     * @returns false para impedir que React Flow modifique el estado por su cuenta.
     */
    async function handleBeforeDelete(deletion: { nodes: Node[]; edges: Edge[] }): Promise<boolean> {
        const nodesToDelete = deletion.nodes;
        const edgesToDelete = deletion.edges;

        if (document === null) {
            return false;
        }

        if (nodesToDelete.length === 0 && edgesToDelete.length > 0) {
            setError('Semantic reference deletion is not available yet');
            return false;
        }

        if (nodesToDelete.length === 0) {
            return false;
        }

        await deleteSelectedElements(nodesToDelete.map(node => node.id));

        // El backend devuelve el documento actualizado y updateDocument sincroniza el lienzo.
        return false;
    }

    /**
     * Carga el metamodelo seleccionado.
     *
     * @param metamodelId identificador seleccionado.
     * @returns promesa sin resultado.
     */
    async function loadBootstrap(metamodelId: string): Promise<void> {
        try {
            const [loadedBootstrap, loadedOperationCatalog] = await Promise.all([loadEditorBootstrap(metamodelId), loadOperationRuntimeCatalog(metamodelId)]);
            let compatibleNotationId = '';

            for (const notationSummary of notations) {
                if (notationSummary.metamodelId === metamodelId) {
                    compatibleNotationId = notationSummary.id;

                    if (notationSummary.id === selectedNotationId) {
                        break;
                    }
                }
            }

            setBootstrap(loadedBootstrap);
            setOperationCatalog(loadedOperationCatalog);
            setDocument(null);
            setSelectedElementId('');
            setRepresentationByElementId({});
            setNodes([]);
            setEdges([]);
            setDiagramDirty(false);

            const selectedNotationIsCompatible = notations.some(notationSummary => notationSummary.id === selectedNotationId && notationSummary.metamodelId === metamodelId);

            if (!selectedNotationIsCompatible) {
                setNotation(null);
                setSelectedNotationId(compatibleNotationId);
            }

            setError('');
            setMessage('');
        } catch (exception) {
            setOperationCatalog(null);
            handleError(exception, 'Could not load editor');
        }
    }

    /**
     * Crea un modelo semántico nuevo.
     *
     * @returns promesa sin resultado.
     */
    async function createSemanticModel(): Promise<void> {
        if (bootstrap === null) {
            return;
        }

        const rootClass = findRootClass(bootstrap.descriptor);

        if (rootClass === undefined) {
            setError('No suitable root EClass was found');
            return;
        }

        try {
            const createdDocument = await createModel(bootstrap.metamodelId, rootClass.id);
            updateDocument(createdDocument);
            setDiagramDirty(true);
            setSelectedElementId(createdDocument.rootElementId);
            setError('');
        } catch (exception) {
            handleError(exception, 'Could not create model');
        }
    }

    /**
     * Añade un elemento semántico.
     *
     * @param entry elemento seleccionado de la paleta.
     * @returns promesa sin resultado.
     */
    async function addSemanticElement(entry: PaletteEntry, nodeStyleId?: string, position?: { x: number; y: number }): Promise<void> {
        if (bootstrap === null || document === null) {
            setError('Create a model before adding elements');
            return;
        }
        const rootElement = document.elements.find(element => element.id === document.rootElementId);
        const selectedContainer = document.elements.find(element => element.id === selectedElementId);

        if (rootElement === undefined) {
            setError('Root element was not found');
            return;
        }

        const candidateContainers: ModelElement[] = [];

        if (selectedContainer !== undefined) {
            candidateContainers.push(selectedContainer);
        }

        if (selectedContainer === undefined || selectedContainer.id !== rootElement.id) {
            candidateContainers.push(rootElement);
        }

        let container: ModelElement | undefined;
        let containmentReferenceName = '';

        for (const candidate of candidateContainers) {
            const containmentReference = findContainmentReference(bootstrap.descriptor, candidate.classId, entry.classId);

            if (containmentReference !== undefined) {
                container = candidate;
                containmentReferenceName = containmentReference.name;
                break;
            }
        }

        if (container === undefined) {
            setError('No compatible containment reference was found for ' + entry.className);
            return;
        }

        try {
            const previousIds = new Set(document.elements.map(element => element.id));
            const updatedDocument = await addModelElement(document.id, container.id, containmentReferenceName, entry.classId);
            const createdElement = updatedDocument.elements.find(element => !previousIds.has(element.id));
            const representationOverrides: Record<string, string> = {};

            if (createdElement !== undefined && nodeStyleId !== undefined) {
                representationOverrides[createdElement.id] = nodeStyleId;
            }

            updateDocument(updatedDocument, representationOverrides);
            if (createdElement !== undefined && position !== undefined) {
                setNodes(current => current.map(node => node.id === createdElement.id ? { ...node, position } : node));
            }
            setDiagramDirty(true);
            if (createdElement !== undefined) {
                setSelectedElementId(createdElement.id);
            }

            setError('');
        } catch (exception) {
            handleError(exception, 'Could not add element');
        }
    }

    /**
     * Crea una referencia semántica desde una conexión visual.
     *
     * @param connection conexión creada en React Flow.
     * @returns promesa sin resultado.
     */
    async function connectSemanticElements(connection: Connection): Promise<void> {
        if (bootstrap === null || document === null || connection.source === null || connection.target === null) {
            return;
        }

        const sourceElement = findModelElement(document, connection.source);
        const targetElement = findModelElement(document, connection.target);

        if (sourceElement === undefined || targetElement === undefined) {
            setError('Connection elements were not found');
            return;
        }

        const reference = findConnectionReference(bootstrap.descriptor, sourceElement.classId, targetElement.classId);

        if (reference === undefined) {
            setError('No compatible semantic reference was found');
            return;
        }

        try {
            updateDocument(await setModelReference(document.id, sourceElement.id, reference.name, targetElement.id));
            setDiagramDirty(true);
            setError('');
        } catch (exception) {
            handleError(exception, 'Could not create reference');
        }
    }

    /**
     * Modifica un atributo del elemento seleccionado.
     *
     * @param attributeName nombre del atributo.
     * @param value valor serializado.
     * @returns promesa sin resultado.
     */
    async function applyAttribute(attributeName: string, value: string): Promise<void> {
        if (document === null || selectedElementId === '') {
            return;
        }

        try {
            updateDocument(await setModelAttribute(document.id, selectedElementId, attributeName, value));
            setError('');
        } catch (exception) {
            handleError(exception, 'Could not update attribute');
        }
    }

    /**
     * Guarda el documento actual.
     *
     * @returns promesa sin resultado.
     */
    async function saveCurrentModel(onStage?: (stage: 'PERSIST' | 'REFRESH' | 'COMPLETE') => void): Promise<void> {
        if (document === null) return;
        try {
            onStage?.('PERSIST');
            const saved = await saveModel(document.id);
            updateDocument(saved);
            setMessage('Model saved.');
            onStage?.('REFRESH');
            try {
                await refreshSavedModels();
                setError('');
            } catch {
                // Persistence already succeeded. A refresh failure must not be reported as a failed save.
                setError('The model was saved, but the saved model list could not be refreshed. Use Reload to synchronize the list.');
            }
            onStage?.('COMPLETE');
        } catch (exception) {
            handleError(exception, 'Could not save model');
        }
    }

    function applyOperationDocument(updatedDocument: ModelDocument): void {
        const changed = document !== null && (updatedDocument.elements.length !== document.elements.length || updatedDocument.references.length !== document.references.length || updatedDocument.dirty !== document.dirty);
        updateDocument(updatedDocument);
        if (changed) setDiagramDirty(true);
        if (!updatedDocument.elements.some(element => element.id === selectedElementId)) setSelectedElementId(updatedDocument.rootElementId);
    }


    /** Sincroniza marcadores de Problems con los nodos del modelo. */
    function applyProblemsReport(report: ModelValidationReport | null): void {
        const next: Record<string, ValidationSeverity> = {};
        const priority: Record<ValidationSeverity, number> = { INFO: 1, WARNING: 2, ERROR: 3 };
        for(const diagnostic of report?.diagnostics ?? []) {
            const elementId = diagnostic.location?.modelElementId ?? '';
            if(elementId === '') continue;
            const current = next[elementId];
            if(current === undefined || priority[diagnostic.severity] > priority[current]) next[elementId] = diagnostic.severity;
        }
        setProblemSeverityByElementId(next);
        setNodes(current => current.map(node => ({ ...node, data: { ...node.data, problemSeverity: next[node.id] } })));
    }

    /** Actualiza el modelo y reconstruye su proyección visual de forma indexada. */
    function updateDocument(updatedDocument: ModelDocument, representationOverrides: Record<string, string> = {}, diagram: DiagramDocument | null = null, notationOverride: NotationDefinition | null = notation): void {
        const severityByElementId = document?.id === updatedDocument.id ? problemSeverityByElementId : {};
        if (document?.id !== updatedDocument.id) setProblemSeverityByElementId({});
        setDocument(updatedDocument);
        setNodes(currentNodes => {
            const graph = buildModelEditorGraph(updatedDocument, currentNodes, representationByElementId, representationOverrides, diagram, notationOverride, severityByElementId);
            setRepresentationByElementId(graph.representations); setEdges(graph.edges); return graph.nodes;
        });
    }

    /**
     * Carga y aplica la notación seleccionada.
     *
     * @param notationId identifica la notación.
     * @returns promesa sin resultado.
     */
    async function loadSelectedNotation(notationId: string): Promise<void> {
        try {
            const loadedNotation = await loadNotation(notationId);
            setNotation(loadedNotation);

            if (document !== null) {
                updateDocument(document, {}, null, loadedNotation);
            }

            setError('');
        } catch (exception) {
            handleError(exception, 'Could not load notation');
        }
    }

    /**
     * Recarga los Ecore existentes en data/metamodels.
     *
     * @returns promesa sin resultado.
     */
    async function reloadMetamodels(): Promise<void> {
        try {
            const loadedMetamodels = await reloadMetamodelDirectory();
            setMetamodels(loadedMetamodels);

            if (loadedMetamodels.length > 0 && selectedMetamodelId === '') {
                setSelectedMetamodelId(loadedMetamodels[0].id);
            }

            setError('');
        } catch (exception) {
            handleError(exception, 'Could not reload metamodel directory');
        }
    }

    /**
     * Aplica cambios visuales y detecta movimientos o redimensionamientos.
     *
     * @param changes contiene los cambios producidos por React Flow.
     *
     * Salida:
     * No devuelve ningún valor.
     */
    function handleNodesChange(changes: NodeChange<Node>[]): void {
        applyNodeChanges(changes);

        if (hasHydraGeometryChange(changes)) {
            setDiagramDirty(true);
        }
    }

    /**
     * Guarda posiciones, tamaños y estilos del diagrama actual.
     *
     * @returns promesa sin resultado.
     */
    async function saveCurrentDiagram(): Promise<void> {
        if (document === null || selectedNotationId === '') {
            return;
        }

        try {
            const diagram = buildDiagramDocument(document.fileName, selectedNotationId, nodes, edges);
            await saveDiagram(diagram);
            setDiagramDirty(false);
            setError('');
        } catch (exception) {
            handleError(exception, 'Could not save diagram');
        }
    }

    /**
     * Carga posiciones, tamaños y representaciones guardadas.
     *
     * @returns promesa sin resultado.
     */
    async function loadCurrentDiagram(): Promise<void> {
        if (document === null) {
            return;
        }

        try {
            const diagram = await loadDiagram(document.fileName);
            let activeNotation = notation;

            if (activeNotation === null || activeNotation.id !== diagram.notationId) {
                activeNotation = await loadNotation(diagram.notationId);
                setNotation(activeNotation);
                setSelectedNotationId(activeNotation.id);
            }

            updateDocument(document, {}, diagram, activeNotation);
            setDiagramDirty(false);
            setError('');
        } catch (exception) {
            handleError(exception, 'Could not load diagram');
        }
    }

    /**
     * Abre un módulo de HYDRA y publica una ruta profunda estable.
     *
     * @param moduleId identifica el módulo solicitado.
     * @returns no devuelve ningún valor.
     */
    function navigate(moduleId: HydraModuleId): void {
        if (moduleId !== activeModule) {
            // Errors and success messages belong to the module that produced them.
            // Never carry a model/metamodel diagnostic into an unrelated capability.
            setError('');
            setMessage('');
        }
        setActiveModule(moduleId);
        const hash = moduleHash(moduleId);
        if(window.location.hash !== hash){window.location.hash = hash;}
        try { window.localStorage.setItem('hydra:last-module', moduleId); } catch { /* Navigation must work even when browser storage is disabled. */ }
    }

    async function handleMetamodelSaved(metamodelId: string): Promise<void> {
        try {
            const loadedMetamodels = await listMetamodels();
            setMetamodels(loadedMetamodels);
            setSelectedMetamodelId(metamodelId);
            setError('');
        } catch (exception) {
            handleError(exception, 'Could not refresh metamodels');
        }
    }


    /**
     * Guarda un mensaje de error sencillo.
     *
     * @param exception contiene el error recibido.
     * @param fallbackMessage contiene el mensaje alternativo.
     *
     * Salida:
     * No devuelve ningún valor.
     */
    function handleError(exception: unknown, fallbackMessage: string): void {
        setError(explainInputError(exception, fallbackMessage));
    }

    const initializeEffect = useEffectEvent(initialize);
    const loadBootstrapEffect = useEffectEvent(loadBootstrap);
    const loadNotationEffect = useEffectEvent(async (notationId: string): Promise<void> => {
        if (notationId === '') { setNotation(null); return; }
        await loadSelectedNotation(notationId);
    });

    useEffect(() => {
        // La inicialización se difiere a una microtarea para evitar renders encadenados.
        void Promise.resolve().then(() => initializeEffect());
    }, []);

    useEffect(() => {
        const onHashChange = (): void => {
            const nextModule = moduleFromHash(window.location.hash);
            setActiveModule(current => {
                if (current !== nextModule) {
                    setError('');
                    setMessage('');
                }
                return nextModule;
            });
        };
        window.addEventListener('hashchange', onHashChange);
        if(window.location.hash === ''){window.history.replaceState(null, '', moduleHash('HOME'));}
        return () => window.removeEventListener('hashchange', onHashChange);
    }, []);

    useEffect(() => {
        if (selectedMetamodelId !== '' && !selectedMetamodelId.startsWith('GRAMMAR:')) void Promise.resolve().then(() => loadBootstrapEffect(selectedMetamodelId));
    }, [selectedMetamodelId]);

    useEffect(() => {
        void Promise.resolve().then(() => loadNotationEffect(selectedNotationId));
    }, [selectedNotationId]);


    let workspaceContent: ReactNode;
    if (activeModule === 'HOME') {
        workspaceContent = <HydraHome metamodels={metamodels} selectedMetamodelId={selectedMetamodelId} onMetamodelChange={setSelectedMetamodelId} onNavigate={navigate} />;
    } else if (activeModule === 'MODEL') {
        workspaceContent = <ModelEditorWorkspace
            selectedMetamodelId={selectedMetamodelId} selectedSavedFileName={selectedSavedFileName} savedModels={savedModels}
            selectedNotationId={selectedNotationId} notations={notations} document={document} notation={notation} bootstrap={bootstrap}
            selectedElementId={selectedElementId} diagramDirty={diagramDirty} operationCatalog={operationCatalog} nodes={nodes} edges={edges}
            nodeTypes={nodeTypes} edgeTypes={edgeTypes} setSelectedSavedFileName={setSelectedSavedFileName} setSelectedNotationId={setSelectedNotationId}
            setSelectedElementId={setSelectedElementId} setDiagramDirty={setDiagramDirty} setError={setError} setMessage={setMessage}
            openCurrentSavedModel={openCurrentSavedModel} createSemanticModel={createSemanticModel} reloadMetamodels={reloadMetamodels}
            saveCurrentDiagram={saveCurrentDiagram} loadCurrentDiagram={loadCurrentDiagram} saveCurrentModel={saveCurrentModel}
            deleteSelectedSavedModel={deleteSelectedSavedModel} deleteSelectedElements={deleteSelectedElements} addSemanticElement={addSemanticElement} handleBeforeDelete={handleBeforeDelete}
            connectSemanticElements={connectSemanticElements} onEdgesChange={onEdgesChange} handleNodesChange={handleNodesChange}
            applyAttribute={applyAttribute} applyOperationDocument={applyOperationDocument} applyProblemsReport={applyProblemsReport}
        />;
    } else {
        workspaceContent = <HydraCapabilityModule activeModule={activeModule} metamodelId={selectedMetamodelId}
            onMetamodelSaved={metamodelId => void handleMetamodelSaved(metamodelId)}
            onUpdateSiteImported={metamodelId => { void initialize(metamodelId); }}
            onNavigate={navigate} onError={setError} />;
    }


    const modelToolbar = undefined;

    const projectScoped = ['HOME', 'MODEL', 'TEXTUAL_MODELS', 'METAMODEL', 'GRAMMARS', 'VALIDATIONS', 'QUICK_FIXES', 'EVENTS', 'EVENT_FLOWS', 'GAMIFICATION', 'EDITORS', 'INTEGRATIONS', 'REPRESENTABLE_IMPORTS'].includes(activeModule);
    const lazyAwareContent = <Suspense fallback={<HydraModuleLoading moduleTitle={moduleDefinition(activeModule).title} />}>
        {workspaceContent}
    </Suspense>;
    const presentedContent = projectScoped
        ? <ProjectExplorerWorkspace onWorkspaceReload={initialize} workbenchId={activeModule}>{lazyAwareContent}</ProjectExplorerWorkspace>
        : lazyAwareContent;

    return <>
        <HydraApplicationShell activeModule={activeModule} metamodels={metamodels} selectedMetamodelId={selectedMetamodelId}
            status={<>{document !== null && activeModule === 'MODEL' && <span>{document.id}{document.dirty && ' *'}</span>}{diagramDirty && activeModule === 'MODEL' && <span>Layout modified</span>}</>}
            toolbar={modelToolbar} onMetamodelChange={setSelectedMetamodelId} onNavigate={navigate} onOpenSettings={() => setSettingsOpen(true)}>
            {error !== '' && (activeModule === 'MODEL' || activeModule === 'METAMODEL') && <HydraActionableErrorNotice message={error} context={activeModule === 'METAMODEL' ? 'metamodel designer' : 'model editor'} onDismiss={() => setError('')} />}
            {message !== '' && error === '' && <div className="status-message">{message}</div>}
            {presentedContent}
        </HydraApplicationShell>
        <SettingsDialog open={settingsOpen} onClose={() => setSettingsOpen(false)} />
    </>;
}

export default App;
