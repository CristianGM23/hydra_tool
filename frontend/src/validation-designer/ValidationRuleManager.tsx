import { HydraButton } from '../design-system/HydraButton';
import { useMemo, useState } from 'react';
import { HydraAdaptiveSelect } from '../components/HydraAdaptiveSelect';
import type { ValidationModel, ValidationRule } from '../validation/types';
import { countRuleDiagnostics, ruleGroupId } from './validationDesignerSelectors';
import { createValidationDesignerId } from './validationDesignerFactory';
import type { ValidationModelCheckReport } from '../validation/types';
import { ProcessGuideButton } from './ValidationFieldHelp';

interface ValidationRuleManagerProps {
    model: ValidationModel;
    selectedRuleId: string;
    report: ValidationModelCheckReport | null;
    onChange: (model: ValidationModel) => void;
    onCreateRule: (ruleSetId?: string) => void;
    onDuplicateRule: (ruleId: string) => void;
    onSelectRule: (ruleId: string) => void;
}

export function ValidationRuleManager({ model, selectedRuleId, report, onChange, onCreateRule, onDuplicateRule, onSelectRule }: ValidationRuleManagerProps) {
    const [filter, setFilter] = useState('');
    const [severity, setSeverity] = useState<'ALL' | ValidationRule['severity']>('ALL');
    const normalizedFilter = filter.trim().toLowerCase();
    const ungroupedRules = useMemo(() => model.rules.filter(rule => ruleGroupId(rule.id, model.ruleSets) === ''), [model.rules, model.ruleSets]);

    function addRuleSet(): void {
        const nextOrder = model.ruleSets.length;
        const id = createValidationDesignerId('rule-set');
        onChange({
            ...model,
            ruleSets: [...model.ruleSets, { id, name: `Validation group ${nextOrder + 1}`, description: '', enabled: true, order: nextOrder, ruleIds: [], annotations: {} }]
        });
    }

    function renameRuleSet(ruleSetId: string, name: string): void {
        onChange({ ...model, ruleSets: model.ruleSets.map(item => item.id === ruleSetId ? { ...item, name } : item) });
    }

    function toggleRuleSet(ruleSetId: string): void {
        onChange({ ...model, ruleSets: model.ruleSets.map(item => item.id === ruleSetId ? { ...item, enabled: !item.enabled } : item) });
    }

    function deleteRuleSet(ruleSetId: string): void {
        onChange({ ...model, ruleSets: model.ruleSets.filter(item => item.id !== ruleSetId) });
    }

    function matches(rule: ValidationRule): boolean {
        if (severity !== 'ALL' && rule.severity !== severity) return false;
        if (normalizedFilter === '') return true;
        return [rule.name, rule.code, rule.description ?? '', rule.messageTemplate].some(value => value.toLowerCase().includes(normalizedFilter));
    }

    function renderRule(rule: ValidationRule) {
        const diagnosticCount = countRuleDiagnostics(rule, report);
        return (
            <HydraButton
                className={`validation-rule-list-item${selectedRuleId === rule.id ? ' selected' : ''}`}
                key={rule.id}
                type="button"
                onClick={() => onSelectRule(rule.id)}
            >
                <span className={`validation-severity-dot ${rule.severity.toLowerCase()}`} aria-label={rule.severity} />
                <span className="validation-rule-list-copy">
                    <strong>{rule.name || 'Unnamed validation'}</strong>
                    <small>{rule.code}</small>
                </span>
                {!rule.enabled && <span className="validation-rule-state">Off</span>}
                {diagnosticCount > 0 && <span className="validation-rule-diagnostics">{diagnosticCount}</span>}
            </HydraButton>
        );
    }

    return (
        <aside className="validation-rule-manager">
            <div className="validation-panel-heading">
                <div><h3>Validations</h3><span>{model.rules.length} rule(s)</span></div>
                <div className="validation-panel-heading-actions"><ProcessGuideButton kind="RULE" /><HydraButton type="button" onClick={() => onCreateRule()}>+ Rule</HydraButton></div>
            </div>
            <div className="validation-rule-filters">
                <input aria-label="Search validations" placeholder="Search rules..." value={filter} onChange={event => setFilter(event.target.value)} />
                <HydraAdaptiveSelect
                    ariaLabel="Filter by severity"
                    value={severity}
                    options={[
                        { value: 'ALL', label: 'All severities', description: 'Show every validation rule regardless of diagnostic severity.', category: 'Visibility' },
                        { value: 'ERROR', label: 'Errors', description: 'Show rules that report blocking or invalid model states.', category: 'Diagnostic severity' },
                        { value: 'WARNING', label: 'Warnings', description: 'Show rules that report risky or discouraged states.', category: 'Diagnostic severity' },
                        { value: 'INFO', label: 'Information', description: 'Show rules that provide non-blocking guidance.', category: 'Diagnostic severity' }
                    ]}
                    onChange={next => setSeverity(next as typeof severity)}
                />
            </div>
            <div className="validation-rule-set-toolbar">
                <HydraButton type="button" onClick={addRuleSet}>+ Group</HydraButton>
                {selectedRuleId !== '' && <HydraButton type="button" onClick={() => onDuplicateRule(selectedRuleId)}>Duplicate</HydraButton>}
            </div>
            <div className="validation-rule-tree">
                {model.ruleSets.slice().sort((left, right) => left.order - right.order).map(ruleSet => {
                    const rules = ruleSet.ruleIds
                        .map(ruleId => model.rules.find(rule => rule.id === ruleId))
                        .filter((rule): rule is ValidationRule => rule !== undefined && matches(rule));
                    return (
                        <section className="validation-rule-set" key={ruleSet.id}>
                            <div className="validation-rule-set-header">
                                <input aria-label="Validation group name" value={ruleSet.name} onChange={event => renameRuleSet(ruleSet.id, event.target.value)} />
                                <HydraButton aria-label={ruleSet.enabled ? 'Disable group' : 'Enable group'} type="button" onClick={() => toggleRuleSet(ruleSet.id)}>{ruleSet.enabled ? 'On' : 'Off'}</HydraButton>
                                <HydraButton aria-label="Add rule to group" type="button" onClick={() => onCreateRule(ruleSet.id)}>+</HydraButton>
                                <HydraButton aria-label="Delete group" type="button" onClick={() => deleteRuleSet(ruleSet.id)}>×</HydraButton>
                            </div>
                            {rules.length === 0 ? <p className="validation-empty-group">No matching rules</p> : rules.map(renderRule)}
                        </section>
                    );
                })}
                {ungroupedRules.filter(matches).length > 0 && (
                    <section className="validation-rule-set">
                        <div className="validation-rule-set-title">Ungrouped</div>
                        {ungroupedRules.filter(matches).map(renderRule)}
                    </section>
                )}
                {model.rules.length === 0 && <div className="validation-empty-state"><strong>No validations yet</strong><span>Create the first rule to start defining constraints.</span></div>}
            </div>
        </aside>
    );
}
