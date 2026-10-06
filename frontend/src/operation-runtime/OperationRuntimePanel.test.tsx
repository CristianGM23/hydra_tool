import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import type { ModelDocument } from '../types/editor';
import type { OperationRuntimeCatalog } from '../validation/types';
import { OperationRuntimePanel } from './OperationRuntimePanel';

const mocks = vi.hoisted(() => ({ invoke: vi.fn(), undo: vi.fn() }));
vi.mock('./operationRuntimeApi', () => ({ invokeModelOperation: mocks.invoke, undoModelOperation: mocks.undo, operationRuntimeCatalogUrl: () => '/catalog' }));

const document: ModelDocument = { id: 'model', metamodelId: 'shop', fileName: 'model.xmi', rootElementId: 'customer:1', dirty: false, elements: [{ id: 'customer:1', classId: 'vip-customer', className: 'VipCustomer', containerId: null, containmentReference: null, attributes: {} }], references: [] };
const catalog: OperationRuntimeCatalog = { metamodelId: 'shop', operations: [{ operationId: 'operation:Customer:normalize', ownerClassId: 'customer', ownerClassName: 'Customer', applicableClassIds: ['customer', 'vip-customer'], applicableClassNames: ['Customer', 'VipCustomer'], name: 'normalize', behaviorKind: 'COMMAND', parameters: [], returnTypeLabel: 'void', implementationKind: 'ACTION', enabled: true, surfaces: ['BUTTON', 'FORM', 'PALETTE', 'MENU'] }] };

describe('integrated operation runtime panel', () => {
    it('executes a selected-element operation and enables undo', async () => {
        const updated = { ...document, dirty: true };
        mocks.invoke.mockResolvedValue({ operationId: 'operation:Customer:normalize', document: updated, undoAvailable: true, messages: ['Normalized'] });
        const onDocumentChange = vi.fn();
        render(<OperationRuntimePanel catalog={catalog} document={document} selectedElementId="customer:1" onDocumentChange={onDocumentChange} onError={vi.fn()} onMessage={vi.fn()} />);
        fireEvent.click(screen.getAllByRole('button', { name: /normalize/i })[0]);
        await waitFor(() => expect(mocks.invoke).toHaveBeenCalledWith('model', 'operation:Customer:normalize', { targetElementId: 'customer:1', arguments: {}, selectionElementIds: ['customer:1'] }));
        expect(onDocumentChange).toHaveBeenCalledWith(updated);
        expect(screen.getByRole('button', { name: 'Undo last operation' })).toBeEnabled();
    });
});
