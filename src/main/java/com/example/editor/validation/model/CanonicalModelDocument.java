package com.example.editor.validation.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.example.editor.validation.contract.StableIdentifierPolicy;
import com.example.editor.validation.contract.ValidationContractVersions;

/** Documento neutral de instancias que no depende de EObject, EClass o XMI. */
public record CanonicalModelDocument(String schemaVersion, String id, String metamodelId, String metamodelEtag, String name, List<String> rootElementIds, List<CanonicalModelElement> elements, List<CanonicalModelRelation> relations, Map<String, String> annotations) {

    public CanonicalModelDocument {
        if(schemaVersion == null || schemaVersion.isBlank()) schemaVersion = ValidationContractVersions.CANONICAL_MODEL_DOCUMENT;
        if(!ValidationContractVersions.CANONICAL_MODEL_DOCUMENT.equals(schemaVersion)) {
            throw new IllegalArgumentException("Unsupported canonical model schemaVersion: " + schemaVersion);
        }
        id = StableIdentifierPolicy.requireDocumentId(id, "id");
        metamodelId = StableIdentifierPolicy.requireDocumentId(metamodelId, "metamodelId");
        metamodelEtag = metamodelEtag == null ? "" : metamodelEtag;
        name = name == null ? "" : name;
        rootElementIds = rootElementIds == null ? List.of() : rootElementIds.stream().map(value -> StableIdentifierPolicy.requireElementId(value, "rootElementId")).toList();
        elements = elements == null ? List.of() : List.copyOf(elements);
        relations = relations == null ? List.of() : List.copyOf(relations);
        if(annotations == null || annotations.isEmpty()) annotations = Map.of();
        else annotations = Collections.unmodifiableMap(new LinkedHashMap<>(annotations));
    }

    /** Descripción: Obtiene la información correspondiente a «findElement» dentro de CanonicalModelDocument. Entradas: elementId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public Optional<CanonicalModelElement> findElement(String elementId) {
        return elements.stream().filter(element -> element.id().equals(elementId)).findFirst();
    }
}
