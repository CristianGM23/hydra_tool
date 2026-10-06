package com.example.editor.metamodel.importing.importers;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import com.example.editor.metamodel.core.TransformationWarning;
import com.example.editor.metamodel.importing.ImportSourceEntry;
import com.example.editor.metamodel.importing.MetamodelImportFormat;
import com.example.editor.metamodel.importing.MetamodelImportOptions;
import com.example.editor.metamodel.importing.MetamodelImportOutput;
import com.example.editor.metamodel.importing.MetamodelImportSource;
import com.example.editor.metamodel.importing.MetamodelImporter;
import com.example.editor.metamodel.importing.SafeImportArchiveReader;
import com.example.editor.metamodel.importing.updatesite.UpdateSiteArtifactKind;
import com.example.editor.metamodel.importing.updatesite.UpdateSitePortableSourceMetamodelDeriver;

/** Base común para importadores estáticos de código fuente portable. Nunca ejecuta código importado. */
abstract class PortableSourceMetamodelImporter implements MetamodelImporter {
    private final SafeImportArchiveReader archiveReader;
    private final UpdateSitePortableSourceMetamodelDeriver deriver;
    private final Set<String> extensions;
    private final UpdateSiteArtifactKind artifactKind;

    /** Descripción: Inicializa una instancia de PortableSourceMetamodelImporter con sus dependencias y datos de entrada. Entradas: archiveReader: archivo o contenido que debe procesarse; deriver: dato de entrada necesario para la operación; extensions: dato de entrada necesario para la operación; artifactKind: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ PortableSourceMetamodelImporter(SafeImportArchiveReader archiveReader, UpdateSitePortableSourceMetamodelDeriver deriver,
            Set<String> extensions, UpdateSiteArtifactKind artifactKind) {
        this.archiveReader = archiveReader;
        this.deriver = deriver;
        this.extensions = Set.copyOf(extensions);
        this.artifactKind = artifactKind;
    }

    @Override
    /** Descripción: Ejecuta la operación «importMetamodel» correspondiente a la responsabilidad de PortableSourceMetamodelImporter. Entradas: source: dato de entrada necesario para la operación; options: configuración que gobierna el comportamiento. Salida: devuelve el resultado definido por el contrato del método. */ public MetamodelImportOutput importMetamodel(MetamodelImportSource source, MetamodelImportOptions options) {
        List<ImportSourceEntry> entries = archiveReader.read(source, extensions);
        StringBuilder combined = new StringBuilder();
        for(ImportSourceEntry entry : entries) {
            combined.append("\n/* HYDRA source: ").append(entry.path().replace("*/", "* /")).append(" */\n")
                    .append(new String(entry.content(), StandardCharsets.UTF_8)).append('\n');
        }
        String requestedId = options.targetId().isBlank() ? sourceStem(source.fileName()) : options.targetId();
        var metamodel = deriver.derive(artifactKind, combined.toString().getBytes(StandardCharsets.UTF_8), requestedId, source.fileName());
        String formatName = format().displayName();
        return new MetamodelImportOutput(metamodel, null, List.of(new TransformationWarning(
                "IMPORT_STATIC_SOURCE_INFERENCE", "$", formatName + " was analyzed statically; imported code was never executed.")));
    }

    /** Descripción: Ejecuta la operación «sourceStem» correspondiente a la responsabilidad de PortableSourceMetamodelImporter. Entradas: fileName: archivo o contenido que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String sourceStem(String fileName) {
        String value = fileName == null ? "source-model" : fileName.replace('\\', '/');
        int slash = value.lastIndexOf('/');
        if(slash >= 0) value = value.substring(slash + 1);
        for(String suffix : List.of(".typescript.zip", ".javascript.zip", ".go.zip", ".tsx", ".ts", ".mjs", ".cjs", ".js", ".go", ".zip")) {
            if(value.toLowerCase(java.util.Locale.ROOT).endsWith(suffix)) {
                value = value.substring(0, value.length() - suffix.length());
                break;
            }
        }
        return value.isBlank() ? "source-model" : value;
    }
}
