import type { MetaModel } from '../metamodel-designer/types';
import type { ActionModel } from '../validation/types';
import { createEditorDefinitionId } from './editorDefinitionFactory';
import type { EditorTool } from './types';
import type { PaletteActionKind } from './EditorPaletteToolForm';

export type PaletteActionDraftResult =
    | { ok: true; tool: EditorTool }
    | { ok: false; message: string };

/**
 * Creates a palette action only from authoritative metamodel/validation definitions.
 * The UI selects the outcome; this factory owns the safe defaults and prerequisite checks.
 */
export function createPaletteActionToolDraft(kind: PaletteActionKind, metamodel: MetaModel, actions: ActionModel, representationId: string): PaletteActionDraftResult {
    if (representationId === '') return { ok: false, message: 'Add a representation before adding palette actions.' };
    const common = { id: createEditorDefinitionId('tool'), description: '', icon: '', representationIds: [representationId], visibleExpressionId: '', enabledExpressionId: '' };

    if (kind === 'INVOKE_OPERATION') {
        const first = metamodel.classes.flatMap(owner => owner.operations.map(operation => ({ owner, operation })))[0];
        if (first === undefined) return { ok: false, message: 'Define a metamodel operation before adding an operation action.' };
        return { ok: true, tool: { ...common, kind, label: first.operation.name || 'Run operation', description: `Run ${first.operation.name || 'the selected operation'}.`, operationId: first.operation.id } };
    }
    if (kind === 'RUN_ACTION') {
        const first = actions.actions.find(action => action.enabled);
        if (first === undefined) return { ok: false, message: 'Define and enable an action before adding it to the palette.' };
        return { ok: true, tool: { ...common, kind, label: first.name || 'Run action', description: first.description ?? 'Run the selected configured action.', actionId: first.id } };
    }
    return { ok: true, tool: { ...common, kind, label: 'Validate model', description: 'Run validation for the selected model elements.', validationMode: 'SELECTION' } };
}
