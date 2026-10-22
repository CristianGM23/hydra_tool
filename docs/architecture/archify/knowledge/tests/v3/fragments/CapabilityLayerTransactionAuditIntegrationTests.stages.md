CapabilityLayerTransactionAuditIntegrationTests - stage mapping
- Defect #10 (HALLAZGOS_PRODUCCION) corrected: applying and rolling back a layer now leaves one raw audit line each.
- x_package_and_move_capabilities.r2 (documents saved and audit entries): applyingAndRollingBackALayerLeavesOneAuditLineEach reads the raw JSONL audit file and asserts one line per mutation; aPreviewOnlyRequestWritesNothingAndAuditsNothing asserts the negative case.
