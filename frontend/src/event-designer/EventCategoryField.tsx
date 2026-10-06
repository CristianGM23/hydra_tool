import { useMemo, useState } from 'react';
import { HydraAdaptiveSelect } from '../components/HydraAdaptiveSelect';
import { DesignerHelpedField } from '../designer-ui/DesignerFieldHelp';
import { EVENT_CATEGORY_OPTIONS, isPredefinedEventCategory } from './eventCategoryOptions';

interface Props { value: string; onChange: (category: string) => void; }

export function EventCategoryField({ value, onChange }: Props) {
    const initialCustom = !isPredefinedEventCategory(value) ? (value.trim() || 'CUSTOM') : '';
    const [customCategory, setCustomCategory] = useState(initialCustom);
    const selection = useMemo(() => isPredefinedEventCategory(value) ? value.trim().toUpperCase() : '__CUSTOM__', [value]);

    const customValue = selection === '__CUSTOM__' ? (value.trim() || 'CUSTOM') : customCategory;

    function select(next: string): void {
        if (next === '__CUSTOM__') onChange(customCategory.trim() || 'CUSTOM');
        else onChange(next);
    }

    return <DesignerHelpedField
        label="Category"
        targetId="event-type-category"
        required
        help="Choose the functional family used to organize this event and make it easier to discover in palettes, bindings and flows. HYDRA provides common families, but domain-specific categories remain supported."
        example="DOMAIN"
        options={EVENT_CATEGORY_OPTIONS.map(option => ({ value: option.value, label: option.label, description: option.description ?? '' }))}
    >
        <div className="event-category-control">
            <HydraAdaptiveSelect id="event-type-category" ariaLabel="Category" value={selection} options={EVENT_CATEGORY_OPTIONS} onChange={select} />
            {selection === '__CUSTOM__' && <div className="event-category-custom">
                <label htmlFor="event-type-custom-category">Custom category</label>
                <input id="event-type-custom-category" value={customValue} onChange={event => { setCustomCategory(event.target.value); onChange(event.target.value.trim() || 'CUSTOM'); }} />
                <small>Use a short, stable category name. Prefer a predefined HYDRA family when it already expresses the same meaning.</small>
            </div>}
        </div>
    </DesignerHelpedField>;
}
