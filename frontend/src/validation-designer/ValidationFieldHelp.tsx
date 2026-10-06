import { HydraButton } from '../design-system/HydraButton';
import { useEffect, useId, useRef, useState, type ReactNode } from 'react';
import { createPortal } from 'react-dom';
import { HydraFormField, HydraRequiredFieldsLegend, HydraTooltip, HydraWindowControls } from '../design-system';
import { useHydraLanguage } from '../i18n';
import { fieldGuidance, shouldEnrichFieldHelp } from '../design-system/HydraFieldGuidance';

export interface ValidationSelectOptionHelp {
    value: string;
    label: string;
    description: string;
}

interface FieldLabelProps {
    label: string;
    targetId: string;
    help: string;
    example: string;
    required?: boolean;
    options?: ValidationSelectOptionHelp[];
}

/** Label reutilizable con ayuda contextual de alto nivel y foco sobre el control asociado. */
export function FieldLabel({ label, targetId, help, example, required = false, options = [] }: FieldLabelProps) {
    const { t, language } = useHydraLanguage();
    const guidance = shouldEnrichFieldHelp(help) ? fieldGuidance(label, help, language, options.length > 0) : null;
    const buttonRef = useRef<HTMLButtonElement>(null);

    return (
        <div className="validation-field-label-row">
            <label htmlFor={targetId === '' ? undefined : targetId}>{t(label)}</label>
            {required && <span className="validation-required-marker" aria-label={t('Required field')}>*</span>}
            <HydraTooltip
                label={`${t('Information about')} ${t(label)}`}
                title={`${t('About')} ${t(label)}`}
                className="validation-field-tooltip-portal"
                trigger={<HydraButton ref={buttonRef} className="hydra-context-help-button validation-field-info-button" type="button">i</HydraButton>}
                onOpenChange={open => { if (open) document.getElementById(targetId)?.focus(); }}
            >
                <p>{t(help)}</p>
                {guidance !== null && <section className="hydra-ui-field-guidance"><div><strong>{t('How to choose')}</strong><p>{guidance.choose}</p></div><div><strong>{t('What changes')}</strong><p>{guidance.impact}</p></div>{guidance.options !== undefined && <div><strong>{t('Use the current context')}</strong><p>{guidance.options}</p></div>}</section>}
                <div className="hydra-ui-tooltip-example"><span>{t('Example')}</span><code>{example}</code></div>
                {options.length > 0 && <section className="validation-field-option-help"><strong>{t('Available values')}</strong><dl>{options.map(option => <div key={option.value}><dt>{t(option.label)}</dt><dd>{t(option.description)}</dd></div>)}</dl></section>}
            </HydraTooltip>
        </div>
    );
}

interface HelpedFieldProps extends FieldLabelProps {
    children: ReactNode;
    className?: string;
}

/** Contenedor de un campo de formulario con ayuda y marcado de obligatoriedad. */
export function HelpedField({ children, className = '', ...labelProps }: HelpedFieldProps) {
    return <HydraFormField
        className={`validation-helped-field${className === '' ? '' : ` ${className}`}`}
        example={labelProps.example}
        help={labelProps.help}
        label={labelProps.label}
        labelRowClassName="validation-field-label-row"
        options={labelProps.options ?? []}
        required={labelProps.required}
        requiredMarkerClassName="validation-required-marker"
        targetId={labelProps.targetId}
        tooltipTriggerClassName="validation-field-info-button"
    >{children}</HydraFormField>;
}

export function RequiredFieldsLegend() {
    return <div className="validation-required-legend"><HydraRequiredFieldsLegend /></div>;
}

type GuideKind = 'RULE' | 'QUICK_FIX';

interface ProcessGuideButtonProps {
    kind: GuideKind;
}

const guides: Record<GuideKind, { title: string; introduction: string; steps: { title: string; description: string; example: string }[]; finalNote: string }> = {
    RULE: {
        title: 'How to create a validation rule',
        introduction: 'A rule identifies the model element to check, defines a boolean condition and explains the problem when that condition is false.',
        steps: [
            { title: '1. Identify the rule', description: 'Give the rule a clear name and a stable problem code. The code is used by diagnostics and future import/export adapters.', example: 'Name: Positive age · Code: PERSON_AGE_POSITIVE' },
            { title: '2. Choose the scope', description: 'Select whether the rule applies to a class, attribute, relation or the complete model. Select the class and feature when the chosen context requires them.', example: 'Context: Attribute · Class: Person · Feature: age' },
            { title: '3. Define its behaviour', description: 'Choose the severity, execution mode and at least one trigger for an executable rule.', example: 'Severity: Error · Mode: Incremental · Trigger: On attribute change' },
            { title: '4. Build the condition', description: 'Use the visual expression builder. The condition must evaluate to true when the model is valid.', example: 'age is not null AND age >= 0' },
            { title: '5. Explain and repair', description: 'Write the message shown when the condition is false and optionally associate one or more quick fixes.', example: 'Message: Age cannot be negative · Quick fix: Set age to zero' },
            { title: '6. Save and check', description: 'Save the definitions and run Check definitions to validate references, types, triggers and quick-fix associations.', example: 'Expected result: 0 errors' }
        ],
        finalNote: 'The condition describes the valid state. The message is shown only when that condition evaluates to false.'
    },
    QUICK_FIX: {
        title: 'How to create a quick fix',
        introduction: 'A quick fix is a declarative, reusable action that changes the model to solve a validation problem without writing code.',
        steps: [
            { title: '1. Identify the quick fix', description: 'Choose a concise name and optionally describe what will change.', example: 'Name: Set age to zero' },
            { title: '2. Restrict availability', description: 'Optionally select a boolean precondition. The quick fix will only be offered when the precondition is true.', example: 'Precondition: age < 0' },
            { title: '3. Choose transaction and undo policies', description: 'Atomic execution rolls back all changes if one operation fails. Undo support determines whether the action must be reversible.', example: 'Transaction: All or nothing · Undo: Required' },
            { title: '4. Add parameters when needed', description: 'Parameters allow the user or another action to provide values or model element identifiers at execution time.', example: 'Parameter: replacementAge · Type: Integer · Required: Yes' },
            { title: '5. Add and order operations', description: 'A quick fix needs at least one operation. Configure every required selector, class, feature or value.', example: 'Operation 1: Set attribute · Element: Current element · Attribute: age · Value: 0' },
            { title: '6. Associate it with a rule', description: 'Return to the rule form and select this quick fix in Available quick fixes.', example: 'Rule: Positive age → Quick fix: Set age to zero' },
            { title: '7. Save and check', description: 'Save the definitions and run Check definitions to detect incomplete selectors, invalid references and action cycles.', example: 'Expected result: 0 errors' }
        ],
        finalNote: 'Quick fixes are definitions only at this stage. Their transactional execution over model instances belongs to the following runtime blocks.'
    }
};

export function ProcessGuideButton({ kind }: ProcessGuideButtonProps) {
    const { t } = useHydraLanguage();
    const [open, setOpen] = useState(false);
    const dialogRef = useRef<HTMLDivElement>(null);
    const titleId = useId();
    const guide = guides[kind];

    useEffect(() => {
        if (!open) return;
        const previous = document.activeElement as HTMLElement | null;
        dialogRef.current?.focus();
        function closeOnEscape(event: KeyboardEvent): void { if (event.key === 'Escape') setOpen(false); }
        document.addEventListener('keydown', closeOnEscape);
        return () => { document.removeEventListener('keydown', closeOnEscape); previous?.focus(); };
    }, [open]);

    return (
        <>
            <HydraButton className="validation-process-info-button" type="button" onClick={() => setOpen(true)}>
                <span aria-hidden="true">i</span>
                {t(kind === 'RULE' ? 'Rule guide' : 'Quick-fix guide')}
            </HydraButton>
            {open && createPortal(
                <div className="validation-guide-backdrop" onMouseDown={event => { if (event.target === event.currentTarget) setOpen(false); }}>
                    <div aria-labelledby={titleId} aria-modal="true" className="validation-guide-dialog" ref={dialogRef} role="dialog" tabIndex={-1}>
                        <header>
                            <div><span className="validation-guide-icon" aria-hidden="true">i</span><div><h3 id={titleId}>{t(guide.title)}</h3><p>{t(guide.introduction)}</p></div></div>
                            <HydraWindowControls closeLabel="Close guide" onClose={() => setOpen(false)} />
                        </header>
                        <ol>{guide.steps.map(step => <li key={step.title}><strong>{t(step.title)}</strong><p>{t(step.description)}</p><code>{t(step.example)}</code></li>)}</ol>
                        <footer><p>{t(guide.finalNote)}</p><HydraButton type="button" onClick={() => setOpen(false)}>{t('Understood')}</HydraButton></footer>
                    </div>
                </div>, document.body
            )}
        </>
    );
}
