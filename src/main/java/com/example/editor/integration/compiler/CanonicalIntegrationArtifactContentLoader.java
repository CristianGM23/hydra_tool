package com.example.editor.integration.compiler;

import java.util.List;
import com.example.editor.integration.IntegrationProfileModel;
import com.example.editor.integration.projection.CanonicalArtifactContent;

/** Puerto que carga y serializa los documentos canónicos seleccionados por un perfil. */
public interface CanonicalIntegrationArtifactContentLoader {
    /** Descripción: Obtiene la información correspondiente a «load» dentro de CanonicalIntegrationArtifactContentLoader. Entradas: profile: archivo o contenido que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ List<CanonicalArtifactContent> load(IntegrationProfileModel profile);
}
