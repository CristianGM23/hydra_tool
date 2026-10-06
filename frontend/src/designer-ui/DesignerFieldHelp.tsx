import { useState, type ReactNode } from 'react';
import { HydraButton, HydraFormField, HydraModal, HydraRequiredFieldsLegend, HydraTooltip } from '../design-system';
import { useHydraLanguage } from '../i18n/useHydraLanguage';
import { fieldGuidance, shouldEnrichFieldHelp } from '../design-system/HydraFieldGuidance';
import './designer-ui.css';

export interface DesignerSelectOptionHelp {
    value: string;
    label: string;
    description: string;
}

export interface DesignerFieldLabelProps {
    label: string;
    targetId: string;
    help: string;
    example: string;
    required?: boolean;
    options?: readonly DesignerSelectOptionHelp[];
}

/** Label común para los diseñadores HYDRA con ayuda contextual estable. */
export function DesignerFieldLabel({ label, targetId, help, example, required = false, options = [] }: DesignerFieldLabelProps) {
    const { t, language } = useHydraLanguage();
    const visibleLabel = t(label);
    const guidance = shouldEnrichFieldHelp(help) ? fieldGuidance(label, help, language, options.length > 0) : null;
    return (
        <div className="hydra-field-label-row">
            <label htmlFor={targetId} title={visibleLabel}>{visibleLabel}</label>
            {required && <span className="hydra-required-marker" aria-label={t('Required field')}>*</span>}
            <HydraTooltip
                className="hydra-field-tooltip"
                label={`${t('Information about')} ${visibleLabel}`}
                title={`${t('About')} ${visibleLabel}`}
                trigger={<HydraButton className="hydra-context-help-button hydra-field-info-button" type="button">i</HydraButton>}
                onOpenChange={open => { if (open) document.getElementById(targetId)?.focus(); }}
            >
                <p>{t(help)}</p>
                {guidance !== null && <section className="hydra-ui-field-guidance"><div><strong>{t('How to choose')}</strong><p>{guidance.choose}</p></div><div><strong>{t('What changes')}</strong><p>{guidance.impact}</p></div>{guidance.options !== undefined && <div><strong>{t('Use the current context')}</strong><p>{guidance.options}</p></div>}</section>}
                <div><span>{t('Example')}</span><code>{example}</code></div>
                {options.length > 0 && (
                    <section className="hydra-field-option-help">
                        <strong>{t('Available values')}</strong>
                        <dl>{options.map(option => <div key={option.value}><dt>{t(option.label)}</dt><dd>{t(option.description)}</dd></div>)}</dl>
                    </section>
                )}
            </HydraTooltip>
        </div>
    );
}

interface DesignerHelpedFieldProps extends DesignerFieldLabelProps {
    children: ReactNode;
    className?: string;
}

/** Contenedor de campo reutilizable para todos los formularios de HYDRA. */
export function DesignerHelpedField({ children, className = '', label, targetId, help, example, required = false, options = [] }: DesignerHelpedFieldProps) {
    return <HydraFormField
        className={`hydra-helped-field${className === '' ? '' : ` ${className}`}`}
        example={example}
        help={help}
        label={label}
        options={options}
        required={required}
        targetId={targetId}
    >{children}</HydraFormField>;
}

export function DesignerRequiredFieldsLegend() {
    return <div className="hydra-required-legend"><HydraRequiredFieldsLegend /></div>;
}

interface DesignerHelpedCheckboxProps extends DesignerFieldLabelProps {
    checked: boolean;
    onChange: (checked: boolean) => void;
    disabled?: boolean;
    className?: string;
}

/** Checkbox semántico con la misma ayuda contextual que el resto de campos HYDRA. */
export function DesignerHelpedCheckbox({ checked, onChange, disabled = false, className = '', ...labelProps }: DesignerHelpedCheckboxProps) {
    return (
        <div className={`hydra-helped-checkbox${className === '' ? '' : ` ${className}`}`}>
            <input
                aria-label={labelProps.label}
                checked={checked}
                disabled={disabled}
                id={labelProps.targetId}
                type="checkbox"
                onChange={event => onChange(event.target.checked)}
            />
            <DesignerFieldLabel {...labelProps} />
        </div>
    );
}

export type DesignerGuideKind =
    | 'EVENT'
    | 'EVENT_BINDING'
    | 'EVENT_FLOW'
    | 'GAMIFICATION'
    | 'EDITOR'
    | 'REPRESENTATION'
    | 'INTEGRATION';

interface GuideDefinition {
    buttonLabel: string;
    title: string;
    introduction: string;
    steps: Array<{ title: string; description: string; example: string }>;
    finalNote: string;
}

const guides: Record<DesignerGuideKind, GuideDefinition> = {
    EVENT: {
        buttonLabel: 'Event guide',
        title: 'How to define an event type',
        introduction: 'An event type describes an immutable fact that HYDRA or an external adapter may publish. Its event data must be stable, portable and meaningful outside one screen.',
        steps: [
            { title: '1. Identify the event', description: 'Choose a clear name and review the stable event ID. The ID is the technical contract used by bindings, flows and consumers.', example: 'Name: ViolationResolved · ID: event:violation-resolved' },
            { title: '2. Classify and version it', description: 'Set the namespace, category and version so adapters can distinguish ownership and evolution.', example: 'Namespace: quality.people · Category: VALIDATION · Version: 1.0' },
            { title: '3. Choose persistence', description: 'Decide whether the event is transient, session-scoped, audited, consumed by gamification or exported.', example: 'Persistence: AUDIT' },
            { title: '4. Define the event data', description: 'Add only the information another consumer needs, using stable IDs, names, types, cardinality and descriptions. Mark only genuinely mandatory data as required.', example: 'Field: diagnosticId · Type: DOCUMENT_ID · Required: yes' },
            { title: '5. Restrict sources and subjects', description: 'Optionally document which components and subject kinds are allowed to publish the event.', example: 'Source: validation-runtime · Subject: VALIDATION_DIAGNOSTIC' },
            { title: '6. Save and connect', description: 'Save the event contract, then create bindings or flows that consume it without changing its original meaning.', example: 'Event → Binding → Derived event or Event flow' }
        ],
        finalNote: 'Events are facts, not commands. Publish them only after the operation that produced the fact has been committed.'
    },
    EVENT_BINDING: {
        buttonLabel: 'Binding guide',
        title: 'How to define an event binding',
        introduction: 'A binding derives one event from another by checking an optional condition and mapping information from the source event into the target event data.',
        steps: [
            { title: '1. Identify the binding', description: 'Give it a stable ID and a name that states the transformation.', example: 'model-save-to-quality-check' },
            { title: '2. Select source and target', description: 'The source may be reserved or custom. The target must be a custom event type defined in this catalog.', example: 'core.model.saved → quality.model-ready' },
            { title: '3. Add a condition', description: 'Optionally reference an ExpressionModel expression. Leave it empty when every source event must be transformed.', example: 'conditionExpressionId: expression:only-complete-models' },
            { title: '4. Map the event data', description: 'Fill every required target field from a source path, expression or fixed value.', example: 'target modelId ← context.modelId' },
            { title: '5. Choose the error policy', description: 'Decide whether a failed transformation is skipped, reported or disables the binding.', example: 'Error policy: REPORT' },
            { title: '6. Enable, save and check', description: 'Enable the binding only when references and mappings are complete.', example: 'Enabled: true · Check: 0 errors' }
        ],
        finalNote: 'Bindings derive events; they must not mutate model state or award gamification directly.'
    },
    EVENT_FLOW: {
        buttonLabel: 'Event flow guide',
        title: 'How to define an event flow',
        introduction: 'An event flow coordinates event-driven processing through explicit nodes and transitions while keeping the compiled plan separate from the editable definition.',
        steps: [
            { title: '1. Identify the flow', description: 'Give the flow a stable ID, a clear name and a concise purpose.', example: 'flow:model-quality-cycle' },
            { title: '2. Select trigger events', description: 'Declare the event types that may start the flow.', example: 'core.model.saved, core.validation.completed' },
            { title: '3. Add start and final nodes', description: 'Every executable flow needs one start and explicit success or error endings.', example: 'START → ... → SUCCESS_END' },
            { title: '4. Add processing nodes', description: 'Use filters, mappings, decisions, waits, operations or emits according to the intended process.', example: 'FILTER → INVOKE_OPERATION → EMIT' },
            { title: '5. Connect and configure', description: 'Create transitions and configure references, conditions, emitted event types and compensation metadata.', example: 'Decision true → Emit quality.accepted' },
            { title: '6. Check, preview and save', description: 'Run the checker before starting the runtime and persist the semantic document together with its separate diagram.', example: 'Check: 0 errors · Runtime: stopped during editing' }
        ],
        finalNote: 'Flows may produce derived events. They must remain independent from optional gamification rules.'
    },
    GAMIFICATION: {
        buttonLabel: 'Gamification guide',
        title: 'How to define gamification',
        introduction: 'Gamification consumes verified event evidence and records compensable outcomes. Define the catalog first and then create rules that connect evidence to points, levels, achievements, challenges or rewards.',
        steps: [
            { title: '1. Define point types', description: 'Create one or more independent point scales with stable IDs and meaningful names.', example: 'points:quality · Quality points' },
            { title: '2. Define progression elements', description: 'Create levels, achievements and challenges and connect them to the relevant point type or reward.', example: 'Level: Model Guardian · Minimum: 100 quality points' },
            { title: '3. Define rewards', description: 'Describe the reward and its portable payload. Rewards must not embed UI-only state.', example: 'reward:quality-badge · payload: { badge: "guardian" }' },
            { title: '4. Create an evidence rule', description: 'Choose a verified event, optional condition and the outcomes to grant.', example: 'core.validation.violation-resolved → +3 quality points' },
            { title: '5. Configure repetition limits', description: 'Choose idempotence, cooldown and anti-farming limits per actor and day.', example: 'ONCE_PER_EVENT · max 20/day' },
            { title: '6. Check the ledger behaviour', description: 'Save, inspect runtime state and verify that undo or violation recurrence creates compensation instead of silently deleting history.', example: 'ACTIVE award → COMPENSATED ledger entry' }
        ],
        finalNote: 'Never award points merely because a quick fix was clicked. Use verified evidence such as core.validation.violation-resolved.'
    },
    EDITOR: {
        buttonLabel: 'Editor guide',
        title: 'How to define a model editor',
        introduction: 'A model editor configuration belongs to one metamodel and groups the palette, forms, behavior, mappings and visual representations used by one editing workflow.',
        steps: [
            { title: '1. Identify the editing workflow', description: 'Choose a stable configuration ID and a visible name. Create another configuration only for a genuinely different workflow; visual alternatives belong as representations in this configuration.', example: 'editor:people-administration' },
            { title: '2. Add representations', description: 'Create one or more diagram, tree, table, form, matrix or dashboard representations.', example: 'Main diagram · Person table' },
            { title: '3. Configure mappings', description: 'Map metamodel classes and relations to visual nodes and edges with stable mapping IDs.', example: 'node:person → class:Person' },
            { title: '4. Configure the palette', description: 'Expose only the tools that belong to this editor and its representations.', example: 'Create Person available only in Main diagram' },
            { title: '5. Define forms and capabilities', description: 'Create forms for semantic classes and declare optional runtime capabilities.', example: 'Person properties form · VALIDATION_RUNTIME' },
            { title: '6. Save and check', description: 'Check all references before using the editor to create model instances.', example: 'Representations: 2 · Tools: 4 · Forms: 1' }
        ],
        finalNote: 'One model editor uses the selected configuration. Switching representations changes its visual projection, mappings, palette and forms without duplicating the semantic model.'
    },
    REPRESENTATION: {
        buttonLabel: 'Representation guide',
        title: 'How to define a representation',
        introduction: 'A representation is one view of the same semantic model. It determines which elements appear and how nodes, edges and tools are presented.',
        steps: [
            { title: '1. Identify the representation', description: 'Set a stable ID, visible name and representation kind.', example: 'representation:main-diagram · DIAGRAM' },
            { title: '2. Select roots', description: 'Choose the metamodel classes that may act as roots in this view.', example: 'Root class: class:Package' },
            { title: '3. Add node mappings', description: 'Map concrete classes to visual shapes, dimensions, labels and styles.', example: 'class:Person → rounded rectangle' },
            { title: '4. Add edge mappings', description: 'Map semantic relations to connection types and label expressions.', example: 'relation:worksFor → ORTHOGONAL' },
            { title: '5. Expose tools', description: 'Associate creation, operation, action and validation tools with this representation.', example: 'tool:create-person' },
            { title: '6. Decide editability', description: 'Mark the representation read-only when it is intended only for analysis or projection.', example: 'Dashboard: editable = false' }
        ],
        finalNote: 'All representations remain synchronized because they edit or display the same HYDRA model.'
    },
    INTEGRATION: {
        buttonLabel: 'Integration guide',
        title: 'How to define a technology integration',
        introduction: 'A technology integration describes what HYDRA should expose to an external tool and how to deliver it without losing control of the host project.',
        steps: [
            { title: '1. Identify the integration', description: 'Use a stable technical ID and a clear name so the profile can be reused and recognized later.', example: 'people-langium-integration' },
            { title: '2. Choose the destination', description: 'Select the external technology, host project and delivery style. Add version or module details only when they matter.', example: 'LANGIUM · NPM · GENERATED_MODULE' },
            { title: '3. Choose the capabilities', description: 'Enable only what the destination must actually do: validation, events, EventFlow, gamification, UI contributions or import/export.', example: 'EVENT_PUBLICATION · VALIDATION_RUNTIME' },
            { title: '4. Review HYDRA inputs', description: 'Select the HYDRA documents that the adapter needs and mark only genuine prerequisites as required.', example: 'METAMODEL required · EVENT_FLOWS optional' },
            { title: '5. Add host mappings when needed', description: 'Map HYDRA elements to host classes, AST paths, aliases or external identifiers only when a capability needs a concrete target element.', example: 'class:Person → com.acme.Person' },
            { title: '6. Analyze and deliver', description: 'Check compatibility, save the profile and then generate a module or preview a reversible injection before applying changes.', example: 'Analyze → 0 blocking incompatibilities' }
        ],
        finalNote: 'Keep preview and rollback enabled. The host project should change only after the generated plan has been reviewed.'
    }
};

interface DesignerGuideButtonProps {
    kind: DesignerGuideKind;
    compact?: boolean;
}

/** Botón y diálogo de proceso común para cada diseñador especializado. */
export function DesignerGuideButton({ kind, compact = false }: DesignerGuideButtonProps) {
    const [open, setOpen] = useState(false);
    const { t } = useHydraLanguage();
    const guide = guides[kind];
    return (
        <>
            <HydraButton
                aria-label={compact ? t(guide.buttonLabel) : undefined}
                className={`hydra-process-guide-button${compact ? ' compact' : ''}`}
                compact={compact}
                icon={<span className="hydra-process-guide-icon">i</span>}
                title={compact ? t(guide.buttonLabel) : undefined}
                variant="quiet"
                onClick={() => setOpen(true)}
            >
                <span className="hydra-process-guide-label">{t(guide.buttonLabel)}</span>
            </HydraButton>
            <HydraModal
                className="hydra-guide-dialog"
                closeLabel={t('Close guide')}
                description={t(guide.introduction)}
                footer={<><p>{t(guide.finalNote)}</p><HydraButton variant="primary" onClick={() => setOpen(false)}>{t('Understood')}</HydraButton></>}
                open={open}
                size="large"
                title={t(guide.title)}
                onClose={() => setOpen(false)}
            >
                <ol className="hydra-guide-steps">
                    {guide.steps.map(step => <li key={step.title}><strong>{t(step.title)}</strong><p>{t(step.description)}</p><code>{t(step.example)}</code></li>)}
                </ol>
            </HydraModal>
        </>
    );
}
