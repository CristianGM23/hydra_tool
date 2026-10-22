UpdateSiteRecoveryHttpLifecycleIntegrationTests - stage mapping
- recovery_author_wizard.d / recovery_lifecycle.a1: analyzeReturnsCandidatesAndTheExpectedMetamodel (L172 classes vs expectations, L176 nothing persisted).
- recovery_author_workspace.d: reconcile test (L199, any candidate as primary) + partialLayerSelectionNarrowsThePreview.
- recovery_lifecycle.a6 / recovery_author_workspace.f: materializeWithoutPersistWritesNothingToDisk (L213 unique node mapping ids with all layers, L226 stores empty).
- recovery_author_workspace.g/h, recovery_lifecycle.a3/a4, recovery_persist_rollback.d3: INDIRECT only. The persist=true test (L259) and the repeat test (L289) check never-5xx and that a refusal leaves no documents; a successful persist and rollback are not pinned.
