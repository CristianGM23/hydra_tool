import { domainSpanishCatalogA } from './domainSpanishCatalogA';
import { domainSpanishCatalogB } from './domainSpanishCatalogB';

/** Stable facade over reviewed Spanish catalogue slices. */
export const DOMAIN_SPANISH: Readonly<Record<string, string>> = {
    ...domainSpanishCatalogA,
    ...domainSpanishCatalogB,
};
