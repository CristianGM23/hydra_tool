HydraReleaseGateFiveRealChecksIntegrationTests - stage mapping (class lives in the update-site test package: the remote capture type is package-private)
- publication_generate_publish.gt: fiveRealChecksPassAndTheReportIsAttestedOnDisk (L217 five checks PASS with real mirror, bench, merge, performance).
- publication_generate_publish.ok: same test (L217 passed) + aFailingAdapterBenchBlocksTheReleaseAndCannotBeCertified (L261 passed=false).
- publication_generate_publish.cert: L249 assertPassed returns the id for the apt report; L267 throws for the blocked one.
- publication_generate_publish.blk / recovery_author_governance.r: theHtmlOfABlockedReportListsEveryCheckWithItsStatus (L289 one row per check, FAIL row) and fingerprint recomputed (L244), tampering rejected (L349).
- recovery_author_governance.a: L217 merge PASS; theSemanticMergeCheckRequiresAnApplicablePreviewMatchingTheReleasePackage (L309 dangling reference FAIL, L315 differing package FAIL).
- recovery_author_governance.b: requiredEvidenceThatIsMissingBlocksWhileOptionalEvidenceOnlyWarns (L328 required mirror/bench/performance/merge missing = FAIL).
- recovery_author_governance.c: L217 real bench PASS, L261 real bench FAIL.
- recovery_author_governance.d: L335 optional missing = WARNING and passed=true.

- HELD: strength MEDIUM (external: mocked). The STRONG claim is withheld until the simulated network capture and snapshot analysis are replaced by real ones.
