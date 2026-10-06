import type { EditorCapability } from './types';

export interface EditorCapabilityHelp {
    value: EditorCapability;
    label: string;
    description: string;
}

/** User-facing explanations for the runtime services an editor can expose. */
export const EDITOR_CAPABILITY_HELP: readonly EditorCapabilityHelp[] = [
    { value: 'MODEL_EDITING', label: 'Model editing', description: 'Create, change and remove model elements and their properties according to the metamodel.' },
    { value: 'MULTIPLE_REPRESENTATIONS', label: 'Multiple synchronized views', description: 'Open the same semantic model in several diagram, tree, table, form, matrix or dashboard views while keeping them synchronized.' },
    { value: 'OPERATIONS', label: 'Operations', description: 'Run operations defined by the metamodel or configured editor actions from the generated editor.' },
    { value: 'VALIDATION_RUNTIME', label: 'Validation while editing', description: 'Check the open model with HYDRA validation rules and show precise diagnostics in the editor.' },
    { value: 'QUICK_FIX_RUNTIME', label: 'Quick fixes', description: 'Offer the approved automatic fixes associated with validation problems when they are safe and applicable.' },
    { value: 'EVENTS', label: 'Editor events', description: 'Publish neutral events after committed model actions so other HYDRA layers or integrations can react.' },
    { value: 'EVENT_FLOWS', label: 'Event flows', description: 'Process editor events through configured EventFlow definitions to coordinate reactions and workflows.' },
    { value: 'GAMIFICATION', label: 'Gamification evidence', description: 'Send verified editor outcomes to gamification rules without coupling the semantic model to points or rewards.' },
    { value: 'IMPORT', label: 'Import models', description: 'Bring supported external or portable model data into the generated editor.' },
    { value: 'EXPORT', label: 'Export models', description: 'Save or export model data and the portable artifacts supported by the generated editor.' },
    { value: 'UNDO_REDO', label: 'Undo and redo', description: 'Undo or reapply committed editing actions in the current editor session.' },
];

export function capabilityHelp(capability: EditorCapability): EditorCapabilityHelp {
    return EDITOR_CAPABILITY_HELP.find(item => item.value === capability) ?? { value: capability, label: capability, description: capability };
}
