export type HydraOverwriteMatchKind = 'STABLE_ID' | 'UNIQUE_NAME' | 'SEMANTIC_SIGNATURE';

export interface HydraPersistedIdentity {
    layer: string;
    /** Tipo de entidad dentro de una capa compuesta (regla, flujo, evento, etc.). */
    entityKind?: string;
    stableId: string;
    uniqueName?: string;
    /** Firma semántica opcional. Se usa únicamente cuando la capa declara que dos elementos equivalen por contenido. */
    semanticSignature?: string;
    /** Firma completa del contenido para distinguir un elemento modificado de uno persistido sin cambios. */
    contentSignature?: string;
    /** Etiqueta legible que se presenta al usuario en la confirmación. */
    displayLabel?: string;
}

export interface HydraOverwriteConflict {
    current: HydraPersistedIdentity;
    persisted: HydraPersistedIdentity;
    matchedBy: HydraOverwriteMatchKind[];
}

function normalized(value: string | undefined): string {
    // Los IDs se comparan exactamente (salvo espacios accidentales) porque el backend
    // distingue mayúsculas y minúsculas. Los nombres siguen la misma política para no
    // convertir dos nombres válidos en una única identidad de forma implícita.
    return (value ?? '').trim();
}


function canonicalizeContent(value: unknown): unknown {
    if (Array.isArray(value)) return value.map(canonicalizeContent);
    if (value === null || typeof value !== 'object') return value;
    const record = value as Record<string, unknown>;
    return Object.fromEntries(Object.keys(record).sort((left, right) => left.localeCompare(right)).map(key => [key, canonicalizeContent(record[key])]));
}

/** Firma determinista del contenido usada para ignorar entidades persistidas que no han cambiado. */
export function hydraContentSignature(value: unknown): string {
    return JSON.stringify(canonicalizeContent(value));
}

function sameScope(left: HydraPersistedIdentity, right: HydraPersistedIdentity): boolean {
    return left.layer === right.layer && normalized(left.entityKind) === normalized(right.entityKind);
}

export function hydraOverwriteMatchKinds(
    current: HydraPersistedIdentity,
    persisted: HydraPersistedIdentity,
    includeSemanticSignature = false
): HydraOverwriteMatchKind[] {
    if (!sameScope(current, persisted)) return [];
    const matches: HydraOverwriteMatchKind[] = [];
    const currentId = normalized(current.stableId);
    const persistedId = normalized(persisted.stableId);
    if (currentId !== '' && persistedId !== '' && currentId === persistedId) matches.push('STABLE_ID');
    const currentName = normalized(current.uniqueName);
    const persistedName = normalized(persisted.uniqueName);
    if (currentName !== '' && persistedName !== '' && currentName === persistedName) matches.push('UNIQUE_NAME');
    if (includeSemanticSignature) {
        const currentSemantic = normalized(current.semanticSignature);
        const persistedSemantic = normalized(persisted.semanticSignature);
        if (currentSemantic !== '' && persistedSemantic !== '' && currentSemantic === persistedSemantic) matches.push('SEMANTIC_SIGNATURE');
    }
    return matches;
}

/** Determina si dos identidades representan el mismo elemento persistente. */
export function sameHydraPersistedIdentity(current: HydraPersistedIdentity, persisted: HydraPersistedIdentity): boolean {
    return hydraOverwriteMatchKinds(current, persisted, false).length > 0;
}

/**
 * Descarta de la comprobación los elementos que son exactamente iguales a su snapshot
 * persistido. De esta forma, añadir una segunda regla/flujo/evento no provoca una falsa
 * sobrescritura únicamente porque el documento contenedor ya tenga ETag.
 */
function scopeKey(identity: HydraPersistedIdentity): string {
    return `${identity.layer}\u0000${normalized(identity.entityKind)}`;
}

function indexKey(identity: HydraPersistedIdentity, value: string | undefined): string {
    return `${scopeKey(identity)}\u0000${normalized(value)}`;
}

export function changedHydraPersistedIdentities(
    current: readonly HydraPersistedIdentity[],
    persisted: readonly HydraPersistedIdentity[]
): HydraPersistedIdentity[] {
    const persistedById = new Map<string, HydraPersistedIdentity>();
    for (const item of persisted) {
        const id = normalized(item.stableId);
        if (id !== '') persistedById.set(indexKey(item, id), item);
    }
    return current.filter(candidate => {
        const id = normalized(candidate.stableId);
        const content = normalized(candidate.contentSignature);
        if (id === '' || content === '') return true;
        const previous = persistedById.get(indexKey(candidate, id));
        if (previous === undefined) return true;
        const previousContent = normalized(previous.contentSignature);
        return previousContent === '' || content !== previousContent;
    });
}

/**
 * Busca colisiones reales contra elementos que ya existen en persistencia. La comparación
 * se realiza mediante índices por ámbito para que el coste sea lineal incluso cuando una
 * capa contenga cientos o miles de elementos.
 */
export function findHydraOverwriteConflicts(
    current: readonly HydraPersistedIdentity[],
    persisted: readonly HydraPersistedIdentity[],
    includeSemanticSignature = false
): HydraOverwriteConflict[] {
    const byId = new Map<string, HydraPersistedIdentity[]>();
    const byName = new Map<string, HydraPersistedIdentity[]>();
    const bySemantic = new Map<string, HydraPersistedIdentity[]>();
    function add(index: Map<string, HydraPersistedIdentity[]>, key: string, item: HydraPersistedIdentity): void {
        const values = index.get(key);
        if (values === undefined) index.set(key, [item]);
        else values.push(item);
    }
    for (const existing of persisted) {
        const id = normalized(existing.stableId);
        const name = normalized(existing.uniqueName);
        const semantic = normalized(existing.semanticSignature);
        if (id !== '') add(byId, indexKey(existing, id), existing);
        if (name !== '') add(byName, indexKey(existing, name), existing);
        if (includeSemanticSignature && semantic !== '') add(bySemantic, indexKey(existing, semantic), existing);
    }

    const conflicts: HydraOverwriteConflict[] = [];
    for (const candidate of changedHydraPersistedIdentities(current, persisted)) {
        const matched = new Map<HydraPersistedIdentity, Set<HydraOverwriteMatchKind>>();
        const collect = (items: HydraPersistedIdentity[] | undefined, kind: HydraOverwriteMatchKind): void => {
            for (const item of items ?? []) {
                const kinds = matched.get(item) ?? new Set<HydraOverwriteMatchKind>();
                kinds.add(kind);
                matched.set(item, kinds);
            }
        };
        const id = normalized(candidate.stableId);
        const name = normalized(candidate.uniqueName);
        const semantic = normalized(candidate.semanticSignature);
        if (id !== '') collect(byId.get(indexKey(candidate, id)), 'STABLE_ID');
        if (name !== '') collect(byName.get(indexKey(candidate, name)), 'UNIQUE_NAME');
        if (includeSemanticSignature && semantic !== '') collect(bySemantic.get(indexKey(candidate, semantic)), 'SEMANTIC_SIGNATURE');
        for (const [existing, kinds] of matched) conflicts.push({ current: candidate, persisted: existing, matchedBy: [...kinds] });
    }
    return conflicts;
}

/** Compatibilidad para consumidores antiguos: solo una versión ya persistida del mismo elemento necesita confirmación. */
export function needsHydraOverwriteConfirmation(
    persistenceToken: string,
    current: HydraPersistedIdentity,
    persisted: HydraPersistedIdentity | null
): boolean {
    if (persistenceToken.trim() === '') return false;
    if (persisted === null) return false;
    return sameHydraPersistedIdentity(current, persisted);
}
