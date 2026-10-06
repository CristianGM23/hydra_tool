export interface EventArchitecturePaletteEntry {
    eventTypeId: string;
    label: string;
    description: string;
    category: string;
    kind: 'RESERVED_EVENT' | 'CUSTOM_EVENT';
    onCanvas: boolean;
}

export interface EventArchitecturePaletteGroup {
    category: string;
    entries: EventArchitecturePaletteEntry[];
}

export function filterEventArchitecturePalette(entries: EventArchitecturePaletteEntry[], query: string): EventArchitecturePaletteEntry[] {
    const normalized = query.trim().toLowerCase();
    if (normalized === '') return [...entries];
    return entries.filter(item => [item.label, item.description, item.category]
        .some(value => value.toLowerCase().includes(normalized)));
}

export function groupEventArchitecturePalette(entries: EventArchitecturePaletteEntry[]): EventArchitecturePaletteGroup[] {
    const grouped = new Map<string, EventArchitecturePaletteEntry[]>();
    const ordered = [...entries].sort((left, right) => left.category.localeCompare(right.category)
        || left.label.localeCompare(right.label));
    for (const entry of ordered) grouped.set(entry.category, [...(grouped.get(entry.category) ?? []), entry]);
    return [...grouped.entries()].map(([category, values]) => ({ category, entries: values }));
}
