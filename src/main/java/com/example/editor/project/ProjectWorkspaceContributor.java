package com.example.editor.project;

import java.util.List;

/** SPI que permite añadir repositorios sincronizables sin modificar ProjectWorkspaceBridge. */
public interface ProjectWorkspaceContributor {
    /** Descripción: Ejecuta la operación «contributions» correspondiente a la responsabilidad de ProjectWorkspaceContributor. Entradas: layout: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ List<ProjectWorkspaceContribution> contributions(ProjectLayout layout);
}
