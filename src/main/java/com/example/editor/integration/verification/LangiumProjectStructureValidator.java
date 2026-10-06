package com.example.editor.integration.verification;

import java.nio.file.Path;
import java.util.List;
import org.springframework.stereotype.Component;
import com.example.editor.integration.IntegrationTechnology;
import com.example.editor.integration.compiler.TechnologyCompilationResult;
import tools.jackson.databind.JsonNode;

/** Comprueba que el paquete Langium puede instalarse, generar AST y compilarse. */
@Component
public class LangiumProjectStructureValidator implements TechnologyProjectStructureValidator {
    /** Descripción: Ejecuta la operación «technology» correspondiente a la responsabilidad de LangiumProjectStructureValidator. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @Override public IntegrationTechnology technology(){return IntegrationTechnology.LANGIUM;}
    /** Descripción: Valida las condiciones necesarias para «validate» y rechaza entradas no válidas. Entradas: root: ruta o ubicación utilizada por la operación; compilation: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ @Override public List<TechnologyValidationStepResult> validate(Path root, TechnologyCompilationResult compilation){
        return List.of(
                TechnologyProjectValidationSupport.check("langium-package", "Validate npm scripts and dependencies", () -> {
                    JsonNode json = TechnologyProjectValidationSupport.json(TechnologyProjectValidationSupport.requireSuffix(root, "/package.json", "Langium package.json"));
                    requireText(json.at("/scripts/langium:generate"), "langium:generate script");
                    requireText(json.at("/scripts/build"), "build script");
                    requireText(json.at("/dependencies/langium"), "Langium dependency");
                }),
                TechnologyProjectValidationSupport.check("langium-config", "Validate Langium configuration", () -> {
                    JsonNode json = TechnologyProjectValidationSupport.json(TechnologyProjectValidationSupport.requireSuffix(root, "/langium-config.json", "langium-config.json"));
                    if(!json.path("languages").isArray() || json.path("languages").isEmpty()){throw new IllegalStateException("Langium configuration has no language");}
                }),
                TechnologyProjectValidationSupport.check("langium-grammar", "Validate Langium grammar and cross-reference syntax", () -> {
                    String grammar = TechnologyProjectValidationSupport.text(TechnologyProjectValidationSupport.requireSuffix(root, ".langium", "Langium grammar"));
                    TechnologyProjectValidationSupport.requireContains(grammar, "grammar ", "Langium grammar");
                    TechnologyProjectValidationSupport.requireContains(grammar, "entry Model:", "Langium grammar");
                    if(grammar.contains("=@")){throw new IllegalStateException("Langium cross references must use [Type:ID] syntax");}
                }),
                TechnologyProjectValidationSupport.check("langium-typescript", "Check generated TypeScript entry points", () -> {
                    long sources = TechnologyProjectValidationSupport.files(root).stream().filter(path -> path.toString().endsWith(".ts")).count();
                    if(sources < 5){throw new IllegalStateException("Langium package does not contain enough TypeScript sources");}
                })
        );
    }
    /** Descripción: Valida las condiciones necesarias para «requireText» y rechaza entradas no válidas. Entradas: value: valor de entrada que debe procesarse; description: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void requireText(JsonNode value, String description){if(value == null || value.isMissingNode() || value.asText().isBlank()){throw new IllegalStateException("Missing " + description);}}
}
