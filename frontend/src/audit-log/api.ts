import { backendFetch } from '../api/backendFetch';
import type { AuditClientActivityRequest, AuditDeletionMode, AuditDescriptorView, AuditExportFormat, AuditLanguage, AuditLogViewPage, AuditMaintenanceResult, AuditQueryFilters, AuditStatistics, AuditStorageStatus } from './types';

function params(language: AuditLanguage, filters: AuditQueryFilters, page?: number): URLSearchParams {
    const result = new URLSearchParams({ language });
    if (filters.text?.trim()) result.set('text', filters.text.trim());
    if (filters.module?.trim()) result.append('module', filters.module.trim());
    if (filters.action?.trim()) result.append('action', filters.action.trim());
    if (filters.from) result.set('from', new Date(`${filters.from}T00:00:00`).toISOString());
    if (filters.to) result.set('to', new Date(`${filters.to}T23:59:59.999`).toISOString());
    if (page !== undefined) result.set('page', String(page));
    return result;
}

async function requireOk(response: Response): Promise<Response> {
    if (response.ok) return response;
    let detail = `${response.status} ${response.statusText}`;
    try {
        const body = await response.json() as { message?: string; detail?: string };
        detail = body.detail ?? body.message ?? detail;
    } catch { /* keep HTTP detail */ }
    throw new Error(detail);
}

export async function loadAuditLog(language: AuditLanguage, filters: AuditQueryFilters, page = 0): Promise<AuditLogViewPage> {
    const response = await requireOk(await backendFetch(`/api/audit-log?${params(language, filters, page)}`));
    return response.json() as Promise<AuditLogViewPage>;
}

export async function loadAuditStatistics(filters: AuditQueryFilters): Promise<AuditStatistics> {
    const search = params('en', filters);
    search.delete('language');
    const response = await requireOk(await backendFetch(`/api/audit-log/statistics?${search}`));
    return response.json() as Promise<AuditStatistics>;
}

export async function loadAuditDescriptors(language: AuditLanguage): Promise<AuditDescriptorView[]> {
    const response = await requireOk(await backendFetch(`/api/audit-log/descriptors?language=${language}`));
    return response.json() as Promise<AuditDescriptorView[]>;
}

export async function exportAuditLog(format: AuditExportFormat, language: AuditLanguage, filters: AuditQueryFilters): Promise<void> {
    const search = params(language, filters);
    search.set('format', format);
    const response = await requireOk(await backendFetch(`/api/audit-log/export?${search}`));
    const disposition = response.headers.get('Content-Disposition') ?? '';
    const extension = format === 'MARKDOWN' ? 'md' : format.toLowerCase();
    const filename = disposition.match(/filename="([^"]+)"/)?.[1] ?? `hydra-activity-log.${extension}`;
    const blob = await response.blob();
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = filename;
    document.body.appendChild(anchor);
    anchor.click();
    anchor.remove();
    URL.revokeObjectURL(url);
}

/** Best-effort: la auditoría nunca debe bloquear una acción de interfaz del usuario. */
export async function recordAuditActivity(activity: AuditClientActivityRequest): Promise<void> {
    try {
        await backendFetch('/api/audit-log/activities', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(activity)
        });
    } catch {
        // El backend mantiene además la red de seguridad HTTP/persistencia.
    }
}


export async function loadAuditStorageStatus(): Promise<AuditStorageStatus> {
    const response = await requireOk(await backendFetch('/api/audit-log/maintenance/storage'));
    return response.json() as Promise<AuditStorageStatus>;
}

export async function runAuditMaintenance(): Promise<AuditMaintenanceResult> {
    const response = await requireOk(await backendFetch('/api/audit-log/maintenance/run', { method: 'POST' }));
    return response.json() as Promise<AuditMaintenanceResult>;
}

export async function deleteAuditLog(mode: AuditDeletionMode, filters: AuditQueryFilters = {}): Promise<AuditMaintenanceResult> {
    const body = {
        mode,
        module: filters.module?.trim() ? [filters.module.trim()] : [],
        action: filters.action?.trim() ? [filters.action.trim()] : [],
        text: filters.text?.trim() ?? '',
        from: filters.from ? new Date(`${filters.from}T00:00:00`).toISOString() : null,
        to: filters.to ? new Date(`${filters.to}T23:59:59.999`).toISOString() : null
    };
    const response = await requireOk(await backendFetch('/api/audit-log/maintenance/delete', {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body)
    }));
    return response.json() as Promise<AuditMaintenanceResult>;
}
