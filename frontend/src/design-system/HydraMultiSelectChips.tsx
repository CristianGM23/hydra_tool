import { HydraButton } from './HydraButton';
import { useMemo, useState } from 'react';

export interface HydraMultiSelectOption {
    value: string;
    label: string;
    description?: string;
    category?: string;
}

export interface HydraMultiSelectChipsProps {
    id: string;
    ariaLabel: string;
    options: readonly HydraMultiSelectOption[];
    selected: readonly string[];
    onChange: (selected: string[]) => void;
    placeholder?: string;
    allowCustom?: boolean;
    customLabel?: string;
    emptySelectionLabel?: string;
    className?: string;
}

/**
 * Selección múltiple basada en checkboxes y píldoras. Los valores conocidos se
 * eligen visualmente; el campo personalizado opcional se conserva únicamente
 * para compatibilidad avanzada con contratos antiguos o adaptadores externos.
 */
export function HydraMultiSelectChips({ id, ariaLabel, options, selected, onChange, placeholder = 'Search options…', allowCustom = false, customLabel = 'Custom value', emptySelectionLabel = 'No restrictions selected', className = '' }: HydraMultiSelectChipsProps) {
    const [filter, setFilter] = useState('');
    const [showOptions, setShowOptions] = useState(true);
    const [customValue, setCustomValue] = useState('');
    const optionMap = useMemo(() => new Map(options.map(option => [option.value, option])), [options]);
    const normalized = filter.trim().toLowerCase();
    const filtered = options.filter(option => normalized === '' || `${option.label} ${option.value} ${option.description ?? ''} ${option.category ?? ''}`.toLowerCase().includes(normalized));
    const categories = [...new Set(filtered.map(option => option.category ?? 'Options'))];

    function toggle(value: string): void {
        onChange(selected.includes(value) ? selected.filter(item => item !== value) : [...selected, value]);
    }

    function addCustom(): void {
        const value = customValue.trim();
        if (value === '' || selected.includes(value)) return;
        onChange([...selected, value]);
        setCustomValue('');
    }

    return <div className={`hydra-multiselect-chips${className === '' ? '' : ` ${className}`}`} id={id} aria-label={ariaLabel}>
        <div className="hydra-multiselect-chip-field">
            <div className="hydra-multiselect-chip-list">
                {selected.length === 0 && <span className="hydra-multiselect-empty">{emptySelectionLabel}</span>}
                {selected.map(value => {
                    const option = optionMap.get(value);
                    return <span className={`hydra-multiselect-chip${option === undefined ? ' custom' : ''}`} key={value}>
                        <span>{option?.label ?? value}</span>
                        <HydraButton aria-label={`Remove ${option?.label ?? value}`} type="button" onClick={() => toggle(value)}>×</HydraButton>
                    </span>;
                })}
            </div>
            <HydraButton className="hydra-multiselect-options-toggle" aria-expanded={showOptions} type="button" onClick={() => setShowOptions(value => !value)}>{showOptions ? 'Hide choices' : 'Choose values'}</HydraButton>
        </div>
        {showOptions && <div className="hydra-multiselect-option-panel">
            <div className="hydra-multiselect-toolbar">
                <input aria-label={`Search ${ariaLabel}`} placeholder={placeholder} value={filter} onChange={event => setFilter(event.target.value)} />
                <HydraButton type="button" onClick={() => onChange([...new Set([...selected, ...filtered.map(option => option.value)])])}>Select visible</HydraButton>
                <HydraButton type="button" onClick={() => onChange(selected.filter(value => !filtered.some(option => option.value === value)))}>Clear visible</HydraButton>
            </div>
            <div className="hydra-multiselect-groups">
                {categories.map(category => <fieldset key={category}>
                    <legend>{category}</legend>
                    <div>{filtered.filter(option => (option.category ?? 'Options') === category).map(option => <label key={option.value} title={option.description}>
                        <input checked={selected.includes(option.value)} type="checkbox" onChange={() => toggle(option.value)} />
                        <span><strong>{option.label}</strong>{option.description !== undefined && <small>{option.description}</small>}</span>
                    </label>)}</div>
                </fieldset>)}
                {filtered.length === 0 && <p className="hydra-multiselect-no-results">No matching options.</p>}
            </div>
            {allowCustom && <details className="hydra-multiselect-custom">
                <summary>Advanced compatibility: add a custom value</summary>
                <p>Use this only for an external or custom source that is not present in the HYDRA catalog.</p>
                <div><input aria-label={customLabel} value={customValue} onChange={event => setCustomValue(event.target.value)} onKeyDown={event => { if (event.key === 'Enter') { event.preventDefault(); addCustom(); } }} /><HydraButton disabled={customValue.trim() === ''} type="button" onClick={addCustom}>Add</HydraButton></div>
            </details>}
        </div>}
    </div>;
}
