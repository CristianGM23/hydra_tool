import { describe, expect, it } from 'vitest';
import { projectFileDownloadUrl, projectFileVisualUrl } from './projectApi';

describe('project browser artifact URL security', () => {
    it.each([
        '../outside.json',
        'models/../outside.json',
        './models/people.json',
        '/etc/passwd',
        'C:\\temp\\secret.svg',
        '\\\\server\\share\\secret.svg',
        'models//people.json'
    ])('rejects unsafe browser path %s before URL construction', (path) => {
        expect(() => projectFileDownloadUrl(path)).toThrow('HYDRA_UNSAFE_PROJECT_BROWSER_PATH');
        expect(() => projectFileVisualUrl(path)).toThrow('HYDRA_UNSAFE_PROJECT_BROWSER_PATH');
    });

    it('encodes a valid nested path without changing its semantic identity', () => {
        const path = 'model folder/a#b?.json';
        expect(projectFileDownloadUrl(path)).toContain('/api/projects/active/download?path=model%20folder%2Fa%23b%3F.json');
        expect(projectFileVisualUrl(path)).toContain('/api/projects/active/visual?path=model%20folder%2Fa%23b%3F.json');
    });
});
