import type { IntegrationArtifactResolution, IntegrationDiagnostic } from './types';

export interface IntegrationResolutionAdvice {
    cause: string;
    action: string;
    optionalAction?: string;
}


/**
 * Localiza los diagnósticos cuyo mensaje backend contiene valores dinámicos.
 * El código estable es el contrato; el texto backend se conserva para inglés y
 * para códigos desconocidos, evitando depender de traducir frases concatenadas.
 */
export function integrationDiagnosticDisplayMessage(diagnostic: IntegrationDiagnostic, language: 'en' | 'es'): string {
    if (language === 'en') return diagnostic.message;
    const spanishByCode: Readonly<Record<string, string>> = {
        TARGET_KIND_MISMATCH: 'El tipo de destino no coincide con el requerido por la tecnología seleccionada.',
        UNSUPPORTED_DELIVERY_MODE: 'La tecnología seleccionada no admite el modo de entrega configurado.',
        UNSUPPORTED_INJECTION_STRATEGY: 'La tecnología seleccionada no admite la estrategia de inyección configurada.',
        UNSUPPORTED_CAPABILITY: 'La tecnología seleccionada no admite una de las capacidades requeridas.',
        PARTIAL_CAPABILITY: 'Una capacidad requiere código adaptador generado o específico de la versión del destino.',
        EXTERNAL_CAPABILITY: 'Una capacidad debe proporcionarla un runtime externo del destino.',
        PLANTUML_PROJECTION_ONLY: 'PlantUML solo puede utilizarse como destino de proyección.',
        PLANTUML_RUNTIME_FORBIDDEN: 'PlantUML no puede ejecutar capacidades de runtime.',
        BESSER_SDK_UI_TARGET: 'Las contribuciones de interfaz de BESSER requieren BESSER Web Modeling Editor.',
        BESSER_WME_EMBEDDED_UNSUPPORTED: 'El editor web de BESSER requiere un módulo generado, un runtime remoto o un modo híbrido.',
        MISSING_BUILD_SYSTEM: 'Los módulos generados deberían declarar el sistema de compilación del proyecto anfitrión.',
        PLANTUML_BUILD_OPTIONAL: 'Las proyecciones PlantUML no necesitan un sistema de compilación salvo que formen parte de un proyecto mayor.',
        DUPLICATE_ARTIFACT: 'El mismo documento de entrada de HYDRA se ha seleccionado más de una vez.',
        MISSING_METAMODEL_ARTIFACT: 'Todo perfil de integración debe incluir el metamodelo propietario.',
        MISSING_SEMANTIC_ROOT_ARTIFACT: 'Todo perfil de integración debe incluir una raíz semántica: metamodelo o gramática.',
        DUPLICATE_MAPPING: 'Hay un ID de mapping duplicado.',
        UNSUPPORTED_LOCATOR: 'La tecnología seleccionada no admite la estrategia usada para localizar este elemento del anfitrión.',
        PROFILE_SELF_MAPPING: 'El mapping apunta al propio perfil de integración; normalmente debe apuntar a un documento semántico o de comportamiento.',
        NO_RUNTIME_MAPPINGS: 'La integración en runtime está habilitada, pero no se han definido mappings con elementos del anfitrión.',
        MISSING_FEATURE_ARTIFACT: 'Una capacidad seleccionada requiere un documento de HYDRA que todavía no está incluido entre las entradas.',
        PREVIEW_REQUIRED: 'La vista previa de inyección es obligatoria y no puede deshabilitarse.',
        ROLLBACK_REQUIRED: 'La integración debe conservar un plan de reversión.',
        CUSTOM_CODE_NOT_PRESERVED: 'La configuración actual no garantiza la conservación del código personalizado del usuario.',
        MODIFIES_GENERATED_FILES: 'Modificar archivos generados por el anfitrión aumenta el riesgo de regeneración.',
        PATCH_EXISTING_RISK: 'Modificar archivos existentes es menos reversible que añadir un módulo separado.',
        UNRESOLVED_REQUIRED_ARTIFACT: diagnostic.message,
        UNRESOLVED_OPTIONAL_ARTIFACT: diagnostic.message,
        DEFERRED_ARTIFACT: diagnostic.message,
        TARGET_NOTE: diagnostic.message
    };
    return spanishByCode[diagnostic.code] ?? diagnostic.message;
}

export function integrationDiagnosticAdvice(diagnostic: IntegrationDiagnostic): IntegrationResolutionAdvice {
    switch (diagnostic.code) {
        case 'UNRESOLVED_REQUIRED_ARTIFACT':
            return {
                cause: 'The profile marks this HYDRA artifact as required, but HYDRA cannot find a saved document with that ID for the selected metamodel.',
                action: requiredArtifactAction(diagnostic.elementId, diagnostic.message),
                optionalAction: 'When the target does not need this capability, remove the artifact from HYDRA artifacts or disable the feature that requires it.'
            };
        case 'NO_RUNTIME_MAPPINGS':
            return {
                cause: 'At least one runtime feature is enabled, but the profile does not explain how HYDRA elements correspond to host-tool elements.',
                action: 'Open Defined structure → Host mappings, create the required type/feature/event mappings, and run Check again.',
                optionalAction: 'For a generation-only or projection-only integration, disable runtime features instead of adding host mappings.'
            };
        case 'TARGET_NOTE':
            return {
                cause: diagnostic.message,
                action: 'No correction is required for this information message. Confirm that the selected target matches the intended output.',
                optionalAction: targetAlternative(diagnostic.message)
            };
        case 'MISSING_FEATURE_ARTIFACT':
            return {
                cause: 'An enabled feature depends on a HYDRA document that is not selected in the profile.',
                action: 'Add the required document under HYDRA artifacts and save that document in its HYDRA designer before checking again.',
                optionalAction: 'Disable the feature when the target must not export or execute that capability.'
            };
        case 'MISSING_METAMODEL_ARTIFACT':
        case 'MISSING_SEMANTIC_ROOT_ARTIFACT':
            return {
                cause: 'Every integration needs a canonical semantic root: either the owning MetaModel 2.1 document or a grammar-first definition.',
                action: 'Add the current metamodel or the grammar-first document under HYDRA artifacts and mark that semantic root as required.'
            };
        default:
            return {
                cause: diagnostic.message,
                action: diagnostic.path === '' ? 'Review the profile fields related to this diagnostic and run Check again.' : `Open the ${diagnostic.path} section, correct the referenced value and run Check again.`
            };
    }
}

export function artifactResolutionAdvice(resolution: IntegrationArtifactResolution): IntegrationResolutionAdvice {
    if (resolution.status === 'RESOLVED') {
        return { cause: 'HYDRA found the HYDRA document and can include it in the generated package.', action: 'No action is required.' };
    }
    if (resolution.status === 'NOT_FOUND') {
        return {
            cause: resolution.message,
            action: requiredArtifactAction(resolution.artifact.documentId, resolution.message),
            optionalAction: resolution.artifact.required ? 'Remove the artifact or mark it optional only when the selected target genuinely does not need it.' : 'The artifact is optional, so it may also be removed from the profile.'
        };
    }
    if (resolution.status === 'INCOMPATIBLE') {
        return {
            cause: resolution.message,
            action: 'Choose a compatible target technology or remove the unsupported artifact/feature before compiling the integration.'
        };
    }
    return {
        cause: resolution.message,
        action: 'Save the dependency and rerun Check before compiling or injecting the generated project.'
    };
}

function requiredArtifactAction(documentId: string, message: string): string {
    const normalized = `${documentId} ${message}`.toLowerCase();
    if (normalized.includes('event')) return 'Open Event Designer for this metamodel, create or load the event definition, save it, then return to Integrations and run Check again.';
    if (normalized.includes('gamification')) return 'Open Gamification Designer for this metamodel, create and save its definitions, then return to Integrations and run Check again.';
    if (normalized.includes('validation')) return 'Open Validation Designer for this metamodel, create and save the validation document, then return to Integrations and run Check again.';
    if (normalized.includes('editor')) return 'Open Model Editor Designer, save the editor definition, then return to Integrations and run Check again.';
    return 'Create or save the missing HYDRA document for the same metamodel, then reload this profile and run Check again.';
}

function targetAlternative(message: string): string | undefined {
    return message.toLowerCase().includes('web-editor target')
        ? 'Choose BESSER Web Modeling Editor when the integration must contribute editor UI, notation or palette elements.'
        : undefined;
}
