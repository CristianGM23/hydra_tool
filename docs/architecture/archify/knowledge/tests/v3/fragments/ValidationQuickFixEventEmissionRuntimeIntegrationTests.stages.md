ValidationQuickFixEventEmissionRuntimeIntegrationTests - stage mapping
- events_publish_session.a8: runningTheValidationEmitsTheExecutionAndTheViolationEvents (l.197-210) fixes the validation events; applyingAndUndoingAQuickFixEmitsTheFixEvents (l.222-239) fixes the exact order and data of resolved/started/applied and undone events on the real bus.
- x_validate_fix_revalidate_reward.d1 (are there diagnostics?): diagnosticsCarrySeverityAndFixesAndAResolvedOneIsNoLongerFound (l.247-264) asserts one diagnostic with error severity and its fix list, and the not-found rejection once the report has no diagnostics.
- x_validate_fix_revalidate_reward.diag (problems panel list) is NOT claimed direct: the panel is a screen projection of the same report.
