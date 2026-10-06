package com.example.editor.standalone.distribution;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.example.editor.io.BoundedIo;
import com.example.editor.standalone.StandaloneEditorGenerationProperties;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Validates materialized PWA/Tauri configuration before packaging. */
@Service
public class StandaloneDistributionValidationService {
    private final JsonMapper mapper;
    private final StandaloneEditorGenerationProperties properties;
    @Autowired
    /** Descripción: Inicializa una instancia de StandaloneDistributionValidationService con el mapper y los límites compartidos del editor standalone. Entradas: mapper: componente JSON; properties: límites configurables de generación. Salida: deja la instancia preparada para validar artefactos sin lecturas de texto no acotadas. */ public StandaloneDistributionValidationService(JsonMapper mapper,StandaloneEditorGenerationProperties properties){this.mapper=Objects.requireNonNull(mapper,"mapper");this.properties=Objects.requireNonNull(properties,"properties");}
    /** Descripción: Inicializa una instancia compatible para pruebas focales utilizando los límites standalone por defecto. Entradas: mapper: componente JSON. Salida: deja una instancia equivalente a la configuración por defecto. */ public StandaloneDistributionValidationService(JsonMapper mapper){this(mapper,new StandaloneEditorGenerationProperties());}

    /** Descripción: Valida las condiciones necesarias para «validate» y rechaza entradas no válidas. Entradas: editorRoot: dato de entrada necesario para la operación; profile: archivo o contenido que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ public StandaloneDistributionValidationReport validate(Path editorRoot, StandaloneDistributionProfile profile){
        Path root = Objects.requireNonNull(editorRoot, "editorRoot").toAbsolutePath().normalize();
        Objects.requireNonNull(profile, "profile"); List<StandaloneDistributionDiagnostic> diagnostics = new ArrayList<>();
        boolean applicationEntryExists = List.of("index.html", "dist/index.html", "frontend/index.html").stream()
                .map(path -> StandaloneDistributionMaterializer.safeResolve(root, path)).anyMatch(Files::isRegularFile);
        if(!applicationEntryExists){error("APP_ENTRY", "", "No standalone application entry was found (index.html, dist/index.html or frontend/index.html)", diagnostics);}
        JsonNode descriptor = json(root, "distribution/hydra-distribution.json", "distribution descriptor", diagnostics);
        if(descriptor != null){requireText(descriptor.path("productName"), "DIST_PRODUCT", "distribution/hydra-distribution.json", "productName", diagnostics);}
        if(profile.targets().contains(StandaloneDistributionTarget.PWA)){
            JsonNode manifest = json(root, "public/manifest.webmanifest", "PWA manifest", diagnostics);
            if(manifest != null){
                requireValue(manifest.path("start_url").asText(), "./", "PWA_START", "public/manifest.webmanifest", "start_url must be ./", diagnostics);
                requireValue(manifest.path("display").asText(), "standalone", "PWA_DISPLAY", "public/manifest.webmanifest", "display must be standalone", diagnostics);
            }
            Path worker = StandaloneDistributionMaterializer.safeResolve(root, "public/service-worker.js");
            if(!Files.exists(worker)){error("PWA_WORKER", "public/service-worker.js", "Service worker is missing", diagnostics);}
            else try{
                String content = BoundedIo.readUtf8(worker,properties.getMaximumGeneratedModelBytes());
                if(profile.offlineEnabled() && (!content.contains("const SHELL") || !content.contains("caches.open"))){
                    error("PWA_OFFLINE", "public/service-worker.js", "Offline-enabled profile must cache the application shell", diagnostics);
                }
            }catch(IOException exception){error("PWA_WORKER_READ", "public/service-worker.js", exception.getMessage(), diagnostics);}
        }
        if(profile.targets().stream().anyMatch(target -> target != StandaloneDistributionTarget.PWA)){
            JsonNode tauri = json(root, "src-tauri/tauri.conf.json", "Tauri configuration", diagnostics);
            if(tauri != null){
                requireText(tauri.path("identifier"), "TAURI_IDENTIFIER", "src-tauri/tauri.conf.json", "identifier", diagnostics);
                requireText(tauri.path("version"), "TAURI_VERSION", "src-tauri/tauri.conf.json", "version", diagnostics);
                requireValue(tauri.path("build").path("frontendDist").asText(), "../dist", "TAURI_DIST", "src-tauri/tauri.conf.json", "frontendDist must be ../dist", diagnostics);
            }
        }
        diagnostics.add(new StandaloneDistributionDiagnostic("VALIDATED", StandaloneDistributionDiagnosticSeverity.INFO, "",
                "Validated " + profile.targets().size() + " distribution target(s)"));
        boolean valid = diagnostics.stream().noneMatch(item -> item.severity() == StandaloneDistributionDiagnosticSeverity.ERROR);
        return new StandaloneDistributionValidationReport(valid, diagnostics, Map.of("root", root.toString(), "targetCount", Integer.toString(profile.targets().size())));
    }
    /** Descripción: Ejecuta la operación «json» correspondiente a la responsabilidad de StandaloneDistributionValidationService. Entradas: root: ruta o ubicación utilizada por la operación; path: ruta o ubicación utilizada por la operación; description: dato de entrada necesario para la operación; diagnostics: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private JsonNode json(Path root, String path, String description, List<StandaloneDistributionDiagnostic> diagnostics){
        Path target = StandaloneDistributionMaterializer.safeResolve(root, path);
        if(!Files.exists(target)){error("MISSING_JSON", path, "Missing " + description, diagnostics); return null;}
        try{return mapper.readTree(com.example.editor.io.BoundedIo.readInternal(target));}
        catch(IOException | RuntimeException exception){error("INVALID_JSON", path, description + " is not valid JSON: " + exception.getMessage(), diagnostics); return null;}
    }
    /** Descripción: Valida las condiciones necesarias para «requireText» y rechaza entradas no válidas. Entradas: node: dato de entrada necesario para la operación; code: dato de entrada necesario para la operación; path: ruta o ubicación utilizada por la operación; field: dato de entrada necesario para la operación; diagnostics: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void requireText(JsonNode node, String code, String path, String field, List<StandaloneDistributionDiagnostic> diagnostics){
        if(node == null || node.isMissingNode() || node.asText().isBlank()){error(code, path, "Missing required field " + field, diagnostics);}
    }
    /** Descripción: Valida las condiciones necesarias para «requireValue» y rechaza entradas no válidas. Entradas: actual: dato de entrada necesario para la operación; expected: dato de entrada necesario para la operación; code: dato de entrada necesario para la operación; path: ruta o ubicación utilizada por la operación; message: dato de entrada necesario para la operación; diagnostics: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void requireValue(String actual, String expected, String code, String path, String message, List<StandaloneDistributionDiagnostic> diagnostics){
        if(!expected.equals(actual)){error(code, path, message, diagnostics);}
    }
    /** Descripción: Ejecuta la operación «error» correspondiente a la responsabilidad de StandaloneDistributionValidationService. Entradas: code: dato de entrada necesario para la operación; path: ruta o ubicación utilizada por la operación; message: dato de entrada necesario para la operación; diagnostics: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void error(String code, String path, String message, List<StandaloneDistributionDiagnostic> diagnostics){
        diagnostics.add(new StandaloneDistributionDiagnostic(code, StandaloneDistributionDiagnosticSeverity.ERROR, path, message));
    }
}
