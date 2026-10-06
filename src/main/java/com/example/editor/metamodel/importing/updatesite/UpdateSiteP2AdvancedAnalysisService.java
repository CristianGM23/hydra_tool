package com.example.editor.metamodel.importing.updatesite;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/** Analiza repositorios compuestos y familias de versiones sin descargar contenido remoto. */
@Component
public class UpdateSiteP2AdvancedAnalysisService {
    private static final Pattern CHILD = Pattern.compile("<child\\s+[^>]*location=\"([^\"]+)\"[^>]*/?>", Pattern.CASE_INSENSITIVE);

    /** Descripción: Ejecuta la operación «analyze» correspondiente a la responsabilidad de UpdateSiteP2AdvancedAnalysisService. Entradas: repository: dato de entrada necesario para la operación; summary: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ UpdateSiteReleaseComparison analyze(UpdateSiteRepositoryContent repository, UpdateSiteRepositorySummary summary) {
        List<UpdateSiteCompositeRepository> composites = compositeRepositories(repository);
        List<UpdateSiteVersionFamily> families = versionFamilies(summary);
        List<UpdateSiteVersionDelta> deltas = deltas(summary, families);
        int unresolved = (int) composites.stream().filter(value -> !value.presentInUpload()).count();
        List<String> diagnostics = new ArrayList<>();
        if(unresolved > 0) diagnostics.add(unresolved + " composite child repository or repositories were declared but not included in the uploaded source.");
        if(families.stream().noneMatch(UpdateSiteVersionFamily::multipleVersions)) diagnostics.add("No multiple bundle or feature versions were found in this repository snapshot.");
        return new UpdateSiteReleaseComparison(composites, families, deltas, unresolved, diagnostics);
    }

    /** Descripción: Ejecuta la operación «compositeRepositories» correspondiente a la responsabilidad de UpdateSiteP2AdvancedAnalysisService. Entradas: repository: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<UpdateSiteCompositeRepository> compositeRepositories(UpdateSiteRepositoryContent repository) {
        List<UpdateSiteCompositeRepository> result = new ArrayList<>();
        repository.files().entrySet().stream().filter(entry -> {
            String lower = entry.getKey().toLowerCase(Locale.ROOT);
            return lower.endsWith("compositecontent.xml") || lower.endsWith("compositeartifacts.xml");
        }).sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            Matcher matcher = CHILD.matcher(new String(entry.getValue(), StandardCharsets.UTF_8));
            while(matcher.find()) {
                String location = matcher.group(1).trim();
                boolean remote = location.startsWith("http://") || location.startsWith("https://");
                String normalized = location.replace('\\', '/').replaceAll("^\\./", "");
                boolean present = repository.files().keySet().stream().anyMatch(path -> path.startsWith(normalized + "/") || path.contains("!/" + normalized + "/"));
                String explanation = remote
                        ? "Remote child declared by p2 metadata. HYDRA records it but does not download it unless a controlled remote-import policy is explicitly enabled."
                        : present ? "Child repository content is present in the uploaded source."
                                : "Child repository is referenced but its files were not included; upload that child to obtain a complete analysis.";
                result.add(new UpdateSiteCompositeRepository(entry.getKey(), location, remote, present, explanation));
            }
        });
        return result.stream().distinct().toList();
    }

    /** Descripción: Ejecuta la operación «versionFamilies» correspondiente a la responsabilidad de UpdateSiteP2AdvancedAnalysisService. Entradas: summary: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<UpdateSiteVersionFamily> versionFamilies(UpdateSiteRepositorySummary summary) {
        Map<String, Set<String>> values = new LinkedHashMap<>();
        summary.bundles().forEach(bundle -> values.computeIfAbsent("BUNDLE:" + bundle.id(), ignored -> new LinkedHashSet<>()).add(bundle.version()));
        summary.features().forEach(feature -> values.computeIfAbsent("FEATURE:" + feature.id(), ignored -> new LinkedHashSet<>()).add(feature.version()));
        List<UpdateSiteVersionFamily> result = new ArrayList<>();
        values.forEach((key, versions) -> {
            List<String> sorted = versions.stream().sorted(this::compareVersions).toList();
            String type = key.substring(0, key.indexOf(':'));
            String id = key.substring(key.indexOf(':') + 1);
            boolean multiple = sorted.size() > 1;
            result.add(new UpdateSiteVersionFamily(id, type, sorted, sorted.getFirst(), sorted.getLast(), multiple,
                    multiple ? "Review the latest version and compare model-bearing artifacts before importing."
                            : "Only one version is present in this snapshot."));
        });
        return result.stream().sorted(Comparator.comparing(UpdateSiteVersionFamily::artifactType).thenComparing(UpdateSiteVersionFamily::id)).toList();
    }

    /** Descripción: Ejecuta la operación «deltas» correspondiente a la responsabilidad de UpdateSiteP2AdvancedAnalysisService. Entradas: summary: dato de entrada necesario para la operación; families: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<UpdateSiteVersionDelta> deltas(UpdateSiteRepositorySummary summary, List<UpdateSiteVersionFamily> families) {
        List<UpdateSiteVersionDelta> result = new ArrayList<>();
        for(UpdateSiteVersionFamily family : families) {
            if(!family.multipleVersions() || !family.artifactType().equals("BUNDLE")) continue;
            UpdateSiteBundleDescriptor oldest = summary.bundles().stream().filter(item -> item.id().equals(family.id()) && item.version().equals(family.oldestVersion())).findFirst().orElse(null);
            UpdateSiteBundleDescriptor latest = summary.bundles().stream().filter(item -> item.id().equals(family.id()) && item.version().equals(family.latestVersion())).findFirst().orElse(null);
            if(oldest == null || latest == null) continue;
            Map<String, String> oldArtifacts = artifactHashes(oldest);
            Map<String, String> newArtifacts = artifactHashes(latest);
            Set<String> oldPaths = oldArtifacts.keySet();
            Set<String> newPaths = newArtifacts.keySet();
            List<String> added = newPaths.stream().filter(path -> !oldPaths.contains(path)).sorted().toList();
            List<String> removed = oldPaths.stream().filter(path -> !newPaths.contains(path)).sorted().toList();
            List<String> changed = newPaths.stream().filter(oldPaths::contains)
                    .filter(path -> !oldArtifacts.get(path).equals(newArtifacts.get(path))).sorted().toList();
            int unchanged = (int) newPaths.stream().filter(oldPaths::contains)
                    .filter(path -> oldArtifacts.get(path).equals(newArtifacts.get(path))).count();
            boolean modelBearing = latest.kind() == UpdateSiteBundleKind.MODEL_PLUGIN || latest.artifacts().stream().anyMatch(item ->
                    item.kind() == UpdateSiteArtifactKind.ECORE || item.kind() == UpdateSiteArtifactKind.GENMODEL
                            || item.kind() == UpdateSiteArtifactKind.XTEXT_GRAMMAR || item.kind() == UpdateSiteArtifactKind.LANGIUM_GRAMMAR);
            UpdateSiteReleaseRisk risk = risk(modelBearing, added.size() + removed.size() + changed.size());
            String explanation = modelBearing
                    ? "The bundle contains model or grammar artifacts; content changes may affect the recovered metamodel or editor layers."
                    : "The delta is primarily runtime/UI structure and does not directly prove a metamodel change.";
            String recommendation = recommendation(risk, modelBearing);
            result.add(new UpdateSiteVersionDelta(family.id(), family.artifactType(), family.oldestVersion(), family.latestVersion(),
                    added, removed, changed, unchanged, modelBearing, risk, explanation, recommendation));
        }
        return List.copyOf(result);
    }

    /** Descripción: Ejecuta la operación «artifactHashes» correspondiente a la responsabilidad de UpdateSiteP2AdvancedAnalysisService. Entradas: bundle: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Map<String, String> artifactHashes(UpdateSiteBundleDescriptor bundle) {
        Map<String, String> result = new LinkedHashMap<>();
        bundle.artifacts().stream().sorted(Comparator.comparing(UpdateSiteArtifactDescriptor::path))
                .forEach(item -> result.put(relative(item.path(), bundle.sourcePath()), item.sha256()));
        return result;
    }

    /** Descripción: Ejecuta la operación «risk» correspondiente a la responsabilidad de UpdateSiteP2AdvancedAnalysisService. Entradas: modelBearing: dato de entrada necesario para la operación; changes: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private UpdateSiteReleaseRisk risk(boolean modelBearing, int changes) {
        if(changes == 0) return UpdateSiteReleaseRisk.NONE;
        if(modelBearing) return UpdateSiteReleaseRisk.HIGH;
        return changes > 10 ? UpdateSiteReleaseRisk.MEDIUM : UpdateSiteReleaseRisk.LOW;
    }

    /** Descripción: Ejecuta la operación «recommendation» correspondiente a la responsabilidad de UpdateSiteP2AdvancedAnalysisService. Entradas: risk: dato de entrada necesario para la operación; modelBearing: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String recommendation(UpdateSiteReleaseRisk risk, boolean modelBearing) {
        return switch(risk) {
            case NONE -> "No artifact content changes were detected between the compared versions.";
            case LOW -> "Review the runtime/UI delta; the recovered metamodel is unlikely to change directly.";
            case MEDIUM -> "Inspect added, removed and changed files before selecting the latest bundle.";
            case HIGH -> modelBearing
                    ? "Re-run metamodel, grammar and layer recovery for the latest version and reconcile it explicitly."
                    : "Review the complete bundle delta before importing the latest version.";
        };
    }

    /** Descripción: Ejecuta la operación «relative» correspondiente a la responsabilidad de UpdateSiteP2AdvancedAnalysisService. Entradas: path: ruta o ubicación utilizada por la operación; source: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String relative(String path, String source) {
        int marker = path.indexOf("!/");
        return marker >= 0 ? path.substring(marker + 2) : path.replace(source, "");
    }

    /** Descripción: Ejecuta la operación «compareVersions» correspondiente a la responsabilidad de UpdateSiteP2AdvancedAnalysisService. Entradas: left: dato de entrada necesario para la operación; right: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private int compareVersions(String left, String right) {
        String[] a = left.split("[._-]");
        String[] b = right.split("[._-]");
        for(int index = 0; index < Math.max(a.length, b.length); index++) {
            String av = index < a.length ? a[index] : "0";
            String bv = index < b.length ? b[index] : "0";
            int compared;
            try { compared = Integer.compare(Integer.parseInt(av), Integer.parseInt(bv)); }
            catch(NumberFormatException exception) { compared = av.compareToIgnoreCase(bv); }
            if(compared != 0) return compared;
        }
        return left.compareTo(right);
    }
}
