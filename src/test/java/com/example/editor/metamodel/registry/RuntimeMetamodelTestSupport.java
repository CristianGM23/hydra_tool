package com.example.editor.metamodel.registry;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.example.editor.config.StorageProperties;
import com.example.editor.emf.LoadedMetamodel;
import com.example.editor.emf.MetamodelLoader;
import com.example.editor.metamodel.MetamodelIntrospectionService;
import com.example.editor.metamodel.registry.MetamodelRegistryException.Reason;

/**
 * Utilidades de prueba para consumidores del registro runtime.
 *
 * No vuelve a introducir la antigua operación de subida Ecore. Los tests reciben
 * metamodelos ya disponibles, igual que sucede después de generar una proyección
 * Ecore válida desde el MetaModel canónico.
 */
public final class RuntimeMetamodelTestSupport {

    private RuntimeMetamodelTestSupport() {
    }

    public static RegisteredMetamodel load(String id, String fileName, Path path, MetamodelLoader loader) {
        LoadedMetamodel loaded = loader.load(path);
        return new RegisteredMetamodel(id, fileName, new MetamodelIntrospectionService().describe(loaded));
    }

    public static MetamodelRegistryService fixedRegistry(StorageProperties storage, MetamodelLoader loader, List<RegisteredMetamodel> metamodels) {
        return fixedRegistry(storage, loader, metamodels, List.of());
    }

    public static MetamodelRegistryService fixedRegistry(StorageProperties storage, MetamodelLoader loader, List<RegisteredMetamodel> metamodels, List<MetamodelRegistryService.RuntimeMetamodelStatus> runtimeStatuses) {
        Map<String, RegisteredMetamodel> byId = new LinkedHashMap<>();
        for(RegisteredMetamodel metamodel : metamodels) {
            byId.put(metamodel.id(), metamodel);
        }

        Map<String, MetamodelRegistryService.RuntimeMetamodelStatus> statusById = new LinkedHashMap<>();
        for(MetamodelRegistryService.RuntimeMetamodelStatus status : runtimeStatuses) {
            statusById.put(status.metamodelId(), status);
        }

        return new MetamodelRegistryService(storage, loader, new MetamodelIntrospectionService(), null, null) {
            @Override
            public synchronized void initialize() {
                // Registro fijo utilizado únicamente por pruebas unitarias.
            }

            @Override
            public synchronized List<RegisteredMetamodel> list() {
                return byId.values().stream().sorted(java.util.Comparator.comparing(RegisteredMetamodel::id)).toList();
            }

            @Override
            public synchronized List<RuntimeMetamodelStatus> statuses() {
                return statusById.values().stream().sorted(java.util.Comparator.comparing(RuntimeMetamodelStatus::metamodelId)).toList();
            }

            @Override
            public synchronized RuntimeMetamodelStatus status(String id) {
                RuntimeMetamodelStatus status = statusById.get(id);
                if(status == null) {
                    throw new MetamodelRegistryException(Reason.NOT_FOUND, "Canonical metamodel was not found in the runtime registry: " + id);
                }
                return status;
            }

            @Override
            public synchronized RegisteredMetamodel find(String id) {
                RegisteredMetamodel metamodel = byId.get(id);
                if(metamodel == null) {
                    throw new MetamodelRegistryException(Reason.NOT_FOUND, "Metamodel was not found: " + id);
                }
                return metamodel;
            }
        };
    }
}
