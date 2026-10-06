package com.example.editor.integration.roundtrip;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import com.example.editor.integration.IntegrationArtifactKind;
import com.example.editor.integration.compiler.TechnologyCompilationRequest;
import com.example.editor.integration.compiler.TechnologyCompilationResult;
import com.example.editor.integration.compiler.TechnologyGeneratedFile;

/** Construye el manifiesto de round-trip a partir de provenance y hashes reales. */
@Service
public class RoundTripManifestService {
    /** Descripción: Crea o prepara el resultado de «create» dentro de RoundTripManifestService. Entradas: request: solicitud con los datos necesarios para ejecutar la operación; result: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public RoundTripManifest create(TechnologyCompilationRequest request, TechnologyCompilationResult result){
        List<RoundTripElementMapping> mappings = new ArrayList<>();
        for(TechnologyGeneratedFile file : result.files().stream().sorted(Comparator.comparing(TechnologyGeneratedFile::relativePath)).toList()){
            List<String> documentIds = file.sourceDocumentIds().isEmpty() ? List.of(request.profile().semanticScopeId()) : file.sourceDocumentIds();
            for(String documentId : documentIds){
                IntegrationArtifactKind kind = request.artifacts().stream().filter(value -> value.documentId().equals(documentId)).map(value -> value.kind()).findFirst().orElse(IntegrationArtifactKind.METAMODEL);
                mappings.add(new RoundTripElementMapping(documentId, kind, file.relativePath(),
                        file.metadata().containsKey("sourceElementIds") ? List.of(file.metadata().get("sourceElementIds").split(",")) : List.of(),
                        sha256(file.content()), file.ownership(), file.metadata()));
            }
        }
        return new RoundTripManifest("1.0", request.profile().id(), result.technology(), request.generatorVersion(), Instant.EPOCH,
                mappings, Map.of("canonicalMetamodelId", request.profile().metamodelId(), "canonicalSemanticScopeId", request.profile().semanticScopeId(),
                        "mappingCount", Integer.toString(mappings.size())));
    }

    /** Descripción: Ejecuta la operación «sha256» correspondiente a la responsabilidad de RoundTripManifestService. Entradas: content: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String sha256(byte[] content){
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));}
        catch(NoSuchAlgorithmException exception){throw new IllegalStateException(exception);}
    }
}
