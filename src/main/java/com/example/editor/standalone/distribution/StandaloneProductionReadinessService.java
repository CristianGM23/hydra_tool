package com.example.editor.standalone.distribution;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Separates source-archive validity from deployable PWA/native-project readiness.
 * This is a pre-build quality gate, never an assertion of a signed installer.
 */
public final class StandaloneProductionReadinessService {
    private static final Pattern LOCAL_ASSET = Pattern.compile("(?:src|href)\\s*=\\s*[\"']([^\"']+\\.(?:js|css)(?:\\?[^\"']*)?)[\"']", Pattern.CASE_INSENSITIVE);
    private static final int MAX_HTML_BYTES = 1_048_576;
    private static final int MAX_ASSETS = 128;

    /**Descripción: Comprueba que existe una aplicación construida y un proyecto de empaquetado válido; no declara instaladores nativos certificados. Entradas: datos validados por el contrato. Salida: resultado verificable de la operación. */
    public StandaloneDistributionValidationReport assess(Path editorRoot,
            StandaloneDistributionProfile profile, StandaloneDistributionValidationReport sourceValidation) {
        Objects.requireNonNull(editorRoot, "editorRoot");
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(sourceValidation, "sourceValidation");
        Path root = editorRoot.toAbsolutePath().normalize();
        List<StandaloneDistributionDiagnostic> diagnostics = new ArrayList<>(sourceValidation.diagnostics());
        Path dist = safe(root, "dist");
        require(root, "dist/index.html", "BUILT_ENTRY", diagnostics);
        if(profile.targets().contains(StandaloneDistributionTarget.PWA)) {
            require(root, "dist/manifest.webmanifest", "BUILT_MANIFEST", diagnostics);
            require(root, "dist/service-worker.js", "BUILT_WORKER", diagnostics);
            if(profile.offlineEnabled() && regular(safe(root, "dist/service-worker.js"))) {
                try {
                    Path worker = safe(root, "dist/service-worker.js");
                    if(Files.size(worker) > MAX_HTML_BYTES) {
                        error("OFFLINE_WORKER_SIZE", "dist/service-worker.js", "Service worker exceeds inspection budget", diagnostics);
                    } else {
                        String content = Files.readString(worker, StandardCharsets.UTF_8);
                        if(!content.contains("caches.open") || !content.contains("SHELL") || !content.contains("fetch")) {
                            error("OFFLINE_WORKER", "dist/service-worker.js", "Missing offline cache and fetch behavior", diagnostics);
                        }
                    }
                } catch(IOException exception) {
                    error("OFFLINE_WORKER_READ", "dist/service-worker.js", "Cannot inspect offline worker", diagnostics);
                }
            }
            if(profile.offlineEnabled()) {
                require(root, "dist/index.html", "OFFLINE_ENTRY", diagnostics);
            }
        }
        if(profile.targets().stream().anyMatch(target -> target != StandaloneDistributionTarget.PWA)) {
            require(root, "src-tauri/Cargo.toml", "TAURI_CARGO", diagnostics);
            require(root, "src-tauri/build.rs", "TAURI_BUILD", diagnostics);
            boolean rustEntry = regular(safe(root, "src-tauri/src/main.rs"))
                    || regular(safe(root, "src-tauri/src/lib.rs"));
            if(!rustEntry) error("TAURI_RUST_ENTRY", "src-tauri/src", "Missing Rust application entry point", diagnostics);
            require(root, "src-tauri/capabilities/default.json", "TAURI_CAPABILITIES", diagnostics);
        }
        if(regular(dist.resolve("index.html"))) {
            try {
                if(Files.size(dist.resolve("index.html")) > MAX_HTML_BYTES) {
                    error("BUILT_HTML_SIZE", "dist/index.html", "Built HTML exceeds inspection budget", diagnostics);
                } else {
                    inspectAssets(dist, Files.readString(dist.resolve("index.html"), StandardCharsets.UTF_8), diagnostics);
                }
            } catch(IOException exception) {
                error("BUILT_HTML_READ", "dist/index.html", "Cannot inspect built HTML", diagnostics);
            }
        }
        boolean valid = sourceValidation.valid()
                && diagnostics.stream().noneMatch(item -> item.severity() == StandaloneDistributionDiagnosticSeverity.ERROR);
        return new StandaloneDistributionValidationReport(valid, diagnostics, Map.of(
                "qualification", "STATIC_DEPLOYMENT_READINESS",
                "nativeInstallerCertified", "false",
                "sourceValid", Boolean.toString(sourceValidation.valid()),
                "builtAssetsChecked", "true"));
    }

    /**Descripción: Verifica recursos locales explícitamente referenciados; nunca lee fuera del directorio dist. Entradas: datos validados por el contrato. Salida: resultado verificable de la operación. */
    private void inspectAssets(Path dist, String html, List<StandaloneDistributionDiagnostic> diagnostics) {
        Matcher matcher = LOCAL_ASSET.matcher(html);
        int inspected = 0;
        while(matcher.find()) {
            if(++inspected > MAX_ASSETS) {
                error("ASSET_BUDGET", "dist/index.html", "Too many referenced assets", diagnostics);
                break;
            }
            String url = matcher.group(1).split("\\?", 2)[0];
            if(url.startsWith("http:") || url.startsWith("https:") || url.startsWith("//")) {
                error("REMOTE_EXECUTABLE_ASSET", "dist/index.html", "Remote JS/CSS prevents offline assurance", diagnostics);
                continue;
            }
            String relative = url.startsWith("/") ? url.substring(1) : url.replaceFirst("^\\./", "");
            if(relative.contains("..") || relative.contains(":") || relative.contains("\\")) {
                error("ASSET_PATH", "dist/index.html", "Unsafe application asset path", diagnostics);
                continue;
            }
            Path target = dist.resolve(relative).normalize();
            try {
                if(!target.startsWith(dist) || !regular(safe(dist, relative))) {
                    error("MISSING_BUILT_ASSET", relative, "Referenced JavaScript or CSS asset is absent", diagnostics);
                }
            } catch(IllegalArgumentException exception) {
                error("ASSET_PATH", relative, "Asset path contains a symbolic link", diagnostics);
            }
        }
    }

    /**Descripción: Exige un archivo regular sin enlaces simbólicos en sus segmentos. Entradas: datos validados por el contrato. Salida: resultado verificable de la operación. */
    private void require(Path root, String relative, String code,
            List<StandaloneDistributionDiagnostic> diagnostics) {
        if(!regular(safe(root, relative))) {
            error(code, relative, "Required distribution artifact is missing", diagnostics);
        }
    }

    /**Descripción: Resuelve una ruta fija por debajo del editor y rechaza cruces por enlaces simbólicos. Entradas: datos validados por el contrato. Salida: resultado verificable de la operación. */
    private Path safe(Path root, String relative) {
        Path target = root.resolve(relative).normalize();
        if(!target.startsWith(root)) throw new IllegalArgumentException("Unsafe readiness path");
        Path current = root;
        for(Path segment : root.relativize(target)) {
            current = current.resolve(segment);
            if(Files.isSymbolicLink(current)) throw new IllegalArgumentException("Symbolic links are not allowed in distribution readiness");
        }
        return target;
    }

    /**Descripción: Consulta la existencia de un fichero sin seguir enlaces. Entradas: datos validados por el contrato. Salida: resultado verificable de la operación. */
    private boolean regular(Path path) {
        return Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS);
    }

    /**Descripción: Añade un diagnóstico de fallo verificable. Entradas: datos validados por el contrato. Salida: resultado verificable de la operación. */
    private void error(String code, String path, String message,
            List<StandaloneDistributionDiagnostic> diagnostics) {
        diagnostics.add(new StandaloneDistributionDiagnostic(code, StandaloneDistributionDiagnosticSeverity.ERROR, path, message));
    }
}
