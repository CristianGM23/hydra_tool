package com.example.editor.project;

import java.util.List;

/**
 * Módulo cuyo estado en memoria se deriva de los repositorios del espacio de trabajo. Tras traer un proyecto al espacio de trabajo
 * (PROJECT_TO_WORKSPACE) el puente de sincronización lo recarga, como anuncia la vista previa de la sincronización.
 */
public interface ProjectWorkspaceReloadListener {
    /** Descripción: Recarga el estado del módulo desde los repositorios recién sincronizados. Salida: avisos legibles para el usuario (vacío si todo quedó disponible). Entradas: no requiere parámetros de entrada. */
    List<String> reloadAfterProjectToWorkspace();
}
