package com.example.editor.project;

import java.time.Instant;
import java.util.List;

/** Resultado de una comprobación de cambios externos. */
public record ProjectChangeSet(Instant checkedAt,List<ProjectFileChange> changes) {
    public ProjectChangeSet { changes = changes == null ? List.of() : List.copyOf(changes); }
}
