package com.example.editor.integration.compiler;

import java.util.Set;
import com.example.editor.integration.IntegrationArtifactKind;
import com.example.editor.integration.IntegrationTechnology;

/** SPI neutral para traducir capacidades HYDRA al lenguaje y estructura nativos de un destino. */
public interface TechnologyAdapter {
    /** Descripción: Ejecuta la operación «technology» correspondiente a la responsabilidad de TechnologyAdapter. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ IntegrationTechnology technology();
    /** Descripción: Ejecuta la operación «supportedArtifactKinds» correspondiente a la responsabilidad de TechnologyAdapter. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ Set<IntegrationArtifactKind> supportedArtifactKinds();
    /** Descripción: Declara qué tipos de artefacto pueden actuar como raíz semántica autónoma para este adaptador. Entradas: no requiere parámetros de entrada. Salida: conjunto de raíces aceptadas; por compatibilidad, el metamodelo es la única raíz predeterminada. */ default Set<IntegrationArtifactKind> semanticRootKinds(){return Set.of(IntegrationArtifactKind.METAMODEL);}
    /** Descripción: Ejecuta la operación «compile» correspondiente a la responsabilidad de TechnologyAdapter. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ TechnologyCompilationResult compile(TechnologyCompilationRequest request);
}
