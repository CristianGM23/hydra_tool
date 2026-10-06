package com.example.editor.integration.verification;

import java.nio.file.Path;
import java.util.List;
import org.springframework.stereotype.Component;
import com.example.editor.integration.IntegrationTechnology;
import com.example.editor.integration.compiler.TechnologyCompilationResult;
import tools.jackson.databind.JsonNode;

/** Comprueba el contrato cliente-servidor y el source-model bridge de un proyecto GLSP. */
@Component
public class EclipseGlspProjectStructureValidator implements TechnologyProjectStructureValidator {
    /** Descripción: Ejecuta la operación «technology» correspondiente a la responsabilidad de EclipseGlspProjectStructureValidator. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @Override public IntegrationTechnology technology(){return IntegrationTechnology.ECLIPSE_GLSP;}
    /** Descripción: Valida las condiciones necesarias para «validate» y rechaza entradas no válidas. Entradas: root: ruta o ubicación utilizada por la operación; compilation: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ @Override public List<TechnologyValidationStepResult> validate(Path root, TechnologyCompilationResult compilation){
        return List.of(
                TechnologyProjectValidationSupport.check("glsp-descriptor", "Validate HYDRA GLSP descriptor", () -> {
                    JsonNode json = TechnologyProjectValidationSupport.json(TechnologyProjectValidationSupport.requireSuffix(root, "/hydra-glsp.json", "hydra-glsp.json"));
                    if(json.path("diagramType").asText().isBlank()){throw new IllegalStateException("GLSP diagramType is required");}
                    if(json.path("sourceModel").asText().isBlank()){throw new IllegalStateException("GLSP sourceModel is required");}
                }),
                TechnologyProjectValidationSupport.check("glsp-client", "Validate GLSP TypeScript client package", () -> {
                    JsonNode json = TechnologyProjectValidationSupport.json(TechnologyProjectValidationSupport.require(root,
                            path -> path.toString().replace('\\', '/').endsWith("/client/package.json"), "GLSP client package.json"));
                    if(json.path("dependencies").isMissingNode()){throw new IllegalStateException("GLSP client dependencies are required");}
                }),
                TechnologyProjectValidationSupport.check("glsp-server", "Validate GLSP server project", () -> {
                    boolean javaServer = TechnologyProjectValidationSupport.files(root).stream().anyMatch(path -> normalized(path).endsWith("/server/pom.xml"));
                    boolean nodeServer = TechnologyProjectValidationSupport.files(root).stream().anyMatch(path -> normalized(path).endsWith("/server/package.json"));
                    if(!javaServer && !nodeServer){throw new IllegalStateException("GLSP project must contain a Java or TypeScript server");}
                    if(javaServer){TechnologyProjectValidationSupport.xml(TechnologyProjectValidationSupport.requireSuffix(root, "/server/pom.xml", "GLSP server pom"));}
                }),
                TechnologyProjectValidationSupport.check("glsp-source-model", "Check canonical source model bridge", () -> TechnologyProjectValidationSupport.json(TechnologyProjectValidationSupport.requireSuffix(root, "/hydra/metamodel.json", "canonical metamodel")))
        );
    }

    /** Descripción: Ejecuta la operación «normalized» correspondiente a la responsabilidad de EclipseGlspProjectStructureValidator. Entradas: path: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String normalized(Path path){return path.toString().replace('\\', '/');}
}

