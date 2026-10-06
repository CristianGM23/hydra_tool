const DOCUMENT_ID_PATTERN = /^[A-Za-z0-9][A-Za-z0-9._-]{0,119}$/;
const ELEMENT_ID_PATTERN = /^[A-Za-z0-9][A-Za-z0-9._:-]{0,239}$/;
const DOCUMENT_ID_MAX_LENGTH = 120;

export function isStableDocumentId(value: string): boolean {
    return DOCUMENT_ID_PATTERN.test(value);
}

export function isStableElementId(value: string): boolean {
    return ELEMENT_ID_PATTERN.test(value);
}

/**
 * Builds the private document IDs used by validation layers from the owning
 * metamodel ID without introducing characters rejected by the backend.
 * The repositories themselves are scoped by metamodelId, so truncating only
 * the derived readable prefix cannot alias storage between metamodels.
 */
export function derivedValidationDocumentId(metamodelId: string, suffix: 'actions' | 'validations'): string {
    const tail = `-${suffix}`;
    const maximumBaseLength = DOCUMENT_ID_MAX_LENGTH - tail.length;
    const base = metamodelId.slice(0, maximumBaseLength);
    return `${base}${tail}`;
}
