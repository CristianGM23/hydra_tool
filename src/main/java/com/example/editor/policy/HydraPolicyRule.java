package com.example.editor.policy;

import java.util.Map;

/** Data-only policy rule; no scripts or executable classes are accepted. */
public record HydraPolicyRule(String id, HydraPolicyKind kind, String key, String value,
        HydraPolicySeverity severity, boolean enabled, Map<String,String> annotations) {
    public HydraPolicyRule { annotations=annotations==null?Map.of():Map.copyOf(annotations); severity=severity==null?HydraPolicySeverity.ERROR:severity; }
}
