import type {
    InjectionStrategy,
    IntegrationBuildSystem,
    IntegrationDeliveryMode,
    IntegrationTargetKind,
    IntegrationTechnology,
    IntegrationTechnologyDescriptor
} from './types';

const TECHNOLOGIES: readonly IntegrationTechnology[] = [
    'EMF','SIRIUS_DESKTOP','XTEXT','LANGIUM','ANTLR','BESSER_SDK','BESSER_WEB_MODELING_EDITOR','ECLIPSE_GLSP',
    'ECLIPSE_MODELING_DISTRIBUTION','VSCODE_EXTENSION','MXGRAPH_DRAWIO','PLANTUML'
];
const TARGET_KINDS: readonly IntegrationTargetKind[] = [
    'MODELING_RUNTIME','GRAPHICAL_MODELING_EDITOR','TEXTUAL_LANGUAGE_TOOLING','LOW_CODE_PLATFORM',
    'DIAGRAM_PROJECTION','IDE_DISTRIBUTION','IDE_EXTENSION'
];
const DELIVERY_ORDER: readonly IntegrationDeliveryMode[] = ['GENERATED_MODULE','EMBEDDED','HYBRID','REMOTE','PROJECTION_ONLY'];
const STRATEGY_ORDER: readonly InjectionStrategy[] = ['ADD_PACKAGE','ADD_PLUGIN','ADD_MODULE','GENERATE_SIDECAR','GENERATE_PROJECTION','PATCH_EXISTING'];
const BUILD_SYSTEMS: readonly IntegrationBuildSystem[] = ['MAVEN','GRADLE','TYCHO','NPM','PNPM','YARN','PIP','POETRY','UV','NONE','OTHER'];

function record(value: unknown): Record<string, unknown> {
    if (value === null || typeof value !== 'object' || Array.isArray(value)) throw new Error('Integration technology catalogue contains an invalid entry.');
    return value as Record<string, unknown>;
}
function strings(value: unknown): string[] {
    return Array.isArray(value) ? [...new Set(value.filter((item): item is string => typeof item === 'string' && item.trim() !== ''))] : [];
}
function known<T extends string>(value: unknown, values: readonly T[]): value is T {
    return typeof value === 'string' && values.includes(value as T);
}
function firstSupported<T extends string>(supported: readonly T[], order: readonly T[]): T | undefined {
    return order.find(item => supported.includes(item)) ?? supported[0];
}

/**
 * Normalizes technology metadata at the HTTP boundary. This keeps the guided
 * designer resilient to sparse responses from an older server without adding
 * technology-specific branches to the UI. Missing recommendations fall back
 * only to values the server actually declared as supported.
 */
export function normalizeIntegrationTechnologyDescriptor(value: unknown): IntegrationTechnologyDescriptor {
    const source = record(value);
    if (!known(source.technology, TECHNOLOGIES)) throw new Error('Integration technology catalogue contains an unsupported technology identifier.');
    if (!known(source.targetKind, TARGET_KINDS)) throw new Error(`Integration technology ${source.technology} does not declare a supported target kind.`);

    const deliveryModes = strings(source.deliveryModes).filter((item): item is IntegrationDeliveryMode => known(item, DELIVERY_ORDER));
    const injectionStrategies = strings(source.injectionStrategies).filter((item): item is InjectionStrategy => known(item, STRATEGY_ORDER));
    if (deliveryModes.length === 0) throw new Error(`Integration technology ${source.technology} does not declare any delivery mode.`);
    if (injectionStrategies.length === 0) throw new Error(`Integration technology ${source.technology} does not declare any host integration method.`);

    const fallbackDelivery = firstSupported(deliveryModes, DELIVERY_ORDER)!;
    const fallbackStrategy = firstSupported(injectionStrategies, STRATEGY_ORDER)!;
    const recommendedDeliveryMode = known(source.recommendedDeliveryMode, DELIVERY_ORDER) && deliveryModes.includes(source.recommendedDeliveryMode)
        ? source.recommendedDeliveryMode : fallbackDelivery;
    const recommendedInjectionStrategy = known(source.recommendedInjectionStrategy, STRATEGY_ORDER) && injectionStrategies.includes(source.recommendedInjectionStrategy)
        ? source.recommendedInjectionStrategy : fallbackStrategy;
    const recommendedBuildSystem = known(source.recommendedBuildSystem, BUILD_SYSTEMS) ? source.recommendedBuildSystem : 'NONE';
    const compatibilityFallback = source.recommendedDeliveryMode !== recommendedDeliveryMode
        || source.recommendedInjectionStrategy !== recommendedInjectionStrategy
        || source.recommendedBuildSystem !== recommendedBuildSystem;
    const notes = strings(source.notes);
    if (compatibilityFallback) notes.push('Some technology recommendations were not provided by the server. HYDRA selected only declared safe defaults; review the host build system before saving.');

    const capabilities = source.capabilities !== null && typeof source.capabilities === 'object' && !Array.isArray(source.capabilities)
        ? source.capabilities as IntegrationTechnologyDescriptor['capabilities'] : {};
    return {
        technology: source.technology,
        displayName: typeof source.displayName === 'string' && source.displayName.trim() !== '' ? source.displayName.trim() : source.technology,
        targetKind: source.targetKind,
        recommendedDeliveryMode,
        recommendedInjectionStrategy,
        recommendedBuildSystem,
        deliveryModes,
        injectionStrategies,
        locatorStrategies: strings(source.locatorStrategies) as IntegrationTechnologyDescriptor['locatorStrategies'],
        capabilities,
        notes
    };
}

export function normalizeIntegrationTechnologyDescriptors(value: unknown): IntegrationTechnologyDescriptor[] {
    if (!Array.isArray(value)) throw new Error('Integration technology catalogue response must be a list.');
    const normalized = value.map(normalizeIntegrationTechnologyDescriptor);
    if (normalized.length === 0) throw new Error('No integration technologies are available.');
    const seen = new Set<IntegrationTechnology>();
    return normalized.filter(descriptor => {
        if (seen.has(descriptor.technology)) return false;
        seen.add(descriptor.technology);
        return true;
    });
}
