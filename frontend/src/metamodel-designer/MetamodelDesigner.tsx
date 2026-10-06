import { explainInputError } from '../api/inputErrorExplanation';
import type { Connection } from '@xyflow/react';
import { lazy, Suspense, useCallback, useEffect, useMemo, useRef, useState } from 'react';
import type { CSSProperties, KeyboardEvent as ReactKeyboardEvent, PointerEvent as ReactPointerEvent } from 'react';
import { HydraActionableErrorNotice, HydraButton, HydraCreationModeSwitch, HydraErrorBoundary, HydraGuidedWorkflow } from '../design-system';
import type { HydraCreationMode, HydraGuidedStep } from '../design-system';
import { HydraModuleLoading } from '../application-shell/HydraModuleLoading';
import { HydraOperationProgress } from '../design-system/HydraOperationProgress';
import { useBoundedDesignerHistory } from '../designer-ui/useBoundedDesignerHistory';
import '../visual-style/visual-style.css';
import './metamodel-designer.css';
import { createMetaModel, deleteMetamodel, listCanonicalMetamodels, listMetamodelExportFormats, loadMetaModel, loadMetamodelDeletionImpact, loadMetamodelDiagram, reloadNotationDirectory, replaceMetaModel, saveMetamodelDiagram } from './api';
import { deleteMetamodelElementsInBatch, deleteMetamodelRelationsInBatch } from './batchDeletion';
import { createsSuperTypeCycle, validateAndNormalizeConnection } from './connectionValidation';
import { findMetamodelDataTypeError } from './dataTypeUtils';
import { createEmptyMetamodelDiagram } from './diagram';
import { findMetamodelEnumError } from './enumUtils';
import { listMetamodelImportFormats } from './importApi';
import { layoutMetamodelDiagram } from './layoutApi';
import { MetamodelCanvas } from './MetamodelCanvas';
import { MetamodelExportDialog } from './MetamodelExportDialog';
import { MetamodelImportDialog } from './MetamodelImportDialog';
import { MetamodelGuidedReadyNotice } from './MetamodelGuidedReadyNotice';
import { MetamodelLibraryDialog, type MetamodelBatchDeleteResult } from './MetamodelLibraryDialog';
import { useHydraLanguage } from '../i18n';
import { createInitialMetaModel, createMetaClass, createMetaDataType, createMetaEnum, createMetaModelId, createMetaRelation } from './metaModelFactory';
import { createUniqueRelationName, removeClass, removeDataType, removeEnum, removeRelation } from './metaModelOperations';
import { MetamodelPalette } from './MetamodelPalette';
import { limitPaletteWidth } from './paletteLayout';
import { MetamodelPropertyPanel } from './MetamodelPropertyPanel';
import type { CanonicalMetamodelSummary, MetaModel, MetamodelConnectionKind, MetamodelDiagramDocument, MetamodelExportFormatDescriptor, MetamodelSelection } from './types';
import type { MetamodelImportFormatDescriptor, MetamodelImportPreview, MetamodelImportSaveResult } from './importTypes';
import type { MetamodelLayoutDirection, MetamodelLayoutOptions } from './layoutTypes';
import { backendFetch } from '../api/backendFetch';
import { metamodelElkLayout } from './metamodelElkLayout';
import { MetamodelDesignerCommandBar, type MetamodelDesignerMode } from './MetamodelDesignerCommandBar';
import { useHydraOverwriteConfirmation } from '../hooks/useHydraOverwriteConfirmation';


const LazyValidationDesigner = lazy(() => import('../validation-designer/ValidationDesigner')
    .then(module => ({ default: module.ValidationDesigner })));
const LazyEventDesigner = lazy(() => import('../event-designer/EventDesigner')
    .then(module => ({ default: module.EventDesigner })));
const LazyGamificationDesigner = lazy(() => import('../gamification-designer/GamificationDesigner')
    .then(module => ({ default: module.GamificationDesigner })));
const LazyEditorDefinitionDesigner = lazy(() => import('../editor-definition/EditorDefinitionDesigner')
    .then(module => ({ default: module.EditorDefinitionDesigner })));
const LazyIntegrationDesigner = lazy(() => import('../integration-designer/IntegrationDesigner')
    .then(module => ({ default: module.IntegrationDesigner })));

export type MetamodelDesignerSection = MetamodelDesignerMode;

interface MetamodelDesignerProps {
    onSaved: (metamodelId: string) => void;
    initialSection?: MetamodelDesignerSection;
    sectionLocked?: boolean;
}

export function MetamodelDesigner({ onSaved, initialSection = 'STRUCTURE', sectionLocked = false }: MetamodelDesignerProps) {
    const { t } = useHydraLanguage();
    const initial = createInitialMetaModel();
    const [metamodels, setMetamodels] = useState<CanonicalMetamodelSummary[]>([]);
    const [selectedMetamodelId, setSelectedMetamodelId] = useState('');
    const [draft, setDraft] = useState<MetaModel>(initial);
    const [etag, setEtag] = useState('');
    const [savedMetamodelSnapshot, setSavedMetamodelSnapshot] = useState('');
    const [diagram, setDiagram] = useState<MetamodelDiagramDocument>(() => createEmptyMetamodelDiagram(initial.id));
    const [selection, setSelection] = useState<MetamodelSelection>({ kind: 'MODEL', id: initial.id });
    const [connectionKind, setConnectionKind] = useState<MetamodelConnectionKind | null>(null);
    const [connectionSourceClassId, setConnectionSourceClassId] = useState('');
    const [error, setError] = useState('');
    const [message, setMessage] = useState('');
    const [loading, setLoading] = useState(false);
    const [semanticSaving, setSemanticSaving] = useState(false);
    const [diagramSaving, setDiagramSaving] = useState(false);
    const [preparingExport, setPreparingExport] = useState(false);
    const [paletteWidth, setPaletteWidth] = useState(230);
    const [exportFormats, setExportFormats] = useState<MetamodelExportFormatDescriptor[]>([]);
    const [showExportDialog, setShowExportDialog] = useState(false);
    const [importFormats, setImportFormats] = useState<MetamodelImportFormatDescriptor[]>([]);
    const [showImportDialog, setShowImportDialog] = useState(false);
    const [showMetamodelLibrary, setShowMetamodelLibrary] = useState(false);
    const [libraryInitialSelectedIds, setLibraryInitialSelectedIds] = useState<string[]>([]);
    const [layoutDirection, setLayoutDirection] = useState<MetamodelLayoutDirection>('LEFT_TO_RIGHT');
    const [focusSelected, setFocusSelected] = useState(false);
    const [hideDependencies, setHideDependencies] = useState(false);
    const [designerMode, setDesignerMode] = useState<MetamodelDesignerSection>(initialSection);
    const [structureCreationMode, setStructureCreationMode] = useState<HydraCreationMode>('GUIDED');
    const [structureGuidedStep, setStructureGuidedStep] = useState('identity');
    const { confirmOverwrite, overwriteDialogs } = useHydraOverwriteConfirmation({
        current: [{ layer: 'Metamodel', entityKind: 'METAMODEL', stableId: draft.id, uniqueName: draft.name, displayLabel: draft.name || draft.id }],
        persisted: metamodels.map(item => ({ layer: 'Metamodel', entityKind: 'METAMODEL', stableId: item.id, uniqueName: item.name, displayLabel: item.name || item.id }))
    });
    const draftSnapshot = useMemo(() => JSON.stringify(draft), [draft]);
    const draftPersisted = etag !== '' && selectedMetamodelId === draft.id && savedMetamodelSnapshot === draftSnapshot;
    const persistedDraftId = draftPersisted ? draft.id : '';
    const pendingDiagramSaveRef = useRef<Promise<boolean> | null>(null);
    const history = useBoundedDesignerHistory(captureHistorySnapshot, restoreHistorySnapshot, 50);

    function captureHistorySnapshot(): string { return JSON.stringify({ draft, diagram, selection }); }
    function restoreHistorySnapshot(snapshot: string): void {
        const restored = JSON.parse(snapshot) as { draft: MetaModel; diagram: MetamodelDiagramDocument; selection: MetamodelSelection };
        setDraft(restored.draft); setDiagram(restored.diagram); setSelection(restored.selection);
        setConnectionKind(null); setConnectionSourceClassId(''); setError(''); setMessage('');
    }
    function rememberHistory(): void { history.remember(); }
    function updateDraft(next: MetaModel): void { rememberHistory(); setDraft(next); }
    function updateDiagram(next: MetamodelDiagramDocument): void { rememberHistory(); setDiagram(next); }


    const handleValidationDesignerError = useCallback((messageValue: string): void => {
        setMessage('');
        setError(messageValue);
    }, []);
    const handleValidationDesignerMessage = useCallback((messageValue: string): void => {
        setError('');
        setMessage(messageValue);
    }, []);

    function handleError(exception: unknown, fallback: string): void { setMessage(''); setError(explainInputError(exception, fallback, 'Metamodel Designer')); }

    async function refreshMetamodels(): Promise<void> { setMetamodels(await listCanonicalMetamodels()); }

    useEffect(() => {
        let active = true;
        void Promise.all([listCanonicalMetamodels(), listMetamodelImportFormats()])
            .then(([models, formats]) => {
                if(!active) return;
                setMetamodels(models);
                setImportFormats(formats);
                if(models.length > 0) setSelectedMetamodelId(models[0].id);
                setError('');
            })
            .catch(exception => {
                if(!active) return;
                setMessage('');
                setError(explainInputError(exception, 'Could not initialize metamodel designer', 'Metamodel Designer'));
            });
        return () => { active = false; };
    }, []);

    useEffect(() => {
        if (designerMode !== 'STRUCTURE') return undefined;
        function handleHistoryShortcut(event: KeyboardEvent): void {
            if (!(event.ctrlKey || event.metaKey) || event.altKey) return;
            const key = event.key.toLowerCase();
            if (key === 'z') { event.preventDefault(); if (event.shiftKey) history.redo(); else history.undo(); return; }
            if (key === 'y') { event.preventDefault(); history.redo(); }
        }
        window.addEventListener('keydown', handleHistoryShortcut);
        return () => window.removeEventListener('keydown', handleHistoryShortcut);
    }, [designerMode, history]);

    function createNewMetamodel(): void {
        const model = createInitialMetaModel();
        history.clear();
        setDraft(model); setEtag(''); setSavedMetamodelSnapshot(''); setDiagram(createEmptyMetamodelDiagram(model.id)); setSelection({ kind: 'MODEL', id: model.id });
        setConnectionKind(null); setConnectionSourceClassId(''); setSelectedMetamodelId(''); setMessage('New metamodel created'); setError('');
    }

    async function openSelectedMetamodel(): Promise<void> {
        if (selectedMetamodelId === '') { setError('Select a metamodel to open'); return; }
        setLoading(true);
        try {
            const loaded = await loadMetaModel(selectedMetamodelId);
            let loadedDiagram = createEmptyMetamodelDiagram(loaded.metamodel.id);
            try { loadedDiagram = await loadMetamodelDiagram(loaded.metamodel.id); } catch { /* a missing visual document is valid */ }
            history.clear();
            setDraft(loaded.metamodel); setEtag(loaded.etag); setSavedMetamodelSnapshot(JSON.stringify(loaded.metamodel)); setDiagram({ ...loadedDiagram, metamodelId: loaded.metamodel.id });
            setSelection({ kind: 'MODEL', id: loaded.metamodel.id }); setConnectionKind(null); setConnectionSourceClassId('');
            setMessage(`Opened ${loaded.metamodel.name}`); setError(''); onSaved(loaded.metamodel.id);
        } catch (exception) { handleError(exception, 'Could not open metamodel'); } finally { setLoading(false); }
    }

    function schedulePostSaveWork(metamodelId: string, metamodelName: string, savedDiagram: MetamodelDiagramDocument): Promise<boolean> {
        setDiagramSaving(true);
        const previousDiagramTask = pendingDiagramSaveRef.current ?? Promise.resolve(true);
        const diagramTask = previousDiagramTask.then(() => saveMetamodelDiagram(metamodelId, savedDiagram)
            .then(() => {
                if (pendingDiagramSaveRef.current === diagramTask) { setMessage(`Saved ${metamodelName}`); setError(''); }
                return true;
            })
            .catch(diagramException => {
                if (pendingDiagramSaveRef.current === diagramTask) {
                    setMessage(`${t('Metamodel saved')}: ${metamodelName}. ${t('The semantic metamodel is safe, but its visual layout could not be saved.')}`);
                    setError(explainInputError(diagramException, t('Could not save the metamodel layout'), 'Metamodel Designer'));
                }
                return false;
            }));
        pendingDiagramSaveRef.current = diagramTask;
        void diagramTask.finally(() => {
            if (pendingDiagramSaveRef.current === diagramTask) { pendingDiagramSaveRef.current = null; setDiagramSaving(false); }
        });
        void refreshMetamodels().catch(exception => {
            setError(current => current !== '' ? current : explainInputError(exception, t('Could not refresh saved metamodels'), 'Metamodel Designer'));
        });
        return diagramTask;
    }

    async function persistCurrentMetamodel(waitForDiagram = false): Promise<{ id: string; diagramSaved?: boolean } | null> {
        const enumError = findMetamodelEnumError(draft);
        const dataTypeError = findMetamodelDataTypeError(draft);
        if (enumError !== '' || dataTypeError !== '') { setError(enumError || dataTypeError); setMessage(''); return null; }
        try {
            const result = etag === '' ? await createMetaModel(draft) : await replaceMetaModel(draft, etag);
            const savedDiagram = { ...diagram, metamodelId: result.metamodel.id };
            setDraft(result.metamodel); setEtag(result.etag); setSavedMetamodelSnapshot(JSON.stringify(result.metamodel)); setSelectedMetamodelId(result.metamodel.id);
            setMetamodels(current => {
                const summary: CanonicalMetamodelSummary = { id: result.metamodel.id, name: result.metamodel.name, schemaVersion: result.metamodel.schemaVersion, etag: result.etag };
                return [...current.filter(item => item.id !== summary.id), summary].sort((left, right) => left.name.localeCompare(right.name));
            });
            onSaved(result.metamodel.id);
            setMessage(`${t('Metamodel saved')}: ${result.metamodel.name}. ${t('Saving the visual layout in the background…')}`);
            setError('');
            const diagramTask = schedulePostSaveWork(result.metamodel.id, result.metamodel.name, savedDiagram);
            return { id: result.metamodel.id, diagramSaved: waitForDiagram ? await diagramTask : undefined };
        } catch (exception) { handleError(exception, 'Could not save metamodel'); return null; }
    }

    async function saveCurrentMetamodel(): Promise<boolean> {
        if (!await confirmOverwrite()) return false;
        setLoading(true); setSemanticSaving(true);
        try { return await persistCurrentMetamodel(false) !== null; }
        finally { setSemanticSaving(false); setLoading(false); }
    }

    async function requestMetamodelDeletion(): Promise<void> {
        if (persistedDraftId === '') { setError(t('Save or open the metamodel before deleting it')); return; }
        setLibraryInitialSelectedIds([persistedDraftId]);
        setShowMetamodelLibrary(true);
        setError('');
    }

    async function cleanupDependentMetamodelArtifacts(metamodelId: string, deleteNotations: boolean, deleteExports: boolean): Promise<void> {
        const parameters = new URLSearchParams({ deleteNotations: String(deleteNotations), deleteExports: String(deleteExports) });
        const response = await backendFetch(`/api/metamodels/${encodeURIComponent(metamodelId)}/dependent-artifacts:cleanup?${parameters.toString()}`, { method: 'POST' });
        if (response.status === 404) return;
        if (!response.ok) {
            const message = (await response.text()).trim();
            throw new Error(message === '' ? 'Could not clean dependent metamodel artifacts' : message);
        }
    }

    async function openMetamodelFromLibrary(id: string): Promise<void> {
        setSelectedMetamodelId(id);
        setLoading(true);
        try {
            const loaded = await loadMetaModel(id);
            let loadedDiagram = createEmptyMetamodelDiagram(loaded.metamodel.id);
            try { loadedDiagram = await loadMetamodelDiagram(loaded.metamodel.id); } catch { /* optional visual document */ }
            history.clear();
            setDraft(loaded.metamodel); setEtag(loaded.etag); setSavedMetamodelSnapshot(JSON.stringify(loaded.metamodel)); setDiagram({ ...loadedDiagram, metamodelId: loaded.metamodel.id });
            setSelection({ kind:'MODEL', id:loaded.metamodel.id }); setConnectionKind(null); setConnectionSourceClassId('');
            setShowMetamodelLibrary(false); setMessage(`${t('Opened')} ${loaded.metamodel.name}`); setError(''); onSaved(loaded.metamodel.id);
        } catch (exception) { handleError(exception, t('Could not open metamodel')); } finally { setLoading(false); }
    }

    async function deleteMetamodelsFromLibrary(ids: string[], options: { deleteNotations: boolean; deleteExports: boolean }): Promise<MetamodelBatchDeleteResult> {
        const uniqueIds = [...new Set(ids)];
        if (uniqueIds.length === 0) return { deletedIds: [], failed: {} };
        setLoading(true);
        let notationRemoved = false;
        const deletedIds: string[] = [];
        const failures: Record<string,string> = {};
        const warnings: Record<string,string> = {};
        try {
            for (const id of uniqueIds) {
                try {
                    const impact = await loadMetamodelDeletionImpact(id);
                    let expectedEtag = id === draft.id ? etag : (metamodels.find(item => item.id === id)?.etag ?? '');
                    if (expectedEtag === '') {
                        try { expectedEtag = (await loadMetaModel(id)).etag; } catch { /* lifecycle endpoint still performs existence checks */ }
                    }
                    // The ETag-protected lifecycle deletion must happen before any best-effort cleanup.
                    // A concurrency conflict therefore cannot remove derived files from a metamodel that still exists.
                    const deletion = await deleteMetamodel(id, options.deleteNotations, options.deleteExports, expectedEtag);
                    deletedIds.push(id);
                    notationRemoved = notationRemoved || (options.deleteNotations && (impact.notationFiles?.length ?? 0) > 0);
                    const pendingFiles = deletion.pendingFiles ?? [];
                    if (pendingFiles.length > 0) {
                        warnings[id] = `${t('Some dependent files remain and can be cleaned safely without restoring the metamodel.')}: ${pendingFiles.join(', ')}`;
                    }
                    try { await cleanupDependentMetamodelArtifacts(id, options.deleteNotations, options.deleteExports); }
                    catch (cleanupException) {
                        const message = cleanupException instanceof Error ? cleanupException.message : t('Residual dependent artifacts could not be cleaned.');
                        warnings[id] = warnings[id] === undefined ? message : `${warnings[id]} · ${message}`;
                    }
                } catch (exception) {
                    failures[id] = exception instanceof Error ? exception.message : t('Could not delete metamodel');
                }
            }
            if (notationRemoved) {
                try { await reloadNotationDirectory(); }
                catch (exception) { warnings.notations = exception instanceof Error ? exception.message : t('Could not refresh visual representations.'); }
            }
            let remaining = metamodels.filter(item => !deletedIds.includes(item.id));
            try { remaining = await listCanonicalMetamodels(); }
            catch (exception) { warnings.refresh = exception instanceof Error ? exception.message : t('Could not refresh saved metamodels'); }
            setMetamodels(remaining);
            const removedCurrent = deletedIds.includes(draft.id) || deletedIds.includes(selectedMetamodelId);
            if (removedCurrent) {
                const next = remaining[0];
                if (next !== undefined) {
                    try {
                        const loaded = await loadMetaModel(next.id);
                        let loadedDiagram = createEmptyMetamodelDiagram(loaded.metamodel.id);
                        try { loadedDiagram = await loadMetamodelDiagram(loaded.metamodel.id); } catch { /* optional visual document */ }
                        history.clear();
                        setDraft(loaded.metamodel); setEtag(loaded.etag); setSavedMetamodelSnapshot(JSON.stringify(loaded.metamodel)); setDiagram({ ...loadedDiagram, metamodelId:loaded.metamodel.id }); setSelectedMetamodelId(next.id); setSelection({kind:'MODEL',id:next.id}); onSaved(next.id);
                    } catch (exception) { warnings.openNext = exception instanceof Error ? exception.message : t('Could not open the next saved metamodel.'); }
                } else {
                    const nextDraft = createInitialMetaModel();
                    history.clear();
                    setDraft(nextDraft); setEtag(''); setSavedMetamodelSnapshot(''); setDiagram(createEmptyMetamodelDiagram(nextDraft.id)); setSelectedMetamodelId(''); setSelection({kind:'MODEL',id:nextDraft.id}); onSaved('');
                }
            }
            if (Object.keys(failures).length === 0) {
                setMessage(deletedIds.length === 1 ? t('Metamodel deleted.') : `${deletedIds.length} ${t('metamodels deleted.')}`);
                setError(Object.keys(warnings).length === 0 ? '' : `${t('Deletion completed with cleanup warnings')}: ${Object.values(warnings).join(' · ')}`);
            } else {
                setMessage(deletedIds.length === 0 ? '' : `${deletedIds.length} ${t('metamodels deleted.')}`);
                setError(`${t('Some metamodels could not be deleted.')}: ${Object.entries(failures).map(([id,message]) => `${id}: ${message}`).join(' · ')}`);
            }
            return { deletedIds, failed: failures, warnings };
        } finally { setLoading(false); }
    }

    function loadImportedPreview(preview: MetamodelImportPreview): void {
        history.clear();
        setDraft(preview.metamodel); setEtag(''); setSavedMetamodelSnapshot(''); setDiagram({ ...preview.diagram, metamodelId: preview.metamodel.id });
        setSelection({ kind: 'MODEL', id: preview.metamodel.id }); setSelectedMetamodelId(''); setConnectionKind(null); setConnectionSourceClassId('');
        setShowImportDialog(false); setMessage(`Imported ${preview.metamodel.name} into the designer. Save to persist it.`); setError('');
    }
    async function loadSavedImport(result: MetamodelImportSaveResult): Promise<void> {
        setShowImportDialog(false);
        try {
            const loaded = await loadMetaModel(result.id);
            history.clear();
            setDraft(loaded.metamodel); setEtag(loaded.etag || result.etag); setSavedMetamodelSnapshot(JSON.stringify(loaded.metamodel)); setDiagram({ ...result.preview.diagram, metamodelId: result.id });
            setSelectedMetamodelId(result.id); setSelection({ kind: 'MODEL', id: result.id }); await refreshMetamodels();
            setMessage(`Imported and saved ${loaded.metamodel.name}`); setError(''); onSaved(result.id);
        } catch (exception) { handleError(exception, 'Import was saved but could not be reopened'); }
    }
    async function openExportDialog(): Promise<void> {
        setError('');
        setLoading(true); setPreparingExport(true);
        try {
            let metamodelId = persistedDraftId;
            if (metamodelId === '') {
                const persisted = await persistCurrentMetamodel(true);
                if (persisted === null) return;
                metamodelId = persisted.id;
            } else if (pendingDiagramSaveRef.current !== null) {
                await pendingDiagramSaveRef.current;
            }
            setExportFormats(await listMetamodelExportFormats(metamodelId));
            setShowExportDialog(true);
        } catch (exception) { handleError(exception, 'Could not load export formats'); } finally { setPreparingExport(false); setLoading(false); }
    }

    function createLayoutOptions(mode: 'FULL' | 'ROUTE_ONLY'): MetamodelLayoutOptions {
        return { algorithm: 'layered', direction: layoutDirection, preserveNodeSizes: true, preserveStyles: true, useAttributeTypesForPlacement: true, fixedElementIds: focusSelected && selection.kind === 'CLASS' ? [selection.id] : [], mode, portAssignmentAlgorithm: 'distributed', edgeRoutingAlgorithm: 'orthogonal', labelPlacementAlgorithm: 'longest-segment' };
    }
    function applyElkStyleLayout(): void {
        if (diagram.nodes.length === 0) return;
        history.remember(); setDiagram(metamodelElkLayout(draft, diagram, layoutDirection));
        setMessage(t('ELK-style layout applied. Elements were separated using their actual dimensions.'));
    }

    async function applyLayout(mode: 'FULL' | 'ROUTE_ONLY'): Promise<void> {
        setLoading(true);
        try {
            const response = await layoutMetamodelDiagram({ metamodel: draft, diagram, options: createLayoutOptions(mode) });
            rememberHistory();
            setDiagram(response.diagram); setMessage(mode === 'FULL' ? `Layout applied: ${response.statistics.layers} layer(s)` : `Routing updated: ${response.statistics.edges} relation(s)`); setError('');
        } catch (exception) { handleError(exception, mode === 'FULL' ? 'Could not apply layout' : 'Could not route relations'); } finally { setLoading(false); }
    }

    function addClass(kind: 'CLASS' | 'ABSTRACT' | 'INTERFACE'): void {
        const name = createUniqueClassifierName(kind === 'INTERFACE' ? 'NewInterface' : kind === 'ABSTRACT' ? 'NewAbstractClass' : 'NewClass');
        const metaClass = createMetaClass(name, kind === 'INTERFACE' ? 'INTERFACE' : kind === 'ABSTRACT' ? 'ABSTRACT_CLASS' : 'CLASS');
        rememberHistory();
        setDraft(current => ({ ...current, classes: [...current.classes, metaClass] })); setSelection({ kind: 'CLASS', id: metaClass.id });
    }
    function addEnum(): void { const value = createMetaEnum(createUniqueClassifierName('NewEnum')); rememberHistory(); setDraft(current => ({ ...current, enums: [...current.enums, value] })); setSelection({ kind: 'ENUM', id: value.id }); }
    function addDataType(): void { const value = createMetaDataType(createUniqueClassifierName('NewDataType')); rememberHistory(); setDraft(current => ({ ...current, dataTypes: [...current.dataTypes, value] })); setSelection({ kind: 'DATA_TYPE', id: value.id }); }

    function createConnection(connection: Connection, kind: MetamodelConnectionKind): void {
        const validation = validateAndNormalizeConnection(draft, connection, kind);
        if (validation.valid === false) { setError(validation.error); return; }
        const { sourceClass, targetClass, connection: normalized } = validation.value;
        if ((kind === 'INHERITANCE' || kind === 'IMPLEMENTATION') && createsSuperTypeCycle(draft, sourceClass.id, targetClass.id)) { setError('This relationship would create an inheritance cycle'); return; }
        if (draft.relations.some(item => item.sourceTypeId === sourceClass.id && item.targetTypeId === targetClass.id && item.kind === kind)) { setError('The selected relationship already exists'); return; }
        const name = kind === 'INHERITANCE' || kind === 'IMPLEMENTATION' ? '' : kind === 'DEPENDENCY' ? 'dependsOn' : createUniqueRelationName(draft, sourceClass.id, targetClass.name);
        const relation = { ...createMetaRelation(sourceClass.id, targetClass.id, kind, name), id: createMetaModelId('relation') };
        rememberHistory();
        setDraft(current => ({ ...current, relations: [...current.relations, relation] }));
        setDiagram(current => ({ ...current, edges: [...current.edges.filter(edge => edge.relationId !== relation.id), { relationId: relation.id, sourceHandle: normalized.sourceHandle ?? '', targetHandle: normalized.targetHandle ?? '', kind, sourceClassId: sourceClass.id, targetClassId: targetClass.id, label: name, routePoints: [] }] }));
        setSelection({ kind: 'RELATION', id: relation.id }); setConnectionSourceClassId(''); setConnectionKind(null); setError(''); setMessage(`${kind} created`);
    }

    function deleteElement(id: string): void {
        let model = draft;
        if (model.classes.some(item => item.id === id)) {
            if (model.classes.length === 1) { setError('A metamodel must contain at least one class'); return; }
            model = removeClass(model, id);
        } else if (model.enums.some(item => item.id === id)) model = removeEnum(model, id);
        else if (model.dataTypes.some(item => item.id === id)) model = removeDataType(model, id);
        rememberHistory();
        setDraft(model); setDiagram(current => ({ ...current, nodes: current.nodes.filter(node => node.elementId !== id), edges: current.edges.filter(edge => model.relations.some(relation => relation.id === edge.relationId)) }));
        setSelection({ kind: 'MODEL', id: model.id });
    }
    function deleteElements(ids: string[]): void { const result = deleteMetamodelElementsInBatch(draft, diagram, ids); if (result.deletedElementIds.length === 0) return; rememberHistory(); setDraft(result.model); setDiagram(result.diagram); setSelection({ kind: 'MODEL', id: result.model.id }); setMessage(`Deleted ${result.deletedElementIds.length} element(s). ${preservedElementMessage(result.preservedReason)}`.trim()); }
    function deleteRelations(ids: string[]): void { const result = deleteMetamodelRelationsInBatch(draft, diagram, ids); if (result.deletedRelationIds.length === 0) return; rememberHistory(); setDraft(result.model); setDiagram(result.diagram); setSelection({ kind: 'MODEL', id: result.model.id }); setMessage(`Deleted ${result.deletedRelationIds.length} relation(s)`); }
    function deleteRelation(id: string): void { if (!draft.relations.some(item => item.id === id)) return; const model = removeRelation(draft, id); rememberHistory(); setDraft(model); setDiagram(current => ({ ...current, edges: current.edges.filter(edge => edge.relationId !== id) })); setSelection({ kind: 'MODEL', id: model.id }); }
    function createUniqueClassifierName(base: string): string { const names = new Set([...draft.classes, ...draft.enums, ...draft.dataTypes].map(item => item.name)); let value = base; let suffix = 2; while (names.has(value)) value = `${base}${suffix++}`; return value; }

    const classifierNames = [...draft.classes, ...draft.enums, ...draft.dataTypes].map(item => item.name.trim()).filter(Boolean);
    const classifierNamesUnique = new Set(classifierNames).size === classifierNames.length;
    const classifierIds = new Set([...draft.classes, ...draft.enums, ...draft.dataTypes].map(item => item.id));
    const relationEndpointsValid = draft.relations.every(item => classifierIds.has(item.sourceTypeId) && classifierIds.has(item.targetTypeId));
    const structuralMembersNamed = draft.classes.every(item => item.name.trim() !== ''
        && item.attributes.every(attribute => attribute.name.trim() !== '')
        && item.operations.every(operation => operation.name.trim() !== '' && operation.parameters.every(parameter => parameter.name.trim() !== '')));
    const enumErrorForGuide = findMetamodelEnumError(draft);
    const dataTypeErrorForGuide = findMetamodelDataTypeError(draft);
    const metamodelReadyToSave = draft.name.trim() !== '' && draft.classes.length > 0 && classifierNames.length === draft.classes.length + draft.enums.length + draft.dataTypes.length && classifierNamesUnique && structuralMembersNamed && relationEndpointsValid && enumErrorForGuide === '' && dataTypeErrorForGuide === '';
    const guidedStructureActive = designerMode === 'STRUCTURE' && structureCreationMode === 'GUIDED';
    const explicitSaveDisabled = loading || guidedStructureActive;
    const explicitExportDisabled = loading || (guidedStructureActive && !draftPersisted);
    const metamodelGuideSteps: readonly HydraGuidedStep[] = [
        {
            id: 'identity',
            title: 'Identity',
            description: 'Name the metamodel before defining its structure.',
            checks: [
                { id: 'name', label: 'Metamodel name is defined', complete: draft.name.trim() !== '', detail: 'Use a stable, meaningful domain name. The technical identifier is managed separately by HYDRA.' }
            ],
            content: <div className="metamodel-guided-card">
                <label>Metamodel name *<input aria-label="Guided metamodel name" value={draft.name} onChange={event => { rememberHistory(); setDraft(current => ({ ...current, name: event.target.value })); }} /></label>
                <p>This is the main technology-neutral model. The visual/manual editor and every generated projection use this same document.</p>
            </div>
        },
        {
            id: 'classifiers',
            title: 'Classifiers',
            description: 'Create the concepts that make up the domain. A metamodel needs at least one class.',
            checks: [
                { id: 'class', label: 'At least one class exists', complete: draft.classes.length > 0, detail: `${draft.classes.length} class(es), ${draft.enums.length} enum(s), ${draft.dataTypes.length} datatype(s).` },
                { id: 'classifierNames', label: 'Classifier names are present and unique', complete: classifierNames.length === draft.classes.length + draft.enums.length + draft.dataTypes.length && classifierNamesUnique, detail: 'Classes, interfaces, enumerations and datatypes share the same naming space.' }
            ],
            content: <div className="metamodel-guided-card">
                <div className="metamodel-guided-actions">
                    <HydraButton size="small" onClick={() => addClass('CLASS')}>+ Class</HydraButton>
                    <HydraButton size="small" onClick={() => addClass('ABSTRACT')}>+ Abstract class</HydraButton>
                    <HydraButton size="small" onClick={() => addClass('INTERFACE')}>+ Interface</HydraButton>
                    <HydraButton size="small" onClick={addEnum}>+ Enumeration</HydraButton>
                    <HydraButton size="small" onClick={addDataType}>+ Datatype</HydraButton>
                </div>
                <p>Use the visual/manual editor to rename classifiers, define attributes and operations, or arrange them on the canvas. Nothing created here uses a separate representation.</p>
                <HydraButton size="small" variant="quiet" onClick={() => setStructureCreationMode('ADVANCED')}>Open visual / manual structure editor</HydraButton>
            </div>
        },
        {
            id: 'structure',
            title: 'Properties and relationships',
            description: 'Complete attributes, operations and relations only when your domain needs them.',
            checks: [
                { id: 'members', label: 'Defined members have valid names', complete: structuralMembersNamed, detail: 'Empty attributes, operations or parameter names must be completed before proceeding.' },
                { id: 'relations', label: 'Every relation references existing classifiers', complete: relationEndpointsValid, detail: `${draft.relations.length} relation(s) currently defined. Relations are optional.` }
            ],
            content: <div className="metamodel-guided-card">
                <p>Relationships are deliberately not mandatory: a valid metamodel may contain independent concepts. Use the visual canvas when you need references, containment, aggregation, dependencies, inheritance or interface implementation.</p>
                <HydraButton size="small" variant="quiet" onClick={() => setStructureCreationMode('ADVANCED')}>Edit properties and relations visually</HydraButton>
            </div>
        },
        {
            id: 'validation',
            title: 'Structural review',
            description: 'HYDRA checks the neutral structure before persistence or generation.',
            checks: [
                { id: 'enum', label: 'Enumerations are structurally valid', complete: enumErrorForGuide === '', detail: enumErrorForGuide || 'Enumeration names, literals and values are valid.' },
                { id: 'datatype', label: 'Datatypes are structurally valid', complete: dataTypeErrorForGuide === '', detail: dataTypeErrorForGuide || 'Datatype declarations are valid.' },
                { id: 'names', label: 'Classifier names remain unique', complete: classifierNamesUnique, detail: 'This prevents ambiguous projections and generated artifacts.' }
            ],
            content: <div className="metamodel-guided-card"><p>Advanced technology-specific details stay in their existing editors and exporters. This review verifies the main neutral model and never removes unsupported information.</p></div>
        },
        {
            id: 'save',
            title: 'Save metamodel',
            description: 'Persist the same metamodel used by the guided and visual/manual workflows.',
            checks: [
                { id: 'ready', label: 'Metamodel is ready to save', complete: metamodelReadyToSave, detail: etag === '' ? 'This will create a new saved metamodel.' : 'This will update the currently opened metamodel.' }
            ],
            content: <div className="metamodel-guided-card"><p>Saving stores both the semantic model and its visual diagram. You can continue later in Guided or Visual / manual mode without conversion.</p></div>
        }
    ];

    function startPaletteResize(event: ReactPointerEvent<HTMLDivElement>): void {
        const startX = event.clientX; const startWidth = paletteWidth;
        function resize(moveEvent: PointerEvent): void { setPaletteWidth(limitPaletteWidth(startWidth + moveEvent.clientX - startX)); }
        function finish(): void { window.removeEventListener('pointermove', resize); window.removeEventListener('pointerup', finish); document.body.classList.remove('metamodel-palette-resizing'); }
        event.preventDefault(); document.body.classList.add('metamodel-palette-resizing'); window.addEventListener('pointermove', resize); window.addEventListener('pointerup', finish);
    }
    function resizePaletteWithKeyboard(event: ReactKeyboardEvent<HTMLDivElement>): void { if (event.key === 'ArrowLeft' || event.key === 'ArrowRight') { event.preventDefault(); setPaletteWidth(current => limitPaletteWidth(current + (event.key === 'ArrowLeft' ? -10 : 10))); } }
    return <section className="metamodel-visual-designer">
        <MetamodelDesignerCommandBar
            metamodels={metamodels} selectedMetamodelId={selectedMetamodelId} loading={loading} exportDisabled={explicitExportDisabled} saveDisabled={explicitSaveDisabled}
            persistedDraftId={persistedDraftId} designerMode={designerMode} structureCreationMode={structureCreationMode} layoutDirection={layoutDirection}
            focusSelected={focusSelected} hideDependencies={hideDependencies} sectionLocked={sectionLocked} canUndo={history.canUndo} canRedo={history.canRedo}
            onSelectMetamodel={setSelectedMetamodelId} onOpenLibrary={() => setShowMetamodelLibrary(true)} onOpenSelected={openSelectedMetamodel} onCreateNew={createNewMetamodel}
            onImport={() => setShowImportDialog(true)} onExport={openExportDialog} onFullLayout={() => applyLayout('FULL')} onElkLayout={applyElkStyleLayout}
            onDirectionChange={setLayoutDirection} onFocusSelectedChange={setFocusSelected} onHideDependenciesChange={setHideDependencies} onRoute={() => applyLayout('ROUTE_ONLY')}
            onUndo={history.undo} onRedo={history.redo} onSave={saveCurrentMetamodel} onDelete={requestMetamodelDeletion} onModeChange={setDesignerMode}
        />
        <HydraOperationProgress active={semanticSaving || diagramSaving} title="Saving metamodel" detail={semanticSaving ? 'Validating and saving the metamodel structure…' : 'Saving the visual layout and traceability…'} currentStageId={semanticSaving ? 'semantic' : 'diagram'} stages={[{ id: 'semantic', label: 'Metamodel structure' }, { id: 'diagram', label: 'Visual layout' }]} />
        <HydraOperationProgress active={preparingExport} title="Preparing export" detail="HYDRA is synchronizing saved state and loading available export formats before opening the export dialog." currentStageId={diagramSaving ? 'layout' : 'formats'} stages={[{ id: 'layout', label: 'Finish pending layout save' }, { id: 'formats', label: 'Load export formats' }, { id: 'dialog', label: 'Open export dialog' }]} />
        {error !== '' && <HydraActionableErrorNotice message={error} context="Metamodel Designer" onDismiss={() => setError('')} />}{message !== '' && <div className="metamodel-success-message">{message}</div>}
        {designerMode === 'STRUCTURE' && <div className="metamodel-structure-mode-strip"><HydraCreationModeSwitch advancedLabel="Visual / manual" mode={structureCreationMode} onChange={setStructureCreationMode} /></div>}
        {designerMode === 'STRUCTURE' && structureCreationMode === 'GUIDED' && <div className="metamodel-guided-workspace">
            <MetamodelGuidedReadyNotice currentStepId={structureGuidedStep} ready={metamodelReadyToSave} hasError={error !== ''} />
            <HydraGuidedWorkflow
            key={draft.id}
            currentStepId={structureGuidedStep}
            description="Create the metamodel step by step. Every check applies to the same model used by the full visual designer, so you can switch modes at any time without losing information."
            finishLabel="Save metamodel"
            steps={metamodelGuideSteps}
            title="Guided metamodel creation"
            onFinish={saveCurrentMetamodel}
            onStepChange={setStructureGuidedStep}
        /></div>}
        {designerMode === 'STRUCTURE' && structureCreationMode === 'ADVANCED' && <div className="metamodel-designer-body" style={{ '--metamodel-palette-width': `${paletteWidth}px` } as CSSProperties}>
            <MetamodelPalette connectionKind={connectionKind} connectionSourceClassId={connectionSourceClassId} onAddClass={addClass} onAddDataType={addDataType} onAddEnum={addEnum} onConnectionKindChange={kind => { setConnectionKind(kind); setConnectionSourceClassId(''); }} />
            <div aria-label="Resize palette" aria-orientation="vertical" aria-valuemax={420} aria-valuemin={190} aria-valuenow={paletteWidth} className="metamodel-palette-resizer" role="separator" tabIndex={0} onKeyDown={resizePaletteWithKeyboard} onPointerDown={startPaletteResize} />
            <MetamodelCanvas connectionKind={connectionKind} connectionSourceClassId={connectionSourceClassId} diagram={diagram} draft={draft} focusSelected={focusSelected} hideDependencies={hideDependencies} onConnectionSourceChange={setConnectionSourceClassId} onCreateConnection={createConnection} onDeleteElement={deleteElement} onDeleteElements={deleteElements} onDeleteRelation={deleteRelation} onDeleteRelations={deleteRelations} onDiagramChange={updateDiagram} onSelect={setSelection} selection={selection} />
            <MetamodelPropertyPanel diagram={diagram} draft={draft} onChange={updateDraft} onDiagramChange={updateDiagram} onSelect={setSelection} onValidationError={setError} selection={selection} />
        </div>}
        {designerMode !== 'STRUCTURE' && <Suspense fallback={<HydraModuleLoading moduleTitle={`${designerMode.toLowerCase()} designer`} />}>
            {designerMode === 'VALIDATIONS' && <HydraErrorBoundary scope="Validation designer" resetKey={`${draft.id}:validations`} onError={exception => handleValidationDesignerError(`Validation designer interface error: ${exception.message}`)}><LazyValidationDesigner metamodel={draft} persisted={draftPersisted} onError={handleValidationDesignerError} onMessage={handleValidationDesignerMessage} /></HydraErrorBoundary>}
            {designerMode === 'EVENTS' && <HydraErrorBoundary scope="Event designer" resetKey={`${draft.id}:events`} onError={exception => handleValidationDesignerError(`Event designer interface error: ${exception.message}`)}><LazyEventDesigner metamodelId={draft.id} metamodel={draft} persisted={draftPersisted} onError={handleValidationDesignerError} onMessage={handleValidationDesignerMessage} /></HydraErrorBoundary>}
            {designerMode === 'GAMIFICATION' && <HydraErrorBoundary scope="Gamification designer" resetKey={`${draft.id}:gamification`} onError={exception => handleValidationDesignerError(`Gamification designer interface error: ${exception.message}`)}><LazyGamificationDesigner metamodelId={draft.id} metamodel={draft} persisted={draftPersisted} onError={handleValidationDesignerError} onMessage={handleValidationDesignerMessage} /></HydraErrorBoundary>}
            {designerMode === 'EDITORS' && <HydraErrorBoundary scope="Editor definition designer" resetKey={`${draft.id}:editors`} onError={exception => handleValidationDesignerError(`Editor definition interface error: ${exception.message}`)}><LazyEditorDefinitionDesigner metamodel={draft} persisted={draftPersisted} onError={handleValidationDesignerError} onMessage={handleValidationDesignerMessage} /></HydraErrorBoundary>}
            {designerMode === 'INTEGRATIONS' && <HydraErrorBoundary scope="Integration designer" resetKey={`${draft.id}:integrations`} onError={exception => handleValidationDesignerError(`Integration designer interface error: ${exception.message}`)}><LazyIntegrationDesigner metamodelId={draft.id} persisted={draftPersisted} onError={handleValidationDesignerError} onMessage={handleValidationDesignerMessage} /></HydraErrorBoundary>}
        </Suspense>}
        {showExportDialog && <MetamodelExportDialog formats={exportFormats} metamodelId={draft.id} onClose={() => setShowExportDialog(false)} />}
        {showImportDialog && <MetamodelImportDialog formats={importFormats} onClose={() => setShowImportDialog(false)} onLoad={loadImportedPreview} onSaved={result => void loadSavedImport(result)} />}
        {overwriteDialogs}
        <MetamodelLibraryDialog open={showMetamodelLibrary} metamodels={metamodels} activeMetamodelId={draftPersisted ? draft.id : ''} initialSelectedIds={libraryInitialSelectedIds} busy={loading} onClose={() => { setShowMetamodelLibrary(false); setLibraryInitialSelectedIds([]); }} onOpen={openMetamodelFromLibrary} onLoadImpact={loadMetamodelDeletionImpact} onDelete={deleteMetamodelsFromLibrary} />
    </section>;
}

function preservedElementMessage(reason: 'CONTAINMENT_ROOT' | 'FIRST_CANVAS_ELEMENT' | 'LAST_CLASS' | 'NONE'): string {
    if (reason === 'CONTAINMENT_ROOT') return 'The containment root was preserved.';
    if (reason === 'FIRST_CANVAS_ELEMENT') return 'The first canvas element was preserved because the metamodel has no containment root.';
    if (reason === 'LAST_CLASS') return 'The first class was preserved because a metamodel must contain at least one class.';
    return '';
}
