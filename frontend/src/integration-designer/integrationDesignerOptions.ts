import type { HostElementReference, IntegrationArtifactKind, IntegrationElementMapping, IntegrationFeature, IntegrationLocatorStrategy, IntegrationMappingKind, IntegrationProfileModel, IntegrationTechnologyDescriptor } from './types';

export const FEATURES: IntegrationFeature[] = [
    'VALIDATION_RUNTIME','QUICK_FIX_RUNTIME','OPERATION_RUNTIME','EVENT_PUBLICATION','EVENT_CONSUMPTION',
    'EVENT_FLOW_RUNTIME','GAMIFICATION_RUNTIME','GAMIFICATION_UI','EDITOR_UI_CONTRIBUTION','NOTATION_CONTRIBUTION',
    'PALETTE_CONTRIBUTION','VALIDATION_PROJECTION','EVENT_DEFINITION_PROJECTION','EVENT_FLOW_PROJECTION',
    'GAMIFICATION_PROJECTION','CODE_GENERATION','PROJECT_IMPORT','PROJECT_EXPORT'
];
export const ARTIFACT_KINDS: IntegrationArtifactKind[] = [
    'METAMODEL','GRAMMAR','TEXTUAL_EDITOR_DEFINITION','TEXTUAL_SYNTAX_STYLE','EXPRESSIONS','VALIDATIONS','ACTIONS','OPERATION_BEHAVIORS','EVENT_DEFINITIONS','EVENT_BINDINGS',
    'EVENT_FLOWS','GAMIFICATION_DEFINITIONS','GAMIFICATION_BINDINGS','EDITOR_DEFINITION','CODE_GENERATION','NOTATION'
];
export const MAPPING_KINDS: IntegrationMappingKind[] = [
    'METAMODEL','TYPE','FEATURE','RELATION','OPERATION','VALIDATION_RULE','QUICK_FIX','EVENT_TYPE','EVENT_FLOW',
    'GAMIFICATION_ELEMENT','EDITOR','REPRESENTATION','TOOL'
];
export const BUILD_SYSTEMS: IntegrationProfileModel['target']['buildSystem'][] = ['MAVEN','GRADLE','TYCHO','NPM','PNPM','YARN','PIP','POETRY','UV','NONE','OTHER'];

export const INTEGRATION_OPTION_DESCRIPTIONS: Record<string, string> = {
    MAVEN:'Java dependency and build management with Maven.', GRADLE:'JVM build and dependency management with Gradle.', TYCHO:'Eclipse plug-in and update-site builds with Tycho.',
    NPM:'Node.js package management with npm.', PNPM:'Disk-efficient Node.js package management with pnpm.', YARN:'Node.js package management with Yarn.',
    PIP:'Python packages installed with pip.', POETRY:'Python dependencies and packaging managed by Poetry.', UV:'Fast Python project and package management with uv.',
    NONE:'No host build system is required.', OTHER:'A build system not represented by the predefined choices.',
    METAMODEL:'Main DSL structure.', GRAMMAR:'Grammar definition and its neutral textual-editor personalization.', TEXTUAL_EDITOR_DEFINITION:'Textual editor runtime definition and linked neutral capabilities.', TEXTUAL_SYNTAX_STYLE:'Reusable textual syntax-style document.', EXPRESSIONS:'Reusable neutral expressions.', VALIDATIONS:'Validation rule definitions.', ACTIONS:'Quick-fix and command actions.',
    OPERATION_BEHAVIORS:'Operation implementations.', EVENT_DEFINITIONS:'Event type catalog.', EVENT_BINDINGS:'Derivations between events.', EVENT_FLOWS:'Executable event-flow definitions.',
    GAMIFICATION_DEFINITIONS:'Points, levels, achievements, challenges and rewards.', GAMIFICATION_BINDINGS:'Evidence rules that connect events to gamification.',
    EDITOR_DEFINITION:'Configurable model-editor definition.', CODE_GENERATION:'Model-to-code generation configuration and target catalog.', NOTATION:'Reusable visual notation.',
    TYPE:'A class, interface, enum or datatype.', FEATURE:'An attribute or structural feature.', RELATION:'A model relation.', OPERATION:'A metamodel operation.',
    VALIDATION_RULE:'A validation rule.', QUICK_FIX:'A repair action.', EVENT_TYPE:'An event type.', EVENT_FLOW:'An event flow.', GAMIFICATION_ELEMENT:'A gamification definition or rule.',
    EDITOR:'A configurable model editor.', REPRESENTATION:'A visual representation.', TOOL:'A palette or editor tool.'
};

export function readableIntegrationValue(value: string): string {
    return value.toLowerCase().split('_').map(part => part.charAt(0).toUpperCase() + part.slice(1)).join(' ');
}

export function integrationResolutionStatusLabel(value: string): string {
    if (value === 'RESOLVED') return 'Resolved';
    if (value === 'NOT_FOUND') return 'Not found';
    if (value === 'INCOMPATIBLE') return 'Incompatible';
    if (value === 'DEFERRED') return 'Deferred';
    return readableIntegrationValue(value);
}

export function integrationRelatedSectionLabel(value: string): string {
    const normalized = value.trim();
    if (normalized === 'artifacts') return 'HYDRA input documents';
    if (normalized === 'mappings') return 'Host mappings';
    if (normalized === 'target') return 'Target';
    if (normalized === 'features') return 'Capabilities';
    if (normalized === 'deliveryMode') return 'Delivery mode';
    if (normalized.startsWith('target.')) return 'Target';
    if (normalized.startsWith('injectionPolicy.')) return 'Host integration';
    if (normalized.startsWith('hostReference.')) return 'Host mapping';
    return normalized === '' ? '' : readableIntegrationValue(normalized.replaceAll('.', '_'));
}

export function buildSystemCategory(value: string): string {
    if (['MAVEN','GRADLE','TYCHO'].includes(value)) return 'JVM and Eclipse';
    if (['NPM','PNPM','YARN'].includes(value)) return 'JavaScript and TypeScript';
    if (['PIP','POETRY','UV'].includes(value)) return 'Python';
    return 'Other';
}

export function artifactCategory(value: string): string {
    if (['METAMODEL','GRAMMAR','TEXTUAL_EDITOR_DEFINITION','TEXTUAL_SYNTAX_STYLE','EXPRESSIONS','VALIDATIONS','ACTIONS','OPERATION_BEHAVIORS'].includes(value)) return 'Language and behaviour';
    if (['EVENT_DEFINITIONS','EVENT_BINDINGS','EVENT_FLOWS'].includes(value)) return 'Events and flows';
    if (value.startsWith('GAMIFICATION')) return 'Gamification';
    return 'Editors and notation';
}

export function mappingCategory(value: string): string {
    if (['METAMODEL','TYPE','FEATURE','RELATION','OPERATION'].includes(value)) return 'Metamodel elements';
    if (['VALIDATION_RULE','QUICK_FIX'].includes(value)) return 'Validation and repair';
    if (['EVENT_TYPE','EVENT_FLOW'].includes(value)) return 'Events and flows';
    if (value === 'GAMIFICATION_ELEMENT') return 'Gamification';
    return 'Editors and representations';
}

export function technologyCategory(value: string): string {
    if (['EMF','SIRIUS_DESKTOP','XTEXT','ECLIPSE_GLSP','ECLIPSE_MODELING_DISTRIBUTION'].includes(value)) return 'Eclipse modeling';
    if (value === 'LANGIUM') return 'TypeScript language tooling';
    if (value === 'ANTLR') return 'Parser and textual tooling';
    if (value === 'VSCODE_EXTENSION') return 'VS Code modeling';
    if (value === 'MXGRAPH_DRAWIO') return 'mxGraph and draw.io';
    if (value.startsWith('BESSER')) return 'BESSER';
    if (value === 'PLANTUML') return 'Documentation and projection';
    return 'Other integrations';
}



export function integrationDynamicDescription(value: string): string {
    const descriptions: Record<string, string> = {
        EMBEDDED:'Runs the integration runtime inside the host application. Deployment is simple and local, but the integration shares the host lifecycle and must remain compatible with its dependencies.',
        REMOTE:'Keeps the integration runtime in a separately deployed service. The host uses a connector or client, allowing independent runtime updates at the cost of endpoint, network and availability management.',
        HYBRID:'Keeps selected generated or UI components inside the host while delegating other runtime responsibilities to a remote service. This is flexible but requires compatible local and remote versions.',
        GENERATED_MODULE:'Creates an explicit self-contained module, package or plug-in inside the host repository. The host consumes it through its normal build system and HYDRA can regenerate or replace the module without mixing generated code into unrelated source.',
        PROJECTION_ONLY:'Generates source, configuration or model projection files without installing or executing a HYDRA runtime in the host. Use it for exchange, documentation or targets that consume generated artifacts themselves.',
        ADD_MODULE:'Adds a separate generated module so the host project stays easy to understand and rollback.',
        ADD_PLUGIN:'Adds a separate plug-in contribution without mixing generated code into existing source folders.',
        ADD_PACKAGE:'Adds a new package or module without changing existing source files.',
        GENERATE_SIDECAR:'Generates a companion module beside the host project and connects it through explicit integration points.',
        GENERATE_PROJECTION:'Generates portable projection files without modifying the host project.',
        PATCH_EXISTING:'Updates selected existing project files through a previewable and reversible patch plan.',
        ADD_FILES:'Adds generated files at safe project locations.',
        MODIFY_CONFIGURATION:'Updates build or configuration files through a previewable plan.',
        REGISTER_EXTENSION:'Registers an extension, plug-in or contribution point.',
        REPLACE_GENERATED:'Replaces only files previously generated and tracked by HYDRA.',
        QUALIFIED_NAME:'Finds the host element through its namespace-qualified name.',
        DOCUMENT_PATH:'Finds the host element through a stable document or AST path.',
        EXTERNAL_ID:'Uses a stable identifier maintained by the target technology.',
        URI_FRAGMENT:'Uses a document URI and fragment identifier.',
        BESSER_ELEMENT_ID:'Uses the stable identifier exposed by BESSER.',
        ECORE_URI:'Uses an EMF/Ecore URI or fragment.',
        SIRIUS_MAPPING_ID:'Uses a stable Sirius mapping identifier.',
        XTEXT_RULE_NAME:'Uses an Xtext grammar or validation rule name.',
        LANGIUM_AST_TYPE:'Uses a Langium AST type or grammar element.',
        GLSP_DIAGRAM_TYPE:'Uses a registered GLSP diagram type.',
        GLSP_ELEMENT_ID:'Uses a stable source-model or GModel element identifier.',
        PLANTUML_ALIAS:'Uses a PlantUML alias or qualified element name.'
    };
    return descriptions[value] ?? `Uses the ${readableIntegrationValue(value)} option supported by the selected target.`;
}
export function deliveryCategory(value: string): string {
    if (['EMBEDDED','GENERATED_MODULE'].includes(value)) return 'Local delivery';
    if (value === 'REMOTE') return 'Remote delivery';
    if (value === 'HYBRID') return 'Combined delivery';
    return 'Projection';
}
export function injectionCategory(value: string): string {
    if (value.startsWith('ADD_')) return 'Additive changes';
    if (value.includes('CONFIGURATION') || value.includes('REGISTER')) return 'Project configuration';
    return 'Managed replacement';
}
export function locatorCategory(value: string): string {
    if (['QUALIFIED_NAME','DOCUMENT_PATH','EXTERNAL_ID','URI_FRAGMENT'].includes(value)) return 'Generic locators';
    if (value.includes('BESSER')) return 'BESSER';
    if (value.includes('ECORE') || value.includes('SIRIUS') || value.includes('XTEXT') || value.includes('GLSP')) return 'Eclipse modeling';
    if (value.includes('LANGIUM')) return 'Langium';
    if (value.includes('PLANTUML')) return 'PlantUML';
    return 'Target-specific locators';
}

export function newHostReference(strategy: IntegrationLocatorStrategy): HostElementReference {
    return { strategy, documentUri:'', namespace:'', qualifiedName:'', externalId:'unresolved', featureName:'', path:'', attributes:{} };
}

export function newMapping(descriptor: IntegrationTechnologyDescriptor, index: number): IntegrationElementMapping {
    return {
        id:`mapping-${index}`,
        kind:'TYPE',
        canonicalDocumentId:'',
        canonicalElementId:'',
        hostReference:newHostReference(descriptor.locatorStrategies[0] ?? 'QUALIFIED_NAME'),
        required:true,
        annotations:{}
    };
}
export function orderedInjectionStrategies(descriptor: IntegrationTechnologyDescriptor): IntegrationTechnologyDescriptor['injectionStrategies'] {
    const rank = ['ADD_PACKAGE','ADD_PLUGIN','ADD_MODULE','GENERATE_SIDECAR','GENERATE_PROJECTION','PATCH_EXISTING'];
    const position = (value: string): number => { const index = rank.indexOf(value); return index < 0 ? rank.length : index; };
    return [...descriptor.injectionStrategies].sort((left, right) =>
        (left === descriptor.recommendedInjectionStrategy ? -1 : right === descriptor.recommendedInjectionStrategy ? 1 : 0)
        || position(left) - position(right));
}

export function orderedDeliveryModes(descriptor: IntegrationTechnologyDescriptor): IntegrationTechnologyDescriptor['deliveryModes'] {
    const rank = ['GENERATED_MODULE','EMBEDDED','HYBRID','REMOTE','PROJECTION_ONLY'];
    const position = (value: string): number => { const index = rank.indexOf(value); return index < 0 ? rank.length : index; };
    return [...descriptor.deliveryModes].sort((left, right) =>
        (left === descriptor.recommendedDeliveryMode ? -1 : right === descriptor.recommendedDeliveryMode ? 1 : 0)
        || position(left) - position(right));
}

