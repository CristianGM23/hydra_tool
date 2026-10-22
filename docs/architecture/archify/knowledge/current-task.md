# HYDRA Archify — Current Portal Refinement

## 1. Objective

Continue and improve the EXISTING HYDRA architecture portal.

Public entry point:

docs/architecture/archify/hydra-architecture.html

Do NOT regenerate HYDRA architecture from scratch.

Reuse the existing portal, Archify artifacts and validated architecture knowledge.

The goal is to turn the portal into a rich, navigable HYDRA documentation system, not merely a high-level architecture diagram.


## 2. Source-of-truth rule

The CURRENT LOCAL HYDRA WORKING TREE is authoritative.

Never assume a historical commit SHA is still current.

At the beginning of architecture work:

1. resolve current Git branch and HEAD dynamically;
2. inspect staged changes;
3. inspect unstaged changes;
4. inspect relevant untracked source/configuration files;
5. read the existing architecture knowledge cache;
6. compare its last analyzed state with the current repository state;
7. re-analyze only changed or insufficient evidence.

The existing cache is:

docs/architecture/archify/knowledge/baseline.json
docs/architecture/archify/knowledge/capabilities-cache.json
docs/architecture/archify/knowledge/gaps.json
docs/architecture/archify/knowledge/README.md

Do NOT create a competing cache structure.


## 3. Incremental-analysis policy

Do NOT perform repository-wide rediscovery for normal portal refinements.

First:

knowledge cache
→ Git state
→ changed evidence
→ specific knowledge gaps

Only inspect source when:

- supporting source changed;
- cached evidence is insufficient;
- a requested concept has not yet been investigated;
- evidence conflicts;
- a relationship/process needs verification.

Reuse unchanged VALIDATED evidence.

Use hydra-evidence-scout only for ONE narrowly scoped source-evidence question when useful.

Do not recreate broad backend/frontend/infrastructure exploration agents.


## 4. Audience projections

HYDRA must provide three genuinely different views:

USER / USUARIO
FUNCTIONAL / FUNCIONAL
TECHNICAL / TÉCNICO

They describe the same underlying HYDRA system but MUST NOT be forced to expose the same visible topology or detail.


### USER

For a non-technical HYDRA user.

Explain:

- what each capability is;
- what it is for;
- what can be defined;
- what choices/types exist;
- what every element means;
- what is mandatory;
- what is optional;
- what is conditional;
- how something can be created;
- what it produces;
- what may happen afterwards.

Do NOT expose by default:

- Java/TypeScript class names;
- DTOs;
- packages;
- source paths;
- line numbers;
- API endpoints;
- internal runtime/service names;
- implementation schemas.


### FUNCTIONAL

Explain:

- domain concepts;
- types and variants;
- relationships;
- cardinalities;
- optionality;
- inputs/outputs;
- authoring semantics;
- lifecycle;
- processes;
- relationships between HYDRA capabilities.


### TECHNICAL

May expose:

- runtimes;
- services;
- classes/interfaces;
- packages;
- APIs/protocols;
- schemas;
- tests;
- source file + line evidence.


## 5. Portal structure

The portal must provide three main exploration areas:

OVERVIEW
CAPABILITIES
PROCESSES


### OVERVIEW

Must represent the complete current HYDRA platform.

It must include ALL architecturally meaningful capabilities discovered from current source.

Do not restrict the architecture to previously discussed areas.

Show meaningful relationships between capabilities.

Distinguish when source-backed:

- required relationship;
- optional relationship;
- conditional relationship;
- alternative relationship;
- event-driven relationship;
- external relationship.

USER Overview must use high-level language.


## 6. Dedicated capability workspaces

Every major capability must have its own rich view.

Preferred internal navigation:

Overview
Elements
Domain Map
How to Define
Processes
Relationships
Implementation


## 7. Capability Overview

For every capability explain in depth:

- What is it?
- What is it for?
- When would I use it?
- What can I define?
- How can I define it?
- What does it need?
- What does it produce?
- What is mandatory?
- What is optional?
- What can happen next?
- What other HYDRA capabilities does it interact with?


## 8. Complete element/domain catalog

For every meaningful definable element discover and document:

- display name;
- plain-language meaning;
- purpose;
- when it is used;
- actual supported types/variants;
- purpose of every variant;
- required / optional / conditional / alternative;
- meaningful configuration options;
- relationships;
- cardinality when meaningful;
- inputs;
- outputs;
- runtime effect;
- authoring methods.

Technical implementation belongs only to deeper Technical mode.


## 9. High-priority capability depth

The current portal particularly needs much deeper treatment of:


### Validation

Discover and explain actual source-backed:

- validation types;
- constraint types;
- contexts/scopes;
- expressions/conditions;
- severities;
- messages;
- targets;
- diagnostics;
- ranges/locations;
- structural/semantic/external/runtime categories if actually present;
- available authoring mechanisms.

For every actual validation type explain what it is for and when it should be used.


### Quick Fixes

Discover the real Quick Fix model.

Explain actual:

- Quick Fix types/variants;
- actions/operations;
- applicability;
- conditions;
- parameters;
- targets;
- association with validation/diagnostics;
- what each variant modifies;
- optionality;
- user invocation;
- what happens after application;
- revalidation if implemented;
- distinction between grammar/model Quick Fix mechanisms if they are different.


### Gamification

Discover and explain the actual supported elements, such as where present:

- rules;
- achievements;
- challenges;
- levels;
- points;
- rewards;
- conditions;
- triggers;
- bindings;
- progress;
- state;
- feedback.

Explain what each one represents and what it is for.


### Events

Discover and explain:

- event types;
- triggers;
- sources;
- conditions;
- actions/steps;
- bindings;
- flow elements;
- branches;
- outputs;
- internal/external actions;
- execution states.

Explain every meaningful event/flow element.

Verify its relationship with Gamification and other capabilities rather than assuming it.


### Transformations

Explain actual:

- transformation definitions;
- source/target;
- rules/mappings;
- transformation types;
- directionality;
- modes;
- batch/incremental behavior where present;
- current/baseline/deltas where present;
- correspondences;
- traceability;
- toolchains/bridges;
- authoring mechanisms.


### Grammars / Languages

Explain actual:

- language/grammar definitions;
- rules;
- tokens;
- references;
- semantic concepts;
- generated/inferred structures;
- textual editing;
- diagnostics;
- hover;
- code actions;
- Quick Fixes;
- runtime/editor generation;
- available authoring methods.

Apply equivalent depth to ALL other major HYDRA capabilities.


## 10. Domain Map

Every significant capability needs a visual conceptual/domain map.

DOMAIN MAP means:

what elements exist
+
how they relate.

It is NOT a Java class diagram.

It may show where meaningful:

- containment;
- references;
- cardinalities;
- required/optional relationships;
- variants;
- generated concepts.

Domain-map elements should be selectable.


## 11. How to Define

A major requirement is to explain HOW users create/configure concepts.

Discover actual source-backed authoring methods such as:

- guided process;
- wizard;
- form;
- graphical diagram;
- flow designer;
- textual editor;
- property editor;
- import;
- generated configuration;
- other real mechanisms.

Do not merely list these methods.

Explain what the user does in each real mechanism.


## 12. Visual authoring micro-processes

When a meaningful element has an authoring workflow, provide a visual process for it.

For example, conceptually:

START
→ choose element/type
→ configure mandatory information
→ configure optional information
→ establish relationships
→ validate
→ save/result

This is only an example.

Use the ACTUAL HYDRA workflow.

If an element supports several authoring mechanisms, each meaningful mechanism may need its own micro-process.


## 13. Domain Map and Process are different

Do not confuse:

DOMAIN MAP
=
what exists and how concepts relate

with:

PROCESS
=
what happens through time.

Both may be interactive.


## 14. Processes area

PROCESSES must become a TRUE graphical interactive process explorer.

It must not principally be:

- text lists;
- cards;
- numbered descriptions.

A process should occupy a real diagram/canvas area.


## 15. Process selection

Conceptually support:

Capability: [ Validation ▼ ]
Process:    [ Execute validation ▼ ]

and display the chosen process graphically.

Equivalent UI is acceptable.


## 16. Process diagrams

A process may contain:

START

END

MANDATORY STEP

OPTIONAL STEP

DECISION

ALTERNATIVE

CONDITIONAL BRANCH

LOOP / REVALIDATION

ARTIFACT / RESULT

USER ACTION

AUTOMATIC HYDRA ACTION

EXTERNAL SYSTEM

CROSS-CAPABILITY TRANSITION

Do not rely on color alone to represent semantics.


## 17. Interactive process stages

Every meaningful process node/stage must be clickable/selectable.

Selecting a stage must expose an inspector.


### USER stage detail

- what happens;
- why;
- what is needed;
- what is produced;
- required/optional;
- what may happen next.


### FUNCTIONAL stage detail

- owner capability;
- associated domain concept;
- semantic inputs/outputs;
- conditions;
- state transition;
- related elements.


### TECHNICAL stage detail

- runtime/service;
- implementation concept;
- relevant tests;
- source evidence.


## 18. Process navigation

Support navigation where useful:

process stage
↔ domain concept
↔ capability

and:

domain concept
→ processes where it participates.


## 19. Process coverage

Do not model HYDRA as one single workflow.

Discover:

CAPABILITY-SPECIFIC PROCESSES

and

CROSS-CAPABILITY PROCESSES.

A capability may have several different processes.

Examples to investigate, not assume:

Validation:
- define validation;
- execute validation;
- apply Quick Fix.

Transformations:
- define;
- configure;
- execute;
- incremental synchronization.

Grammars:
- define language;
- analyze/generate;
- textual editing;
- diagnostics.

Events:
- define;
- compose flow;
- execute.

Gamification:
- define;
- consume event/trigger;
- update progress/reward.

Use real source evidence.


## 20. Cross-capability workflows

Discover meaningful complete workflows involving several HYDRA capabilities.

Represent:

- capability ownership per stage;
- artifacts exchanged;
- required transitions;
- optional transitions;
- alternative routes;
- possible continuation;
- possible termination.

Do not restrict discovery to workflows previously suggested by the user.


## 21. Visual interaction quality

For complex processes provide where practical:

- large process canvas;
- pan;
- zoom;
- fit-to-view;
- reset;
- selectable nodes;
- focused stage;
- side inspector.

Do not place a complex process in a tiny card.


## 22. Required / optional semantics

Use consistent visual/text semantics for:

- mandatory;
- optional;
- conditional;
- alternative;
- repeatable;
- derived/generated.

Apply this both to:

domain concepts/properties

and

process stages.


## 23. User-view sanitization

Audit USER-facing content.

Normal USER content must not expose:

*.java
*.ts
*.tsx
class names
DTOs
packages
source line references
API paths
internal runtime/service names

unless a term is genuinely part of HYDRA's public vocabulary.

Rewrite or hide implementation leakage.


## 24. Functional depth

FUNCTIONAL must not be USER plus more words.

It must add:

- concept variants;
- semantic relationships;
- cardinality;
- optionality;
- process states;
- authoring semantics;
- lifecycle;
- cross-capability behavior.


## 25. Technical depth

TECHNICAL must map functional concepts onto implementation:

functional concept
→ technical type/schema

process stage
→ runtime/service

relationship
→ API/event/dependency

and expose verified source evidence.


## 26. Portal architecture

Maintain ONE public entry point:

docs/architecture/archify/hydra-architecture.html

Auxiliary Archify artifacts may remain internal.

Users must not need to manually open individual filesystem HTML artifacts.


## 27. Incremental implementation

For every missing piece of knowledge:

1. consult cache;
2. inspect current Git state;
3. reuse VALIDATED evidence;
4. perform focused source lookup only if required;
5. optionally use hydra-evidence-scout for one narrow evidence question;
6. update cache with validated findings;
7. update portal.

Do not repeatedly scan the repository.


## 28. Validation and definition of done

Archify validation passing is NOT sufficient.

Every major capability should be checked for:

- USER explanation;
- FUNCTIONAL explanation;
- TECHNICAL mapping;
- definable concepts;
- variants/types;
- purpose of each;
- required/optional semantics;
- authoring methods;
- visual authoring micro-processes;
- domain map;
- internal processes;
- graphical interactive process;
- relationships;
- inputs;
- outputs;
- cross-capability interactions;
- source evidence.

A capability remains incomplete when meaningful dimensions above are missing.

A process remains incomplete when it requires temporal explanation but has no real graphical clickable representation.


## 29. Safety

Do not modify HYDRA production code for this architecture task.

Do not reset, restore, clean, stash or discard user changes.

Do not commit.

Do not push.


## 30. Current status

At the start of every new session resolve dynamically:

- repository root;
- branch;
- HEAD;
- dirty/clean state.

Read baseline.json instead of trusting historical SHAs in conversation history.

The current local working tree is authoritative.


## 31. Current task

Update and deepen the EXISTING HYDRA architecture portal.

Do not regenerate everything from scratch.

Reuse validated findings and existing portal work.

Fill missing knowledge incrementally.

The task is pending human acceptance.
