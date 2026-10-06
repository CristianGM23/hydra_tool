package com.example.editor.policy.bundle;

/** Activates exactly the bundle version and simulation evidence reviewed by the caller. */
public record HydraPolicyBundleActivationRequest(String simulationReportId) { }
