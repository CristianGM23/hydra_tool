import { HydraButton } from '../design-system/HydraButton';
import { HydraAdaptiveSelect } from '../components/HydraAdaptiveSelect';
import { DesignerHelpedCheckbox, DesignerHelpedField } from '../designer-ui/DesignerFieldHelp';
import { HydraTooltip } from '../design-system';
import { useHydraLanguage } from '../i18n';
import { createDefaultEventField } from './eventDesignerFactory';
import type { EventFieldDefinition, EventTypeDefinition } from './types';

const EVENT_FIELD_TYPE_OPTIONS = [
    { value: 'STRING', label: 'Text', description: 'A sequence of characters.' },
    { value: 'INTEGER', label: 'Whole number', description: 'A number without decimal places.' },
    { value: 'DECIMAL', label: 'Decimal number', description: 'A numeric value that may include decimal places.' },
    { value: 'BOOLEAN', label: 'True or false', description: 'A logical value.' },
    { value: 'DATE', label: 'Date', description: 'A calendar date without a time of day.' },
    { value: 'DATETIME', label: 'Date and time', description: 'A timestamp with date and time.' },
    { value: 'DURATION', label: 'Duration', description: 'An amount of elapsed time.' },
    { value: 'DOCUMENT_ID', label: 'Document reference', description: 'A stable reference to a HYDRA document.' },
    { value: 'ELEMENT_ID', label: 'Element reference', description: 'A stable reference to an element inside a model or metamodel.' },
    { value: 'ENUM', label: 'Enumeration value', description: 'One value from a named enumeration contract.' },
    { value: 'OBJECT', label: 'Structured object', description: 'A structured value with named properties.' },
    { value: 'ARRAY', label: 'List', description: 'An ordered collection of values.' },
    { value: 'ANY', label: 'Open value', description: 'A value whose concrete type is intentionally unrestricted.' }
] as const;

interface Props {
    type: EventTypeDefinition;
    onChange: (type: EventTypeDefinition) => void;
    onUpdateField: (type: EventTypeDefinition, field: EventFieldDefinition) => void;
    onRenameFieldId: (type: EventTypeDefinition, previousId: string, nextId: string) => void;
}

export function EventDataFieldsSection({ type, onChange, onUpdateField, onRenameFieldId }: Props) {
    const { t } = useHydraLanguage();
    return <section className="hydra-designer-section event-payload-section">
        <header>
            <div className="event-section-title-with-help">
                <div><h4>{t('Event data')}</h4><p>{type.fields.length} {t('field(s) describe the information carried by this event.')}</p></div>
                <HydraTooltip label={t('Information about Event data')} title={t('About Event data')}>
                    <p>{t('Event data is the extra information that travels with the event so another validation, flow, integration or report can understand what happened.')}</p>
                    <p>{t('Example: a “Payment approved” event can carry orderId, amount and approvedAt so HYDRA knows which order was approved, for how much and when.')}</p>
                    <p>{t('Add only information another consumer really needs. An event with no custom data is still valid because HYDRA already carries its standard event header.')}</p>
                </HydraTooltip>
            </div>
            <HydraButton type="button" onClick={() => onChange({ ...type, fields: [...type.fields, createDefaultEventField()] })}>{t('+ Data field')}</HydraButton>
        </header>
        <div className="event-data-explainer">
            <strong>{t('What is this information for?')}</strong>
            <p>{t('Use these fields to describe the fact in enough detail to react correctly. For example, orderId can identify what changed, amount can feed a business rule and approvedAt can be recorded in an audit trail.')}</p>
        </div>
        {type.fields.map((field, index) => <div className="hydra-designer-row-card event-payload-field-card" key={field.id}>
            <header><strong>{t('Data field')} {index + 1}</strong><HydraButton className="hydra-danger-button" type="button" onClick={() => onChange({ ...type, fields: type.fields.filter(item => item.id !== field.id) })}>{t('Remove')}</HydraButton></header>
            <div className="hydra-designer-row-fields event-payload-grid">
                <DesignerHelpedField label="Field ID" targetId={`event-field-id-${index}`} required help="Stable technical identifier used by event bindings and schemas. Keep it stable after consumers begin referencing the field." example="field:diagnostic-id">
                    <input id={`event-field-id-${index}`} value={field.id} onChange={event => onRenameFieldId(type, field.id, event.target.value)} />
                </DesignerHelpedField>
                <DesignerHelpedField label="Field name" targetId={`event-field-name-${index}`} required help="Name of this event data value as seen by expressions, bindings, EventFlow nodes and external consumers." example="diagnosticId">
                    <input id={`event-field-name-${index}`} aria-label="Field name" value={field.name} onChange={event => onUpdateField(type, { ...field, name: event.target.value })} />
                </DesignerHelpedField>
                <DesignerHelpedField label="Field type" targetId={`event-field-type-${index}`} required help="Portable data type used to validate, serialize and map this event data value. Prefer the narrowest type that accurately represents the information." example="Document reference" options={[...EVENT_FIELD_TYPE_OPTIONS]}>
                    <HydraAdaptiveSelect showOptionHelp={false} id={`event-field-type-${index}`} ariaLabel="Field type" value={field.type} options={EVENT_FIELD_TYPE_OPTIONS.map(option => ({ ...option, category: eventFieldTypeCategory(option.value) }))} onChange={next => onUpdateField(type, { ...field, type: next as EventFieldDefinition['type'] })} />
                </DesignerHelpedField>
                {field.type === 'ENUM' && <DesignerHelpedField label="Enumeration" targetId={`event-field-enum-${index}`} help="Enumeration identifier used to validate the allowed values of this event data field." example="severity"><input id={`event-field-enum-${index}`} value={field.enumTypeId} onChange={event => onUpdateField(type, { ...field, enumTypeId: event.target.value })} /></DesignerHelpedField>}
                {(field.type === 'OBJECT' || field.type === 'ARRAY') && <DesignerHelpedField label="Object contract" targetId={`event-field-object-${index}`} help="Structured object identifier used when this event data follows a known reusable object contract." example="diagnostic-summary"><input id={`event-field-object-${index}`} value={field.objectTypeId} onChange={event => onUpdateField(type, { ...field, objectTypeId: event.target.value })} /></DesignerHelpedField>}
                <DesignerHelpedField className="hydra-designer-wide event-payload-description" label="Field description" targetId={`event-field-description-${index}`} help="Describe what this event data means, where it comes from and its units when relevant." example="Stable identifier of the diagnostic that was resolved."><textarea id={`event-field-description-${index}`} value={field.description} onChange={event => onUpdateField(type, { ...field, description: event.target.value })} /></DesignerHelpedField>
                <DesignerHelpedCheckbox className="event-payload-flag" label="Required data field" targetId={`event-field-required-${index}`} help="Requires every publisher and binding that emits this event to provide a value. Mark it only when the event would be incomplete without this information." example="true" checked={field.required} onChange={required => onUpdateField(type, { ...field, required })} />
                <DesignerHelpedCheckbox className="event-payload-flag" label="Multiple values" targetId={`event-field-many-${index}`} help="Stores an ordered collection instead of one value. Use it only when the event genuinely carries several values of this kind." example="false" checked={field.many} onChange={many => onUpdateField(type, { ...field, many })} />
            </div>
        </div>)}
        {type.fields.length === 0 && <div className="hydra-empty-state event-inline-empty"><strong>{t('No event data fields')}</strong><span>{t('This event currently carries only the standard HYDRA event header. Add a data field only when another consumer needs extra information about what happened.')}</span></div>}
    </section>;
}

function eventFieldTypeCategory(value: string): string {
    if (['STRING', 'INTEGER', 'DECIMAL', 'BOOLEAN'].includes(value)) return 'Basic values';
    if (['DATE', 'DATETIME', 'DURATION'].includes(value)) return 'Temporal values';
    if (['DOCUMENT_ID', 'ELEMENT_ID'].includes(value)) return 'HYDRA references';
    if (['ENUM', 'OBJECT', 'ARRAY'].includes(value)) return 'Structured values';
    return 'Open values';
}
