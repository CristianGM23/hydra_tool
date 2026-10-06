package com.example.editor.grammar.web;

import java.net.URI;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.example.editor.grammar.application.*;
import com.example.editor.grammar.core.*;
import com.example.editor.grammar.provider.GrammarProviderDescriptor;
import com.example.editor.grammar.provider.GrammarProviderRegistry;
import com.example.editor.grammar.runtime.GrammarProviderHealthService;
import com.example.editor.grammar.visualization.GrammarClassDiagramProjection;
import com.example.editor.grammar.visualization.GrammarClassDiagramProjectionService;

/** REST surface for editable grammars and live analysis. */
@RestController
@RequestMapping("/api/grammars")
public class GrammarController {
    private final GrammarApplicationService grammars;
    private final GrammarAnalysisService analysis;
    private final GrammarToolchainProfileService profiles;
    private final GrammarProviderRegistry providers;
    private final GrammarProviderHealthService providerHealth;
    private final GrammarClassDiagramProjectionService classDiagrams;
    private final GrammarPlatformConversionService conversions;

    /** Descripción: Inicializa una instancia de GrammarController con sus dependencias y datos de entrada. Entradas: grammars: dato de entrada necesario para la operación; analysis: dato de entrada necesario para la operación; profiles: archivo o contenido que debe procesarse; providers: dato de entrada necesario para la operación; providerHealth: dato de entrada necesario para la operación; classDiagrams: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public GrammarController(GrammarApplicationService grammars,GrammarAnalysisService analysis,
            GrammarToolchainProfileService profiles,GrammarProviderRegistry providers,GrammarProviderHealthService providerHealth,
            GrammarClassDiagramProjectionService classDiagrams,GrammarPlatformConversionService conversions){
        this.grammars=grammars;this.analysis=analysis;this.profiles=profiles;this.providers=providers;this.providerHealth=providerHealth;this.classDiagrams=classDiagrams;this.conversions=conversions;
    }
    @PostMapping("/analyze-draft") public GrammarAnalysisResult analyzeDraft(@RequestBody GrammarDraftAnalysisRequest request){return analysis.analyzeDraft(request.grammar(),request.analysis());}
    @PostMapping("/class-diagram-draft") public GrammarClassDiagramProjection classDiagramDraft(@RequestBody GrammarDraftAnalysisRequest request){GrammarAnalysisResult result=analysis.analyzeDraft(request.grammar(),request.analysis());return classDiagrams.project(request.grammar(),result);}
    /** Descripción: Prepara una conversión de plataforma sin persistir ni mutar el borrador. Entradas: request: gramática actual y tecnología destino. Salida: devuelve el borrador convertido, evidencias, advertencias y la trazabilidad; Apply solo es seguro cuando semanticallyEquivalent es true. */ @PostMapping("/conversion-preview") public GrammarPlatformConversionPreview conversionPreview(@RequestBody GrammarPlatformConversionRequest request){return conversions.prepare(request);}
    @PostMapping("/analysis/{requestId}/cancel") public Map<String,Boolean> cancel(@PathVariable String requestId){return Map.of("cancelled",analysis.cancel(requestId));}
    @GetMapping("/analysis/status") public Map<String,Object> analysisStatus(){return Map.of("cache",analysis.cacheStatistics(),"capacity",analysis.capacityStatus());}
    /** Descripción: Obtiene la información correspondiente a «list» dentro de GrammarController. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @GetMapping public List<GrammarSummary> list(){return grammars.list();}
    /** Descripción: Crea o prepara el resultado de «create» dentro de GrammarController. Entradas: grammar: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ @PostMapping public ResponseEntity<GrammarSaveResponse> create(@RequestBody GrammarDefinitionModel grammar){GrammarSaveResponse result=grammars.create(grammar);return ResponseEntity.created(URI.create("/api/grammars/"+grammar.id())).header(HttpHeaders.ETAG,result.etag()).body(result);}
    @GetMapping("/{grammarId}") public ResponseEntity<GrammarDefinitionModel> find(@PathVariable String grammarId){GrammarLoadResponse result=grammars.find(grammarId);return ResponseEntity.ok().header(HttpHeaders.ETAG,result.etag()).body(result.grammar());}
    @PutMapping("/{grammarId}") public ResponseEntity<GrammarSaveResponse> replace(@PathVariable String grammarId,@RequestHeader(value=HttpHeaders.IF_MATCH,required=false)String etag,@RequestBody GrammarDefinitionModel grammar){GrammarSaveResponse result=grammars.replace(grammarId,grammar,etag);return ResponseEntity.ok().header(HttpHeaders.ETAG,result.etag()).body(result);}
    @DeleteMapping("/{grammarId}") public ResponseEntity<Void> delete(@PathVariable String grammarId,@RequestHeader(value=HttpHeaders.IF_MATCH,required=false)String etag){grammars.delete(grammarId,etag);return ResponseEntity.noContent().build();}
    @PostMapping("/{grammarId}/analyze") public GrammarAnalysisResult analyze(@PathVariable String grammarId,@RequestBody GrammarAnalysisRequest request){return analysis.analyze(grammarId,request);}
    @PostMapping("/{grammarId}/class-diagram") public GrammarClassDiagramProjection classDiagram(@PathVariable String grammarId,@RequestBody GrammarAnalysisRequest request){GrammarLoadResponse loaded=grammars.find(grammarId);GrammarAnalysisResult result=analysis.analyze(grammarId,request);List<GrammarSourceDocument> documents=request==null||request.documents().isEmpty()?loaded.grammar().documents():request.documents();return classDiagrams.project(loaded.grammar(),documents,result);}
    @PostMapping("/{grammarId}/code-actions") public List<com.example.editor.grammar.diagnostic.GrammarCodeAction> codeActions(@PathVariable String grammarId,@RequestBody GrammarAnalysisRequest request){return analysis.analyze(grammarId,request).codeActions();}
    @GetMapping("/toolchain-profiles") public List<GrammarToolchainProfile> profiles(){return profiles.list();}
    @GetMapping("/providers") public List<GrammarProviderDescriptor> providers(){return providers.descriptors();}
    @PostMapping("/providers/{providerId}/reset") public ResponseEntity<Void> resetProvider(@PathVariable String providerId){providerHealth.resetProvider(providerId);return ResponseEntity.noContent().build();}
}
