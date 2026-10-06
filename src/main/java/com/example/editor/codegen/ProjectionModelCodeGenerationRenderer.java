package com.example.editor.codegen;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.validation.model.CanonicalModelDocument;
import com.example.editor.validation.model.CanonicalModelElement;
import com.example.editor.validation.model.CanonicalModelRelation;
import tools.jackson.databind.json.JsonMapper;

/** Renderer de serializaciones, gramáticas, diagramas y documentación; no mezcla estas proyecciones con scaffolding de código fuente. */
@Component
public class ProjectionModelCodeGenerationRenderer implements ModelCodeGenerationRenderer {
    private static final Set<String> TARGETS = Set.of("ecore", "emf", "xmi", "json", "xml", "drawio", "langium", "xtext", "antlr",
            "blockly-json", "blockly-javascript", "glsp", "plantuml", "markdown", "markdown-agent");
    private final ModelCodeGenerationSupport support;

    /** Descripción: Inicializa el renderer de proyecciones con el mapper compartido de producción. Entradas: mapper JSON. Salida: renderer listo. */
    /** Descripción: Inicializa una instancia de ProjectionModelCodeGenerationRenderer con sus dependencias y datos de entrada. Entradas: mapper: componente de conversión utilizado durante el procesamiento. Salida: deja la instancia inicializada y lista para su uso. */ public ProjectionModelCodeGenerationRenderer(JsonMapper mapper) { this.support = new ModelCodeGenerationSupport(mapper); }

    /** Descripción: Expone las proyecciones atendidas por este renderer. Entradas: ninguna. Salida: identificadores de destinos no enriquecidos. */
    /** Descripción: Ejecuta la operación «targetIds» correspondiente a la responsabilidad de ProjectionModelCodeGenerationRenderer. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @Override public Set<String> targetIds() { return TARGETS; }

    /** Descripción: Indica que las proyecciones portables no admiten helpers propios de lenguajes de programación. Entradas: destino. Salida: conjunto vacío. */
    /** Descripción: Ejecuta la operación «supportedFeatures» correspondiente a la responsabilidad de ProjectionModelCodeGenerationRenderer. Entradas: targetId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ @Override public Set<ModelCodeGenerationFeature> supportedFeatures(String targetId) { requireTarget(targetId); return Set.of(); }

    /** Descripción: Genera una proyección portable del modelo activo. Entradas: destino, descriptor, metamodelo, modelo y opciones. Salida: artefacto textual descargable. */
    /** Descripción: Crea o prepara el resultado de «generate» dentro de ProjectionModelCodeGenerationRenderer. Entradas: targetId: identificador que delimita el recurso objetivo; target: dato de entrada necesario para la operación; metamodel: dato de entrada necesario para la operación; model: dato de entrada necesario para la operación; options: configuración que gobierna el comportamiento. Salida: devuelve el resultado definido por el contrato del método. */ @Override public GeneratedModelArtifact generate(String targetId, ModelCodeGenerationTarget target, MetaModel metamodel,
            CanonicalModelDocument model, ModelCodeGenerationOptions options) {
        requireTarget(targetId);
        String text = switch(targetId) {
            case "json" -> support.json(model, options);
            case "xml" -> xml(metamodel, model, options, "hydraModel");
            case "xmi" -> xml(metamodel, model, options, "xmiModel");
            case "ecore" -> xml(metamodel, model, options, "ecoreModelProjection");
            case "emf" -> emfatic(metamodel, model, options);
            case "drawio" -> drawio(metamodel, model, options);
            case "langium" -> textual(metamodel, model, options, "langium-model");
            case "xtext" -> textual(metamodel, model, options, "xtext-model");
            case "antlr" -> textual(metamodel, model, options, "antlr-model");
            case "blockly-json" -> blocklyJson(metamodel, model, options);
            case "blockly-javascript" -> "export const workspace = " + blocklyJson(metamodel, model, options) + ";\n";
            case "glsp" -> glsp(metamodel, model, options);
            case "plantuml" -> plantUml(metamodel, model, options);
            case "markdown" -> markdown(metamodel, model, options, false);
            case "markdown-agent" -> markdown(metamodel, model, options, true);
            default -> throw new IllegalArgumentException("Unsupported projection target: " + targetId);
        };
        String base = support.safeFile(options.artifactName().isBlank() ? model.name() : options.artifactName());
        return new GeneratedModelArtifact(base + target.extension(), target.mediaType(), text.getBytes(StandardCharsets.UTF_8));
    }

    /** Descripción: Genera una serialización XML neutral para variantes XMI/Ecore/XML. Entradas: metamodelo, modelo, opciones y elemento raíz. Salida: documento XML. */
    /** Descripción: Ejecuta la operación «xml» correspondiente a la responsabilidad de ProjectionModelCodeGenerationRenderer. Entradas: metamodel: dato de entrada necesario para la operación; model: dato de entrada necesario para la operación; options: configuración que gobierna el comportamiento; root: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String xml(MetaModel metamodel, CanonicalModelDocument model, ModelCodeGenerationOptions options, String root) {
        StringBuilder out = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<").append(root)
                .append(" xmlns:xmi=\"http://www.omg.org/XMI\" namespace=\"").append(support.xml(options.namespace())).append("\"");
        if(options.includeTraceability()) out.append(" hydraMetamodelId=\"").append(support.xml(metamodel.id())).append("\" hydraModelId=\"").append(support.xml(model.id())).append("\"");
        out.append(">\n");
        for(CanonicalModelElement element : model.elements()) {
            out.append("  <element xmi:id=\"").append(support.xml(element.id())).append("\" classId=\"").append(support.xml(element.classId())).append("\">\n");
            element.attributes().forEach((key,value) -> out.append("    <attribute id=\"").append(support.xml(key)).append("\" value=\"").append(support.xml(String.valueOf(value))).append("\"/>\n"));
            out.append("  </element>\n");
        }
        for(CanonicalModelRelation relation : model.relations()) out.append("  <relation xmi:id=\"").append(support.xml(relation.id())).append("\" relationId=\"").append(support.xml(relation.relationId())).append("\" source=\"").append(support.xml(relation.sourceElementId())).append("\" target=\"").append(support.xml(relation.targetElementId())).append("\"/>\n");
        return out.append("</").append(root).append(">\n").toString();
    }

    /** Descripción: Genera una proyección textual tipo Emfatic de las instancias. Entradas: metamodelo, modelo y opciones. Salida: texto Emfatic orientado a instancias. */
    /** Descripción: Ejecuta la operación «emfatic» correspondiente a la responsabilidad de ProjectionModelCodeGenerationRenderer. Entradas: metamodel: dato de entrada necesario para la operación; model: dato de entrada necesario para la operación; options: configuración que gobierna el comportamiento. Salida: devuelve el resultado definido por el contrato del método. */ private String emfatic(MetaModel metamodel, CanonicalModelDocument model, ModelCodeGenerationOptions options) {
        StringBuilder out = new StringBuilder("// HYDRA generated model projection\n").append(support.traceComment("//", model, options)).append("@namespace(uri=\"").append(support.escape(options.namespace())).append("\")\n");
        for(CanonicalModelElement element : model.elements()) out.append("object ").append(support.identifier(element.id())).append(" : ").append(support.className(metamodel, element.classId())).append(" { ").append(element.attributes().entrySet().stream().map(entry -> support.identifier(entry.getKey()) + " = " + support.literal(entry.getValue())).collect(Collectors.joining(", "))).append(" }\n");
        for(CanonicalModelRelation relation : model.relations()) out.append("link ").append(support.identifier(relation.sourceElementId())).append(" -[").append(support.identifier(relation.relationId())).append("]-> ").append(support.identifier(relation.targetElementId())).append("\n");
        return out.toString();
    }

    /** Descripción: Genera una proyección textual neutral reutilizable por dialectos Langium, Xtext y ANTLR. Entradas: metamodelo, modelo, opciones y dialecto. Salida: texto portable. */
    /** Descripción: Ejecuta la operación «textual» correspondiente a la responsabilidad de ProjectionModelCodeGenerationRenderer. Entradas: metamodel: dato de entrada necesario para la operación; model: dato de entrada necesario para la operación; options: configuración que gobierna el comportamiento; dialect: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String textual(MetaModel metamodel, CanonicalModelDocument model, ModelCodeGenerationOptions options, String dialect) {
        StringBuilder out = new StringBuilder("// HYDRA ").append(dialect).append(" projection\n").append(support.traceComment("//", model, options)).append("model ").append(support.identifier(model.name())).append(" namespace \"").append(support.escape(options.namespace())).append("\" {\n");
        for(CanonicalModelElement element : model.elements()) out.append("  ").append(support.className(metamodel, element.classId())).append(" ").append(support.identifier(element.id())).append(" { ").append(element.attributes().entrySet().stream().map(entry -> support.identifier(entry.getKey()) + ": " + support.literal(entry.getValue())).collect(Collectors.joining(", "))).append(" }\n");
        for(CanonicalModelRelation relation : model.relations()) out.append("  relation ").append(support.identifier(relation.relationId())).append(" ").append(support.identifier(relation.sourceElementId())).append(" -> ").append(support.identifier(relation.targetElementId())).append("\n");
        return out.append("}\n").toString();
    }

    /** Descripción: Genera un workspace Blockly JSON a partir de los elementos del modelo. Entradas: metamodelo, modelo y opciones. Salida: JSON Blockly portable. */
    /** Descripción: Ejecuta la operación «blocklyJson» correspondiente a la responsabilidad de ProjectionModelCodeGenerationRenderer. Entradas: metamodel: dato de entrada necesario para la operación; model: dato de entrada necesario para la operación; options: configuración que gobierna el comportamiento. Salida: devuelve el resultado definido por el contrato del método. */ private String blocklyJson(MetaModel metamodel, CanonicalModelDocument model, ModelCodeGenerationOptions options) {
        List<Map<String,Object>> blocks = new ArrayList<>(); int y = 30;
        for(CanonicalModelElement element : model.elements()) { Map<String,Object> block = new LinkedHashMap<>(); block.put("type", support.blockType(support.className(metamodel, element.classId()))); block.put("id", element.id()); block.put("x", 30); block.put("y", y); block.put("extraState", Map.of("attributes", element.attributes())); blocks.add(block); y += 70; }
        Map<String,Object> output = new LinkedHashMap<>(); output.put("languageVersion", 0); output.put("namespace", options.namespace());
        if(options.includeTraceability()) output.put("traceability", support.traceability(metamodel.id(), model.id()));
        output.put("blocks", Map.of("blocks", blocks)); output.put("relations", model.relations());
        return support.prettyJson(output, "Could not serialize Blockly model");
    }

    /** Descripción: Genera un grafo GLSP neutral desde el modelo activo. Entradas: metamodelo, modelo y opciones. Salida: JSON GLSP. */
    /** Descripción: Ejecuta la operación «glsp» correspondiente a la responsabilidad de ProjectionModelCodeGenerationRenderer. Entradas: metamodel: dato de entrada necesario para la operación; model: dato de entrada necesario para la operación; options: configuración que gobierna el comportamiento. Salida: devuelve el resultado definido por el contrato del método. */ private String glsp(MetaModel metamodel, CanonicalModelDocument model, ModelCodeGenerationOptions options) {
        Map<String,Object> output = new LinkedHashMap<>(); output.put("schemaVersion", "1.0"); output.put("type", "graph"); output.put("id", model.id()); output.put("namespace", options.namespace());
        if(options.includeTraceability()) output.put("traceability", support.traceability(metamodel.id(), model.id()));
        output.put("nodes", model.elements()); output.put("edges", model.relations());
        return support.prettyJson(output, "Could not serialize GLSP projection");
    }

    /** Descripción: Genera un diagrama draw.io de objetos y relaciones. Entradas: metamodelo y modelo. Salida: XML draw.io. */
    /** Descripción: Ejecuta la operación «drawio» correspondiente a la responsabilidad de ProjectionModelCodeGenerationRenderer. Entradas: metamodel: dato de entrada necesario para la operación; model: dato de entrada necesario para la operación; options: configuración que gobierna el comportamiento. Salida: devuelve el resultado definido por el contrato del método. */ private String drawio(MetaModel metamodel, CanonicalModelDocument model, ModelCodeGenerationOptions options) {
        StringBuilder out = new StringBuilder("<mxfile host=\"HYDRA\"");
        if(options.includeTraceability()) out.append(" hydraMetamodelId=\"").append(support.xml(metamodel.id())).append("\" hydraModelId=\"").append(support.xml(model.id())).append("\"");
        out.append("><diagram name=\"Model\"><mxGraphModel><root><mxCell id=\"0\"/><mxCell id=\"1\" parent=\"0\"/>"); int x = 40, y = 40;
        for(CanonicalModelElement element : model.elements()) { out.append("<mxCell id=\"").append(support.xml(element.id())).append("\" value=\"").append(support.xml(support.className(metamodel, element.classId()))).append("\" vertex=\"1\" parent=\"1\"><mxGeometry x=\"").append(x).append("\" y=\"").append(y).append("\" width=\"140\" height=\"60\" as=\"geometry\"/></mxCell>"); x += 180; if(x > 760) { x = 40; y += 100; } }
        for(CanonicalModelRelation relation : model.relations()) out.append("<mxCell id=\"").append(support.xml(relation.id())).append("\" value=\"").append(support.xml(support.relationName(metamodel, relation.relationId()))).append("\" edge=\"1\" parent=\"1\" source=\"").append(support.xml(relation.sourceElementId())).append("\" target=\"").append(support.xml(relation.targetElementId())).append("\"><mxGeometry relative=\"1\" as=\"geometry\"/></mxCell>");
        return out.append("</root></mxGraphModel></diagram></mxfile>\n").toString();
    }

    /** Descripción: Genera un diagrama de objetos PlantUML. Entradas: metamodelo y modelo. Salida: documento PlantUML. */
    /** Descripción: Ejecuta la operación «plantUml» correspondiente a la responsabilidad de ProjectionModelCodeGenerationRenderer. Entradas: metamodel: dato de entrada necesario para la operación; model: dato de entrada necesario para la operación; options: configuración que gobierna el comportamiento. Salida: devuelve el resultado definido por el contrato del método. */ private String plantUml(MetaModel metamodel, CanonicalModelDocument model, ModelCodeGenerationOptions options) {
        StringBuilder out = new StringBuilder("@startuml\n").append(support.traceComment("'", model, options)).append("title ").append(model.name()).append("\n");
        for(CanonicalModelElement element : model.elements()) out.append("object \"").append(support.escape(support.className(metamodel, element.classId()))).append("\" as ").append(support.identifier(element.id())).append("\n");
        for(CanonicalModelRelation relation : model.relations()) out.append(support.identifier(relation.sourceElementId())).append(" --> ").append(support.identifier(relation.targetElementId())).append(" : ").append(support.escape(support.relationName(metamodel, relation.relationId()))).append("\n");
        return out.append("@enduml\n").toString();
    }

    /** Descripción: Genera documentación Markdown legible o contexto orientado a agentes. Entradas: metamodelo, modelo, opciones y modo agente. Salida: Markdown. */
    /** Descripción: Ejecuta la operación «markdown» correspondiente a la responsabilidad de ProjectionModelCodeGenerationRenderer. Entradas: metamodel: dato de entrada necesario para la operación; model: dato de entrada necesario para la operación; options: configuración que gobierna el comportamiento; agent: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String markdown(MetaModel metamodel, CanonicalModelDocument model, ModelCodeGenerationOptions options, boolean agent) {
        StringBuilder out = new StringBuilder(agent ? "# HYDRA Agent Model Context\n" : "# Generated model\n");
        out.append("\n- Namespace: `").append(options.namespace()).append("`\n");
        if(options.includeTraceability()) out.append("- Metamodel trace: `").append(metamodel.id()).append("`\n- Model trace: `").append(model.id()).append("`\n");
        out.append("\n## Elements\n");
        for(CanonicalModelElement element : model.elements()) out.append("\n### ").append(support.className(metamodel, element.classId())).append(" · `").append(element.id()).append("`\n").append(element.attributes().entrySet().stream().map(attribute -> "- `" + attribute.getKey() + "`: `" + String.valueOf(attribute.getValue()) + "`").collect(Collectors.joining("\n"))).append("\n");
        out.append("\n## Relations\n"); for(CanonicalModelRelation relation : model.relations()) out.append("- `").append(relation.sourceElementId()).append("` — **").append(support.relationName(metamodel, relation.relationId())).append("** → `").append(relation.targetElementId()).append("`\n");
        return out.toString();
    }

    /** Descripción: Rechaza identificadores ajenos a este renderer. Entradas: destino. Salida: no devuelve valor; valida el contrato. */
    /** Descripción: Valida las condiciones necesarias para «requireTarget» y rechaza entradas no válidas. Entradas: targetId: identificador que delimita el recurso objetivo. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private static void requireTarget(String targetId) { if(!TARGETS.contains(targetId)) throw new IllegalArgumentException("Projection renderer does not support target: " + targetId); }
}
