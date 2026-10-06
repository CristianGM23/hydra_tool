package com.example.editor.project;

import java.time.Instant;
import java.util.List;

/** Estado de la sesión de proyecto activa. */
public record ProjectSession(boolean active,ProjectDescriptor project,String directory,Instant openedAt,List<String> restoredTabs) {
    public ProjectSession {
        directory = directory == null ? "" : directory;
        restoredTabs = restoredTabs == null ? List.of() : List.copyOf(restoredTabs);
    }

    /** Descripción: Elimina o libera el recurso gestionado por «closed» de forma controlada. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public static ProjectSession closed() {
        return new ProjectSession(false,null,"",null,List.of());
    }
}
