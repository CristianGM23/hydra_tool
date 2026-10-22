package com.example.editor.textual.web;

import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.example.editor.textual.interaction.*;
import com.example.editor.textual.runtime.*;
import com.example.editor.web.ApiExceptionHandler;
import com.example.editor.validation.quickfix.QuickFixDescriptor;
import com.example.editor.validation.quickfix.QuickFixExecutionRequest;

@RestController
@RequestMapping("/api/textual-model-sessions")
public class TextualModelSessionController {
    private final TextualModelSessionService service;
    private final TextualQuickFixRuntimeService quickFixes;
    private final TextualInteractionFacade interactions;

    /** Descripción: Inicializa el controlador con sesión, quick fixes e interacciones neutrales. Entradas: s, q e interactions. Salida: deja la instancia lista para atender la API textual. */
    public TextualModelSessionController(TextualModelSessionService s,TextualQuickFixRuntimeService q,TextualInteractionFacade interactions){service=s;quickFixes=q;this.interactions=interactions;}
    /** Descripción: Crea una sesión textual. Entradas: r: solicitud de creación. Salida: devuelve sesión y ETag. */
    @PostMapping public ResponseEntity<TextualModelSession>create(@RequestBody CreateTextualModelSessionRequest r){var s=service.create(r);return ResponseEntity.ok().header(HttpHeaders.ETAG,s.etag()).body(s.document());}
    /** Descripción: Obtiene una sesión textual. Entradas: id: identificador de sesión. Salida: devuelve sesión y ETag. */
    @GetMapping("/{id}")public ResponseEntity<TextualModelSession>find(@PathVariable String id){var s=service.find(id);return ResponseEntity.ok().header(HttpHeaders.ETAG,s.etag()).body(s.document());}
    /** Descripción: Analiza una revisión textual. Entradas: id, e y r. Salida: devuelve la sesión actualizada y ETag. */
    @PostMapping("/{id}/parse")public ResponseEntity<TextualModelSession>parse(@PathVariable String id,@RequestHeader(value=HttpHeaders.IF_MATCH,required=false)String e,@RequestBody ParseTextualModelRequest r){var s=service.parse(id,r,e);return ResponseEntity.ok().header(HttpHeaders.ETAG,s.etag()).body(s.document());}
    /** Descripción: Persiste el last-known-good vigente. Entradas: id y e. Salida: devuelve la sesión confirmada y ETag. */
    @PostMapping("/{id}/save")public ResponseEntity<TextualModelSession>save(@PathVariable String id,@RequestHeader(value=HttpHeaders.IF_MATCH,required=false)String e){var s=service.save(id,e);return ResponseEntity.ok().header(HttpHeaders.ETAG,s.etag()).body(s.document());}
    /** Descripción: Busca semántica bajo una posición. Entradas: id, documentId, line y column. Salida: devuelve el binding encontrado. */
    @GetMapping("/{id}/semantic-at")public TextualModelSessionService.TextualSemanticLookupResult semanticAt(@PathVariable String id,@RequestParam String documentId,@RequestParam int line,@RequestParam int column){return service.semanticAt(id,documentId,line,column);}
    /** Descripción: Ejecuta validación compartida sobre la sesión. Entradas: id. Salida: devuelve diagnósticos visibles. */
    @PostMapping("/{id}/validation")public List<TextualValidationDiagnosticView>validate(@PathVariable String id){return quickFixes.validate(id);}
    /** Descripción: Resuelve quick fixes para un diagnóstico. Entradas: id y diagnosticId. Salida: devuelve acciones compatibles. */
    @GetMapping("/{id}/validation/{diagnosticId}/quick-fixes")public List<QuickFixDescriptor>quickFixes(@PathVariable String id,@PathVariable String diagnosticId){return quickFixes.resolve(id,diagnosticId);}
    /** Descripción: Previsualiza un quick fix sin persistir cambios. Entradas: id, diagnosticId, actionId y r. Salida: devuelve la previsualización. */
    @PostMapping("/{id}/validation/{diagnosticId}/quick-fixes/{actionId}/preview")public TextualQuickFixPreviewResult previewQuickFix(@PathVariable String id,@PathVariable String diagnosticId,@PathVariable String actionId,@RequestBody(required=false)QuickFixExecutionRequest r){return quickFixes.preview(id,diagnosticId,actionId,r);}
    /** Descripción: Aplica un quick fix con control ETag. Entradas: id, diagnosticId, actionId, e y r. Salida: devuelve aplicación y ETag. */
    @PostMapping("/{id}/validation/{diagnosticId}/quick-fixes/{actionId}/apply")public ResponseEntity<TextualQuickFixApplicationResult>applyQuickFix(@PathVariable String id,@PathVariable String diagnosticId,@PathVariable String actionId,@RequestHeader(value=HttpHeaders.IF_MATCH,required=false)String e,@RequestBody(required=false)QuickFixExecutionRequest r){var result=quickFixes.apply(id,diagnosticId,actionId,r,e);return ResponseEntity.ok().header(HttpHeaders.ETAG,result.etag()).body(result);}

    /** Descripción: Publica la matriz real de capacidades de edición neutral. Entradas: id. Salida: devuelve estados por capacidad sin fingir runtime nativo embebido. */
    @GetMapping("/{id}/interactions/capabilities")public List<TextualInteractionCapabilityState>interactionCapabilities(@PathVariable String id){return interactions.capabilities(id);}
    /** Descripción: Obtiene completion acotado sobre last-known-good. Entradas: id, documentId, line, column, prefix y limit. Salida: devuelve propuestas deterministas. */
    @GetMapping("/{id}/interactions/completion")public List<TextualCompletionItem>completion(@PathVariable String id,@RequestParam String documentId,@RequestParam int line,@RequestParam int column,@RequestParam(defaultValue="")String prefix,@RequestParam(defaultValue="100")int limit){return interactions.completion(id,new TextualInteractionRequest(documentId,line,column,prefix,limit));}
    /** Descripción: Obtiene hover contextual sobre last-known-good. Entradas: id, documentId, line y column. Salida: devuelve información semántica contextual. */
    @GetMapping("/{id}/interactions/hover")public TextualHoverInfo hover(@PathVariable String id,@RequestParam String documentId,@RequestParam int line,@RequestParam int column){return interactions.hover(id,new TextualInteractionRequest(documentId,line,column,"",100));}
    /** Descripción: Resuelve go-to-definition de forma neutral. Entradas: id, documentId, line y column. Salida: devuelve localización o null. */
    @GetMapping("/{id}/interactions/definition")public TextualNavigationLocation definition(@PathVariable String id,@RequestParam String documentId,@RequestParam int line,@RequestParam int column){return interactions.definition(id,new TextualInteractionRequest(documentId,line,column,"",100));}
    /** Descripción: Enumera referencias semánticas acotadas. Entradas: id, documentId, line y column. Salida: devuelve localizaciones de uso. */
    @GetMapping("/{id}/interactions/references")public List<TextualNavigationLocation>references(@PathVariable String id,@RequestParam String documentId,@RequestParam int line,@RequestParam int column){return interactions.references(id,new TextualInteractionRequest(documentId,line,column,"",1000));}
    /** Descripción: Previsualiza un rename semántico coordinado. Entradas: id y request. Salida: devuelve plan preview-first sin persistir cambios. */
    @PostMapping("/{id}/interactions/rename/preview")public TextualRenamePlan renamePreview(@PathVariable String id,@RequestBody TextualRenameRequest request){return interactions.rename(id,request);}
    /** Descripción: Calcula formato determinista para un documento. Entradas: id y documentId. Salida: devuelve ediciones sin aplicarlas. */
    @PostMapping("/{id}/interactions/format")public List<com.example.editor.grammar.diagnostic.GrammarTextEdit>format(@PathVariable String id,@RequestParam String documentId){return interactions.format(id,documentId);}
    /** Descripción: Obtiene símbolos semánticos de un documento. Entradas: id y documentId. Salida: devuelve símbolos ordenados. */
    @GetMapping("/{id}/interactions/symbols")public List<TextualDocumentSymbol>symbols(@PathVariable String id,@RequestParam String documentId){return interactions.symbols(id,documentId);}
    /** Descripción: Obtiene regiones plegables semánticas. Entradas: id y documentId. Salida: devuelve rangos de folding. */
    @GetMapping("/{id}/interactions/folding")public List<TextualFoldingRange>folding(@PathVariable String id,@RequestParam String documentId){return interactions.folding(id,documentId);}
    /** Descripción: Obtiene tokens semánticos neutrales para Monaco/LSP. Entradas: id y documentId. Salida: devuelve tokens acotados. */
    @GetMapping("/{id}/interactions/semantic-tokens")public List<TextualSemanticToken>semanticTokens(@PathVariable String id,@RequestParam String documentId){return interactions.tokens(id,documentId);}
    /** Descripción: Calcula el cierre de invalidación incremental del workspace. Entradas: id y changedDocuments. Salida: devuelve plan de reanálisis transitivo. */
    @PostMapping("/{id}/interactions/invalidation")public TextualDependencyInvalidationPlan invalidation(@PathVariable String id,@RequestBody List<String>changedDocuments){return interactions.invalidation(id,changedDocuments);}
    /** Descripción: Evalúa compatibilidad entre snapshots de gramática. Entradas: id y request. Salida: devuelve política de reutilización, reparse o migración. */
    @PostMapping("/{id}/interactions/snapshot-compatibility")public TextualSnapshotCompatibilityReport snapshotCompatibility(@PathVariable String id,@RequestBody TextualSnapshotCompatibilityRequest request){interactions.capabilities(id);return interactions.snapshotCompatibility(request);}
    /** Descripción: Traduce a un conflicto de cliente (409) las consultas, quick fixes y guardados que exigen un borrador válido cuando el actual está roto; solo aplica a este controlador y no altera el manejador global. Entradas: exception: rechazo del borrador no listo. Salida: respuesta 409 con código CONFLICT y el mensaje del rechazo. */
    @ExceptionHandler(TextualDraftNotReadyException.class)public ResponseEntity<ApiExceptionHandler.ApiError>draftNotReady(TextualDraftNotReadyException exception){return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiExceptionHandler.ApiError("CONFLICT",exception.getMessage()));}
}
