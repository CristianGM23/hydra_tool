package com.example.editor.integration.adapter.mxgraph;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.example.editor.definition.EdgeCreationTool;
import com.example.editor.definition.EdgeMapping;
import com.example.editor.definition.EditorDefinitionModel;
import com.example.editor.definition.EditorNodeShape;
import com.example.editor.definition.EditorTool;
import com.example.editor.definition.NodeCreationTool;
import com.example.editor.definition.NodeMapping;
import com.example.editor.definition.RepresentationDefinition;
import com.example.editor.definition.RepresentationKind;
import com.example.editor.integration.IntegrationArtifactKind;
import com.example.editor.integration.IntegrationDependencyChange;
import com.example.editor.integration.IntegrationDiagnostic;
import com.example.editor.integration.IntegrationTechnology;
import com.example.editor.integration.IntegrationVerificationStep;
import com.example.editor.integration.compiler.TechnologyAdapter;
import com.example.editor.integration.compiler.TechnologyAdapterGenerationSupport;
import com.example.editor.integration.compiler.TechnologyAdapterSupport;
import com.example.editor.integration.compiler.TechnologyCompilationException;
import com.example.editor.integration.compiler.TechnologyCompilationRequest;
import com.example.editor.integration.compiler.TechnologyCompilationResult;
import com.example.editor.integration.compiler.TechnologyGeneratedFile;
import com.example.editor.metamodel.core.MetaModel;

import tools.jackson.databind.json.JsonMapper;

/** Genera un runtime portable e inyectable para mxGraph y un plug-in autocontenido para diagrams.net/draw.io. */
@Component
public class MxGraphDrawioTechnologyAdapter implements TechnologyAdapter {
    private static final Set<IntegrationArtifactKind> SUPPORTED = EnumSet.allOf(IntegrationArtifactKind.class);
    private final JsonMapper mapper;

    /** Descripción: Inicializa una instancia de MxGraphDrawioTechnologyAdapter con sus dependencias y datos de entrada. Entradas: mapper: componente de conversión utilizado durante el procesamiento. Salida: deja la instancia inicializada y lista para su uso. */ public MxGraphDrawioTechnologyAdapter(JsonMapper mapper) { this.mapper = mapper; }

    /** Descripción: Ejecuta la operación «technology» correspondiente a la responsabilidad de MxGraphDrawioTechnologyAdapter. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @Override public IntegrationTechnology technology() { return IntegrationTechnology.MXGRAPH_DRAWIO; }
    /** Descripción: Ejecuta la operación «supportedArtifactKinds» correspondiente a la responsabilidad de MxGraphDrawioTechnologyAdapter. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @Override public Set<IntegrationArtifactKind> supportedArtifactKinds() { return Set.copyOf(SUPPORTED); }

    /** Descripción: Ejecuta la operación «compile» correspondiente a la responsabilidad de MxGraphDrawioTechnologyAdapter. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ @Override public TechnologyCompilationResult compile(TechnologyCompilationRequest request) {
        try {
            MetaModel metamodel = mapper.readValue(request.require(IntegrationArtifactKind.METAMODEL).content(), MetaModel.class);
            EditorDefinitionModel editor = mapper.readValue(request.require(IntegrationArtifactKind.EDITOR_DEFINITION).content(), EditorDefinitionModel.class);
            String slug = TechnologyAdapterSupport.slug(metamodel.id());
            String root = TechnologyAdapterSupport.generatedRoot(request, slug + "-mxgraph-drawio");
            String packageName = TechnologyAdapterGenerationSupport.npmPackage(
                    request.profile().target().settings().get("packageName"), "hydra-" + slug + "-mxgraph");
            String commandPrefix = TechnologyAdapterSupport.slug(
                    request.profile().target().settings().getOrDefault("commandPrefix", "hydra." + slug + ".mxgraph"));
            List<String> sourceIds = TechnologyAdapterGenerationSupport.sourceIds(request);
            String bundle = canonicalBundle(request);
            String runtime = runtimeSource(bundle, TechnologyAdapterGenerationSupport.activationEnabledByDefault(request));
            List<TechnologyGeneratedFile> files = new ArrayList<>();

            files.add(TechnologyAdapterSupport.text(root + "/package.json", "application/json", packageJson(packageName), sourceIds));
            files.add(TechnologyAdapterSupport.text(root + "/src/hydra-mxgraph-runtime.js", "text/javascript", runtime, sourceIds));
            files.add(TechnologyAdapterSupport.text(root + "/drawio/hydra-drawio-plugin.js", "text/javascript",
                    runtime + "\n" + drawioPluginSource(commandPrefix), sourceIds));
            files.addAll(drawioLibraries(root, editor, sourceIds));
            files.add(TechnologyAdapterSupport.text(root + "/resources/hydra-mxgraph-manifest.json", "application/json",
                    manifest(request, editor, packageName, commandPrefix), sourceIds));
            files.addAll(TechnologyAdapterGenerationSupport.canonicalDocuments(root, "resources/hydra", request));
            files.add(TechnologyAdapterSupport.text(root + "/tests/smoke.mjs", "text/javascript", smokeTest(), sourceIds));
            files.add(TechnologyAdapterSupport.text(root + "/tests/drawio-smoke.mjs", "text/javascript", drawioSmokeTest(), sourceIds));
            files.add(TechnologyAdapterSupport.text(root + "/README.md", "text/markdown",
                    readme(metamodel, editor, packageName, commandPrefix), sourceIds));

            List<IntegrationDependencyChange> dependencies = List.of();
            List<IntegrationVerificationStep> verification = List.of(
                    new IntegrationVerificationStep("mxgraph-runtime-syntax", "Check generated mxGraph runtime JavaScript syntax", root,
                            List.of("node", "--check", "src/hydra-mxgraph-runtime.js"), 0, true),
                    new IntegrationVerificationStep("drawio-plugin-syntax", "Check generated draw.io plug-in JavaScript syntax", root,
                            List.of("node", "--check", "drawio/hydra-drawio-plugin.js"), 0, true),
                    new IntegrationVerificationStep("mxgraph-runtime-smoke", "Execute the generated mxGraph runtime against a compatible host stub", root,
                            List.of("node", "tests/smoke.mjs"), 0, true),
                    new IntegrationVerificationStep("drawio-plugin-smoke", "Load the generated diagrams.net/draw.io plug-in against a compatible host stub", root,
                            List.of("node", "tests/drawio-smoke.mjs"), 0, true));
            List<IntegrationDiagnostic> diagnostics = new ArrayList<>(
                    TechnologyAdapterSupport.unsupportedDiagnostics(request, SUPPORTED, "mxGraph / draw.io"));
            if(editor.representations().stream().noneMatch(value -> value.kind() == RepresentationKind.DIAGRAM)) {
                diagnostics.add(new IntegrationDiagnostic(com.example.editor.integration.IntegrationDiagnosticSeverity.WARNING,
                        "MXGRAPH_WITHOUT_DIAGRAM", editor.id(), "representations",
                        "The editor has no DIAGRAM representation; canonical layers are preserved but no graphical draw.io palette can be generated."));
            }
            return new TechnologyCompilationResult(technology(), files, dependencies,
                    List.of("Use src/hydra-mxgraph-runtime.js from an mxGraph-compatible host, or load drawio/hydra-drawio-plugin.js as a diagrams.net plug-in.",
                            "Keep HYDRA disabled until the target metamodel is selected, then enable it with the generated runtime toggle.",
                            "Connect HYDRA_MXGRAPH_HOST_BRIDGE when validations, quick fixes, operations, events or gamification need executable host services."),
                    List.of("Disable the HYDRA runtime before uninstalling the package.", "Remove the generated package or draw.io plug-in and its custom libraries."),
                    verification, diagnostics,
                    Map.of("generatorVersion", request.generatorVersion(), "moduleRoot", root, "packageName", packageName,
                            "commandPrefix", commandPrefix, "runtimeActivation", "TOGGLEABLE_SAFE_BY_DEFAULT",
                            "host", "MXGRAPH_COMPATIBLE,DRAWIO", "paletteLibraries", Integer.toString(diagramRepresentations(editor).size())));
        } catch (RuntimeException exception) {
            if(exception instanceof TechnologyCompilationException) throw exception;
            throw new TechnologyCompilationException("Could not generate mxGraph / draw.io integration package", exception);
        }
    }

    /** Descripción: Evalúa la condición «canonicalBundle» sin modificar el estado externo. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private String canonicalBundle(TechnologyCompilationRequest request) {
        try {
            Map<String, Object> documents = new LinkedHashMap<>();
            request.artifacts().stream().sorted(Comparator.comparing(value -> value.kind().name())).forEach(artifact -> {
                Object content;
                try { content = mapper.readTree(artifact.content()); }
                catch(RuntimeException exception) { content = Map.of("encoding", "base64", "value", Base64.getEncoder().encodeToString(artifact.content())); }
                documents.put(artifact.kind().name(), Map.of("documentId", artifact.documentId(), "mediaType", artifact.mediaType(), "content", content));
            });
            String json = mapper.writeValueAsString(Map.of("schemaVersion", "1.0", "metamodelId", request.profile().metamodelId(), "documents", documents));
            return json.replace("\u2028", "\\u2028").replace("\u2029", "\\u2029");
        } catch(RuntimeException exception) { throw new TechnologyCompilationException("Could not serialize mxGraph canonical bundle", exception); }
    }

    /** Descripción: Ejecuta la operación «packageJson» correspondiente a la responsabilidad de MxGraphDrawioTechnologyAdapter. Entradas: packageName: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String packageJson(String packageName) {
        try {
            Map<String, Object> json = new LinkedHashMap<>();
            json.put("name", packageName);
            json.put("version", "1.0.0");
            json.put("description", "HYDRA-generated mxGraph runtime and diagrams.net/draw.io injectable plug-in");
            json.put("main", "src/hydra-mxgraph-runtime.js");
            json.put("files", List.of("src", "drawio", "resources", "README.md"));
            json.put("scripts", Map.of("check", "node --check src/hydra-mxgraph-runtime.js && node --check drawio/hydra-drawio-plugin.js", "test", "node tests/smoke.mjs && node tests/drawio-smoke.mjs"));
            json.put("keywords", List.of("hydra", "mxgraph", "drawio", "diagrams.net", "modeling"));
            json.put("license", "UNLICENSED");
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(json) + "\n";
        } catch(RuntimeException exception) { throw new TechnologyCompilationException("Could not create mxGraph package.json", exception); }
    }

    /** Descripción: Ejecuta la operación «manifest» correspondiente a la responsabilidad de MxGraphDrawioTechnologyAdapter. Entradas: request: solicitud con los datos necesarios para ejecutar la operación; editor: dato de entrada necesario para la operación; packageName: dato de entrada necesario para la operación; commandPrefix: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String manifest(TechnologyCompilationRequest request, EditorDefinitionModel editor, String packageName, String commandPrefix) {
        try {
            List<Map<String, Object>> reps = diagramRepresentations(editor).stream().map(rep -> Map.<String, Object>of(
                    "id", rep.id(), "name", rep.name(), "library", "drawio/hydra-palette-" + TechnologyAdapterSupport.slug(rep.id()) + ".xml",
                    "nodeMappings", rep.nodeMappings().size(), "edgeMappings", rep.edgeMappings().size())).toList();
            Map<String, Object> manifest = new LinkedHashMap<>();
            manifest.put("schemaVersion", "1.0");
            manifest.put("technology", "MXGRAPH_DRAWIO");
            manifest.put("packageName", packageName);
            manifest.put("metamodelId", request.profile().metamodelId());
            manifest.put("editorDefinitionId", editor.id());
            manifest.put("runtimeEntry", "src/hydra-mxgraph-runtime.js");
            manifest.put("drawioPlugin", "drawio/hydra-drawio-plugin.js");
            manifest.put("commandPrefix", commandPrefix);
            manifest.put("activation", Map.of("mode", request.profile().annotations().getOrDefault("hydra.runtime.activation", "TOGGLEABLE"),
                    "enabledByDefault", TechnologyAdapterGenerationSupport.activationEnabledByDefault(request)));
            manifest.put("representations", reps);
            manifest.put("bridgeOperations", List.of("validate", "applyQuickFix", "runAction", "runOperation", "invokeTool", "publishAfterCommit", "executeEventFlow", "gamification", "resolveAsset", "evaluateExpression"));
            manifest.put("visualCustomization", Map.of("baseStyles", true, "conditionalStyles", true, "assets", true, "failSafeConditionalFallback", true, "maximumConditionalStyles", 64));
            manifest.put("security", Map.of("dynamicEvaluation", false, "externalCodeExecution", false, "hostBridgeRequiredForBehavior", true));
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(manifest) + "\n";
        } catch(RuntimeException exception) { throw new TechnologyCompilationException("Could not create mxGraph manifest", exception); }
    }

    /** Descripción: Ejecuta la operación «drawioLibraries» correspondiente a la responsabilidad de MxGraphDrawioTechnologyAdapter. Entradas: root: ruta o ubicación utilizada por la operación; editor: dato de entrada necesario para la operación; sourceIds: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<TechnologyGeneratedFile> drawioLibraries(String root, EditorDefinitionModel editor, List<String> sourceIds) {
        List<TechnologyGeneratedFile> result = new ArrayList<>();
        List<String> combined = new ArrayList<>();
        for(RepresentationDefinition representation : diagramRepresentations(editor)) {
            List<String> entries = libraryEntries(editor, representation);
            combined.addAll(entries);
            result.add(TechnologyAdapterSupport.text(root + "/drawio/hydra-palette-" + TechnologyAdapterSupport.slug(representation.id()) + ".xml",
                    "application/xml", libraryXml(entries), sourceIds));
        }
        result.add(TechnologyAdapterSupport.text(root + "/drawio/hydra-palette.xml", "application/xml", libraryXml(combined), sourceIds));
        return result;
    }

    /** Descripción: Ejecuta la operación «libraryEntries» correspondiente a la responsabilidad de MxGraphDrawioTechnologyAdapter. Entradas: editor: dato de entrada necesario para la operación; representation: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<String> libraryEntries(EditorDefinitionModel editor, RepresentationDefinition representation) {
        List<String> result = new ArrayList<>();
        Map<String, NodeMapping> nodeMappings = new LinkedHashMap<>();
        Map<String, EdgeMapping> edgeMappings = new LinkedHashMap<>();
        representation.nodeMappings().forEach(mapping -> nodeMappings.putIfAbsent(mapping.targetClassId(), mapping));
        representation.edgeMappings().forEach(mapping -> edgeMappings.putIfAbsent(mapping.id(), mapping));
        for(EditorTool tool : editor.palette().tools()) {
            if(!appliesTo(tool.representationIds(), representation.id())) continue;
            try {
                if(tool instanceof NodeCreationTool node) {
                    NodeMapping mapping = nodeMappings.get(node.targetClassId());
                    if(mapping == null) continue;
                    String graphXml = templateGraphXml(editor.metamodelId(), representation, mapping, node);
                    result.add(mapper.writeValueAsString(Map.of("xml", graphXml, "w", mapping.defaultWidth(), "h", mapping.defaultHeight(),
                            "title", representation.name() + " — " + node.label())));
                } else if(tool instanceof EdgeCreationTool edge) {
                    EdgeMapping mapping = edgeMappings.get(edge.edgeMappingId());
                    if(mapping == null) continue;
                    String graphXml = templateEdgeGraphXml(editor.metamodelId(), representation, mapping, edge);
                    result.add(mapper.writeValueAsString(Map.of("xml", graphXml, "w", 120, "h", 40,
                            "title", representation.name() + " — " + edge.label())));
                }
            } catch(RuntimeException exception) { throw new TechnologyCompilationException("Could not serialize draw.io palette entry", exception); }
        }
        return result;
    }

    /** Descripción: Ejecuta la operación «libraryXml» correspondiente a la responsabilidad de MxGraphDrawioTechnologyAdapter. Entradas: entries: colección de elementos que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String libraryXml(List<String> entries) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<mxlibrary>" + TechnologyAdapterSupport.xml("[" + String.join(",", entries) + "]") + "</mxlibrary>\n";
    }

    /** Descripción: Ejecuta la operación «templateGraphXml» correspondiente a la responsabilidad de MxGraphDrawioTechnologyAdapter. Entradas: representation: dato de entrada necesario para la operación; mapping: dato de entrada necesario para la operación; tool: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String templateGraphXml(String metamodelId, RepresentationDefinition representation, NodeMapping mapping, NodeCreationTool tool) {
        String style = mxNodeStyle(mapping, "");
        return "<mxGraphModel><root><mxCell id=\"0\"/><mxCell id=\"1\" parent=\"0\"/><object label=\""
                + TechnologyAdapterSupport.xml(tool.label()) + "\" hydraMetamodelId=\"" + TechnologyAdapterSupport.xml(metamodelId)
                + "\" hydraRepresentationId=\"" + TechnologyAdapterSupport.xml(representation.id()) + "\" hydraClassId=\""
                + TechnologyAdapterSupport.xml(mapping.targetClassId()) + "\" hydraMappingId=\"" + TechnologyAdapterSupport.xml(mapping.id())
                + "\" hydraToolId=\"" + TechnologyAdapterSupport.xml(tool.id()) + "\"><mxCell vertex=\"1\" parent=\"1\" style=\""
                + TechnologyAdapterSupport.xml(style) + "\"><mxGeometry width=\"" + mapping.defaultWidth() + "\" height=\""
                + mapping.defaultHeight() + "\" as=\"geometry\"/></mxCell></object></root></mxGraphModel>";
    }

    /** Descripción: Genera la plantilla XML de una herramienta de creación de aristas para una biblioteca draw.io conservando la procedencia HYDRA. Entradas: metamodelId: identificador del metamodelo; representation: representación de origen; mapping: mapping de arista; tool: herramienta asociada. Salida: devuelve un mxGraphModel importable como entrada de biblioteca. */ private String templateEdgeGraphXml(String metamodelId, RepresentationDefinition representation, EdgeMapping mapping, EdgeCreationTool tool) {
        String style = mxEdgeStyle(mapping);
        return "<mxGraphModel><root><mxCell id=\"0\"/><mxCell id=\"1\" parent=\"0\"/><object label=\""
                + TechnologyAdapterSupport.xml(tool.label()) + "\" hydraMetamodelId=\"" + TechnologyAdapterSupport.xml(metamodelId)
                + "\" hydraRepresentationId=\"" + TechnologyAdapterSupport.xml(representation.id()) + "\" hydraEdgeMappingId=\""
                + TechnologyAdapterSupport.xml(mapping.id()) + "\" hydraToolId=\"" + TechnologyAdapterSupport.xml(tool.id())
                + "\" hydraRelationId=\"" + TechnologyAdapterSupport.xml(tool.relationId())
                + "\"><mxCell edge=\"1\" parent=\"1\" style=\"" + TechnologyAdapterSupport.xml(style)
                + "\"><mxGeometry relative=\"1\" as=\"geometry\"/></mxCell></object></root></mxGraphModel>";
    }

    /** Descripción: Convierte el estilo neutral de una arista HYDRA al formato de estilo de mxGraph. Entradas: mapping: mapping visual de arista. Salida: devuelve el estilo mxGraph equivalente. */ private String mxEdgeStyle(EdgeMapping mapping) {
        List<String> parts = new ArrayList<>();
        switch(mapping.lineType()) {
            case BEZIER -> parts.add("curved=1");
            case STRAIGHT -> parts.add("edgeStyle=none");
            case STEP -> parts.add("edgeStyle=elbowEdgeStyle");
            case ORTHOGONAL -> parts.add("edgeStyle=orthogonalEdgeStyle");
            case SMOOTH_STEP -> { parts.add("edgeStyle=orthogonalEdgeStyle"); parts.add("rounded=1"); }
        }
        mapping.styles().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            String key = mxStyleKey(entry.getKey()); String value = safeStyleValue(entry.getValue());
            if(!key.isBlank() && !value.isBlank()) parts.add(key + "=" + value);
        });
        parts.add("html=1");
        return String.join(";", parts) + ";";
    }

    /** Descripción: Ejecuta la operación «mxNodeStyle» correspondiente a la responsabilidad de MxGraphDrawioTechnologyAdapter. Entradas: mapping: dato de entrada necesario para la operación; image: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String mxNodeStyle(NodeMapping mapping, String image) {
        List<String> parts = new ArrayList<>();
        parts.add(switch(mapping.shape()) {
            case RECTANGLE -> "shape=rectangle";
            case ROUNDED_RECTANGLE -> "shape=rectangle;rounded=1";
            case ELLIPSE -> "shape=ellipse";
            case DIAMOND -> "shape=rhombus";
            case TRIANGLE -> "shape=triangle";
            case PENTAGON, HEXAGON, OCTAGON -> "shape=hexagon";
            case PARALLELOGRAM -> "shape=parallelogram";
            case TRAPEZOID -> "shape=trapezoid";
            case CYLINDER -> "shape=cylinder";
            case DOCUMENT -> "shape=document";
            case CLOUD -> "shape=cloud";
            case STAR -> "shape=star";
            case CROSS -> "shape=cross";
            case SVG_PATH -> "shape=rectangle";
            case PROJECT_IMAGE -> "shape=image";
        });
        if(mapping.shape() == EditorNodeShape.PROJECT_IMAGE && image != null && !image.isBlank()) parts.add("image=" + image);
        mapping.styles().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            String key = mxStyleKey(entry.getKey()); String value = safeStyleValue(entry.getValue());
            if(!key.isBlank() && !value.isBlank()) parts.add(key + "=" + value);
        });
        parts.add("whiteSpace=wrap"); parts.add("html=1");
        return String.join(";", parts) + ";";
    }

    /** Descripción: Ejecuta la operación «mxStyleKey» correspondiente a la responsabilidad de MxGraphDrawioTechnologyAdapter. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String mxStyleKey(String value) {
        if(value == null) return "";
        return switch(value.trim().toLowerCase(Locale.ROOT)) {
            case "fill", "fillcolor", "background", "backgroundcolor" -> "fillColor";
            case "stroke", "strokecolor", "border", "bordercolor" -> "strokeColor";
            case "color", "fontcolor", "labelcolor" -> "fontColor";
            case "strokewidth", "linewidth" -> "strokeWidth";
            case "fontsize" -> "fontSize";
            case "fontstyle" -> "fontStyle";
            case "opacity" -> "opacity";
            case "rounded" -> "rounded";
            case "dashed" -> "dashed";
            default -> value.matches("[A-Za-z][A-Za-z0-9]*") ? value : "";
        };
    }

    /** Descripción: Ejecuta la operación «safeStyleValue» correspondiente a la responsabilidad de MxGraphDrawioTechnologyAdapter. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String safeStyleValue(String value) {
        return value == null ? "" : value.trim().replace(";", "%3B").replace("\n", "").replace("\r", "");
    }

    /** Descripción: Ejecuta la operación «diagramRepresentations» correspondiente a la responsabilidad de MxGraphDrawioTechnologyAdapter. Entradas: editor: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<RepresentationDefinition> diagramRepresentations(EditorDefinitionModel editor) {
        return editor.representations().stream().filter(value -> value.kind() == RepresentationKind.DIAGRAM).toList();
    }

    /** Descripción: Ejecuta la operación «appliesTo» correspondiente a la responsabilidad de MxGraphDrawioTechnologyAdapter. Entradas: representationIds: dato de entrada necesario para la operación; representationId: identificador que delimita el recurso objetivo. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean appliesTo(List<String> representationIds, String representationId) {
        return representationIds == null || representationIds.isEmpty() || representationIds.contains(representationId);
    }

    /** Descripción: Procesa la operación «runtimeSource» coordinando las responsabilidades de MxGraphDrawioTechnologyAdapter. Entradas: bundle: dato de entrada necesario para la operación; enabledByDefault: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String runtimeSource(String bundle, boolean enabledByDefault) {
        String source = """
(function(global){
'use strict';
const BUNDLE=__HYDRA_BUNDLE__;
const DEFAULT_ENABLED=__HYDRA_ENABLED__;
const documents=()=>BUNDLE.documents||{};
const canonical=(kind)=>documents()[kind]?.content??null;
const editor=()=>canonical('EDITOR_DEFINITION')||{representations:[],palette:{groups:[],tools:[]},forms:[]};
const array=(value)=>Array.isArray(value)?value:[];
const applies=(tool,representationId)=>array(tool?.representationIds).length===0||array(tool?.representationIds).includes(representationId);
const rep=(id)=>array(editor().representations).find(value=>value.id===id)||null;
const diagramReps=()=>array(editor().representations).filter(value=>value.kind==='DIAGRAM');
const firstDiagram=()=>diagramReps()[0]||null;
const toolById=(id)=>array(editor().palette?.tools).find(value=>value.id===id)||null;
const nodeMapping=(classId,representationId)=>array(rep(representationId)?.nodeMappings).find(value=>value.targetClassId===classId)||null;
const edgeMapping=(mappingId,representationId)=>array(rep(representationId)?.edgeMappings).find(value=>value.id===mappingId)||null;
const semanticStyleKeys=new Set(['shape','routestyle','borderstyle','imageassetpath']);
const styleKey=(key)=>{const raw=String(key||''),lower=raw.toLowerCase();if(semanticStyleKeys.has(lower))return '';return ({fill:'fillColor',fillcolor:'fillColor',background:'fillColor',backgroundcolor:'fillColor',stroke:'strokeColor',strokecolor:'strokeColor',border:'strokeColor',bordercolor:'strokeColor',color:'fontColor',fontcolor:'fontColor',labelcolor:'fontColor',strokewidth:'strokeWidth',linewidth:'strokeWidth',fontsize:'fontSize',fontstyle:'fontStyle',opacity:'opacity',rounded:'rounded',dashed:'dashed'}[lower]||(/^[A-Za-z][A-Za-z0-9]*$/.test(raw)?raw:''));};
const styleValue=(value)=>String(value??'').trim().replaceAll(';','%3B').replace(/[\\r\\n]/g,'');
const boundedInt=(value,fallback,min,max)=>{const parsed=Number.parseInt(String(value??''),10);return Number.isFinite(parsed)?Math.max(min,Math.min(max,parsed)):fallback;};
const encodeImageForStyle=(uri)=>{
  const value=String(uri||'').trim(); if(!value)return '';
  if(!value.startsWith('data:'))return styleValue(value);
  if(value.startsWith('data:image/svg+xml,')&&!value.includes(';base64,'))return value.replaceAll(';','%3B');
  const escaped=value.replaceAll('&','&amp;').replaceAll('"','&quot;').replaceAll('<','&lt;').replaceAll('>','&gt;');
  return 'data:image/svg+xml,'+encodeURIComponent('<svg xmlns="http://www.w3.org/2000/svg" width="100" height="100"><image href="'+escaped+'" width="100%" height="100%" preserveAspectRatio="xMidYMid meet"/></svg>');
};
const baseNodeShape=(shape)=>({RECTANGLE:'shape=rectangle',ROUNDED_RECTANGLE:'shape=rectangle;rounded=1',ELLIPSE:'shape=ellipse',DIAMOND:'shape=rhombus',TRIANGLE:'shape=triangle',PENTAGON:'shape=hexagon',HEXAGON:'shape=hexagon',OCTAGON:'shape=hexagon',PARALLELOGRAM:'shape=parallelogram',TRAPEZOID:'shape=trapezoid',CYLINDER:'shape=cylinder',DOCUMENT:'shape=document',CLOUD:'shape=cloud',STAR:'shape=star',CROSS:'shape=cross',SVG_PATH:'shape=rectangle',PROJECT_IMAGE:'shape=image'}[shape]||'shape=rectangle');
const appendStyles=(parts,styles)=>Object.entries(styles||{}).sort(([a],[b])=>a.localeCompare(b)).forEach(([key,value])=>{const mapped=styleKey(key),safe=styleValue(value);if(mapped&&safe)parts.push(mapped+'='+safe);});
const appendBorderStyle=(parts,styles)=>{const border=String(styles?.borderStyle||'').toUpperCase();if(border==='DASHED')parts.push('dashed=1');else if(border==='DOTTED')parts.push('dashed=1','dashPattern=1 4');else if(border==='DOUBLE')parts.push('double=1');else if(border==='NONE')parts.push('strokeColor=none');};
const nodeStyle=(mapping,styles,resolveAsset)=>{const effective=styles||mapping?.styles||{};const shape=String(effective.shape||mapping?.shape||'RECTANGLE').toUpperCase();const parts=[baseNodeShape(shape)];const asset=effective.imageAssetPath||mapping?.imageAssetPath;if(shape==='PROJECT_IMAGE'&&asset){const resolved=resolveAsset(asset);if(resolved)parts.push('image='+encodeImageForStyle(resolved));}appendStyles(parts,effective);appendBorderStyle(parts,effective);parts.push('whiteSpace=wrap','html=1');return parts.join(';')+';';};
const edgeStyle=(mapping,styles)=>{const effective=styles||mapping?.styles||{};const parts=[];const type=String(effective.routeStyle||mapping?.lineType||'SMOOTH_STEP').toUpperCase();if(type==='BEZIER'||type==='SMOOTH')parts.push('curved=1');else if(type==='STRAIGHT')parts.push('edgeStyle=none');else if(type==='STEP')parts.push('edgeStyle=elbowEdgeStyle');else if(type==='ORTHOGONAL')parts.push('edgeStyle=orthogonalEdgeStyle');else parts.push('edgeStyle=orthogonalEdgeStyle','rounded=1');appendStyles(parts,effective);appendBorderStyle(parts,effective);parts.push('html=1');return parts.join(';')+';';};
const annotate=(cell,values)=>{if(!cell)return cell;Object.entries(values).forEach(([key,value])=>{if(value!==undefined&&value!==null&&value!=='')cell[key]=value;});return cell;};
function create(config={}){
  let enabled=config.enabled??DEFAULT_ENABLED;let selected=config.representationId||firstDiagram()?.id||'';let graph=config.graph||null;
  const bridge=config.bridge||global.HYDRA_MXGRAPH_HOST_BRIDGE||{};
  const maximumConditionalStyles=boundedInt(config.maximumConditionalStyles??bridge.maximumConditionalStyles,64,1,64);
  const maximumStyleProperties=boundedInt(config.maximumStyleProperties??bridge.maximumStyleProperties,64,1,256);
  const reportDiagnostic=(diagnostic)=>{
    try{if(typeof bridge?.reportVisualDiagnostic==='function')bridge.reportVisualDiagnostic(diagnostic);return true;}
    catch(reportError){return false;}
  };
  const resolveAsset=(path)=>{
    try{if(typeof config.assetResolver==='function')return config.assetResolver(path);if(typeof bridge.resolveAsset==='function')return bridge.resolveAsset(path);}
    catch(error){reportDiagnostic({code:'VISUAL_ASSET_RESOLUTION_FAILED',assetPath:String(path||''),message:error instanceof Error?error.message:String(error)});}
    return path;
  };
  const evaluateExpression=(expressionId,subject,variables,mappingId='',ruleId='')=>{
    const fn=typeof config.evaluateExpression==='function'?config.evaluateExpression:bridge?.evaluateExpression;
    if(typeof fn!=='function')return false;
    try{const value=fn(expressionId,subject,variables||{});return value&&typeof value.then==='function'?false:Boolean(value);}
    catch(error){reportDiagnostic({code:'VISUAL_CONDITION_EVALUATION_FAILED',mappingId:String(mappingId||''),ruleId:String(ruleId||''),expressionId:String(expressionId||''),message:error instanceof Error?error.message:String(error)});return false;}
  };
  const safeStyleEntry=(key)=>{const name=String(key||'').trim();return name&&name.length<=128&&!['__proto__','prototype','constructor'].includes(name.toLowerCase());};
  const boundedStyleValue=(value)=>String(value??'').replace(/[\\u0000-\\u0008\\u000b\\u000c\\u000e-\\u001f]/g,'').slice(0,4096);
  const resolveStyles=(mapping,subject,variables)=>{const resolved={};for(const [key,value] of Object.entries(mapping?.styles||{}).sort(([a],[b])=>a.localeCompare(b))){if(Object.keys(resolved).length>=maximumStyleProperties)break;if(!safeStyleEntry(key))continue;resolved[key]=boundedStyleValue(value);}const rules=array(mapping?.conditionalStyles).filter(rule=>rule?.enabled!==false&&rule?.conditionExpressionId).sort((a,b)=>(Number(a.priority)||0)-(Number(b.priority)||0)||String(a.id||'').localeCompare(String(b.id||''))).slice(0,maximumConditionalStyles);for(const rule of rules){if(!evaluateExpression(rule.conditionExpressionId,subject,variables,String(mapping?.id||''),String(rule.id||'')))continue;for(const [key,value] of Object.entries(rule.styles||{}).sort(([a],[b])=>a.localeCompare(b))){if(!safeStyleEntry(key))continue;if(!(key in resolved)&&Object.keys(resolved).length>=maximumStyleProperties)break;resolved[key]=boundedStyleValue(value);}}return resolved;};
  const requireEnabled=()=>{if(!enabled)throw new Error('HYDRA mxGraph runtime is disabled for '+BUNDLE.metamodelId);};
  const callBridge=(name,args)=>{requireEnabled();const fn=bridge?.[name];if(typeof fn!=='function')throw new Error('HYDRA host bridge does not implement '+name);return fn(...args);};
  const api={
    metamodelId:BUNDLE.metamodelId,bundle:()=>BUNDLE,canonical,
    isEnabled:()=>enabled,isEnabledFor:(id)=>enabled&&id===BUNDLE.metamodelId,setEnabled:(value)=>{enabled=Boolean(value);return enabled;},toggle:()=>{enabled=!enabled;return enabled;},
    install:(candidate)=>{graph=candidate;return api;},representations:()=>array(editor().representations),diagramRepresentations:()=>diagramReps(),
    currentRepresentation:()=>rep(selected),selectRepresentation:(id)=>{if(!rep(id))throw new Error('Unknown HYDRA representation: '+id);selected=id;return rep(id);},
    mappingFor:(classId,representationId=selected)=>nodeMapping(classId,representationId),edgeMappingFor:(mappingId,representationId=selected)=>edgeMapping(mappingId,representationId),
    tools:(representationId=selected)=>array(editor().palette?.tools).filter(tool=>applies(tool,representationId)),forms:()=>array(editor().forms),formForClass:(classId)=>array(editor().forms).find(value=>value.targetClassId===classId)||null,
    resolveNodeStyle:(mappingOrClassId,subject=null,variables={},representationId=selected)=>{const mapping=typeof mappingOrClassId==='string'?nodeMapping(mappingOrClassId,representationId):mappingOrClassId;if(!mapping)return '';return nodeStyle(mapping,resolveStyles(mapping,subject,variables),resolveAsset);},
    resolveEdgeStyle:(mappingOrId,subject=null,variables={},representationId=selected)=>{const mapping=typeof mappingOrId==='string'?edgeMapping(mappingOrId,representationId):mappingOrId;if(!mapping)return '';return edgeStyle(mapping,resolveStyles(mapping,subject,variables));},
    applyAppearance:(cell,subject=null,variables={})=>{requireEnabled();if(!cell)return '';let style='';if(cell.hydraEdgeMappingId)style=api.resolveEdgeStyle(cell.hydraEdgeMappingId,subject,variables,cell.hydraRepresentationId||selected);else if(cell.hydraClassId)style=api.resolveNodeStyle(cell.hydraClassId,subject,variables,cell.hydraRepresentationId||selected);if(!style)return '';if(graph&&typeof graph.setCellStyle==='function')graph.setCellStyle(style,[cell]);else if(graph?.getModel&&typeof graph.getModel()?.setStyle==='function')graph.getModel().setStyle(cell,style);else cell.style=style;return style;},
    paletteEntries:(representationId=selected)=>api.tools(representationId).map(tool=>{if(tool.kind==='CREATE_NODE'){const mapping=nodeMapping(tool.targetClassId,representationId);return mapping?{kind:'NODE',tool,mapping,style:nodeStyle(mapping,mapping.styles,resolveAsset),width:mapping.defaultWidth,height:mapping.defaultHeight}:null;}if(tool.kind==='CREATE_EDGE'){const mapping=edgeMapping(tool.edgeMappingId,representationId);return mapping?{kind:'EDGE',tool,mapping,style:edgeStyle(mapping,mapping.styles)}:null;}return {kind:'ACTION',tool};}).filter(Boolean),
    createNode:(toolId,x=0,y=0,parent,subject=null,variables={})=>{requireEnabled();if(!graph||typeof graph.insertVertex!=='function')throw new Error('No mxGraph-compatible graph is installed');const tool=toolById(toolId);if(!tool||tool.kind!=='CREATE_NODE')throw new Error('Unknown node creation tool: '+toolId);if(!applies(tool,selected))throw new Error('Tool is not available in representation '+selected);const mapping=nodeMapping(tool.targetClassId,selected);if(!mapping)throw new Error('No node mapping for '+tool.targetClassId+' in '+selected);const targetParent=parent??(typeof graph.getDefaultParent==='function'?graph.getDefaultParent():null);const cell=graph.insertVertex(targetParent,null,tool.label||tool.targetClassId,x,y,Number(mapping.defaultWidth)||120,Number(mapping.defaultHeight)||80,api.resolveNodeStyle(mapping,subject,variables));return annotate(cell,{hydraMetamodelId:BUNDLE.metamodelId,hydraRepresentationId:selected,hydraClassId:tool.targetClassId,hydraMappingId:mapping.id,hydraToolId:tool.id});},
    createEdge:(toolId,source,target,parent,subject=null,variables={})=>{requireEnabled();if(!graph||typeof graph.insertEdge!=='function')throw new Error('No mxGraph-compatible graph is installed');const tool=toolById(toolId);if(!tool||tool.kind!=='CREATE_EDGE')throw new Error('Unknown edge creation tool: '+toolId);if(!applies(tool,selected))throw new Error('Tool is not available in representation '+selected);const mapping=edgeMapping(tool.edgeMappingId,selected);if(!mapping)throw new Error('No edge mapping '+tool.edgeMappingId+' in '+selected);const sourceClass=source?.hydraClassId,targetClass=target?.hydraClassId;if(array(tool.sourceClassIds).length&&sourceClass&&!array(tool.sourceClassIds).includes(sourceClass))throw new Error('Invalid edge source class '+sourceClass);if(array(tool.targetClassIds).length&&targetClass&&!array(tool.targetClassIds).includes(targetClass))throw new Error('Invalid edge target class '+targetClass);const targetParent=parent??(typeof graph.getDefaultParent==='function'?graph.getDefaultParent():null);const cell=graph.insertEdge(targetParent,null,tool.label||'',source,target,api.resolveEdgeStyle(mapping,subject,variables));return annotate(cell,{hydraMetamodelId:BUNDLE.metamodelId,hydraRepresentationId:selected,hydraEdgeMappingId:mapping.id,hydraToolId:tool.id,hydraRelationId:tool.relationId||mapping.relationId});},
    invokeTool:(toolId,...args)=>{const tool=toolById(toolId);if(!tool)throw new Error('Unknown HYDRA tool: '+toolId);if(tool.kind==='CREATE_NODE')return api.createNode(toolId,...args);if(tool.kind==='CREATE_EDGE')return api.createEdge(toolId,...args);if(tool.kind==='VALIDATE')return api.validate(...args);if(tool.kind==='RUN_ACTION')return api.runAction(tool.actionId,...args);if(tool.kind==='INVOKE_OPERATION')return api.runOperation(tool.operationId,...args);throw new Error('Unsupported HYDRA tool kind '+tool.kind);},
    validate:(model,context={})=>callBridge('validate',[model,{...context,metamodelId:BUNDLE.metamodelId,representationId:selected,validations:canonical('VALIDATIONS')}]),
    applyQuickFix:(ruleId,fixId,model,context={})=>callBridge('applyQuickFix',[ruleId,fixId,model,{...context,metamodelId:BUNDLE.metamodelId,actions:canonical('ACTIONS')}]),
    runAction:(actionId,model,context={})=>callBridge('runAction',[actionId,model,{...context,metamodelId:BUNDLE.metamodelId,actions:canonical('ACTIONS')}]),
    runOperation:(operationId,model,parameters={},context={})=>callBridge('runOperation',[operationId,model,parameters,{...context,metamodelId:BUNDLE.metamodelId,operations:canonical('OPERATION_BEHAVIORS')}]),
    publishAfterCommit:(events,context={})=>callBridge('publishAfterCommit',[events,{...context,metamodelId:BUNDLE.metamodelId,eventDefinitions:canonical('EVENT_DEFINITIONS'),eventBindings:canonical('EVENT_BINDINGS')}]),
    executeEventFlow:(flowId,event,context={})=>callBridge('executeEventFlow',[flowId,event,{...context,metamodelId:BUNDLE.metamodelId,eventFlows:canonical('EVENT_FLOWS')}]),
    gamification:(event,context={})=>callBridge('gamification',[event,{...context,metamodelId:BUNDLE.metamodelId,definitions:canonical('GAMIFICATION_DEFINITIONS'),bindings:canonical('GAMIFICATION_BINDINGS')}])
  };return api;
}
const exported={create,bundle:()=>BUNDLE};global.HydraMxGraphRuntime=exported;if(typeof module==='object'&&module.exports)module.exports=exported;
})(typeof globalThis!=='undefined'?globalThis:this);
""";
        return source.replace("__HYDRA_BUNDLE__", bundle).replace("__HYDRA_ENABLED__", Boolean.toString(enabledByDefault));
    }

    /** Descripción: Ejecuta la operación «drawioPluginSource» correspondiente a la responsabilidad de MxGraphDrawioTechnologyAdapter. Entradas: commandPrefix: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String drawioPluginSource(String commandPrefix) {
        String source = """
(function(global){
'use strict';
if(typeof global.Draw==='undefined'||typeof global.Draw.loadPlugin!=='function')throw new Error('HYDRA draw.io plug-in requires Draw.loadPlugin');
global.Draw.loadPlugin(function(ui){
  const runtime=global.HydraMxGraphRuntime.create({bridge:global.HYDRA_MXGRAPH_HOST_BRIDGE||{}});runtime.install(ui?.editor?.graph);global.HYDRA_DRAWIO_RUNTIME=runtime;
  const prefix='__HYDRA_COMMAND_PREFIX__';
  const status=(text)=>{if(ui?.editor&&typeof ui.editor.setStatus==='function')ui.editor.setStatus(text);};
  const addAction=(suffix,label,fn)=>{const id=prefix+'.'+suffix;if(ui?.actions&&typeof ui.actions.addAction==='function')ui.actions.addAction(id,fn);return {id,label};};
  const actions=[addAction('runtime.enable','HYDRA: Enable runtime',()=>{runtime.setEnabled(true);status('HYDRA enabled');}),addAction('runtime.disable','HYDRA: Disable runtime',()=>{runtime.setEnabled(false);status('HYDRA disabled');}),addAction('runtime.toggle','HYDRA: Toggle runtime',()=>{status(runtime.toggle()?'HYDRA enabled':'HYDRA disabled');}),addAction('validate','HYDRA: Validate model',()=>{try{Promise.resolve(runtime.validate(ui?.editor?.graph?.getModel?.()||null)).then(result=>status('HYDRA validation completed: '+(Array.isArray(result)?result.length:0)+' diagnostic(s)')).catch(error=>status('HYDRA validation failed: '+error.message));}catch(error){status('HYDRA validation unavailable: '+error.message);}})];
  runtime.diagramRepresentations().forEach(representation=>addAction('representation.'+String(representation.id).replace(/[^A-Za-z0-9_.-]/g,'-'),'HYDRA: '+representation.name,()=>{runtime.selectRepresentation(representation.id);status('HYDRA representation: '+representation.name);}));
  runtime.tools().filter(tool=>!['CREATE_NODE','CREATE_EDGE'].includes(String(tool.kind||tool.type||'').toUpperCase())).forEach(tool=>{
    const kind=String(tool.kind||tool.type||'').toUpperCase();
    addAction('tool.'+String(tool.id||tool.name||kind).replace(/[^A-Za-z0-9_.-]/g,'-'),'HYDRA: '+(tool.label||tool.name||kind),()=>{
      try{
        const work=kind==='VALIDATE'?runtime.validate(ui?.editor?.graph?.getModel?.()||null):runtime.invokeTool(tool.id,{});
        Promise.resolve(work).then(()=>status('HYDRA tool completed: '+(tool.label||tool.name||kind))).catch(error=>status('HYDRA tool failed: '+error.message));
      }catch(error){status('HYDRA tool unavailable: '+error.message);}
    });
  });
  if(ui?.menus&&typeof ui.menus.put==='function'){ui.menus.put(prefix,function(menu,parent){actions.forEach(action=>{if(typeof menu?.addItem==='function')menu.addItem(action.label,null,()=>ui.actions?.get?.(action.id)?.funct?.(),parent);});});}
  if(ui?.toolbar&&typeof ui.toolbar.addItem==='function')ui.toolbar.addItem('HYDRA',prefix+'.runtime.toggle');
  const sidebar=ui?.sidebar;if(sidebar&&typeof sidebar.addPaletteFunctions==='function')runtime.diagramRepresentations().forEach(representation=>{
    runtime.selectRepresentation(representation.id);const fns=[];runtime.paletteEntries(representation.id).forEach(entry=>{
      if(entry.kind==='NODE'&&typeof sidebar.createVertexTemplateEntry==='function')fns.push(sidebar.createVertexTemplateEntry(entry.style,entry.width,entry.height,entry.tool.label,entry.tool.label,null,null));
      else if(entry.kind==='EDGE'&&typeof sidebar.createEdgeTemplateEntry==='function')fns.push(sidebar.createEdgeTemplateEntry(entry.style,entry.tool.label,entry.tool.label,null));
    });
    sidebar.addPaletteFunctions('hydra-'+String(representation.id).replace(/[^A-Za-z0-9_.-]/g,'-'),'HYDRA — '+representation.name,false,fns);
  });
  const first=runtime.diagramRepresentations()[0];if(first)runtime.selectRepresentation(first.id);status('HYDRA draw.io plug-in loaded (runtime disabled by default unless profile opted in)');
});
})(typeof globalThis!=='undefined'?globalThis:this);
""";
        return source.replace("__HYDRA_COMMAND_PREFIX__", commandPrefix.replace("\\", "\\\\").replace("'", "\\'"));
    }

    /** Descripción: Ejecuta la operación «smokeTest» correspondiente a la responsabilidad de MxGraphDrawioTechnologyAdapter. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private String smokeTest() {
        return """
import {createRequire} from 'node:module';
const require=createRequire(import.meta.url);const factory=require('../src/hydra-mxgraph-runtime.js');
const cells=[];const graph={getDefaultParent:()=>({id:'root'}),insertVertex:(parent,id,value,x,y,w,h,style)=>{const cell={parent,id,value,x,y,w,h,style};cells.push(cell);return cell;},insertEdge:(parent,id,value,source,target,style)=>{const cell={parent,id,value,source,target,style,edge:true};cells.push(cell);return cell;},setCellStyle:(style,targets)=>targets.forEach(cell=>cell.style=style)};
const bridge={validate:()=>[{severity:'INFO'}],applyQuickFix:()=>({ok:true}),runAction:()=>({ok:true}),runOperation:()=>({ok:true}),publishAfterCommit:()=>({ok:true}),executeEventFlow:()=>({ok:true}),gamification:()=>({points:1}),evaluateExpression:(id,subject)=>{if(id==='expr.throw')throw new Error('injected');return id==='expr.vip'&&subject?.vip===true;}};
const runtime=factory.create({graph,bridge});const reps=runtime.diagramRepresentations();if(reps.length===0)throw new Error('No generated DIAGRAM representation');runtime.selectRepresentation(reps[0].id);runtime.setEnabled(true);
const nodeEntry=runtime.paletteEntries().find(entry=>entry.kind==='NODE');if(!nodeEntry)throw new Error('No node palette entry');const node=runtime.createNode(nodeEntry.tool.id,10,20);if(node.hydraRepresentationId!==reps[0].id||!node.hydraClassId)throw new Error('Node provenance was not preserved');
const edgeEntry=runtime.paletteEntries().find(entry=>entry.kind==='EDGE');if(edgeEntry){const source={hydraClassId:edgeEntry.tool.sourceClassIds?.[0]};const target={hydraClassId:edgeEntry.tool.targetClassIds?.[0]};const edge=runtime.createEdge(edgeEntry.tool.id,source,target);if(edge.hydraEdgeMappingId!==edgeEntry.mapping.id)throw new Error('Edge provenance was not preserved');}
const syntheticNode={shape:'RECTANGLE',styles:{fillColor:'#eeeeee'},conditionalStyles:[{id:'b',enabled:true,priority:20,conditionExpressionId:'expr.vip',styles:{fillColor:'#00ff00',shape:'ELLIPSE'}},{id:'a',enabled:true,priority:10,conditionExpressionId:'expr.vip',styles:{fillColor:'#ff0000'}},{id:'z',enabled:true,priority:30,conditionExpressionId:'expr.throw',styles:{strokeColor:'#000000'}}]};
const conditionalNodeStyle=runtime.resolveNodeStyle(syntheticNode,{vip:true});if(!conditionalNodeStyle.includes('shape=ellipse')||!conditionalNodeStyle.includes('fillColor=#00ff00'))throw new Error('Conditional node styling did not execute deterministically');if(conditionalNodeStyle.includes('strokeColor=#000000'))throw new Error('Failed conditional expression must fall back safely');
const hostileNode=JSON.parse('{"shape":"RECTANGLE","styles":{"Constructor":"x","__proto__":"x","fillColor":"#ffffff"}}');const hostileStyle=runtime.resolveNodeStyle(hostileNode,{});if(hostileStyle.includes('Constructor=')||hostileStyle.includes('__proto__='))throw new Error('Unsafe base style key escaped sanitization');const boundedRuntime=factory.create({graph,bridge,maximumStyleProperties:2});const boundedStyle=boundedRuntime.resolveNodeStyle({shape:'RECTANGLE',styles:{a:'1',b:'2',c:'3'}},{});if(boundedStyle.includes('c=3'))throw new Error('Style property limit was not enforced');
const syntheticEdge={lineType:'STRAIGHT',styles:{strokeColor:'#111111'},conditionalStyles:[{id:'route',enabled:true,priority:1,conditionExpressionId:'expr.vip',styles:{routeStyle:'BEZIER',strokeColor:'#cc0000',borderStyle:'DASHED'}}]};
const conditionalEdgeStyle=runtime.resolveEdgeStyle(syntheticEdge,{vip:true});if(!conditionalEdgeStyle.includes('curved=1')||!conditionalEdgeStyle.includes('strokeColor=#cc0000')||!conditionalEdgeStyle.includes('dashed=1'))throw new Error('Conditional edge styling did not execute');
const diagnostics=await runtime.validate({});if(!Array.isArray(diagnostics)||diagnostics.length!==1)throw new Error('Validation bridge did not execute');if(runtime.forms().length===0)throw new Error('Forms were not preserved');console.log('HYDRA_MXGRAPH_SMOKE=PASS reps='+reps.length+' cells='+cells.length+' forms='+runtime.forms().length+' conditional=PASS');
""";
    }

    /** Descripción: Ejecuta una prueba de humo del plug-in generado para diagrams.net/draw.io contra un host compatible simulado. Entradas: no requiere parámetros de entrada. Salida: devuelve el script ejecutable de verificación. */ private String drawioSmokeTest() {
        return """
import {createRequire} from 'node:module';
const require=createRequire(import.meta.url);const actions=new Map();let palettes=0,toolbar=0,loaded=false;
const bridge={evaluateExpression:(id,subject)=>{if(id==='expr.throw')throw new Error('injected');return id==='expr.vip'&&subject?.vip===true;}};
globalThis.HYDRA_MXGRAPH_HOST_BRIDGE=bridge;
globalThis.Draw={loadPlugin(callback){loaded=true;const graph={getModel:()=>({}),getDefaultParent:()=>({}),insertVertex:()=>({}),insertEdge:()=>({})};const ui={editor:{graph,setStatus:()=>{}},actions:{addAction:(id,fn)=>actions.set(id,{funct:fn}),get:id=>actions.get(id)},menus:{put:()=>{}},toolbar:{addItem:()=>{toolbar+=1;}},sidebar:{createVertexTemplateEntry:()=>()=>{},createEdgeTemplateEntry:()=>()=>{},addPaletteFunctions:(id,name,expanded,entries)=>{if(!Array.isArray(entries))throw new Error('Invalid palette '+id);palettes+=1;}}};callback(ui);}};
require('../drawio/hydra-drawio-plugin.js');
if(!loaded)throw new Error('draw.io plug-in did not load');if(!globalThis.HYDRA_DRAWIO_RUNTIME)throw new Error('HYDRA_DRAWIO_RUNTIME not exposed');if(actions.size<4)throw new Error('Expected draw.io actions');if(palettes<1)throw new Error('Expected draw.io palette');if(toolbar<1)throw new Error('Expected draw.io toolbar item');
const runtime=globalThis.HYDRA_DRAWIO_RUNTIME;
const syntheticNode={shape:'RECTANGLE',styles:{fillColor:'#eeeeee'},conditionalStyles:[{id:'b',enabled:true,priority:20,conditionExpressionId:'expr.vip',styles:{fillColor:'#00ff00',shape:'ELLIPSE'}},{id:'a',enabled:true,priority:10,conditionExpressionId:'expr.vip',styles:{fillColor:'#ff0000'}},{id:'z',enabled:true,priority:30,conditionExpressionId:'expr.throw',styles:{strokeColor:'#000000'}}]};
const conditionalNodeStyle=runtime.resolveNodeStyle(syntheticNode,{vip:true});if(!conditionalNodeStyle.includes('shape=ellipse')||!conditionalNodeStyle.includes('fillColor=#00ff00'))throw new Error('Conditional node styling did not execute deterministically');if(conditionalNodeStyle.includes('strokeColor=#000000'))throw new Error('Failed conditional expression must fall back safely');
const hostileNode=JSON.parse('{"shape":"RECTANGLE","styles":{"Constructor":"x","__proto__":"x","fillColor":"#ffffff"}}');const hostileStyle=runtime.resolveNodeStyle(hostileNode,{});if(hostileStyle.includes('Constructor=')||hostileStyle.includes('__proto__='))throw new Error('Unsafe base style key escaped sanitization');
const boundedRuntime=globalThis.HydraMxGraphRuntime.create({bridge,maximumStyleProperties:2});const boundedStyle=boundedRuntime.resolveNodeStyle({shape:'RECTANGLE',styles:{a:'1',b:'2',c:'3'}},{});if(boundedStyle.includes('c=3'))throw new Error('Style property limit was not enforced');
const syntheticEdge={lineType:'STRAIGHT',styles:{strokeColor:'#111111'},conditionalStyles:[{id:'route',enabled:true,priority:1,conditionExpressionId:'expr.vip',styles:{routeStyle:'BEZIER',strokeColor:'#cc0000',borderStyle:'DASHED'}}]};
const conditionalEdgeStyle=runtime.resolveEdgeStyle(syntheticEdge,{vip:true});if(!conditionalEdgeStyle.includes('curved=1')||!conditionalEdgeStyle.includes('strokeColor=#cc0000')||!conditionalEdgeStyle.includes('dashed=1'))throw new Error('Conditional edge styling did not execute');
console.log('HYDRA_DRAWIO_PLUGIN_SMOKE=PASS actions='+actions.size+' palettes='+palettes+' conditional=PASS');
""";
    }

    /** Descripción: Obtiene la información correspondiente a «readme» dentro de MxGraphDrawioTechnologyAdapter. Entradas: metamodel: dato de entrada necesario para la operación; editor: dato de entrada necesario para la operación; packageName: dato de entrada necesario para la operación; commandPrefix: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String readme(MetaModel metamodel, EditorDefinitionModel editor, String packageName, String commandPrefix) {
        return "# " + packageName + "\n\nGenerated by HYDRA for metamodel **" + metamodel.name() + "** (`" + metamodel.id() + "`) and editor **"
                + editor.name() + "**.\n\n## mxGraph-compatible host\n\nLoad `src/hydra-mxgraph-runtime.js`, create the runtime with `HydraMxGraphRuntime.create({ graph, bridge })`, and enable it only for the matching metamodel. The runtime preserves representation-specific node/edge mappings, palette tools, forms and HYDRA provenance on generated cells.\n\n"
                + "## diagrams.net / draw.io\n\nLoad `drawio/hydra-drawio-plugin.js` as a diagrams.net plug-in. It registers safe runtime actions under `" + commandPrefix + "`, exposes one palette per DIAGRAM representation, and can also use the generated `drawio/hydra-palette*.xml` custom libraries.\n\n"
                + "## Behaviour layers\n\nValidation, quick fixes, operations, events, EventFlow and gamification remain canonical HYDRA documents. For safety the generated JavaScript never uses `eval` or `new Function`; executable behaviour is delegated through `HYDRA_MXGRAPH_HOST_BRIDGE`.\n\n"
                + "## Assets\n\nFor PROJECT_IMAGE mappings provide `assetResolver(path)` or `HYDRA_MXGRAPH_HOST_BRIDGE.resolveAsset(path)`. Conditional styles are evaluated through the synchronous `evaluateExpression(expressionId, subject, variables)` host bridge and fall back to the base style on errors or unavailable evaluators. Data URIs are wrapped safely so the semicolon separator used by mxGraph styles cannot corrupt an embedded image.\n";
    }
}
