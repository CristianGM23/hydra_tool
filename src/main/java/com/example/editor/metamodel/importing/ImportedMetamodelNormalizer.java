package com.example.editor.metamodel.importing;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.example.editor.metamodel.core.MetaAttribute;
import com.example.editor.metamodel.core.MetaClass;
import com.example.editor.metamodel.core.MetaDataType;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.core.MetaPrimitiveType;
import com.example.editor.metamodel.core.MetaTypeReference;
import com.example.editor.metamodel.core.MetaTypeReferenceKind;
import com.example.editor.metamodel.core.TransformationWarning;
import com.example.editor.metamodel.design.MetamodelDiagramDocument;
import com.example.editor.metamodel.importing.support.ImportParsingSupport;
import com.example.editor.metamodel.importing.canonicalization.EcoreImportMetadata;

/**
 * Normaliza una entrada externa directamente al contrato canónico MetaModel actual.
 */
@Component
public class ImportedMetamodelNormalizer {

    /** Descripción: Ejecuta la operación «normalize» correspondiente a la responsabilidad de ImportedMetamodelNormalizer. Entradas: output: dato de entrada necesario para la operación; source: dato de entrada necesario para la operación; format: dato de entrada necesario para la operación; options: configuración que gobierna el comportamiento. Salida: devuelve el resultado definido por el contrato del método. */ public NormalizedImport normalize(MetamodelImportOutput output, MetamodelImportSource source, MetamodelImportFormat format, MetamodelImportOptions options) {
        if(output == null || output.metamodel() == null) {
            throw new IllegalArgumentException("Imported metamodel must not be null");
        }

        MetaModel current = normalizeExternalTypes(output.metamodel());
        List<TransformationWarning> warnings = new ArrayList<>(externalTypeWarnings(output.metamodel(), current));
        String fallbackId = ImportParsingSupport.safeId(ImportParsingSupport.fileBaseName(source.fileName()), "imported-metamodel");
        String targetId = ImportParsingSupport.safeId(options.targetId().isBlank() ? current.id() : options.targetId(), fallbackId);
        String name = current.name() == null || current.name().isBlank() ? ImportParsingSupport.pascal(targetId, "ImportedMetamodel") : current.name();
        Map<String, String> annotations = new LinkedHashMap<>(current.annotations());
        annotations.put("import:format", format.id());
        annotations.put("import:sourceFile", source.fileName());
        preserveImportedEcoreDefaults(annotations, current, targetId);

        MetaModel normalized = new MetaModel(MetaModel.CURRENT_SCHEMA_VERSION, targetId, name, current.classes(), current.enums(), current.dataTypes(), current.relations(), annotations);
        MetamodelDiagramDocument diagram = output.diagram();

        if(diagram != null) {
            diagram = new MetamodelDiagramDocument(targetId, diagram.nodes(), diagram.edges());
        }

        return new NormalizedImport(normalized, diagram, warnings);
    }

    /** Descripción: Ejecuta la operación «preserveImportedEcoreDefaults» correspondiente a la responsabilidad de ImportedMetamodelNormalizer. Entradas: annotations: dato de entrada necesario para la operación; original: dato de entrada necesario para la operación; targetId: identificador que delimita el recurso objetivo. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void preserveImportedEcoreDefaults(Map<String, String> annotations, MetaModel original, String targetId) {
        annotations.putIfAbsent(EcoreImportMetadata.ARTIFACT_NAME, textOrDefault(annotations.get(EcoreImportMetadata.ARTIFACT_NAME), targetId + ".ecore"));
        annotations.putIfAbsent(EcoreImportMetadata.NAMESPACE_URI, textOrDefault(annotations.get(EcoreImportMetadata.NAMESPACE_URI), "urn:imported:" + targetId));
        annotations.putIfAbsent(EcoreImportMetadata.NAMESPACE_PREFIX, textOrDefault(annotations.get(EcoreImportMetadata.NAMESPACE_PREFIX), targetId.replace('-', '_')));

        // Las anotaciones transitorias del importador se copian antes de canonicalizarse.
        if(original.annotations() != null) {
            copyIfText(original.annotations(), annotations, EcoreImportMetadata.ARTIFACT_NAME);
            copyIfText(original.annotations(), annotations, EcoreImportMetadata.NAMESPACE_URI);
            copyIfText(original.annotations(), annotations, EcoreImportMetadata.NAMESPACE_PREFIX);
        }
    }

    /** Descripción: Ejecuta la operación «normalizeExternalTypes» correspondiente a la responsabilidad de ImportedMetamodelNormalizer. Entradas: metamodel: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private MetaModel normalizeExternalTypes(MetaModel metamodel) {
        List<MetaDataType> dataTypes = new ArrayList<>(metamodel.dataTypes());
        Map<String, String> externalTypeIds = new LinkedHashMap<>();

        for(MetaDataType dataType : dataTypes) {
            String implementation = dataType.annotations().getOrDefault(EcoreImportMetadata.INSTANCE_CLASS, "");
            if(!implementation.isBlank()) {
                externalTypeIds.put(implementation, dataType.id());
            }
        }

        List<MetaClass> classes = new ArrayList<>();

        for(MetaClass metaClass : metamodel.classes()) {
            List<MetaAttribute> attributes = new ArrayList<>();

            for(MetaAttribute attribute : metaClass.attributes()) {
                if(attribute.type().kind() != MetaTypeReferenceKind.EXTERNAL) {
                    attributes.add(attribute);
                    continue;
                }

                String externalName = attribute.type().externalName();

                if(externalName == null || externalName.isBlank()) {
                    attributes.add(new MetaAttribute(attribute.id(), attribute.name(), MetaTypeReference.primitive(MetaPrimitiveType.OBJECT), attribute.multiplicity(), attribute.ordered(), attribute.unique(), attribute.defaultValue(), attribute.annotations()));
                    continue;
                }

                String dataTypeId = externalTypeIds.get(externalName);

                if(dataTypeId == null) {
                    String simpleName = externalName.contains(".") ? externalName.substring(externalName.lastIndexOf('.') + 1) : externalName;
                    dataTypeId = "datatype:external:" + ImportParsingSupport.safeId(externalName, "external-type");
                    Map<String, String> dataTypeAnnotations = new LinkedHashMap<>();
                    dataTypeAnnotations.put("origin", "imported-external-type");
                    dataTypeAnnotations.put(EcoreImportMetadata.INSTANCE_CLASS, externalName);
                    dataTypeAnnotations.put(EcoreImportMetadata.SERIALIZABLE, "true");
                    dataTypes.add(new MetaDataType(dataTypeId, ImportParsingSupport.pascal(simpleName, "ExternalType"), dataTypeAnnotations));
                    externalTypeIds.put(externalName, dataTypeId);
                }

                attributes.add(new MetaAttribute(attribute.id(), attribute.name(), MetaTypeReference.dataType(dataTypeId), attribute.multiplicity(), attribute.ordered(), attribute.unique(), attribute.defaultValue(), attribute.annotations()));
            }

            classes.add(new MetaClass(metaClass.id(), metaClass.name(), metaClass.kind(), attributes, metaClass.operations(), metaClass.annotations()));
        }

        return new MetaModel(MetaModel.CURRENT_SCHEMA_VERSION, metamodel.id(), metamodel.name(), classes, metamodel.enums(), dataTypes, metamodel.relations(), metamodel.annotations());
    }

    /** Descripción: Ejecuta la operación «externalTypeWarnings» correspondiente a la responsabilidad de ImportedMetamodelNormalizer. Entradas: original: dato de entrada necesario para la operación; normalized: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<TransformationWarning> externalTypeWarnings(MetaModel original, MetaModel normalized) {
        int originalExternal = original.classes().stream().flatMap(item -> item.attributes().stream()).map(MetaAttribute::type).filter(item -> item.kind() == MetaTypeReferenceKind.EXTERNAL).mapToInt(item -> 1).sum();

        if(originalExternal == 0) {
            return List.of();
        }

        int addedDataTypes = Math.max(0, normalized.dataTypes().size() - original.dataTypes().size());
        return List.of(new TransformationWarning("IMPORT_EXTERNAL_TYPE_TO_DATATYPE", "$.dataTypes", originalExternal + " external attribute type(s) were converted into " + addedDataTypes + " editable custom data type(s)"));
    }

    /** Descripción: Ejecuta la operación «copyIfText» correspondiente a la responsabilidad de ImportedMetamodelNormalizer. Entradas: source: dato de entrada necesario para la operación; destination: dato de entrada necesario para la operación; key: identificador que delimita el recurso objetivo. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void copyIfText(Map<String, String> source, Map<String, String> destination, String key) {
        String value = source.get(key);
        if(value != null && !value.isBlank()) {
            destination.put(key, value);
        }
    }

    /** Descripción: Ejecuta la operación «textOrDefault» correspondiente a la responsabilidad de ImportedMetamodelNormalizer. Entradas: value: valor de entrada que debe procesarse; fallback: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String textOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    public record NormalizedImport(MetaModel metamodel, MetamodelDiagramDocument diagram, List<TransformationWarning> warnings) {
        public NormalizedImport {
            warnings = warnings == null ? List.of() : List.copyOf(warnings);
        }
    }
}
