package com.example.editor.metamodel.importing.importers;

import java.util.Set;
import org.springframework.stereotype.Component;
import com.example.editor.metamodel.importing.MetamodelImportFormat;
import com.example.editor.metamodel.importing.SafeImportArchiveReader;
import com.example.editor.metamodel.importing.updatesite.UpdateSiteArtifactKind;
import com.example.editor.metamodel.importing.updatesite.UpdateSitePortableSourceMetamodelDeriver;

/** Importa structs e interfaces Go mediante análisis estático, sin ejecutar código. */
@Component
public class GoMetamodelImporter extends PortableSourceMetamodelImporter {
    /** Descripción: Inicializa una instancia de GoMetamodelImporter con sus dependencias y datos de entrada. Entradas: archiveReader: archivo o contenido que debe procesarse; deriver: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public GoMetamodelImporter(SafeImportArchiveReader archiveReader, UpdateSitePortableSourceMetamodelDeriver deriver) {
        super(archiveReader, deriver, Set.of(".go"), UpdateSiteArtifactKind.GO_SOURCE);
    }
    /** Descripción: Ejecuta la operación «format» correspondiente a la responsabilidad de GoMetamodelImporter. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @Override public MetamodelImportFormat format() { return MetamodelImportFormat.GO; }
}
