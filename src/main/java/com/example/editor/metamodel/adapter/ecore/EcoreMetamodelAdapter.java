package com.example.editor.metamodel.adapter.ecore;

import java.nio.file.Path;

import org.eclipse.emf.ecore.EPackage;

import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.core.TransformationResult;
import com.example.editor.metamodel.target.EcoreProjectionConfiguration;

/**
 * Convierte entre el núcleo canónico y la proyección Ecore.
 */
public interface EcoreMetamodelAdapter {

    /** Descripción: Ejecuta la operación «toEPackage» correspondiente a la responsabilidad de EcoreMetamodelAdapter. Entradas: metamodel: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ TransformationResult<EPackage> toEPackage(MetaModel metamodel);

    /** Descripción: Ejecuta la operación «toEPackage» correspondiente a la responsabilidad de EcoreMetamodelAdapter. Entradas: metamodel: dato de entrada necesario para la operación; configuration: configuración que gobierna el comportamiento. Salida: devuelve el resultado definido por el contrato del método. */ default TransformationResult<EPackage> toEPackage(MetaModel metamodel, EcoreProjectionConfiguration configuration) {
        return toEPackage(metamodel);
    }

    /** Descripción: Ejecuta la operación «toBytes» correspondiente a la responsabilidad de EcoreMetamodelAdapter. Entradas: metamodel: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ TransformationResult<byte[]> toBytes(MetaModel metamodel);

    /** Descripción: Ejecuta la operación «toBytes» correspondiente a la responsabilidad de EcoreMetamodelAdapter. Entradas: metamodel: dato de entrada necesario para la operación; configuration: configuración que gobierna el comportamiento. Salida: devuelve el resultado definido por el contrato del método. */ default TransformationResult<byte[]> toBytes(MetaModel metamodel, EcoreProjectionConfiguration configuration) {
        return toBytes(metamodel);
    }

    /** Descripción: Ejecuta la operación «fromPath» correspondiente a la responsabilidad de EcoreMetamodelAdapter. Entradas: path: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ TransformationResult<MetaModel> fromPath(Path path);

    /** Descripción: Ejecuta la operación «fromEPackage» correspondiente a la responsabilidad de EcoreMetamodelAdapter. Entradas: ePackage: dato de entrada necesario para la operación; artifactName: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ TransformationResult<MetaModel> fromEPackage(EPackage ePackage, String artifactName);
}
