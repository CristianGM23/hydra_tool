package com.example.editor.portability.archive;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.example.editor.portability.CapabilityDependency;
import com.example.editor.portability.CapabilitySelection;
import com.example.editor.portability.HydraCapabilityPackageManifest;

/** Ordena capacidades de forma determinista colocando primero sus dependencias transitivas. */
@Component
public class CapabilityPackageDependencyResolver {

    /** Descripción: Obtiene la información correspondiente a «resolve» dentro de CapabilityPackageDependencyResolver. Entradas: manifest: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public List<CapabilitySelection> resolve(HydraCapabilityPackageManifest manifest) {
        Map<String, CapabilitySelection> selections = new LinkedHashMap<>();
        manifest.capabilities().forEach(item -> selections.put(item.id(), item));
        Map<String, List<String>> dependencies = new LinkedHashMap<>();
        for(CapabilityDependency dependency : manifest.dependencies()) {
            if(selections.containsKey(dependency.sourceCapabilityId()) && selections.containsKey(dependency.targetCapabilityId()))
                dependencies.computeIfAbsent(dependency.sourceCapabilityId(), ignored -> new ArrayList<>()).add(dependency.targetCapabilityId());
        }
        dependencies.values().forEach(values -> values.sort(String::compareTo));
        List<CapabilitySelection> result = new ArrayList<>();
        Set<String> visited = new LinkedHashSet<>();
        Set<String> active = new LinkedHashSet<>();
        selections.keySet().stream().sorted().forEach(id -> visit(id, selections, dependencies, visited, active, result));
        return List.copyOf(result);
    }

    /** Descripción: Ejecuta la operación «visit» correspondiente a la responsabilidad de CapabilityPackageDependencyResolver. Entradas: id: identificador que delimita el recurso objetivo; selections: dato de entrada necesario para la operación; dependencies: dato de entrada necesario para la operación; visited: dato de entrada necesario para la operación; active: dato de entrada necesario para la operación; result: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void visit(String id, Map<String, CapabilitySelection> selections, Map<String, List<String>> dependencies,
            Set<String> visited, Set<String> active, List<CapabilitySelection> result) {
        if(visited.contains(id)) return;
        if(!active.add(id)) throw new CapabilityPackageArchiveException(CapabilityPackageArchiveException.Reason.INVALID_PACKAGE,
                "Capability dependencies contain a cycle at: " + id);
        for(String dependency : dependencies.getOrDefault(id, List.of())) visit(dependency, selections, dependencies, visited, active, result);
        active.remove(id);
        visited.add(id);
        CapabilitySelection selection = selections.get(id);
        if(selection != null) result.add(selection);
    }
}
