import { fireEvent, render, screen, within } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { UpdateSiteReleaseComparisonPanel } from './UpdateSiteReleaseComparisonPanel';
import type { UpdateSiteReleaseComparison } from './types';

const release: UpdateSiteReleaseComparison = {
    compositeRepositories: [{ sourcePath: 'compositeContent.xml', location: 'child', remote: false, presentInUpload: false, explanation: 'Upload the child.' }],
    versionFamilies: [{ id: 'library', artifactType: 'BUNDLE', versions: ['1.0.0', '2.0.0'], oldestVersion: '1.0.0', latestVersion: '2.0.0', multipleVersions: true, recommendation: 'Compare.' }],
    deltas: [{ id: 'library', artifactType: 'BUNDLE', fromVersion: '1.0.0', toVersion: '2.0.0',
        addedArtifacts: ['diagram/library.odesign'], removedArtifacts: ['icons/old.gif'],
        changedArtifacts: ['model/library.ecore'], unchangedArtifacts: 8, modelBearing: true,
        risk: 'HIGH', explanation: 'Model evidence changed.', recommendation: 'Re-run metamodel recovery.' }],
    unresolvedCompositeChildren: 1,
    diagnostics: []
};

describe('UpdateSiteReleaseComparisonPanel', () => {
    it('shows content-aware release risk and high-level purposes for changed artifacts', () => {
        render(<UpdateSiteReleaseComparisonPanel release={release} />);
        expect(screen.getAllByText('HIGH').length).toBeGreaterThanOrEqual(1);
        expect(screen.getByText('Re-run metamodel recovery.')).toBeInTheDocument();
        fireEvent.click(screen.getByText('model/library.ecore'));
        expect(screen.getByText(/Defines packages, classes, attributes/i)).toBeInTheDocument();
        const unresolved = screen.getByText('Unresolved child repositories').closest('article');
        expect(unresolved).not.toBeNull();
        expect(within(unresolved as HTMLElement).getByText('1')).toBeInTheDocument();
    });
});
