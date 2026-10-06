package com.example.editor.metamodel.importing.importers;

import java.util.Set;
import org.springframework.stereotype.Component;
import com.example.editor.metamodel.importing.MetamodelImportFormat;
import com.example.editor.metamodel.importing.SafeImportArchiveReader;
import com.example.editor.metamodel.importing.updatesite.UpdateSiteArtifactKind;
import com.example.editor.metamodel.importing.updatesite.UpdateSitePortableSourceMetamodelDeriver;

/** Importa clases JavaScript y miembros inferibles mediante análisis estático, sin ejecutar código. */
@Component
public class JavaScriptMetamodelImporter extends PortableSourceMetamodelImporter {
    /** Descripción: Inicializa una instancia de JavaScriptMetamodelImporter con sus dependencias y datos de entrada. Entradas: archiveReader: archivo o contenido que debe procesarse; deriver: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public JavaScriptMetamodelImporter(SafeImportArchiveReader archiveReader, UpdateSitePortableSourceMetamodelDeriver deriver) {
        super(archiveReader, deriver, Set.of(".js", ".mjs", ".cjs"), UpdateSiteArtifactKind.JAVASCRIPT_SOURCE);
    }
    /** Descripción: Ejecuta la operación «format» correspondiente a la responsabilidad de JavaScriptMetamodelImporter. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @Override public MetamodelImportFormat format() { return MetamodelImportFormat.JAVASCRIPT; }
}
