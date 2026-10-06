import { HydraButton } from '../design-system/HydraButton';
import { useState } from 'react';
import { useHydraLanguage } from '../i18n';
import type { MetaModel } from '../metamodel-designer/types';
import type { ActionModel, ExpressionDefinition, ValidationModel, ValidationRule, ValidationTrigger } from '../validation/types';
import { ExpressionBuilder } from './ExpressionBuilder';
import { HydraAdaptiveSelect } from '../components/HydraAdaptiveSelect';
import { HydraCreationModeSwitch, HydraGuidedWorkflow } from '../design-system';
import { FieldLabel, HelpedField, RequiredFieldsLegend } from './ValidationFieldHelp';
import { attributesForContext, findClass, relationsForContext, ruleGroupId } from './validationDesignerSelectors';
import { findExpressionBlockingError, findQuickFixBlockingError } from './validationDesignerValidation';
import { isStableElementId } from '../validation/stableIdentifierPolicy';
import { AUTHORITATIVE_OPTIONS, CONTEXT_OPTIONS, CONVERSION_STATUS_OPTIONS, EXECUTION_MODE_OPTIONS, SEVERITY_OPTIONS } from './validationRuleOptions';

interface ValidationRuleEditorProps {
    metamodel: MetaModel;
    validationModel: ValidationModel;
    actionModel: ActionModel;
    rule: ValidationRule | null;
    expression: ExpressionDefinition | null;
    onChangeRule: (rule: ValidationRule) => void;
    onChangeExpression: (expression: ExpressionDefinition) => void;
    onDelete: (ruleId: string) => void;
    onMoveToGroup: (ruleId: string, ruleSetId: string) => void;
    onFinishGuided?: () => boolean | Promise<boolean>;
    allDefinitionsReady?: boolean;
}

const triggers: ValidationTrigger[] = ['ON_CREATE', 'ON_ATTRIBUTE_CHANGE', 'ON_RELATION_CHANGE', 'ON_DELETE', 'ON_OPEN', 'ON_SAVE', 'MANUAL', 'BEFORE_EXPORT', 'BACKGROUND', 'FULL_VALIDATION'];

const TRIGGER_LABELS: Record<ValidationTrigger, string> = {
    ON_CREATE: 'When an element is created',
    ON_ATTRIBUTE_CHANGE: 'When an attribute changes',
    ON_RELATION_CHANGE: 'When a relation changes',
    ON_DELETE: 'Before an element is deleted',
    ON_OPEN: 'When the model is opened',
    ON_SAVE: 'When the model is saved',
    MANUAL: 'When the user requests validation',
    BEFORE_EXPORT: 'Before exporting',
    BACKGROUND: 'In the background',
    FULL_VALIDATION: 'During full validation'
};

const structuralContextKinds: ValidationRule['context']['kind'][] = ['CLASS', 'OBJECT', 'ATTRIBUTE', 'RELATION'];

export function ValidationRuleEditor({ metamodel, validationModel, actionModel, rule, expression, onChangeRule, onChangeExpression, onDelete, onMoveToGroup, onFinishGuided, allDefinitionsReady = true }: ValidationRuleEditorProps) {
    const { t } = useHydraLanguage();
    const [creationMode, setCreationMode] = useState<'GUIDED' | 'ADVANCED'>('GUIDED');
    const [guidedStep, setGuidedStep] = useState('identity');

    if (rule === null || expression === null) {
        return <section className="validation-rule-editor validation-empty-state"><strong>Select a validation</strong><span>Choose a rule from the tree or create a new one.</span></section>;
    }

    const currentRule = rule;
    const currentExpression = expression;
    const prefix = `validation-${safeId(currentRule.id)}`;
    const contextClass = findClass(metamodel, currentRule.context.classId);
    const attributes = attributesForContext(metamodel, currentRule.context.classId);
    const relations = relationsForContext(metamodel, currentRule.context.classId);
    const groupId = ruleGroupId(currentRule.id, validationModel.ruleSets);
    const hasStructuralContext = structuralContextKinds.includes(currentRule.context.kind);
    const contextClassRequired = ['CLASS', 'OBJECT', 'ATTRIBUTE'].includes(currentRule.context.kind);
    const targetFeatureRequired = currentRule.context.kind === 'ATTRIBUTE' || currentRule.context.kind === 'RELATION';
    const triggersRequired = currentRule.executionMode !== 'EXTERNAL';
    const associatedRepresentationId = currentRule.annotations?.['hydra.visual.representationId'] ?? '';
    const importedVisualRule = associatedRepresentationId !== '' || currentRule.annotations?.['hydra.import.association'] === 'visual-representation';

    function changeContextKind(kind: ValidationRule['context']['kind']): void {
        const firstClass = metamodel.classes[0];
        const classId = currentRule.context.classId || firstClass?.id || '';
        const structural = structuralContextKinds.includes(kind);
        onChangeRule({
            ...currentRule,
            context: structural ? { kind, classId, featureId: undefined } : { kind }
        });
        onChangeExpression({ ...currentExpression, contextClassId: structural ? classId : undefined });
    }

    function toggleTrigger(trigger: ValidationTrigger): void {
        const next = currentRule.triggers.includes(trigger) ? currentRule.triggers.filter(item => item !== trigger) : [...currentRule.triggers, trigger];
        onChangeRule({ ...currentRule, triggers: next });
    }

    function toggleQuickFix(actionId: string): void {
        const next = currentRule.quickFixIds.includes(actionId) ? currentRule.quickFixIds.filter(item => item !== actionId) : [...currentRule.quickFixIds, actionId];
        onChangeRule({ ...currentRule, quickFixIds: next });
    }

    const identityFields = <div className="validation-rule-fields two-columns">
        <HelpedField label="Name" targetId={`${prefix}-name`} required help="Human-readable title used in the rule tree and diagnostics. Describe the constraint in domain terms so users understand what is being checked." example="Positive customer age"><input id={`${prefix}-name`} required value={currentRule.name} onChange={event => onChangeRule({ ...currentRule, name: event.target.value })} /></HelpedField>
        <HelpedField label="Stable problem code" targetId={`${prefix}-code`} required help="Stable technical identifier for the diagnostic. Quick fixes, exports and imported rule projections may reference it, so it should remain stable even when the visible name changes." example="CUSTOMER_AGE_POSITIVE"><input id={`${prefix}-code`} required value={currentRule.code} onChange={event => onChangeRule({ ...currentRule, code: event.target.value.toUpperCase().replace(/[^A-Z0-9_]/g, '_') })} /></HelpedField>
        <HelpedField label="Severity" targetId={`${prefix}-severity`} required help="Choose how important the problem is for the model user. Use Error when the model should not be considered valid, Warning when the user should review something but may continue, and Information for useful non-blocking guidance." example="Use Error when a required identifier is missing; use Warning when a recommended description is empty." options={[...SEVERITY_OPTIONS]}><HydraAdaptiveSelect showOptionHelp={false} id={`${prefix}-severity`} ariaLabel="Severity" required value={currentRule.severity} options={SEVERITY_OPTIONS} onChange={severity => onChangeRule({ ...currentRule, severity: severity as ValidationRule['severity'] })} /></HelpedField>
        <HelpedField label="Group" targetId={`${prefix}-group`} help="Optional rule set used to organize related validations. A rule set lets you enable or disable the contained rules as a set, while moving a rule between groups does not change its condition or diagnostic semantics." example="Customer consistency rules" options={[{ value:'',label:'Ungrouped',description:'Keep this rule outside a named rule set.' }, ...validationModel.ruleSets.map(item => ({ value:item.id,label:item.name,description:item.description || 'Named rule set that can be enabled or disabled together.' }))]}><HydraAdaptiveSelect showOptionHelp={false} id={`${prefix}-group`} ariaLabel="Group" value={groupId} options={[{ value:'',label:'Ungrouped',description:'Keep this rule outside a named rule set.',category:'Recommended' }, ...validationModel.ruleSets.map(item => ({ value:item.id,label:item.name,description:item.description || 'Named rule set that can be enabled or disabled together.',category:'Rule sets' }))]} onChange={ruleSetId => onMoveToGroup(currentRule.id, ruleSetId)} /></HelpedField>
        <div className="validation-inline-help-field"><FieldLabel label="Enabled" targetId={`${prefix}-enabled`} help="Controls whether the rule is active in the validation runtime. When disabled, the rule does not produce diagnostics and is not evaluated by HYDRA. Keep it disabled while drafting or repairing broken references; enable it after the guided checks and Check definitions pass." example="Disable CUSTOMER_AGE_POSITIVE while changing the underlying age attribute, then re-enable it after updating the rule." /><input id={`${prefix}-enabled`} type="checkbox" checked={currentRule.enabled} onChange={event => onChangeRule({ ...currentRule, enabled: event.target.checked })} /></div>
    </div>;

    const contextFields = <><div className="validation-rule-fields two-columns">
        <HelpedField label="Execution mode" targetId={`${prefix}-execution-mode`} required help="Choose when HYDRA should spend time evaluating this rule. Incremental checks only the affected part after a change, Full validation waits for a complete check, Manual runs only when requested, and External leaves execution to another validation engine." example="Choose Incremental when you want an age rule to be checked immediately after the age changes."><HydraAdaptiveSelect showOptionHelp={false} id={`${prefix}-execution-mode`} ariaLabel="Execution mode" required value={currentRule.executionMode} options={EXECUTION_MODE_OPTIONS} onChange={executionMode => onChangeRule({ ...currentRule, executionMode: executionMode as ValidationRule['executionMode'] })} /></HelpedField>
        <HelpedField label="Context" targetId={`${prefix}-context`} required help="Choose what part of the model this rule is about. Select a class, attribute or relation for a precise field-level problem, or choose the complete model for rules that compare several elements or global counts." example="Choose Attribute → Person → age for an age rule; choose Complete model for a rule about unique identifiers." options={CONTEXT_OPTIONS.map(option => ({ value:option.value,label:option.label,description:option.description }))}><HydraAdaptiveSelect showOptionHelp={false} id={`${prefix}-context`} ariaLabel="Context" required value={currentRule.context.kind} options={CONTEXT_OPTIONS} onChange={kind => changeContextKind(kind as ValidationRule['context']['kind'])} /></HelpedField>
    </div>{hasStructuralContext && <div className="validation-rule-fields two-columns">
        <HelpedField label="Context class" targetId={`${prefix}-context-class`} required={contextClassRequired} help="Choose the type of element that this rule checks. This class also defines what Current element means inside the condition. HYDRA will then show the attributes and relations of that type in the guided and advanced condition builders, including inherited members when they are compatible." example="Choose Person when the rule checks the age of a person." options={metamodel.classes.map(item => ({ value:item.id,label:item.name,description:`${item.kind.toLowerCase().replaceAll('_', ' ')} declared in the selected metamodel.` }))}><HydraAdaptiveSelect showOptionHelp={false} id={`${prefix}-context-class`} ariaLabel="Context class" required={contextClassRequired} placeholder="Select class" value={currentRule.context.classId ?? ''} options={metamodel.classes.map(item => ({ value:item.id,label:item.name,description:`${item.kind.toLowerCase().replaceAll('_', ' ')} declared in the selected metamodel.`,category:item.kind === 'INTERFACE' ? 'Interfaces' : item.kind === 'ABSTRACT_CLASS' ? 'Abstract classes' : 'Concrete classes' }))} onChange={classId => { onChangeRule({ ...currentRule, context: { ...currentRule.context, classId, featureId: undefined } }); onChangeExpression({ ...currentExpression, contextClassId: classId }); }} /></HelpedField>
        {targetFeatureRequired && <HelpedField label="Target feature" targetId={`${prefix}-target-feature`} required help="Attribute or relation directly associated with the diagnostic so the generated model editor can highlight the exact field or connection." example={currentRule.context.kind === 'ATTRIBUTE' ? 'Person.age' : 'Company.employees'} options={currentRule.context.kind === 'ATTRIBUTE' ? attributes.map(({ owner, attribute }) => ({ value:attribute.id,label:`${owner.name}.${attribute.name}`,description:`Attribute of ${owner.name}.` })) : relations.map(relation => ({ value:relation.id,label:relation.name || relation.kind,description:`${relation.kind.toLowerCase()} relation.` }))}><HydraAdaptiveSelect showOptionHelp={false} id={`${prefix}-target-feature`} ariaLabel="Target feature" required placeholder="Select feature" value={currentRule.context.featureId ?? ''} options={currentRule.context.kind === 'ATTRIBUTE' ? attributes.map(({ owner, attribute }) => ({ value:attribute.id,label:`${owner.name}.${attribute.name}`,description:`Attribute of ${owner.name}.`,category:owner.name })) : relations.map(relation => ({ value:relation.id,label:relation.name || relation.kind,description:`${relation.kind.toLowerCase()} relation.`,category:relation.kind === 'CONTAINMENT' ? 'Containment relations' : 'Relations' }))} onChange={featureId => onChangeRule({ ...currentRule, context: { ...currentRule.context, featureId } })} /></HelpedField>}
    </div>}</>;

    const diagnosticFields = <><HelpedField className="validation-wide-field" label="Description" targetId={`${prefix}-description`} help="Internal explanation of why this validation exists. It helps other designers maintain the rule without changing the end-user message." example="Prevents negative ages imported from external data."><textarea id={`${prefix}-description`} rows={2} value={currentRule.description ?? ''} onChange={event => onChangeRule({ ...currentRule, description: event.target.value })} /></HelpedField>
        <HelpedField className="validation-wide-field" label="Message shown when the rule fails" targetId={`${prefix}-message`} required help="Diagnostic shown to model users when the condition evaluates to false. Explain what is wrong and, when possible, how to correct it." example="Age cannot be negative. Enter zero or a positive value."><textarea id={`${prefix}-message`} required rows={2} value={currentRule.messageTemplate} onChange={event => onChangeRule({ ...currentRule, messageTemplate: event.target.value })} /></HelpedField>
        <fieldset className="validation-choice-fieldset"><legend><FieldLabel label="When should it run?" targetId={`${prefix}-trigger-${triggers[0]}`} required={triggersRequired} help="Choose the moments when HYDRA should check the rule. For most field rules, checking after the related value changes and again when saving gives fast feedback without running every rule all the time." example="For an age rule, choose When an attribute changes and When the model is saved." /></legend><div className="validation-chip-grid">{triggers.map(trigger => <label className={currentRule.triggers.includes(trigger) ? 'selected' : ''} key={trigger}><input id={`${prefix}-trigger-${trigger}`} type="checkbox" checked={currentRule.triggers.includes(trigger)} onChange={() => toggleTrigger(trigger)} />{t(TRIGGER_LABELS[trigger])}</label>)}</div></fieldset></>;

    const conditionSection = <ExpressionBuilder expression={currentExpression} metamodel={metamodel} onChange={onChangeExpression} />;
    const quickFixSection = <fieldset className="validation-choice-fieldset"><legend><FieldLabel label="Available quick fixes" targetId={`${prefix}-quick-fix-${actionModel.actions[0]?.id ?? 'none'}`} help="Associates declarative repair actions with this diagnostic. Quick fixes are not executed automatically: the user chooses a repair explicitly, and its own precondition, transaction and undo policies still apply. Multiple alternatives may be offered." example="For NEGATIVE_AGE offer Set age to zero and Restore imported value." /></legend>{actionModel.actions.length === 0 ? <p>No quick fixes have been designed yet.</p> : <div className="validation-chip-grid">{actionModel.actions.map(action => <label className={currentRule.quickFixIds.includes(action.id) ? 'selected' : ''} key={action.id}><input id={`${prefix}-quick-fix-${action.id}`} type="checkbox" checked={currentRule.quickFixIds.includes(action.id)} onChange={() => toggleQuickFix(action.id)} />{action.name}</label>)}</div>}</fieldset>;
    const advancedSection = <details className="validation-advanced-section"><summary>Advanced and imported-rule properties</summary><div className="validation-rule-fields two-columns"><HelpedField label="Authoritative representation" targetId={`${prefix}-authoritative`} required help="Choose where the editable source of this rule lives. HYDRA means the rule is edited here; imported OCL, EVL, ATL, Sirius AQL, Java, Xtext or external rules can keep their original technology as the source." example="HYDRA for a rule created in this designer." options={AUTHORITATIVE_OPTIONS.map(option => ({ value:option.value,label:option.label,description:option.description }))}><HydraAdaptiveSelect showOptionHelp={false} id={`${prefix}-authoritative`} ariaLabel="Authoritative representation" required value={currentRule.authoritativeRepresentation} options={AUTHORITATIVE_OPTIONS} onChange={authoritativeRepresentation => onChangeRule({ ...currentRule, authoritativeRepresentation: authoritativeRepresentation as ValidationRule['authoritativeRepresentation'] })} /></HelpedField><HelpedField label="Conversion status" targetId={`${prefix}-conversion-status`} required help="How completely an imported rule is represented by HYDRA. Partial/external states preserve unsupported fragments rather than silently losing them." example="FULL when every part is editable visually; PARTIAL when an external fragment is preserved." options={[...CONVERSION_STATUS_OPTIONS]}><HydraAdaptiveSelect showOptionHelp={false} id={`${prefix}-conversion-status`} ariaLabel="Conversion status" required value={currentRule.conversionStatus} options={CONVERSION_STATUS_OPTIONS} onChange={conversionStatus => onChangeRule({ ...currentRule, conversionStatus: conversionStatus as ValidationRule['conversionStatus'] })} /></HelpedField></div><p>Current context: {contextClass?.name ?? currentRule.context.kind}. External source editing remains available through the corresponding import adapters.</p></details>;

    const stableCodeValid = isStableElementId(currentRule.code);
    const selectedContextClass = currentRule.context.classId === undefined ? undefined : metamodel.classes.find(item => item.id === currentRule.context.classId);
    const selectedContextRelation = currentRule.context.featureId === undefined ? undefined : metamodel.relations.find(item => item.id === currentRule.context.featureId);
    const contextComplete = (() => {
        switch (currentRule.context.kind) {
            case 'CLASS':
            case 'OBJECT':
                return selectedContextClass !== undefined;
            case 'ATTRIBUTE':
                return selectedContextClass !== undefined && selectedContextClass.attributes.some(attribute => attribute.id === currentRule.context.featureId);
            case 'RELATION':
                return selectedContextRelation !== undefined && (currentRule.context.classId === undefined || currentRule.context.classId === '' || selectedContextRelation.sourceTypeId === currentRule.context.classId);
            case 'MODEL':
            case 'PROJECT':
            case 'REPRESENTATION':
            case 'SELECTION':
                return true;
        }
    })();
    const triggerComplete = (() => {
        if (currentRule.executionMode === 'EXTERNAL') return currentRule.authoritativeRepresentation !== 'CANONICAL';
        if (currentRule.triggers.length === 0) return false;
        if (currentRule.executionMode === 'MANUAL_ONLY') return currentRule.triggers.every(trigger => trigger === 'MANUAL');
        if (currentRule.executionMode === 'FULL_ONLY') return currentRule.triggers.every(trigger => trigger === 'FULL_VALIDATION' || trigger === 'ON_SAVE' || trigger === 'BEFORE_EXPORT');
        return true;
    })();
    const expressionComplete = findExpressionBlockingError(currentExpression) === '';
    const quickFixReferencesValid = currentRule.quickFixIds.every(id => {
        const action = actionModel.actions.find(candidate => candidate.id === id);
        return action !== undefined && findQuickFixBlockingError(action) === '';
    }) && actionModel.actions.every(action => findQuickFixBlockingError(action) === '');
    const identityReady = currentRule.name.trim() !== '' && stableCodeValid && currentRule.messageTemplate.trim() !== '';
    const steps = [
        { id:'identity', title:'Identity', description:'Name and classify the diagnostic.', checks:[{ id:'name',label:'Validation name is defined',complete:currentRule.name.trim() !== '',detail:'Visible in the validation catalogue.'},{ id:'code',label:'Stable problem code is defined',complete:stableCodeValid,detail:'Use only a stable identifier accepted by HYDRA: letters, numbers, dots, underscores, colons or hyphens.'}], content:identityFields },
        { id:'context', title:'Context', description:'Choose what is checked and how it executes.', checks:[{ id:'context-valid',label:'Required context references are selected',complete:contextComplete,detail:contextClassRequired ? 'Select the class and target feature required by the chosen context.' : 'This context does not require a class feature.'},{ id:'mode',label:'Execution mode is defined',complete:triggerComplete || currentRule.executionMode !== 'EXTERNAL',detail:`${t('Current mode')}: ${t(EXECUTION_MODE_OPTIONS.find(option => option.value === currentRule.executionMode)?.label ?? currentRule.executionMode)}.`}], content:contextFields },
        { id:'diagnostic', title:'Diagnostic and triggers', description:'Explain the failure and when HYDRA should evaluate it.', checks:[{ id:'message',label:'Failure message is defined',complete:currentRule.messageTemplate.trim() !== '',detail:'The message should explain the problem in a way the model user can act on.'},{ id:'triggers',label:'Execution triggers are valid',complete:triggerComplete,detail:triggersRequired ? `${currentRule.triggers.length} trigger(s) selected.` : 'External execution requires an external authoritative representation.'}], content:diagnosticFields },
        { id:'condition', title:'Condition', description:'Build the valid-state expression in guided or advanced mode.', checks:[{ id:'expression-id',label:'Condition expression is identified',complete:isStableElementId(currentExpression.id),detail:'The validation rule is linked to this HYDRA condition definition.'},{ id:'expression-name',label:'Condition expression has a name',complete:currentExpression.name.trim() !== '',detail:'Use the guided condition builder for common comparisons or Advanced for complex trees.'},{ id:'expression-valid',label:'Condition expression is structurally valid',complete:expressionComplete,detail:'Every condition node and reference must be complete before continuing.'}], content:conditionSection },
        { id:'quickfix', title:'Quick fixes', description:'Optionally associate safe declarative repairs.', checks:[{ id:'fix-refs',label:'Associated quick fixes exist',complete:quickFixReferencesValid,detail:currentRule.quickFixIds.length === 0 ? 'No repair is associated; this is valid.' : `${currentRule.quickFixIds.length} repair option(s) linked.`}], content:quickFixSection },
        { id:'review', title:'Review', description:'Verify the rule before Check definitions and saving.', checks:[{ id:'identity-ready',label:'Identity and diagnostic are complete',complete:identityReady,detail:'Name, code and failure message are mandatory.'},{ id:'runtime-ready',label:'Context, condition and triggers are ready',complete:contextComplete && expressionComplete && triggerComplete,detail:'The runtime has enough information to decide where and when to execute.'},{ id:'fix-ready',label:'Quick-fix references are consistent',complete:quickFixReferencesValid,detail:'Broken action associations are not allowed.'},{ id:'all-ready',label:'All validation definitions are ready to save',complete:allDefinitionsReady,detail:'No other rule, expression or quick-fix may contain a blocking error.'}], content:<div className="hydra-guided-review-summary"><strong>{currentRule.name || 'Unnamed validation'}</strong><span>{currentRule.code}</span><p>{currentRule.messageTemplate || 'No failure message.'}</p><dl><div><dt>Severity</dt><dd>{currentRule.severity}</dd></div><div><dt>Context</dt><dd>{currentRule.context.kind}</dd></div><div><dt>Execution</dt><dd>{currentRule.executionMode}</dd></div><div><dt>Quick fixes</dt><dd>{currentRule.quickFixIds.length}</dd></div></dl></div> }
    ];

    return <section className="validation-rule-editor">
        <div className="validation-rule-editor-header"><div><h3>{currentRule.name || 'Validation'}</h3><span>{currentRule.code}</span></div><HydraCreationModeSwitch mode={creationMode} onChange={setCreationMode} advancedLabel="Full form" /><HydraButton className="validation-danger-button" type="button" onClick={() => onDelete(currentRule.id)}>Delete</HydraButton></div>
        {importedVisualRule && <div className="validation-visual-association"><strong>{t('Associated visual representation')}</strong><span>{associatedRepresentationId || t('Representation not identified')}</span><p>{t('This rule was imported with a visual representation. Its original Sirius/AQL condition is preserved and the rule remains external and disabled until it is reviewed or converted safely in HYDRA.')}</p></div>}
        <RequiredFieldsLegend />
        {creationMode === 'GUIDED'
            ? <>
                <HydraGuidedWorkflow key={currentRule.id} title="Guided validation" description="Define identity, scope, diagnostic, condition and optional repairs in a verified order. The full form and advanced expression tree remain available without data conversion." steps={steps} currentStepId={guidedStep} onStepChange={setGuidedStep} finishLabel="Save validation" onFinish={onFinishGuided} />
                {actionModel.actions.length > 0 && guidedStep !== 'quickfix' && <section className="validation-guided-associated-repairs" aria-label="Quick fixes available for this validation">{quickFixSection}</section>}
            </>
            : <>{identityFields}{contextFields}{diagnosticFields}{conditionSection}{quickFixSection}{advancedSection}</>}
    </section>;

}

function safeId(value: string): string {
    return value.replace(/[^A-Za-z0-9_-]/g, '-');
}
