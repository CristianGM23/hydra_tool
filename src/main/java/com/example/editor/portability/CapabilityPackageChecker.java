package com.example.editor.portability;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

/** Comprueba identidad, dependencias, compatibilidad y hashes sin ejecutar artefactos. */
@Component
public class CapabilityPackageChecker {

    /** Descripción: Valida las condiciones necesarias para «check» y rechaza entradas no válidas. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ public CapabilityPackageCheckReport check(HydraCapabilityPackage value) {
        List<CapabilityPackageDiagnostic> diagnostics = new ArrayList<>();
        if(value == null || value.manifest() == null) {
            diagnostics.add(error("PACKAGE_REQUIRED", "", "$", "A capability package and its manifest are required"));
            return new CapabilityPackageCheckReport("", diagnostics);
        }

        HydraCapabilityPackageManifest manifest = value.manifest();
        require(manifest.id(), "PACKAGE_ID_REQUIRED", "$manifest.id", diagnostics);
        require(manifest.name(), "PACKAGE_NAME_REQUIRED", "$manifest.name", diagnostics);
        require(manifest.semanticScopeId(), "SEMANTIC_SCOPE_ID_REQUIRED", "$manifest.semanticScopeId", diagnostics);
        if(manifest.metamodelId().isBlank() && manifest.capabilities().stream().noneMatch(selection -> selection != null && selection.kind() == HydraCapabilityKind.GRAMMAR)) {
            diagnostics.add(error("SEMANTIC_ROOT_REQUIRED", manifest.id(), "$manifest.capabilities", "A package without metamodelId must carry a GRAMMAR semantic root"));
        }
        if(!HydraCapabilityPackageManifest.CURRENT_SCHEMA_VERSION.equals(manifest.schemaVersion())) {
            diagnostics.add(error("UNSUPPORTED_PACKAGE_SCHEMA", manifest.id(), "$manifest.schemaVersion", "Unsupported capability package schema: " + manifest.schemaVersion()));
        }

        Map<String, CapabilitySelection> selections = new LinkedHashMap<>();
        for(int index = 0; index < manifest.capabilities().size(); index++) {
            CapabilitySelection selection = manifest.capabilities().get(index);
            String path = "$manifest.capabilities[" + index + "]";
            if(selection == null || selection.id().isBlank() || selection.kind() == null || selection.documentId().isBlank()) {
                diagnostics.add(error("INVALID_CAPABILITY", manifest.id(), path, "A capability requires id, kind and documentId"));
                continue;
            }
            if(selections.put(selection.id(), selection) != null) diagnostics.add(error("DUPLICATE_CAPABILITY_ID", selection.id(), path + ".id", "Capability identifiers must be unique"));
            if(selection.required() && !value.canonicalDocuments().containsKey(selection.documentId())) diagnostics.add(error("REQUIRED_DOCUMENT_MISSING", selection.id(), path + ".documentId", "Required canonical document is not embedded in the package"));
            if(!selection.required() && !value.canonicalDocuments().containsKey(selection.documentId())) diagnostics.add(warning("OPTIONAL_DOCUMENT_MISSING", selection.id(), path + ".documentId", "Optional canonical document is not embedded in the package"));
        }

        checkDependencies(manifest, selections, diagnostics);
        checkCompatibility(manifest, selections, diagnostics);
        checkChecksums(manifest, diagnostics);
        return new CapabilityPackageCheckReport(manifest.id(), diagnostics);
    }

    /** Descripción: Valida las condiciones necesarias para «checkDependencies» y rechaza entradas no válidas. Entradas: manifest: dato de entrada necesario para la operación; selections: dato de entrada necesario para la operación; diagnostics: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void checkDependencies(HydraCapabilityPackageManifest manifest, Map<String, CapabilitySelection> selections, List<CapabilityPackageDiagnostic> diagnostics) {
        Map<String, Set<String>> graph = new HashMap<>();
        for(int index = 0; index < manifest.dependencies().size(); index++) {
            CapabilityDependency dependency = manifest.dependencies().get(index);
            String path = "$manifest.dependencies[" + index + "]";
            if(dependency == null) {
                diagnostics.add(error("INVALID_DEPENDENCY", manifest.id(), path, "Dependency must not be null"));
                continue;
            }
            if(!selections.containsKey(dependency.sourceCapabilityId())) diagnostics.add(error("DEPENDENCY_SOURCE_MISSING", dependency.sourceCapabilityId(), path + ".sourceCapabilityId", "Dependency source is not selected"));
            CapabilitySelection target = selections.get(dependency.targetCapabilityId());
            if(target == null) {
                CapabilityPackageDiagnostic diagnostic = dependency.required() ? error("DEPENDENCY_TARGET_MISSING", dependency.targetCapabilityId(), path + ".targetCapabilityId", "Required dependency target is not selected") : warning("OPTIONAL_DEPENDENCY_TARGET_MISSING", dependency.targetCapabilityId(), path + ".targetCapabilityId", "Optional dependency target is not selected");
                diagnostics.add(diagnostic);
            } else if(!CapabilityVersionRange.valid(dependency.versionRange())) {
                diagnostics.add(error("INVALID_DEPENDENCY_VERSION_RANGE", dependency.targetCapabilityId(), path + ".versionRange", "Dependency version range is invalid: " + dependency.versionRange()));
            } else if(!CapabilityVersionRange.supports(target.schemaVersion(), dependency.versionRange())) {
                diagnostics.add(error("DEPENDENCY_VERSION_MISMATCH", dependency.targetCapabilityId(), path + ".versionRange", "Selected capability version " + target.schemaVersion() + " does not satisfy " + dependency.versionRange()));
            }
            graph.computeIfAbsent(dependency.sourceCapabilityId(), key -> new HashSet<>()).add(dependency.targetCapabilityId());
        }
        if(hasCycle(graph)) diagnostics.add(error("CAPABILITY_DEPENDENCY_CYCLE", manifest.id(), "$manifest.dependencies", "Capability dependencies contain a cycle"));
    }

    /** Descripción: Valida las condiciones necesarias para «checkCompatibility» y rechaza entradas no válidas. Entradas: manifest: dato de entrada necesario para la operación; selections: dato de entrada necesario para la operación; diagnostics: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void checkCompatibility(HydraCapabilityPackageManifest manifest, Map<String, CapabilitySelection> selections, List<CapabilityPackageDiagnostic> diagnostics) {
        Set<HydraCapabilityKind> selectedKinds = new HashSet<>();
        selections.values().forEach(item -> selectedKinds.add(item.kind()));
        for(int index = 0; index < manifest.compatibility().size(); index++) {
            TargetCapabilityCompatibility item = manifest.compatibility().get(index);
            String path = "$manifest.compatibility[" + index + "]";
            if(item == null || item.technology() == null || item.capability() == null) {
                diagnostics.add(error("INVALID_COMPATIBILITY", manifest.id(), path, "Compatibility requires technology and capability"));
                continue;
            }
            if(!selectedKinds.contains(item.capability())) diagnostics.add(warning("COMPATIBILITY_FOR_UNSELECTED_CAPABILITY", item.capability().name(), path + ".capability", "Compatibility refers to a capability that is not selected"));
            if(item.fidelity() == CapabilityFidelity.UNSUPPORTED) diagnostics.add(warning("UNSUPPORTED_TARGET_CAPABILITY", item.capability().name(), path + ".fidelity", "The selected target cannot translate this capability"));
            if(item.fidelity() != CapabilityFidelity.UNSUPPORTED && item.nativeLanguage().isBlank()) diagnostics.add(warning("NATIVE_LANGUAGE_NOT_DECLARED", item.capability().name(), path + ".nativeLanguage", "A technological translation should declare its native language or format"));
        }
    }

    /** Descripción: Valida las condiciones necesarias para «checkChecksums» y rechaza entradas no válidas. Entradas: manifest: dato de entrada necesario para la operación; diagnostics: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void checkChecksums(HydraCapabilityPackageManifest manifest, List<CapabilityPackageDiagnostic> diagnostics) {
        manifest.checksums().forEach((path, checksum) -> {
            if(path.isBlank() || !checksum.matches("(?i)[a-f0-9]{64}")) diagnostics.add(error("INVALID_SHA256", path, "$manifest.checksums", "Checksums must use a relative path and a 64-character SHA-256 value"));
        });
    }

    /** Descripción: Evalúa la condición «hasCycle» sin modificar el estado externo. Entradas: graph: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean hasCycle(Map<String, Set<String>> graph) {
        Set<String> visited = new HashSet<>();
        Set<String> active = new HashSet<>();
        for(String node : graph.keySet()) if(detectCycle(node, graph, visited, active)) return true;
        return false;
    }

    /** Descripción: Ejecuta la operación «detectCycle» correspondiente a la responsabilidad de CapabilityPackageChecker. Entradas: node: dato de entrada necesario para la operación; graph: dato de entrada necesario para la operación; visited: dato de entrada necesario para la operación; active: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean detectCycle(String node, Map<String, Set<String>> graph, Set<String> visited, Set<String> active) {
        if(active.contains(node)) return true;
        if(!visited.add(node)) return false;
        active.add(node);
        ArrayDeque<String> targets = new ArrayDeque<>(graph.getOrDefault(node, Set.of()));
        while(!targets.isEmpty()) if(detectCycle(targets.removeFirst(), graph, visited, active)) return true;
        active.remove(node);
        return false;
    }

    /** Descripción: Valida las condiciones necesarias para «require» y rechaza entradas no válidas. Entradas: value: valor de entrada que debe procesarse; code: dato de entrada necesario para la operación; path: ruta o ubicación utilizada por la operación; diagnostics: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void require(String value, String code, String path, List<CapabilityPackageDiagnostic> diagnostics) {
        if(value == null || value.isBlank()) diagnostics.add(error(code, "", path, "Required value is missing"));
    }

    /** Descripción: Ejecuta la operación «error» correspondiente a la responsabilidad de CapabilityPackageChecker. Entradas: code: dato de entrada necesario para la operación; elementId: identificador que delimita el recurso objetivo; path: ruta o ubicación utilizada por la operación; message: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private CapabilityPackageDiagnostic error(String code, String elementId, String path, String message) {
        return new CapabilityPackageDiagnostic(CapabilityPackageDiagnosticSeverity.ERROR, code, elementId, path, message);
    }

    /** Descripción: Ejecuta la operación «warning» correspondiente a la responsabilidad de CapabilityPackageChecker. Entradas: code: dato de entrada necesario para la operación; elementId: identificador que delimita el recurso objetivo; path: ruta o ubicación utilizada por la operación; message: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private CapabilityPackageDiagnostic warning(String code, String elementId, String path, String message) {
        return new CapabilityPackageDiagnostic(CapabilityPackageDiagnosticSeverity.WARNING, code, elementId, path, message);
    }
}
