package com.example.editor.policy;
/** Existing AV report re-evaluated under current policy and revocation data. */
public record HydraPolicyEvaluationRequest(String provenanceReportId, boolean includeWarnings) { }
