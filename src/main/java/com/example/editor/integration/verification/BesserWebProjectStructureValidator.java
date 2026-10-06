package com.example.editor.integration.verification;

import java.nio.file.Path;
import java.util.List;
import org.springframework.stereotype.Component;
import com.example.editor.integration.IntegrationTechnology;
import com.example.editor.integration.compiler.TechnologyCompilationResult;
import tools.jackson.databind.JsonNode;

/** Comprueba que la extensión BESSER WME puede instalarse como paquete npm aditivo. */
@Component
public class BesserWebProjectStructureValidator implements TechnologyProjectStructureValidator {
    /** Descripción: Ejecuta la operación «technology» correspondiente a la responsabilidad de BesserWebProjectStructureValidator. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @Override public IntegrationTechnology technology(){return IntegrationTechnology.BESSER_WEB_MODELING_EDITOR;}
    /** Descripción: Valida las condiciones necesarias para «validate» y rechaza entradas no válidas. Entradas: root: ruta o ubicación utilizada por la operación; compilation: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ @Override public List<TechnologyValidationStepResult> validate(Path root, TechnologyCompilationResult compilation){
        return List.of(
                TechnologyProjectValidationSupport.check("besser-wme-package", "Validate BESSER WME package", () -> {
                    JsonNode json = TechnologyProjectValidationSupport.json(TechnologyProjectValidationSupport.requireSuffix(root, "/package.json", "BESSER WME package.json"));
                    if(json.path("dependencies").isMissingNode() && json.path("peerDependencies").isMissingNode()){throw new IllegalStateException("WME dependencies or peer dependencies are required");}
                    if(json.at("/scripts/build").asText().isBlank()){throw new IllegalStateException("WME build script is required");}
                }),
                TechnologyProjectValidationSupport.check("besser-wme-codec", "Check model codec and diagram registration", () -> {
                    TechnologyProjectValidationSupport.requireSuffix(root, "/src/hydra-model-codec.ts", "HYDRA model codec");
                    TechnologyProjectValidationSupport.requireSuffix(root, "/src/hydra-diagram-type.ts", "diagram type registration");
                }),
                TechnologyProjectValidationSupport.check("besser-wme-tests", "Check generated extension test", () -> TechnologyProjectValidationSupport.requireSuffix(root, "/tests/extension.test.ts", "WME extension test")),
                TechnologyProjectValidationSupport.check("besser-wme-canonical", "Parse canonical editor definition", () -> TechnologyProjectValidationSupport.json(TechnologyProjectValidationSupport.requireSuffix(root, "/hydra/editor_definition.json", "canonical editor definition")))
        );
    }
}
