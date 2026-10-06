package com.example.editor.integration.verification;

import java.nio.file.Path;
import java.util.List;
import com.example.editor.integration.IntegrationTechnology;
import com.example.editor.integration.compiler.TechnologyCompilationResult;

/** SPI para comprobar que una exportación puede abrirse o utilizarse como proyecto del destino declarado. */
public interface TechnologyProjectStructureValidator {
    /** Descripción: Ejecuta la operación «technology» correspondiente a la responsabilidad de TechnologyProjectStructureValidator. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ IntegrationTechnology technology();
    /** Descripción: Valida las condiciones necesarias para «validate» y rechaza entradas no válidas. Entradas: projectRoot: dato de entrada necesario para la operación; compilation: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ List<TechnologyValidationStepResult> validate(Path projectRoot, TechnologyCompilationResult compilation);
}
