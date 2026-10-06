import { HydraButton } from '../design-system/HydraButton';
import { useMemo, useState } from 'react';
import type { ChangeEvent } from 'react';
import { HydraAdaptiveSelect } from '../components/HydraAdaptiveSelect';
import { HydraDiagnosticList } from '../design-system';
import { useHydraLanguage } from '../i18n';
import { ResizableInfoTooltip } from '../designer-ui/ResizableInfoTooltip';
import type { ModelDocument } from '../types/editor';
import type { ActionParameter, ModelValidationDiagnostic, ModelValidationReport, ValidationSeverity } from '../validation/types';
import { applyQuickFix, loadDiagnosticQuickFixes, previewQuickFix, redoQuickFix, undoQuickFix, validateLiveModel } from './problemsApi';
import type { OrchestratedValidationResult, QuickFixDescriptor, QuickFixExecutionRequest, QuickFixPreviewResult } from './types';
import './problems.css';

interface ProblemsPanelProps {
    document: ModelDocument | null;
    selectedElementId: string;
    onSelectElement: (elementId: string) => void;
    onDocumentChange: (document: ModelDocument) => void;
    onReportChange: (report: ModelValidationReport | null) => void;
    onError: (message: string) => void;
    onMessage: (message: string) => void;
}

type SeverityFilter = 'ALL' | ValidationSeverity;

export function ProblemsPanel({ document, selectedElementId, onSelectElement, onDocumentChange, onReportChange, onError, onMessage }: ProblemsPanelProps) {
    const { t } = useHydraLanguage();
    const [result, setResult] = useState<OrchestratedValidationResult | null>(null);
    const [selectedDiagnosticId, setSelectedDiagnosticId] = useState('');
    const [severity, setSeverity] = useState<SeverityFilter>('ALL');
    const [query, setQuery] = useState('');
    const [quickFixes, setQuickFixes] = useState<QuickFixDescriptor[]>([]);
    const [selectedActionId, setSelectedActionId] = useState('');
    const [argumentsByName, setArgumentsByName] = useState<Record<string, unknown>>({});
    const [preview, setPreview] = useState<QuickFixPreviewResult | null>(null);
    const [busy, setBusy] = useState(false);
    const [undoAvailable, setUndoAvailable] = useState(false);
    const [redoAvailable, setRedoAvailable] = useState(false);

    const selectedDiagnostic = result?.report.diagnostics.find(item => item.id === selectedDiagnosticId) ?? null;
    const selectedAction = quickFixes.find(item => item.actionId === selectedActionId) ?? null;
    const filtered = useMemo(() => {
        const normalized = query.trim().toLowerCase();
        return (result?.report.diagnostics ?? []).filter(item => {
            if(severity !== 'ALL' && item.severity !== severity) return false;
            if(normalized === '') return true;
            return `${item.message} ${item.ruleId} ${item.details?.problemCode ?? ''}`.toLowerCase().includes(normalized);
        });
    }, [query, result, severity]);

    async function validate(): Promise<void> {
        if(document === null) return;
        setBusy(true);
        try {
            const next = await validateLiveModel(document.id);
            applyValidationResult(next);
            onMessage(`Validation completed: ${next.report.diagnostics.length} problem(s), ${next.delta.resolved.length} resolved`);
        } catch(exception) { onError(message(exception, 'Could not validate the model')); }
        finally { setBusy(false); }
    }

    async function selectDiagnostic(diagnostic: ModelValidationDiagnostic): Promise<void> {
        setSelectedDiagnosticId(diagnostic.id);
        setPreview(null);
        if(diagnostic.location?.modelElementId) onSelectElement(diagnostic.location.modelElementId);
        if(document === null || diagnostic.quickFixIds.length === 0) {
            setQuickFixes([]); setSelectedActionId(''); return;
        }
        setQuickFixes([]);
        setSelectedActionId('');
        setArgumentsByName({});
        setBusy(true);
        try {
            const resolved = await loadDiagnosticQuickFixes(document.id, diagnostic.id);
            setQuickFixes(resolved);
            setSelectedActionId(resolved[0]?.actionId ?? '');
            setArgumentsByName(defaultArguments(resolved[0]?.parameters ?? []));
        } catch(exception) { onError(message(exception, 'Could not resolve quick fixes')); }
        finally { setBusy(false); }
    }

    function selectAction(actionId: string): void {
        setSelectedActionId(actionId);
        const action = quickFixes.find(item => item.actionId === actionId);
        setArgumentsByName(defaultArguments(action?.parameters ?? []));
        setPreview(null);
    }

    async function previewSelected(): Promise<void> {
        if(document === null || selectedDiagnostic === null || selectedAction === null) return;
        setBusy(true);
        try {
            setPreview(await previewQuickFix(document.id, selectedDiagnostic.id, selectedAction.actionId, executionRequest()));
            onMessage('Quick-fix preview calculated without modifying the model');
        } catch(exception) { onError(message(exception, 'Could not preview quick fix')); }
        finally { setBusy(false); }
    }

    async function applySelected(): Promise<void> {
        if(document === null || selectedDiagnostic === null || selectedAction === null) return;
        setBusy(true);
        try {
            const applied = await applyQuickFix(document.id, selectedDiagnostic.id, selectedAction.actionId, executionRequest());
            onDocumentChange(applied.document);
            applyValidationResult(applied.validation);
            setUndoAvailable(applied.undoAvailable);
            setRedoAvailable(applied.redoAvailable);
            setPreview(null);
            setSelectedDiagnosticId(applied.diagnosticResolved ? '' : selectedDiagnostic.id);
            onMessage(`${selectedAction.name}: ${statusLabel(applied.status)}`);
        } catch(exception) { onError(message(exception, 'Could not apply quick fix')); }
        finally { setBusy(false); }
    }

    async function compensate(kind: 'UNDO' | 'REDO'): Promise<void> {
        if(document === null) return;
        setBusy(true);
        try {
            const applied = kind === 'UNDO' ? await undoQuickFix(document.id) : await redoQuickFix(document.id);
            onDocumentChange(applied.document);
            applyValidationResult(applied.validation);
            setUndoAvailable(applied.undoAvailable);
            setRedoAvailable(applied.redoAvailable);
            setPreview(null);
            onMessage(applied.messages[0] ?? (kind === 'UNDO' ? 'Quick fix was undone' : 'Quick fix was redone'));
        } catch(exception) { onError(message(exception, `Could not ${kind.toLowerCase()} quick fix`)); }
        finally { setBusy(false); }
    }

    function applyValidationResult(next: OrchestratedValidationResult): void {
        setResult(next);
        onReportChange(next.report);
        if(selectedDiagnosticId !== '' && !next.report.diagnostics.some(item => item.id === selectedDiagnosticId)) {
            setSelectedDiagnosticId(''); setQuickFixes([]); setSelectedActionId('');
        }
    }

    function executionRequest(): QuickFixExecutionRequest {
        return { arguments: argumentsByName, selectionElementIds: selectedElementId === '' ? [] : [selectedElementId] };
    }

    function updateArgument(parameter: ActionParameter, event: ChangeEvent<HTMLInputElement>): void {
        const value = parameter.type === 'BOOLEAN' ? event.target.checked : parseValue(parameter, event.target.value);
        setArgumentsByName(current => ({ ...current, [parameter.name]: value }));
        setPreview(null);
    }

    return (
        <section className="problems-panel" aria-label="Problems">
            <header>
                <div><strong>Problems</strong><span>{result === null ? 'Not validated' : `${result.report.diagnostics.length} current`}</span></div>
                <div className="problems-header-actions">
                    <HydraButton disabled={document === null || busy} type="button" onClick={() => void validate()}>{busy ? 'Working…' : 'Validate'}</HydraButton>
                    <HydraButton disabled={!undoAvailable || busy} type="button" onClick={() => void compensate('UNDO')}>Undo fix</HydraButton>
                    <HydraButton disabled={!redoAvailable || busy} type="button" onClick={() => void compensate('REDO')}>Redo fix</HydraButton>
                </div>
            </header>
            <div className="problems-filters">
                <HydraAdaptiveSelect ariaLabel="Problem severity" value={severity} options={[{ value:'ALL',label:'All severities',description:'Show every current validation problem.',category:'All problems' },{ value:'ERROR',label:'Errors',description:'Show only critical or blocking problems.',category:'Specific severity' },{ value:'WARNING',label:'Warnings',description:'Show only important non-blocking problems.',category:'Specific severity' },{ value:'INFO',label:'Information',description:'Show only guidance and informational problems.',category:'Specific severity' }]} onChange={next => setSeverity(next as SeverityFilter)} />
                <input aria-label="Search problems" placeholder="Search problems" value={query} onChange={event => setQuery(event.target.value)} />
            </div>
            {result?.blocking.blocked && <div className="problems-blocking">{result.blocking.reason}</div>}
            <div className="problems-list">
                {filtered.length === 0 && <p className="problems-empty">{result === null ? 'Run validation to populate this panel.' : 'No problems match the filters.'}</p>}
                {filtered.map(diagnostic => <HydraButton className={`problem-row severity-${diagnostic.severity.toLowerCase()}${selectedDiagnosticId === diagnostic.id ? ' selected' : ''}`} key={diagnostic.id} type="button" onClick={() => void selectDiagnostic(diagnostic)}>
                    <span className="problem-severity">{diagnostic.severity}</span>
                    <span><strong>{diagnostic.details?.problemCode || diagnostic.ruleId}</strong><small data-hydra-runtime-message="true" data-hydra-message-context="problems" data-hydra-message-code={diagnostic.details?.problemCode || diagnostic.ruleId} data-hydra-message-severity={diagnostic.severity}>{diagnostic.message}</small><em>{diagnostic.location?.modelElementId || 'Model'}</em></span>
                    {diagnostic.quickFixIds.length > 0 && <span className="problem-fix-count">{diagnostic.quickFixIds.length} fix</span>}
                </HydraButton>)}
            </div>
            {selectedDiagnostic !== null && <div className="quickfix-area">
                <HydraDiagnosticList
                    diagnostics={[{
                        severity: selectedDiagnostic.severity,
                        code: selectedDiagnostic.details?.problemCode || selectedDiagnostic.ruleId,
                        message: selectedDiagnostic.message,
                        path: selectedDiagnostic.location?.featureId || selectedDiagnostic.location?.relationInstanceId || '',
                        elementId: selectedDiagnostic.location?.modelElementId || '',
                    }]}
                    context="model validation"
                    title={t('Selected problem')}
                    onSelectElement={onSelectElement}
                />
                <header><strong>{t('Quick fixes')}</strong></header>
                {quickFixes.length === 0 && <p>No executable quick fix is available.</p>}
                {quickFixes.length > 0 && <>
                    <div className="problems-quickfix-selector"><ResizableInfoTooltip label="Information about quick-fix choices" title="Available quick fixes">Each value is a transactional repair offered for the selected diagnostic. Its description explains the intended change before preview or application.</ResizableInfoTooltip><HydraAdaptiveSelect ariaLabel="Quick fix" value={selectedActionId} options={quickFixes.map(action => ({ value:action.actionId,label:action.name,description:action.description || 'Executable repair for the selected validation problem.',category:action.parameters.length > 0 ? 'Requires parameters' : 'Ready to preview' }))} onChange={selectAction} /></div>
                    {selectedAction?.description && <p>{selectedAction.description}</p>}
                    {selectedAction?.parameters.map(parameter => <label key={parameter.name}>{parameter.name}{parameter.required && ' *'}
                        <input checked={parameter.type === 'BOOLEAN' ? Boolean(argumentsByName[parameter.name]) : undefined} type={parameter.type === 'BOOLEAN' ? 'checkbox' : parameter.type === 'INTEGER' || parameter.type === 'DECIMAL' ? 'number' : 'text'} value={parameter.type === 'BOOLEAN' ? undefined : String(argumentsByName[parameter.name] ?? '')} onChange={event => updateArgument(parameter, event)} />
                    </label>)}
                    <div className="quickfix-actions"><HydraButton disabled={busy} type="button" onClick={() => void previewSelected()}>Preview</HydraButton><HydraButton className="quickfix-apply" disabled={busy || !requiredArgumentsPresent(selectedAction, argumentsByName)} type="button" onClick={() => void applySelected()}>Apply</HydraButton></div>
                </>}
                {preview !== null && <div className="quickfix-preview"><strong>Preview</strong><span>{preview.changes.createdElementIds.length} created · {preview.changes.modifiedElementIds.length} modified · {preview.changes.deletedElementIds.length} deleted</span><small>The live model has not been modified.</small></div>}
            </div>}
        </section>
    );
}

function defaultArguments(parameters: ActionParameter[]): Record<string, unknown> {
    const values: Record<string, unknown> = {};
    parameters.forEach(parameter => { if(parameter.type === 'BOOLEAN') values[parameter.name] = false; });
    return values;
}
function parseValue(parameter: ActionParameter, value: string): unknown {
    if(parameter.type === 'INTEGER') return value === '' ? '' : Number.parseInt(value, 10);
    if(parameter.type === 'DECIMAL') return value === '' ? '' : Number.parseFloat(value);
    return value;
}
function requiredArgumentsPresent(action: QuickFixDescriptor | null, values: Record<string, unknown>): boolean {
    return action !== null && action.parameters.every(parameter => !parameter.required || (values[parameter.name] !== undefined && values[parameter.name] !== ''));
}
function statusLabel(status: string): string { return status.toLowerCase().replaceAll('_', ' '); }
function message(exception: unknown, fallback: string): string { return exception instanceof Error ? exception.message : fallback; }
