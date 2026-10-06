package com.example.editor.metamodel.importing.importers;

import java.util.Set;
import org.springframework.stereotype.Component;
import com.example.editor.metamodel.importing.MetamodelImportFormat;
import com.example.editor.metamodel.importing.SafeImportArchiveReader;
import com.example.editor.metamodel.importing.updatesite.UpdateSiteArtifactKind;
import com.example.editor.metamodel.importing.updatesite.UpdateSitePortableSourceMetamodelDeriver;

/** Importa interfaces y clases TypeScript mediante análisis estático, sin ejecutar código. */
@Component
public class TypeScriptMetamodelImporter extends PortableSourceMetamodelImporter {
    /** Descripción: Inicializa una instancia de TypeScriptMetamodelImporter con sus dependencias y datos de entrada. Entradas: archiveReader: archivo o contenido que debe procesarse; deriver: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public TypeScriptMetamodelImporter(SafeImportArchiveReader archiveReader, UpdateSitePortableSourceMetamodelDeriver deriver) {
        super(archiveReader, deriver, Set.of(".ts", ".tsx"), UpdateSiteArtifactKind.TYPESCRIPT_SOURCE);
    }
    /** Descripción: Ejecuta la operación «format» correspondiente a la responsabilidad de TypeScriptMetamodelImporter. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @Override public MetamodelImportFormat format() { return MetamodelImportFormat.TYPESCRIPT; }
}
