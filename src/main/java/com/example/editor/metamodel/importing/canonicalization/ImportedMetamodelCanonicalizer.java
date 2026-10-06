package com.example.editor.metamodel.importing.canonicalization;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.example.editor.metamodel.core.MetaAttribute;
import com.example.editor.metamodel.core.MetaClass;
import com.example.editor.metamodel.core.MetaDataType;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.core.MetaOperation;
import com.example.editor.metamodel.core.MetaParameter;
import com.example.editor.metamodel.core.MetaRelation;
import com.example.editor.metamodel.target.TargetConfigurationDocument;

/**
 * Convierte metadatos transitorios de importación al contrato canónico actual.
 */
public final class ImportedMetamodelCanonicalizer {

    private static final String IMPORT_PREFIX = EcoreImportMetadata.PREFIX;

    /** Descripción: Inicializa una instancia de ImportedMetamodelCanonicalizer con sus dependencias y datos de entrada. Entradas: no requiere parámetros de entrada. Salida: deja la instancia inicializada y lista para su uso. */ private ImportedMetamodelCanonicalizer() {
    }

    /** Descripción: Ejecuta la operación «prepare» correspondiente a la responsabilidad de ImportedMetamodelCanonicalizer. Entradas: source: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static ImportedMetamodelCanonicalization prepare(MetaModel source) {
        if (source == null) {
            throw new IllegalArgumentException("MetaModel must not be null");
        }

        if (!MetaModel.CURRENT_SCHEMA_VERSION.equals(source.schemaVersion())) {
            throw new IllegalArgumentException("Unsupported MetaModel schema version: " + source.schemaVersion()
                    + ". HYDRA accepts only the current internal metamodel contract.");
        }

        TargetConfigurationDocument targets = TargetConfigurationDocument.defaultsFor(source);
        List<MetaClass> classes = source.classes().stream().map(ImportedMetamodelCanonicalizer::cleanClass).toList();
        List<MetaDataType> dataTypes = source.dataTypes().stream().map(item -> new MetaDataType(item.id(), item.name(), cleanAnnotations(item.annotations()))).toList();
        List<MetaRelation> relations = source.relations().stream().map(item -> new MetaRelation(item.id(), item.name(), item.sourceTypeId(), item.targetTypeId(), item.kind(), item.sourceMultiplicity(), item.targetMultiplicity(), item.oppositeRelationId(), item.ordered(), item.unique(), cleanAnnotations(item.annotations()))).toList();
        MetaModel cleaned = new MetaModel(MetaModel.CURRENT_SCHEMA_VERSION, source.id(), source.name(), classes, source.enums(), dataTypes, relations, cleanAnnotations(source.annotations()));
        return new ImportedMetamodelCanonicalization(cleaned, targets, !cleaned.equals(source));
    }


    /** Devuelve true cuando el documento transporta opciones Ecore transitorias de una importación. */
    /** Descripción: Evalúa la condición «containsEcoreImportMetadata» sin modificar el estado externo. Entradas: source: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ public static boolean containsEcoreImportMetadata(MetaModel source) {
        if (source == null) {
            return false;
        }

        if (containsImportMetadata(source.annotations())) {
            return true;
        }

        for (MetaClass metaClass : source.classes()) {
            if (containsImportMetadata(metaClass.annotations())) {
                return true;
            }
            for (MetaAttribute attribute : metaClass.attributes()) {
                if (containsImportMetadata(attribute.annotations())) {
                    return true;
                }
            }
            for (MetaOperation operation : metaClass.operations()) {
                if (containsImportMetadata(operation.annotations())) {
                    return true;
                }
                for (MetaParameter parameter : operation.parameters()) {
                    if (containsImportMetadata(parameter.annotations())) {
                        return true;
                    }
                }
            }
        }

        for (MetaDataType dataType : source.dataTypes()) {
            if (containsImportMetadata(dataType.annotations())) {
                return true;
            }
        }

        for (MetaRelation relation : source.relations()) {
            if (containsImportMetadata(relation.annotations())) {
                return true;
            }
        }

        return false;
    }

    /** Descripción: Evalúa la condición «containsImportMetadata» sin modificar el estado externo. Entradas: annotations: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private static boolean containsImportMetadata(Map<String, String> annotations) {
        return annotations != null && annotations.keySet().stream().anyMatch(key -> key != null && key.startsWith(IMPORT_PREFIX));
    }

    /** Descripción: Ejecuta la operación «cleanClass» correspondiente a la responsabilidad de ImportedMetamodelCanonicalizer. Entradas: source: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static MetaClass cleanClass(MetaClass source) {
        List<MetaAttribute> attributes = source.attributes().stream().map(item -> new MetaAttribute(item.id(), item.name(), item.type(), item.multiplicity(), item.ordered(), item.unique(), item.defaultValue(), cleanAnnotations(item.annotations()))).toList();
        List<MetaOperation> operations = source.operations().stream().map(ImportedMetamodelCanonicalizer::cleanOperation).toList();
        return new MetaClass(source.id(), source.name(), source.kind(), attributes, operations, cleanAnnotations(source.annotations()));
    }

    /** Descripción: Ejecuta la operación «cleanOperation» correspondiente a la responsabilidad de ImportedMetamodelCanonicalizer. Entradas: source: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static MetaOperation cleanOperation(MetaOperation source) {
        List<MetaParameter> parameters = source.parameters().stream().map(item -> new MetaParameter(item.id(), item.name(), item.type(), item.multiplicity(), item.ordered(), item.unique(), cleanAnnotations(item.annotations()))).toList();
        return new MetaOperation(source.id(), source.name(), source.returnType(), source.returnMultiplicity(), source.ordered(), source.unique(), parameters, source.exceptionTypes(), source.behaviorKind(), cleanAnnotations(source.annotations()));
    }

    /** Descripción: Ejecuta la operación «cleanAnnotations» correspondiente a la responsabilidad de ImportedMetamodelCanonicalizer. Entradas: source: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static Map<String, String> cleanAnnotations(Map<String, String> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }

        Map<String, String> result = new LinkedHashMap<>();

        for (Map.Entry<String, String> entry : source.entrySet()) {
            if (!entry.getKey().startsWith(IMPORT_PREFIX)) {
                result.put(entry.getKey(), entry.getValue());
            }
        }

        return result;
    }
}
