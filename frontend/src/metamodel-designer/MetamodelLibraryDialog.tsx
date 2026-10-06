import { useEffect, useMemo, useRef, useState } from 'react';
import { HydraButton, HydraModal } from '../design-system';
import { useHydraLanguage } from '../i18n';
import type { CanonicalMetamodelSummary, MetamodelDeletionImpact } from './types';

interface DeleteOptions { deleteNotations: boolean; deleteExports: boolean; }
export interface MetamodelBatchDeleteResult {
    deletedIds: string[];
    failed: Readonly<Record<string, string>>;
    warnings?: Readonly<Record<string, string>>;
}
interface Props {
    open: boolean;
    metamodels: CanonicalMetamodelSummary[];
    activeMetamodelId: string;
    initialSelectedIds?: string[];
    busy: boolean;
    onClose: () => void;
    onOpen: (id: string) => Promise<void>;
    onLoadImpact?: (id: string) => Promise<MetamodelDeletionImpact>;
    onDelete: (ids: string[], options: DeleteOptions) => Promise<MetamodelBatchDeleteResult>;
}

const IMPACT_CONCURRENCY = 4;

export function MetamodelLibraryDialog(props: Props) {
    return props.open ? <MetamodelLibraryDialogContent {...props} /> : null;
}

function MetamodelLibraryDialogContent({ metamodels, activeMetamodelId, initialSelectedIds = [], busy, onClose, onOpen, onLoadImpact, onDelete }: Props) {
    const { t } = useHydraLanguage();
    const [selectedIds, setSelectedIds] = useState<Set<string>>(() => new Set(initialSelectedIds.filter(id => metamodels.some(item => item.id === id))));
    const [pendingIds, setPendingIds] = useState<string[]>([]);
    const [impacts, setImpacts] = useState<Readonly<Record<string, MetamodelDeletionImpact>>>({});
    const [impactFailures, setImpactFailures] = useState<Readonly<Record<string, string>>>({});
    const [deleteFailures, setDeleteFailures] = useState<Readonly<Record<string, string>>>({});
    const [deleteWarnings, setDeleteWarnings] = useState<Readonly<Record<string, string>>>({});
    const [impactLoading, setImpactLoading] = useState(false);
    const [deleteNotations, setDeleteNotations] = useState(true);
    const [deleteExports, setDeleteExports] = useState(true);
    const impactRequestRef = useRef(0);
    const metamodelById = useMemo(() => new Map(metamodels.map(item => [item.id, item])), [metamodels]);
    const allSelected = metamodels.length > 0 && selectedIds.size === metamodels.length;
    const impactBlocked = onLoadImpact !== undefined && (impactLoading || Object.keys(impactFailures).length > 0);

    useEffect(() => () => { impactRequestRef.current += 1; }, []);

    function toggle(id: string): void {
        setSelectedIds(current => {
            const next = new Set(current);
            if (next.has(id)) next.delete(id); else next.add(id);
            return next;
        });
    }

    function cancelDeleteReview(): void {
        impactRequestRef.current += 1;
        setPendingIds([]);
        setImpacts({});
        setImpactFailures({});
        setDeleteFailures({});
        setDeleteWarnings({});
        setImpactLoading(false);
    }

    async function requestDelete(ids: string[]): Promise<void> {
        const validIds = [...new Set(ids)].filter(id => metamodelById.has(id));
        const requestId = impactRequestRef.current + 1;
        impactRequestRef.current = requestId;
        setPendingIds(validIds);
        setImpacts({});
        setImpactFailures({});
        setDeleteFailures({});
        setDeleteWarnings({});
        if (validIds.length === 0 || onLoadImpact === undefined) return;
        setImpactLoading(true);
        const results = await mapSettledWithConcurrency(validIds, IMPACT_CONCURRENCY, async id => await onLoadImpact(id));
        if (impactRequestRef.current !== requestId) return;
        const nextImpacts: Record<string, MetamodelDeletionImpact> = {};
        const nextFailures: Record<string, string> = {};
        results.forEach((result, index) => {
            const id = validIds[index];
            if (result.status === 'fulfilled') nextImpacts[id] = result.value;
            else nextFailures[id] = result.reason instanceof Error ? result.reason.message : 'Could not inspect the files affected by deletion.';
        });
        setImpacts(nextImpacts);
        setImpactFailures(nextFailures);
        setImpactLoading(false);
    }

    async function confirmDelete(): Promise<void> {
        if (pendingIds.length === 0 || impactBlocked) return;
        try {
            const result = await onDelete(pendingIds, { deleteNotations, deleteExports });
            const deleted = new Set(result.deletedIds);
            setSelectedIds(current => new Set([...current].filter(id => !deleted.has(id))));
            setDeleteFailures(result.failed);
            setDeleteWarnings(result.warnings ?? {});
            const remaining = pendingIds.filter(id => !deleted.has(id) && metamodelById.has(id));
            if (remaining.length === 0) {
                cancelDeleteReview();
                return;
            }
            setPendingIds(remaining);
            setImpacts(current => Object.fromEntries(Object.entries(current).filter(([id]) => remaining.includes(id))));
            setImpactFailures(current => Object.fromEntries(Object.entries(current).filter(([id]) => remaining.includes(id))));
        } catch (error) {
            const message = error instanceof Error ? error.message : 'Could not delete the selected metamodels.';
            setDeleteFailures(Object.fromEntries(pendingIds.map(id => [id, message])));
            setDeleteWarnings({});
        }
    }

    const footer = pendingIds.length > 0 ? <>
        <HydraButton disabled={busy} onClick={cancelDeleteReview}>{t('Cancel')}</HydraButton>
        {Object.keys(impactFailures).length > 0 && <HydraButton disabled={busy || impactLoading} onClick={() => void requestDelete(pendingIds)}>{t('Retry inspection')}</HydraButton>}
        <HydraButton disabled={busy || impactBlocked} variant="danger" onClick={() => void confirmDelete()}>{busy ? t('Deleting…') : pendingIds.length === 1 ? t('Delete metamodel') : `${t('Delete')} ${pendingIds.length} ${t('metamodels')}`}</HydraButton>
    </> : <>
        <HydraButton onClick={onClose}>{t('Close')}</HydraButton>
        <HydraButton disabled={busy || selectedIds.size === 0} variant="danger" onClick={() => void requestDelete([...selectedIds])}>{selectedIds.size <= 1 ? t('Delete selected') : `${t('Delete selected')} (${selectedIds.size})`}</HydraButton>
    </>;

    return <HydraModal open onClose={onClose} title={t('Saved metamodels')} description={t('Open or remove saved metamodels from one place. Select several rows to delete them together, or remove all saved metamodels when you need a clean workspace.')} size="large" className="metamodel-library-dialog" footer={footer} closeLabel={t('Close metamodel manager')}>
        {pendingIds.length > 0 ? <section className="metamodel-library-confirmation">
            <div className="metamodel-library-danger-icon" aria-hidden="true">!</div>
            <div><h3>{pendingIds.length === 1 ? t('Delete this metamodel?') : t('Delete selected metamodels?')}</h3><p>{t('This removes the saved metamodel and the lifecycle files that belong to it. This action cannot be undone.')}</p></div>
            <div className="metamodel-library-delete-list">{pendingIds.map(id => <span key={id}>{metamodelById.get(id)?.name || id}</span>)}</div>
            <section className="metamodel-library-impact" aria-live="polite">
                <header><strong>{t('Affected files')}</strong><span>{impactLoading ? t('Checking affected files…') : t('Review what will be removed before confirming.')}</span></header>
                {Object.keys(impactFailures).length > 0 && <div className="error-message"><strong>{t('Some deletion impacts could not be inspected.')}</strong>{Object.entries(impactFailures).map(([id,message]) => <span key={id}>{metamodelById.get(id)?.name || id}: {t(message)}</span>)}</div>}
                {Object.keys(deleteFailures).length > 0 && <div className="error-message"><strong>{t('Some metamodels could not be deleted.')}</strong>{Object.entries(deleteFailures).map(([id,message]) => <span key={id}>{metamodelById.get(id)?.name || id}: {t(message)}</span>)}</div>}
                {Object.keys(deleteWarnings).length > 0 && <div className="warning-message"><strong>{t('Deletion completed with cleanup warnings')}</strong>{Object.entries(deleteWarnings).map(([id,message]) => <span key={id}>{metamodelById.get(id)?.name || id}: {t(message)}</span>)}</div>}
                {!impactLoading && Object.keys(impacts).length > 0 && <div>{pendingIds.map(id => { const impact=impacts[id]; if(impact===undefined) return null; const files=affectedFiles(impact,deleteNotations,deleteExports); return <article key={id}><strong>{metamodelById.get(id)?.name || id}</strong><span>{files.length} {t('managed file(s)')}</span>{files.length>0&&<ul>{files.map(file=><li key={file}><code>{file}</code></li>)}</ul>}<small>{t(impact.warning)}</small></article>; })}</div>}
            </section>
            <div className="metamodel-library-delete-options">
                <label><input type="checkbox" checked={deleteNotations} onChange={event => setDeleteNotations(event.target.checked)} /> <span><strong>{t('Remove generated notations')}</strong><small>{t('Delete notation files generated for the selected metamodels.')}</small></span></label>
                <label><input type="checkbox" checked={deleteExports} onChange={event => setDeleteExports(event.target.checked)} /> <span><strong>{t('Remove generated exports')}</strong><small>{t('Delete generated export files associated with the selected metamodels.')}</small></span></label>
            </div>
        </section> : <>
            <div className="metamodel-library-toolbar">
                <div><strong>{metamodels.length} {t('saved metamodel(s)')}</strong><span>{selectedIds.size === 0 ? t('Select one or more metamodels to enable batch deletion.') : `${selectedIds.size} ${t('selected')}`}</span></div>
                <div><HydraButton size="small" disabled={metamodels.length === 0} onClick={() => setSelectedIds(allSelected ? new Set() : new Set(metamodels.map(item => item.id)))}>{allSelected ? t('Clear selection') : t('Select all')}</HydraButton><HydraButton size="small" disabled={busy || metamodels.length === 0} variant="danger" onClick={() => void requestDelete(metamodels.map(item => item.id))}>{t('Delete all')}</HydraButton></div>
            </div>
            {metamodels.length === 0 ? <div className="metamodel-library-empty"><strong>{t('No saved metamodels')}</strong><span>{t('Create or import a metamodel and save it to make it appear here.')}</span></div> : <div className="metamodel-library-list" role="list">
                {metamodels.map(item => <article className={`metamodel-library-row${selectedIds.has(item.id) ? ' selected' : ''}`} key={item.id} role="listitem">
                    <label className="metamodel-library-select"><input aria-label={`${t('Select')} ${item.name || item.id}`} type="checkbox" checked={selectedIds.has(item.id)} onChange={() => toggle(item.id)} /></label>
                    <div className="metamodel-library-identity"><strong>{item.name || item.id}</strong><span>{item.id}</span><small>{item.id === activeMetamodelId ? t('Currently open') : t('Saved metamodel')}</small></div>
                    <div className="metamodel-library-row-actions"><HydraButton size="small" disabled={busy} onClick={() => void onOpen(item.id)}>{t('Open')}</HydraButton><HydraButton size="small" disabled={busy} variant="danger" onClick={() => void requestDelete([item.id])}>{t('Delete')}</HydraButton></div>
                </article>)}
            </div>}
        </>}
    </HydraModal>;
}

function affectedFiles(impact: MetamodelDeletionImpact, deleteNotations: boolean, deleteExports: boolean): string[] {
    const optional = [impact.canonicalFile, impact.targetConfigurationFile, impact.projectionManifestFile, impact.expressionFile, impact.validationFile, impact.actionFile, impact.eventDefinitionFile, impact.eventBindingFile];
    return [...new Set([
        ...optional.filter((value): value is string => value !== undefined && value !== ''),
        impact.ecoreFile, impact.diagramFile,
        ...(impact.editorDefinitionFiles ?? []), ...(impact.integrationProfileFiles ?? []), ...(impact.modelFiles ?? []), ...(impact.dependentArtifactFiles ?? []),
        ...(deleteNotations ? impact.notationFiles ?? [] : []), ...(deleteExports ? impact.exportFiles ?? [] : [])
    ].filter(value => value !== ''))];
}

async function mapSettledWithConcurrency<T, R>(items: readonly T[], concurrency: number, task: (item: T) => Promise<R>): Promise<PromiseSettledResult<R>[]> {
    if (items.length === 0) return [];
    const results = new Array<PromiseSettledResult<R>>(items.length);
    let cursor = 0;
    const worker = async (): Promise<void> => {
        while (true) {
            const index = cursor;
            cursor += 1;
            if (index >= items.length) return;
            try { results[index] = { status:'fulfilled', value:await task(items[index]) }; }
            catch (reason) { results[index] = { status:'rejected', reason }; }
        }
    };
    const workerCount = Math.min(Math.max(1, concurrency), items.length);
    await Promise.all(Array.from({ length: workerCount }, () => worker()));
    return results;
}
