package com.example.editor.integration.verification;

import java.nio.file.Path;
import java.util.List;

import org.springframework.stereotype.Component;

import com.example.editor.integration.IntegrationTechnology;
import com.example.editor.integration.compiler.TechnologyCompilationResult;

import tools.jackson.databind.JsonNode;

/** Valida la estructura y los contratos de seguridad del paquete inyectable mxGraph/draw.io generado por HYDRA. */
@Component
public class MxGraphDrawioProjectStructureValidator implements TechnologyProjectStructureValidator {
    /** Descripción: Ejecuta la operación «technology» correspondiente a la responsabilidad de MxGraphDrawioProjectStructureValidator. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @Override public IntegrationTechnology technology(){return IntegrationTechnology.MXGRAPH_DRAWIO;}

    /** Descripción: Valida las condiciones necesarias para «validate» y rechaza entradas no válidas. Entradas: root: ruta o ubicación utilizada por la operación; compilation: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ @Override public List<TechnologyValidationStepResult> validate(Path root, TechnologyCompilationResult compilation){
        return List.of(
                TechnologyProjectValidationSupport.check("mxgraph-package", "Validate mxGraph package manifest", () -> {
                    JsonNode json=TechnologyProjectValidationSupport.json(TechnologyProjectValidationSupport.requireSuffix(root,"/package.json","mxGraph package.json"));
                    if(json.path("main").asText().isBlank())throw new IllegalStateException("mxGraph runtime entry point is required");
                    TechnologyProjectValidationSupport.requireContains(json.path("scripts").path("test").asText(),"smoke.mjs","mxGraph package test script");
                    TechnologyProjectValidationSupport.requireContains(json.path("scripts").path("test").asText(),"drawio-smoke.mjs","draw.io package test script");
                }),
                TechnologyProjectValidationSupport.check("mxgraph-runtime", "Validate portable mxGraph runtime and safe bridge", () -> {
                    String source=TechnologyProjectValidationSupport.text(TechnologyProjectValidationSupport.requireSuffix(root,"/src/hydra-mxgraph-runtime.js","mxGraph runtime"));
                    TechnologyProjectValidationSupport.requireContains(source,"HydraMxGraphRuntime","mxGraph runtime");
                    TechnologyProjectValidationSupport.requireContains(source,"paletteEntries","mxGraph runtime");
                    TechnologyProjectValidationSupport.requireContains(source,"createNode","mxGraph runtime");
                    TechnologyProjectValidationSupport.requireContains(source,"createEdge","mxGraph runtime");
                    TechnologyProjectValidationSupport.requireContains(source,"applyQuickFix","mxGraph runtime");
                    TechnologyProjectValidationSupport.requireContains(source,"executeEventFlow","mxGraph runtime");
                    if(source.contains("eval(")||source.contains("new Function"))throw new IllegalStateException("Generated runtime must not evaluate imported expressions as arbitrary JavaScript");
                }),
                TechnologyProjectValidationSupport.check("drawio-plugin", "Validate diagrams.net/draw.io plug-in contribution", () -> {
                    String source=TechnologyProjectValidationSupport.text(TechnologyProjectValidationSupport.requireSuffix(root,"/drawio/hydra-drawio-plugin.js","draw.io plug-in"));
                    TechnologyProjectValidationSupport.requireContains(source,"Draw.loadPlugin","draw.io plug-in");
                    TechnologyProjectValidationSupport.requireContains(source,"addPaletteFunctions","draw.io plug-in");
                    TechnologyProjectValidationSupport.requireContains(source,"runtime.toggle","draw.io plug-in");
                }),
                TechnologyProjectValidationSupport.check("drawio-library", "Validate draw.io custom library", () ->
                    TechnologyProjectValidationSupport.xml(TechnologyProjectValidationSupport.requireSuffix(root,"/drawio/hydra-palette.xml","draw.io custom library"))),
                TechnologyProjectValidationSupport.check("mxgraph-manifest", "Validate HYDRA mxGraph manifest and canonical runtime", () -> {
                    JsonNode json=TechnologyProjectValidationSupport.json(TechnologyProjectValidationSupport.requireSuffix(root,"/resources/hydra-mxgraph-manifest.json","mxGraph HYDRA manifest"));
                    if(!"MXGRAPH_DRAWIO".equals(json.path("technology").asText()))throw new IllegalStateException("MXGRAPH_DRAWIO technology marker is required");
                    if(json.path("editorDefinitionId").asText().isBlank())throw new IllegalStateException("Editor definition provenance is required");
                    TechnologyProjectValidationSupport.json(TechnologyProjectValidationSupport.requireSuffix(root,"/resources/hydra/runtime-activation.json","runtime activation descriptor"));
                    TechnologyProjectValidationSupport.json(TechnologyProjectValidationSupport.requireSuffix(root,"/resources/hydra/editor_definition.json","canonical editor definition"));
                }),
                TechnologyProjectValidationSupport.check("mxgraph-smoke", "Check generated mxGraph smoke tests", () -> {
                    TechnologyProjectValidationSupport.requireSuffix(root,"/tests/smoke.mjs","mxGraph runtime smoke test");
                    TechnologyProjectValidationSupport.requireSuffix(root,"/tests/drawio-smoke.mjs","draw.io plug-in smoke test");
                })
        );
    }
}
