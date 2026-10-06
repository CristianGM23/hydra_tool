# HYDRA Project model

## Decision

HYDRA has one structural project type: **HYDRA Project**. Creation presets describe the user's initial goal; they are not permanent project types and never restrict what can be added later.

A project owns a stable descriptor (`hydra-project.json`, schema 1.1), an experience mode, a set of capabilities, optional primary artifact metadata and the normal project resources. Existing schema 1.0 descriptors remain readable.

## Creation flow

1. Choose the goal/preset.
2. Choose Guided or Free experience.
3. Accept the recommended capabilities or customize them.
4. HYDRA resolves capability dependencies.
5. Create only the physical roots required by the selected capabilities.
6. Add or remove capabilities later from Project capabilities.

Disabling a capability never deletes files. Existing content belonging to a disabled capability remains visible in the logical explorer as an inactive group when that directory contains resources.

## Presets

- EMPTY
- GRAPHICAL_LANGUAGE
- TEXTUAL_DSL
- HYBRID_LANGUAGE
- MODELING_VALIDATION
- M2M_TRANSFORMATION
- M2T_GENERATION
- T2M_ENGINEERING
- COMPLETE_EDITOR
- COMPLETE_MDE

`createdFromPreset` is provenance only. It does not constrain the future project configuration.

## Capability dependency examples

- Visual modeling -> metamodel design + model editing.
- Textual modeling -> grammar design + model editing.
- Visual editor -> metamodel design + editor definitions.
- Textual editor -> grammar design + editor definitions.
- Syntax styling/contextual information -> grammar design + textual editor.
- Quick fixes -> validations.
- Event flows -> events.
- M2M/M2T -> metamodel design.
- T2M -> grammar design + metamodel design.
- Editor generation -> editor definitions.

The backend catalog is authoritative. The frontend wizard consumes `/api/projects/catalog` rather than duplicating these rules.

## Experience mode

- `GUIDED`: HYDRA prioritizes the recommended workflow and progressive disclosure.
- `FREE`: all enabled project tools are directly accessible.

The mode changes presentation, not the project data model.

## Primary artifact

A project can optionally identify one primary artifact by kind, ID and relative path. The Project Explorer exposes “Set as primary artifact” for files. Consumers such as Home, preview, generation and run actions can use this as the project's default entry point.

## Project Explorer context actions

The explorer provides node-specific context menus.

### Project

Create file/folder, paste, copy paths, refresh, properties, download project ZIP, configure project capabilities, repository/cloud actions, send/load workspace, close and delete project.

### Folder/group

Create file/folder, upload, paste, cut/copy, duplicate, rename, copy paths, export ZIP, export to repository, refresh, properties and delete.

### File

Open, cut/copy, duplicate, rename, download, copy paths, set as primary artifact, export ZIP/repository, refresh, properties and delete.

Selection-aware operations keep multi-selection semantics and destructive operations continue to use the existing confirmation flow.

## Compatibility

Legacy callers that use the previous project creation payload retain the previous complete-project/free-mode behavior. New callers use the explicit preset/capability model. Capability configuration is additive/non-destructive at the filesystem level.
