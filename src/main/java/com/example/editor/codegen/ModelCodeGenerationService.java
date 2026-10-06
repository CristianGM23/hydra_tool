package com.example.editor.codegen;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.example.editor.definition.CodeGenerationFeatureOptions;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.validation.model.CanonicalModelDocument;
import com.example.editor.validation.model.CanonicalModelElement;
import com.example.editor.validation.model.CanonicalModelRelation;
import tools.jackson.databind.json.JsonMapper;

/** Orquesta generación de modelos delegando cada destino en renderers extensibles y validados al arrancar. */
@Service
public class ModelCodeGenerationService {
    private final ModelCodeGenerationRendererRegistry renderers;

    /** Descripción: Inicializa el servicio con el registro extensible descubierto por Spring. Entradas: registro de renderers. Salida: servicio listo. */
    /** Descripción: Ejecuta la operación «ModelCodeGenerationService» correspondiente a la responsabilidad de ModelCodeGenerationService. Entradas: renderers: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ @Autowired public ModelCodeGenerationService(ModelCodeGenerationRendererRegistry renderers) { this.renderers = renderers; }

    /** Descripción: Inicializa el servicio con los renderers incorporados para pruebas aisladas. Entradas: mapper JSON. Salida: servicio equivalente al de producción. */
    /** Descripción: Inicializa una instancia de ModelCodeGenerationService con sus dependencias y datos de entrada. Entradas: mapper: componente de conversión utilizado durante el procesamiento. Salida: deja la instancia inicializada y lista para su uso. */ ModelCodeGenerationService(JsonMapper mapper) { this(ModelCodeGenerationRendererRegistry.builtIns(mapper)); }

    /** Descripción: Lista destinos de generación y permite filtrarlos por la política del editor exportado. Entradas: destinos permitidos opcionales. Salida: catálogo filtrado y estable. */
    /** Descripción: Ejecuta la operación «targets» correspondiente a la responsabilidad de ModelCodeGenerationService. Entradas: allowed: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public List<ModelCodeGenerationTarget> targets(Set<String> allowed) {
        List<ModelCodeGenerationTarget> all = ModelCodeGenerationTarget.catalog();
        if(allowed == null || allowed.isEmpty()) return all;
        return all.stream().filter(item -> allowed.contains(item.id())).toList();
    }

    /** Descripción: Genera un artefacto desde el modelo y metamodelo activos delegando en el renderer registrado. Entradas: metamodelo, modelo, destino y opciones. Salida: artefacto descargable. */
    /** Descripción: Crea o prepara el resultado de «generate» dentro de ModelCodeGenerationService. Entradas: metamodel: dato de entrada necesario para la operación; model: dato de entrada necesario para la operación; targetId: identificador que delimita el recurso objetivo; options: configuración que gobierna el comportamiento. Salida: devuelve el resultado definido por el contrato del método. */ public GeneratedModelArtifact generate(MetaModel metamodel, CanonicalModelDocument model, String targetId, ModelCodeGenerationOptions options) {
        if(metamodel == null || model == null) throw new IllegalArgumentException("Metamodel and model are required for code generation");
        if(!metamodel.id().equals(model.metamodelId())) throw new IllegalArgumentException("The model does not belong to the active metamodel");
        ModelCodeGenerationTarget target = ModelCodeGenerationTarget.require(targetId);
        ModelCodeGenerationOptions effective = options == null ? ModelCodeGenerationOptions.defaults() : options;
        validateFeatures(target, effective.features());
        return renderers.require(targetId).generate(targetId, target, metamodel, model, effective);
    }

    /** Genera sobre todo el modelo o sobre el subgrafo inducido por los elementos seleccionados. */
    /** Descripción: Crea o prepara el resultado de «generate» dentro de ModelCodeGenerationService. Entradas: metamodel: dato de entrada necesario para la operación; model: dato de entrada necesario para la operación; targetId: identificador que delimita el recurso objetivo; options: configuración que gobierna el comportamiento; selectionElementIds: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public GeneratedModelArtifact generate(MetaModel metamodel, CanonicalModelDocument model, String targetId, ModelCodeGenerationOptions options, List<String> selectionElementIds) {
        return generate(metamodel, selection(model, selectionElementIds), targetId, options);
    }

    /** Genera una vista previa textual. Los paquetes binarios se previsualizan como su fuente principal sin incluir tests auxiliares. */
    /** Descripción: Ejecuta la operación «preview» correspondiente a la responsabilidad de ModelCodeGenerationService. Entradas: metamodel: dato de entrada necesario para la operación; model: dato de entrada necesario para la operación; targetId: identificador que delimita el recurso objetivo; options: configuración que gobierna el comportamiento; selectionElementIds: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ModelCodeGenerationPreview preview(MetaModel metamodel, CanonicalModelDocument model, String targetId, ModelCodeGenerationOptions options, List<String> selectionElementIds) {
        ModelCodeGenerationOptions effective = options == null ? ModelCodeGenerationOptions.defaults() : options;
        if(effective.features().generateTests()) effective = new ModelCodeGenerationOptions(effective.namespace(), effective.artifactName(), effective.includeTraceability(),
                new CodeGenerationFeatureOptions(false, effective.features().generateGettersSetters(), effective.features().generateComparison(), effective.features().generateStringRepresentation(), effective.features().generateEquality()));
        CanonicalModelDocument selected = selection(model, selectionElementIds);
        GeneratedModelArtifact artifact = generate(metamodel, selected, targetId, effective);
        if(!artifact.mediaType().startsWith("text/") && !artifact.mediaType().contains("json") && !artifact.mediaType().contains("xml"))
            throw new IllegalArgumentException("The selected generation target does not provide a textual preview");
        return new ModelCodeGenerationPreview(artifact.fileName(), artifact.mediaType(), new String(artifact.content(), StandardCharsets.UTF_8), selected.elements().size());
    }

    /** Construye en O(n) el subgrafo inducido por la selección y conserva únicamente relaciones cuyos extremos están incluidos. */
    /** Descripción: Ejecuta la operación «selection» correspondiente a la responsabilidad de ModelCodeGenerationService. Entradas: model: dato de entrada necesario para la operación; selectionElementIds: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static CanonicalModelDocument selection(CanonicalModelDocument model, List<String> selectionElementIds) {
        if(selectionElementIds == null || selectionElementIds.isEmpty()) return model;
        Set<String> requested = new LinkedHashSet<>(selectionElementIds);
        List<CanonicalModelElement> elements = model.elements().stream().filter(item -> requested.contains(item.id())).toList();
        if(elements.size() != requested.size()) {
            Set<String> existing = elements.stream().map(CanonicalModelElement::id).collect(java.util.stream.Collectors.toSet());
            String missing = requested.stream().filter(id -> !existing.contains(id)).findFirst().orElse("");
            throw new IllegalArgumentException("Selected model element was not found: " + missing);
        }
        Set<String> included = elements.stream().map(CanonicalModelElement::id).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        List<CanonicalModelRelation> relations = model.relations().stream().filter(item -> included.contains(item.sourceElementId()) && included.contains(item.targetElementId())).toList();
        List<String> roots = model.rootElementIds().stream().filter(included::contains).toList();
        if(roots.isEmpty() && !elements.isEmpty()) roots = List.of(elements.get(0).id());
        return new CanonicalModelDocument(model.schemaVersion(), model.id(), model.metamodelId(), model.metamodelEtag(), model.name(), roots, elements, relations, model.annotations());
    }

    /** Descripción: Rechaza capacidades que el destino seleccionado no declara para impedir opciones ignoradas silenciosamente. Entradas: descriptor de destino y perfil solicitado. Salida: no devuelve valor; valida o lanza error. */
    /** Descripción: Valida las condiciones necesarias para «validateFeatures» y rechaza entradas no válidas. Entradas: target: dato de entrada necesario para la operación; features: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private static void validateFeatures(ModelCodeGenerationTarget target, CodeGenerationFeatureOptions features) {
        if(features.generateTests()) requireFeature(target, ModelCodeGenerationFeature.TESTS);
        if(features.generateGettersSetters()) requireFeature(target, ModelCodeGenerationFeature.GETTERS_SETTERS);
        if(features.generateComparison()) requireFeature(target, ModelCodeGenerationFeature.COMPARISON);
        if(features.generateStringRepresentation()) requireFeature(target, ModelCodeGenerationFeature.STRING_REPRESENTATION);
        if(features.generateEquality()) requireFeature(target, ModelCodeGenerationFeature.EQUALITY);
    }

    /** Descripción: Comprueba una capacidad concreta contra el descriptor público del destino. Entradas: destino y capacidad. Salida: no devuelve valor; valida o lanza error. */
    /** Descripción: Valida las condiciones necesarias para «requireFeature» y rechaza entradas no válidas. Entradas: target: dato de entrada necesario para la operación; feature: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private static void requireFeature(ModelCodeGenerationTarget target, ModelCodeGenerationFeature feature) {
        if(!target.supportedFeatures().contains(feature)) throw new IllegalArgumentException("Code generation feature " + feature + " is not supported by target " + target.id());
    }
}
