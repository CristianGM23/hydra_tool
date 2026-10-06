/** Construye un índice inmutable por clave para evitar búsquedas lineales repetidas en rutas de render. */
export function indexBy<T, K extends string | number>(values: readonly T[], key: (value: T) => K): ReadonlyMap<K, T> {
    return new Map(values.map(value => [key(value), value] as const));
}
