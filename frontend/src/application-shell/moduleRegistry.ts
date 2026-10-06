import type { HydraModuleDefinition, HydraModuleId } from './types';

/**
 * The registry follows the path a DSL author normally takes: define the domain,
 * define how it is edited, add constraints and repair behaviour, then runtime
 * behaviour. Usage, interchange/delivery and operational utilities come after
 * the language definition instead of being mixed with it.
 */
export const HYDRA_MODULES: readonly HydraModuleDefinition[] = [
    { id: 'HOME', title: 'Home', shortTitle: 'Home', description: 'Choose a capability, continue a project or review its readiness.', category: 'START', requiresMetamodel: false, accent: 'home' },

    { id: 'METAMODEL', title: 'Metamodel designer', shortTitle: '1. Metamodel', description: 'Define the DSL concepts, attributes, relations, operations and inheritance that form its semantic contract.', category: 'DSL', requiresMetamodel: false, accent: 'metamodel' },
    { id: 'GRAMMARS', requirements: {}, title: 'Grammar designer', shortTitle: '2. Grammars', description: 'Create, import, analyze and compile Xtext, Langium or ANTLR grammars through the neutral HYDRA semantic contract.', category: 'DSL', requiresMetamodel: false, accent: 'grammars' },
    { id: 'EDITORS', requirements: { allOf: ['SEMANTIC_CONTRACT'] }, title: 'Model editor definitions', shortTitle: '3. Editor & representation', description: 'Define graphical representations, palettes, forms, mappings, containers and the generated editor runtime.', category: 'DSL', requiresMetamodel: true, accent: 'editors' },
    { id: 'VALIDATIONS', requirements: { allOf: ['SEMANTIC_CONTRACT'] }, title: 'Validation designer', shortTitle: '4. Validations', description: 'Define constraints, diagnostics and reusable expressions that keep models consistent.', category: 'DSL', requiresMetamodel: true, accent: 'validation' },
    { id: 'QUICK_FIXES', requirements: { allOf: ['SEMANTIC_CONTRACT'] }, title: 'Quick-fix designer', shortTitle: '5. Quick fixes', description: 'Define safe, declarative and reversible repairs that users can apply to validation problems.', category: 'DSL', requiresMetamodel: true, accent: 'validation' },
    { id: 'EVENTS', title: 'Event architecture', shortTitle: '6. Events', description: 'Define event contracts, payloads, bindings and their visual architecture.', category: 'DSL', requiresMetamodel: true, accent: 'events' },
    { id: 'EVENT_FLOWS', title: 'EventFlow designer', shortTitle: '7. EventFlow', description: 'Compose event-driven processes, transitions and reactions over the DSL event catalogue.', category: 'DSL', requiresMetamodel: true, accent: 'flow' },
    { id: 'GAMIFICATION', title: 'Gamification designer', shortTitle: '8. Gamification', description: 'Add evidence, points, penalties, levels and rewards on top of verified DSL events.', category: 'DSL', requiresMetamodel: true, accent: 'gamification' },
    { id: 'TRANSFORMATIONS', title: 'Model transformation designer', shortTitle: '9. Transformations', description: 'Define portable M2M transformations by connecting source and target metamodel elements, conditions and property mappings.', category: 'DSL', requiresMetamodel: false, accent: 'transformations' },

    { id: 'TEXTUAL_MODELS', requirements: { allOf: ['SEMANTIC_CONTRACT', 'COMPILED_GRAMMAR'] }, title: 'Textual model editor', shortTitle: 'Textual models', description: 'Create textual models against an activated grammar snapshot while preserving the last valid semantic model.', category: 'USE', requiresMetamodel: false, accent: 'grammars' },
    { id: 'MODEL', requirements: { allOf: ['SEMANTIC_CONTRACT', 'EDITOR_DEFINITION'] }, title: 'Model editor', shortTitle: 'Models', description: 'Create and edit model instances with the selected metamodel, notation, validations and editor definition.', category: 'USE', requiresMetamodel: true, accent: 'model' },
    { id: 'INTEGRATIONS', title: 'Technology integrations', shortTitle: 'Integrations', description: 'Map HYDRA capabilities to external technologies through explicit, reversible integration profiles.', category: 'USE', requiresMetamodel: true, accent: 'integrations' },
    { id: 'REPOSITORIES', title: 'External repositories', shortTitle: 'External repositories', description: 'Prepare secure Git, GitHub, GitLab, OneDrive, Google Drive and Dropbox connections for import, synchronization and delivery.', category: 'USE', requiresMetamodel: false, accent: 'repositories' },
    { id: 'ROUND_TRIP', title: 'Generated project synchronization', shortTitle: 'Synchronize project', description: 'Compare HYDRA with a generated project, choose what to keep and synchronize safely.', category: 'USE', requiresMetamodel: true, accent: 'roundtrip' },

    { id: 'REPRESENTABLE_IMPORTS', title: 'Representable imports', shortTitle: 'Imports', description: 'Reconstruct supported external changes and diagnose constructs that cannot be represented neutrally.', category: 'DELIVERY', requiresMetamodel: true, accent: 'representable-imports' },
    { id: 'UPDATE_SITE_IMPORTS', title: 'Eclipse update site recovery', shortTitle: 'Update site', description: 'Audit p2 repositories, recover their DSL layers and preserve evidence for every imported artifact.', category: 'DELIVERY', requiresMetamodel: false, accent: 'update-site-imports' },
    { id: 'DISTRIBUTION', title: 'Standalone distribution center', shortTitle: 'Distribution', description: 'Prepare, validate and package independent PWA or Tauri editor distributions.', category: 'DELIVERY', requiresMetamodel: false, accent: 'distribution' },
    { id: 'PUBLICATION', title: 'Publication and updates', shortTitle: 'Publication', description: 'Validate and publish distribution manifests with platform-specific update channels.', category: 'DELIVERY', requiresMetamodel: false, accent: 'publication' },

    { id: 'CAPABILITIES', title: 'Project preparation', shortTitle: 'Project status', description: 'Review which parts of the project are ready and what still needs attention.', category: 'OPERATIONS', requiresMetamodel: true, accent: 'capabilities' },
    { id: 'COMPATIBILITY', title: 'Available tools', shortTitle: 'Available tools', description: 'Check that the external tools needed by the project are installed and working correctly.', category: 'OPERATIONS', requiresMetamodel: false, accent: 'compatibility' },
    { id: 'GENERATED_PROJECTS', title: 'Generated projects', shortTitle: 'Generated projects', description: 'Create, check, locate and open the projects generated from HYDRA.', category: 'OPERATIONS', requiresMetamodel: false, accent: 'generated-projects' },
    { id: 'ARTIFACTS', title: 'Generated files and history', shortTitle: 'Generated files', description: 'Review generated files, their origin, dependencies and synchronization status.', category: 'OPERATIONS', requiresMetamodel: false, accent: 'artifacts' }
] as const;

export interface HydraRequirementState {
    available: ReadonlySet<import('./types').HydraCapabilityRequirement>;
}

export function unmetModuleRequirements(module: HydraModuleDefinition, state: HydraRequirementState): readonly import('./types').HydraCapabilityRequirement[] {
    const requirements = module.requirements;
    if (!requirements) return module.requiresMetamodel && !state.available.has('METAMODEL') ? ['METAMODEL'] : [];
    const missingAll = (requirements.allOf ?? []).filter(item => !state.available.has(item));
    const any = requirements.anyOf ?? [];
    if (any.length > 0 && !any.some(item => state.available.has(item))) return [...missingAll, ...any];
    return missingAll;
}

export function moduleDefinition(id: HydraModuleId): HydraModuleDefinition {
    return HYDRA_MODULES.find(module => module.id === id) ?? HYDRA_MODULES[0];
}

const GRAMMAR_CONTEXT_MODULES = new Set<HydraModuleId>([
    'EDITORS','VALIDATIONS','QUICK_FIXES','EVENTS','EVENT_FLOWS','GAMIFICATION','INTEGRATIONS','CAPABILITIES'
]);

/**
 * Returns whether a module can operate on a grammar semantic context without
 * forcing the user to materialize or persist an artificial metamodel first.
 */
export function moduleSupportsGrammarContext(id: HydraModuleId): boolean {
    return GRAMMAR_CONTEXT_MODULES.has(id);
}

export function moduleHash(id: HydraModuleId): string {
    return `#/${id.toLowerCase().replaceAll('_', '-')}`;
}

export function moduleFromHash(hash: string): HydraModuleId {
    const token = hash.replace(/^#\/?/, '').trim().replaceAll('-', '_').toUpperCase();
    return HYDRA_MODULES.some(module => module.id === token) ? token as HydraModuleId : 'HOME';
}
