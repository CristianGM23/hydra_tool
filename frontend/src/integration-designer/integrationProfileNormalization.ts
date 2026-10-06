import type { IntegrationProfileModel } from './types';

/** Valida que un perfil de integración cumpla el contrato interno actual sin migrarlo ni completar campos ausentes. */
export function normalizeIntegrationProfile(input: IntegrationProfileModel): IntegrationProfileModel {
    if (input === null || typeof input !== 'object') throw new Error('Integration profile must be a JSON object.');
    if (input.schemaVersion !== '1.0') throw new Error(`Unsupported integration profile schema: ${String(input.schemaVersion)}.`);
    requireText(input.id, 'id'); if(typeof input.metamodelId!=='string') throw new Error('Integration profile metamodelId must be a string.'); requireText(input.semanticScopeId || input.metamodelId, 'semanticScopeId'); requireText(input.name, 'name');
    if(!input.semanticScopeId) input.semanticScopeId=input.metamodelId;
    if (input.target === null || typeof input.target !== 'object') throw new Error('Integration profile target is required.');
    if (!Array.isArray(input.features) || !Array.isArray(input.artifacts) || !Array.isArray(input.mappings) || !Array.isArray(input.requiredCapabilities))
        throw new Error('Integration profile collections are incomplete.');
    if (input.injectionPolicy === null || typeof input.injectionPolicy !== 'object') throw new Error('Integration profile injectionPolicy is required.');
    if (input.annotations === null || typeof input.annotations !== 'object' || Array.isArray(input.annotations)) throw new Error('Integration profile annotations must be an object.');
    return input;
}

function requireText(value: string, field: string): void {
    if (typeof value !== 'string' || value.trim() === '') throw new Error(`Integration profile ${field} is required.`);
}
