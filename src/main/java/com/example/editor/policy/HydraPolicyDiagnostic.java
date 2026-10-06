package com.example.editor.policy;
/** Explainable AY policy result. */
public record HydraPolicyDiagnostic(String ruleId, HydraPolicySeverity severity, String subjectId, String message) { }
