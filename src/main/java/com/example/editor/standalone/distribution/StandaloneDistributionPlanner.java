package com.example.editor.standalone.distribution;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

/** Creates deterministic PWA and Tauri distribution configuration artifacts. */
public final class StandaloneDistributionPlanner {

    /** Descripción: Ejecuta la operación «plan» correspondiente a la responsabilidad de StandaloneDistributionPlanner. Entradas: profile: archivo o contenido que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ public List<StandaloneDistributionArtifact> plan(StandaloneDistributionProfile profile){
        Objects.requireNonNull(profile, "profile");
        List<StandaloneDistributionArtifact> artifacts = new ArrayList<>();
        artifacts.add(new StandaloneDistributionArtifact(
                "distribution/hydra-distribution.json", "application/json", distributionDescriptor(profile)));
        if(profile.targets().contains(StandaloneDistributionTarget.PWA)){
            artifacts.add(new StandaloneDistributionArtifact(
                    "public/manifest.webmanifest", "application/manifest+json", webManifest(profile)));
            artifacts.add(new StandaloneDistributionArtifact(
                    "public/service-worker.js", "text/javascript", serviceWorker(profile)));
        }
        if(profile.targets().stream().anyMatch(target -> target != StandaloneDistributionTarget.PWA)){
            artifacts.add(new StandaloneDistributionArtifact(
                    "src-tauri/tauri.conf.json", "application/json", tauriConfiguration(profile)));
            artifacts.add(new StandaloneDistributionArtifact(
                    "src-tauri/Cargo.toml", "text/plain", tauriCargo(profile)));
            artifacts.add(new StandaloneDistributionArtifact(
                    "src-tauri/build.rs", "text/x-rust", "fn main() { tauri_build::build(); }\n"));
            artifacts.add(new StandaloneDistributionArtifact(
                    "src-tauri/src/main.rs", "text/x-rust", tauriMain()));
            artifacts.add(new StandaloneDistributionArtifact(
                    "src-tauri/src/model_store.rs", "text/x-rust", TauriModelStoreTemplate.source()));
            artifacts.add(new StandaloneDistributionArtifact(
                    "src-tauri/capabilities/default.json", "application/json", tauriCapabilities()));
        }
        return artifacts.stream().sorted(Comparator.comparing(StandaloneDistributionArtifact::path)).toList();
    }

    /** Descripción: Ejecuta la operación «distributionDescriptor» correspondiente a la responsabilidad de StandaloneDistributionPlanner. Entradas: profile: archivo o contenido que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String distributionDescriptor(StandaloneDistributionProfile profile){
        String targets = profile.targets().stream().map(Enum::name).sorted()
                .map(this::jsonString).collect(Collectors.joining(","));
        String associations = profile.fileAssociations().stream().map(this::jsonString)
                .collect(Collectors.joining(","));
        return "{\n" +
                "  \"schemaVersion\": \"1.0\",\n" +
                "  \"productName\": " + jsonString(profile.productName()) + ",\n" +
                "  \"version\": " + jsonString(profile.version()) + ",\n" +
                "  \"targets\": [" + targets + "],\n" +
                "  \"offlineEnabled\": " + profile.offlineEnabled() + ",\n" +
                "  \"fileAssociations\": [" + associations + "],\n" +
                "  \"updateChannel\": " + jsonString(profile.updateChannel()) + "\n" +
                "}\n";
    }

    /** Descripción: Ejecuta la operación «webManifest» correspondiente a la responsabilidad de StandaloneDistributionPlanner. Entradas: profile: archivo o contenido que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String webManifest(StandaloneDistributionProfile profile){
        return "{\n" +
                "  \"name\": " + jsonString(profile.productName()) + ",\n" +
                "  \"short_name\": " + jsonString(shortName(profile.productName())) + ",\n" +
                "  \"start_url\": \"./\",\n" +
                "  \"display\": \"standalone\",\n" +
                "  \"scope\": \"./\",\n" +
                "  \"background_color\": \"#ffffff\",\n" +
                "  \"theme_color\": \"#173f67\"\n" +
                "}\n";
    }

    /** Descripción: Ejecuta la operación «serviceWorker» correspondiente a la responsabilidad de StandaloneDistributionPlanner. Entradas: profile: archivo o contenido que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String serviceWorker(StandaloneDistributionProfile profile){
        if(!profile.offlineEnabled()){
            return "self.addEventListener('install', () => self.skipWaiting());\n" +
                    "self.addEventListener('activate', event => event.waitUntil(self.clients.claim()));\n";
        }
        String cacheName = "hydra-editor-" + profile.version().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "-");
        return "const CACHE = " + jsonString(cacheName) + ";\n" +
                "const SHELL = ['./', './index.html'];\n" +
                "self.addEventListener('install', event => event.waitUntil(caches.open(CACHE).then(cache => cache.addAll(SHELL))));\n" +
                "self.addEventListener('activate', event => event.waitUntil(self.clients.claim()));\n" +
                "self.addEventListener('fetch', event => event.respondWith(caches.match(event.request).then(hit => hit || fetch(event.request))));\n";
    }

    /** Descripción: Ejecuta la operación «tauriConfiguration» correspondiente a la responsabilidad de StandaloneDistributionPlanner. Entradas: profile: archivo o contenido que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String tauriConfiguration(StandaloneDistributionProfile profile){
        String extensions = profile.fileAssociations().stream()
                .map(extension -> jsonString(extension.substring(1)))
                .collect(Collectors.joining(","));
        return "{\n" +
                "  \"$schema\": \"https://schema.tauri.app/config/2\",\n" +
                "  \"productName\": " + jsonString(profile.productName()) + ",\n" +
                "  \"version\": " + jsonString(profile.version()) + ",\n" +
                "  \"identifier\": " + jsonString(identifier(profile.productName())) + ",\n" +
                "  \"app\": { \"withGlobalTauri\": true },\n" +
                "  \"build\": { \"frontendDist\": \"../dist\" },\n" +
                "  \"bundle\": {\n" +
                "    \"active\": true,\n" +
                "    \"targets\": \"all\",\n" +
                "    \"fileAssociations\": [{ \"ext\": [" + extensions + "] }]\n" +
                "  }\n" +
                "}\n";
    }

    /**Descripción: Genera la configuración Cargo mínima para construir el ejecutable nativo Tauri 2. Entradas: datos validados por el contrato. Salida: resultado verificable de la operación. */
    private String tauriCargo(StandaloneDistributionProfile profile) {
        String version = profile.version();
        if(!version.matches("[0-9]+\\.[0-9]+\\.[0-9]+(?:-[0-9A-Za-z.-]+)?(?:\\+[0-9A-Za-z.-]+)?")) {
            throw new IllegalArgumentException("Tauri distribution version must be valid SemVer");
        }
        String packageName = identifier(profile.productName()).substring("org.hydra.".length());
        return "[package]\nname = \"hydra-" + packageName + "\"\nversion = \"" + version +
                "\"\nedition = \"2021\"\nbuild = \"build.rs\"\n" +
                "\n[build-dependencies]\ntauri-build = { version = \"2\", features = [] }\n" +
                "\n[dependencies]\ntauri = { version = \"2\", features = [] }\n" +
                "serde = { version = \"1\", features = [\"derive\"] }\n" +
                "serde_json = \"1\"\nsha2 = \"0.10\"\nfs2 = \"0.4\"\n" +
                "\n[target.'cfg(windows)'.dependencies]\n" +
                "windows-sys = { version = \"0.59\", features = [\"Win32_Storage_FileSystem\"] }\n";
    }

    /**Descripción: Define el punto de entrada de escritorio reutilizando el build web generado. Entradas: datos validados por el contrato. Salida: resultado verificable de la operación. */
    private String tauriMain() {
        return "#![cfg_attr(not(debug_assertions), windows_subsystem = \"windows\")]\n" +
                "mod model_store;\nuse model_store::ModelGuard;\nuse std::sync::Mutex;\n" +
                "fn main() {\n    tauri::Builder::default()\n" +
                "        .manage(ModelGuard(Mutex::new(())))\n" +
                "        .invoke_handler(tauri::generate_handler![model_store::hydra_list_models, model_store::hydra_read_model, model_store::hydra_write_model, model_store::hydra_delete_model])\n" +
                "        .run(tauri::generate_context!())\n" +
                "        .expect(\"Could not start generated HYDRA editor\");\n}\n";
    }

    /**Descripción: Restringe permisos nativos al conjunto básico de Tauri, sin acceso arbitrario a shell o sistema de archivos. Entradas: datos validados por el contrato. Salida: resultado verificable de la operación. */
    private String tauriCapabilities() {
        return "{\n  \"identifier\": \"default\",\n  \"description\": \"Generated editor basic window\",\n" +
                "  \"windows\": [\"main\"],\n  \"permissions\": [\"core:default\"]\n}\n";
    }

    /** Descripción: Ejecuta la operación «shortName» correspondiente a la responsabilidad de StandaloneDistributionPlanner. Entradas: productName: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String shortName(String productName){
        return productName.length() <= 18 ? productName : productName.substring(0, 18);
    }

    /** Descripción: Ejecuta la operación «identifier» correspondiente a la responsabilidad de StandaloneDistributionPlanner. Entradas: productName: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String identifier(String productName){
        String token = productName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        if(token.isBlank()){
            token = "generated-editor";
        }
        return "org.hydra." + token;
    }

    /** Descripción: Ejecuta la operación «jsonString» correspondiente a la responsabilidad de StandaloneDistributionPlanner. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String jsonString(String value){
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r") + "\"";
    }
}
