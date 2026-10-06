/**
 * Consolidated Spanish catalogue.
 *
 * The physical slices keep this high-coverage catalogue reviewable and under
 * the architectural size guardrails while preserving the exact merge order
 * and therefore the same duplicate-key precedence as the former monolithic
 * object.
 */
import { EXTENDED_SPANISH_A } from './extendedSpanishCatalogA';
import { EXTENDED_SPANISH_B } from './extendedSpanishCatalogB';
import { EXTENDED_SPANISH_C } from './extendedSpanishCatalogC';

export const EXTENDED_SPANISH: Readonly<Record<string, string>> = Object.freeze({
    ...EXTENDED_SPANISH_A,
    ...EXTENDED_SPANISH_B,
    ...EXTENDED_SPANISH_C
});
