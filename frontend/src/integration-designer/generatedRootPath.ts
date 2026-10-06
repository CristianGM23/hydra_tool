export type GeneratedRootProblem = 'REQUIRED' | 'ABSOLUTE' | 'TRAVERSAL' | 'UNSAFE_CHARACTER' | 'TOO_LONG';

export interface GeneratedRootValidation {
    valid: boolean;
    normalized: string;
    problem?: GeneratedRootProblem;
}

export interface ProjectRelativeGeneratedRoot {
    valid: boolean;
    relativePath: string;
    message: string;
}

const WINDOWS_DRIVE = /^[A-Za-z]:\//;
const URI_SCHEME = /^[A-Za-z][A-Za-z0-9+.-]*:\/\//;
const WINDOWS_RESERVED_SEGMENT = /^(con|prn|aux|nul|com[1-9]|lpt[1-9])(\..*)?$/i;
const WINDOWS_RESERVED_CHARACTERS = ':*?"<>|';
const MAX_GENERATED_ROOT_LENGTH = 240;

/**
 * Keeps generated output portable across Windows, macOS, Linux and repository checkouts.
 * It intentionally accepts human-readable directory names (including spaces and Unicode)
 * while rejecting absolute paths, traversal and characters that are unsafe on Windows.
 */
export function validateGeneratedRootPath(value: string): GeneratedRootValidation {
    const normalized = normalizeRelativePath(value);
    if (normalized === '') return { valid: false, normalized, problem: 'REQUIRED' };
    if (normalized.length > MAX_GENERATED_ROOT_LENGTH) return { valid: false, normalized, problem: 'TOO_LONG' };
    if (normalized.startsWith('/') || WINDOWS_DRIVE.test(normalized) || URI_SCHEME.test(normalized)) {
        return { valid: false, normalized, problem: 'ABSOLUTE' };
    }
    const segments = normalized.split('/');
    if (segments.some(segment => segment === '.' || segment === '..')) return { valid: false, normalized, problem: 'TRAVERSAL' };
    if (segments.some(segment => segment === '' || !isPortablePathSegment(segment))) {
        return { valid: false, normalized, problem: 'UNSAFE_CHARACTER' };
    }
    return { valid: true, normalized };
}

export function generatedRootProblemMessage(problem: GeneratedRootProblem | undefined): string {
    if (problem === 'REQUIRED') return 'Generated folder is required.';
    if (problem === 'ABSOLUTE') return 'Generated folder must be a project-relative path, not an absolute path or URI.';
    if (problem === 'TRAVERSAL') return 'Generated folder must stay inside the project and cannot contain . or .. path segments.';
    if (problem === 'UNSAFE_CHARACTER') return 'Generated folder contains a path segment that is not portable across supported operating systems.';
    if (problem === 'TOO_LONG') return 'Generated folder is too long; use a project-relative path of at most 240 characters.';
    return '';
}

/** Converts a native picker result into a safe project-relative path without trusting path casing on Windows. */
export function toProjectRelativeGeneratedRoot(projectRoot: string, selectedDirectory: string): ProjectRelativeGeneratedRoot {
    const root = normalizeAbsolutePath(projectRoot);
    const selected = normalizeAbsolutePath(selectedDirectory);
    if (root === '' || selected === '') {
        return { valid: false, relativePath: '', message: 'The selected folder could not be resolved.' };
    }
    const windowsStyle = WINDOWS_DRIVE.test(root);
    const comparableRoot = windowsStyle ? root.toLowerCase() : root;
    const comparableSelected = windowsStyle ? selected.toLowerCase() : selected;
    if (comparableSelected === comparableRoot) {
        return { valid: false, relativePath: '', message: 'Select a subfolder inside the active project; generating directly into the project root is intentionally blocked.' };
    }
    const prefix = `${comparableRoot}/`;
    if (!comparableSelected.startsWith(prefix)) {
        return { valid: false, relativePath: '', message: 'The selected folder is outside the active project.' };
    }
    const relativePath = selected.slice(root.length + 1);
    const validation = validateGeneratedRootPath(relativePath);
    return validation.valid
        ? { valid: true, relativePath: validation.normalized, message: '' }
        : { valid: false, relativePath: '', message: generatedRootProblemMessage(validation.problem) };
}

function isPortablePathSegment(segment: string): boolean {
    if (segment.endsWith('.') || segment.endsWith(' ') || WINDOWS_RESERVED_SEGMENT.test(segment)) return false;
    for (const character of segment) {
        if (character.charCodeAt(0) < 32 || WINDOWS_RESERVED_CHARACTERS.includes(character)) return false;
    }
    return true;
}

function normalizeRelativePath(value: string): string {
    let normalized = value.replaceAll('\\', '/').trimStart().replace(/\/{2,}/g, '/');
    // Human input may contain whitespace after an explicit trailing slash (for example
    // after pasting "generated/emf/ "), but whitespace that belongs to the last
    // path segment must remain visible to the portability validator.
    const trailingWhitespace = normalized.match(/\s+$/)?.[0] ?? '';
    if (trailingWhitespace !== '' && normalized.slice(0, -trailingWhitespace.length).endsWith('/')) {
        normalized = normalized.slice(0, -trailingWhitespace.length);
    }
    return normalized.replace(/\/$/, '');
}

function normalizeAbsolutePath(value: string): string {
    const normalized = value.trim().replaceAll('\\', '/').replace(/\/{2,}/g, '/');
    if (normalized === '/') return normalized;
    return normalized.replace(/\/$/, '');
}
