/** Frontend/backend boundary. Same-origin /api is the portable default; deployments may set VITE_HYDRA_API_BASE_URL. */
export function backendBaseUrl(): string {
    const configured = (import.meta.env.VITE_HYDRA_API_BASE_URL ?? '').trim();
    return configured.replace(/\/$/, '');
}

export function resolveBackendUrl(path: string): string {
    const base = backendBaseUrl();
    if (base === '' || !path.startsWith('/api')) return path;
    return `${base}${path}`;
}

export function resolveBackendRequest(input: RequestInfo | URL): RequestInfo | URL {
    return typeof input === 'string' ? resolveBackendUrl(input) : input;
}

export function backendEndpointDescription(): string {
    const base = backendBaseUrl();
    return base === '' ? 'the same-origin /api endpoint' : base;
}
