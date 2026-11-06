GeneratedProjectLifecycleRealProcessIntegrationTests - stage mapping
- generated_projects_lifecycle.rg / generated_projects_author_wizard.rg: registerThroughHttpPersistsTheRegistryOnDisk (L118) + unsafeRelativeDirectoriesAreRejected... (L192).
- generated_projects_author_wizard.pv: dryRunPlansTheCommandsAndExecutesNothing (L132-L134, no markers on disk).
- generated_projects_lifecycle.ge/op, generated_projects_author_wizard.ru: realExecutionRunsNativeProcesses... (GENERATE/OPEN PASSED, markers read from disk).
- generated_projects_lifecycle.va: same test (marker only exists if GENERATE ran) + failingValidateStage... (L180, exitCode 1, rest SKIPPED).
- generated_projects_lifecycle.la: LAUNCH SKIPPED without allowOptionalStages, then PASSED with launch.marker.
- Not a stage claim: productionStandaloneWebCommandsPassTheDefaultExternalProcessPolicy (regression of the former finding; real run in GeneratedStandaloneWebProfileRealLifecycleIntegrationTests).
- generated_projects_author_wizard.pj: INDIRECT only (project selection is a UI step).
