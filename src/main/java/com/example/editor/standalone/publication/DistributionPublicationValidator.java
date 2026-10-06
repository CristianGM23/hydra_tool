package com.example.editor.standalone.publication;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Validates paths, duplicate platforms, update URLs and external-signature companions. */
@Component
public class DistributionPublicationValidator {
    /** Descripción: Valida las condiciones necesarias para «validate» y rechaza entradas no válidas. Entradas: distributionRoot: dato de entrada necesario para la operación; request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public PublicationValidationReport validate(Path distributionRoot, PublicationRequest request){
        List<PublicationDiagnostic> diagnostics = new ArrayList<>();
        Set<String> platforms = new HashSet<>();
        if(request.channel() != PublicationChannel.INTERNAL && request.updateBaseUrl().isBlank()){
            diagnostics.add(error("UPDATE_URL_REQUIRED", "A public updateBaseUrl is required outside the internal channel", "updateBaseUrl"));
        }else if(!request.updateBaseUrl().isBlank() && !request.updateBaseUrl().startsWith("https://")){
            diagnostics.add(error("UPDATE_URL_INSECURE", "updateBaseUrl must use HTTPS", "updateBaseUrl"));
        }
        for(PublicationArtifactRequest artifact : request.artifacts()){
            if(!platforms.add(artifact.platform())){
                diagnostics.add(error("DUPLICATE_PLATFORM", "Only one artifact may be published per platform", artifact.platform()));
            }
            Path path;
            try{path = resolve(distributionRoot, artifact.relativePath());}
            catch(IllegalArgumentException exception){
                diagnostics.add(error("UNSAFE_ARTIFACT_PATH", exception.getMessage(), artifact.relativePath()));
                continue;
            }
            if(!Files.isRegularFile(path)){
                diagnostics.add(error("ARTIFACT_MISSING", "Artifact does not exist: " + artifact.relativePath(), artifact.relativePath()));
            }
            if(request.signingMode() == PublicationSigningMode.EXTERNAL_SIGNATURE){
                Path signature = path.resolveSibling(path.getFileName() + ".sig");
                if(!Files.isRegularFile(signature)){
                    diagnostics.add(error("SIGNATURE_MISSING", "External signature is missing: " + signature.getFileName(), artifact.relativePath()));
                }
            }
        }
        return new PublicationValidationReport(diagnostics.isEmpty(), diagnostics);
    }

    /** Descripción: Obtiene la información correspondiente a «resolve» dentro de DistributionPublicationValidator. Entradas: root: ruta o ubicación utilizada por la operación; value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ Path resolve(Path root, String value){
        String normalized = value == null ? "" : value.replace('\\', '/').trim();
        if(normalized.isBlank() || normalized.startsWith("/") || normalized.matches("^[A-Za-z]:.*")
                || normalized.equals(".") || normalized.equals("..") || normalized.contains("../")){
            throw new IllegalArgumentException("Artifact path must remain below the distribution directory");
        }
        Path target = root.resolve(normalized).normalize();
        if(!target.startsWith(root)){throw new IllegalArgumentException("Artifact path escapes the distribution directory");}
        Path current = root;
        for(Path segment : root.relativize(target)){
            current = current.resolve(segment);
            if(Files.isSymbolicLink(current)){throw new IllegalArgumentException("Artifact path crosses a symbolic link");}
        }
        return target;
    }

    /** Descripción: Ejecuta la operación «error» correspondiente a la responsabilidad de DistributionPublicationValidator. Entradas: code: dato de entrada necesario para la operación; message: dato de entrada necesario para la operación; field: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private PublicationDiagnostic error(String code, String message, String field){
        return new PublicationDiagnostic("ERROR", code, message, field);
    }
}
