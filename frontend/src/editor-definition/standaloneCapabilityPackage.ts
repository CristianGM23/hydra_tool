import type { MetaModel } from '../metamodel-designer/types';
import type { EditorDefinitionCompilationResult } from './types';
import { loadExpressionModel } from '../validation/api';
import { loadValidationModel } from '../validation/validationApi';
import { loadActionModel } from '../validation/actionApi';
import { loadOperationBehaviorModel } from '../validation/operationBehaviorApi';
import { loadEventModels } from '../event-designer/api';
import { loadEventFlows } from '../event-designer/flow/api';
import { loadGamification } from '../gamification-designer/api';
import { loadEditorVisualAssetResource } from './editorVisualAssetUrl';

interface CapabilitySelectionDocument {
    id: string;
    kind: string;
    documentId: string;
    schemaVersion: string;
    required: boolean;
    compiled: boolean;
    annotations: Record<string, string>;
}

export interface StandaloneCapabilityBuildResult {
    capabilityPackage: Record<string, unknown>;
    warnings: string[];
    includedCapabilities: string[];
}

/**
 * Builds the portable package consumed by the generated editor. Optional runtime layers are
 * recovered independently so one missing catalogue never prevents exporting the editor itself.
 */
export async function buildStandaloneCapabilityPackage(
    metamodel: MetaModel,
    result: EditorDefinitionCompilationResult
): Promise<StandaloneCapabilityBuildResult> {
    const metamodelDocumentId = `metamodels/${metamodel.id}.metamodel.json`;
    const editorDocumentId = `editors/${result.plan.sourceEditorDefinitionId}.executable-editor-plan.json`;
    const canonicalDocuments: Record<string, unknown> = {
        [metamodelDocumentId]: metamodel,
        [editorDocumentId]: result.plan
    };
    const capabilities: CapabilitySelectionDocument[] = [
        capability('metamodel', 'METAMODEL', metamodelDocumentId, metamodel.schemaVersion || '2.1', true, false),
        capability('editor-runtime', 'VISUAL_REPRESENTATION', editorDocumentId, result.plan.schemaVersion, true, true)
    ];
    const dependencies: Array<Record<string, unknown>> = [dependency('editor-runtime', 'metamodel', true, `>=${metamodel.schemaVersion || '2.1'}`, 'The executable editor resolves stable metamodel identifiers.')];
    const warnings: string[] = [];

    const recovered = await Promise.all([
        optional('expressions', () => loadExpressionModel(metamodel.id).then(value => value.expressionModel)),
        optional('validations', () => loadValidationModel(metamodel.id).then(value => value.validationModel)),
        optional('actions', () => loadActionModel(metamodel.id).then(value => value.actionModel)),
        optional('operations', () => loadOperationBehaviorModel(metamodel.id).then(value => value.behaviorModel)),
        optional('events', () => loadEventModels(metamodel.id)),
        optional('event-flow', () => loadEventFlows(metamodel.id).then(value => value.model)),
        optional('gamification', () => loadGamification(metamodel.id))
    ]);

    const expressions = recovered[0];
    if (expressions.value !== undefined) add('expressions', 'EXPRESSIONS', `expressions/${metamodel.id}.expressions.json`, expressions.value, capabilities, canonicalDocuments, dependencies, ['metamodel']);
    else warnings.push(expressions.warning);

    const validations = recovered[1];
    if (validations.value !== undefined) add('validations', 'VALIDATIONS', `validations/${metamodel.id}.validations.json`, validations.value, capabilities, canonicalDocuments, dependencies, expressions.value === undefined ? ['metamodel'] : ['metamodel', 'expressions']);
    else warnings.push(validations.warning);

    const actions = recovered[2];
    if (actions.value !== undefined) {
        const path = `actions/${metamodel.id}.actions.json`;
        add('actions', 'ACTIONS', path, actions.value, capabilities, canonicalDocuments, dependencies, expressions.value === undefined ? ['metamodel'] : ['metamodel', 'expressions']);
        capabilities.push(capability('quick-fixes', 'QUICK_FIXES', path, schemaVersion(actions.value), false, false));
        dependencies.push(dependency('quick-fixes', 'actions', true, `>=${schemaVersion(actions.value)}`, 'Quick fixes are portable declarative actions.'));
    } else warnings.push(actions.warning);

    const operations = recovered[3];
    if (operations.value !== undefined) add('operations', 'OPERATIONS', `operations/${metamodel.id}.operation-behaviors.json`, operations.value, capabilities, canonicalDocuments, dependencies, ['metamodel']);
    else warnings.push(operations.warning);

    const events = recovered[4];
    if (events.value !== undefined && isRecord(events.value)) {
        const definitionPath = `events/${metamodel.id}.event-definitions.json`;
        const bindingPath = `events/${metamodel.id}.event-bindings.json`;
        const definitions = events.value.definitions;
        const bindings = events.value.bindings;
        if (definitions !== undefined) add('event-definitions', 'EVENT_DEFINITIONS', definitionPath, definitions, capabilities, canonicalDocuments, dependencies, ['metamodel']);
        if (bindings !== undefined) add('event-bindings', 'EVENT_BINDINGS', bindingPath, bindings, capabilities, canonicalDocuments, dependencies, expressions.value === undefined ? ['event-definitions'] : ['event-definitions', 'expressions']);
    } else warnings.push(events.warning);

    const eventFlow = recovered[5];
    if (eventFlow.value !== undefined) add('event-flow', 'EVENT_FLOW', `event-flows/${metamodel.id}.event-flows.json`, eventFlow.value, capabilities, canonicalDocuments, dependencies, capabilities.some(item => item.id === 'event-definitions') ? ['event-definitions'] : ['metamodel']);
    else warnings.push(eventFlow.warning);

    const gamification = recovered[6];
    if (gamification.value !== undefined && isRecord(gamification.value)) {
        const definitions = gamification.value.definitions;
        const bindings = gamification.value.bindings;
        if (definitions !== undefined) add('gamification-definitions', 'GAMIFICATION', `gamification/${metamodel.id}.gamification-definitions.json`, definitions, capabilities, canonicalDocuments, dependencies, ['metamodel']);
        if (bindings !== undefined) add('gamification-bindings', 'GAMIFICATION', `gamification/${metamodel.id}.gamification-bindings.json`, bindings, capabilities, canonicalDocuments, dependencies, capabilities.some(item => item.id === 'event-definitions') ? ['gamification-definitions', 'event-definitions'] : ['gamification-definitions']);
    } else warnings.push(gamification.warning);

    const resources: Record<string, string> = {};
    let resourceBytes = 0;
    const maximumPortableVisualBytes = 12 * 1024 * 1024;
    for (const path of collectImageAssetPaths(result)) {
        try {
            const encoded = await loadEditorVisualAssetResource(path);
            resourceBytes += encoded.length;
            if (resourceBytes > maximumPortableVisualBytes) throw new Error('visual assets exceed the portable package budget');
            resources[path] = encoded;
        } catch (error) {
            warnings.push(`visual asset ${path}: ${error instanceof Error ? error.message : 'could not be embedded'}`);
        }
    }

    return {
        capabilityPackage: {
            manifest: {
                schemaVersion: '1.0',
                id: `${result.plan.sourceEditorDefinitionId}-standalone`,
                name: `${result.plan.name} standalone package`,
                packageVersion: '1.0.0',
                metamodelId: metamodel.id,
                metamodelOrigin: { kind: 'OWNED', technology: 'HYDRA', sourceUri: '', sourceRevision: '', sourceChecksum: '', importedAt: '', synchronizedAt: '', elementMappings: {}, annotations: {} },
                capabilities,
                dependencies,
                compatibility: [],
                checksums: {},
                migrations: [],
                annotations: { 'hydra:generatedFrom': 'editor-runtime-preview', 'hydra:portableRuntime': 'true' }
            },
            canonicalDocuments,
            resources
        },
        warnings: warnings.filter(value => value !== ''),
        includedCapabilities: capabilities.map(item => item.kind)
    };
}

function add(
    id: string,
    kind: string,
    documentId: string,
    document: unknown,
    capabilities: CapabilitySelectionDocument[],
    documents: Record<string, unknown>,
    dependencies: Array<Record<string, unknown>>,
    dependencyIds: string[]
): void {
    documents[documentId] = document;
    capabilities.push(capability(id, kind, documentId, schemaVersion(document), false, false));
    for (const dependencyId of dependencyIds) if (capabilities.some(item => item.id === dependencyId)) dependencies.push(dependency(id, dependencyId, false, '>=1.0', 'Portable runtime dependency.'));
}

function capability(id: string, kind: string, documentId: string, version: string, required: boolean, compiled: boolean): CapabilitySelectionDocument {
    return { id, kind, documentId, schemaVersion: version, required, compiled, annotations: {} };
}

function dependency(sourceCapabilityId: string, targetCapabilityId: string, required: boolean, versionRange: string, reason: string): Record<string, unknown> {
    return { sourceCapabilityId, targetCapabilityId, required, versionRange, reason };
}

async function optional<T>(name: string, loader: () => Promise<T>): Promise<{ value?: T; warning: string }> {
    try { return { value: await loader(), warning: '' }; }
    catch (error) { return { warning: `${name}: ${error instanceof Error ? error.message : 'layer is not available'}` }; }
}

function collectImageAssetPaths(result: EditorDefinitionCompilationResult): string[] {
    const paths = new Set<string>();
    for (const representation of Object.values(result.plan.representationsById ?? {})) {
        for (const mappings of Object.values(representation.nodeMappingsByClassId ?? {})) {
            for (const mapping of mappings) {
                const path = (mapping.imageAssetPath ?? '').trim();
                if (path !== '') paths.add(path);
            }
        }
    }
    return [...paths].sort((left, right) => left.localeCompare(right));
}

function schemaVersion(value: unknown): string {
    return isRecord(value) && typeof value.schemaVersion === 'string' && value.schemaVersion !== '' ? value.schemaVersion : '1.0';
}

function isRecord(value: unknown): value is Record<string, unknown> {
    return typeof value === 'object' && value !== null && !Array.isArray(value);
}
