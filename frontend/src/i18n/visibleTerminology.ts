/**
 * Removes architecture vocabulary that is useful internally but noisy in the UI.
 * This function is presentation-only: callers never apply it to IDs, API paths,
 * persisted documents, enum values or source/code elements.
 */
export function presentVisibleTerminology(input: string, language: 'en' | 'es'): string {
    if (input.trim() === '') return input;
    if (language === 'es') return replaceSpanish(input);
    return replaceEnglish(input);
}

function replaceEnglish(input: string): string {
    return input
        .replace(/\bcanonical metamodels\b/gi, match => matchCase(match, 'metamodels'))
        .replace(/\bcanonical metamodel\b/gi, match => matchCase(match, 'metamodel'))
        .replace(/\bcanonical semantic model\b/gi, match => matchCase(match, 'metamodel structure'))
        .replace(/\bcanonical models\b/gi, match => matchCase(match, 'models'))
        .replace(/\bcanonical model\b/gi, match => matchCase(match, 'model'))
        .replace(/\bcanonical documents\b/gi, 'HYDRA documents')
        .replace(/\bcanonical document\b/gi, 'HYDRA document')
        .replace(/\bcanonical layers\b/gi, 'HYDRA layers')
        .replace(/\bcanonical layer\b/gi, 'HYDRA layer')
        .replace(/\bcanonical artifacts\b/gi, 'HYDRA artifacts')
        .replace(/\bcanonical artifact\b/gi, 'HYDRA artifact')
        .replace(/\bcanonical semantics\b/gi, 'HYDRA semantics')
        .replace(/\bcanonical (references|relationships|links|fields|properties)\b/gi, (_match, noun: string) => `HYDRA ${noun}`)
        .replace(/\bcanonical\b/gi, 'HYDRA')
        .replace(/\bHYDRA\s+HYDRA\b/g, 'HYDRA');
}

function replaceSpanish(input: string): string {
    return input
        .replace(/\bmetamodelos canónicos\b/gi, 'metamodelos')
        .replace(/\bmetamodelo canónico\b/gi, 'metamodelo')
        .replace(/\bmodelos canónicos\b/gi, 'modelos')
        .replace(/\bmodelo canónico\b/gi, 'modelo')
        .replace(/\bcapas canónicas\b/gi, 'capas de HYDRA')
        .replace(/\bcapa canónica\b/gi, 'capa de HYDRA')
        .replace(/\bdocumentos canónicos\b/gi, 'documentos de HYDRA')
        .replace(/\bdocumento canónico\b/gi, 'documento de HYDRA')
        .replace(/\bartefactos canónicos\b/gi, 'artefactos de HYDRA')
        .replace(/\bartefacto canónico\b/gi, 'artefacto de HYDRA')
        .replace(/\bsemántica canónica\b/gi, 'semántica de HYDRA')
        .replace(/\b(referencias|relaciones|propiedades) canónicas\b/gi, '$1 de HYDRA')
        .replace(/\b(referencia|relación|propiedad) canónica\b/gi, '$1 de HYDRA')
        .replace(/\bcanónic[oa]s?\b/gi, 'de HYDRA')
        .replace(/\bde HYDRA\s+de HYDRA\b/g, 'de HYDRA')
        .replace(/\bHYDRA\s+de HYDRA\b/g, 'HYDRA');
}

function matchCase(source: string, replacement: string): string {
    return /^[A-Z]/.test(source) ? replacement.charAt(0).toUpperCase() + replacement.slice(1) : replacement;
}
