import { HydraButton } from '../design-system/HydraButton';
import { useMemo, useState } from 'react';
import type { ModelDocument, ModelElement } from '../types/editor';
import type { OperationRuntimeCatalog, OperationRuntimeDescriptor } from '../validation/types';
import { HydraAdaptiveSelect } from '../components/HydraAdaptiveSelect';
import { ResizableInfoTooltip } from '../designer-ui/ResizableInfoTooltip';
import { invokeModelOperation, operationRuntimeCatalogUrl, undoModelOperation } from './operationRuntimeApi';
import './operation-runtime.css';

interface Props {
    catalog: OperationRuntimeCatalog | null;
    document: ModelDocument | null;
    selectedElementId: string;
    onDocumentChange: (document: ModelDocument) => void;
    onError: (message: string) => void;
    onMessage: (message: string) => void;
}

type Surface = 'BUTTON' | 'FORM' | 'PALETTE' | 'MENU';

export function OperationRuntimePanel({ catalog, document, selectedElementId, onDocumentChange, onError, onMessage }: Props) {
    const [selectedOperationId, setSelectedOperationId] = useState('');
    const [argumentsByName, setArgumentsByName] = useState<Record<string, string>>({});
    const [result, setResult] = useState<unknown>(undefined);
    const [undoAvailable, setUndoAvailable] = useState(false);
    const [loading, setLoading] = useState(false);
    const selectedElement = document?.elements.find(element => element.id === selectedElementId) ?? null;
    const applicable = useMemo(() => (catalog?.operations ?? []).filter(operation => operation.enabled && isApplicable(operation, selectedElement)), [catalog, selectedElement]);
    const defaultOperation = applicable.find(operation => operation.surfaces.includes('FORM')) ?? applicable[0] ?? null;
    const selectedOperation = applicable.find(operation => operation.operationId === selectedOperationId) ?? defaultOperation;

    function chooseOperation(operationId: string): void {
        setSelectedOperationId(operationId);
        setArgumentsByName({});
        setResult(undefined);
    }

    async function execute(operation: OperationRuntimeDescriptor): Promise<void> {
        if (document === null || selectedElement === null) return;
        const missing = operation.parameters.find(parameter => parameter.required && (argumentsByName[parameter.name] ?? '').trim() === '');
        if (missing !== undefined) {
            onError(`Required operation parameter is missing: ${missing.name}`);
            return;
        }
        setLoading(true);
        try {
            const response = await invokeModelOperation(document.id, operation.operationId, {
                targetElementId: selectedElement.id,
                arguments: Object.fromEntries(operation.parameters.map(parameter => [parameter.name, parseParameterValue(parameter.typeLabel, parameter.many, argumentsByName[parameter.name] ?? '')])),
                selectionElementIds: [selectedElement.id]
            });
            onDocumentChange(response.document);
            setResult(response.value);
            setUndoAvailable(response.undoAvailable);
            onMessage(response.messages[0] ?? `${operation.name} executed`);
        } catch (error) {
            onError(error instanceof Error ? error.message : `Could not execute ${operation.name}`);
        } finally {
            setLoading(false);
        }
    }

    async function undo(): Promise<void> {
        if (document === null) return;
        setLoading(true);
        try {
            const response = await undoModelOperation(document.id);
            onDocumentChange(response.document);
            setUndoAvailable(response.undoAvailable);
            setResult(undefined);
            onMessage(response.messages[0] ?? 'Last operation was undone');
        } catch (error) {
            onError(error instanceof Error ? error.message : 'Could not undo the operation');
        } finally {
            setLoading(false);
        }
    }

    if (catalog === null || catalog.operations.length === 0) return <aside className="operation-runtime-panel"><h2>Operations</h2><p>No operations are defined for this metamodel.</p></aside>;
    if (document === null) return <aside className="operation-runtime-panel"><h2>Operations</h2><p>Create or open a model to execute operations.</p></aside>;
    if (selectedElement === null) return <aside className="operation-runtime-panel"><h2>Operations</h2><p>Select a model element to see its operations.</p></aside>;

    return <aside className="operation-runtime-panel">
        <header><div><h2>Operations</h2><span>{selectedElement.className}</span></div><a href={operationRuntimeCatalogUrl(catalog.metamodelId)} download>Export catalog</a></header>
        {applicable.length === 0 ? <p>No executable operation applies to the selected element.</p> : <>
            <OperationSurfaceGroup title="Buttons" surface="BUTTON" operations={applicable} loading={loading} onChoose={operation => operation.parameters.length === 0 ? void execute(operation) : chooseOperation(operation.operationId)} />
            <OperationSurfaceGroup title="Palette" surface="PALETTE" operations={applicable} loading={loading} onChoose={operation => operation.parameters.length === 0 ? void execute(operation) : chooseOperation(operation.operationId)} />
            {applicable.some(operation => operation.surfaces.includes('MENU')) && <div className="operation-runtime-menu"><span>Menu <ResizableInfoTooltip label="Information about operation menu" title="Operation menu">Lists operations available for the selected model element. Values are grouped by behaviour and the description states their parameters and return value.</ResizableInfoTooltip></span><HydraAdaptiveSelect ariaLabel="Operation menu" value={selectedOperation?.operationId ?? ''} placeholder="Select operation" options={applicable.filter(operation => operation.surfaces.includes('MENU')).map(operation => ({ value:operation.operationId,label:operation.name,description:`${operation.parameters.length} parameter(s); returns ${operation.returnTypeLabel}.`,category:operation.behaviorKind === 'COMMAND' ? 'Commands' : 'Queries' }))} onChange={chooseOperation} /></div>}
            {selectedOperation !== null && selectedOperation.surfaces.some(surface => surface === 'FORM' || surface === 'MENU' || surface === 'BUTTON' || surface === 'PALETTE') && <section className="operation-runtime-form"><h3>{selectedOperation.name}</h3><span>{selectedOperation.behaviorKind.toLowerCase()} · returns {selectedOperation.returnTypeLabel}</span>{selectedOperation.parameters.map(parameter => <label key={parameter.id}>{parameter.name}{parameter.required && <span aria-label="Required field"> *</span>}<input aria-label={parameter.name} required={parameter.required} value={argumentsByName[parameter.name] ?? ''} onChange={event => setArgumentsByName(current => ({ ...current, [parameter.name]: event.target.value }))} placeholder={parameter.many ? `${parameter.typeLabel}, separated by commas` : parameter.typeLabel} /></label>)}<HydraButton disabled={loading} type="button" onClick={() => void execute(selectedOperation)}>{loading ? 'Executing…' : 'Execute'}</HydraButton></section>}
            {result !== undefined && <output className="operation-runtime-result"><strong>Result</strong><code>{formatResult(result)}</code></output>}
        </>}
        <HydraButton className="operation-runtime-undo" disabled={loading || !undoAvailable} type="button" onClick={() => void undo()}>Undo last operation</HydraButton>
    </aside>;
}

function OperationSurfaceGroup({ title, surface, operations, loading, onChoose }: { title: string; surface: Surface; operations: OperationRuntimeDescriptor[]; loading: boolean; onChoose: (operation: OperationRuntimeDescriptor) => void }) {
    const visible = operations.filter(operation => operation.surfaces.includes(surface));
    if (visible.length === 0) return null;
    return <section className={`operation-runtime-${surface.toLowerCase()}`}><h3>{title}</h3>{visible.map(operation => <HydraButton disabled={loading} key={operation.operationId} type="button" onClick={() => onChoose(operation)}><strong>{operation.name}</strong><span>{operation.parameters.length} parameter(s)</span></HydraButton>)}</section>;
}

function isApplicable(operation: OperationRuntimeDescriptor, element: ModelElement | null): boolean {
    return element !== null && (operation.applicableClassIds.includes(element.classId) || operation.applicableClassNames.includes(element.className));
}

function parseParameterValue(typeLabel: string, many: boolean, raw: string): unknown {
    if (many) return raw.trim() === '' ? [] : raw.split(',').map(value => parseSingleValue(typeLabel, value.trim()));
    if (raw.trim() === '') return null;
    return parseSingleValue(typeLabel, raw.trim());
}

function parseSingleValue(typeLabel: string, raw: string): unknown {
    const normalized = typeLabel.toUpperCase();
    if (normalized === 'BOOLEAN') return raw.toLowerCase() === 'true';
    if (['BYTE', 'SHORT', 'INTEGER', 'LONG', 'BIG_INTEGER'].includes(normalized)) return Number.parseInt(raw, 10);
    if (['FLOAT', 'DOUBLE', 'DECIMAL'].includes(normalized)) return Number.parseFloat(raw);
    return raw;
}

function formatResult(value: unknown): string {
    if (typeof value === 'string') return value;
    try { return JSON.stringify(value, null, 2); } catch { return String(value); }
}
