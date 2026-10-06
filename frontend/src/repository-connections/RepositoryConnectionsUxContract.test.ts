import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';
import { translateUiText } from '../i18n/translationCatalog';

function source(path: string): string {
    return readFileSync(resolve(process.cwd(), path), 'utf8');
}

describe('Repository connections UX convergence', () => {
    it('uses the shared HYDRA guided workflow, form-field help and required-field affordances', () => {
        const component = source('src/repository-connections/RepositoryConnectionsCenter.tsx') + source('src/repository-connections/RepositoryConnectionsGuidedSteps.tsx');
        expect(component).toContain('HydraGuidedWorkflow');
        expect(component).toContain('HydraRequiredFieldsLegend');
        expect(component).toContain("Technical identifier reused internally by importers, exporters and future automations.");
        expect(component).toContain("Provider family that will store or serve the artifacts.");
        expect(component).toContain("Remote folder path inside the chosen provider where HYDRA will place the exported artifacts");
        expect(component).toMatch(/title\s*:\s*t\('Paste URL'\)/);
        expect(component).toContain("label={t('Repository or folder URL')}");
    });

    it('widens the editor and structures it as a guided full-width mechanism', () => {
        const css = source('src/repository-connections/repository-connections.css');
        expect(css.replace(/\s+/g, '')).toContain('grid-template-columns:minmax(260px,320px)minmax(0,1fr);');
        expect(css).toContain('.repository-guided-workflow');
        expect(css).toContain('.repository-profile-grid');
        expect(css.replace(/\s+/g, '')).toContain('repeat(2,minmax(280px,1fr))');
    });

    it('offers GitHub, GitLab, generic Git, OneDrive, Google Drive and Dropbox', () => {
        const component = source('src/repository-connections/RepositoryConnectionsCenter.tsx') + source('src/repository-connections/RepositoryConnectionsGuidedSteps.tsx');
        const storageTypes = source('src/remote-storage/types.ts');
        const providerEnum = source('../src/main/java/com/example/editor/remote/storage/RemoteStorageProviderType.java');
        for (const phrase of ['GENERIC_GIT', 'GOOGLE_DRIVE', 'DROPBOX']) {
            expect(component).toContain(phrase);
            expect(storageTypes).toContain(phrase);
            expect(providerEnum).toContain(phrase);
        }
        expect(component).toContain('Generic Git repository URL');
        expect(component).toContain('Google Drive');
        expect(component).toContain('Dropbox');
    });

    it('keeps the guided repository copy translatable in the Spanish catalogue', () => {
        const translated: Readonly<Record<string, string>> = {
            'Guided repository setup': 'Configuración guiada del repositorio',
            'Repository or folder URL': 'URL del repositorio o carpeta',
            'Recognize destination': 'Reconocer destino',
            'Generic Git repository URL is required.': 'La URL del repositorio Git es obligatoria.',
            'Create one profile for each Git, GitHub, GitLab, OneDrive, Google Drive or Dropbox destination that HYDRA should reuse.': 'Crea una conexión por cada destino Git, GitHub, GitLab, OneDrive, Google Drive o Dropbox que HYDRA deba reutilizar.',
        };
        for (const [english, spanish] of Object.entries(translated)) expect(translateUiText(english, 'es')).toBe(spanish);
        expect(translateUiText('Generic Git', 'es')).toBe('Git genérico');
        expect(translateUiText('Google Drive', 'es')).toBe('Google Drive');
    });
});
