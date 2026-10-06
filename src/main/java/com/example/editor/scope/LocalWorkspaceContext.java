package com.example.editor.scope;

import org.springframework.stereotype.Component;

/** Contexto por defecto de HYDRA local/single-user. Se podrá sustituir en BG/BH sin cambiar los dominios. */
@Component
public class LocalWorkspaceContext implements WorkspaceContext {
    /** Descripción: Ejecuta la operación «currentScope» correspondiente a la responsabilidad de LocalWorkspaceContext. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @Override public ResourceScope currentScope(){return ResourceScope.local();}
}
