package com.example.editor.integration.compiler;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import com.example.editor.integration.InjectionFileOwnership;
import com.example.editor.integration.roundtrip.RoundTripManifest;
import com.example.editor.integration.roundtrip.RoundTripManifestService;
import com.example.editor.integration.roundtrip.RoundTripSyncService;
import com.example.editor.integration.verification.TechnologyProjectValidationDescriptor;
import tools.jackson.databind.json.JsonMapper;

/** Añade provenance, manifiesto de round-trip y scripts reproducibles a toda exportación tecnológica. */
@Service
public class TechnologyCompilationEnrichmentService {
    private final JsonMapper mapper;
    private final RoundTripManifestService roundTrips;

    /** Descripción: Inicializa una instancia de TechnologyCompilationEnrichmentService con sus dependencias y datos de entrada. Entradas: mapper: componente de conversión utilizado durante el procesamiento; roundTrips: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public TechnologyCompilationEnrichmentService(JsonMapper mapper, RoundTripManifestService roundTrips){this.mapper = mapper; this.roundTrips = roundTrips;}

    /** Descripción: Ejecuta la operación «enrich» correspondiente a la responsabilidad de TechnologyCompilationEnrichmentService. Entradas: request: solicitud con los datos necesarios para ejecutar la operación; result: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public TechnologyCompilationResult enrich(TechnologyCompilationRequest request, TechnologyCompilationResult result){
        try{
            List<TechnologyGeneratedFile> files = new ArrayList<>(result.files());
            RoundTripManifest roundTrip = roundTrips.create(request, result);
            files.add(file(RoundTripSyncService.MANIFEST_PATH, "application/json", mapper.writeValueAsBytes(roundTrip), request));
            TechnologyProjectValidationDescriptor descriptor = new TechnologyProjectValidationDescriptor("1.0", result.technology(),
                    result.verificationSteps(), Map.of("generatorVersion", request.generatorVersion(), "profileId", request.profile().id()));
            files.add(file("META-INF/hydra-project-validation.json", "application/json", mapper.writeValueAsBytes(descriptor), request));
            files.add(file("verify-hydra.cmd", "text/plain", windowsScript(result).getBytes(StandardCharsets.UTF_8), request));
            files.add(file("verify-hydra.sh", "text/x-shellscript", unixScript(result).getBytes(StandardCharsets.UTF_8), request));
            Map<String, String> metadata = new java.util.LinkedHashMap<>(result.metadata());
            metadata.put("roundTripManifest", RoundTripSyncService.MANIFEST_PATH);
            metadata.put("projectValidationDescriptor", "META-INF/hydra-project-validation.json");
            return new TechnologyCompilationResult(result.technology(), files, result.dependencies(), result.installationSteps(),
                    result.rollbackSteps(), result.verificationSteps(), result.diagnostics(), metadata);
        }catch(RuntimeException exception){
            if(exception instanceof TechnologyCompilationException){throw exception;}
            throw new TechnologyCompilationException("Could not enrich technology compilation", exception);
        }
    }

    /** Descripción: Ejecuta la operación «file» correspondiente a la responsabilidad de TechnologyCompilationEnrichmentService. Entradas: path: ruta o ubicación utilizada por la operación; mediaType: dato de entrada necesario para la operación; content: valor de entrada que debe procesarse; request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ private TechnologyGeneratedFile file(String path, String mediaType, byte[] content, TechnologyCompilationRequest request){
        return new TechnologyGeneratedFile(path, mediaType, content, InjectionFileOwnership.GENERATED, true,
                request.artifacts().stream().map(value -> value.documentId()).distinct().sorted().toList(), Map.of("hydra:infrastructure", "true"));
    }

    /** Descripción: Ejecuta la operación «windowsScript» correspondiente a la responsabilidad de TechnologyCompilationEnrichmentService. Entradas: result: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String windowsScript(TechnologyCompilationResult result){
        StringBuilder script = new StringBuilder("@echo off\r\nsetlocal\r\n");
        for(var step : result.verificationSteps()){
            script.append("echo [HYDRA] ").append(step.description().replace("&", "and")).append("\r\n");
            if(step.workingDirectory() != null && !step.workingDirectory().isBlank() && !step.workingDirectory().equals(".")){
                script.append("pushd \"").append(step.workingDirectory().replace('/', '\\')).append("\"\r\n");
            }
            script.append(String.join(" ", step.command().stream().map(this::quoteWindows).toList())).append("\r\n");
            script.append("if errorlevel 1 exit /b %errorlevel%\r\n");
            if(step.workingDirectory() != null && !step.workingDirectory().isBlank() && !step.workingDirectory().equals(".")){script.append("popd\r\n");}
        }
        return script.append("echo [HYDRA] Native validation completed.\r\n").toString();
    }

    /** Descripción: Ejecuta la operación «unixScript» correspondiente a la responsabilidad de TechnologyCompilationEnrichmentService. Entradas: result: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String unixScript(TechnologyCompilationResult result){
        StringBuilder script = new StringBuilder("#!/usr/bin/env sh\nset -eu\n");
        for(var step : result.verificationSteps()){
            script.append("echo '").append(step.description().replace("'", "")).append("'\n");
            String command = String.join(" ", step.command().stream().map(this::quoteUnix).toList());
            if(step.workingDirectory() != null && !step.workingDirectory().isBlank() && !step.workingDirectory().equals(".")){
                script.append("(cd ").append(quoteUnix(step.workingDirectory())).append(" && ").append(command).append(")\n");
            }else{script.append(command).append("\n");}
        }
        return script.append("echo '[HYDRA] Native validation completed.'\n").toString();
    }

    /** Descripción: Ejecuta la operación «quoteWindows» correspondiente a la responsabilidad de TechnologyCompilationEnrichmentService. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String quoteWindows(String value){return value.contains(" ") ? "\"" + value.replace("\"", "") + "\"" : value;}
    /** Descripción: Ejecuta la operación «quoteUnix» correspondiente a la responsabilidad de TechnologyCompilationEnrichmentService. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String quoteUnix(String value){return "'" + value.replace("'", "'\\''") + "'";}
}
