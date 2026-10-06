package com.example.editor.metamodel.importing.updatesite;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.example.editor.metamodel.importing.support.SecureXmlSupport;

/** Construye el inventario funcional de features, bundles, dependencias y artefactos. */
@Component
public class P2BundleAnalyzer {
    private final P2BundleClassifier classifier;

    /** Descripción: Inicializa una instancia de P2BundleAnalyzer con sus dependencias y datos de entrada. Entradas: classifier: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public P2BundleAnalyzer(P2BundleClassifier classifier) {
        this.classifier = classifier;
    }

    /** Descripción: Ejecuta la operación «analyze» correspondiente a la responsabilidad de MutableBundle. Entradas: repository: dato de entrada necesario para la operación; metadata: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ UpdateSiteRepositorySummary analyze(UpdateSiteRepositoryContent repository, P2MetadataSnapshot metadata) {
        List<MutableBundle> mutableBundles = discoverBundles(repository);
        Set<String> availableBundleIds = mutableBundles.stream().map(bundle -> bundle.id).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        List<UpdateSiteBundleDescriptor> bundles = mutableBundles.stream().map(bundle -> bundle.freeze(availableBundleIds)).toList();
        List<UpdateSiteFeatureDescriptor> features = discoverFeatures(repository, metadata, availableBundleIds);
        List<UpdateSiteDependency> missing = new ArrayList<>();
        bundles.stream().flatMap(bundle -> bundle.dependencies().stream()).filter(item -> !item.resolved() && !item.optional()).forEach(missing::add);
        features.stream().flatMap(feature -> feature.dependencies().stream()).filter(item -> !item.resolved() && !item.optional()).forEach(missing::add);
        List<List<String>> cycles = dependencyCycles(bundles);
        Set<String> owned = bundles.stream().flatMap(bundle -> bundle.artifacts().stream()).map(UpdateSiteArtifactDescriptor::path).collect(java.util.stream.Collectors.toSet());
        List<UpdateSiteArtifactDescriptor> unowned = repository.artifacts().stream()
                .filter(item -> !item.path().contains("!/") || !owned.contains(item.path()))
                .map(item -> artifact(item, ""))
                .filter(item -> UpdateSiteArtifactKinds.relevant(item.kind()))
                .filter(item -> item.kind() != UpdateSiteArtifactKind.JAVA_CLASS)
                .sorted(Comparator.comparing(UpdateSiteArtifactDescriptor::path)).toList();

        List<String> diagnostics = new ArrayList<>(metadata.diagnostics());
        if(bundles.isEmpty()) diagnostics.add("No OSGi bundle JARs were found");
        if(features.isEmpty()) diagnostics.add("No Eclipse features were found");
        if(!cycles.isEmpty()) diagnostics.add("Bundle dependency cycles were detected and preserved for review");
        return new UpdateSiteRepositorySummary(metadata.repositoryName(), repository.fingerprint(), metadata.contentFound(),
                metadata.artifactsFound(), repository.files().size(), repository.totalBytes(), features, bundles,
                unowned, missing.stream().sorted(Comparator.comparing(UpdateSiteDependency::sourceId).thenComparing(UpdateSiteDependency::targetId)).toList(),
                cycles, diagnostics);
    }

    /** Descripción: Ejecuta la operación «discoverBundles» correspondiente a la responsabilidad de MutableBundle. Entradas: repository: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<MutableBundle> discoverBundles(UpdateSiteRepositoryContent repository) {
        List<MutableBundle> result = new ArrayList<>();
        for(UpdateSiteRepositoryArtifact archive : repository.artifacts().stream()
                .filter(entry -> !entry.path().contains("!/") && entry.path().toLowerCase(Locale.ROOT).endsWith(".jar"))
                .filter(entry -> !isMetadataJar(entry.path()) && !isFeatureJar(entry.path()))
                .sorted(Comparator.comparing(UpdateSiteRepositoryArtifact::path)).toList()) {
            String prefix = archive.path() + "!/";
            List<UpdateSiteRepositoryArtifact> nested = repository.artifacts().stream()
                    .filter(entry -> entry.path().startsWith(prefix))
                    .sorted(Comparator.comparing(UpdateSiteRepositoryArtifact::path)).toList();
            byte[] manifestBytes = repository.find(prefix + "META-INF/MANIFEST.MF").orElse(null);
            Map<String, String> headers = readManifest(manifestBytes);
            String detectedId = symbolicName(headers.getOrDefault("Bundle-SymbolicName", ""));
            if(detectedId.isBlank()) detectedId = idFromJarPath(archive.path());
            final String id = detectedId;
            String version = headers.getOrDefault("Bundle-Version", versionFromJarPath(archive.path()));
            String name = headers.getOrDefault("Bundle-Name", id);
            String pluginXml = text(repository.find(prefix + "plugin.xml").orElse(null));
            List<UpdateSiteArtifactDescriptor> artifacts = nested.stream()
                    .map(entry -> artifact(entry, id))
                    .filter(item -> UpdateSiteArtifactKinds.relevant(item.kind()))
                    .sorted(Comparator.comparing(UpdateSiteArtifactDescriptor::path)).toList();
            List<UpdateSiteDependency> dependencies = parseRequireBundle(id, headers.getOrDefault("Require-Bundle", ""));
            P2BundleClassifier.Classification classification = classifier.classify(id, headers, artifacts, pluginXml);
            result.add(new MutableBundle(id, version, name, archive.path(), classification.kind(), classification.signals(), dependencies, artifacts, headers));
        }
        return result.stream().sorted(Comparator.comparing(bundle -> bundle.id)).toList();
    }

    /** Descripción: Ejecuta la operación «discoverFeatures» correspondiente a la responsabilidad de MutableBundle. Entradas: repository: dato de entrada necesario para la operación; metadata: dato de entrada necesario para la operación; availableBundleIds: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<UpdateSiteFeatureDescriptor> discoverFeatures(UpdateSiteRepositoryContent repository,
            P2MetadataSnapshot metadata, Set<String> availableBundleIds) {
        Map<String, UpdateSiteFeatureDescriptor> result = new LinkedHashMap<>();
        for(UpdateSiteRepositoryArtifact entry : repository.artifacts().stream()
                .filter(item -> item.path().toLowerCase(Locale.ROOT).endsWith("feature.xml"))
                .sorted(Comparator.comparing(UpdateSiteRepositoryArtifact::path)).toList()) {
            byte[] featureContent = repository.find(entry.path()).orElse(new byte[0]);
            Document document = SecureXmlSupport.parse(featureContent, "Eclipse feature descriptor");
            Element root = document.getDocumentElement();
            String id = attr(root, "id");
            if(id.isBlank()) continue;
            String version = attr(root, "version");
            String name = attr(root, "label");
            List<String> plugins = new ArrayList<>();
            List<UpdateSiteDependency> dependencies = new ArrayList<>();
            NodeList pluginNodes = root.getElementsByTagName("plugin");
            for(int index = 0; index < pluginNodes.getLength(); index++) {
                Element plugin = element(pluginNodes.item(index));
                if(plugin == null) continue;
                String target = attr(plugin, "id");
                if(target.isBlank()) continue;
                plugins.add(target);
                dependencies.add(new UpdateSiteDependency(id, target, attr(plugin, "version"), false,
                        availableBundleIds.contains(target), "feature.xml"));
            }
            NodeList importNodes = root.getElementsByTagName("import");
            for(int index = 0; index < importNodes.getLength(); index++) {
                Element imported = element(importNodes.item(index));
                if(imported == null) continue;
                String target = attr(imported, "plugin");
                if(target.isBlank()) target = attr(imported, "feature");
                if(target.isBlank()) continue;
                dependencies.add(new UpdateSiteDependency(id, target, attr(imported, "version"), false,
                        availableBundleIds.contains(target) || result.containsKey(target), "feature.xml"));
            }
            result.put(id, new UpdateSiteFeatureDescriptor(id, version, name, entry.path(), plugins, dependencies));
        }

        for(P2MetadataSnapshot.P2Unit unit : metadata.units().values().stream().filter(P2MetadataSnapshot.P2Unit::featureGroup).toList()) {
            String featureId = unit.id().endsWith(".feature.group")
                    ? unit.id().substring(0, unit.id().length() - ".feature.group".length()) : unit.id();
            result.computeIfAbsent(featureId, ignored -> {
                List<UpdateSiteDependency> dependencies = unit.dependencies().stream().map(item -> new UpdateSiteDependency(
                        featureId, stripFeatureGroup(item.targetId()), item.versionRange(), item.optional(),
                        availableBundleIds.contains(stripFeatureGroup(item.targetId())), item.origin())).toList();
                return new UpdateSiteFeatureDescriptor(featureId, unit.version(), unit.name(), "content.xml", List.of(), dependencies);
            });
        }
        return result.values().stream().sorted(Comparator.comparing(UpdateSiteFeatureDescriptor::id)).toList();
    }

    /** Descripción: Ejecuta la operación «parseRequireBundle» correspondiente a la responsabilidad de MutableBundle. Entradas: sourceId: identificador que delimita el recurso objetivo; value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private List<UpdateSiteDependency> parseRequireBundle(String sourceId, String value) {
        List<UpdateSiteDependency> result = new ArrayList<>();
        for(String token : splitOsgiList(value)) {
            String[] parts = token.split(";");
            String target = parts[0].trim();
            if(target.isBlank()) continue;
            String version = "";
            boolean optional = false;
            for(int index = 1; index < parts.length; index++) {
                String part = parts[index].trim();
                if(part.startsWith("bundle-version=")) version = unquote(part.substring("bundle-version=".length()));
                if(part.equalsIgnoreCase("resolution:=optional")) optional = true;
            }
            result.add(new UpdateSiteDependency(sourceId, target, version, optional, false, "MANIFEST.MF"));
        }
        return List.copyOf(result);
    }

    /** Descripción: Ejecuta la operación «splitOsgiList» correspondiente a la responsabilidad de MutableBundle. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private List<String> splitOsgiList(String value) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for(int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if(character == '"') quoted = !quoted;
            if(character == ',' && !quoted) {
                result.add(current.toString());
                current.setLength(0);
            } else current.append(character);
        }
        if(!current.isEmpty()) result.add(current.toString());
        return result;
    }

    /** Descripción: Ejecuta la operación «dependencyCycles» correspondiente a la responsabilidad de MutableBundle. Entradas: bundles: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<List<String>> dependencyCycles(List<UpdateSiteBundleDescriptor> bundles) {
        Map<String, Set<String>> graph = new LinkedHashMap<>();
        Set<String> ids = bundles.stream().map(UpdateSiteBundleDescriptor::id).collect(java.util.stream.Collectors.toSet());
        for(UpdateSiteBundleDescriptor bundle : bundles) {
            graph.put(bundle.id(), bundle.dependencies().stream().map(UpdateSiteDependency::targetId)
                    .filter(ids::contains).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new)));
        }
        List<List<String>> cycles = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Set<String> active = new HashSet<>();
        Deque<String> stack = new ArrayDeque<>();
        for(String id : graph.keySet()) detectCycles(id, graph, visited, active, stack, cycles);
        return cycles.stream().map(cycle -> cycle.stream().toList()).distinct().sorted(Comparator.comparing(cycle -> String.join("/", cycle))).toList();
    }

    /** Descripción: Ejecuta la operación «detectCycles» correspondiente a la responsabilidad de MutableBundle. Entradas: node: dato de entrada necesario para la operación; graph: dato de entrada necesario para la operación; visited: dato de entrada necesario para la operación; active: dato de entrada necesario para la operación; stack: dato de entrada necesario para la operación; cycles: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void detectCycles(String node, Map<String, Set<String>> graph, Set<String> visited, Set<String> active,
            Deque<String> stack, List<List<String>> cycles) {
        if(active.contains(node)) {
            List<String> path = new ArrayList<>(stack);
            int start = path.indexOf(node);
            if(start >= 0) {
                List<String> cycle = new ArrayList<>(path.subList(start, path.size()));
                cycle.add(node);
                cycles.add(cycle);
            }
            return;
        }
        if(!visited.add(node)) return;
        active.add(node); stack.addLast(node);
        for(String target : graph.getOrDefault(node, Set.of())) detectCycles(target, graph, visited, active, stack, cycles);
        stack.removeLast(); active.remove(node);
    }

    /** Descripción: Obtiene la información correspondiente a «readManifest» dentro de MutableBundle. Entradas: content: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private Map<String, String> readManifest(byte[] content) {
        if(content == null) return Map.of();
        try {
            Manifest manifest = new Manifest(new ByteArrayInputStream(content));
            Map<String, String> result = new LinkedHashMap<>();
            for(Map.Entry<Object, Object> entry : manifest.getMainAttributes().entrySet()) {
                result.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
            }
            return Map.copyOf(result);
        } catch(IOException exception) {
            return Map.of("hydra.manifest.error", exception.getMessage());
        }
    }

    /** Descripción: Ejecuta la operación «artifact» correspondiente a la responsabilidad de MutableBundle. Entradas: path: ruta o ubicación utilizada por la operación; bundleId: identificador que delimita el recurso objetivo; content: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private UpdateSiteArtifactDescriptor artifact(String path, String bundleId, byte[] content) {
        return new UpdateSiteArtifactDescriptor(path, UpdateSiteArtifactKinds.fromPath(path), bundleId,
                content.length, UpdateSiteHashing.sha256(content));
    }

    /** Descripción: Ejecuta la operación «artifact» correspondiente a la responsabilidad de MutableBundle. Entradas: content: valor de entrada que debe procesarse; bundleId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private UpdateSiteArtifactDescriptor artifact(UpdateSiteRepositoryArtifact content, String bundleId) {
        return new UpdateSiteArtifactDescriptor(content.path(), UpdateSiteArtifactKinds.fromPath(content.path()), bundleId,
                content.size(), content.sha256());
    }

    /** Descripción: Ejecuta la operación «symbolicName» correspondiente a la responsabilidad de MutableBundle. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String symbolicName(String value) {
        int separator = value.indexOf(';');
        return (separator >= 0 ? value.substring(0, separator) : value).trim();
    }

    /** Descripción: Ejecuta la operación «idFromJarPath» correspondiente a la responsabilidad de MutableBundle. Entradas: path: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String idFromJarPath(String path) {
        String name = path.substring(path.lastIndexOf('/') + 1, path.length() - 4);
        int separator = name.lastIndexOf('_');
        return separator > 0 ? name.substring(0, separator) : name;
    }

    /** Descripción: Ejecuta la operación «versionFromJarPath» correspondiente a la responsabilidad de MutableBundle. Entradas: path: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String versionFromJarPath(String path) {
        String name = path.substring(path.lastIndexOf('/') + 1, path.length() - 4);
        int separator = name.lastIndexOf('_');
        return separator > 0 ? name.substring(separator + 1) : "";
    }

    /** Descripción: Evalúa la condición «isMetadataJar» sin modificar el estado externo. Entradas: path: ruta o ubicación utilizada por la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean isMetadataJar(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        return lower.endsWith("content.jar") || lower.endsWith("artifacts.jar");
    }

    /** Descripción: Evalúa la condición «isFeatureJar» sin modificar el estado externo. Entradas: path: ruta o ubicación utilizada por la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean isFeatureJar(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        return lower.startsWith("features/") || lower.contains("/features/");
    }

    /** Descripción: Ejecuta la operación «stripFeatureGroup» correspondiente a la responsabilidad de MutableBundle. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String stripFeatureGroup(String value) {
        return value.endsWith(".feature.group") ? value.substring(0, value.length() - ".feature.group".length()) : value;
    }

    /** Descripción: Ejecuta la operación «text» correspondiente a la responsabilidad de MutableBundle. Entradas: content: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String text(byte[] content) {
        return content == null ? "" : new String(content, StandardCharsets.UTF_8);
    }

    /** Descripción: Ejecuta la operación «unquote» correspondiente a la responsabilidad de MutableBundle. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String unquote(String value) {
        String trimmed = value.trim();
        return trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")
                ? trimmed.substring(1, trimmed.length() - 1) : trimmed;
    }

    /** Descripción: Ejecuta la operación «element» correspondiente a la responsabilidad de MutableBundle. Entradas: node: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Element element(Node node) { return node instanceof Element value ? value : null; }
    /** Descripción: Ejecuta la operación «attr» correspondiente a la responsabilidad de MutableBundle. Entradas: element: dato de entrada necesario para la operación; name: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String attr(Element element, String name) { return element.hasAttribute(name) ? element.getAttribute(name).trim() : ""; }

    private record MutableBundle(String id, String version, String name, String sourcePath,
            UpdateSiteBundleKind kind, List<String> signals, List<UpdateSiteDependency> dependencies,
            List<UpdateSiteArtifactDescriptor> artifacts, Map<String, String> headers) {
        /** Descripción: Ejecuta la operación «freeze» correspondiente a la responsabilidad de MutableBundle. Entradas: availableBundleIds: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private UpdateSiteBundleDescriptor freeze(Set<String> availableBundleIds) {
            List<UpdateSiteDependency> resolved = dependencies.stream().map(item -> new UpdateSiteDependency(
                    item.sourceId(), item.targetId(), item.versionRange(), item.optional(),
                    availableBundleIds.contains(item.targetId()), item.origin())).toList();
            return new UpdateSiteBundleDescriptor(id, version, name, kind, sourcePath, signals, resolved, artifacts, headers);
        }
    }
}
