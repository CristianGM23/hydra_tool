package com.example.editor.metamodel.importing.updatesite;

/** Valor compacto utilizado por los gráficos del dashboard. */
public record UpdateSiteDashboardSlice(String id, String label, long value, String explanation) {
    public UpdateSiteDashboardSlice {
        id = id == null ? "" : id;
        label = label == null ? id : label;
        value = Math.max(0, value);
        explanation = explanation == null ? "" : explanation;
    }
}
