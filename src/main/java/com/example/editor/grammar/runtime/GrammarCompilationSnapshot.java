package com.example.editor.grammar.runtime;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import com.example.editor.grammar.core.*;
import com.example.editor.grammar.diagnostic.GrammarDiagnostic;
import com.example.editor.grammar.visualization.GrammarClassDiagramProjection;

/** Immutable compilation artifact. A successful snapshot is never mutated after creation. */
public record GrammarCompilationSnapshot(
        String schemaVersion,
        String id,
        String grammarId,
        long sourceRevision,
        String sourceHash,
        GrammarTechnology technology,
        String providerId,
        String providerVersion,
        String toolchainProfileId,
        String toolchainProfileHash,
        String semanticContractId,
        String semanticContractEtag,
        CompilationStatus status,
        List<GrammarDiagnostic> diagnostics,
        GrammarSemanticBindingMap semanticBindings,
        GrammarClassDiagramProjection classDiagram,
        GrammarRuntimeDescriptor runtimeDescriptor,
        Map<String,String> runtimeFiles,
        GeneratedGrammarProject generatedProject,
        List<GeneratedArtifactRef> artifacts,
        Map<String,String> checksums,
        List<GrammarCompilationStageResult> stages,
        Instant createdAt,
        Map<String,String> metadata) {
    public GrammarCompilationSnapshot {
        schemaVersion = text(schemaVersion);
        if (schemaVersion.isBlank()) schemaVersion = "1.0";
        id = required(id, "snapshot.id");
        grammarId = required(grammarId, "snapshot.grammarId");
        sourceRevision = Math.max(0,sourceRevision);
        sourceHash = required(sourceHash, "snapshot.sourceHash");
        technology = technology == null ? GrammarTechnology.LANGIUM : technology;
        providerId = required(providerId, "snapshot.providerId");
        providerVersion = text(providerVersion);
        toolchainProfileId = required(toolchainProfileId, "snapshot.toolchainProfileId");
        toolchainProfileHash = text(toolchainProfileHash);
        semanticContractId = text(semanticContractId);
        semanticContractEtag = text(semanticContractEtag);
        status = status == null ? CompilationStatus.FAILED : status;
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
        semanticBindings = semanticBindings == null ? GrammarSemanticBindingMap.empty() : semanticBindings;
        classDiagram = classDiagram == null ? GrammarClassDiagramProjection.empty(grammarId,sourceRevision,sourceHash) : classDiagram;
        if(status == CompilationStatus.SUCCESS) {
            if(!grammarId.equals(classDiagram.grammarId())) throw new IllegalArgumentException("Successful snapshot class diagram belongs to another grammar");
            if(!sourceHash.equals(classDiagram.sourceHash())) throw new IllegalArgumentException("Successful snapshot class diagram sourceHash differs from frozen source");
        }
        runtimeDescriptor = runtimeDescriptor == null ? new GrammarRuntimeDescriptor("","",List.of(),"","",Map.of()) : runtimeDescriptor;
        runtimeFiles = runtimeFiles == null ? Map.of() : Map.copyOf(runtimeFiles);
        generatedProject = generatedProject == null ? new GeneratedGrammarProject("",Map.of(),Map.of()) : generatedProject;
        artifacts = artifacts == null ? List.of() : List.copyOf(artifacts);
        checksums = checksums == null ? Map.of() : Map.copyOf(checksums);
        stages = stages == null ? List.of() : List.copyOf(stages);
        createdAt = createdAt == null ? Instant.now() : createdAt;
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        if (status == CompilationStatus.SUCCESS && stages.stream().anyMatch(stage -> !stage.successful())) {
            throw new IllegalArgumentException("A SUCCESS grammar snapshot cannot contain a failed compilation stage");
        }
    }

    /** Compatibility constructor for the initial V4.32.97 snapshot shape. */
    /** Descripción: Inicializa una instancia de GrammarCompilationSnapshot con sus dependencias y datos de entrada. Entradas: id: identificador que delimita el recurso objetivo; grammarId: identificador que delimita el recurso objetivo; sourceRevision: dato de entrada necesario para la operación; sourceHash: dato de entrada necesario para la operación; technology: dato de entrada necesario para la operación; providerId: identificador que delimita el recurso objetivo; toolchainProfileId: identificador que delimita el recurso objetivo; status: dato de entrada necesario para la operación; diagnostics: dato de entrada necesario para la operación; semanticBindings: dato de entrada necesario para la operación; runtimeDescriptor: dato de entrada necesario para la operación; generatedProject: dato de entrada necesario para la operación; checksums: dato de entrada necesario para la operación; createdAt: dato de entrada necesario para la operación; metadata: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public GrammarCompilationSnapshot(String id, String grammarId, long sourceRevision, String sourceHash, GrammarTechnology technology,
            String providerId, String toolchainProfileId, CompilationStatus status, List<GrammarDiagnostic> diagnostics,
            GrammarSemanticBindingMap semanticBindings, GrammarRuntimeDescriptor runtimeDescriptor,
            GeneratedGrammarProject generatedProject, Map<String,String> checksums, Instant createdAt, Map<String,String> metadata) {
        this("1.0", id, grammarId, sourceRevision, sourceHash, technology, providerId, "", toolchainProfileId, "", "", "",
                status, diagnostics, semanticBindings, GrammarClassDiagramProjection.empty(grammarId,sourceRevision,sourceHash), runtimeDescriptor, Map.of(), generatedProject, List.of(), checksums, List.of(), createdAt, metadata);
    }

    /** Compatibility constructor for snapshots serialized/constructed before runtime files were retained. */
    /** Descripción: Inicializa una instancia de GrammarCompilationSnapshot con sus dependencias y datos de entrada. Entradas: schemaVersion: dato de entrada necesario para la operación; id: identificador que delimita el recurso objetivo; grammarId: identificador que delimita el recurso objetivo; sourceRevision: dato de entrada necesario para la operación; sourceHash: dato de entrada necesario para la operación; technology: dato de entrada necesario para la operación; providerId: identificador que delimita el recurso objetivo; providerVersion: dato de entrada necesario para la operación; toolchainProfileId: identificador que delimita el recurso objetivo; toolchainProfileHash: archivo o contenido que debe procesarse; semanticContractId: identificador que delimita el recurso objetivo; semanticContractEtag: dato de entrada necesario para la operación; status: dato de entrada necesario para la operación; diagnostics: dato de entrada necesario para la operación; semanticBindings: dato de entrada necesario para la operación; runtimeDescriptor: dato de entrada necesario para la operación; generatedProject: dato de entrada necesario para la operación; artifacts: dato de entrada necesario para la operación; checksums: dato de entrada necesario para la operación; stages: dato de entrada necesario para la operación; createdAt: dato de entrada necesario para la operación; metadata: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public GrammarCompilationSnapshot(String schemaVersion, String id, String grammarId, long sourceRevision, String sourceHash,
            GrammarTechnology technology, String providerId, String providerVersion, String toolchainProfileId,
            String toolchainProfileHash, String semanticContractId, String semanticContractEtag, CompilationStatus status,
            List<GrammarDiagnostic> diagnostics, GrammarSemanticBindingMap semanticBindings, GrammarRuntimeDescriptor runtimeDescriptor,
            GeneratedGrammarProject generatedProject, List<GeneratedArtifactRef> artifacts, Map<String,String> checksums,
            List<GrammarCompilationStageResult> stages, Instant createdAt, Map<String,String> metadata) {
        this(schemaVersion, id, grammarId, sourceRevision, sourceHash, technology, providerId, providerVersion, toolchainProfileId,
                toolchainProfileHash, semanticContractId, semanticContractEtag, status, diagnostics, semanticBindings,
                GrammarClassDiagramProjection.empty(grammarId,sourceRevision,sourceHash), runtimeDescriptor,
                Map.of(), generatedProject, artifacts, checksums, stages, createdAt, metadata);
    }

    /** Compatibility constructor for snapshots constructed after runtimeFiles were added but before class-diagram snapshots. */
    /** Descripción: Inicializa una instancia de GrammarCompilationSnapshot con sus dependencias y datos de entrada. Entradas: schemaVersion: dato de entrada necesario para la operación; id: identificador que delimita el recurso objetivo; grammarId: identificador que delimita el recurso objetivo; sourceRevision: dato de entrada necesario para la operación; sourceHash: dato de entrada necesario para la operación; technology: dato de entrada necesario para la operación; providerId: identificador que delimita el recurso objetivo; providerVersion: dato de entrada necesario para la operación; toolchainProfileId: identificador que delimita el recurso objetivo; toolchainProfileHash: archivo o contenido que debe procesarse; semanticContractId: identificador que delimita el recurso objetivo; semanticContractEtag: dato de entrada necesario para la operación; status: dato de entrada necesario para la operación; diagnostics: dato de entrada necesario para la operación; semanticBindings: dato de entrada necesario para la operación; runtimeDescriptor: dato de entrada necesario para la operación; runtimeFiles: archivo o contenido que debe procesarse; generatedProject: dato de entrada necesario para la operación; artifacts: dato de entrada necesario para la operación; checksums: dato de entrada necesario para la operación; stages: dato de entrada necesario para la operación; createdAt: dato de entrada necesario para la operación; metadata: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public GrammarCompilationSnapshot(String schemaVersion, String id, String grammarId, long sourceRevision, String sourceHash,
            GrammarTechnology technology, String providerId, String providerVersion, String toolchainProfileId,
            String toolchainProfileHash, String semanticContractId, String semanticContractEtag, CompilationStatus status,
            List<GrammarDiagnostic> diagnostics, GrammarSemanticBindingMap semanticBindings, GrammarRuntimeDescriptor runtimeDescriptor,
            Map<String,String> runtimeFiles, GeneratedGrammarProject generatedProject, List<GeneratedArtifactRef> artifacts,
            Map<String,String> checksums, List<GrammarCompilationStageResult> stages, Instant createdAt, Map<String,String> metadata) {
        this(schemaVersion,id,grammarId,sourceRevision,sourceHash,technology,providerId,providerVersion,toolchainProfileId,
                toolchainProfileHash,semanticContractId,semanticContractEtag,status,diagnostics,semanticBindings,
                GrammarClassDiagramProjection.empty(grammarId,sourceRevision,sourceHash),runtimeDescriptor,runtimeFiles,generatedProject,
                artifacts,checksums,stages,createdAt,metadata);
    }

    /** Descripción: Ejecuta la operación «successful» correspondiente a la responsabilidad de GrammarCompilationSnapshot. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean successful(){return status==CompilationStatus.SUCCESS;}
    /** Descripción: Ejecuta la operación «immutable» correspondiente a la responsabilidad de GrammarCompilationSnapshot. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean immutable(){return "true".equalsIgnoreCase(metadata.getOrDefault("immutable", "true"));}
    /** Descripción: Ejecuta la operación «text» correspondiente a la responsabilidad de GrammarCompilationSnapshot. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String text(String value){return value==null?"":value.trim();}
    /** Descripción: Valida las condiciones necesarias para «required» y rechaza entradas no válidas. Entradas: value: valor de entrada que debe procesarse; field: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String required(String value, String field){String result=text(value);if(result.isBlank())throw new IllegalArgumentException(field+" must not be blank");return result;}
}
