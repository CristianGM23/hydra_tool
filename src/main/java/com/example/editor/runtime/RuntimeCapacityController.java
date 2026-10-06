package com.example.editor.runtime;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.editor.audit.DefaultAuditEventPublisher;
import com.example.editor.definition.runtime.session.EditorSessionPendingEventBuffer;
import com.example.editor.definition.runtime.session.EditorSessionWorkingCopyStore;
import com.example.editor.integration.verification.ExternalToolProcessRunner;
import com.example.editor.metamodel.importing.MetamodelImportAnalysisJobService;
import com.example.editor.metamodel.importing.python.PythonProcessAstAnalyzer;
import com.example.editor.remote.storage.RemoteStorageService;
import com.example.editor.runtime.job.HydraJobOrchestrator;

/** Read-only operational view of bounded queues, jobs and process/transfer capacity. */
@RestController
@RequestMapping("/api/runtime-capacity")
public class RuntimeCapacityController {
    private final MetamodelImportAnalysisJobService importJobs;
    private final ExternalToolProcessRunner externalTools;
    private final PythonProcessAstAnalyzer pythonAst;
    private final RemoteStorageService remoteStorage;
    private final DefaultAuditEventPublisher audit;
    private final EditorSessionPendingEventBuffer pendingEvents;
    private final EditorSessionWorkingCopyStore workingCopies;
    private final HydraJobOrchestrator jobs;

    /** Descripción: Inicializa una instancia de RuntimeCapacityController con sus dependencias y datos de entrada. Entradas: importJobs: dato de entrada necesario para la operación; externalTools: dato de entrada necesario para la operación; pythonAst: dato de entrada necesario para la operación; remoteStorage: dato de entrada necesario para la operación; audit: dato de entrada necesario para la operación; pendingEvents: dato de entrada necesario para la operación; workingCopies: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public RuntimeCapacityController(MetamodelImportAnalysisJobService importJobs, ExternalToolProcessRunner externalTools,
            PythonProcessAstAnalyzer pythonAst, RemoteStorageService remoteStorage, DefaultAuditEventPublisher audit,
            EditorSessionPendingEventBuffer pendingEvents, EditorSessionWorkingCopyStore workingCopies, HydraJobOrchestrator jobs) {
        this.importJobs=importJobs; this.externalTools=externalTools; this.pythonAst=pythonAst; this.remoteStorage=remoteStorage;
        this.audit=audit; this.pendingEvents=pendingEvents; this.workingCopies=workingCopies; this.jobs=jobs;
    }

    @GetMapping
    /** Descripción: Ejecuta la operación «capacity» correspondiente a la responsabilidad de RuntimeCapacityController. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public Map<String,Object> capacity() {
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("jobs", jobs.capacityStatus());
        result.put("metamodelImportJobs",importJobs.capacityStatus());
        result.put("externalToolProcesses",externalTools.capacityStatus());
        result.put("pythonAstProcesses",pythonAst.capacityStatus());
        result.put("remoteTransfers",remoteStorage.capacityStatus());
        result.put("auditQueue",audit.status());
        result.put("editorPendingEvents",pendingEvents.capacityStatus());
        result.put("editorWorkingCopies",workingCopies.capacityStatus());
        return Map.copyOf(result);
    }
}
