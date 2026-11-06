- x_metamodel_save_and_cascade.val / vok: cyclicInheritanceIsRejectedWithoutDocumentNorAuditLine (lines 142-147) and cyclicUpdateLeavesTheStoredDocumentUntouched (line 162).
- x_metamodel_save_and_cascade.doc / saved: validMetamodelIsStoredAsVersionedDocument (line 178, ETag = SHA-256 of the file) and obsoleteEtagIsAConflictAndDiskIsIntact (line 194).
- x_metamodel_save_and_cascade.aud: successfulSavesAppendOneAuditLineEach (lines 208-210, JSONL lines metamodel.created / metamodel.updated).
- x_project_lifecycle_overview.mm / d2: the same save and stale-ETag conflict assertions (lines 178, 194).
Note: the repository audits through AuditEventPublisher ("metamodel.created"), not through AuditCanonicalDocumentMutationObserver.
