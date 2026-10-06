package com.example.editor.grammar.web;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.example.editor.grammar.application.GrammarCompilationService;
import com.example.editor.grammar.application.GrammarCompilationProgressService;
import com.example.editor.grammar.application.GrammarGeneratedProjectArchiveService;
import com.example.editor.grammar.core.GrammarCompilationRequest;
import com.example.editor.grammar.runtime.GrammarActivationState;
import com.example.editor.grammar.runtime.GrammarCompilationSnapshot;
import com.example.editor.grammar.runtime.GrammarCompilationProgress;
import com.example.editor.runtime.job.HydraJobExecutionService;
import com.example.editor.runtime.job.HydraJobSnapshot;

@RestController
public class GrammarCompilationController {
    private static final MediaType ZIP = new MediaType("application", "zip", StandardCharsets.UTF_8);
    private final GrammarCompilationService service;
    private final GrammarGeneratedProjectArchiveService archives;
    private final GrammarCompilationProgressService progress;
    private final HydraJobExecutionService jobs;

    /**
     * Descripción: Inicializa el controlador de compilaciones de gramáticas y descarga de proyectos generados.
     * Entradas: service: servicio de snapshots/activación; archives: empaquetador seguro del proyecto generado.
     * Salida: deja el controlador preparado para compilación, consulta, activación y descarga.
     */
    /** Descripción: Inicializa una instancia de GrammarCompilationController con compilación, archivos, progreso y el gestor transversal de trabajos. Entradas: service: servicio de compilación; archives: empaquetador; progress: progreso de compilación; jobs: ejecutor global acotado. Salida: deja el controlador preparado. */
    public GrammarCompilationController(GrammarCompilationService service, GrammarGeneratedProjectArchiveService archives,
            GrammarCompilationProgressService progress, HydraJobExecutionService jobs) {
        this.service = service;
        this.archives = archives;
        this.progress = progress;
        this.jobs = jobs;
    }

    @PostMapping("/api/grammars/{grammarId}/compile")
    /** Descripción: Compila una revisión congelada de la gramática. Entradas: grammarId: gramática objetivo; request: revisión, documentos y perfil. Salida: devuelve el snapshot inmutable resultante. */
    /** Descripción: Ejecuta la operación «compile» correspondiente a la responsabilidad de GrammarCompilationController. Entradas: grammarId: identificador que delimita el recurso objetivo; request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public GrammarCompilationSnapshot compile(@PathVariable String grammarId, @RequestBody(required = false) GrammarCompilationRequest request) {
        return service.compile(grammarId, request);
    }

    @PostMapping("/api/grammars/{grammarId}/compile/jobs")
    /** Descripción: Encola una compilación de gramática en el executor transversal sin bloquear la petición HTTP. Entradas: grammarId: gramática; request: revisión a compilar; correlationId: correlación opcional. Salida: trabajo global registrado; el snapshot compilado queda disponible mediante los endpoints de compilaciones. */
    public HydraJobSnapshot compileAsync(@PathVariable String grammarId, @RequestBody(required = false) GrammarCompilationRequest request,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
        return jobs.submit("GRAMMAR_COMPILE", "Compile grammar " + grammarId, correlationId, context -> {
            context.progress(5, "VALIDATING", "Validating grammar compilation request");
            context.throwIfCancellationRequested();
            GrammarCompilationSnapshot snapshot = service.compile(grammarId, request);
            context.progress(95, "PERSISTED", "Grammar compilation snapshot persisted: " + snapshot.id());
        });
    }

    @GetMapping("/api/grammars/{grammarId}/compilations")
    /** Descripción: Enumera los snapshots de una gramática. Entradas: grammarId: gramática objetivo. Salida: devuelve snapshots persistidos. */
    /** Descripción: Obtiene la información correspondiente a «list» dentro de GrammarCompilationController. Entradas: grammarId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public List<GrammarCompilationSnapshot> list(@PathVariable String grammarId) {
        return service.list(grammarId);
    }

    @GetMapping("/api/grammars/{grammarId}/active-compilation")
    /** Descripción: Obtiene el snapshot activo de una gramática. Entradas: grammarId: gramática objetivo. Salida: 200 con snapshot o 204 si aún no existe uno activo. */
    /** Descripción: Ejecuta la operación «active» correspondiente a la responsabilidad de GrammarCompilationController. Entradas: grammarId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public ResponseEntity<GrammarCompilationSnapshot> active(@PathVariable String grammarId) {
        return service.active(grammarId).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/api/grammars/{grammarId}/compile-progress/{requestId}")
    /** Descripción: Ejecuta la operación «progress» correspondiente a la responsabilidad de GrammarCompilationController. Entradas: grammarId: identificador que delimita el recurso objetivo; requestId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public ResponseEntity<GrammarCompilationProgress> progress(@PathVariable String grammarId,@PathVariable String requestId) {
        return progress.find(requestId).filter(value->grammarId.equals(value.grammarId())).map(ResponseEntity::ok).orElseGet(()->ResponseEntity.noContent().build());
    }

    @GetMapping("/api/grammar-compilations/{snapshotId}")
    /** Descripción: Obtiene un snapshot por su ID estable. Entradas: snapshotId: identificador de compilación. Salida: devuelve el snapshot solicitado. */
    /** Descripción: Obtiene la información correspondiente a «find» dentro de GrammarCompilationController. Entradas: snapshotId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public GrammarCompilationSnapshot find(@PathVariable String snapshotId) {
        return service.find(snapshotId);
    }

    @PostMapping("/api/grammar-compilations/{snapshotId}/activate")
    /** Descripción: Activa un snapshot SUCCESS. Entradas: snapshotId: identificador de compilación. Salida: devuelve el estado de activación resultante. */
    /** Descripción: Ejecuta la operación «activate» correspondiente a la responsabilidad de GrammarCompilationController. Entradas: snapshotId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public GrammarActivationState activate(@PathVariable String snapshotId) {
        return service.activate(snapshotId);
    }

    @GetMapping(value = "/api/grammar-compilations/{snapshotId}/generated-project.zip", produces = "application/zip")
    /**
     * Descripción: Descarga el proyecto de servicios de lenguaje generado por el provider para un snapshot correcto.
     * Entradas: snapshotId: snapshot SUCCESS cuyo GeneratedGrammarProject se quiere materializar.
     * Salida: devuelve un ZIP determinista con las fuentes/configuración de parser, LSP, worker, CLI y pruebas que soporte el provider.
     */
    /** Descripción: Crea o prepara el resultado de «generatedProject» dentro de GrammarCompilationController. Entradas: snapshotId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public ResponseEntity<byte[]> generatedProject(@PathVariable String snapshotId) {
        GrammarCompilationSnapshot snapshot = service.find(snapshotId);
        if (!snapshot.successful()) {
            throw new IllegalArgumentException("Only a successful grammar snapshot can export a generated project");
        }
        byte[] archive = archives.archive(snapshot.generatedProject());
        String fileName = portableFileName(snapshot.grammarId()) + "-" + portableFileName(snapshot.id()) + "-language-services.zip";
        return ResponseEntity.ok()
                .contentType(ZIP)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .contentLength(archive.length)
                .body(archive);
    }

    /**
     * Descripción: Convierte identificadores técnicos en un nombre de archivo portable sin permitir separadores ni caracteres de control.
     * Entradas: value: ID de gramática o snapshot.
     * Salida: devuelve un componente seguro para Content-Disposition.
     */
    /** Descripción: Ejecuta la operación «portableFileName» correspondiente a la responsabilidad de GrammarCompilationController. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String portableFileName(String value) {
        String safe = value == null ? "grammar" : value.replaceAll("[^A-Za-z0-9._-]+", "-");
        return safe.isBlank() ? "grammar" : safe;
    }
}
