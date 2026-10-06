package com.example.editor.metamodel.importing.importers;

import org.springframework.stereotype.Component;

import com.example.editor.metamodel.adapter.ecore.EcoreMetamodelAdapter;
import com.example.editor.metamodel.importing.MetamodelImportFormat;

/**
 * Importa archivos .ecore.
 */
@Component
public class EcoreMetamodelImporter extends EcoreXmiMetamodelImporter {

    /** Descripción: Inicializa una instancia de EcoreMetamodelImporter con sus dependencias y datos de entrada. Entradas: adapter: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public EcoreMetamodelImporter(EcoreMetamodelAdapter adapter) {
        super(adapter, MetamodelImportFormat.ECORE);
    }
}
