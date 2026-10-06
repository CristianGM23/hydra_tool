import { guidedSpanishCatalogA } from './guidedSpanishCatalogA';
import { guidedSpanishCatalogB } from './guidedSpanishCatalogB';

/** Stable facade over reviewed Spanish catalogue slices. */
export const GUIDED_SPANISH: Readonly<Record<string, string>> = {
    ...guidedSpanishCatalogA,
    ...guidedSpanishCatalogB,
};
