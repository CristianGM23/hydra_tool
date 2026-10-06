import type { HydraModuleId } from '../application-shell/types';

export type AggressiveProofKind = 'ARTIFACT' | 'MUTATION' | 'ROUND_TRIP' | 'RECOVERY' | 'SECURITY' | 'INVARIANT';

export interface AggressiveProcessScenario {
    moduleId: HydraModuleId;
    process: string;
    proof: AggressiveProofKind;
    finalEvidence: string;
    attacks: readonly string[];
}

/**
 * Acceptance matrix for every process exposed by HYDRA. This is intentionally
 * kept next to executable tests: adding a module without an aggressive scenario
 * must fail the campaign instead of silently reducing coverage.
 */
export const AGGRESSIVE_PROCESS_SCENARIOS: readonly AggressiveProcessScenario[] = [
    { moduleId:'HOME', process:'Project entry and capability navigation', proof:'INVARIANT', finalEvidence:'Every registered module round-trips through its hash and unknown hashes fail closed to Home.', attacks:['unknown hash','repeated navigation','missing capability'] },
    { moduleId:'METAMODEL', process:'Metamodel creation, mutation and deletion', proof:'MUTATION', finalEvidence:'Structural mutations preserve immutability and deleting a classifier removes every dangling semantic reference.', attacks:['duplicate names','high fan-out relations','cascade deletion','invalid multiplicity'] },
    { moduleId:'GRAMMARS', process:'Grammar authoring, local inspection and quick fixes', proof:'RECOVERY', finalEvidence:'Malformed or misspelled grammar text produces deterministic safe edits and preserves recoverable source structure.', attacks:['undefined references','malformed entry','missing terminator','duplicate diagnostics'] },
    { moduleId:'EDITORS', process:'Editor and representation definition', proof:'ARTIFACT', finalEvidence:'A serializable editor definition contains a valid representation/palette contract and rejects dangling tools or conditions.', attacks:['unknown representation','duplicate IDs','dangling tool','condition reuse'] },
    { moduleId:'VALIDATIONS', process:'Constraint design and validation compilation preflight', proof:'INVARIANT', finalEvidence:'Complete rule/expression documents pass while malformed quick fixes or layer identities are blocked before persistence.', attacks:['empty operation','bad layer id','missing expression','type mismatch'] },
    { moduleId:'QUICK_FIXES', process:'Safe repair definition', proof:'MUTATION', finalEvidence:'Repairs remain explicit, serializable and rejected when they cannot produce a deterministic model mutation.', attacks:['empty operation list','missing target value','dangling expression','unsafe mutation'] },
    { moduleId:'EVENTS', process:'Event contract and binding design', proof:'INVARIANT', finalEvidence:'Bindings only target known event types and every required payload field is mapped before save.', attacks:['duplicate event IDs','unknown target','missing required mapping','definition mismatch'] },
    { moduleId:'EVENT_FLOWS', process:'Event-driven flow design', proof:'ARTIFACT', finalEvidence:'Flow definitions synchronize into a stable visual diagram and node/transition identities survive repeated synchronization.', attacks:['orphan transition','duplicate node','repeated synchronization','empty flow'] },
    { moduleId:'GAMIFICATION', process:'Gamification evidence, points and rewards', proof:'INVARIANT', finalEvidence:'Only verified evidence can award points and all point/reward dependencies remain referentially valid.', attacks:['unknown point type','unknown reward','duplicate ID','unverified quick-fix evidence'] },
    { moduleId:'TRANSFORMATIONS', process:'M2M/M2T/T2M transformation design', proof:'MUTATION', finalEvidence:'Rule identity survives endpoint/kind changes and compatible attribute mappings produce deterministic canonical bindings.', attacks:['kind switching','endpoint rebinding','missing attributes','duplicate mappings'] },
    { moduleId:'TEXTUAL_MODELS', process:'Textual model authoring', proof:'RECOVERY', finalEvidence:'Completion remains bounded and context-derived even with incomplete text instead of corrupting the last valid semantic state.', attacks:['partial token','unknown reference','cursor at boundaries','large prefix'] },
    { moduleId:'MODEL', process:'Graphical model editing', proof:'MUTATION', finalEvidence:'Model/notation operations preserve stable element identity and derived views never invent semantic elements.', attacks:['unknown element','empty document','repeated projection','stale selection'] },
    { moduleId:'INTEGRATIONS', process:'Technology integration profiles', proof:'SECURITY', finalEvidence:'Profiles reject unsupported runtime/UI capabilities and mappings without a concrete host locator.', attacks:['unsupported runtime','missing host locator','unsafe injection','missing artifact'] },
    { moduleId:'REPOSITORIES', process:'External repository connections', proof:'SECURITY', finalEvidence:'Connection, export/import round-trips, stale-version conflicts and provider isolation are proven without exposing arbitrary commands or secrets.', attacks:['empty token','provider mismatch','offline backend','malformed repository path','stale remote version','concurrent transfer isolation'] },
    { moduleId:'ROUND_TRIP', process:'Generated-project synchronization', proof:'ROUND_TRIP', finalEvidence:'Preview, apply and rollback remain separate encoded operations and explicit conflict decisions survive transport.', attacks:['path metacharacters','conflicting decision','rollback id encoding','empty action set'] },
    { moduleId:'REPRESENTABLE_IMPORTS', process:'Representable external change import', proof:'ROUND_TRIP', finalEvidence:'External changes are previewed before apply and preserve technology, strategy and canonical target identity.', attacks:['unsupported construct','empty changes','partial mapping','conflicting external IDs'] },
    { moduleId:'UPDATE_SITE_IMPORTS', process:'Eclipse update-site recovery', proof:'RECOVERY', finalEvidence:'Recovery evidence and selected layers remain explicit so unsupported content cannot be silently materialized.', attacks:['missing metadata','partial p2 repository','unknown extension','layer conflicts'] },
    { moduleId:'DISTRIBUTION', process:'Standalone PWA/Tauri distribution', proof:'ARTIFACT', finalEvidence:'Plan/materialization/package stages produce a non-empty package with a server filename and SHA-256 evidence.', attacks:['missing entry point','invalid package','transient directories','repeat packaging'] },
    { moduleId:'PUBLICATION', process:'Distribution publication and updates', proof:'SECURITY', finalEvidence:'Publication sends artifact metadata and checksums without serializing signing secrets into the request.', attacks:['invalid channel','missing artifact','secret-like metadata','version rollback'] },
    { moduleId:'CAPABILITIES', process:'Project readiness and capability transactions', proof:'MUTATION', finalEvidence:'Readiness is context-scoped and layer transactions separate preview, apply and rollback.', attacks:['grammar/metamodel collision','missing layer','rollback after partial apply','encoded context id'] },
    { moduleId:'COMPATIBILITY', process:'Native tool compatibility checks', proof:'SECURITY', finalEvidence:'Compatibility execution transports catalog keys only and cannot inject arbitrary commands.', attacks:['unknown key','empty selection','command-shaped key','missing latest matrix'] },
    { moduleId:'GENERATED_PROJECTS', process:'Generated-project lifecycle', proof:'ARTIFACT', finalEvidence:'Lifecycle execution is restricted to declared stages and never accepts arbitrary host commands.', attacks:['invalid stage','dry-run','repeated validation','hostile project id'] },
    { moduleId:'ARTIFACTS', process:'Artifact registry and provenance', proof:'ARTIFACT', finalEvidence:'Scanning remains server-owned and the resulting registry/graph identifies produced artifacts and dependencies.', attacks:['empty registry','repeated scan','missing dependency','stale provenance'] }
] as const;
