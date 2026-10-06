package com.example.editor.codegen;

import java.util.Set;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.validation.model.CanonicalModelDocument;

/** Contrato extensible que genera uno o varios destinos de código o proyección desde un modelo canónico. */
public interface ModelCodeGenerationRenderer {
    /** Descripción: Expone los identificadores de destino atendidos por este renderer. Entradas: ninguna. Salida: conjunto estable y no vacío de identificadores. */
    /** Descripción: Ejecuta la operación «targetIds» correspondiente a la responsabilidad de ModelCodeGenerationRenderer. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ Set<String> targetIds();

    /** Descripción: Expone las capacidades opcionales realmente soportadas por un destino. Entradas: identificador del destino. Salida: conjunto de capacidades. */
    /** Descripción: Ejecuta la operación «supportedFeatures» correspondiente a la responsabilidad de ModelCodeGenerationRenderer. Entradas: targetId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ Set<ModelCodeGenerationFeature> supportedFeatures(String targetId);

    /** Descripción: Genera el artefacto solicitado desde el modelo y metamodelo activos. Entradas: destino, descriptor público, metamodelo, modelo y opciones. Salida: artefacto descargable. */
    /** Descripción: Crea o prepara el resultado de «generate» dentro de ModelCodeGenerationRenderer. Entradas: targetId: identificador que delimita el recurso objetivo; target: dato de entrada necesario para la operación; metamodel: dato de entrada necesario para la operación; model: dato de entrada necesario para la operación; options: configuración que gobierna el comportamiento. Salida: devuelve el resultado definido por el contrato del método. */ GeneratedModelArtifact generate(String targetId, ModelCodeGenerationTarget target, MetaModel metamodel,
            CanonicalModelDocument model, ModelCodeGenerationOptions options);
}
