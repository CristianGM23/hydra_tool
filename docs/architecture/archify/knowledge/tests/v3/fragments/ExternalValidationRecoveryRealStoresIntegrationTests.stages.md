- recovery_lifecycle.a4 (recovered rules): onlySafelyTranslatedRulesAndFixesAreEnabledOnDisk, lines 152-172 (stored rule names equal the 11 counted in the .evl; enabled iff safe translation) and lines 177-198 (6 actions, operations only when enabled, rule linked to its fix).
- recovery_lifecycle.v ("available rules" artifact: translated enabled, rest in review): lines 163-164 and 170-172 (disabled rules are external mode with the review flag; guarded NoSelfLoop stays disabled).
- Negative: anOversizedOrBinarySourceProducesNoRulesAndNoDocuments, lines 121-126 (no rules and no documents on disk).
Note: real expression/action/validation services over temp stores; the Mockito-based ExternalValidationRecoveryRuntimeTests is left untouched.
