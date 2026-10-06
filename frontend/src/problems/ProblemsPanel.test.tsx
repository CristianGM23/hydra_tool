import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { ModelDocument } from '../types/editor';
import type { OrchestratedValidationResult } from './types';
import { ProblemsPanel } from './ProblemsPanel';
import * as api from './problemsApi';

vi.mock('./problemsApi');
const document: ModelDocument = { id: 'model', metamodelId: 'people', fileName: 'people.xmi', rootElementId: 'element-1', dirty: true, elements: [{ id: 'element-1', classId: 'people#//Person', className: 'Person', containerId: null, containmentReference: null, attributes: { name: 'Bob' } }], references: [] };
const validation: OrchestratedValidationResult = {
    report: { schemaVersion: '1.0', id: 'report', modelId: 'model', metamodelId: 'people', validationModelId: 'validations', generatedAt: '2026-07-27T00:00:00Z', scope: { kind: 'MODEL' }, diagnostics: [{ id: 'diagnostic:one', kind: 'VIOLATION', ruleId: 'rule:name', severity: 'ERROR', message: 'Name is invalid', location: { modelElementId: 'element-1', featureId: 'attribute:name' }, quickFixIds: ['action:fix'], details: { problemCode: 'INVALID_NAME' } }], metrics: { evaluatedRules: 1, skippedRules: 0, durationMillis: 1 } },
    delta: { created: [], unchanged: [], resolved: [] }, blocking: { gate: 'NONE', blocked: false, diagnosticIds: [], reason: '' }
};

function renderPanel() {
    const props = { document, selectedElementId: 'element-1', onSelectElement: vi.fn(), onDocumentChange: vi.fn(), onReportChange: vi.fn(), onError: vi.fn(), onMessage: vi.fn() };
    render(<ProblemsPanel {...props} />);
    return props;
}

beforeEach(() => vi.clearAllMocks());

describe('ProblemsPanel', () => {
    it('starts without inventing diagnostics', () => {
        renderPanel();
        expect(screen.getByText('Not validated')).toBeInTheDocument();
        expect(screen.getByText('Run validation to populate this panel.')).toBeInTheDocument();
    });

    it('shows current diagnostics after validation', async () => {
        vi.mocked(api.validateLiveModel).mockResolvedValue(validation);
        const props = renderPanel();
        fireEvent.click(screen.getByRole('button', { name: 'Validate' }));
        expect(await screen.findByText('Name is invalid')).toBeInTheDocument();
        expect(props.onReportChange).toHaveBeenCalledWith(validation.report);
    });

    it('navigates to the affected element and resolves offered fixes', async () => {
        vi.mocked(api.validateLiveModel).mockResolvedValue(validation);
        vi.mocked(api.loadDiagnosticQuickFixes).mockResolvedValue([{ actionId: 'action:fix', name: 'Repair name', description: '', enabled: true, parameters: [], transactionPolicy: 'ATOMIC', undoPolicy: 'REQUIRED' }]);
        const props = renderPanel();
        fireEvent.click(screen.getByRole('button', { name: 'Validate' }));
        fireEvent.click(await screen.findByText('Name is invalid'));
        await waitFor(() => expect(props.onSelectElement).toHaveBeenCalledWith('element-1'));
        expect(await screen.findByRole('option', { name: 'Repair name' })).toBeInTheDocument();
    });

    it('previews without changing the document', async () => {
        vi.mocked(api.validateLiveModel).mockResolvedValue(validation);
        vi.mocked(api.loadDiagnosticQuickFixes).mockResolvedValue([{ actionId: 'action:fix', name: 'Repair name', description: '', enabled: true, parameters: [], transactionPolicy: 'ATOMIC', undoPolicy: 'REQUIRED' }]);
        vi.mocked(api.previewQuickFix).mockResolvedValue({ diagnosticId: 'diagnostic:one', actionId: 'action:fix', previewDocument: { schemaVersion: '1.0', id: 'model', metamodelId: 'people', rootElementIds: [], elements: [], relations: [], annotations: {} }, changes: { createdElementIds: [], deletedElementIds: [], modifiedElementIds: ['element-1'], attributeIds: ['attribute:name'], relationIds: [], classIds: [], validationChangeSet: { elementIds: ['element-1'], classIds: [], attributeIds: ['attribute:name'], relationIds: [], operationIds: [], modelChanged: false } }, messages: [] });
        const props = renderPanel();
        fireEvent.click(screen.getByRole('button', { name: 'Validate' }));
        fireEvent.click(await screen.findByText('Name is invalid'));
        fireEvent.click(await screen.findByRole('button', { name: 'Preview' }));
        expect(await screen.findByText('The live model has not been modified.')).toBeInTheDocument();
        expect(props.onDocumentChange).not.toHaveBeenCalled();
    });

    it('applies the fix and refreshes the report', async () => {
        vi.mocked(api.validateLiveModel).mockResolvedValue(validation);
        vi.mocked(api.loadDiagnosticQuickFixes).mockResolvedValue([{ actionId: 'action:fix', name: 'Repair name', description: '', enabled: true, parameters: [], transactionPolicy: 'ATOMIC', undoPolicy: 'REQUIRED' }]);
        const resolved = { ...validation, report: { ...validation.report, diagnostics: [] }, delta: { created: [], unchanged: [], resolved: validation.report.diagnostics } };
        vi.mocked(api.applyQuickFix).mockResolvedValue({ diagnosticId: 'diagnostic:one', actionId: 'action:fix', status: 'APPLIED_RESOLVED', diagnosticResolved: true, document: { ...document, dirty: true }, changes: { createdElementIds: [], deletedElementIds: [], modifiedElementIds: ['element-1'], attributeIds: ['attribute:name'], relationIds: [], classIds: [], validationChangeSet: { elementIds: ['element-1'], classIds: [], attributeIds: ['attribute:name'], relationIds: [], operationIds: [], modelChanged: false } }, validation: resolved, undoAvailable: true, redoAvailable: false, messages: [] });
        const props = renderPanel();
        fireEvent.click(screen.getByRole('button', { name: 'Validate' }));
        fireEvent.click(await screen.findByText('Name is invalid'));
        fireEvent.click(await screen.findByRole('button', { name: 'Apply' }));
        await waitFor(() => expect(props.onDocumentChange).toHaveBeenCalled());
        expect(props.onReportChange).toHaveBeenLastCalledWith(resolved.report);
    });
});
