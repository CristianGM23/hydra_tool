import { useState } from 'react';
import { DesignerGuideButton, DesignerHelpedCheckbox, DesignerHelpedField, DesignerRequiredFieldsLegend } from '../designer-ui/DesignerFieldHelp';
import { HydraAdaptiveSelect } from '../components/HydraAdaptiveSelect';
import { HydraButton, HydraCreationModeSwitch, HydraGuidedWorkflow, HydraMultiSelectChips } from '../design-system';
import { useHydraLanguage } from '../i18n';
import { eventTypeOptionHelp, presentEventType } from './eventTypePresentation';
import { EventDataFieldsSection } from './EventDataFieldsSection';
import { EventCategoryField } from './EventCategoryField';
import type { EventBinding, EventFieldDefinition, EventPayloadMapping, EventSubjectKind, EventTypeDefinition } from './types';

export type EventFormSelection = { kind: 'EVENT'; id: string } | { kind: 'BINDING'; id: string } | null;

const EVENT_PERSISTENCE_OPTIONS = [
    { value: 'TRANSIENT', label: 'Transient', description: 'Available only while the event is being delivered; it is not retained.' },
    { value: 'SESSION', label: 'Session', description: 'Retained for the current editing or runtime session.' },
    { value: 'AUDIT', label: 'Audit', description: 'Stored as durable evidence for traceability and review.' },
    { value: 'GAMIFICATION', label: 'Gamification evidence', description: 'Intended to be consumed as verified evidence by gamification rules.' },
    { value: 'EXTERNAL', label: 'External delivery', description: 'Prepared for delivery to an external adapter or integration.' }
] as const;

const EVENT_BINDING_ERROR_OPTIONS = [
    { value: 'SKIP', label: 'Skip failed event', description: 'Ignore this transformation when mapping or condition evaluation fails.' },
    { value: 'REPORT', label: 'Report the error', description: 'Keep the binding active and publish a diagnostic about the failed transformation.' },
    { value: 'DISABLE_BINDING', label: 'Disable the binding', description: 'Turn the binding off after a transformation failure.' }
] as const;

const SUBJECT_KINDS: EventSubjectKind[] = [
    'METAMODEL', 'META_CLASS', 'META_ATTRIBUTE', 'META_REFERENCE', 'META_OPERATION', 'MODEL', 'MODEL_ELEMENT',
    'MODEL_FEATURE', 'VALIDATION_RULE', 'VALIDATION_DIAGNOSTIC', 'QUICK_FIX', 'ACTION', 'EVENT_DEFINITION',
    'EVENT_FLOW', 'EDITOR', 'GAMIFICATION_RULE', 'GAMIFICATION_REWARD'
];

const EVENT_SOURCE_OPTIONS = [
    { value: 'editor-runtime', label: 'Model editor', description: 'Events emitted by interactions inside a generated or previewed model editor.', category: 'Editing' },
    { value: 'model-runtime', label: 'Model runtime', description: 'Events emitted when model elements, attributes or relations change.', category: 'Editing' },
    { value: 'validation-runtime', label: 'Validation runtime', description: 'Events emitted while checking validation rules or producing diagnostics.', category: 'Quality' },
    { value: 'quick-fix-runtime', label: 'Quick-fix runtime', description: 'Events emitted after declarative repairs are evaluated or executed.', category: 'Quality' },
    { value: 'event-runtime', label: 'Event runtime', description: 'Events produced directly by HYDRA event bindings and HYDRA event processing.', category: 'Behaviour' },
    { value: 'event-flow-runtime', label: 'Event-flow runtime', description: 'Events emitted by executable EventFlow nodes and derived-event steps.', category: 'Behaviour' },
    { value: 'gamification-runtime', label: 'Gamification runtime', description: 'Verified award, penalty, level, achievement or reward evidence.', category: 'Behaviour' },
    { value: 'import-runtime', label: 'Import runtime', description: 'Events produced while analyzing, reconciling or materializing imported artifacts.', category: 'Delivery' },
    { value: 'export-runtime', label: 'Export runtime', description: 'Events produced while generating code, diagrams, packages or deployments.', category: 'Delivery' },
    { value: 'integration-runtime', label: 'Integration adapter', description: 'Events produced by configured integrations such as messaging or external adapters.', category: 'Integration' },
    { value: 'standalone-runtime', label: 'Standalone editor runtime', description: 'Events emitted by an exported standalone editor, including container deployments.', category: 'Integration' },
    { value: 'external-adapter', label: 'External adapter', description: 'Events produced outside HYDRA by a declared adapter contract.', category: 'Integration' }
] as const;

const EVENT_SUBJECT_OPTIONS = SUBJECT_KINDS.map(subject => ({
    value: subject,
    label: humanizeTechnicalValue(subject),
    description: subjectKindDescription(subject),
    category: subjectKindCategory(subject)
}));

export function EventConnectionSummary({ eventTypeId, bindings, onSelectBinding }: { eventTypeId: string; bindings: EventBinding[]; onSelectBinding: (bindingId: string) => void }) {
    const { t } = useHydraLanguage();
    const related = bindings.filter(binding => binding.sourceEventTypeId === eventTypeId || binding.targetEventTypeId === eventTypeId);
    return <section className="event-connection-summary" aria-label={t(`Visual connections for ${eventTypeId}`)}>
        <header><div><h4>{t('Visual connections')}</h4><p>{t('Bindings update these source and target properties automatically.')}</p></div><span>{related.length}</span></header>
        <div>
            {related.map(binding => {
                const outgoing = binding.sourceEventTypeId === eventTypeId;
                return <HydraButton type="button" key={binding.id} onClick={() => onSelectBinding(binding.id)}>
                    <strong>{t(outgoing ? 'Outgoing' : 'Incoming')}</strong>
                    <span>{outgoing ? binding.targetEventTypeId : binding.sourceEventTypeId}</span>
                    <small>{binding.name || binding.id} · {t('select to edit the binding')}</small>
                </HydraButton>;
            })}
            {related.length === 0 && <p>{t('No visual binding is connected to this event.')}</p>}
        </div>
    </section>;
}

interface EventTypeFormProps {
    type: EventTypeDefinition;
    onChange: (type: EventTypeDefinition) => void;
    onRenameId: (previousId: string, nextId: string) => void;
    onUpdateField: (type: EventTypeDefinition, field: EventFieldDefinition) => void;
    onRenameFieldId: (type: EventTypeDefinition, previousId: string, nextId: string) => void;
    onDelete: () => void;
    onCreateAnother: () => void;
}

export function EventTypeForm({ type, onChange, onRenameId, onUpdateField, onRenameFieldId, onDelete, onCreateAnother }: EventTypeFormProps) {
    const { t } = useHydraLanguage();
    const [creationMode, setCreationMode] = useState<'GUIDED' | 'ADVANCED'>('ADVANCED');
    const [guidedStep, setGuidedStep] = useState('contract');
    const fieldIds = type.fields.map(field => field.id.trim()).filter(Boolean);
    const fieldNames = type.fields.map(field => field.name.trim()).filter(Boolean);
    const fieldsComplete = type.fields.every(field => field.id.trim() !== '' && field.name.trim() !== '');
    const fieldIdsUnique = new Set(fieldIds).size === fieldIds.length;
    const fieldNamesUnique = new Set(fieldNames).size === fieldNames.length;
    const contractComplete = type.name.trim() !== '' && type.id.trim() !== '' && type.category.trim() !== '' && type.version.trim() !== '' && type.namespace.trim() !== '';

    const contractFields = <div className="hydra-designer-fields">
        <DesignerHelpedField label="Name" targetId="event-type-name" required help="Human-readable event name shown in catalogs and diagrams. Prefer a short fact in past tense or a stable domain concept." example="ViolationResolved">
            <input id="event-type-name" value={type.name} onChange={event => onChange({ ...type, name: event.target.value })} />
        </DesignerHelpedField>
        <DesignerHelpedField label="Stable event ID" targetId="event-type-id" required help="Technical identifier referenced by bindings, flows, integrations and external consumers. Change it only before publication because other contracts may depend on it." example="event:violation-resolved">
            <input id="event-type-id" value={type.id} onChange={event => onRenameId(type.id, event.target.value)} />
        </DesignerHelpedField>
        <EventCategoryField value={type.category} onChange={category => onChange({ ...type, category })} />
        <DesignerHelpedField label="Version" targetId="event-type-version" required help="Contract version of this event type. Increase it when the event data contract changes incompatibly so consumers can distinguish compatible and breaking revisions." example="1.0">
            <input id="event-type-version" value={type.version} onChange={event => onChange({ ...type, version: event.target.value })} />
        </DesignerHelpedField>
        <DesignerHelpedField label="Persistence" targetId="event-type-persistence" required help="Defines how the event evidence is retained and what kind of consumers may use it. Transient is delivery-only; Session survives during one runtime session; Audit is durable evidence; Gamification is verified evidence for game rules; External delivery is intended for adapters." example="Audit" options={[...EVENT_PERSISTENCE_OPTIONS]}>
            <HydraAdaptiveSelect showOptionHelp={false} id="event-type-persistence" ariaLabel="Persistence" value={type.persistencePolicy} options={EVENT_PERSISTENCE_OPTIONS} onChange={persistencePolicy => onChange({ ...type, persistencePolicy: persistencePolicy as EventTypeDefinition['persistencePolicy'] })} />
        </DesignerHelpedField>
        <DesignerHelpedField label="Namespace" targetId="event-type-namespace" required help="Shared namespace that identifies the owning event contract family. It is inherited from the event document to keep event IDs coherent." example="quality.people">
            <input id="event-type-namespace" value={type.namespace} readOnly />
        </DesignerHelpedField>
        <DesignerHelpedField className="hydra-designer-wide" label="Description" targetId="event-type-description" help="Explain at a high level what fact happened, when it is emitted and what a consumer can safely infer from receiving it." example="Published after a previously reported validation diagnostic disappears.">
            <textarea id="event-type-description" value={type.description} onChange={event => onChange({ ...type, description: event.target.value })} />
        </DesignerHelpedField>
    </div>;

    const scopeFields = <div className="hydra-designer-fields one-column">
        <DesignerHelpedField className="hydra-designer-wide" label="Allowed sources" targetId="event-type-sources" help="Restricts which HYDRA subsystem or external adapter is allowed to publish this event contract. Use it when the same fact must only be produced by trusted runtimes. Leaving the selection empty means unrestricted. Standard choices cover editor/model changes, validation, quick fixes, events, EventFlow, gamification, import/export and integrations. Existing external or custom source identifiers can be preserved through the advanced entry." example="Validation runtime + Quick-fix runtime" options={EVENT_SOURCE_OPTIONS.map(option => ({ value: option.value, label: option.label, description: option.description }))}>
            <HydraMultiSelectChips id="event-type-sources" ariaLabel="Allowed sources" options={EVENT_SOURCE_OPTIONS} selected={type.allowedSources} allowCustom customLabel="Custom event source" emptySelectionLabel="Unrestricted: any compatible publisher may emit this event" onChange={allowedSources => onChange({ ...type, allowedSources })} />
        </DesignerHelpedField>
        <DesignerHelpedField className="hydra-designer-wide" label="Allowed subjects" targetId="event-type-subjects" help="Defines the kinds of semantic object this event may describe. Subjects make the contract easier to validate, filter and reuse. Select one or more metamodel, model, validation, quick-fix, event, flow, editor or gamification subjects. Leaving the selection empty means that the contract does not restrict the subject kind." example="Validation diagnostic + Model element" options={EVENT_SUBJECT_OPTIONS.map(option => ({ value: option.value, label: option.label, description: option.description }))}>
            <HydraMultiSelectChips id="event-type-subjects" ariaLabel="Allowed subjects" options={EVENT_SUBJECT_OPTIONS} selected={type.allowedSubjects} emptySelectionLabel="Unrestricted: the event may describe any compatible subject" onChange={allowedSubjects => onChange({ ...type, allowedSubjects: allowedSubjects as EventSubjectKind[] })} />
        </DesignerHelpedField>
    </div>;

    const eventDataSection = <EventDataFieldsSection type={type} onChange={onChange} onUpdateField={onUpdateField} onRenameFieldId={onRenameFieldId} />;

    const lifecycleFields = <div className="hydra-designer-fields">
        <DesignerHelpedCheckbox label="Enabled" targetId="event-type-enabled" help="Controls whether this event type may be used by bindings, flows and publishers. Disable it while a contract is incomplete or temporarily retired without deleting it." example="Enabled" checked={type.enabled} onChange={enabled => onChange({ ...type, enabled })} />
        <DesignerHelpedCheckbox label="Abstract type" targetId="event-type-abstract" help="Abstract event types organize shared contracts but are not published directly. Concrete event types can use them as conceptual parents." example="false" checked={type.abstractType} onChange={abstractType => onChange({ ...type, abstractType })} />
        <DesignerHelpedCheckbox label="Deprecated" targetId="event-type-deprecated" help="Marks the event contract as retained for compatibility but discouraged for new producers. Existing consumers can continue to resolve it while a replacement is introduced." example="false" checked={type.deprecated} onChange={deprecated => onChange({ ...type, deprecated })} />
    </div>;

    const steps = [
        { id: 'contract', title: 'Contract', description: 'Identify and version the fact.', checks: [
            { id: 'name', label: 'Event name is defined', complete: type.name.trim() !== '', detail: 'Use a human-readable fact name.' },
            { id: 'id', label: 'Stable event ID is defined', complete: type.id.trim() !== '', detail: 'Bindings and flows reference this technical ID.' },
            { id: 'classification', label: 'Category, version and namespace are available', complete: type.category.trim() !== '' && type.version.trim() !== '' && type.namespace.trim() !== '', detail: 'These values make the contract identifiable and evolvable.' }
        ], content: contractFields },
        { id: 'scope', title: 'Scope', description: 'Choose who may publish and what the event describes.', checks: [
            { id: 'sources', label: 'Publisher scope is valid', complete: true, detail: type.allowedSources.length === 0 ? 'Unrestricted source scope is explicitly valid.' : `${type.allowedSources.length} allowed source(s) selected.` },
            { id: 'subjects', label: 'Subject scope is valid', complete: true, detail: type.allowedSubjects.length === 0 ? 'Unrestricted subject scope is explicitly valid.' : `${type.allowedSubjects.length} subject kind(s) selected.` }
        ], content: scopeFields },
        { id: 'payload', title: 'Event data', description: 'Define the information carried with the fact.', checks: [
            { id: 'fields', label: 'Every event data field has ID, name and type', complete: fieldsComplete, detail: type.fields.length === 0 ? 'No additional event data: standard HYDRA event header only.' : `${type.fields.length} custom field(s) configured.` },
            { id: 'field-ids', label: 'Event data field IDs are unique', complete: fieldIdsUnique, detail: 'Bindings use these stable identifiers.' },
            { id: 'field-names', label: 'Event data names are unique', complete: fieldNamesUnique, detail: 'Consumers address fields by these names.' }
        ], content: eventDataSection },
        { id: 'lifecycle', title: 'Lifecycle', description: 'Review availability and compatibility flags.', checks: [
            { id: 'persistence', label: 'Persistence policy is defined', complete: true, detail: `Current policy: ${type.persistencePolicy}.` },
            { id: 'flags', label: 'Lifecycle flags are explicit', complete: true, detail: `${type.enabled ? 'Enabled' : 'Disabled'} · ${type.abstractType ? 'Abstract' : 'Concrete'} · ${type.deprecated ? 'Deprecated' : 'Current'}.` }
        ], content: lifecycleFields },
        { id: 'review', title: 'Review', description: 'Confirm the contract is ready to save and connect.', checks: [
            { id: 'contract-ready', label: 'Contract fields are complete', complete: contractComplete, detail: 'Name, ID, category, version and namespace must be present.' },
            { id: 'payload-ready', label: 'Event data definition is internally consistent', complete: fieldsComplete && fieldIdsUnique && fieldNamesUnique, detail: 'Field identifiers and names must be complete and unique.' }
        ], content: <div className="hydra-guided-review-summary"><strong>{type.name || 'Unnamed event'}</strong><span>{type.id}</span><p>{type.description || 'No description provided.'}</p><dl><div><dt>Persistence</dt><dd>{type.persistencePolicy}</dd></div><div><dt>Sources</dt><dd>{type.allowedSources.length === 0 ? 'Unrestricted' : type.allowedSources.join(', ')}</dd></div><div><dt>Subjects</dt><dd>{type.allowedSubjects.length === 0 ? 'Unrestricted' : type.allowedSubjects.map(humanizeTechnicalValue).join(', ')}</dd></div><div><dt>Event data</dt><dd>{type.fields.length} custom field(s)</dd></div></dl></div> }
    ];

    return <section className="event-type-editor">
        <div className="hydra-designer-form-header">
            <div><h3>{type.name || 'Unnamed event'}</h3><p>Custom event type · <span className="hydra-id-preview">{type.id}</span></p></div>
            <div className="hydra-designer-form-actions">
                <HydraCreationModeSwitch mode={creationMode} onChange={setCreationMode} advancedLabel="Advanced / manual" />
                <DesignerGuideButton kind="EVENT" />
                <HydraButton variant="quiet" onClick={onCreateAnother}>{t('Create another event')}</HydraButton>
                <HydraButton className="hydra-danger-button" type="button" onClick={onDelete}>Delete</HydraButton>
            </div>
        </div>
        <DesignerRequiredFieldsLegend />
        {creationMode === 'GUIDED'
            ? <HydraGuidedWorkflow key={type.id} title="Guided event definition" description="Complete one verified block at a time. HYDRA distinguishes pending, ready-to-confirm, incomplete and completed steps." steps={steps} currentStepId={guidedStep} onStepChange={setGuidedStep} finishLabel="Finish guide" onFinish={() => { setCreationMode('ADVANCED'); return true; }} />
            : <><section className="hydra-designer-section event-manual-section"><header><div><h4>Contract and classification</h4><p>Complete technical form. Changes are the same ones used by the guided assistant.</p></div></header>{contractFields}</section><section className="hydra-designer-section"><header><div><h4>Publishing scope</h4><p>Restrict publishers and semantic subjects when the contract requires it.</p></div></header>{scopeFields}</section>{eventDataSection}<section className="hydra-designer-section"><header><div><h4>Lifecycle</h4><p>Availability, abstraction and deprecation controls.</p></div></header>{lifecycleFields}</section></>}
    </section>;
}

interface EventBindingFormProps {
    binding: EventBinding;
    target: EventTypeDefinition | null;
    sourceTypes: string[];
    customTypes: EventTypeDefinition[];
    onChange: (binding: EventBinding) => void;
    onRenameId: (previousId: string, nextId: string) => void;
    onAddMapping: () => void;
    onReplaceMapping: (index: number, mapping: EventPayloadMapping) => void;
    onDeleteMapping: (index: number) => void;
    onDelete: () => void;
}

export function EventBindingForm({ binding, target, sourceTypes, customTypes, onChange, onRenameId, onAddMapping, onReplaceMapping, onDeleteMapping, onDelete }: EventBindingFormProps) {
    const { t } = useHydraLanguage();
    const [creationMode, setCreationMode] = useState<'GUIDED' | 'ADVANCED'>('ADVANCED');
    const [guidedStep, setGuidedStep] = useState('identity');
    const requiredTargetFields = (target?.fields ?? []).filter(field => field.required);
    const mappedTargetIds = binding.payloadMappings.map(mapping => mapping.targetFieldId).filter(Boolean);
    const mappedTargetIdsUnique = new Set(mappedTargetIds).size === mappedTargetIds.length;
    const mappingsHaveValues = binding.payloadMappings.every(mapping => mapping.targetFieldId !== '' && (mapping.sourcePath.trim() !== '' || mapping.expressionId.trim() !== '' || mapping.fixedValue !== undefined));
    const requiredFieldsMapped = requiredTargetFields.every(field => binding.payloadMappings.some(mapping => mapping.targetFieldId === field.id && (mapping.sourcePath.trim() !== '' || mapping.expressionId.trim() !== '' || mapping.fixedValue !== undefined)));

    const identityFields = <div className="hydra-designer-fields">
        <DesignerHelpedField label="Name" targetId="event-binding-name" required help="Human-readable description of the event transformation. State what source fact becomes what derived fact." example="Model saved to quality check"><input id="event-binding-name" value={binding.name} onChange={event => onChange({ ...binding, name: event.target.value })} /></DesignerHelpedField>
        <DesignerHelpedField label="Binding ID" targetId="event-binding-id" required help="Stable technical identifier of this transformation rule. Event diagrams and persisted contracts use this value." example="binding:model-saved-quality-check"><input id="event-binding-id" value={binding.id} onChange={event => onRenameId(binding.id, event.target.value)} /></DesignerHelpedField>
        <DesignerHelpedCheckbox label="Enabled" targetId="event-binding-enabled" help="Controls whether the event bus may execute this transformation. Keep a binding disabled while mappings or references are incomplete." example="Enabled" checked={binding.enabled} onChange={enabled => onChange({ ...binding, enabled })} />
    </div>;

    const routeFields = <div className="hydra-designer-fields">
        <DesignerHelpedField label="Source event" targetId="event-binding-source" required help="Event type that activates this transformation. It may be a HYDRA reserved event or a custom contract. The original source event is never mutated." example="Model saved" options={eventTypeOptionHelp(sourceTypes, customTypes)}><HydraAdaptiveSelect showOptionHelp={false} id="event-binding-source" ariaLabel="Source event" value={binding.sourceEventTypeId} options={sourceTypes.map(eventTypeId => { const presentation = presentEventType(eventTypeId, customTypes); return { value: eventTypeId, label: presentation.label, description: presentation.description, category: presentation.category }; })} onChange={sourceEventTypeId => onChange({ ...binding, sourceEventTypeId })} /></DesignerHelpedField>
        <DesignerHelpedField label="Target event" targetId="event-binding-target" required help="Custom event produced when the source event matches and its event data can be built. Changing the target clears old mappings because their field IDs may no longer be valid." example="Quality check requested" options={eventTypeOptionHelp(customTypes.map(type => type.id), customTypes)}><HydraAdaptiveSelect showOptionHelp={false} id="event-binding-target" ariaLabel="Target event" value={binding.targetEventTypeId} options={customTypes.map(type => ({ value: type.id, label: type.name, description: type.description || 'Custom event produced by this binding.', category: type.category || 'Custom events' }))} onChange={targetEventTypeId => onChange({ ...binding, targetEventTypeId, payloadMappings: [] })} /></DesignerHelpedField>
    </div>;

    const conditionFields = <div className="hydra-designer-fields">
        <DesignerHelpedField className="hydra-designer-wide" label="Condition expression ID" targetId="event-binding-condition" help="Optional boolean ExpressionModel condition evaluated before the transformation. Leave it empty when every source event should create the target event. Advanced expressions remain editable in the validation/expression designer." example="expression:complete-model-only"><input id="event-binding-condition" value={binding.conditionExpressionId} onChange={event => onChange({ ...binding, conditionExpressionId: event.target.value })} /></DesignerHelpedField>
    </div>;

    const mappingSection = <section className="hydra-designer-section">
        <header><div><h4>{t('How the target event data is filled')}</h4><p>{t('Choose where each piece of information for the target event comes from. Required target fields must receive a source path, expression or fixed value.')}</p></div><HydraButton type="button" onClick={onAddMapping}>{t('+ Data mapping')}</HydraButton></header>
        <div className="event-data-explainer compact"><strong>{t('Example')}</strong><p>{t('For example, copy context.modelId into modelId, use payload.severity for severity, or assign a fixed value such as “QUALITY”.')}</p></div>
        {binding.payloadMappings.map((mapping, index) => <div className="hydra-designer-row-card" key={`${mapping.targetFieldId}-${index}`}><header><strong>{t('Data mapping')} {index + 1}</strong><HydraButton className="hydra-danger-button" type="button" onClick={() => onDeleteMapping(index)}>{t('Remove')}</HydraButton></header><div className="hydra-designer-row-fields">
            <DesignerHelpedField label="Target field" targetId={`event-mapping-target-${index}`} required help="Target event data field that receives this value. A target field should normally be mapped only once." example="Diagnostic identifier" options={(target?.fields ?? []).map(field => ({ value: field.id, label: field.name, description: field.description || 'Event data field defined by the target event.' }))}><HydraAdaptiveSelect showOptionHelp={false} id={`event-mapping-target-${index}`} ariaLabel="Target field" value={mapping.targetFieldId} options={(target?.fields ?? []).map(field => ({ value: field.id, label: field.name, description: field.description || 'Event data field defined by the target event.', category: field.required ? 'Required fields' : 'Optional fields' }))} onChange={targetFieldId => onReplaceMapping(index, { ...mapping, targetFieldId })} /></DesignerHelpedField>
            <DesignerHelpedField label="Source path" targetId={`event-mapping-path-${index}`} help="Direct path to information in the source event. Use this for simple copies. Technical paths may reference the event header, context or payload object." example="context.modelId"><input id={`event-mapping-path-${index}`} aria-label="Source path" value={mapping.sourcePath} onChange={event => onReplaceMapping(index, { ...mapping, sourcePath: event.target.value })} /></DesignerHelpedField>
            <DesignerHelpedField label="Expression ID" targetId={`event-mapping-expression-${index}`} help="Optional expression that computes or transforms the target value when a direct path is insufficient. Use either a source path, an expression or a fixed value." example="expression:resolved-code"><input id={`event-mapping-expression-${index}`} value={mapping.expressionId} onChange={event => onReplaceMapping(index, { ...mapping, expressionId: event.target.value })} /></DesignerHelpedField>
            <DesignerHelpedField label="Fixed value (JSON)" targetId={`event-mapping-fixed-${index}`} help="Optional constant JSON value. Use it only when neither the source event nor an expression provides the value." example='"QUALITY"'><input id={`event-mapping-fixed-${index}`} value={serializeFixedValue(mapping.fixedValue)} onChange={event => onReplaceMapping(index, { ...mapping, fixedValue: parseFixedValue(event.target.value) })} /></DesignerHelpedField>
        </div></div>)}
        {binding.payloadMappings.length === 0 && <div className="hydra-empty-state event-inline-empty"><strong>{t('No data mappings')}</strong><span>{requiredTargetFields.length === 0 ? t('The target has no required custom fields, so an empty mapping set is valid.') : t('Add mappings for every required target field before enabling this binding.')}</span></div>}
    </section>;

    const policyFields = <div className="hydra-designer-fields">
        <DesignerHelpedField label="Error policy" targetId="event-binding-error-policy" required help="Response when the condition or an event-data mapping cannot be evaluated. Skip ignores this occurrence; Report keeps the binding active and publishes a diagnostic; Disable binding turns this rule off after a transformation failure." example="Report the error" options={[...EVENT_BINDING_ERROR_OPTIONS]}><HydraAdaptiveSelect showOptionHelp={false} id="event-binding-error-policy" ariaLabel="Error policy" value={binding.errorPolicy} options={EVENT_BINDING_ERROR_OPTIONS} onChange={errorPolicy => onChange({ ...binding, errorPolicy: errorPolicy as EventBinding['errorPolicy'] })} /></DesignerHelpedField>
    </div>;

    const routeComplete = binding.sourceEventTypeId.trim() !== '' && binding.targetEventTypeId.trim() !== '';
    const mappingComplete = requiredFieldsMapped && mappingsHaveValues && mappedTargetIdsUnique;
    const steps = [
        { id: 'identity', title: 'Identity', description: 'Name and identify the transformation.', checks: [
            { id: 'name', label: 'Binding name is defined', complete: binding.name.trim() !== '', detail: 'Describe the transformation in business terms.' },
            { id: 'id', label: 'Stable binding ID is defined', complete: binding.id.trim() !== '', detail: 'The event architecture references this ID.' }
        ], content: identityFields },
        { id: 'route', title: 'Source and target', description: 'Choose the fact to consume and the derived fact to emit.', checks: [
            { id: 'source', label: 'Source event is selected', complete: binding.sourceEventTypeId.trim() !== '', detail: 'This event activates the binding.' },
            { id: 'target', label: 'Target custom event is selected', complete: binding.targetEventTypeId.trim() !== '', detail: 'This is the derived event produced by the binding.' }
        ], content: routeFields },
        { id: 'condition', title: 'Condition', description: 'Optionally restrict when the transformation runs.', checks: [
            { id: 'condition-valid', label: 'Condition strategy is valid', complete: true, detail: binding.conditionExpressionId.trim() === '' ? 'No condition: every source event is transformed.' : `Expression ${binding.conditionExpressionId} will be evaluated.` }
        ], content: conditionFields },
        { id: 'mapping', title: 'Event data mapping', description: 'Provide every required target value.', checks: [
            { id: 'required-fields', label: 'All required target data fields are mapped', complete: requiredFieldsMapped, detail: requiredTargetFields.length === 0 ? 'The target has no required custom fields.' : `${requiredTargetFields.length} required target field(s) checked.` },
            { id: 'mapping-values', label: 'Every mapping has a value source', complete: mappingsHaveValues, detail: 'Use source path, expression or fixed JSON value.' },
            { id: 'mapping-unique', label: 'Target fields are not mapped twice', complete: mappedTargetIdsUnique, detail: 'Duplicate target mappings are ambiguous.' }
        ], content: mappingSection },
        { id: 'review', title: 'Error handling and review', description: 'Choose failure behaviour and verify the complete route.', checks: [
            { id: 'route-ready', label: 'Source and target are ready', complete: routeComplete, detail: `${binding.sourceEventTypeId || 'No source'} → ${binding.targetEventTypeId || 'No target'}` },
            { id: 'mapping-ready', label: 'Event data mapping is compatible', complete: mappingComplete, detail: `${binding.payloadMappings.length} mapping(s) configured.` },
            { id: 'policy', label: 'Error policy is defined', complete: true, detail: `Current policy: ${binding.errorPolicy}.` }
        ], content: <>{policyFields}<div className="hydra-guided-review-summary"><strong>{binding.name || 'Unnamed binding'}</strong><span>{binding.id}</span><p>{binding.sourceEventTypeId || 'No source'} → {binding.targetEventTypeId || 'No target'}</p><dl><div><dt>Condition</dt><dd>{binding.conditionExpressionId || 'Always'}</dd></div><div><dt>Mappings</dt><dd>{binding.payloadMappings.length}</dd></div><div><dt>Error policy</dt><dd>{binding.errorPolicy}</dd></div><div><dt>Status</dt><dd>{binding.enabled ? 'Enabled' : 'Disabled'}</dd></div></dl></div></> }
    ];

    return <section className="event-binding-editor">
        <div className="hydra-designer-form-header"><div><h3>{binding.name || 'Unnamed binding'}</h3><p><span>Selected binding</span> · <span className="hydra-id-preview">{binding.id}</span></p></div><div className="hydra-designer-form-actions"><HydraCreationModeSwitch mode={creationMode} onChange={setCreationMode} advancedLabel="Advanced / manual" /><DesignerGuideButton kind="EVENT_BINDING" /><HydraButton className="hydra-danger-button" type="button" onClick={onDelete}>Delete binding</HydraButton></div></div>
        <DesignerRequiredFieldsLegend />
        {creationMode === 'GUIDED'
            ? <HydraGuidedWorkflow key={binding.id} title="Guided event binding" description="Build the event transformation in a safe order. HYDRA prevents advancing while required references or mappings are incomplete." steps={steps} currentStepId={guidedStep} onStepChange={setGuidedStep} finishLabel="Finish guide" onFinish={() => { setCreationMode('ADVANCED'); return true; }} />
            : <><section className="hydra-designer-section event-manual-section"><header><div><h4>Identity and availability</h4><p>Complete binding identity and enabled state.</p></div></header>{identityFields}</section><section className="hydra-designer-section"><header><div><h4>Event route</h4><p>Source and derived target contracts.</p></div></header>{routeFields}</section><section className="hydra-designer-section"><header><div><h4>Condition</h4><p>Optional ExpressionModel gate.</p></div></header>{conditionFields}</section>{mappingSection}<section className="hydra-designer-section"><header><div><h4>Failure policy</h4><p>How runtime transformation errors are handled.</p></div></header>{policyFields}</section></>}
    </section>;
}

function subjectKindCategory(subject: EventSubjectKind): string {
    if (subject.startsWith('META')) return 'Metamodel';
    if (subject.startsWith('MODEL')) return 'Model';
    if (subject.startsWith('VALIDATION') || subject === 'QUICK_FIX' || subject === 'ACTION') return 'Validation and repair';
    if (subject.startsWith('GAMIFICATION')) return 'Gamification';
    return 'Events and editors';
}

function subjectKindDescription(subject: EventSubjectKind): string {
    const descriptions: Record<EventSubjectKind, string> = {
        METAMODEL: 'The complete metamodel document or a change affecting it.',
        META_CLASS: 'A metamodel class or interface definition.',
        META_ATTRIBUTE: 'A structural attribute defined by a metamodel class.',
        META_REFERENCE: 'A relation, reference or containment defined by the metamodel.',
        META_OPERATION: 'An operation declared on a metamodel type.',
        MODEL: 'The complete model document or model-level lifecycle fact.',
        MODEL_ELEMENT: 'One concrete element instance inside a model.',
        MODEL_FEATURE: 'An attribute or relation value of a concrete model element.',
        VALIDATION_RULE: 'A validation rule definition or its lifecycle.',
        VALIDATION_DIAGNOSTIC: 'A validation problem, warning or successful diagnostic result.',
        QUICK_FIX: 'A declarative repair offered or executed for a diagnostic.',
        ACTION: 'A reusable declarative action executed by HYDRA.',
        EVENT_DEFINITION: 'An event contract, binding or event-catalog change.',
        EVENT_FLOW: 'An EventFlow definition, execution or transition.',
        EDITOR: 'A generated model editor, representation or editor session.',
        GAMIFICATION_RULE: 'A gamification evidence rule or scoring policy.',
        GAMIFICATION_REWARD: 'A point, level, achievement, challenge or reward outcome.'
    };
    return descriptions[subject];
}

function humanizeTechnicalValue(value: string): string { return value.toLowerCase().split('_').map(part => `${part.charAt(0).toUpperCase()}${part.slice(1)}`).join(' '); }


function serializeFixedValue(value: unknown): string {
    if (value === undefined) return '';
    return JSON.stringify(value);
}

function parseFixedValue(value: string): unknown {
    if (value.trim() === '') return undefined;
    try { return JSON.parse(value) as unknown; }
    catch { return value; }
}

