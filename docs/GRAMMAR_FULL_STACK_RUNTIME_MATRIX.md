# HYDRA R5C13 — Grammar-first full-stack runtime matrix

HYDRA treats `grammar.<grammarId>` as a stable semantic context. The transient class projection is resolved in memory; no artificial canonical metamodel is persisted.

| Capability | HYDRA textual editor | Standalone editor | VS Code export | Eclipse Modeling export |
|---|---|---|---|---|
| Grammar-defined structure | Native `grammar.<id>` projection | Embedded canonical grammar/projection | Grammar is an autonomous semantic root | Grammar is an autonomous semantic root; no synthetic Ecore is required |
| Validations | Canonical validation runtime | Portable runtime | Canonical documents + semantic runtime bridge | Canonical documents + semantic runtime bridge |
| Quick Fixes / actions | Canonical action runtime | Portable runtime | Commit lifecycle | EMF/command lifecycle when applicable |
| EventBinding | Native `EventBindingRuntimeService` | FIFO portable event runtime | Embedded canonical event documents | Embedded canonical event documents |
| EventFlow | Lazy auto-deployment on first scoped event | FIFO portable flow runtime | Semantic runtime bridge | Semantic runtime bridge |
| Gamification | Receives canonical/derived events | Persistent state, with model-annotation fallback | Embedded canonical gamification documents | Embedded canonical gamification documents |
| Audit | Grammar, syntax style and textual context mutations | N/A local generated editor | Generation inputs remain traceable | Generation inputs remain traceable |

## Execution invariants

1. Nested event publications are FIFO: a derived event never interrupts delivery of its cause.
2. Binding and flow chains are bounded to avoid accidental cycles.
3. `EventFlow.stop(scope)` prevents lazy restart until an explicit `start(scope)`.
4. A grammar-first export must not require a `METAMODEL` artifact just to satisfy the target adapter.
5. Syntax style, contextual textual-editor information and grammar mutations are named explicitly in the bilingual audit catalog.

The executable standalone smoke in `scripts/validation/r5c13_standalone_smoke.mjs` verifies the chain `source.saved → domain.saved → flow.completed` and a five-point gamification award.
