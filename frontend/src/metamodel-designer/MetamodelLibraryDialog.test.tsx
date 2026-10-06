import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { MetamodelLibraryDialog } from './MetamodelLibraryDialog';

const models = [
    { id:'people', name:'People', schemaVersion:'2.1', etag:'p1' },
    { id:'orders', name:'Orders', schemaVersion:'2.1', etag:'o1' }
];

describe('MetamodelLibraryDialog', () => {
    it('supports single deletion from a row', async () => {
        const onDelete = vi.fn().mockResolvedValue({ deletedIds:['people'], failed:{} });
        render(<MetamodelLibraryDialog open metamodels={models} activeMetamodelId="people" busy={false} onClose={vi.fn()} onOpen={vi.fn()} onDelete={onDelete} />);
        fireEvent.click(screen.getAllByRole('button', { name:'Delete' })[0]);
        fireEvent.click(screen.getByRole('button', { name:'Delete metamodel' }));
        await vi.waitFor(() => expect(onDelete).toHaveBeenCalledWith(['people'], { deleteNotations:true, deleteExports:true }));
    });

    it('supports multiple and all selection', () => {
        render(<MetamodelLibraryDialog open metamodels={models} activeMetamodelId="" busy={false} onClose={vi.fn()} onOpen={vi.fn()} onDelete={vi.fn().mockResolvedValue({ deletedIds:[], failed:{} })} />);
        fireEvent.click(screen.getByRole('button', { name:'Select all' }));
        expect(screen.getByRole('button', { name:'Delete selected (2)' })).toBeTruthy();
        fireEvent.click(screen.getByRole('button', { name:'Delete all' }));
        expect(screen.getByText('Delete selected metamodels?')).toBeTruthy();
    });

    it('reviews lifecycle impact before deleting several metamodels', async () => {
        const onLoadImpact = vi.fn(async (id: string) => ({ metamodelId:id, canonicalFile:`${id}.metamodel.json`, ecoreFile:`${id}.ecore`, ecoreExists:true, diagramFile:`${id}.metamodel-diagram.json`, diagramExists:true, editorDefinitionFiles:[`${id}.editor.json`], integrationProfileFiles:[`${id}.integration.json`], notationFiles:[`${id}.notation.json`], exportFiles:[`${id}.zip`], warning:'Review dependent artifacts before deletion.' }));
        render(<MetamodelLibraryDialog open metamodels={models} activeMetamodelId="people" initialSelectedIds={['people','orders']} busy={false} onClose={vi.fn()} onOpen={vi.fn()} onLoadImpact={onLoadImpact} onDelete={vi.fn().mockResolvedValue({ deletedIds:[], failed:{} })} />);
        fireEvent.click(screen.getByRole('button', { name:'Delete selected (2)' }));
        await screen.findByText('Affected files');
        expect(await screen.findByText('people.integration.json')).toBeTruthy();
        expect(screen.getByText('orders.editor.json')).toBeTruthy();
        expect(onLoadImpact).toHaveBeenCalledTimes(2);
        expect(screen.getByLabelText(/Remove generated notations/i)).toBeChecked();
        expect(screen.getByLabelText(/Remove generated exports/i)).toBeChecked();
    });

    it('blocks destructive confirmation when impact inspection fails and allows retrying it', async () => {
        const onLoadImpact = vi.fn().mockRejectedValueOnce(new Error('impact unavailable')).mockResolvedValue({ metamodelId:'people', canonicalFile:'people.metamodel.json', ecoreFile:'people.ecore', ecoreExists:true, diagramFile:'people.metamodel-diagram.json', diagramExists:true, editorDefinitionFiles:[], integrationProfileFiles:[], notationFiles:[], exportFiles:[], warning:'Review dependent artifacts before deletion.' });
        const onDelete = vi.fn().mockResolvedValue({ deletedIds:['people'], failed:{} });
        render(<MetamodelLibraryDialog open metamodels={models} activeMetamodelId="people" busy={false} onClose={vi.fn()} onOpen={vi.fn()} onLoadImpact={onLoadImpact} onDelete={onDelete} />);

        fireEvent.click(screen.getAllByRole('button', { name:'Delete' })[0]);
        expect(await screen.findByText('Some deletion impacts could not be inspected.')).toBeTruthy();
        expect(screen.getByRole('button', { name:'Delete metamodel' })).toBeDisabled();
        fireEvent.click(screen.getByRole('button', { name:'Retry inspection' }));
        await vi.waitFor(() => expect(onLoadImpact).toHaveBeenCalledTimes(2));
        await vi.waitFor(() => expect(screen.getByRole('button', { name:'Delete metamodel' })).toBeEnabled());
    });

    it('keeps only failed items pending after a partial batch deletion', async () => {
        const onDelete = vi.fn().mockResolvedValue({ deletedIds:['people'], failed:{ orders:'etag conflict' }, warnings:{ people:'residual cleanup pending' } });
        render(<MetamodelLibraryDialog open metamodels={models} activeMetamodelId="people" initialSelectedIds={['people','orders']} busy={false} onClose={vi.fn()} onOpen={vi.fn()} onDelete={onDelete} />);

        fireEvent.click(screen.getByRole('button', { name:'Delete selected (2)' }));
        fireEvent.click(screen.getByRole('button', { name:'Delete 2 metamodels' }));

        expect(await screen.findByText(/Orders: etag conflict/i)).toBeTruthy();
        expect(screen.getByText('Deletion completed with cleanup warnings')).toBeTruthy();
        expect(screen.getByRole('button', { name:'Delete metamodel' })).toBeEnabled();
    });

    it('opens a saved metamodel directly', async () => {
        const onOpen = vi.fn().mockResolvedValue(undefined);
        render(<MetamodelLibraryDialog open metamodels={models} activeMetamodelId="" busy={false} onClose={vi.fn()} onOpen={onOpen} onDelete={vi.fn().mockResolvedValue({ deletedIds:[], failed:{} })} />);
        fireEvent.click(screen.getAllByRole('button', { name:'Open' })[1]);
        await vi.waitFor(() => expect(onOpen).toHaveBeenCalledWith('orders'));
    });
});
