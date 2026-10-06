package com.example.editor.generated.project;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Safe server-owned catalog; the browser never supplies executable command lines. */
@Component
public class GeneratedProjectRuntimeProfileCatalog {
    private final List<GeneratedProjectRuntimeProfile> profiles = List.of(
            profile("standalone-web", "Standalone web editor", "HYDRA",
                    stage(GeneratedProjectStage.GENERATE, "Build the dependency-free Java backend",
                            List.of("cmd", "/c", "build.cmd"), List.of("sh", "./build.sh"), 180, false),
                    stage(GeneratedProjectStage.VALIDATE, "Verify the generated descriptor and frontend",
                            List.of("cmd", "/c", "if exist standalone-editor.json (exit /b 0) else (exit /b 1)"),
                            List.of("sh", "-c", "test -f standalone-editor.json && test -f frontend/index.html"), 30, false),
                    stage(GeneratedProjectStage.OPEN, "Resolve the generated project directory", List.of(), List.of(), 5, false),
                    stage(GeneratedProjectStage.LAUNCH, "Launch the local standalone editor",
                            List.of("cmd", "/c", "run.cmd"), List.of("sh", "./run.sh"), 30, true)),
            profile("langium", "Langium workspace", "LANGIUM",
                    stage(GeneratedProjectStage.GENERATE, "Generate Langium AST and services",
                            List.of("npm.cmd", "run", "langium:generate"), List.of("npm", "run", "langium:generate"), 300, false),
                    stage(GeneratedProjectStage.VALIDATE, "Type-check and build the Langium workspace",
                            List.of("npm.cmd", "run", "build"), List.of("npm", "run", "build"), 600, false),
                    stage(GeneratedProjectStage.OPEN, "Resolve the generated Langium workspace", List.of(), List.of(), 5, false)),
            profile("xtext", "Xtext reactor", "XTEXT",
                    stage(GeneratedProjectStage.GENERATE, "Generate Xtext sources through Maven",
                            List.of("mvn.cmd", "-DskipTests", "generate-sources"), List.of("mvn", "-DskipTests", "generate-sources"), 900, false),
                    stage(GeneratedProjectStage.VALIDATE, "Verify the generated Xtext reactor",
                            List.of("mvn.cmd", "verify"), List.of("mvn", "verify"), 1800, false),
                    stage(GeneratedProjectStage.OPEN, "Resolve the generated Xtext workspace", List.of(), List.of(), 5, false)),
            profile("glsp", "Eclipse GLSP project", "ECLIPSE_GLSP",
                    stage(GeneratedProjectStage.GENERATE, "Install generated GLSP dependencies",
                            List.of("npm.cmd", "install"), List.of("npm", "install"), 1200, false),
                    stage(GeneratedProjectStage.VALIDATE, "Build the GLSP project",
                            List.of("npm.cmd", "run", "build"), List.of("npm", "run", "build"), 1200, false),
                    stage(GeneratedProjectStage.OPEN, "Resolve the generated GLSP project", List.of(), List.of(), 5, false)),
            profile("besser-sdk", "BESSER SDK project", "BESSER",
                    stage(GeneratedProjectStage.GENERATE, "Install the generated Python package",
                            List.of("python", "-m", "pip", "install", "-e", ".[test]"),
                            List.of("python3", "-m", "pip", "install", "-e", ".[test]"), 1200, false),
                    stage(GeneratedProjectStage.VALIDATE, "Run generated BESSER tests",
                            List.of("python", "-m", "pytest"), List.of("python3", "-m", "pytest"), 1200, false),
                    stage(GeneratedProjectStage.OPEN, "Resolve the generated BESSER project", List.of(), List.of(), 5, false))
    ).stream().sorted(Comparator.comparing(GeneratedProjectRuntimeProfile::key)).toList();

    /** Descripción: Ejecuta la operación «profiles» correspondiente a la responsabilidad de GeneratedProjectRuntimeProfileCatalog. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public List<GeneratedProjectRuntimeProfile> profiles(){return profiles;}
    /** Descripción: Valida las condiciones necesarias para «require» y rechaza entradas no válidas. Entradas: key: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public GeneratedProjectRuntimeProfile require(String key){
        return profiles.stream().filter(item -> item.key().equals(key)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown generated-project profile: " + key));
    }

    /** Descripción: Ejecuta la operación «profile» correspondiente a la responsabilidad de GeneratedProjectRuntimeProfileCatalog. Entradas: key: identificador que delimita el recurso objetivo; name: dato de entrada necesario para la operación; technology: dato de entrada necesario para la operación; stages: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static GeneratedProjectRuntimeProfile profile(String key, String name, String technology,
            GeneratedProjectStageDefinition... stages){
        return new GeneratedProjectRuntimeProfile(key, name, technology,
                "Generate, validate, open and optionally launch a " + technology + " project.", List.of(stages));
    }

    /** Descripción: Ejecuta la operación «stage» correspondiente a la responsabilidad de GeneratedProjectRuntimeProfileCatalog. Entradas: stage: dato de entrada necesario para la operación; description: dato de entrada necesario para la operación; windows: dato de entrada necesario para la operación; unix: dato de entrada necesario para la operación; timeout: dato de entrada necesario para la operación; optional: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static GeneratedProjectStageDefinition stage(GeneratedProjectStage stage, String description,
            List<String> windows, List<String> unix, int timeout, boolean optional){
        return new GeneratedProjectStageDefinition(stage, description, windows, unix, timeout, optional, Map.of());
    }
}
