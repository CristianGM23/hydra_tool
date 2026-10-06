package com.example.editor.project;

import java.util.List;

/** Selección del Project Explorer que se empaquetará para exportación local o remota. */
public record ProjectSelectionExportRequest(List<String> paths) {
    public ProjectSelectionExportRequest {
        paths = paths == null ? List.of() : List.copyOf(paths);
    }
}
