import { describe, expect, it } from 'vitest';
import { filterEventArchitecturePalette, groupEventArchitecturePalette } from './eventArchitecturePalette';
import type { EventArchitecturePaletteEntry } from './eventArchitecturePalette';

const entries: EventArchitecturePaletteEntry[] = [
    { eventTypeId: 'core.model.saved', label: 'Model saved', description: 'Stored successfully.', category: 'Model lifecycle', kind: 'RESERVED_EVENT', onCanvas: false },
    { eventTypeId: 'event:reviewed', label: 'Review completed', description: 'A reviewer accepted the model.', category: 'Custom events', kind: 'CUSTOM_EVENT', onCanvas: true },
    { eventTypeId: 'core.validation.completed', label: 'Validation completed', description: 'Validation execution finished.', category: 'Validation', kind: 'RESERVED_EVENT', onCanvas: false }
];

describe('event architecture palette', () => {
    it('groups the catalog by descriptive category', () => {
        expect(groupEventArchitecturePalette(entries).map(group => group.category))
            .toEqual(['Custom events', 'Model lifecycle', 'Validation']);
    });

    it('sorts entries by their high-level label inside each group', () => {
        const grouped = groupEventArchitecturePalette([...entries, { ...entries[0], eventTypeId: 'core.model.created', label: 'Model created' }]);
        expect(grouped.find(group => group.category === 'Model lifecycle')?.entries.map(item => item.label))
            .toEqual(['Model created', 'Model saved']);
    });

    it('searches labels, descriptions and categories without exposing IDs', () => {
        expect(filterEventArchitecturePalette(entries, 'reviewer').map(item => item.label)).toEqual(['Review completed']);
        expect(filterEventArchitecturePalette(entries, 'validation').map(item => item.label)).toEqual(['Validation completed']);
        expect(filterEventArchitecturePalette(entries, 'core.model')).toEqual([]);
    });

    it('preserves whether an event is already placed on the canvas', () => {
        expect(filterEventArchitecturePalette(entries, '').find(item => item.label === 'Review completed')?.onCanvas).toBe(true);
    });
});
