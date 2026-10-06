package com.example.editor.integration.compiler;

import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Service;
import com.example.editor.integration.IntegrationArtifactKind;
import com.example.editor.integration.IntegrationDiagnostic;
import com.example.editor.integration.IntegrationDiagnosticSeverity;

/** Coordina la traducción tecnológica sin acoplar el negocio a EMF, Sirius u otro destino. */
@Service
public class TechnologyCompilerService {
    private final TechnologyAdapterRegistry registry;

    /** Descripción: Inicializa una instancia de TechnologyCompilerService con sus dependencias y datos de entrada. Entradas: registry: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public TechnologyCompilerService(TechnologyAdapterRegistry registry){this.registry = registry;}

    /** Descripción: Ejecuta la operación «compile» correspondiente a la responsabilidad de TechnologyCompilerService. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public TechnologyCompilationResult compile(TechnologyCompilationRequest request){
        TechnologyAdapter adapter = registry.require(request.profile().target().technology());
        Set<IntegrationArtifactKind> selected = new HashSet<>();
        request.artifacts().forEach(value -> selected.add(value.kind()));
        Set<IntegrationArtifactKind> acceptedRoots = adapter.semanticRootKinds();
        boolean hasSemanticRoot = acceptedRoots.stream().anyMatch(selected::contains);
        if(!hasSemanticRoot){
            throw new TechnologyCompilationException("A canonical semantic root is required; adapter accepts " + acceptedRoots);
        }
        TechnologyCompilationResult result = adapter.compile(request);
        if(result.technology() != adapter.technology()){
            throw new TechnologyCompilationException("Technology adapter returned a mismatched result: " + result.technology());
        }
        long duplicatePaths = result.files().stream().map(TechnologyGeneratedFile::relativePath).distinct().count();
        if(duplicatePaths != result.files().size()){
            throw new TechnologyCompilationException("Technology adapter generated duplicate paths");
        }
        for(var artifact : request.artifacts()){
            if(!adapter.supportedArtifactKinds().contains(artifact.kind()) && !acceptedRoots.contains(artifact.kind())){
                // Los adaptadores pueden devolver advertencias más específicas; esta comprobación protege silencios accidentales.
                if(result.diagnostics().stream().noneMatch(value -> value.code().equals("UNSUPPORTED_ARTIFACT") && value.elementId().equals(artifact.documentId()))){
                    throw new TechnologyCompilationException("Adapter did not report unsupported artifact: " + artifact.kind());
                }
            }
        }
        if(result.files().isEmpty() && result.valid()){
            throw new TechnologyCompilationException("A valid technology compilation must generate at least one file");
        }
        return result;
    }

    /** Descripción: Ejecuta la operación «unsupported» correspondiente a la responsabilidad de TechnologyCompilerService. Entradas: kind: dato de entrada necesario para la operación; documentId: identificador que delimita el recurso objetivo; technology: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static IntegrationDiagnostic unsupported(IntegrationArtifactKind kind, String documentId, String technology){
        return new IntegrationDiagnostic(IntegrationDiagnosticSeverity.WARNING, "UNSUPPORTED_ARTIFACT", documentId,
                "artifacts." + kind.name(), technology + " does not translate this artifact yet; it remains available in the canonical package.");
    }
}
