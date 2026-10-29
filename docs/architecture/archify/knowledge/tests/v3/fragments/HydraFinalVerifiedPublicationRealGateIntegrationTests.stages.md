HydraFinalVerifiedPublicationRealGateIntegrationTests - stage mapping
- publication_generate_publish.v (simple route, no gate): theSimpleRoutePublishesWithoutAnyGate (L143 manifest artifact hash = hash recomputed from the distribution file).
- publication_generate_publish.v (verified route accepted): theVerifiedRoutePublishesWhenGateProvenanceAndPlatformAreSound (L156 provenance valid, L166 manifest on disk with recomputed hash).
- publication_generate_publish.v (blocked gate): theVerifiedRouteRejectsABlockedGateAndWritesNothing (L182 error status, L183 no publication directory).
- publication_generate_publish.v (mismatch/missing evidence + positive control): theVerifiedRouteRejectsMismatchedOrMissingEvidence... (L194-L199 rejected, nothing written; L201 correct request publishes).
