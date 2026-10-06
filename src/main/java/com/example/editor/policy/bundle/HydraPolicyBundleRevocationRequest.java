package com.example.editor.policy.bundle;

/** Revokes one immutable policy bundle version without deleting its history. */
public record HydraPolicyBundleRevocationRequest(String reason) { }
