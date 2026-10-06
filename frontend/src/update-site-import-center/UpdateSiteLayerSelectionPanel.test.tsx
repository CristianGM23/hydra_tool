import { fireEvent, render, screen, within } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { UpdateSiteLayerSelectionPanel } from './UpdateSiteLayerSelectionPanel';
import { emptyLayerSelection, recommendedLayerSelection } from './updateSiteLayerSelection';
import type { UpdateSiteImportAnalysis } from './types';

const analysis = {
    recoveredLayers: {
        palettes: [{ id: 'objects', label: 'Objects', order: 0, sourcePath: 'library.gmftool', confidence: 'DERIVED',
            tools: [
                { id: 'book', label: 'Create book', kind: 'NODE', targetElement: 'Book', sourcePath: 'library.gmftool', confidence: 'DERIVED' },
                { id: 'author', label: 'Create author', kind: 'NODE', targetElement: 'Author', sourcePath: 'library.gmftool', confidence: 'INFERRED' }
            ] }],
        representations: [{ id: 'diagram', name: 'Library diagram', technology: 'GMF', kind: 'DIAGRAM', rootElements: ['Library'], sourcePath: 'library.gmfgraph', confidence: 'RECOVERED', warnings: [] }],
        mappings: [{ id: 'book-node', semanticElement: 'Book', visualElement: 'BookFigure', mappingKind: 'NODE', sourcePath: 'library.gmfmap', confidence: 'DERIVED', diagnostic: '' }],
        diagnostics: []
    },
    semanticRecovery: { elements: [
        { id: 'command:open', kind: 'COMMAND', label: 'Open', implementation: '', sourcePath: 'plugin.xml', extensionPoint: 'commands', confidence: 'RECOVERED', representable: true, neutralTarget: 'Operation', purpose: 'Opens the model.', warnings: [] },
        { id: 'custom:lost', kind: 'UNREPRESENTABLE', label: 'Custom', implementation: '', sourcePath: 'plugin.xml', extensionPoint: 'custom', confidence: 'RECOVERED', representable: false, neutralTarget: '', purpose: 'Unknown.', warnings: [] }
    ], representableCount: 1, partialCount: 0, unrepresentableCount: 1, diagnostics: [] }
} as unknown as UpdateSiteImportAnalysis;

describe('UpdateSiteLayerSelectionPanel', () => {
    it('builds a recommended granular selection and excludes inferred or unrepresentable drafts', () => {
        const selection = recommendedLayerSelection(analysis);
        expect(selection.paletteGroupIds).toEqual(['objects']);
        expect(selection.paletteToolIds).toEqual(['book']);
        expect(selection.semanticElementIds).toEqual(['command:open']);
        expect(selection.explicit).toBe(true);
    });

    it('selects individual tools without forcing every tool in the palette group', () => {
        const onChange = vi.fn();
        render(<UpdateSiteLayerSelectionPanel analysis={analysis} selection={emptyLayerSelection()} onChange={onChange} />);
        const palette = screen.getByText('Palette groups and tools').closest('section');
        fireEvent.click(within(palette as HTMLElement).getByLabelText('Create book'));

        expect(onChange).toHaveBeenCalledWith(expect.objectContaining({
            paletteGroupIds: ['objects'], paletteToolIds: ['book'], explicit: true
        }));
        expect(screen.getByLabelText('Custom')).toBeDisabled();
    });

    it('removes the palette group when its last selected tool is cleared', () => {
        const onChange = vi.fn();
        const selection = recommendedLayerSelection(analysis);
        render(<UpdateSiteLayerSelectionPanel analysis={analysis} selection={selection} onChange={onChange} />);

        fireEvent.click(screen.getByLabelText('Create book'));

        expect(onChange).toHaveBeenCalledWith(expect.objectContaining({
            paletteGroupIds: [], paletteToolIds: [], explicit: true
        }));
    });

});
