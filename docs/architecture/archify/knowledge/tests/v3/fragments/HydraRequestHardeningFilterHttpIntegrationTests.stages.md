HydraRequestHardeningFilterHttpIntegrationTests - stage mapping
- security_filter_sandbox.a: aNormalRequestPassesTheFilter (L86), oversizedLineHeadersAndControlCharactersAreRejectedWith400 (L96), theRealFilterRejectsEveryUnsafePathSequence... (L133-L134), oversizedJsonBody...413AndNothingIsWritten (L149, L151, positive control L156).
- x_package_and_move_capabilities.sec: oversizedCapabilityPackageImportIsRejectedBeforeTheImportController (L175, multipart to /api/capability-packages/archives/import).
- x_remote_repository_exchange.sec: INDIRECT (filter half only; HydraExternalProcessRunner isolation not asserted - dropped as direct).
