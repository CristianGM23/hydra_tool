StandaloneDistributionHttpFlowIntegrationTests - stage mapping
- publication_standalone_distribution.pl / publication_author_distribution.pv: planMaterializeAndValidateOverHttp (L124, plan writes nothing).
- publication_standalone_distribution.mt / publication_author_distribution.mv, .va: same test (L129-L135 files on disk, APP_ENTRY then valid).
- publication_standalone_distribution.pk / publication_author_distribution.pk / x_generate_certify_distribute.zip,dist / x_project_lifecycle_overview.r: packageDownloadsAReproducibleZip... (L155-L156 hash header = recomputed SHA-256, ZIP entries).
- x_generate_certify_distribute.sec: invalidEditorsSecretsAndSymbolicLinksAreNotPackaged (L171, L177, L181, L200 (400 INVALID_REQUEST); positive control after removing secrets).
- publication_standalone_distribution.pr: blankProfileTraversalAndSymbolicEditorDirectoryAreRejected (L204).
- publication_standalone_distribution.rd/pr2: releaseReadinessAndReleaseSourcePackage... (L238 invalid on sources, L258 hash of release-source ZIP).
- x_project_lifecycle_overview.dl: INDIRECT (model code generation not exercised).
