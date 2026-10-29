TransformationCheckDiagnosticsQuickFixRuntimeTests - stage mapping
- transformation_author_guided.ck: aTypeMismatchShowsItsDiagnosticAndAnExplicitConversionFixThatMakesTheDraftValid (L80 error report, L95 fix id, L112 fix re-checked valid, then saved and re-read) + aRequiredTargetWithoutMappingWarnsAndProposesTheCompatibleDirectMapping (L137 fix id, L148 warning gone) + aCleanDraftHasNoFixesAndTheHttpEndpointReturnsTheSameFixesAsTheService (L163 no fixes, L176 HTTP = service).
- Limit: the wizard screen that renders the diagnostics is UI; only the data it shows is proven.
