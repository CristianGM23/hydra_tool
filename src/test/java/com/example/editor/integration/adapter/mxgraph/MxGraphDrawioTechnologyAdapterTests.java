package com.example.editor.integration.adapter.mxgraph;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import com.example.editor.integration.compiler.TechnologyAdapterTestFixtures;
import com.example.editor.integration.compiler.TechnologyCompilationResult;

class MxGraphDrawioTechnologyAdapterTests {
    private final MxGraphDrawioTechnologyAdapter adapter = new MxGraphDrawioTechnologyAdapter(TechnologyAdapterTestFixtures.MAPPER);

    @Test void generatesPortableRuntimePluginAndRepresentationPalette(){
        TechnologyCompilationResult result=adapter.compile(TechnologyAdapterTestFixtures.mxgraphDrawioRequest());
        assertThat(paths(result)).anyMatch(path->path.endsWith("src/hydra-mxgraph-runtime.js"))
                .anyMatch(path->path.endsWith("drawio/hydra-drawio-plugin.js"))
                .anyMatch(path->path.endsWith("drawio/hydra-palette.xml"))
                .anyMatch(path->path.contains("drawio/hydra-palette-representation-main.xml"))
                .anyMatch(path->path.endsWith("resources/hydra-mxgraph-manifest.json"))
                .anyMatch(path->path.endsWith("tests/drawio-smoke.mjs"));
        assertThat(text(result,"src/hydra-mxgraph-runtime.js")).contains("paletteEntries","createNode","createEdge","applyQuickFix","executeEventFlow","hydraRepresentationId")
                .doesNotContain("eval(","new Function");
        assertThat(text(result,"drawio/hydra-drawio-plugin.js")).contains("Draw.loadPlugin","addPaletteFunctions","runtime.toggle","runtime.invokeTool");
        assertThat(text(result,"drawio/hydra-palette.xml")).contains("representation:main","class:Person","tool:create-person","hydraMetamodelId","people");
    }

    @Test void keepsGeneratedPackageDeterministic(){
        assertThat(adapter.compile(TechnologyAdapterTestFixtures.mxgraphDrawioRequest()).files()).usingRecursiveComparison()
                .isEqualTo(adapter.compile(TechnologyAdapterTestFixtures.mxgraphDrawioRequest()).files());
    }

    private java.util.List<String> paths(TechnologyCompilationResult result){return result.files().stream().map(file->file.relativePath()).toList();}
    @Test void executesConditionalVisualCustomizationInMxGraphAndDrawioRuntime(){var result=adapter.compile(TechnologyAdapterTestFixtures.mxgraphDrawioRequest());String runtime=text(result,"src/hydra-mxgraph-runtime.js");assertThat(runtime).contains("resolveNodeStyle","resolveEdgeStyle","applyAppearance","evaluateExpression","conditionalStyles","maximumConditionalStyles","VISUAL_CONDITION_EVALUATION_FAILED","BEZIER","DASHED","safeStyleEntry","boundedStyleValue").doesNotContain("eval(","new Function");assertThat(text(result,"resources/hydra-mxgraph-manifest.json")).contains("conditionalStyles","failSafeConditionalFallback","maximumConditionalStyles");assertThat(text(result,"tests/drawio-smoke.mjs")).contains("evaluateExpression","conditionalStyles","BEZIER","DASHED","Unsafe base style key escaped sanitization","Style property limit was not enforced");}

    private String text(TechnologyCompilationResult result,String suffix){return result.files().stream().filter(file->file.relativePath().endsWith(suffix)).map(file->new String(file.content(),StandardCharsets.UTF_8)).findFirst().orElseThrow();}
}
