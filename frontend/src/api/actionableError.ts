import { currentHydraLanguage, localizeActionableErrorSection, localizeKnownErrorText } from './errorLocalization';

export interface HydraRecoveryActionBody { id?: unknown; label?: unknown; kind?: unknown; safe?: unknown; }

export interface ActionableApiErrorBody {
    code?: unknown;
    message?: unknown;
    detail?: unknown;
    field?: unknown;
    reason?: unknown;
    solution?: unknown;
    suggestions?: unknown;
    technicalDetail?: unknown;
    impact?: unknown;
    retryable?: unknown;
    recoveryActions?: unknown;
    correlationId?: unknown;
    operation?: unknown;
    affectedResource?: unknown;
}

export interface HydraRecoveryAction { id: string; label: string; kind: string; safe: boolean; }

export class ActionableApiError extends Error {
    readonly status: number;
    readonly code: string;
    readonly field: string;
    readonly reason: string;
    readonly solution: string;
    readonly suggestions: string[];
    readonly technicalDetail: string;
    readonly impact: string;
    readonly retryable: boolean;
    readonly recoveryActions: HydraRecoveryAction[];
    readonly correlationId: string;
    readonly operation: string;
    readonly affectedResource: string;

    constructor(status: number, body: ActionableApiErrorBody) {
        const technicalMessage = stringValue(body.message) || stringValue(body.detail) || `HTTP request failed with status ${status}`;
        const code = stringValue(body.code);
        const message = localizeKnownErrorText(stableUserSummary(status, code));
        const field = stringValue(body.field);
        const reason = localizeActionableErrorSection(stringValue(body.reason), 'reason', code);
        const solution = localizeActionableErrorSection(stringValue(body.solution), 'solution', code);
        const suggestions = Array.isArray(body.suggestions)
            ? body.suggestions.filter((item): item is string => typeof item === 'string' && item.trim() !== '').map(item => localizeActionableErrorSection(item, 'check', code))
            : [];
        const impact = localizeActionableErrorSection(stringValue(body.impact), 'reason', code);
        const retryable = body.retryable === true;
        const recoveryActions = recoveryActionValues(body.recoveryActions);
        const correlationId = stringValue(body.correlationId);
        const operation = stringValue(body.operation);
        const affectedResource = stringValue(body.affectedResource);
        const technicalDetail = stringValue(body.technicalDetail) || technicalMessage;
        super(formatActionableError(message, field, reason, solution, suggestions, { impact, retryable, recoveryActions, correlationId, operation, affectedResource, technicalDetail }));
        this.name = 'ActionableApiError';
        this.status = status;
        this.code = code;
        this.field = field;
        this.reason = reason;
        this.solution = solution;
        this.suggestions = suggestions;
        this.technicalDetail = technicalDetail;
        this.impact = impact;
        this.retryable = retryable;
        this.recoveryActions = recoveryActions;
        this.correlationId = correlationId;
        this.operation = operation;
        this.affectedResource = affectedResource;
    }
}

export function stableUserSummary(status: number, code: string): string {
    const normalized = code.trim().toUpperCase();
    if (['INVALID_INPUT','INVALID_REQUEST','INVALID_JSON','BAD_REQUEST'].includes(normalized)) return 'The submitted data is not valid.';
    if (['NOT_FOUND','PROFILE_NOT_FOUND','CONNECTION_NOT_FOUND'].includes(normalized) || status === 404) return 'The requested item could not be found.';
    if (['CONFLICT','STATE_MISMATCH'].includes(normalized) || status === 409) return 'The operation conflicts with the current state.';
    if (normalized === 'PRECONDITION_FAILED' || status === 412) return 'A required condition is no longer satisfied.';
    if (['DISABLED','PROVIDER_NOT_CONFIGURED','VAULT_UNAVAILABLE','CREDENTIALS_MISSING'].includes(normalized)) return 'This capability is not configured for the current HYDRA installation.';
    if (['UNAUTHORIZED','REFRESH_REQUIRED'].includes(normalized) || status === 401 || status === 403) return 'Authorization is required before continuing.';
    if (['FILE_TOO_LARGE','CAPACITY_EXCEEDED'].includes(normalized) || status === 413 || status === 429) return 'The request exceeds the configured safety limits.';
    if (['TOKEN_EXCHANGE_FAILED','REMOTE_ERROR','FLOW_EXPIRED'].includes(normalized)) return 'The external service could not complete the requested operation.';
    if (status === 502) return 'The external service could not complete the requested operation.';
    if (status === 503 || status === 504) return 'The HYDRA service is temporarily unavailable.';
    if (normalized === 'STORAGE_ERROR') return 'HYDRA could not read or save the requested data.';
    if (normalized === 'INTERNAL_ERROR' || status >= 500) return 'An unexpected internal error occurred.';
    if (status >= 400) return 'The request could not be completed with the submitted data.';
    return 'The operation could not be completed.';
}

export async function readActionableApiError(response: Response): Promise<ActionableApiError> {
    const body = await readActionableBody(response);
    return new ActionableApiError(response.status, body);
}

async function readActionableBody(response: Response): Promise<ActionableApiErrorBody> {
    const jsonReader = responseReader(response, 'json');
    if (jsonReader !== null) {
        try {
            const value = await jsonReader();
            if (value !== null && typeof value === 'object') return value as ActionableApiErrorBody;
        } catch {
            // Fall through to the text reader or the stable HTTP fallback.
        }
    }
    const textReader = responseReader(response, 'text');
    if (textReader !== null) {
        try {
            const message = await textReader();
            if (message.trim() !== '') return nonJsonFallbackBody(response.status, message.trim());
        } catch {
            // Use the stable HTTP fallback below.
        }
    }
    return nonJsonFallbackBody(response.status, '');
}

function nonJsonFallbackBody(status: number, technicalDetail: string): ActionableApiErrorBody {
    if (status === 502) return {
        code: 'REMOTE_ERROR',
        message: technicalDetail,
        reason: 'HYDRA reached a gateway or upstream service, but that service could not complete the request.',
        solution: 'Keep the current project unchanged, verify that the external provider or gateway is available, run Test connection for the affected remote profile, and retry the operation. If the provider is healthy, check the configured proxy or reverse-proxy route between HYDRA and that service.',
        suggestions: ['Do not change OAuth credentials unless Test connection identifies an authorization problem.'],
        technicalDetail,
    };
    if (status === 503 || status === 504) return {
        code: 'SERVICE_UNAVAILABLE',
        message: technicalDetail,
        reason: status === 504 ? 'HYDRA or one of its required services did not answer before the request timeout.' : 'The HYDRA service or one of its required services is temporarily unavailable.',
        solution: 'Wait briefly and retry. If the failure persists, check that the HYDRA backend and its configured dependent services are running and reachable, then repeat the operation without changing project data.',
        technicalDetail,
    };
    if (status >= 500) return {
        code: 'INTERNAL_ERROR',
        message: technicalDetail,
        reason: 'The server failed while processing the request and did not return a structured HYDRA diagnostic.',
        solution: 'Keep the current project unchanged and retry once. If it fails again, inspect the backend log for the same timestamp and operation; fix the reported server-side cause before retrying the write.',
        technicalDetail,
    };
    if (status >= 400) return {
        code: fallbackCodeForStatus(status),
        message: technicalDetail,
        reason: 'The server rejected the request but did not return a structured field-level diagnostic.',
        solution: 'Review the highlighted form fields and the information button beside them, correct the submitted value or reference, and run the operation again. If no field is highlighted, use the backend message kept in technical details to identify the rejected input.',
        technicalDetail,
    };
    return { message: technicalDetail, technicalDetail };
}

function fallbackCodeForStatus(status: number): string {
    if (status === 401 || status === 403) return 'UNAUTHORIZED';
    if (status === 404) return 'NOT_FOUND';
    if (status === 409) return 'CONFLICT';
    if (status === 412) return 'PRECONDITION_FAILED';
    if (status === 413 || status === 429) return 'CAPACITY_EXCEEDED';
    return 'INVALID_REQUEST';
}

function responseReader(response: Response, kind: 'json'): (() => Promise<unknown>) | null;
function responseReader(response: Response, kind: 'text'): (() => Promise<string>) | null;
function responseReader(response: Response, kind: 'json' | 'text'): (() => Promise<unknown>) | null {
    let candidate = response;
    if (typeof response.clone === 'function') {
        try {
            candidate = response.clone();
        } catch {
            // A consumed body cannot be cloned. Reading it may still fail, but the
            // caller already handles that and will use the stable HTTP fallback.
            candidate = response;
        }
    }
    const reader = candidate[kind];
    return typeof reader === 'function' ? reader.bind(candidate) : null;
}

export async function requireOk(response: Response): Promise<Response> {
    if (!response.ok) throw await readActionableApiError(response);
    return response;
}

export interface ActionableErrorMetadata { impact?: string; retryable?: boolean; recoveryActions?: HydraRecoveryAction[]; correlationId?: string; operation?: string; affectedResource?: string; technicalDetail?: string; }

export function formatActionableError(message: string, field: string, reason: string, solution: string, suggestions: string[], metadata: ActionableErrorMetadata = {}): string {
    const spanish = currentHydraLanguage() === 'es';
    const lines = [`${spanish ? 'Qué ha ocurrido' : 'What went wrong'}: ${message}`];
    if (field !== '') lines.push(`${spanish ? 'Campo' : 'Field'}: ${humanizeField(field)}`);
    if (reason !== '') lines.push(`${spanish ? 'Por qué' : 'Why it is invalid'}: ${reason}`);
    if (metadata.impact?.trim()) lines.push(`${spanish ? 'Impacto' : 'Impact'}: ${metadata.impact.trim()}`);
    if (solution !== '') lines.push(`${spanish ? 'Cómo solucionarlo' : 'How to fix it'}: ${solution}`);
    if (suggestions.length > 0) lines.push(`${spanish ? 'Comprobaciones' : 'Possible checks'}: ${suggestions.join(' ')}`);
    const recovery = (metadata.recoveryActions ?? []).map(action => `${action.safe ? '[safe] ' : ''}${action.label}`).join(' ');
    if (recovery !== '') lines.push(`${spanish ? 'Recuperación' : 'Recovery'}: ${recovery}`);
    if (metadata.retryable === true) lines.push(`${spanish ? 'Reintentable' : 'Retryable'}: yes`);
    if (metadata.operation?.trim()) lines.push(`${spanish ? 'Operación' : 'Operation'}: ${metadata.operation.trim()}`);
    if (metadata.affectedResource?.trim()) lines.push(`${spanish ? 'Recurso afectado' : 'Affected resource'}: ${metadata.affectedResource.trim()}`);
    if (metadata.correlationId?.trim()) lines.push(`${spanish ? 'ID de correlación' : 'Correlation ID'}: ${metadata.correlationId.trim()}`);
    if (metadata.technicalDetail?.trim()) lines.push(`${spanish ? 'Detalles técnicos' : 'Technical details'}: ${boundedTechnicalDetail(metadata.technicalDetail)}`);
    return lines.join('\n');
}

export function humanizeField(value: string): string {
    const spanish = currentHydraLanguage() === 'es';
    const key = value.trim().toLowerCase();
    const spanishFields: Record<string, string> = {
        tenant: 'Identificador de la organización de Microsoft', 'oauth-microsoft-tenant': 'Identificador de la organización de Microsoft', 'oauth-microsoft-organization': 'Identificador de la organización de Microsoft', organizationid: 'Identificador de la organización de Microsoft', clientid: 'Client / Application ID', callbackurl: 'URL de retorno', scopes: 'Permisos OAuth', account: 'Cuenta autorizada', permissions: 'Permisos', deviceflow: 'Flujo de dispositivo', authorizationmode: 'Modo de autorización', providersetup: 'Configuración del proveedor', credentialvault: 'Almacén seguro de credenciales', providerurl: 'URL del proveedor', repositoryurl: 'URL del repositorio o carpeta', displayname: 'Nombre de la conexión', 'repository/branch': 'Repositorio y rama', 'projectid/branch': 'Proyecto y rama', 'drive/folder': 'Unidad y carpeta', rootpath: 'Ruta raíz remota', remotelocation: 'Ubicación remota', id: 'ID técnico'
    };
    if (spanish && spanishFields[key] !== undefined) return spanishFields[key];
    const last = value.split('.').at(-1) ?? value;
    return last.replaceAll(/([a-z0-9])([A-Z])/g, '$1 $2').replaceAll(/[_-]+/g, ' ').replace(/^./, character => character.toUpperCase());
}

function stringValue(value: unknown): string {
    return typeof value === 'string' ? value.trim() : '';
}

function recoveryActionValues(value: unknown): HydraRecoveryAction[] {
    if (!Array.isArray(value)) return [];
    return value.flatMap(item => {
        if (item === null || typeof item !== 'object') return [];
        const body = item as HydraRecoveryActionBody;
        const label = stringValue(body.label);
        if (label === '') return [];
        return [{ id:stringValue(body.id), label:localizeActionableErrorSection(label, 'solution', ''), kind:stringValue(body.kind) || 'GUIDANCE', safe:body.safe === true }];
    }).slice(0, 8);
}

function boundedTechnicalDetail(value: string): string {
    const normalized = value.replaceAll(/\s+/g, ' ').trim();
    return normalized.length <= 1600 ? normalized : `${normalized.slice(0, 1597)}...`;
}
