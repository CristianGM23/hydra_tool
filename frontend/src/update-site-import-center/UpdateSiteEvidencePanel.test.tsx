import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { UpdateSiteEvidencePanel } from './UpdateSiteEvidencePanel';
import type { UpdateSiteImportAnalysis } from './types';

const analysis = {
    provenance: [
        { elementId: 'class:Book', elementPath: '//Book', origin: 'library.model.jar',
            sourcePath: 'model/library.ecore', format: 'ECORE', originalElement: '//Book',
            method: 'DIRECT_METAMODEL', confidence: 'EXACT', warnings: [] },
        { elementId: 'class:Author', elementPath: '//Author', origin: 'library.model.jar',
            sourcePath: 'LibraryPackageImpl.class', format: 'JAVA_CLASS', originalElement: 'getAuthor',
            method: 'EMF_BYTECODE', confidence: 'RECOVERED', warnings: ['Documentation was not recovered'] }
    ]
} as unknown as UpdateSiteImportAnalysis;

describe('UpdateSiteEvidencePanel', () => {
    it('filters provenance by text and confidence while preserving warnings and source detail', () => {
        render(<UpdateSiteEvidencePanel analysis={analysis} />);

        expect(screen.getByLabelText('Neutral path //Book')).toBeInTheDocument();
        expect(screen.getByLabelText('Neutral path //Author')).toBeInTheDocument();
        expect(screen.getByText('Documentation was not recovered')).toBeInTheDocument();
        expect(screen.getByText('Same as neutral path')).toBeInTheDocument();

        fireEvent.change(screen.getByLabelText('Confidence'), { target: { value: 'EXACT' } });
        expect(screen.getByLabelText('Neutral path //Book')).toBeInTheDocument();
        expect(screen.queryByLabelText('Neutral path //Author')).not.toBeInTheDocument();

        fireEvent.change(screen.getByLabelText('Confidence'), { target: { value: 'ALL' } });
        fireEvent.change(screen.getByLabelText('Search evidence'), { target: { value: 'PackageImpl' } });
        expect(screen.getByLabelText('Neutral path //Author')).toBeInTheDocument();
        expect(screen.queryByLabelText('Neutral path //Book')).not.toBeInTheDocument();
    });
});
