package com.example.editor.definition;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import com.example.editor.validation.contract.StableIdentifierPolicy;
import com.fasterxml.jackson.annotation.JsonInclude;
/** Configuración portable de un editor de modelos construido sobre un metamodelo. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EditorDefinitionModel(String schemaVersion, String id, String metamodelId, String name,
        List<RepresentationDefinition> representations, ToolPaletteDefinition palette, List<FormDefinition> forms,
        EditorCapabilityManifest capabilityManifest, EditorRuntimePolicy runtimePolicy, CodeGenerationDefinition codeGeneration, Map<String, String> annotations) {
    public EditorDefinitionModel {
        if(schemaVersion == null || schemaVersion.isBlank()) schemaVersion = EditorContractVersions.EDITOR_DEFINITION_MODEL;
        if(!EditorContractVersions.EDITOR_DEFINITION_MODEL.equals(schemaVersion)) throw new IllegalArgumentException("Unsupported editor definition schemaVersion: " + schemaVersion);
        id = StableIdentifierPolicy.requireDocumentId(id, "id");
        metamodelId = StableIdentifierPolicy.requireDocumentId(metamodelId, "metamodelId");
        name = name == null || name.isBlank() ? id : name.trim();
        representations = representations == null ? List.of() : List.copyOf(representations);
        if(palette == null) palette = new ToolPaletteDefinition(List.of(), List.of());
        forms = forms == null ? List.of() : List.copyOf(forms);
        if(capabilityManifest == null) capabilityManifest = new EditorCapabilityManifest(java.util.Set.of(EditorCapability.MODEL_EDITING));
        if(runtimePolicy == null) runtimePolicy = EditorRuntimePolicy.defaults();
        if(codeGeneration == null) codeGeneration = CodeGenerationDefinition.disabled();
        annotations = annotations == null || annotations.isEmpty() ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(annotations));
    }

    /** Descripción: Inicializa una instancia de EditorDefinitionModel con sus dependencias y datos de entrada. Entradas: schemaVersion: dato de entrada necesario para la operación; id: identificador que delimita el recurso objetivo; metamodelId: identificador que delimita el recurso objetivo; name: dato de entrada necesario para la operación; representations: dato de entrada necesario para la operación; palette: dato de entrada necesario para la operación; forms: dato de entrada necesario para la operación; capabilityManifest: dato de entrada necesario para la operación; annotations: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public EditorDefinitionModel(String schemaVersion, String id, String metamodelId, String name,
            List<RepresentationDefinition> representations, ToolPaletteDefinition palette, List<FormDefinition> forms,
            EditorCapabilityManifest capabilityManifest, Map<String, String> annotations) {
        this(schemaVersion, id, metamodelId, name, representations, palette, forms, capabilityManifest,
                EditorRuntimePolicy.defaults(), CodeGenerationDefinition.disabled(), annotations);
    }
    /** Constructor de conveniencia para definiciones sin generación de código explícita. */
    /** Descripción: Inicializa una instancia de EditorDefinitionModel con sus dependencias y datos de entrada. Entradas: schemaVersion: dato de entrada necesario para la operación; id: identificador que delimita el recurso objetivo; metamodelId: identificador que delimita el recurso objetivo; name: dato de entrada necesario para la operación; representations: dato de entrada necesario para la operación; palette: dato de entrada necesario para la operación; forms: dato de entrada necesario para la operación; capabilityManifest: dato de entrada necesario para la operación; runtimePolicy: dato de entrada necesario para la operación; annotations: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public EditorDefinitionModel(String schemaVersion, String id, String metamodelId, String name,
            List<RepresentationDefinition> representations, ToolPaletteDefinition palette, List<FormDefinition> forms,
            EditorCapabilityManifest capabilityManifest, EditorRuntimePolicy runtimePolicy, Map<String, String> annotations) {
        this(schemaVersion, id, metamodelId, name, representations, palette, forms, capabilityManifest,
                runtimePolicy, CodeGenerationDefinition.disabled(), annotations);
    }
    /** Descripción: Obtiene la información correspondiente a «findRepresentation» dentro de EditorDefinitionModel. Entradas: representationId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public Optional<RepresentationDefinition> findRepresentation(String representationId) { return representations.stream().filter(value -> value.id().equals(representationId)).findFirst(); }
    /** Descripción: Obtiene la información correspondiente a «findForm» dentro de EditorDefinitionModel. Entradas: formId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public Optional<FormDefinition> findForm(String formId) { return forms.stream().filter(value -> value.id().equals(formId)).findFirst(); }
}
