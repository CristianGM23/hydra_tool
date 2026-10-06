import { projectFileVisualUrl } from '../project-explorer/projectApi';
import { resolveBackendUrl } from '../api/backendConfig';
import { backendFetch } from '../api/backendFetch';

const CANONICAL_PREFIX = 'hydra-assets/';

/** Resolves a visual mapping image without assuming that an external project is currently open. */
export function editorVisualAssetUrl(path: string): string {
    const normalized = path.trim().replaceAll('\\', '/');
    if (normalized.startsWith(CANONICAL_PREFIX)) return resolveBackendUrl(`/api/editor-assets?path=${encodeURIComponent(normalized)}`);
    return projectFileVisualUrl(normalized);
}

/** Loads one safe visual asset and serializes it as a data URL for a portable capability package. */
export async function loadEditorVisualAssetResource(path: string, maximumBytes = 4 * 1024 * 1024): Promise<string> {
    const response = await backendFetch(editorVisualAssetUrl(path));
    if (!response.ok) throw new Error(`Visual asset could not be loaded: ${path}`);
    const buffer = await response.arrayBuffer();
    if (buffer.byteLength > maximumBytes) throw new Error(`Visual asset exceeds the portable export limit: ${path}`);
    const bytes = new Uint8Array(buffer);
    let binary = '';
    const chunk = 0x8000;
    for (let offset = 0; offset < bytes.length; offset += chunk) binary += String.fromCharCode(...bytes.subarray(offset, offset + chunk));
    const mediaType = response.headers.get('Content-Type')?.split(';')[0]?.trim() || mediaTypeForPath(path);
    return `data:${mediaType};base64,${btoa(binary)}`;
}

function mediaTypeForPath(path: string): string {
    const lower = path.toLowerCase();
    if (lower.endsWith('.svg')) return 'image/svg+xml';
    if (lower.endsWith('.png')) return 'image/png';
    if (lower.endsWith('.jpg') || lower.endsWith('.jpeg')) return 'image/jpeg';
    if (lower.endsWith('.gif')) return 'image/gif';
    if (lower.endsWith('.webp')) return 'image/webp';
    return 'application/octet-stream';
}
