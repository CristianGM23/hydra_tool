# Technology workflow parity (R6C1)

R6C1 turns technology support into an observable end-to-end contract instead of treating a catalogue declaration as proof that a complete integration exists.

Each technology is evaluated through the same neutral stages: **configure → analyze → generate → verify → preview → apply → execute → round-trip → export**. A stage reports its support level, applicability, primary HYDRA action, expected outcome and prerequisites. The result is read-only metadata derived from the existing `IntegrationTechnologyCatalog` plus the `TechnologyAdapterRegistry` and `TechnologyProjectStructureValidatorRegistry`.

## Maturity rules

- `FULL_WORKFLOW`: registered adapter and validator, no unsupported applicable stage, score above the configurable full threshold.
- `OPERATIONAL`: registered adapter and validator and score above the operational threshold.
- `PARTIAL`: an implementation surface exists but the complete delivery/verification contract is not available.
- `DECLARED_ONLY`: the technology exists in the catalogue but no executable adapter is registered.
- `PROJECTION_ONLY`: the destination is intentionally a projection and host injection/runtime stages do not apply.

Native-host execution remains `EXTERNAL`: HYDRA may generate a runnable editor or integration, but it must not claim that a third-party host/toolchain is itself executed by the canonical runtime unless a dedicated execution contract exists.

## Guided UX contract

The Integration Designer consumes the workflow descriptors through the semantic-context API. It:

1. shows the real maturity and blockers for the selected technology;
2. blocks guided delivery for `DECLARED_ONLY` targets;
3. recommends only features backed by `FULL` catalogue capabilities;
4. does not expose host `compile/preview/apply` actions when the selected technology cannot perform them;
5. treats projection-only targets separately and directs them to the projection/export path;
6. keeps advanced partial/external capabilities manually configurable instead of enabling them silently.

This contract is intentionally technology-neutral. Adding a new technology requires a catalogue descriptor; operational generation additionally requires an adapter; qualification-ready generated projects require a structure validator. The workflow view therefore degrades safely as implementation pieces are added or removed.
