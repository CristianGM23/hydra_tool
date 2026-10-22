HydraReleaseGateRealCollaboratorsIntegrationTests - stage mapping
- publication_generate_publish.gt: everythingRequiredAndPassingProducesAPassedAttestedReport (L133-L142: five checks evaluated, reportId from attestation, file on disk L149).
- recovery_author_governance.b: aRequiredCheckThatFailsBlocksTheRelease (L168 unknown mirror FAIL, L175 performance regression FAIL, L180 bench/merge required FAIL).
- recovery_author_governance.d: same test + absentOptionalRequirementsOnlyProduceWarnings (L195): final passed/blocked result.
- recovery_author_governance.r: htmlAttestationIsDownloadableAndTamperingWithTheStoredJsonIsDetected (L213 HTML contains attestation, L218 tamper then reads >=400).
- recovery_author_governance.a, .c: INDIRECT (real semantic merge / real adapter bench not exercised).
- Dropped: final-verified publication with failed gate (needs provenance + platform readiness wiring).
