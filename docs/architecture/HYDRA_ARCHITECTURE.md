# HYDRA architecture

HYDRA is a local-first model-driven engineering workbench. The canonical core is independent from the external technologies that HYDRA imports, exports or integrates with.

## Current capability layers

- Metamodel Designer: canonical semantic structure and graphical authoring.
- Validation and Quick Fixes: independent declarative behavior layers.
- Model Editor and Editor Definition: model instances, forms, palettes, notation and runtime compilation.
- Events, EventFlow and Gamification: independent behavioral/event layers.
- Integrations and repositories: explicit external-location capabilities and reversible generation/injection.
- External Project Recovery: safe deep archive inventory, multi-technology evidence, topology, mapping reconciliation and non-destructive materialization.
- Update Site Recovery: specialized Eclipse/EMF/Sirius/GMF/Xtext recovery that feeds the same neutral evidence principles.
- Publication and generated-project lifecycle: reproducible outputs with validation before publication.

## Architectural rules

1. Canonical HYDRA documents use the current schema only. Internal migration chains are intentionally absent before GA.
2. External formats are interpreted by adapters and never become dependencies of the canonical model.
3. Unknown project files are preserved as PROJECT_RESOURCE rather than silently discarded.
4. Imported executable artifacts are inspected statically; HYDRA never executes imported project code during analysis.
5. User-facing flows start from intent and explain ambiguity. Technical identifiers remain in advanced/detail surfaces.
6. Common controls, progress, actionable errors and i18n are shared across modules.
7. Release metadata is internal and reproducible; product or metamodel version numbers are not part of the visual identity.
