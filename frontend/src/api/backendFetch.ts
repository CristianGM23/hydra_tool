import { backendEndpointDescription, resolveBackendRequest } from './backendConfig';

export const HYDRA_CORRELATION_HEADER = 'X-Hydra-Correlation-Id';

/** Uniform network-boundary error; it never assumes localhost, Windows or a fixed deployment topology. */
export class BackendUnavailableError extends Error {
    constructor() {
        super(`Backend unavailable at ${backendEndpointDescription()}. Check the configured HYDRA backend and retry.`);
        this.name = 'BackendUnavailableError';
    }
}

function correlationId(): string {
    try { return globalThis.crypto?.randomUUID?.() ?? `hydra-${Date.now()}-${Math.random().toString(16).slice(2)}`; }
    catch { return `hydra-${Date.now()}-${Math.random().toString(16).slice(2)}`; }
}

/** Executes a backend request and propagates one correlation id through the complete API boundary. */
export async function backendFetch(input: RequestInfo | URL, init?: RequestInit): Promise<Response> {
    try {
        const resolved = resolveBackendRequest(input);
        const headers = new Headers(init?.headers);
        if (!headers.has(HYDRA_CORRELATION_HEADER)) headers.set(HYDRA_CORRELATION_HEADER, correlationId());
        return await fetch(resolved, { ...init, headers });
    } catch {
        throw new BackendUnavailableError();
    }
}
