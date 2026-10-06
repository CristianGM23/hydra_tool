package com.example.editor.metamodel.importing.updatesite;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.example.editor.metamodel.core.MetaAttribute;
import com.example.editor.metamodel.core.MetaClass;
import com.example.editor.metamodel.core.MetaDataType;
import com.example.editor.metamodel.core.MetaEnum;
import com.example.editor.metamodel.core.MetaEnumLiteral;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.core.MetaOperation;
import com.example.editor.metamodel.core.MetaParameter;
import com.example.editor.metamodel.core.MetaRelation;

/** Inserta procedencia en las anotaciones y produce un índice auditable. */
@Service
public class UpdateSiteProvenanceService {
    private static final String PREFIX = "hydra.provenance.";
    private final UpdateSiteImportProperties properties;

    /** Descripción: Inicializa una instancia de UpdateSiteProvenanceService con sus dependencias y datos de entrada. Entradas: properties: configuración que gobierna el comportamiento. Salida: deja la instancia inicializada y lista para su uso. */ public UpdateSiteProvenanceService(UpdateSiteImportProperties properties) {
        this.properties = properties;
    }

    /** Descripción: Ejecuta la operación «enrich» correspondiente a la responsabilidad de UpdateSiteProvenanceService. Entradas: model: dato de entrada necesario para la operación; candidate: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ ProvenancedMetamodel enrich(MetaModel model, UpdateSiteRecoveryCandidate candidate) {
        List<UpdateSiteElementProvenance> provenance = new ArrayList<>();
        String origin = archiveRoot(candidate.sourcePath());
        Map<String, String> modelAnnotations = annotations(model.annotations(), candidate, origin, "/", candidate.warnings());
        add(provenance, model.id(), "/", origin, candidate, "/", candidate.warnings());

        List<MetaClass> classes = model.classes().stream().map(metaClass -> {
            String classPath = "//" + metaClass.name();
            add(provenance, metaClass.id(), classPath, origin, candidate, classPath, candidate.warnings());
            List<MetaAttribute> attributes = metaClass.attributes().stream().map(attribute -> {
                String path = classPath + "/" + attribute.name();
                add(provenance, attribute.id(), path, origin, candidate, path, candidate.warnings());
                return new MetaAttribute(attribute.id(), attribute.name(), attribute.type(), attribute.multiplicity(),
                        attribute.ordered(), attribute.unique(), attribute.defaultValue(),
                        annotations(attribute.annotations(), candidate, origin, path, candidate.warnings()));
            }).toList();
            List<MetaOperation> operations = metaClass.operations().stream().map(operation -> {
                String operationPath = classPath + "/" + operation.name() + "()";
                add(provenance, operation.id(), operationPath, origin, candidate, operationPath, candidate.warnings());
                List<MetaParameter> parameters = operation.parameters().stream().map(parameter -> {
                    String path = operationPath + "/" + parameter.name();
                    add(provenance, parameter.id(), path, origin, candidate, path, candidate.warnings());
                    return new MetaParameter(parameter.id(), parameter.name(), parameter.type(), parameter.multiplicity(),
                            parameter.ordered(), parameter.unique(), annotations(parameter.annotations(), candidate, origin, path, candidate.warnings()));
                }).toList();
                return new MetaOperation(operation.id(), operation.name(), operation.returnType(), operation.returnMultiplicity(),
                        operation.ordered(), operation.unique(), parameters, operation.exceptionTypes(), operation.behaviorKind(),
                        annotations(operation.annotations(), candidate, origin, operationPath, candidate.warnings()));
            }).toList();
            return new MetaClass(metaClass.id(), metaClass.name(), metaClass.kind(), attributes, operations,
                    annotations(metaClass.annotations(), candidate, origin, classPath, candidate.warnings()));
        }).toList();

        List<MetaEnum> enums = model.enums().stream().map(metaEnum -> {
            String enumPath = "//" + metaEnum.name();
            add(provenance, metaEnum.id(), enumPath, origin, candidate, enumPath, candidate.warnings());
            List<MetaEnumLiteral> literals = metaEnum.literals().stream().map(literal -> {
                String path = enumPath + "/" + literal.name();
                add(provenance, literal.id(), path, origin, candidate, path, candidate.warnings());
                return new MetaEnumLiteral(literal.id(), literal.name(), literal.value(),
                        annotations(literal.annotations(), candidate, origin, path, candidate.warnings()));
            }).toList();
            return new MetaEnum(metaEnum.id(), metaEnum.name(), literals,
                    annotations(metaEnum.annotations(), candidate, origin, enumPath, candidate.warnings()));
        }).toList();

        List<MetaDataType> dataTypes = model.dataTypes().stream().map(dataType -> {
            String path = "//" + dataType.name();
            add(provenance, dataType.id(), path, origin, candidate, path, candidate.warnings());
            return new MetaDataType(dataType.id(), dataType.name(),
                    annotations(dataType.annotations(), candidate, origin, path, candidate.warnings()));
        }).toList();

        List<MetaRelation> relations = model.relations().stream().map(relation -> {
            String sourceName = model.classes().stream().filter(item -> item.id().equals(relation.sourceTypeId()))
                    .map(MetaClass::name).findFirst().orElse(relation.sourceTypeId());
            String path = "//" + sourceName + "/" + relation.name();
            add(provenance, relation.id(), path, origin, candidate, path, candidate.warnings());
            return new MetaRelation(relation.id(), relation.name(), relation.sourceTypeId(), relation.targetTypeId(),
                    relation.kind(), relation.sourceMultiplicity(), relation.targetMultiplicity(), relation.oppositeRelationId(),
                    relation.ordered(), relation.unique(), annotations(relation.annotations(), candidate, origin, path, candidate.warnings()));
        }).toList();

        MetaModel enriched = new MetaModel(model.schemaVersion(), model.id(), model.name(), classes, enums, dataTypes,
                relations, modelAnnotations);
        return new ProvenancedMetamodel(enriched, provenance);
    }

    /** Descripción: Ejecuta la operación «annotations» correspondiente a la responsabilidad de UpdateSiteProvenanceService. Entradas: original: dato de entrada necesario para la operación; candidate: dato de entrada necesario para la operación; origin: dato de entrada necesario para la operación; originalElement: dato de entrada necesario para la operación; warnings: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Map<String, String> annotations(Map<String, String> original, UpdateSiteRecoveryCandidate candidate,
            String origin, String originalElement, List<String> warnings) {
        Map<String, String> result = new LinkedHashMap<>(original == null ? Map.of() : original);
        result.put(PREFIX + "origin", origin);
        result.put(PREFIX + "path", candidate.sourcePath());
        result.put(PREFIX + "format", candidate.format());
        result.put(PREFIX + "originalElement", originalElement);
        result.put(PREFIX + "method", candidate.method().name());
        result.put(PREFIX + "confidence", candidate.confidence().name());
        if(warnings != null && !warnings.isEmpty()) result.put(PREFIX + "warnings", String.join(" | ", warnings));
        return Map.copyOf(result);
    }

    /** Descripción: Ejecuta la operación «add» correspondiente a la responsabilidad de UpdateSiteProvenanceService. Entradas: target: dato de entrada necesario para la operación; id: identificador que delimita el recurso objetivo; path: ruta o ubicación utilizada por la operación; origin: dato de entrada necesario para la operación; candidate: dato de entrada necesario para la operación; originalElement: dato de entrada necesario para la operación; warnings: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void add(List<UpdateSiteElementProvenance> target, String id, String path, String origin,
            UpdateSiteRecoveryCandidate candidate, String originalElement, List<String> warnings) {
        if(target.size() >= properties.getMaxProvenanceElements()) return;
        target.add(new UpdateSiteElementProvenance(id, path, origin, candidate.sourcePath(), candidate.format(),
                originalElement, candidate.method(), candidate.confidence(), warnings));
    }

    /** Descripción: Ejecuta la operación «archiveRoot» correspondiente a la responsabilidad de UpdateSiteProvenanceService. Entradas: path: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String archiveRoot(String path) {
        int marker = path.indexOf("!/");
        return marker >= 0 ? path.substring(0, marker) : path;
    }

    record ProvenancedMetamodel(MetaModel metamodel, List<UpdateSiteElementProvenance> provenance) {
        ProvenancedMetamodel {
            provenance = provenance == null ? List.of() : List.copyOf(provenance);
        }
    }
}
