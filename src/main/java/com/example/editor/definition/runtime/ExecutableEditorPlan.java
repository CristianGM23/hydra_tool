package com.example.editor.definition.runtime;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import com.example.editor.definition.CodeGenerationDefinition;
import com.example.editor.definition.EditorCapabilityManifest;
import com.example.editor.definition.FormDefinition;
import com.example.editor.definition.EditorRuntimePolicy;
import com.fasterxml.jackson.annotation.JsonInclude;

/** Plan compilado e inmutable consumido por los runtimes de editores de modelos. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ExecutableEditorPlan(String schemaVersion, String id, String sourceEditorDefinitionId,
        String sourceEtag, String metamodelId, String name, Instant compiledAt,
        Map<String, ExecutableRepresentationPlan> representationsById,
        Map<String, FormDefinition> formsById,
        Map<String, FormDefinition> formsByTargetClassId,
        EditorCapabilityManifest capabilityManifest, EditorRuntimePolicy runtimePolicy,
        CodeGenerationDefinition codeGeneration, Map<String, String> annotations) {

    public ExecutableEditorPlan {
        if(schemaVersion == null || schemaVersion.isBlank()) schemaVersion = EditorRuntimeContractVersions.EXECUTABLE_EDITOR_PLAN;
        if(!EditorRuntimeContractVersions.EXECUTABLE_EDITOR_PLAN.equals(schemaVersion)) throw new IllegalArgumentException("Unsupported executable editor plan schemaVersion: " + schemaVersion);
        sourceEtag = sourceEtag == null ? "" : sourceEtag;
        compiledAt = compiledAt == null ? Instant.now() : compiledAt;
        representationsById = immutableMap(representationsById);
        formsById = immutableMap(formsById);
        formsByTargetClassId = immutableMap(formsByTargetClassId);
        capabilityManifest = capabilityManifest == null ? new EditorCapabilityManifest(Set.of()) : capabilityManifest;
        runtimePolicy = runtimePolicy == null ? EditorRuntimePolicy.defaults() : runtimePolicy;
        codeGeneration = codeGeneration == null ? CodeGenerationDefinition.disabled() : codeGeneration;
        annotations = immutableMap(annotations);
    }

    /** Descripción: Ejecuta la operación «immutableMap» correspondiente a la responsabilidad de ExecutableEditorPlan. Entradas: values: colección de elementos que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static <T> Map<String, T> immutableMap(Map<String, T> values) {
        if(values == null || values.isEmpty()) return Map.of();
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }
}
