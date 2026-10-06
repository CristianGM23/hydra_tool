package com.example.editor.project;

import java.util.List;

/** Pestañas que el frontend desea restaurar al volver a abrir el proyecto. */
public record ProjectTabSessionRequest(List<String> paths) {
    public ProjectTabSessionRequest { paths = paths == null ? List.of() : List.copyOf(paths); }
}
