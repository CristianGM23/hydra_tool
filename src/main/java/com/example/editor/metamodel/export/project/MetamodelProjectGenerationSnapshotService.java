package com.example.editor.metamodel.export.project;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import com.example.editor.integration.projection.CanonicalArtifactContent;
import com.example.editor.metamodel.validation.MetamodelValidationGateStatus;

/** Calcula una huella estable del metamodelo, capas, selección y configuración que entran en cada ZIP. */
@Component
public class MetamodelProjectGenerationSnapshotService {
    /** Descripción: Crea o prepara el resultado de «create» dentro de MetamodelProjectGenerationSnapshotService. Entradas: metamodelId: identificador que delimita el recurso objetivo; kind: dato de entrada necesario para la operación; options: configuración que gobierna el comportamiento; artifacts: dato de entrada necesario para la operación; validation: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public MetamodelProjectGenerationSnapshot create(String metamodelId, MetamodelProjectExportKind kind,
            MetamodelProjectExportOptions options, List<CanonicalArtifactContent> artifacts, MetamodelValidationGateStatus validation){
        return create(metamodelId, kind.id(), options, artifacts, validation);
    }

    /** Descripción: Crea o prepara el resultado de «create» dentro de MetamodelProjectGenerationSnapshotService. Entradas: metamodelId: identificador que delimita el recurso objetivo; projectExportId: identificador que delimita el recurso objetivo; options: configuración que gobierna el comportamiento; artifacts: dato de entrada necesario para la operación; validation: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public MetamodelProjectGenerationSnapshot create(String metamodelId, String projectExportId,
            MetamodelProjectExportOptions options, List<CanonicalArtifactContent> artifacts, MetamodelValidationGateStatus validation){
        List<MetamodelProjectGenerationArtifactSnapshot> sources = artifacts.stream()
                .map(item -> new MetamodelProjectGenerationArtifactSnapshot(item.kind().name(), item.documentId(), item.content().length, sha256(item.content())))
                .sorted(Comparator.comparing(MetamodelProjectGenerationArtifactSnapshot::kind).thenComparing(MetamodelProjectGenerationArtifactSnapshot::documentId)).toList();
        Map<String, String> settings = options.settings().entrySet().stream().sorted(Map.Entry.comparingByKey())
                .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (left, right) -> right, java.util.LinkedHashMap::new));
        String fingerprint = fingerprint(metamodelId, validation.currentFingerprint(), projectExportId, options.selectionMode().name(), options.editorId(), settings, sources);
        return new MetamodelProjectGenerationSnapshot("1.0", metamodelId, validation.currentFingerprint(), projectExportId,
                options.selectionMode().name(), options.editorId(), settings, sources, fingerprint, Instant.now());
    }

    /** Descripción: Ejecuta la operación «fingerprint» correspondiente a la responsabilidad de MetamodelProjectGenerationSnapshotService. Entradas: metamodelId: identificador que delimita el recurso objetivo; metamodelFingerprint: dato de entrada necesario para la operación; kind: dato de entrada necesario para la operación; selectionMode: dato de entrada necesario para la operación; editorId: identificador que delimita el recurso objetivo; settings: dato de entrada necesario para la operación; artifacts: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String fingerprint(String metamodelId, String metamodelFingerprint, String kind, String selectionMode,
            String editorId, Map<String, String> settings, List<MetamodelProjectGenerationArtifactSnapshot> artifacts){
        MessageDigest digest = digest();
        update(digest, "schemaVersion", "1.0"); update(digest, "metamodelId", metamodelId);
        update(digest, "metamodelFingerprint", metamodelFingerprint); update(digest, "projectExport", kind);
        update(digest, "selectionMode", selectionMode); update(digest, "editorId", editorId);
        settings.forEach((key, value) -> update(digest, "setting:" + key, value));
        for(var artifact : artifacts){update(digest, "artifact-kind", artifact.kind()); update(digest, "artifact-id", artifact.documentId()); update(digest, "artifact-sha256", artifact.sha256());}
        return HexFormat.of().formatHex(digest.digest());
    }

    /** Descripción: Ejecuta la operación «sha256» correspondiente a la responsabilidad de MetamodelProjectGenerationSnapshotService. Entradas: content: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String sha256(byte[] content){MessageDigest digest = digest(); digest.update(content); return HexFormat.of().formatHex(digest.digest());}
    /** Descripción: Ejecuta la operación «digest» correspondiente a la responsabilidad de MetamodelProjectGenerationSnapshotService. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private MessageDigest digest(){try{return MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException exception){throw new IllegalStateException("SHA-256 is unavailable", exception);}}
    /** Descripción: Actualiza el estado gestionado por MetamodelProjectGenerationSnapshotService mediante la operación «update». Entradas: digest: dato de entrada necesario para la operación; key: identificador que delimita el recurso objetivo; value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void update(MessageDigest digest, String key, String value){digest.update(key.getBytes(StandardCharsets.UTF_8)); digest.update((byte)0); digest.update((value == null ? "" : value).getBytes(StandardCharsets.UTF_8)); digest.update((byte)'\n');}
}
