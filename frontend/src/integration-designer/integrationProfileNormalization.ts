import type { IntegrationProfileModel } from './types';

/** Valida que un perfil de integración cumpla el contrato interno actual sin migrarlo; solo completa como vacías las colecciones y los textos que el servidor omite al serializar. */
export function normalizeIntegrationProfile(input: IntegrationProfileModel): IntegrationProfileModel {
    if (input === null || typeof input !== 'object') throw new Error('Integration profile must be a JSON object.');
    if (input.schemaVersion !== '1.0') throw new Error(`Unsupported integration profile schema: ${String(input.schemaVersion)}.`);
    requireText(input.id, 'id'); if(typeof input.metamodelId!=='string') throw new Error('Integration profile metamodelId must be a string.'); requireText(input.semanticScopeId || input.metamodelId, 'semanticScopeId'); requireText(input.name, 'name');
    if(!input.semanticScopeId) input.semanticScopeId=input.metamodelId;
    if (input.target === null || typeof input.target !== 'object') throw new Error('Integration profile target is required.');
    // El servidor omite las colecciones vacías al serializar (NON_EMPTY): una colección ausente equivale a vacía; una presente que no sea lista sigue siendo un error.
    const collections = input as unknown as Record<string, unknown>;
    for (const key of ['features', 'artifacts', 'mappings', 'requiredCapabilities']) if (collections[key] === undefined) collections[key] = [];
    if (!Array.isArray(input.features) || !Array.isArray(input.artifacts) || !Array.isArray(input.mappings) || !Array.isArray(input.requiredCapabilities))
        throw new Error('Integration profile collections are incomplete.');
    if (input.injectionPolicy === null || typeof input.injectionPolicy !== 'object') throw new Error('Integration profile injectionPolicy is required.');
    if (input.annotations === undefined) input.annotations = {};
    if (input.annotations === null || typeof input.annotations !== 'object' || Array.isArray(input.annotations)) throw new Error('Integration profile annotations must be an object.');
    fillOmittedTexts(input);
    return input;
}

/** Los textos vacíos tampoco viajan en la respuesta del servidor: se restauran como cadena vacía para que la interfaz pueda leerlos. */
function fillOmittedTexts(profile: IntegrationProfileModel): void {
    const fill = (object: object, keys: string[]): void => { const record = object as Record<string, unknown>; for (const key of keys) if (record[key] === undefined || record[key] === null) record[key] = ''; };
    const fillLists = (object: object, keys: string[]): void => { const record = object as Record<string, unknown>; for (const key of keys) if (record[key] === undefined || record[key] === null) record[key] = []; };
    const fillMaps = (object: object, keys: string[]): void => { const record = object as Record<string, unknown>; for (const key of keys) if (record[key] === undefined || record[key] === null) record[key] = {}; };
    fill(profile.target, ['technologyVersion', 'projectId', 'moduleName', 'endpoint']);
    fillLists(profile.target, ['sourceRoots', 'configurationFiles']);
    fillMaps(profile.target, ['settings']);
    fill(profile.injectionPolicy, ['generatedRoot', 'moduleName']);
    profile.artifacts.forEach((artifact) => fill(artifact, ['documentId']));
    profile.mappings.forEach((mapping) => {
        fill(mapping, ['id', 'canonicalDocumentId', 'canonicalElementId']);
        fillMaps(mapping, ['annotations']);
        const host = ((mapping as unknown as Record<string, unknown>).hostReference ??= {}) as object;
        fill(host, ['documentUri', 'namespace', 'qualifiedName', 'externalId', 'featureName', 'path']);
        fillMaps(host, ['attributes']);
    });
}

function requireText(value: string, field: string): void {
    if (typeof value !== 'string' || value.trim() === '') throw new Error(`Integration profile ${field} is required.`);
}
