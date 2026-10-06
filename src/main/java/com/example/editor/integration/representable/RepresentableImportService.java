package com.example.editor.integration.representable;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Previews and persists representable external changes without silently dropping unsupported constructs. */
@Service
public class RepresentableImportService {
    private final RepresentableImportMapperRegistry registry;
    private final RepresentableImportStore store;
    private final Path importRoot;

    /** Descripción: Inicializa una instancia de RepresentableImportService con sus dependencias y datos de entrada. Entradas: registry: dato de entrada necesario para la operación; store: dato de entrada necesario para la operación; importRoot: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public RepresentableImportService(RepresentableImportMapperRegistry registry, RepresentableImportStore store,
            @Value("${hydra.representable-import.root:data/representable-imports}") String importRoot){
        this.registry = registry; this.store = store;
        this.importRoot = Path.of(importRoot).toAbsolutePath().normalize();
    }

    /** Descripción: Ejecuta la operación «technologies» correspondiente a la responsabilidad de RepresentableImportService. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public List<String> technologies(){return registry.technologies();}

    /** Descripción: Ejecuta la operación «preview» correspondiente a la responsabilidad de RepresentableImportService. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public RepresentableImportPreview preview(RepresentableImportRequest request){
        RepresentableImportMapper mapper = registry.require(request.technology());
        List<NeutralChangeOperation> operations = new ArrayList<>();
        List<RepresentableImportDiagnostic> diagnostics = new ArrayList<>();
        request.changes().stream().sorted(java.util.Comparator.comparing(ExternalChangeCandidate::externalId))
                .map(mapper::map).forEach(result -> {
                    if(result.operation() != null){operations.add(result.operation());}
                    diagnostics.addAll(result.diagnostics());
                });
        boolean safeStrategy = !"STRICT".equals(request.strategy()) || diagnostics.stream()
                .noneMatch(item -> item.representability() != Representability.FULL);
        boolean applicable = safeStrategy && diagnostics.stream()
                .noneMatch(item -> item.representability() == Representability.NONE);
        String previewId = digest(request.technology() + "|" + request.metamodelId() + "|" + operations + "|" + diagnostics);
        return new RepresentableImportPreview(previewId, request.technology(), request.metamodelId(),
                Instant.now(), operations, diagnostics, applicable);
    }

    /** Descripción: Actualiza el estado gestionado por RepresentableImportService mediante la operación «apply». Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public RepresentableImportPreview apply(RepresentableImportRequest request){
        RepresentableImportPreview preview = preview(request);
        if(!preview.applicable()){throw new IllegalStateException("Representable import contains blocking diagnostics");}
        store.save(importRoot, preview);
        return preview;
    }

    /** Descripción: Ejecuta la operación «digest» correspondiente a la responsabilidad de RepresentableImportService. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String digest(String value){
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8))).substring(0, 24);}
        catch(Exception exception){throw new IllegalStateException("SHA-256 is not available", exception);}
    }
}
