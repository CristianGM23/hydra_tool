TextualBrokenDraftRecoveryIntegrationTests - stage mapping
- Defects #14 and #15 (HALLAZGOS_PRODUCCION): no new process stage is claimed beyond those of TextualModelSessionRealLifecycleIntegrationTests.
- anInsertQuickFixAtTheEndOfTheTextIsSeparatedFromTheLastToken: INSERT replacement carries a separator and the repaired text parses.
- queryingOrSavingABrokenDraftIsAClientConflictNotAServerError: 409 CONFLICT for symbols query and save.
