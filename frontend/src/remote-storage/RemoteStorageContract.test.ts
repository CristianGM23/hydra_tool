import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';
import { translateUiText } from '../i18n/translationCatalog';

function source(path: string): string {
    return readFileSync(resolve(process.cwd(), path), 'utf8');
}

describe('HYDRA V4.27.0 neutral remote storage contract', () => {
    it('keeps credentials out of browser contracts and exposes only configured profile descriptors', () => {
        const types = source('src/remote-storage/types.ts');
        const api = source('src/remote-storage/api.ts');
        expect(types).not.toMatch(/\btoken\s*:/);
        expect(types).not.toMatch(/\bpassword\s*:/);
        expect(api).toContain("'/api/remote-storage/profiles'");
    });

    it('supports complete HYDRA project save and import through portable project endpoints', () => {
        const api = source('src/remote-storage/api.ts');
        const project = source('src/project-explorer/ProjectExplorerWorkspace.tsx');
        expect(api).toContain("'/api/remote-storage/projects/export'");
        expect(api).toContain("'/api/remote-storage/projects/import'");
        expect(project).toContain('RemoteProjectStorageDialog');
        expect(project).toContain('Save to repository');
    });

    it('adds remote transfer to shared independent layer interchange', () => {
        const api = source('src/remote-storage/api.ts');
        const layer = source('src/layer-io/LayerInterchangeDialog.tsx');
        expect(api).toContain('/api/remote-storage/layers/export?');
        expect(api).toContain('/api/remote-storage/layers/import?');
        expect(layer).toContain('RemoteStorageProfileFields');
        expect(layer).toContain('loadRemoteLayerArtifact');
        expect(layer).toContain('saveRemoteLayerArtifact');
    });

    it('allows the neutral metamodel layer to import and export remote artifacts without bypassing its normal pipelines', () => {
        const importer = source('src/metamodel-designer/MetamodelImportDialog.tsx');
        const exporter = source('src/metamodel-designer/MetamodelExportDialog.tsx');
        expect(importer).toContain('loadRemoteFileArtifact');
        expect(importer).toContain('selectFileAndAnalyze(artifact.file)');
        expect(exporter).toContain('generateMetamodelExport');
        expect(exporter).toContain('saveRemoteLayerArtifact');
    });

    it('supports independent remote transfer for editor definitions and integration profiles without growing their main designers', () => {
        const editor = source('src/editor-definition/EditorDefinitionDesigner.tsx');
        const editorRemote = source('src/editor-definition/EditorDefinitionRemoteStorage.tsx');
        const integration = source('src/integration-designer/IntegrationDesigner.tsx');
        const integrationRemote = source('src/integration-designer/IntegrationRemoteStorage.tsx');
        expect(editor).toContain('EditorDefinitionRemoteStorage');
        expect(editorRemote).toContain('RemoteJsonDocumentDialog');
        expect(editorRemote).toContain('normalizeEditorDefinition');
        expect(integration).toContain('IntegrationRemoteStorage');
        expect(integrationRemote).toContain('RemoteJsonDocumentDialog');
        expect(integrationRemote).toContain('normalizeIntegrationProfile');
    });

    it('keeps provider names technical while adding Spanish UI coverage for the new remote workflow', () => {
        expect(translateUiText('Remote storage', 'es')).toBe('Almacenamiento remoto');
        for (const provider of ['GitHub', 'GitLab', 'OneDrive', 'Google Drive', 'Dropbox']) {
            expect(translateUiText(provider, 'es')).toBe(provider);
        }
    });
});
