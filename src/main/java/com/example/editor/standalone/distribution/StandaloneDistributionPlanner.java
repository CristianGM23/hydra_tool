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
                "  \"build\": { \"frontendDist\": \"../dist\" },\n" +
                "  \"bundle\": {\n" +
                "    \"active\": true,\n" +
                "    \"targets\": \"all\",\n" +
                "    \"fileAssociations\": [{ \"ext\": [" + extensions + "] }]\n" +
                "  }\n" +
                "}\n";
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
