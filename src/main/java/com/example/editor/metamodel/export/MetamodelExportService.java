package com.example.editor.metamodel.export;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.editor.metamodel.adapter.ecore.EcoreMetamodelAdapter;
import com.example.editor.metamodel.design.MetamodelDesignException;
import com.example.editor.metamodel.MetamodelIdPolicy;
import com.example.editor.metamodel.design.MetamodelDesignException.Reason;

/**
 * Coordina todas las exportaciones desde el núcleo neutral.
 */
@Service
public class MetamodelExportService {

    private final NeutralMetamodelExportContextLoader contextLoader;
    private final EcoreMetamodelAdapter ecoreAdapter;
    private final Map<MetamodelExportFormat, MetamodelTextExporter> textExporters;
    private final Map<MetamodelExportFormat, MetamodelBinaryExporter> binaryExporters;

    @Autowired
    /** Descripción: Inicializa una instancia de MetamodelExportService con sus dependencias y datos de entrada. Entradas: contextLoader: dato de entrada necesario para la operación; ecoreAdapter: dato de entrada necesario para la operación; availableTextExporters: dato de entrada necesario para la operación; availableBinaryExporters: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public MetamodelExportService(NeutralMetamodelExportContextLoader contextLoader, EcoreMetamodelAdapter ecoreAdapter, List<MetamodelTextExporter> availableTextExporters, List<MetamodelBinaryExporter> availableBinaryExporters) {
        this.contextLoader = contextLoader;
        this.ecoreAdapter = ecoreAdapter;
        this.textExporters = indexTextExporters(availableTextExporters);
        this.binaryExporters = indexBinaryExporters(availableBinaryExporters);
    }


    /** Descripción: Obtiene la información correspondiente a «listFormats» dentro de MetamodelExportService. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public List<MetamodelExportFormatResponse> listFormats() {
        return java.util.Arrays.stream(MetamodelExportFormat.values()).map(format -> descriptor(format, format.category().name())).toList();
    }

    /** Descripción: Ejecuta la operación «export» correspondiente a la responsabilidad de MetamodelExportService. Entradas: metamodelId: identificador que delimita el recurso objetivo; formatId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public MetamodelExportArtifact export(String metamodelId, String formatId) {
        return export(metamodelId, formatId, MetamodelExportOptions.defaults());
    }

    /** Descripción: Ejecuta la operación «export» correspondiente a la responsabilidad de MetamodelExportService. Entradas: metamodelId: identificador que delimita el recurso objetivo; formatId: identificador que delimita el recurso objetivo; options: configuración que gobierna el comportamiento. Salida: devuelve el resultado definido por el contrato del método. */ public MetamodelExportArtifact export(String metamodelId, String formatId, MetamodelExportOptions options) {
        validateMetamodelId(metamodelId);
        MetamodelExportFormat format = MetamodelExportFormat.fromId(formatId);

        if(format == MetamodelExportFormat.BUNDLE) {
            return exportBundle(metamodelId, defaultBundleFormats(), options);
        }

        NeutralMetamodelExportContext context = contextLoader.load(metamodelId, options);
        return exportLoaded(metamodelId, format, context);
    }

    /** Descripción: Ejecuta la operación «exportBundle» correspondiente a la responsabilidad de MetamodelExportService. Entradas: metamodelId: identificador que delimita el recurso objetivo; formatIds: dato de entrada necesario para la operación; options: configuración que gobierna el comportamiento. Salida: devuelve el resultado definido por el contrato del método. */ public MetamodelExportArtifact exportBundle(String metamodelId, List<String> formatIds, MetamodelExportOptions options) {
        validateMetamodelId(metamodelId);
        Set<MetamodelExportFormat> formats = EnumSet.noneOf(MetamodelExportFormat.class);

        for(String formatId : formatIds) {
            MetamodelExportFormat format = MetamodelExportFormat.fromId(formatId);

            if(format != MetamodelExportFormat.BUNDLE) {
                formats.add(format);
            }
        }

        if(formats.isEmpty()) {
            throw new MetamodelDesignException(Reason.INVALID_REQUEST, "At least one export format must be selected");
        }

        NeutralMetamodelExportContext context = contextLoader.load(metamodelId, options);
        byte[] content = createBundle(metamodelId, List.copyOf(formats), context);
        return new MetamodelExportArtifact(metamodelId + MetamodelExportFormat.BUNDLE.extension(), MetamodelExportFormat.BUNDLE.mediaType(), content);
    }

    /** Descripción: Ejecuta la operación «exportLoaded» correspondiente a la responsabilidad de MetamodelExportService. Entradas: metamodelId: identificador que delimita el recurso objetivo; format: dato de entrada necesario para la operación; context: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private MetamodelExportArtifact exportLoaded(String metamodelId, MetamodelExportFormat format, NeutralMetamodelExportContext context) {
        if(format == MetamodelExportFormat.ECORE) {
            return new MetamodelExportArtifact(metamodelId + format.extension(), format.mediaType(), ecoreAdapter.toBytes(context.neutralMetamodel(), context.targets().ecore()).value());
        }

        MetamodelTextExporter textExporter = textExporters.get(format);

        if(textExporter != null) {
            return new MetamodelExportArtifact(metamodelId + format.extension(), format.mediaType(), textExporter.export(context).getBytes(StandardCharsets.UTF_8));
        }

        MetamodelBinaryExporter binaryExporter = binaryExporters.get(format);

        if(binaryExporter != null) {
            return new MetamodelExportArtifact(metamodelId + format.extension(), format.mediaType(), binaryExporter.export(context));
        }

        throw new MetamodelDesignException(Reason.INVALID_REQUEST, "Metamodel exporter is not registered: " + format.id());
    }

    /** Descripción: Crea o prepara el resultado de «createBundle» dentro de MetamodelExportService. Entradas: metamodelId: identificador que delimita el recurso objetivo; formats: dato de entrada necesario para la operación; context: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private byte[] createBundle(String metamodelId, List<MetamodelExportFormat> formats, NeutralMetamodelExportContext context) {
        try(ByteArrayOutputStream output = new ByteArrayOutputStream(); ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            for(MetamodelExportFormat format : formats) {
                MetamodelExportArtifact artifact = exportLoaded(metamodelId, format, context);
                ZipEntry entry = new ZipEntry(artifact.fileName());
                entry.setTime(0L);
                zip.putNextEntry(entry);
                zip.write(artifact.content());
                zip.closeEntry();
            }

            zip.finish();
            return output.toByteArray();
        } catch(IOException exception) {
            throw new MetamodelDesignException(Reason.STORAGE_ERROR, "Could not generate selected export bundle", exception);
        }
    }

    /** Descripción: Ejecuta la operación «defaultBundleFormats» correspondiente a la responsabilidad de MetamodelExportService. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private List<String> defaultBundleFormats() {
        return java.util.Arrays.stream(MetamodelExportFormat.values()).filter(MetamodelExportFormat::includedByDefault).map(MetamodelExportFormat::id).toList();
    }

    /** Descripción: Ejecuta la operación «indexTextExporters» correspondiente a la responsabilidad de MetamodelExportService. Entradas: exporters: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Map<MetamodelExportFormat, MetamodelTextExporter> indexTextExporters(List<MetamodelTextExporter> exporters) {
        Map<MetamodelExportFormat, MetamodelTextExporter> result = new EnumMap<>(MetamodelExportFormat.class);
        exporters.forEach(item -> result.put(item.format(), item));
        return result;
    }

    /** Descripción: Ejecuta la operación «indexBinaryExporters» correspondiente a la responsabilidad de MetamodelExportService. Entradas: exporters: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Map<MetamodelExportFormat, MetamodelBinaryExporter> indexBinaryExporters(List<MetamodelBinaryExporter> exporters) {
        Map<MetamodelExportFormat, MetamodelBinaryExporter> result = new EnumMap<>(MetamodelExportFormat.class);
        exporters.forEach(item -> result.put(item.format(), item));
        return result;
    }

    /** Descripción: Ejecuta la operación «descriptor» correspondiente a la responsabilidad de MetamodelExportService. Entradas: format: dato de entrada necesario para la operación; category: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private MetamodelExportFormatResponse descriptor(MetamodelExportFormat format, String category) {
        return new MetamodelExportFormatResponse(format.id(), format.displayName(), format.extension(), category);
    }

    /** Valida el identificador mediante la política canónica compartida. */
    /** Descripción: Valida el identificador de exportación mediante la política canónica compartida. */
    /** Descripción: Valida las condiciones necesarias para «validateMetamodelId» y rechaza entradas no válidas. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void validateMetamodelId(String metamodelId) {
        try { MetamodelIdPolicy.require(metamodelId); }
        catch(IllegalArgumentException exception) { throw new MetamodelDesignException(Reason.INVALID_REQUEST, exception.getMessage(), exception); }
    }

}
