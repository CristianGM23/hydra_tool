import { useContext, useMemo } from 'react';
import { ResizableInfoTooltip } from '../designer-ui/ResizableInfoTooltip';
import { useHydraLanguage } from '../i18n/useHydraLanguage';
import { HydraFieldOptionHelpContext } from '../design-system/HydraFieldOptionHelpContext';
import './hydra-adaptive-select.css';

export interface HydraSelectOption {
    value: string;
    label: string;
    description?: string;
    category?: string;
    disabled?: boolean;
    keywords?: string[];
}

export interface HydraAdaptiveSelectProps {
    id?: string;
    ariaLabel?: string;
    value: string;
    options: readonly HydraSelectOption[];
    onChange: (value: string) => void;
    placeholder?: string;
    disabled?: boolean;
    required?: boolean;
    className?: string;
    searchThreshold?: number;
    /** Deprecated compatibility hint. Native select remains keyboard-searchable without a second filter control. */
    showFilter?: boolean;
    emptyMessage?: string;
    showOptionHelp?: boolean;
    optionHelpTitle?: string;
}

const DEFAULT_GROUP_THRESHOLD = 7;

/**
 * Selector común de HYDRA. Las listas largas se agrupan, pero no muestran un
 * filtro auxiliar: el select nativo conserva teclado, accesibilidad y búsqueda
 * incremental del navegador sin mantener dos controles desincronizados.
 */
export function HydraAdaptiveSelect({
    id,
    ariaLabel,
    value,
    options,
    onChange,
    placeholder,
    disabled = false,
    required = false,
    className = '',
    searchThreshold = DEFAULT_GROUP_THRESHOLD,
    emptyMessage = 'No matching values',
    showOptionHelp,
    optionHelpTitle
}: HydraAdaptiveSelectProps) {
    const { t } = useHydraLanguage();
    const fieldOwnsOptionHelp = useContext(HydraFieldOptionHelpContext);
    const grouped = options.length >= searchThreshold;
    const groups = useMemo(() => groupOptions([...options]), [options]);
    const describedOptions = options.map(option => ({ ...option, description: (option.description ?? '').trim() === '' ? 'Choose this value when it matches the configuration you want for this field.' : option.description }));
    // Every selector exposes its complete value vocabulary by default. This keeps dynamic values
    // understandable without forcing each feature to reimplement option-level help.
    const resolvedOptionHelpVisible = showOptionHelp ?? options.length > 0;
    const optionHelpVisible = showOptionHelp === true || (!fieldOwnsOptionHelp && resolvedOptionHelpVisible);

    return <div className={`hydra-adaptive-select${grouped ? ' grouped' : ''}${className === '' ? '' : ` ${className}`}`}>
        <div className="hydra-adaptive-select-control">
            <select
                aria-label={ariaLabel === undefined ? undefined : t(ariaLabel)}
                disabled={disabled}
                id={id}
                required={required}
                value={value}
                onChange={event => onChange(event.target.value)}
            >
                {placeholder !== undefined && !options.some(option => option.value === '') && <option value="">{t(placeholder)}</option>}
                {grouped
                    ? groups.map(group => <optgroup key={group.name} label={t(group.name)}>
                        {group.options.map(option => <option disabled={option.disabled} key={option.value} title={option.description === undefined ? undefined : t(option.description)} value={option.value}>{t(option.label)}</option>)}
                    </optgroup>)
                    : options.map(option => <option disabled={option.disabled} key={option.value} title={option.description === undefined ? undefined : t(option.description)} value={option.value}>{t(option.label)}</option>)}
                {options.length === 0 && <option disabled value="__hydra-empty__">{t(emptyMessage)}</option>}
            </select>
            {optionHelpVisible && describedOptions.length > 0 && <ResizableInfoTooltip
                label={`${t('Information about')} ${t(ariaLabel ?? 'available values')}`}
                title={optionHelpTitle === undefined ? `${t(ariaLabel ?? 'Available values')} ${t('options')}` : t(optionHelpTitle)}
            >
                <span className="hydra-adaptive-select-option-help">
                    {groupOptions([...describedOptions]).map(group => <span className="hydra-adaptive-select-option-group" key={group.name}>
                        <strong>{t(group.name)}</strong>
                        {group.options.map(option => <span key={option.value}><b>{t(option.label)}</b><small>{option.description === undefined ? null : t(option.description)}</small></span>)}
                    </span>)}
                </span>
            </ResizableInfoTooltip>}
        </div>
    </div>;
}

function groupOptions(options: HydraSelectOption[]): Array<{ name: string; options: HydraSelectOption[] }> {
    if (options.length === 0) return [];
    const explicitCategories = options.some(option => (option.category ?? '').trim() !== '');
    const result = new Map<string, HydraSelectOption[]>();
    for (const option of options) {
        const category = explicitCategories ? (option.category?.trim() || 'Other') : automaticCategory(option.label, options.length);
        result.set(category, [...(result.get(category) ?? []), option]);
    }
    return [...result.entries()]
        .map(([name, values]) => ({ name, options: [...values] }))
        .sort((left, right) => categoryOrder(left.name) - categoryOrder(right.name) || left.name.localeCompare(right.name));
}

function automaticCategory(label: string, count: number): string {
    if (count <= 12) return 'Available values';
    const first = normalize(label).charAt(0).toUpperCase();
    if (first >= 'A' && first <= 'F') return 'A–F';
    if (first >= 'G' && first <= 'L') return 'G–L';
    if (first >= 'M' && first <= 'R') return 'M–R';
    if (first >= 'S' && first <= 'Z') return 'S–Z';
    return 'Other';
}

function categoryOrder(category: string): number {
    const order = ['Recommended', 'Current project', 'System events', 'Custom events', 'Lifecycle', 'Events and data', 'CEP and state', 'Execution', 'Control and resilience', 'Basic inputs', 'Choice and references', 'Temporal values', 'Display only', 'Compact', 'Comfortable', 'Large', 'A–F', 'G–L', 'M–R', 'S–Z', 'Available values', 'Other'];
    const index = order.indexOf(category);
    return index === -1 ? 100 : index;
}

function normalize(value: string): string {
    return value.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase();
}
