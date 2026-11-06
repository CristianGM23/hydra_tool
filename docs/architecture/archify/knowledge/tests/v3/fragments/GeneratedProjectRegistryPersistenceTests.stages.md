GeneratedProjectRegistryPersistenceTests - stage mapping
- publication_generate_publish.reg: registeredProjectIsPersistedOnDiskAndRecoveredByAFreshServiceInstance (JSON on disk + fresh service, L69) and registeringTheSameIdReplacesTheEntryAndKeepsTheOthers (L90).
- x_generate_certify_distribute.reg: same two tests plus unknownProfileIsRejectedWithoutAnyEntry (L98) and directoriesOutsideTheConfiguredRootAreRejectedWithoutAnyEntry (L116, file content unchanged); OPEN in L138.
- x_generate_certify_distribute.pj: INDIRECT only (relative folder is validated, but the link to the open-project capability is not verified).
