import { describe, expect, it, vi } from 'vitest';
import { canonicalMetaModelFixture } from '../metamodel-designer/test-fixtures/canonicalMetaModelFixture';
import type { EditorDefinitionCompilationResult } from './types';
import { buildStandaloneCapabilityPackage } from './standaloneCapabilityPackage';

vi.mock('../validation/api', () => ({ loadExpressionModel: vi.fn(async () => ({ expressionModel: { schemaVersion: '1.0', id: 'expressions', expressions: [] } })) }));
vi.mock('../validation/validationApi', () => ({ loadValidationModel: vi.fn(async () => ({ validationModel: { schemaVersion: '1.0', id: 'validations', rules: [] } })) }));
vi.mock('../validation/actionApi', () => ({ loadActionModel: vi.fn(async () => ({ actionModel: { schemaVersion: '1.0', id: 'actions', actions: [] } })) }));
vi.mock('../validation/operationBehaviorApi', () => ({ loadOperationBehaviorModel: vi.fn(async () => ({ behaviorModel: { schemaVersion: '1.0', bindings: [] } })) }));
vi.mock('../event-designer/api', () => ({ loadEventModels: vi.fn(async () => ({ definitions: { schemaVersion: '1.0', eventTypes: [] }, bindings: { schemaVersion: '1.0', bindings: [] } })) }));
vi.mock('../event-designer/flow/api', () => ({ loadEventFlows: vi.fn(async () => ({ model: { schemaVersion: '1.0', flows: [] } })) }));
vi.mock('../gamification-designer/api', () => ({ loadGamification: vi.fn(async () => ({ definitions: { schemaVersion: '1.0', pointTypes: [], levels: [], achievements: [], challenges: [], rewards: [] }, bindings: { schemaVersion: '1.0', rules: [] } })) }));

describe('standalone capability package', () => {
    it('embeds every portable runtime layer used by the generated editor', async () => {
        const metamodel = canonicalMetaModelFixture();
        const result = {
            plan: { schemaVersion: '1.0', sourceEditorDefinitionId: 'shop-editor', name: 'Shop editor' }
        } as unknown as EditorDefinitionCompilationResult;
        const built = await buildStandaloneCapabilityPackage(metamodel, result);
        const manifest = (built.capabilityPackage as { manifest: { capabilities: Array<{ kind: string }> } }).manifest;
        expect(new Set(manifest.capabilities.map(item => item.kind))).toEqual(new Set([
            'METAMODEL', 'VISUAL_REPRESENTATION', 'EXPRESSIONS', 'VALIDATIONS', 'ACTIONS', 'QUICK_FIXES',
            'OPERATIONS', 'EVENT_DEFINITIONS', 'EVENT_BINDINGS', 'EVENT_FLOW', 'GAMIFICATION'
        ]));
        expect(built.warnings).toEqual([]);
    });
    it('embeds image-backed imported notation resources so the standalone editor does not depend on an active HYDRA project', async () => {
        const metamodel = canonicalMetaModelFixture();
        const imagePath = 'hydra-assets/0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef.svg';
        const result = {
            plan: {
                schemaVersion: '1.0', sourceEditorDefinitionId: 'shop-editor', name: 'Shop editor',
                representationsById: {
                    diagram: { nodeMappingsByClassId: { 'class:Shop': [{ id: 'shop-image', imageAssetPath: imagePath }] } }
                }
            }
        } as unknown as EditorDefinitionCompilationResult;
        const svg = '<svg xmlns="http://www.w3.org/2000/svg"><circle cx="3" cy="3" r="3"/></svg>';
        vi.stubGlobal('fetch', vi.fn(async () => new Response(svg, { status: 200, headers: { 'Content-Type': 'image/svg+xml' } })));

        const built = await buildStandaloneCapabilityPackage(metamodel, result);
        const resources = (built.capabilityPackage as { resources: Record<string, string> }).resources;

        expect(resources[imagePath]).toMatch(/^data:image\/svg\+xml;base64,/);
        expect(atob(resources[imagePath].substring(resources[imagePath].indexOf(',') + 1))).toBe(svg);
        expect(built.warnings).toEqual([]);
        vi.unstubAllGlobals();
    });

});
