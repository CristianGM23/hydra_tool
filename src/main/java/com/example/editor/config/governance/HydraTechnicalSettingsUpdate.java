package com.example.editor.config.governance;

import java.util.Map;

public record HydraTechnicalSettingsUpdate(Map<String, String> values) {
    public HydraTechnicalSettingsUpdate { values = values == null ? Map.of() : Map.copyOf(values); }
}
