package com.example.editor.metamodel.importing.importers;

import org.springframework.stereotype.Component;

import com.example.editor.metamodel.adapter.ecore.EcoreMetamodelAdapter;
import com.example.editor.metamodel.importing.MetamodelImportFormat;

/**
 * Importa XMI con un EPackage como raíz.
 */
@Component
public class XmiMetamodelImporter extends EcoreXmiMetamodelImporter {

    /** Descripción: Inicializa una instancia de XmiMetamodelImporter con sus dependencias y datos de entrada. Entradas: adapter: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public XmiMetamodelImporter(EcoreMetamodelAdapter adapter) {
        super(adapter, MetamodelImportFormat.XMI);
    }
}
