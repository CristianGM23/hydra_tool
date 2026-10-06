package com.example.editor.projection.drawio;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/** Registro central de exportadores draw.io basado en plugins de Spring. */
@Service
public class DrawioProjectionService {
    private final Map<String, DrawioArtifactExporter<?>> exporters;
    /** Descripción: Inicializa una instancia de DrawioProjectionService con sus dependencias y datos de entrada. Entradas: exporters: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public DrawioProjectionService(List<DrawioArtifactExporter<?>> exporters) {
        Map<String, DrawioArtifactExporter<?>> index = new LinkedHashMap<>();
        for(DrawioArtifactExporter<?> exporter : exporters) {
            DrawioArtifactExporter<?> previous = index.putIfAbsent(exporter.artifactType(), exporter);
            if(previous != null) throw new IllegalStateException("Duplicated draw.io artifact type: " + exporter.artifactType());
        }
        this.exporters = Map.copyOf(index);
    }
    /** Descripción: Ejecuta la operación «artifactTypes» correspondiente a la responsabilidad de DrawioProjectionService. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public List<String> artifactTypes() { return exporters.keySet().stream().sorted().toList(); }
    /** Descripción: Ejecuta la operación «export» correspondiente a la responsabilidad de DrawioProjectionService. Entradas: artifactType: dato de entrada necesario para la operación; source: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public String export(String artifactType, Object source) { return export(artifactType, source, DrawioExportOptions.defaults()); }
    /** Descripción: Ejecuta la operación «export» correspondiente a la responsabilidad de DrawioProjectionService. Entradas: artifactType: dato de entrada necesario para la operación; source: dato de entrada necesario para la operación; options: configuración que gobierna el comportamiento. Salida: devuelve el resultado definido por el contrato del método. */ public String export(String artifactType, Object source, DrawioExportOptions options) {
        DrawioArtifactExporter<?> exporter = exporters.get(artifactType);
        if(exporter == null) throw new IllegalArgumentException("No draw.io exporter is registered for artifact type: " + artifactType);
        if(source == null || !exporter.sourceType().isInstance(source)) throw new IllegalArgumentException("Draw.io exporter " + artifactType + " requires " + exporter.sourceType().getName());
        return exportTyped(exporter, source, options == null ? DrawioExportOptions.defaults() : options);
    }
    /** Descripción: Ejecuta la operación «exportTyped» correspondiente a la responsabilidad de DrawioProjectionService. Entradas: exporter: dato de entrada necesario para la operación; source: dato de entrada necesario para la operación; options: configuración que gobierna el comportamiento. Salida: devuelve el resultado definido por el contrato del método. */ private <T> String exportTyped(DrawioArtifactExporter<T> exporter, Object source, DrawioExportOptions options) { return exporter.export(exporter.sourceType().cast(source), options); }
}
