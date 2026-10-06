import { useCallback, useEffect, useEffectEvent, useMemo, useState } from 'react';
import { HydraActionableErrorNotice, HydraButton, HydraModal, HydraSelect } from '../design-system';
import { DesignerDropdown } from '../designer-ui/DesignerDropdown';
import { translateUiText } from '../i18n/translationCatalog';
import { deleteAuditLog, exportAuditLog, loadAuditDescriptors, loadAuditLog, loadAuditStatistics, loadAuditStorageStatus, runAuditMaintenance } from './api';
import type { AuditDeletionMode, AuditDescriptorView, AuditExportFormat, AuditLanguage, AuditLogViewPage, AuditPresentedEntry, AuditQueryFilters, AuditStatistics, AuditStorageStatus } from './types';
import './activity-log.css';

interface Props { open: boolean; onClose: () => void; }

const EMPTY_PAGE: AuditLogViewPage = { entries: [], page: 0, pageSize: 50, hasMore: false };
const EMPTY_STATS: AuditStatistics = { total: 0, created: 0, updated: 0, deleted: 0, exports: 0, byModule: {}, byAction: {} };
const FORMATS: AuditExportFormat[] = ['JSON', 'CSV', 'MARKDOWN', 'TXT', 'DOC'];
const EMPTY_STORAGE: AuditStorageStatus = { provider: 'local-jsonl', maintenanceSupported: true, usedBytes: 0, maximumBytes: 134217728, partitionCount: 0, retentionEnabled: true, navigationRetentionDays: 7, openRetentionDays: 14, routineRetentionDays: 60, retentionDays: 180 };

const ENGLISH_COPY = {
    title: 'Activity log', description: 'Review what you changed in HYDRA, when you changed it and what the previous value was.',
    language: 'Language', english: 'English', spanish: 'Spanish', search: 'Search activity, resources or changed values…',
    module: 'All modules', action: 'All actions', from: 'From', to: 'To', apply: 'Apply', clear: 'Clear', refresh: 'Refresh', export: 'Export', display: 'Display',
    activity: 'activities', created: 'created', updated: 'updated', deleted: 'deleted', exports: 'exports', loading: 'Loading activity…',
    empty: 'No activity matches the current view.', storedButEmpty: 'Log data exists on disk but no readable activity matches this view. Check the selected filters or inspect the log diagnostics.',
    details: 'Details', hide: 'Hide', previous: 'Previous', next: 'Next', technical: 'Technical details', path: 'Path', close: 'Close activity log', retry: 'Retry', failed: 'Activity could not be loaded.',
    noChanges: 'No field-level changes were recorded for this action.', eventId: 'Event ID', resourceId: 'Resource ID', correlationId: 'Correlation ID',
    storage: 'Storage', manageStorage: 'Open storage management', hideStorage: 'Close storage management', policy: 'Retention policy', navigationRetention: 'Navigation/UI', openRetention: 'Open/view', routineRetention: 'Routine', valuableRetention: 'Changes/important',
    cleanExpired: 'Apply retention now', deleteNavigation: 'Delete navigation/UI', deleteFiltered: 'Delete filtered activity', deleteAll: 'Delete all my activity', confirm: 'Confirm deletion', cancel: 'Cancel', maintenanceRunning: 'Cleaning…',
    confirmNavigation: 'Delete all retained navigation and UI-view activity for the current user?', confirmFiltered: 'Delete every stored entry matching the currently applied filters?', confirmAll: 'Delete all retained activity for the current user in this workspace?', filterRequired: 'Apply at least one filter before using filtered deletion.', cleaned: 'Cleanup completed', deletedNotice: 'entries deleted',
    usage: 'Used', limit: 'Limit', partitions: 'Files', days: 'days'
} as const;

type ActivityLogCopy = { [K in keyof typeof ENGLISH_COPY]: string };

function activityLogCopy(language: AuditLanguage): ActivityLogCopy {
    if (language === 'en') return ENGLISH_COPY;
    return Object.fromEntries(Object.entries(ENGLISH_COPY).map(([key, value]) => [key, translateUiText(value, 'es')])) as ActivityLogCopy;
}

export function ActivityLogDialog({ open, onClose }: Props) {
    const [language, setLanguage] = useState<AuditLanguage>('en');
    const [filters, setFilters] = useState<AuditQueryFilters>({});
    const [draftFilters, setDraftFilters] = useState<AuditQueryFilters>({});
    const [page, setPage] = useState(0);
    const [data, setData] = useState<AuditLogViewPage>(EMPTY_PAGE);
    const [statistics, setStatistics] = useState<AuditStatistics>(EMPTY_STATS);
    const [descriptors, setDescriptors] = useState<AuditDescriptorView[]>([]);
    const [expanded, setExpanded] = useState<Set<string>>(new Set());
    const [format, setFormat] = useState<AuditExportFormat>('TXT');
    const [loading, setLoading] = useState(false);
    const [exporting, setExporting] = useState(false);
    const [error, setError] = useState('');
    const [warning, setWarning] = useState('');
    const [storage, setStorage] = useState<AuditStorageStatus>(EMPTY_STORAGE);
    const [maintenanceOpen, setMaintenanceOpen] = useState(false);
    const [maintaining, setMaintaining] = useState(false);
    const [pendingDelete, setPendingDelete] = useState<AuditDeletionMode | null>(null);
    const [notice, setNotice] = useState('');
    const copy = useMemo(() => activityLogCopy(language), [language]);

    const reload = useCallback(async (targetPage: number) => {
        if (!open) return;
        setLoading(true); setError(''); setWarning('');
        const results = await Promise.allSettled([loadAuditLog(language, filters, targetPage), loadAuditStatistics(filters), loadAuditDescriptors(language), loadAuditStorageStatus()]);
        const failures: string[] = [];
        const entries = results[0];
        if (entries.status === 'fulfilled') { setData(entries.value); setPage(targetPage); }
        else failures.push(`${language === 'es' ? 'Registros' : 'Entries'}: ${failureMessage(entries.reason, copy.failed)}`);
        const stats = results[1];
        if (stats.status === 'fulfilled') setStatistics(stats.value);
        else failures.push(`${language === 'es' ? 'Estadísticas' : 'Statistics'}: ${failureMessage(stats.reason, copy.failed)}`);
        const catalog = results[2];
        if (catalog.status === 'fulfilled') setDescriptors(catalog.value);
        else failures.push(`${language === 'es' ? 'Catálogo' : 'Catalog'}: ${failureMessage(catalog.reason, copy.failed)}`);
        const storageStatus = results[3];
        if (storageStatus.status === 'fulfilled') setStorage(storageStatus.value);
        else failures.push(`${language === 'es' ? 'Almacenamiento' : 'Storage'}: ${failureMessage(storageStatus.reason, copy.failed)}`);
        if (entries.status === 'rejected') setError(failures.join(' · '));
        else if (failures.length > 0) setWarning(failures.join(' · '));
        setLoading(false);
    }, [copy.failed, filters, language, open]);

    const reloadEffect = useEffectEvent(reload);
    useEffect(() => { if (open) void Promise.resolve().then(() => reloadEffect(0)); }, [open, language, filters]);

    const modules = useMemo(() => { const labels = new Map(descriptors.map(item => [item.moduleId, item.module])); return Array.from(new Set([...Object.keys(statistics.byModule), ...labels.keys()])).map(id => [id, labels.get(id) ?? moduleOptionLabel(id, language)] as const).sort((a, b) => a[1].localeCompare(b[1])); }, [descriptors, language, statistics.byModule]);
    const actions = useMemo(() => Array.from(new Set([...Object.keys(statistics.byAction), ...descriptors.map(item => item.action)])).sort(), [descriptors, statistics.byAction]);
    const toggle = (id: string) => setExpanded(current => { const next = new Set(current); if (next.has(id)) next.delete(id); else next.add(id); return next; });
    const applyFilters = () => { setPage(0); setFilters({ ...draftFilters }); };
    const clearFilters = () => { setDraftFilters({}); setFilters({}); setPage(0); };
    const onExport = async (nextFormat: AuditExportFormat = format) => { setFormat(nextFormat); setExporting(true); setError(''); try { await exportAuditLog(nextFormat, language, filters); } catch (failure) { setError(failure instanceof Error ? failure.message : copy.failed); } finally { setExporting(false); } };
    const hasDeletionFilter = Boolean(filters.text?.trim() || filters.module?.trim() || filters.action?.trim() || filters.from || filters.to);
    const runMaintenance = async () => { setMaintaining(true); setError(''); setNotice(''); try { const result = await runAuditMaintenance(); setNotice(`${copy.cleaned}: ${result.deletedEntries} ${copy.deletedNotice}, ${formatBytes(result.reclaimedBytes)}.`); await reload(0); } catch (failure) { setError(failure instanceof Error ? failure.message : copy.failed); } finally { setMaintaining(false); } };
    const confirmDelete = async () => { if (pendingDelete === null) return; setMaintaining(true); setError(''); setNotice(''); try { const result = await deleteAuditLog(pendingDelete, pendingDelete === 'FILTERED' ? filters : {}); setNotice(`${result.deletedEntries} ${copy.deletedNotice}, ${formatBytes(result.reclaimedBytes)}.`); setPendingDelete(null); await reload(0); } catch (failure) { setError(failure instanceof Error ? failure.message : copy.failed); } finally { setMaintaining(false); } };
    const confirmation = pendingDelete === 'NAVIGATION' ? copy.confirmNavigation : pendingDelete === 'FILTERED' ? copy.confirmFiltered : copy.confirmAll;

    return <HydraModal open={open} onClose={onClose} closeLabel={copy.close} size="wide" title={copy.title} description={copy.description} className="hydra-activity-log-modal">
        <div className="hydra-activity-log" data-hydra-i18n-skip="true">
            <section className="hydra-activity-query-bar" aria-label={copy.activity}>
                <div className="hydra-activity-search-row">
                    <input aria-label={copy.search} placeholder={copy.search} value={draftFilters.text ?? ''} onChange={event => setDraftFilters(value => ({ ...value, text: event.target.value }))} onKeyDown={event => { if (event.key === 'Enter') applyFilters(); }} />
                    <div className="hydra-activity-tools">
                        <DesignerDropdown label={copy.display} summary={language === 'es' ? copy.spanish : copy.english}>
                            <HydraButton variant="quiet" aria-pressed={language === 'en'} onClick={() => setLanguage('en')}>{copy.english}</HydraButton>
                            <HydraButton variant="quiet" aria-pressed={language === 'es'} onClick={() => setLanguage('es')}>{copy.spanish}</HydraButton>
                        </DesignerDropdown>
                        <DesignerDropdown label={copy.export} summary={format}>
                            {FORMATS.map(value => <HydraButton variant="quiet" disabled={exporting} key={value} onClick={() => void onExport(value)}>{value === 'MARKDOWN' ? 'Markdown' : value}</HydraButton>)}
                        </DesignerDropdown>
                        <DesignerDropdown align="END" closeOnAction={false} label={copy.storage} summary={formatBytes(storage.usedBytes)}>
                            <div className="hydra-activity-storage-menu" data-hydra-menu-keep-open="true">
                                <span><strong>{copy.usage}</strong>{formatBytes(storage.usedBytes)}</span><span><strong>{copy.limit}</strong>{formatBytes(storage.maximumBytes)}</span><span><strong>{copy.partitions}</strong>{storage.partitionCount}</span>
                                {storage.maintenanceSupported && <HydraButton variant="quiet" onClick={() => { setMaintenanceOpen(value => !value); setPendingDelete(null); }}>{maintenanceOpen ? copy.hideStorage : copy.manageStorage}</HydraButton>}
                            </div>
                        </DesignerDropdown>
                    </div>
                </div>
                <div className="hydra-activity-filter-row">
                    <HydraSelect ariaLabel={copy.module} value={draftFilters.module ?? ''} placeholder={copy.module} options={modules.map(([id,label])=>({value:id,label,description:'Filter activities produced by this HYDRA module.'}))} onChange={module => setDraftFilters(value => ({ ...value, module }))} />
                    <HydraSelect ariaLabel={copy.action} value={draftFilters.action ?? ''} placeholder={copy.action} options={actions.map(action=>({value:action,label:actionOptionLabel(action,language),description:'Filter activities by this action type.'}))} onChange={action => setDraftFilters(value => ({ ...value, action }))} />
                    <label>{copy.from}<input type="date" value={draftFilters.from ?? ''} onChange={event => setDraftFilters(value => ({ ...value, from: event.target.value }))} /></label>
                    <label>{copy.to}<input type="date" value={draftFilters.to ?? ''} onChange={event => setDraftFilters(value => ({ ...value, to: event.target.value }))} /></label>
                    <HydraButton onClick={applyFilters}>{copy.apply}</HydraButton>
                    <HydraButton variant="quiet" onClick={clearFilters}>{copy.clear}</HydraButton>
                    <HydraButton variant="quiet" onClick={() => void reload(page)}>{copy.refresh}</HydraButton>
                </div>
            </section>

            <section className="hydra-activity-summary-strip" aria-label={copy.activity}>
                <strong>{statistics.total} {copy.activity}</strong><span>{statistics.created} {copy.created}</span><span>{statistics.updated} {copy.updated}</span><span>{statistics.deleted} {copy.deleted}</span><span>{statistics.exports} {copy.exports}</span><span className="hydra-activity-summary-storage">{formatBytes(storage.usedBytes)} · {storage.partitionCount} {copy.partitions.toLowerCase()}</span>
            </section>

            {maintenanceOpen && storage.maintenanceSupported && <section className="hydra-activity-maintenance" aria-label={copy.storage}>
                <div className="hydra-activity-policy"><strong>{copy.policy}</strong><span>{copy.navigationRetention}: {storage.navigationRetentionDays} {copy.days}</span><span>{copy.openRetention}: {storage.openRetentionDays} {copy.days}</span><span>{copy.routineRetention}: {storage.routineRetentionDays} {copy.days}</span><span>{copy.valuableRetention}: {storage.retentionDays} {copy.days}</span></div>
                <div className="hydra-activity-maintenance-actions"><HydraButton variant="quiet" disabled={maintaining} onClick={() => void runMaintenance()}>{maintaining ? copy.maintenanceRunning : copy.cleanExpired}</HydraButton><HydraButton variant="quiet" disabled={maintaining} onClick={() => setPendingDelete('NAVIGATION')}>{copy.deleteNavigation}</HydraButton><HydraButton variant="quiet" disabled={maintaining || !hasDeletionFilter} onClick={() => setPendingDelete('FILTERED')}>{copy.deleteFiltered}</HydraButton><HydraButton variant="quiet" disabled={maintaining} onClick={() => setPendingDelete('ALL')}>{copy.deleteAll}</HydraButton></div>
                {pendingDelete && <div className="hydra-activity-delete-confirmation" role="alert"><span>{confirmation}</span><div><HydraButton onClick={() => void confirmDelete()} disabled={maintaining}>{copy.confirm}</HydraButton><HydraButton variant="quiet" onClick={() => setPendingDelete(null)} disabled={maintaining}>{copy.cancel}</HydraButton></div></div>}
                {notice && <div className="hydra-activity-maintenance-notice">{notice}</div>}
            </section>}

            {error && <div className="hydra-activity-error"><HydraActionableErrorNotice message={error} context="activity log" /><HydraButton variant="quiet" onClick={() => void reload(page)}>{copy.retry}</HydraButton></div>}
            {warning && !error && <div className="hydra-activity-warning" role="status"><span>{warning}</span><HydraButton variant="quiet" onClick={() => void reload(page)}>{copy.retry}</HydraButton></div>}
            {loading ? <div className="hydra-activity-state">{copy.loading}</div> : data.entries.length === 0 ? <div className="hydra-activity-state">{storage.usedBytes > 0 && statistics.total === 0 ? copy.storedButEmpty : copy.empty}</div> :
                <div className="hydra-activity-timeline">{data.entries.map(entry => <ActivityCard key={entry.eventId} entry={entry} language={language} expanded={expanded.has(entry.eventId)} onToggle={() => toggle(entry.eventId)} />)}</div>}

            <footer className="hydra-activity-pagination">
                <HydraButton variant="quiet" disabled={page === 0 || loading} onClick={() => void reload(Math.max(0, page - 1))}>{copy.previous}</HydraButton>
                <span>{page + 1}</span>
                <HydraButton variant="quiet" disabled={!data.hasMore || loading} onClick={() => void reload(page + 1)}>{copy.next}</HydraButton>
            </footer>
        </div>
    </HydraModal>;
}

function failureMessage(failure: unknown, fallback: string): string { return failure instanceof Error && failure.message.trim() !== '' ? failure.message : fallback; }
function moduleOptionLabel(moduleId: string, language: AuditLanguage): string { const es: Record<string, string> = { metamodel: 'Metamodelo', validation: 'Validación', event: 'Eventos', 'event-flow': 'Flujo de eventos', gamification: 'Gamificación', integration: 'Integraciones', editor: 'Editor', operation: 'Operaciones', audit: 'Actividad', navigation: 'Navegación', settings: 'Ajustes', project: 'Proyecto', model: 'Modelo', import: 'Importación', compatibility: 'Compatibilidad', distribution: 'Distribución' }; return language === 'es' ? (es[moduleId] ?? humanize(moduleId)) : humanize(moduleId); }
function actionOptionLabel(action: string, language: AuditLanguage): string { const es: Record<string, string> = { CREATE: 'Creación', UPDATE: 'Modificación', DELETE: 'Eliminación', IMPORT: 'Importación', EXPORT: 'Exportación', VALIDATE: 'Validación', OPEN: 'Apertura', SAVE: 'Guardado', EXECUTE: 'Ejecución', APPLY: 'Aplicación', ROLLBACK: 'Reversión', NAVIGATE: 'Navegación', CONFIGURE: 'Configuración', UNDO: 'Deshacer', REDO: 'Rehacer', CLOSE: 'Cierre' }; const en: Record<string, string> = { CREATE: 'Created', UPDATE: 'Updated', DELETE: 'Deleted', IMPORT: 'Imported', EXPORT: 'Exported', VALIDATE: 'Validated', OPEN: 'Opened', SAVE: 'Saved', EXECUTE: 'Executed', APPLY: 'Applied', ROLLBACK: 'Rolled back', NAVIGATE: 'Navigation', CONFIGURE: 'Configured', UNDO: 'Undo', REDO: 'Redo', CLOSE: 'Closed' }; return language === 'es' ? (es[action] ?? humanize(action)) : (en[action] ?? humanize(action)); }
function formatBytes(bytes: number): string { if (!Number.isFinite(bytes) || bytes <= 0) return '0 B'; const units = ['B', 'KiB', 'MiB', 'GiB']; const index = Math.min(units.length - 1, Math.floor(Math.log(bytes) / Math.log(1024))); const value = bytes / Math.pow(1024, index); return `${value >= 10 || index === 0 ? value.toFixed(0) : value.toFixed(1)} ${units[index]}`; }
function humanize(value: string): string { const text = value.replace(/[._-]+/g, ' ').trim(); return text.length === 0 ? value : text.charAt(0).toUpperCase() + text.slice(1); }

function ActivityCard({ entry, language, expanded, onToggle }: { entry: AuditPresentedEntry; language: AuditLanguage; expanded: boolean; onToggle: () => void }) {
    const copy = useMemo(() => activityLogCopy(language), [language]);
    const date = new Intl.DateTimeFormat(language === 'es' ? 'es-ES' : 'en-GB', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(entry.timestamp));
    return <article className={`hydra-activity-card action-${entry.action.toLowerCase()} result-${entry.result.toLowerCase()}`}>
        <div className="hydra-activity-marker" aria-hidden="true" />
        <div className="hydra-activity-card-body">
            <header><div className="hydra-activity-card-heading"><div className="hydra-activity-badges"><span>{entry.module}</span><span>{entry.actionLabel}</span></div><h3>{entry.title}</h3><p>{date} · {entry.actorName}</p></div><HydraButton className="hydra-activity-details-button" type="button" onClick={onToggle}>{expanded ? copy.hide : copy.details}</HydraButton></header>
            <p className="hydra-activity-card-summary">{entry.summary}</p>
            {entry.changes.length > 0 && <div className="hydra-activity-change-preview">{entry.changes.slice(0, expanded ? entry.changes.length : 2).map((change, index) => <div className="hydra-activity-change" key={`${change.path}-${index}`}><strong>{change.label}</strong><span className="before">{change.beforeValue}</span><span aria-hidden="true">→</span><span className="after">{change.afterValue}</span>{expanded && <small>{copy.path}: {change.path}</small>}</div>)}</div>}
            {expanded && entry.changes.length === 0 && <p className="hydra-activity-no-changes">{copy.noChanges}</p>}
            {expanded && <details className="hydra-activity-technical"><summary>{copy.technical}</summary><dl><dt>{copy.eventId}</dt><dd>{entry.eventId}</dd><dt>{copy.resourceId}</dt><dd>{entry.resourceId}</dd><dt>{copy.correlationId}</dt><dd>{entry.correlationId}</dd></dl></details>}
        </div>
    </article>;
}
