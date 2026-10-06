package com.example.editor.definition.importing;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import com.example.editor.definition.*;
import com.example.editor.metamodel.core.MetaAttribute;
import com.example.editor.metamodel.core.MetaClass;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.core.MetaRelation;
import com.example.editor.metamodel.importing.support.SecureXmlSupport;
import com.example.editor.validation.support.CanonicalMetamodelIndex;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Static, non-executing import of Sirius Desktop, Sirius Web and GMF visual specifications.
 * Unsupported semantics are preserved explicitly instead of being silently discarded.
 */
@Service
public class VisualDefinitionImportService {
    private static final long MAX_FILE_BYTES = 20L * 1024 * 1024;
    private static final long MAX_ARCHIVE_BYTES = 60L * 1024 * 1024;
    private static final int MAX_ARCHIVE_ENTRIES = 1200;
    private static final Pattern JAVA_PUBLIC_METHOD = Pattern.compile("(?m)\\bpublic\\s+(?:static\\s+)?(?:<[^>]+>\\s*)?[A-Za-z_$][\\w$<>, ?\\[\\].]*\\s+([A-Za-z_$][\\w$]*)\\s*\\(([^)]*)\\)");
    private final ObjectMapper mapper;
    private final VisualDefinitionImportAnalyzer analyzer;
    private final DrawioVisualImportSupport drawio;
    private final VisualImportMetamodelResolver metamodelResolver;

    /** Descripción: Inicializa una instancia de VisualDefinitionImportService con sus dependencias y datos de entrada. Entradas: mapper: componente de conversión utilizado durante el procesamiento; analyzer: dato de entrada necesario para la operación; drawio: dato de entrada necesario para la operación; metamodelResolver: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public VisualDefinitionImportService(ObjectMapper mapper,
            VisualDefinitionImportAnalyzer analyzer, DrawioVisualImportSupport drawio, VisualImportMetamodelResolver metamodelResolver) {
        this.mapper = mapper; this.analyzer = analyzer; this.drawio = drawio; this.metamodelResolver = metamodelResolver;
    }

    /** Descripción: Ejecuta la operación «importSirius» correspondiente a la responsabilidad de VisualDefinitionImportService. Entradas: metamodelId: identificador que delimita el recurso objetivo; odesign: dato de entrada necesario para la operación; javaSources: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public VisualDefinitionImportResult importSirius(String metamodelId, MultipartFile odesign, List<MultipartFile> javaSources) {
        return importSirius(metamodelId, odesign, javaSources, "EXISTING", List.of());
    }

    /** Descripción: Ejecuta la operación «importSirius» correspondiente a la responsabilidad de VisualDefinitionImportService. Entradas: metamodelId: identificador que delimita el recurso objetivo; odesign: dato de entrada necesario para la operación; javaSources: dato de entrada necesario para la operación; associationMode: dato de entrada necesario para la operación; metamodelResources: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public VisualDefinitionImportResult importSirius(String metamodelId, MultipartFile odesign, List<MultipartFile> javaSources,
            String associationMode, List<MultipartFile> metamodelResources) {
        VisualImportMetamodelResolver.Resolution resolution = metamodelResolver.resolve(associationMode, metamodelId, List.of(odesign), metamodelResources);
        MetaModel metamodel = resolution.metamodel();
        byte[] xml = requireFile(odesign, ".odesign", "Sirius .odesign");
        VisualDefinitionImportDraft state = new VisualDefinitionImportDraft("SIRIUS_DESKTOP", metamodel);
        state.association = resolution.association();
        state.artifacts.add(safeName(odesign));
        recoverXmlVisuals(SecureXmlSupport.parse(xml, "Sirius .odesign"), safeName(odesign), state, true, false);
        recoverJavaSources(javaSources, state);
        return result(state);
    }

    /** Descripción: Ejecuta la operación «importSiriusWeb» correspondiente a la responsabilidad de VisualDefinitionImportService. Entradas: metamodelId: identificador que delimita el recurso objetivo; resources: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public VisualDefinitionImportResult importSiriusWeb(String metamodelId, List<MultipartFile> resources) {
        return importSiriusWeb(metamodelId, resources, "EXISTING", List.of());
    }

    /** Descripción: Ejecuta la operación «importSiriusWeb» correspondiente a la responsabilidad de VisualDefinitionImportService. Entradas: metamodelId: identificador que delimita el recurso objetivo; resources: dato de entrada necesario para la operación; associationMode: dato de entrada necesario para la operación; metamodelResources: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public VisualDefinitionImportResult importSiriusWeb(String metamodelId, List<MultipartFile> resources, String associationMode,
            List<MultipartFile> metamodelResources) {
        VisualImportMetamodelResolver.Resolution resolution = metamodelResolver.resolve(associationMode, metamodelId, resources, metamodelResources);
        MetaModel metamodel = resolution.metamodel();
        VisualDefinitionImportDraft state = new VisualDefinitionImportDraft("SIRIUS_WEB", metamodel); state.association = resolution.association();
        Map<String,byte[]> files = flatten(resources, Set.of(".json", ".zip", ".java"));
        files.forEach((name, bytes) -> {
            state.artifacts.add(name);
            if(name.toLowerCase(Locale.ROOT).endsWith(".java")) recoverJava(name, bytes, state);
            else if(name.toLowerCase(Locale.ROOT).endsWith(".json")) recoverSiriusWebJson(name, bytes, state);
        });
        if(state.representations.isEmpty()) state.diag("WARNING", "NO_REPRESENTATION", "", "No Sirius Web representation description could be identified in the supplied JSON/ZIP resources.");
        return result(state);
    }

    /** Descripción: Ejecuta la operación «importGmf» correspondiente a la responsabilidad de VisualDefinitionImportService. Entradas: metamodelId: identificador que delimita el recurso objetivo; resources: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public VisualDefinitionImportResult importGmf(String metamodelId, List<MultipartFile> resources) {
        return importGmf(metamodelId, resources, "EXISTING", List.of());
    }

    /** Descripción: Ejecuta la operación «importGmf» correspondiente a la responsabilidad de VisualDefinitionImportService. Entradas: metamodelId: identificador que delimita el recurso objetivo; resources: dato de entrada necesario para la operación; associationMode: dato de entrada necesario para la operación; metamodelResources: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public VisualDefinitionImportResult importGmf(String metamodelId, List<MultipartFile> resources, String associationMode,
            List<MultipartFile> metamodelResources) {
        VisualImportMetamodelResolver.Resolution resolution = metamodelResolver.resolve(associationMode, metamodelId, resources, metamodelResources);
        MetaModel metamodel = resolution.metamodel();
        VisualDefinitionImportDraft state = new VisualDefinitionImportDraft("GMF", metamodel); state.association = resolution.association();
        Map<String,byte[]> files = flatten(resources, Set.of(".gmfgraph", ".gmftool", ".gmfmap", ".gmfgen", ".ecore", ".genmodel", ".xml", ".ocl", ".java", ".png", ".gif", ".svg"));
        files.forEach((name, bytes) -> {
            state.artifacts.add(name);
            String lower = name.toLowerCase(Locale.ROOT);
            if(lower.endsWith(".java")) recoverJava(name, bytes, state);
            else if(lower.endsWith(".ocl")) state.validations.add(name + ": " + compact(bytes));
            else if(lower.endsWith(".gmfgraph") || lower.endsWith(".gmftool") || lower.endsWith(".gmfmap") || lower.endsWith(".gmfgen")) recoverXmlVisuals(SecureXmlSupport.parse(bytes, "GMF model " + name), name, state, false, true);
        });
        if(state.representations.isEmpty()) state.representations.add(new RepresentationDefinition(id("gmf", "diagram"), "Recovered GMF diagram", RepresentationKind.DIAGRAM,
                rootClasses(metamodel), List.copyOf(state.nodeMappings), List.copyOf(state.edgeMappings), List.of(), true, Map.of("hydra.import.technology", "GMF")));
        return result(state);
    }

    /** Descripción: Ejecuta la operación «importDrawio» correspondiente a la responsabilidad de VisualDefinitionImportService. Entradas: metamodelId: identificador que delimita el recurso objetivo; resources: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public VisualDefinitionImportResult importDrawio(String metamodelId, List<MultipartFile> resources) {
        return importDrawio(metamodelId, resources, "EXISTING", List.of());
    }

    /** Descripción: Ejecuta la operación «importDrawio» correspondiente a la responsabilidad de VisualDefinitionImportService. Entradas: metamodelId: identificador que delimita el recurso objetivo; resources: dato de entrada necesario para la operación; associationMode: dato de entrada necesario para la operación; metamodelResources: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public VisualDefinitionImportResult importDrawio(String metamodelId, List<MultipartFile> resources, String associationMode,
            List<MultipartFile> metamodelResources) {
        VisualImportMetamodelResolver.Resolution resolution = metamodelResolver.resolve(associationMode, metamodelId, resources, metamodelResources);
        MetaModel metamodel = resolution.metamodel();
        VisualDefinitionImportDraft state = new VisualDefinitionImportDraft("DRAWIO", metamodel); state.association = resolution.association();
        Map<String,byte[]> files = flatten(resources, Set.of(".drawio", ".drawio.xml", ".xml", ".mxlibrary", ".zip"));
        drawio.recover(files, state);
        if(state.representations.isEmpty()) state.diag("WARNING", "NO_DRAWIO_REPRESENTATION", "", "No draw.io page or library item could be mapped safely to the associated metamodel.");
        return result(state);
    }

    /** Descripción: Ejecuta la operación «recoverXmlVisuals» correspondiente a la responsabilidad de VisualDefinitionImportService. Entradas: document: dato de entrada necesario para la operación; source: dato de entrada necesario para la operación; state: dato de entrada necesario para la operación; sirius: dato de entrada necesario para la operación; gmf: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void recoverXmlVisuals(Document document, String source, VisualDefinitionImportDraft state, boolean sirius, boolean gmf) {
        NodeList all = document.getElementsByTagName("*");
        Map<String,List<EditorTool>> toolsByGroup = new LinkedHashMap<>();
        Map<Element,String> representationElements = new IdentityHashMap<>();
        Map<String,List<NodeMapping>> nodesByRepresentation = new LinkedHashMap<>();
        Map<String,List<EdgeMapping>> edgesByRepresentation = new LinkedHashMap<>();
        Map<String,List<String>> toolsByRepresentation = new LinkedHashMap<>();
        Map<Element,LayerAccumulator> layerElements = new IdentityHashMap<>();
        List<RawFilter> rawFilters = new ArrayList<>();
        for(int i=0;i<all.getLength();i++) {
            Element e = all.item(i) instanceof Element value ? value : null; if(e == null) continue; state.elementInspected();
            String type = semanticType(e); String local = local(e).toLowerCase(Locale.ROOT); String name = first(e, "name", "label", "title", "id");
            if(isRepresentation(type, local, sirius, gmf)) {
                RepresentationKind kind = representationKind(type + " " + local);
                String rootReference = first(e, "domainClass", "domainType", "semanticCandidatesExpression", "domainMetaElement");
                String rootClassId = analyzer.resolveClass(state, rootReference, source, "xml[" + i + "]/representation-root", false);
                List<String> roots = rootClassId.isBlank() ? rootClasses(state.metamodel) : List.of(rootClassId);
                String representationId = id(source, "representation", Integer.toString(i));
                state.representations.add(new RepresentationDefinition(representationId, name.isBlank()?stem(source)+" "+kind.name().toLowerCase(Locale.ROOT):name,
                        kind, roots, List.of(), List.of(), List.of(), true, annotations("source", source, "type", type)));
                representationElements.put(e, representationId);
            }
            if(sirius && isLayer(type, local)) {
                String representationId = nearestRepresentation(e, representationElements);
                if(!representationId.isBlank()) {
                    boolean defaultLayer = local.equals("defaultlayer") || type.contains("defaultlayer");
                    String layerName = name.isBlank() ? (defaultLayer ? "Default" : "Imported layer") : name;
                    boolean activeByDefault = defaultLayer || booleanAttribute(e, "activeByDefault", false);
                    layerElements.put(e, new LayerAccumulator(id(source,"layer",Integer.toString(i)), layerName, defaultLayer,
                            activeByDefault, !defaultLayer, representationId, source, type, i));
                }
            }
            if(sirius && isCompositeFilter(type, local)) {
                String representationId = nearestRepresentation(e, representationElements);
                if(!representationId.isBlank()) rawFilters.add(new RawFilter(id(source,"filter",Integer.toString(i)),
                        name.isBlank()?"Imported filter":name, representationId, booleanAttribute(e,"activeByDefault",false),
                        mappingReferenceNames(e), descendantFirst(e,"semanticConditionExpression","conditionExpression","predicateExpression"), source, type));
            }
            if(isNodeMapping(type, local)) {
                String classId = analyzer.resolveClass(state, first(e, "domainClass", "domainType", "domainMetaElement", "semanticCandidatesExpression", "typeName"), source, "xml[" + i + "]/node-mapping", true);
                if(classId.isBlank()) { state.preserved.add(source+"#"+i+" "+type); state.diag("INFO","UNRESOLVED_NODE_MAPPING",source,"Preserved a visual node mapping whose semantic class is expression-based or unresolved: "+name); }
                else {
                    Element visualStyle = visualStyle(e);
                    Map<String,String> mappingAnnotations = new LinkedHashMap<>(annotations("source",source,"externalType",type,"externalName",name));
                    if(visualStyle != null) mappingAnnotations.put("hydra.import.externalStyleType", semanticType(visualStyle));
                    String precondition = first(e, "preconditionExpression", "conditionExpression");
                    if(!precondition.isBlank()) { mappingAnnotations.put("hydra.import.preconditionExpression", precondition); mappingAnnotations.put("hydra.import.precondition", precondition); }
                    String labelExpression = visualStyle == null ? first(e,"labelExpression") : first(visualStyle,"labelExpression");
                    if(!labelExpression.isBlank()) mappingAnnotations.put("hydra.import.labelExpression", labelExpression);
                    String workspacePath = visualStyle == null ? "" : first(visualStyle,"workspacePath");
                    String imageAssetPath = workspacePath.isBlank() ? "" : importedAssetPath(workspacePath);
                    if(!workspacePath.isBlank()) mappingAnnotations.put("hydra.import.originalWorkspacePath", workspacePath);
                    List<String> conditional = directChildren(e,"conditionnalStyles","conditionalStyles").stream().map(this::compactElement).toList();
                    if(!conditional.isEmpty()) mappingAnnotations.put("hydra.import.conditionalStyles", String.join("\n",conditional));
                    List<String> containmentIds = compatibleContainments(state.metamodel,classId);
                    boolean externalContainer = type.contains("container");
                    boolean nativeContainer = externalContainer && !containmentIds.isEmpty();
                    if(externalContainer && !nativeContainer) mappingAnnotations.put("hydra.import.externalContainerReviewRequired","true");
                    NodeMapping recoveredNode = new NodeMapping(id(source,"node",Integer.toString(i)), classId, name.isBlank()?className(state.metamodel,classId):name,
                            shape(e), importedNodeDimension(e, 220, 80, "width"), importedNodeDimension(e, 140, 50, "height"), 80, 50, "", imageAssetPath, EditorImageFit.CONTAIN,
                            nativeContainer, nativeContainer ? containmentIds : List.of(), 18, styles(e), List.of(), mappingAnnotations);
                    state.nodeMappings.add(recoveredNode);
                    addScoped(nodesByRepresentation, nearestRepresentation(e, representationElements), recoveredNode);
                    LayerAccumulator layer = nearestLayer(e, layerElements);
                    if(layer != null) layer.nodeMappingIds.add(recoveredNode.id());
                }
            }
            if(isEdgeMapping(type, local)) {
                EdgeMapping recoveredEdge = recoverSemanticObjectEdge(e, source, i, name, type, state);
                if(recoveredEdge == null) {
                    MetaRelation relation = analyzer.resolveRelation(state, first(e, "relation", "domainMetaElement", "featureName", "targetFinderExpression", "semanticCandidatesExpression"), source, "xml[" + i + "]/edge-mapping", true);
                    if(relation == null) { state.preserved.add(source+"#"+i+" "+type); state.diag("INFO","UNRESOLVED_EDGE_MAPPING",source,"Preserved an edge mapping whose relation is expression-based or unresolved: "+name); }
                    else recoveredEdge = new EdgeMapping(id(source,"edge",Integer.toString(i)), relation.id(), relation.sourceTypeId(), relation.targetTypeId(), edgeLineType(e), "", styles(e), List.of(), annotations("source",source,"externalType",type,"externalName",name));
                }
                if(recoveredEdge != null) {
                    state.edgeMappings.add(recoveredEdge);
                    addScoped(edgesByRepresentation, nearestRepresentation(e, representationElements), recoveredEdge);
                    LayerAccumulator layer = nearestLayer(e, layerElements);
                    if(layer != null) layer.edgeMappingIds.add(recoveredEdge.id());
                }
            }
            if(isTool(type, local)) {
                String group = ancestorName(e, "section"); if(group.isBlank()) group = ancestorName(e,"group"); if(group.isBlank()) group = "Imported tools";
                String representationId = nearestRepresentation(e, representationElements);
                EditorTool tool = scopeTool(nativeTool(e, source, i, state), representationId);
                if(tool != null) {
                    toolsByGroup.computeIfAbsent(group, ignored->new ArrayList<>()).add(tool);
                    if(!representationId.isBlank()) toolsByRepresentation.computeIfAbsent(representationId, ignored->new ArrayList<>()).add(tool.id());
                    LayerAccumulator layer = nearestLayer(e, layerElements);
                    if(layer != null) layer.toolIds.add(tool.id());
                } else state.preserved.add(source+"#tool:"+(name.isBlank()?type:name));
            }
            if(type.contains("validation") || local.contains("validation") || type.contains("audit")) {
                state.validations.add(describeElement(source,e,type));
                VisualImportedValidation validation = recoverValidation(source, e, type, representationElements, state);
                if(validation != null) state.validationDetails.add(validation);
            }
            if(isExpressionCarrier(e)) collectExpressions(source,e,state);
            if(type.contains("conditionalstyle") || local.contains("conditionalstyle")) state.preserved.add("Conditional style: "+describeElement(source,e,type));
            if(type.contains("layer") || type.contains("filter") || type.contains("decoration")) state.preserved.add(describeElement(source,e,type));
        }
        addPaletteGroups(state, toolsByGroup);
        attachScopedMappings(state, nodesByRepresentation, edgesByRepresentation, toolsByRepresentation);
        attachLayersAndFilters(state, layerElements, rawFilters);
        attachMappings(state);
    }

    /** Descripción: Ejecuta la operación «recoverSiriusWebJson» correspondiente a la responsabilidad de VisualDefinitionImportService. Entradas: source: dato de entrada necesario para la operación; content: valor de entrada que debe procesarse; state: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void recoverSiriusWebJson(String source, byte[] content, VisualDefinitionImportDraft state) {
        try { walkJson(mapper.readTree(content), source, "$", state); }
        catch(tools.jackson.core.JacksonException exception) { state.diag("WARNING","INVALID_SIRIUS_WEB_JSON",source,"Could not parse Sirius Web JSON: "+exception.getMessage()); }
        attachMappings(state);
    }

    /** Descripción: Ejecuta la operación «walkJson» correspondiente a la responsabilidad de VisualDefinitionImportService. Entradas: node: dato de entrada necesario para la operación; source: dato de entrada necesario para la operación; path: ruta o ubicación utilizada por la operación; state: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void walkJson(JsonNode node, String source, String path, VisualDefinitionImportDraft state) {
        if(node == null) return;
        if(node.isObject()) {
            state.elementInspected();
            String type = jsonText(node,"type","@type","kind","__typename").toLowerCase(Locale.ROOT);
            String name = jsonText(node,"name","label","title","id");
            if(type.contains("diagramdescription") || type.contains("treedescription") || type.contains("tabledescription") || type.contains("formdescription") || type.contains("matrixdescription")) {
                RepresentationKind kind = representationKind(type);
                String rootRef = jsonText(node,"domainType","domainClass","semanticCandidatesExpression");
                String rootClassId = analyzer.resolveClass(state, rootRef, source, path+"/representation-root", false);
                List<String> roots = rootClassId.isBlank()?rootClasses(state.metamodel):List.of(rootClassId);
                state.representations.add(new RepresentationDefinition(id(source,"web-representation",path), name.isBlank()?"Recovered Sirius Web "+kind.name().toLowerCase(Locale.ROOT):name, kind, roots, List.of(), List.of(), List.of(), true, annotations("source",source,"externalType",type)));
            }
            if(type.contains("nodedescription") || type.contains("nodemapping") || type.contains("containerdescription")) {
                String classId = analyzer.resolveClass(state, jsonText(node,"domainType","domainClass","semanticCandidatesExpression"), source, path+"/node-mapping", true);
                if(!classId.isBlank()) state.nodeMappings.add(new NodeMapping(id(source,"web-node",path),classId,name.isBlank()?className(state.metamodel,classId):name,EditorNodeShape.ROUNDED_RECTANGLE,220,140,80,50,"","",EditorImageFit.CONTAIN,type.contains("container"),List.of(),18,jsonStyles(node),List.of(),annotations("source",source,"externalType",type)));
            }
            if(type.contains("edgedescription") || type.contains("edgemapping")) {
                MetaRelation relation=analyzer.resolveRelation(state,jsonText(node,"relation","featureName","domainType","targetFinderExpression"),source,path+"/edge-mapping",true);
                if(relation!=null) state.edgeMappings.add(new EdgeMapping(id(source,"web-edge",path), relation.id(), relation.sourceTypeId(), relation.targetTypeId(), EditorEdgeLineType.SMOOTH_STEP, "", jsonStyles(node), List.of(), annotations("source",source,"externalType",type)));
            }
            if(type.contains("tool") || type.contains("validation") || type.contains("conditionalstyle") || type.contains("filter") || type.contains("decoration") || type.contains("layer")) state.preserved.add(source+path+" "+(name.isBlank()?type:name));
            collectJsonExpressions(node, source+path, state);
            node.properties().forEach(entry -> walkJson(entry.getValue(),source,path+"/"+entry.getKey(),state));
        } else if(node.isArray()) for(int i=0;i<node.size();i++) walkJson(node.get(i),source,path+"["+i+"]",state);
    }

    /** Recupera el patrón Sirius/GMF donde la arista es un objeto semántico con referencias source y target. */
    /** Descripción: Ejecuta la operación «recoverSemanticObjectEdge» correspondiente a la responsabilidad de LayerAccumulator. Entradas: e: dato de entrada necesario para la operación; source: dato de entrada necesario para la operación; index: dato de entrada necesario para la operación; name: dato de entrada necesario para la operación; type: dato de entrada necesario para la operación; state: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private EdgeMapping recoverSemanticObjectEdge(Element e,String source,int index,String name,String type,VisualDefinitionImportDraft state) {
        String domainRef=first(e,"domainClass","domainType","domainMetaElement");
        if(domainRef.isBlank()) return null;
        String edgeClassId=analyzer.resolveClass(state,domainRef,source,"xml["+index+"]/semantic-edge-class",false);
        if(edgeClassId.isBlank()) return null;
        String sourceFinder=first(e,"sourceFinderExpression","sourceExpression","sourceFeature");
        String targetFinder=first(e,"targetFinderExpression","targetExpression","targetFeature");
        MetaRelation sourceRelation=ownedEndpointRelation(state,edgeClassId,sourceFinder,"source");
        MetaRelation targetRelation=ownedEndpointRelation(state,edgeClassId,targetFinder,"target");
        if(sourceRelation==null||targetRelation==null) return null;
        Map<String,String> annotations=new LinkedHashMap<>(annotations("source",source,"externalType",type,"externalName",name));
        annotations.put("hydra.import.semanticEdgeObject","true");
        annotations.put("hydra.import.originalDomainClass",domainRef);
        if(!sourceFinder.isBlank()) annotations.put("hydra.import.sourceFinderExpression",sourceFinder);
        if(!targetFinder.isBlank()) annotations.put("hydra.import.targetFinderExpression",targetFinder);
        Element style=visualStyle(e); String labelExpression=style==null?first(e,"labelExpression"):first(style,"labelExpression");
        if(!labelExpression.isBlank()) annotations.put("hydra.import.labelExpression",labelExpression);
        SemanticEdgeDescriptor semantic=new SemanticEdgeDescriptor(edgeClassId,sourceRelation.id(),targetRelation.id(),"",Map.of(),
                Map.of("hydra.import.technology",state.technology,"hydra.import.source",source));
        return new EdgeMapping(id(source,"edge",Integer.toString(index)),"",sourceRelation.targetTypeId(),targetRelation.targetTypeId(),
                edgeLineType(e),"",styles(e),List.of(),annotations,semantic);
    }

    /** Descripción: Ejecuta la operación «ownedEndpointRelation» correspondiente a la responsabilidad de LayerAccumulator. Entradas: state: dato de entrada necesario para la operación; ownerClassId: identificador que delimita el recurso objetivo; expression: dato de entrada necesario para la operación; fallbackName: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private MetaRelation ownedEndpointRelation(VisualDefinitionImportDraft state,String ownerClassId,String expression,String fallbackName) {
        String resolvedFeature=featureName(expression);
        if(resolvedFeature.isBlank()) resolvedFeature=fallbackName;
        final String feature=resolvedFeature;
        final String relationExpression=expression==null?"":expression;
        List<MetaRelation> exact=state.metamodel.relations().stream().filter(value->value.sourceTypeId().equals(ownerClassId))
                .filter(value->value.name().equalsIgnoreCase(feature)||value.id().equalsIgnoreCase(relationExpression)).toList();
        return exact.size()==1?exact.get(0):null;
    }

    /** Descripción: Ejecuta la operación «featureName» correspondiente a la responsabilidad de LayerAccumulator. Entradas: expression: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String featureName(String expression) {
        if(expression==null) return ""; String value=expression.trim();
        value=value.replaceFirst("(?i)^feature:","").replaceFirst("(?i)^var:","").replaceFirst("(?i)^aql:self\\.","").replaceFirst("(?i)^self\\.","");
        Matcher matcher=Pattern.compile("([A-Za-z_][\\w]*)\\s*$").matcher(value); return matcher.find()?matcher.group(1):"";
    }

    /** Descripción: Ejecuta la operación «edgeLineType» correspondiente a la responsabilidad de LayerAccumulator. Entradas: e: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private EditorEdgeLineType edgeLineType(Element e) {
        String raw=(first(e,"routingStyle","lineStyle")+" "+(visualStyle(e)==null?"":first(visualStyle(e),"routingStyle","lineStyle"))).toLowerCase(Locale.ROOT);
        if(raw.contains("manhattan")||raw.contains("orthogonal")) return EditorEdgeLineType.ORTHOGONAL;
        if(raw.contains("straight")) return EditorEdgeLineType.STRAIGHT;
        if(raw.contains("bezier")) return EditorEdgeLineType.BEZIER;
        if(raw.contains("step")) return EditorEdgeLineType.STEP;
        return EditorEdgeLineType.SMOOTH_STEP;
    }

    /** Descripción: Ejecuta la operación «enrichSemanticEdgeFromTool» correspondiente a la responsabilidad de LayerAccumulator. Entradas: base: dato de entrada necesario para la operación; tool: dato de entrada necesario para la operación; state: dato de entrada necesario para la operación; source: dato de entrada necesario para la operación; index: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private SemanticEdgeDescriptor enrichSemanticEdgeFromTool(SemanticEdgeDescriptor base, Element tool, VisualDefinitionImportDraft state, String source, int index) {
        Element create=findDescendantBySemanticType(tool,"createinstance");
        String referenceName=create==null?"":first(create,"referenceName","containmentFeature","featureName");
        String containmentId="";
        if(!referenceName.isBlank()) {
            List<MetaRelation> candidates=state.metamodel.relations().stream().filter(value->value.kind().name().equals("CONTAINMENT"))
                    .filter(value->value.targetTypeId().equals(base.edgeClassId()))
                    .filter(value->value.name().equalsIgnoreCase(referenceName)).toList();
            if(candidates.size()==1) containmentId=candidates.get(0).id();
            else state.diag("INFO","SEMANTIC_EDGE_CONTAINMENT_REVIEW",source,"Could not uniquely map semantic edge containment reference '"+referenceName+"'. The creation tool remains preserved without inventing a container relation.");
        }
        Map<String,Object> defaults=recoverToolDefaultAttributes(tool,base,state);
        Map<String,String> annotations=new LinkedHashMap<>(); annotations.put("hydra.import.toolIndex",Integer.toString(index));
        if(!referenceName.isBlank()) annotations.put("hydra.import.originalContainmentFeature",referenceName);
        return base.withContainmentAndDefaults(containmentId,defaults,annotations);
    }

    /** Descripción: Ejecuta la operación «recoverToolDefaultAttributes» correspondiente a la responsabilidad de LayerAccumulator. Entradas: tool: dato de entrada necesario para la operación; semantic: dato de entrada necesario para la operación; state: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Map<String,Object> recoverToolDefaultAttributes(Element tool, SemanticEdgeDescriptor semantic, VisualDefinitionImportDraft state) {
        Map<String,Object> result=new LinkedHashMap<>(); NodeList all=tool.getElementsByTagName("*");
        CanonicalMetamodelIndex metamodelIndex = new CanonicalMetamodelIndex(state.metamodel);
        for(int i=0;i<all.getLength();i++) if(all.item(i) instanceof Element child && semanticType(child).contains("setvalue")) {
            String feature=first(child,"featureName","feature"); if(feature.isBlank()||feature.equalsIgnoreCase("source")||feature.equalsIgnoreCase("target")) continue;
            String raw=first(child,"valueExpression","value"); Object literal=literalValue(raw); if(literal==UnsupportedValue.INSTANCE) continue;
            List<MetaAttribute> candidates = state.metamodel.classes().stream()
                    .filter(owner -> metamodelIndex.isSameOrSubtype(semantic.edgeClassId(), owner.id()))
                    .flatMap(owner -> owner.attributes().stream())
                    .filter(attribute -> attribute.name().equalsIgnoreCase(feature))
                    .toList();
            if(candidates.size()==1) result.put(candidates.get(0).id(), literal);
            else if(candidates.size()>1) state.diag("INFO","SEMANTIC_EDGE_DEFAULT_ATTRIBUTE_REVIEW",first(tool,"name","label"),
                    "Could not uniquely resolve inherited semantic-edge feature '"+feature+"'; the literal default remains preserved without inventing a target attribute.");
        }
        return result;
    }

    /** Descripción: Ejecuta la operación «literalValue» correspondiente a la responsabilidad de LayerAccumulator. Entradas: expression: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Object literalValue(String expression) {
        if(expression==null)return UnsupportedValue.INSTANCE; String value=expression.trim().replaceFirst("(?i)^aql:","");
        if((value.startsWith("'")&&value.endsWith("'"))||(value.startsWith("\"")&&value.endsWith("\""))) return value.substring(1,value.length()-1);
        if("true".equalsIgnoreCase(value)||"false".equalsIgnoreCase(value)) return Boolean.valueOf(value);
        try { if(value.matches("[-+]?\\d+")) return Integer.valueOf(value); if(value.matches("[-+]?\\d+\\.\\d+")) return new java.math.BigDecimal(value); } catch(NumberFormatException ignored) { /* Non-literal numeric text remains external instead of being coerced. */ }
        return UnsupportedValue.INSTANCE;
    }

    /** Descripción: Obtiene la información correspondiente a «findDescendantBySemanticType» dentro de LayerAccumulator. Entradas: parent: dato de entrada necesario para la operación; fragment: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Element findDescendantBySemanticType(Element parent,String fragment){NodeList all=parent.getElementsByTagName("*");for(int i=0;i<all.getLength();i++)if(all.item(i) instanceof Element child&&semanticType(child).contains(fragment))return child;return null;}
    /** Descripción: Actualiza el estado gestionado por LayerAccumulator mediante la operación «replaceEdgeMapping». Entradas: state: dato de entrada necesario para la operación; previous: dato de entrada necesario para la operación; replacement: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void replaceEdgeMapping(VisualDefinitionImportDraft state,EdgeMapping previous,EdgeMapping replacement){for(int i=0;i<state.edgeMappings.size();i++)if(state.edgeMappings.get(i).id().equals(previous.id())){state.edgeMappings.set(i,replacement);return;}}
    private enum UnsupportedValue { INSTANCE }

    /** Recupera herramientas declarativas sin ejecutar operaciones externas. */ /** Descripción: Ejecuta la operación «nativeTool» correspondiente a la responsabilidad de LayerAccumulator. Entradas: e: dato de entrada necesario para la operación; source: dato de entrada necesario para la operación; index: dato de entrada necesario para la operación; state: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private EditorTool nativeTool(Element e, String source, int index, VisualDefinitionImportDraft state) {
        String type=semanticType(e); String label=first(e,"name","label","title","id"); if(label.isBlank()) label="Imported tool";
        if(type.contains("edgecreation") || type.contains("connectiontool") || type.contains("linktool")) {
            String mappingRef=first(e,"edgeMappings","mapping"); String mappingName=referenceName(mappingRef);
            EdgeMapping mapping=state.edgeMappings.stream().filter(value -> mappingName.equals(value.annotations().getOrDefault("hydra.import.externalName",""))||value.id().equals(mappingRef)).findFirst().orElse(null);
            if(mapping==null) return null;
            if(mapping.semanticObjectBacked()) {
                SemanticEdgeDescriptor enriched=enrichSemanticEdgeFromTool(mapping.semanticEdge(),e,state,source,index);
                replaceEdgeMapping(state,mapping,new EdgeMapping(mapping.id(),mapping.relationId(),mapping.sourceClassId(),mapping.targetClassId(),mapping.lineType(),mapping.labelExpressionId(),mapping.styles(),mapping.conditionalStyles(),mapping.annotations(),enriched));
                return new EdgeCreationTool(id(source,"tool",Integer.toString(index)),label,"Recovered semantic-edge creation from "+source,"",List.of(),"","",mapping.id(),"",List.of(mapping.sourceClassId()),List.of(mapping.targetClassId()),false,enriched);
            }
            MetaRelation relation=state.metamodel.relations().stream().filter(value->value.id().equals(mapping.relationId())).findFirst().orElse(null);
            if(relation==null)return null;
            return new EdgeCreationTool(id(source,"tool",Integer.toString(index)),label,"Recovered statically from "+source,"",List.of(),"","",mapping.id(),relation.id(),List.of(relation.sourceTypeId()),List.of(relation.targetTypeId()),true);
        }
        if(type.contains("creation") || type.contains("creationtool") || type.contains("nodetool")) {
            String classReference=first(e,"domainClass","domainType","domainMetaElement","typeName");
            if(classReference.isBlank()) classReference=descendantFirst(e,"typeName","domainClass","domainType");
            String classId=analyzer.resolveClass(state,classReference,source,"tool["+index+"]/class",true); if(classId.isBlank())return null;
            MetaClass metaClass=state.metamodel.classes().stream().filter(value->value.id().equals(classId)).findFirst().orElse(null);
            if(metaClass!=null && metaClass.kind()==com.example.editor.metamodel.core.MetaClassKind.ABSTRACT_CLASS) { state.diag("INFO","ABSTRACT_CREATION_TOOL_SKIPPED",source,"A recovered creation tool targets abstract class '"+metaClass.name()+"' and was intentionally kept out of the executable palette."); return null; }
            return new NodeCreationTool(id(source,"tool",Integer.toString(index)),label,"Recovered statically from "+source,"",List.of(),"","",classId,Map.of(),"");
        }
        return null;
    }
    /** Descripción: Ejecuta la operación «descendantFirst» correspondiente a la responsabilidad de LayerAccumulator. Entradas: parent: dato de entrada necesario para la operación; attributes: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String descendantFirst(Element parent,String...attributes){NodeList all=parent.getElementsByTagName("*");for(int i=0;i<all.getLength();i++)if(all.item(i) instanceof Element child){String value=first(child,attributes);if(!value.isBlank())return value;}return "";}
    /** Descripción: Ejecuta la operación «referenceName» correspondiente a la responsabilidad de LayerAccumulator. Entradas: reference: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String referenceName(String reference){if(reference==null||reference.isBlank())return "";Matcher matcher=Pattern.compile("@(?:nodeMappings|edgeMappings|containerMappings)\\[name=['\"]([^'\"]+)['\"]\\]").matcher(reference);String found="";while(matcher.find())found=matcher.group(1);return found;}

    /** Descripción: Ejecuta la operación «addScoped» correspondiente a la responsabilidad de LayerAccumulator. Entradas: target: dato de entrada necesario para la operación; representationId: identificador que delimita el recurso objetivo; value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private <T> void addScoped(Map<String,List<T>> target,String representationId,T value){if(representationId!=null&&!representationId.isBlank())target.computeIfAbsent(representationId,ignored->new ArrayList<>()).add(value);}

    /** Mantiene cada mapping y herramienta en la representación externa que realmente lo contiene. */
    /** Descripción: Ejecuta la operación «attachScopedMappings» correspondiente a la responsabilidad de LayerAccumulator. Entradas: state: dato de entrada necesario para la operación; nodes: dato de entrada necesario para la operación; edges: dato de entrada necesario para la operación; tools: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void attachScopedMappings(VisualDefinitionImportDraft state,Map<String,List<NodeMapping>> nodes,Map<String,List<EdgeMapping>> edges,Map<String,List<String>> tools){
        for(int index=0;index<state.representations.size();index++){
            RepresentationDefinition rep=state.representations.get(index);
            List<NodeMapping> scopedNodes=nodes.getOrDefault(rep.id(),List.of());
            List<EdgeMapping> scopedEdges=edges.getOrDefault(rep.id(),List.of()).stream().map(original->state.edgeMappings.stream().filter(current->current.id().equals(original.id())).findFirst().orElse(original)).toList();
            List<String> scopedTools=tools.getOrDefault(rep.id(),List.of());
            if(scopedNodes.isEmpty()&&scopedEdges.isEmpty()&&scopedTools.isEmpty())continue;
            state.representations.set(index,new RepresentationDefinition(rep.id(),rep.name(),rep.kind(),rep.rootClassIds(),scopedNodes,scopedEdges,scopedTools,rep.layers(),rep.filters(),rep.editable(),rep.annotations()));
        }
    }

    /** Asocia las capas y filtros externos a la representación correcta sin activar semántica externa no traducida. */
    /** Descripción: Ejecuta la operación «attachLayersAndFilters» correspondiente a la responsabilidad de LayerAccumulator. Entradas: state: dato de entrada necesario para la operación; layerElements: dato de entrada necesario para la operación; rawFilters: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void attachLayersAndFilters(VisualDefinitionImportDraft state, Map<Element,LayerAccumulator> layerElements, List<RawFilter> rawFilters) {
        Map<String,List<RepresentationLayerDefinition>> layersByRepresentation = new LinkedHashMap<>();
        for(LayerAccumulator accumulator : layerElements.values().stream().sorted(java.util.Comparator.comparingInt(value -> value.order)).toList()) {
            Map<String,String> annotations = annotations("source",accumulator.source,"externalType",accumulator.externalType);
            layersByRepresentation.computeIfAbsent(accumulator.representationId, ignored -> new ArrayList<>()).add(
                    new RepresentationLayerDefinition(accumulator.id, accumulator.name, accumulator.defaultLayer,
                            accumulator.activeByDefault, accumulator.userToggleable, List.copyOf(accumulator.nodeMappingIds),
                            List.copyOf(accumulator.edgeMappingIds), List.copyOf(accumulator.toolIds), annotations));
        }
        Map<String,List<RepresentationFilterDefinition>> filtersByRepresentation = new LinkedHashMap<>();
        for(RawFilter raw : rawFilters) {
            RepresentationDefinition representation = state.representations.stream().filter(value -> value.id().equals(raw.representationId)).findFirst().orElse(null);
            if(representation == null) continue;
            List<String> mappingIds = new ArrayList<>();
            for(String externalName : raw.mappingExternalNames) {
                representation.nodeMappings().stream().filter(value -> externalName.equals(value.annotations().get("hydra.import.externalName"))).map(NodeMapping::id).forEach(mappingIds::add);
                representation.edgeMappings().stream().filter(value -> externalName.equals(value.annotations().get("hydra.import.externalName"))).map(EdgeMapping::id).forEach(mappingIds::add);
            }
            Map<String,String> filterAnnotations = new LinkedHashMap<>(annotations("source",raw.source,"externalType",raw.externalType));
            if(!raw.externalCondition.isBlank()) {
                filterAnnotations.put("hydra.import.conditionLanguage", raw.externalCondition.trim().toLowerCase(Locale.ROOT).startsWith("aql:") ? "AQL" : "EXTERNAL");
                filterAnnotations.put("hydra.import.reviewRequired", "true");
            }
            filtersByRepresentation.computeIfAbsent(raw.representationId, ignored -> new ArrayList<>()).add(
                    new RepresentationFilterDefinition(raw.id, raw.name, raw.activeByDefault, mappingIds, "", raw.externalCondition, filterAnnotations));
        }
        for(int index=0; index<state.representations.size(); index++) {
            RepresentationDefinition rep = state.representations.get(index);
            List<RepresentationLayerDefinition> layers = layersByRepresentation.getOrDefault(rep.id(), List.of());
            List<RepresentationFilterDefinition> filters = filtersByRepresentation.getOrDefault(rep.id(), List.of());
            if(layers.isEmpty() && filters.isEmpty()) continue;
            state.representations.set(index, new RepresentationDefinition(rep.id(),rep.name(),rep.kind(),rep.rootClassIds(),
                    rep.nodeMappings(),rep.edgeMappings(),rep.toolIds(),layers,filters,rep.editable(),rep.annotations()));
        }
    }

    /** Descripción: Evalúa la condición «isLayer» sin modificar el estado externo. Entradas: type: dato de entrada necesario para la operación; local: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean isLayer(String type,String local) { return local.equals("defaultlayer") || local.equals("additionallayers") || type.contains("defaultlayer") || type.contains("additionallayer"); }
    /** Descripción: Evalúa la condición «isCompositeFilter» sin modificar el estado externo. Entradas: type: dato de entrada necesario para la operación; local: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean isCompositeFilter(String type,String local) { return type.contains("compositefilterdescription") || local.contains("compositefilter"); }
    /** Descripción: Ejecuta la operación «booleanAttribute» correspondiente a la responsabilidad de LayerAccumulator. Entradas: element: dato de entrada necesario para la operación; name: dato de entrada necesario para la operación; fallback: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean booleanAttribute(Element element,String name,boolean fallback) { String value=element.getAttribute(name); return value==null||value.isBlank()?fallback:Boolean.parseBoolean(value); }
    /** Descripción: Ejecuta la operación «nearestLayer» correspondiente a la responsabilidad de LayerAccumulator. Entradas: element: dato de entrada necesario para la operación; layers: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private LayerAccumulator nearestLayer(Element element,Map<Element,LayerAccumulator> layers) { org.w3c.dom.Node current=element; while(current instanceof Element value){LayerAccumulator found=layers.get(value);if(found!=null)return found;current=value.getParentNode();}return null; }
    /** Descripción: Ejecuta la operación «mappingReferenceNames» correspondiente a la responsabilidad de LayerAccumulator. Entradas: filter: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<String> mappingReferenceNames(Element filter) {
        Set<String> result = new LinkedHashSet<>();
        NodeList descendants = filter.getElementsByTagName("*");
        for(int i=0;i<descendants.getLength();i++) if(descendants.item(i) instanceof Element child) collectMappingReferenceNames(first(child,"mappings","mapping"),result);
        collectMappingReferenceNames(first(filter,"mappings","mapping"),result);
        return List.copyOf(result);
    }
    /** Descripción: Ejecuta la operación «collectMappingReferenceNames» correspondiente a la responsabilidad de LayerAccumulator. Entradas: reference: dato de entrada necesario para la operación; target: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void collectMappingReferenceNames(String reference,Set<String> target) {
        if(reference==null||reference.isBlank())return;
        Matcher matcher=Pattern.compile("@(?:nodeMappings|edgeMappings|containerMappings)\\[name=['\"]([^'\"]+)['\"]\\]").matcher(reference);
        while(matcher.find())target.add(matcher.group(1));
    }

    /** Limita una herramienta recuperada a su representación de origen. */
    /** Descripción: Ejecuta la operación «scopeTool» correspondiente a la responsabilidad de LayerAccumulator. Entradas: tool: dato de entrada necesario para la operación; representationId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private EditorTool scopeTool(EditorTool tool,String representationId){
        if(tool==null||representationId==null||representationId.isBlank())return tool;
        List<String> scope=List.of(representationId);
        if(tool instanceof NodeCreationTool node)return new NodeCreationTool(node.id(),node.label(),node.description(),node.icon(),scope,node.visibleExpressionId(),node.enabledExpressionId(),node.targetClassId(),node.defaultValues(),node.formId());
        if(tool instanceof EdgeCreationTool edge)return new EdgeCreationTool(edge.id(),edge.label(),edge.description(),edge.icon(),scope,edge.visibleExpressionId(),edge.enabledExpressionId(),edge.edgeMappingId(),edge.relationId(),edge.sourceClassIds(),edge.targetClassIds(),edge.reconnectable(),edge.semanticEdge());
        return tool;
    }

    /** Descripción: Ejecuta la operación «attachMappings» correspondiente a la responsabilidad de VisualDefinitionImportService. Entradas: state: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void attachMappings(VisualDefinitionImportDraft state) {
        if(state.representations.isEmpty() && (!state.nodeMappings.isEmpty() || !state.edgeMappings.isEmpty())) state.representations.add(new RepresentationDefinition(id(state.technology,"diagram"),"Recovered visual representation",RepresentationKind.DIAGRAM,rootClasses(state.metamodel),List.of(),List.of(),List.of(),true,Map.of()));
        if(state.representations.isEmpty()) return;
        RepresentationDefinition first=state.representations.get(0);
        if(first.nodeMappings().isEmpty() && first.edgeMappings().isEmpty()) state.representations.set(0,new RepresentationDefinition(first.id(),first.name(),first.kind(),first.rootClassIds(),List.copyOf(state.nodeMappings),List.copyOf(state.edgeMappings),first.toolIds(),first.layers(),first.filters(),first.editable(),first.annotations()));
    }

    /** Descripción: Ejecuta la operación «addPaletteGroups» correspondiente a la responsabilidad de VisualDefinitionImportService. Entradas: state: dato de entrada necesario para la operación; grouped: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void addPaletteGroups(VisualDefinitionImportDraft state, Map<String,List<EditorTool>> grouped) {
        int order=state.paletteGroups.size();
        for(var entry:grouped.entrySet()) { List<String> ids=entry.getValue().stream().map(EditorTool::id).toList(); state.tools.addAll(entry.getValue()); state.paletteGroups.add(new PaletteGroup(id(state.technology,"group",entry.getKey()),entry.getKey(),order++,ids,false)); }
    }

    /** Descripción: Ejecuta la operación «recoverJavaSources» correspondiente a la responsabilidad de VisualDefinitionImportService. Entradas: files: archivo o contenido que debe procesarse; state: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void recoverJavaSources(List<MultipartFile> files, VisualDefinitionImportDraft state) {
        if(files==null) return;
        for(MultipartFile file:files) {
            if(file==null||file.isEmpty()) continue;
            String source=safeName(file);
            state.artifacts.add(source);
            recoverJava(source,requireFile(file,".java","Java source"),state);
        }
    }
    /** Descripción: Ejecuta la operación «recoverJava» correspondiente a la responsabilidad de VisualDefinitionImportService. Entradas: source: dato de entrada necesario para la operación; bytes: dato de entrada necesario para la operación; state: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void recoverJava(String source, byte[] bytes, VisualDefinitionImportDraft state) {
        String text=new String(bytes,StandardCharsets.UTF_8); Matcher matcher=JAVA_PUBLIC_METHOD.matcher(text); int count=0;
        while(matcher.find()) { String name=matcher.group(1); if(!name.equals("main")){state.javaServices.add(source+" :: "+name+"("+matcher.group(2).replaceAll("\\s+"," ").trim()+")");count++;} }
        if(count==0) state.diag("INFO","NO_PUBLIC_JAVA_SERVICE",source,"No public Java service methods were found; the source remains preserved as import evidence.");
    }

    /** Descripción: Ejecuta la operación «result» correspondiente a la responsabilidad de VisualDefinitionImportService. Entradas: state: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private VisualDefinitionImportResult result(VisualDefinitionImportDraft state) {
        attachMappings(state);
        analyzer.validateRecoveredDraft(state);
        Map<String,String> annotations=new LinkedHashMap<>(); annotations.put("hydra.import.technology",state.technology); annotations.put("hydra.import.sources",String.join(", ",state.artifacts));
        putSummary(annotations,"hydra.import.javaServices",state.javaServices); putSummary(annotations,"hydra.import.validations",state.validations); putSummary(annotations,"hydra.import.expressions",state.expressions); putSummary(annotations,"hydra.import.preserved",state.preserved);
        Set<EditorCapability> capabilities=new LinkedHashSet<>(Set.of(EditorCapability.MODEL_EDITING,EditorCapability.MULTIPLE_REPRESENTATIONS,EditorCapability.IMPORT,EditorCapability.EXPORT)); if(!state.validations.isEmpty())capabilities.add(EditorCapability.VALIDATION_RUNTIME);
        EditorRuntimePolicy runtimePolicy = state.validations.isEmpty() ? EditorRuntimePolicy.defaults()
                : new EditorRuntimePolicy(Set.of(EditorValidationTrigger.MANUAL, EditorValidationTrigger.ON_OPEN,
                        EditorValidationTrigger.ON_CHANGE, EditorValidationTrigger.ON_REPRESENTATION_CHANGE),
                        true, true, true, false, false, 2, 30_000L);
        EditorDefinitionModel editor=new EditorDefinitionModel("1.0",id(state.metamodel.id(),state.technology.toLowerCase(Locale.ROOT),"editor"),state.metamodel.id(),state.metamodel.name()+" "+state.technology.replace('_',' ')+" editor",deduplicateRepresentations(state.representations),new ToolPaletteDefinition(state.paletteGroups,state.tools),List.of(),new EditorCapabilityManifest(capabilities),runtimePolicy,annotations);
        Map<String,Integer> counts=Map.of("representations",editor.representations().size(),"nodeMappings",editor.representations().stream().mapToInt(value->value.nodeMappings().size()).sum(),"edgeMappings",editor.representations().stream().mapToInt(value->value.edgeMappings().size()).sum(),"tools",editor.palette().tools().size(),"javaServices",state.javaServices.size(),"validations",state.validations.size(),"expressions",state.expressions.size());
        return new VisualDefinitionImportResult(state.technology,editor,state.analysis(!editor.representations().isEmpty()),state.diagnostics,state.fixes,counts,state.javaServices,state.validations,state.validationDetails,state.expressions,state.preserved,state.metamodel,state.association);
    }

    /** Descripción: Ejecuta la operación «deduplicateRepresentations» correspondiente a la responsabilidad de VisualDefinitionImportService. Entradas: values: colección de elementos que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private List<RepresentationDefinition> deduplicateRepresentations(List<RepresentationDefinition> values){Map<String,RepresentationDefinition> result=new LinkedHashMap<>();for(RepresentationDefinition value:values)result.putIfAbsent(value.id(),value);return List.copyOf(result.values());}
    /** Descripción: Ejecuta la operación «flatten» correspondiente a la responsabilidad de VisualDefinitionImportService. Entradas: resources: dato de entrada necesario para la operación; allowed: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Map<String,byte[]> flatten(List<MultipartFile> resources, Set<String> allowed) {
        if(resources==null||resources.isEmpty())throw new IllegalArgumentException("At least one visual-definition file is required"); Map<String,byte[]> result=new LinkedHashMap<>();
        for(MultipartFile file:resources){if(file==null||file.isEmpty())continue;String name=safeName(file);byte[] bytes=requireBytes(file);String lower=name.toLowerCase(Locale.ROOT);if(lower.endsWith(".zip"))readZip(bytes,result,allowed);else if(allowed.stream().anyMatch(lower::endsWith))result.put(name,bytes);else throw new IllegalArgumentException("Unsupported visual-definition file: "+name);}return result;
    }
    /** Descripción: Obtiene la información correspondiente a «readZip» dentro de VisualDefinitionImportService. Entradas: bytes: dato de entrada necesario para la operación; target: dato de entrada necesario para la operación; allowed: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void readZip(byte[] bytes, Map<String,byte[]> target, Set<String> allowed) {
        long total=0;int entries=0;try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(bytes))){ZipEntry entry;while((entry=zip.getNextEntry())!=null){if(entry.isDirectory())continue;if(++entries>MAX_ARCHIVE_ENTRIES)throw new IllegalArgumentException("Visual-definition ZIP contains too many entries");String name=entry.getName().replace('\\','/');if(name.startsWith("/")||name.contains("../"))throw new IllegalArgumentException("Unsafe ZIP entry: "+name);String lower=name.toLowerCase(Locale.ROOT);if(allowed.stream().noneMatch(lower::endsWith))continue;ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int read;long entrySize=0;while((read=zip.read(buffer))!=-1){entrySize+=read;total+=read;if(entrySize>MAX_FILE_BYTES||total>MAX_ARCHIVE_BYTES)throw new IllegalArgumentException("Visual-definition ZIP exceeds safe import limits");out.write(buffer,0,read);}target.put(name,out.toByteArray());}}catch(IOException exception){throw new IllegalArgumentException("Could not read visual-definition ZIP",exception);}
    }
    /** Descripción: Valida las condiciones necesarias para «requireFile» y rechaza entradas no válidas. Entradas: file: archivo o contenido que debe procesarse; extension: dato de entrada necesario para la operación; label: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private byte[] requireFile(MultipartFile file,String extension,String label){if(file==null||file.isEmpty())throw new IllegalArgumentException(label+" is required");String name=safeName(file).toLowerCase(Locale.ROOT);if(!name.endsWith(extension))throw new IllegalArgumentException(label+" must use "+extension);return requireBytes(file);}
    /** Descripción: Valida las condiciones necesarias para «requireBytes» y rechaza entradas no válidas. Entradas: file: archivo o contenido que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private byte[] requireBytes(MultipartFile file){try{if(file.getSize()>MAX_FILE_BYTES)throw new IllegalArgumentException("Import file is larger than 20 MiB: "+safeName(file));return file.getBytes();}catch(IOException exception){throw new IllegalArgumentException("Could not read import file: "+safeName(file),exception);}}
    /** Descripción: Ejecuta la operación «safeName» correspondiente a la responsabilidad de VisualDefinitionImportService. Entradas: file: archivo o contenido que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String safeName(MultipartFile file){String name=file.getOriginalFilename();return name==null||name.isBlank()?"upload":name.replace('\\','/').replaceAll("^.*/","");}

    /** Descripción: Evalúa la condición «isRepresentation» sin modificar el estado externo. Entradas: type: dato de entrada necesario para la operación; local: dato de entrada necesario para la operación; sirius: dato de entrada necesario para la operación; gmf: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean isRepresentation(String type,String local,boolean sirius,boolean gmf){return (sirius&&(type.contains("diagramdescription")||type.contains("treedescription")||type.contains("tabledescription")||type.contains("editiontabledescription")||type.contains("crossTableDescription".toLowerCase(Locale.ROOT))))||(gmf&&(local.contains("canvas")||type.contains("gmfgen::gendigram")||type.contains("gendiagram")));}
    /** Descripción: Evalúa la condición «isNodeMapping» sin modificar el estado externo. Entradas: type: dato de entrada necesario para la operación; local: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean isNodeMapping(String type,String local){return type.contains("nodemapping")||type.contains("containermapping")||type.contains("linemapping")||type.contains("subitemmapping")||type.contains("topnodereference")||type.contains("childreference")||local.equals("nodes")||local.contains("nodemapping")||local.contains("linemapping")||local.contains("subitemmapping");}
    /** Descripción: Evalúa la condición «isEdgeMapping» sin modificar el estado externo. Entradas: type: dato de entrada necesario para la operación; local: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean isEdgeMapping(String type,String local){return type.contains("edgemapping")||type.contains("linkmapping")||type.contains("linkmapping")||local.contains("edgemapping")||local.contains("links");}
    /** Descripción: Evalúa la condición «isTool» sin modificar el estado externo. Entradas: type: dato de entrada necesario para la operación; local: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean isTool(String type,String local){return type.contains("creationdescription")||type.contains("creationtool")||type.contains("toolentry")||type.contains("connectiontool")||type.contains("nodetool")||type.contains("edgetool")||local.contains("tool");}
    /**
     * Recovers one semantic validation rule, never a structural validation container or
     * the nested Sirius {@code <audits>} expression node. Sirius stores the audit
     * expression below {@code SemanticValidationRule}; walking every XML element used
     * to make the parent container and the audit child look like additional rules.
     */
    /** Descripción: Ejecuta la operación «recoverValidation» correspondiente a la responsabilidad de RawFilter. Entradas: source: dato de entrada necesario para la operación; e: dato de entrada necesario para la operación; type: dato de entrada necesario para la operación; representations: dato de entrada necesario para la operación; state: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private VisualImportedValidation recoverValidation(String source, Element e, String type,
            Map<Element,String> representations, VisualDefinitionImportDraft state) {
        String local = local(e).toLowerCase(Locale.ROOT);
        boolean declaredRule = type.contains("validationrule") || local.contains("validationrule")
                || local.equals("ownedrules") || local.equals("rule");
        String directExpression = first(e, "auditExpression", "conditionExpression", "expression", "check", "precondition");
        String rawContext = first(e,"targetClass","domainClass","domainType");

        // Generic formats may put a complete rule on one element. A bare expression
        // carrier is not enough: without rule identity/context it is evidence owned by
        // its parent rule and must not materialize as a duplicate validation.
        boolean selfContainedRule = !directExpression.isBlank() && !rawContext.isBlank()
                && !local.equals("audits") && !local.equals("audit");
        if(!declaredRule && !selfContainedRule) return null;

        String expression = directExpression;
        if(expression.isBlank() && declaredRule)
            expression = descendantFirst(e,"auditExpression","conditionExpression","expression","check","precondition");
        if(expression.isBlank()) return null;

        String name = first(e, "name", "label", "id", "message");
        String message = first(e, "message", "messageExpression", "label", "name");
        String severityRaw = first(e, "severity", "level").toUpperCase(Locale.ROOT);
        String severity = severityRaw.contains("ERROR") ? "ERROR" : severityRaw.contains("INFO") ? "INFO" : "WARNING";
        String representationId = nearestRepresentation(e, representations);
        if(representationId.isBlank() && !state.representations.isEmpty()) representationId = state.representations.get(0).id();
        String contextClassId = analyzer.resolveClass(state, rawContext, source, "validation/targetClass", false);
        return new VisualImportedValidation(source, state.technology, name, representationId, contextClassId, expression,
                message, severity, describeElement(source,e,type));
    }

    /** Descripción: Ejecuta la operación «nearestRepresentation» correspondiente a la responsabilidad de VisualDefinitionImportService. Entradas: e: dato de entrada necesario para la operación; representations: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String nearestRepresentation(Element e, Map<Element,String> representations) {
        org.w3c.dom.Node current = e;
        while(current instanceof Element element) {
            String id = representations.get(element);
            if(id != null) return id;
            current = element.getParentNode();
        }
        return "";
    }

    /** Descripción: Evalúa la condición «isExpressionCarrier» sin modificar el estado externo. Entradas: e: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean isExpressionCarrier(Element e){for(String name:new String[]{"semanticCandidatesExpression","labelExpression","precondition","conditionExpression","targetFinderExpression","valueExpression","browseExpression","initialOperation"})if(e.hasAttribute(name)&&!e.getAttribute(name).isBlank())return true;return false;}
    /** Descripción: Ejecuta la operación «collectExpressions» correspondiente a la responsabilidad de VisualDefinitionImportService. Entradas: source: dato de entrada necesario para la operación; e: dato de entrada necesario para la operación; state: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void collectExpressions(String source,Element e,VisualDefinitionImportDraft state){String context=analyzer.resolveClass(state,first(e,"domainClass","domainType","domainMetaElement","typeName"),source,"expression-context",false);for(String name:new String[]{"semanticCandidatesExpression","labelExpression","precondition","conditionExpression","targetFinderExpression","valueExpression","browseExpression","initialOperation"}){String value=e.getAttribute(name).trim();if(!value.isBlank()){state.expressions.add(source+" :: "+name+" = "+value);analyzer.inspectExpression(state,source,name,value,context);}}}
    /** Descripción: Ejecuta la operación «collectJsonExpressions» correspondiente a la responsabilidad de VisualDefinitionImportService. Entradas: node: dato de entrada necesario para la operación; source: dato de entrada necesario para la operación; state: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void collectJsonExpressions(JsonNode node,String source,VisualDefinitionImportDraft state){String context=analyzer.resolveClass(state,jsonText(node,"domainType","domainClass"),source,"expression-context",false);node.properties().forEach(entry->{String key=entry.getKey().toLowerCase(Locale.ROOT);if(entry.getValue().isTextual()&&(key.contains("expression")||key.contains("condition")||key.contains("precondition"))){String value=entry.getValue().asString();state.expressions.add(source+" :: "+entry.getKey()+" = "+value);analyzer.inspectExpression(state,source,entry.getKey(),value,context);}});}
    /** Recupera propiedades visuales tanto del mapping como del elemento <style> anidado de Sirius/GMF. */ /** Descripción: Ejecuta la operación «styles» correspondiente a la responsabilidad de LayerAccumulator. Entradas: e: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Map<String,String> styles(Element e){
        Map<String,String> result=new LinkedHashMap<>(); collectStyleAttributes(e,result); Element style=visualStyle(e); if(style!=null){collectStyleAttributes(style,result); collectNestedColors(style,result);}
        return result;
    }
    /** Descripción: Ejecuta la operación «collectStyleAttributes» correspondiente a la responsabilidad de LayerAccumulator. Entradas: e: dato de entrada necesario para la operación; result: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void collectStyleAttributes(Element e, Map<String,String> result){for(String key:new String[]{"color","fillColor","backgroundColor","foregroundColor","borderColor","strokeColor","fontColor","labelColor","fontName","fontSize","borderSize","strokeWidth","lineStyle","labelPosition","gradientState","transparency","width","height","cornerRadius","routingStyle","alignment"}){String value=e.getAttribute(key).trim();if(!value.isBlank()){String normalized=normalizeStyleValue(normalizeStyleKey(key),value);if(!normalized.isBlank())result.put(normalizeStyleKey(key),normalized);}}}
    /** Descripción: Ejecuta la operación «collectNestedColors» correspondiente a la responsabilidad de LayerAccumulator. Entradas: style: dato de entrada necesario para la operación; result: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void collectNestedColors(Element style, Map<String,String> result){for(Element child:directChildren(style)){String local=local(child);String key=switch(local){case "color","backgroundColor","foregroundColor"->"fillColor";case "borderColor"->"strokeColor";case "labelColor"->"labelColor";default->"";};if(key.isBlank())continue;String color=siriusColor(first(child,"href","name","value"));if(!color.isBlank())result.put(key,color);}}
    /** Descripción: Ejecuta la operación «normalizeStyleValue» correspondiente a la responsabilidad de LayerAccumulator. Entradas: key: identificador que delimita el recurso objetivo; value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String normalizeStyleValue(String key,String value){if("labelPosition".equals(key)){String v=value.toLowerCase(Locale.ROOT);if(v.contains("border"))return "OUTSIDE_BOTTOM";if(v.contains("node")||v.contains("center"))return "CENTER";}if("opacity".equals(key)){try{double n=Double.parseDouble(value);if(n>1)n=Math.max(.1,Math.min(1,(100-n)/100d));return Double.toString(n);}catch(NumberFormatException ignored){return "";}}return value;}
    /** Descripción: Ejecuta la operación «siriusColor» correspondiente a la responsabilidad de LayerAccumulator. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String siriusColor(String value){String v=value==null?"":value.toLowerCase(Locale.ROOT);Matcher m=Pattern.compile("name=['\"]([^'\"]+)['\"]").matcher(v);if(m.find())v=m.group(1);return switch(v.replace('-','_')){case "black"->"#000000";case "white"->"#FFFFFF";case "gray","grey"->"#808080";case "dark_gray","dark_grey"->"#4B5563";case "light_gray","light_grey"->"#D1D5DB";case "blue"->"#2563EB";case "light_blue"->"#BFDBFE";case "dark_blue"->"#1E3A8A";case "green"->"#16A34A";case "light_green"->"#BBF7D0";case "dark_green"->"#166534";case "red"->"#DC2626";case "light_red"->"#FECACA";case "dark_red"->"#991B1B";case "yellow"->"#FACC15";case "light_yellow"->"#FEF3C7";case "orange"->"#F97316";case "light_orange"->"#FED7AA";case "dark_orange"->"#9A3412";default->v.matches("#[0-9a-f]{6}")?v.toUpperCase(Locale.ROOT):"";};}
    /** Descripción: Ejecuta la operación «jsonStyles» correspondiente a la responsabilidad de VisualDefinitionImportService. Entradas: node: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Map<String,String> jsonStyles(JsonNode node){Map<String,String> result=new LinkedHashMap<>();for(String key:new String[]{"color","fillColor","backgroundColor","borderColor","strokeColor","fontColor","labelColor","fontName","fontSize","borderSize","strokeWidth","lineStyle","labelPosition","transparency","width","height","cornerRadius"}){JsonNode value=node.get(key);if(value!=null&&value.isValueNode())result.put(normalizeStyleKey(key),value.asString());}return result;}
    /** Descripción: Ejecuta la operación «normalizeStyleKey» correspondiente a la responsabilidad de VisualDefinitionImportService. Entradas: key: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private String normalizeStyleKey(String key){return switch(key){case "color","fillColor","backgroundColor"->"fillColor";case "borderColor","strokeColor"->"strokeColor";case "fontColor","labelColor"->"labelColor";case "fontSize"->"labelFontSize";case "borderSize","strokeWidth"->"strokeWidth";case "cornerRadius"->"borderRadius";case "transparency"->"opacity";default->key;};}
    /** Conserva la geometría de los estilos visuales externos cuando existe una forma neutral equivalente. */ /** Descripción: Ejecuta la operación «shape» correspondiente a la responsabilidad de LayerAccumulator. Entradas: e: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private EditorNodeShape shape(Element e){Element style=visualStyle(e);String value=(semanticType(e)+" "+(style==null?"":semanticType(style))+" "+first(e,"shape","figure","style")).toLowerCase(Locale.ROOT);if(value.contains("workspaceimage"))return EditorNodeShape.PROJECT_IMAGE;if(value.contains("ellipse"))return EditorNodeShape.ELLIPSE;if(value.contains("lozenge")||value.contains("diamond"))return EditorNodeShape.DIAMOND;if(value.contains("note"))return EditorNodeShape.DOCUMENT;if(value.contains("gauge"))return EditorNodeShape.ROUNDED_RECTANGLE;if(value.contains("rounded"))return EditorNodeShape.ROUNDED_RECTANGLE;return EditorNodeShape.RECTANGLE;}
    /** Descripción: Ejecuta la operación «number» correspondiente a la responsabilidad de VisualDefinitionImportService. Entradas: e: dato de entrada necesario para la operación; fallback: dato de entrada necesario para la operación; names: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private double number(Element e,double fallback,String...names){for(String name:names){String value=e.getAttribute(name).replaceAll("[^0-9.]","");if(!value.isBlank())try{return Math.max(1,Double.parseDouble(value));}catch(NumberFormatException ignored){return fallback;}}return fallback;}
    /** Descripción: Normaliza dimensiones visuales externas y evita interpretar factores simbólicos de Sirius como píxeles. Entrada: e: elemento visual; fallback: tamaño seguro por defecto; minimum: tamaño mínimo canónico; names: atributos candidatos. Salida: devuelve una dimensión compatible con el runtime neutral. */
    /** Descripción: Ejecuta la operación «importedNodeDimension» correspondiente a la responsabilidad de RawFilter. Entradas: e: dato de entrada necesario para la operación; fallback: dato de entrada necesario para la operación; minimum: dato de entrada necesario para la operación; names: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private double importedNodeDimension(Element e, double fallback, double minimum, String... names) {
        double value = numberFromVisual(e, fallback, names);
        return value < minimum ? fallback : value;
    }

    /** Descripción: Ejecuta la operación «numberFromVisual» correspondiente a la responsabilidad de LayerAccumulator. Entradas: e: dato de entrada necesario para la operación; fallback: dato de entrada necesario para la operación; names: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private double numberFromVisual(Element e,double fallback,String...names){double direct=number(e,-1,names);if(direct>0)return direct;Element style=visualStyle(e);return style==null?fallback:number(style,fallback,names);}

    /** Descripción: Ejecuta la operación «compatibleContainments» correspondiente a la responsabilidad de LayerAccumulator. Entradas: metamodel: dato de entrada necesario para la operación; sourceClassId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private List<String> compatibleContainments(MetaModel metamodel,String sourceClassId){return metamodel.relations().stream().filter(value->value.kind().name().equals("CONTAINMENT")&&value.sourceTypeId().equals(sourceClassId)).map(MetaRelation::id).toList();}
    /** Descripción: Ejecuta la operación «visualStyle» correspondiente a la responsabilidad de LayerAccumulator. Entradas: e: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Element visualStyle(Element e){for(Element child:directChildren(e)){String local=local(child).toLowerCase(Locale.ROOT);if(local.equals("style")||local.endsWith("style"))return child;}return null;}
    /** Descripción: Ejecuta la operación «directChildren» correspondiente a la responsabilidad de LayerAccumulator. Entradas: parent: dato de entrada necesario para la operación; names: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<Element> directChildren(Element parent,String...names){Set<String> wanted=new LinkedHashSet<>();for(String name:names)wanted.add(name.toLowerCase(Locale.ROOT));List<Element> result=new ArrayList<>();for(Element child:directChildren(parent))if(wanted.contains(local(child).toLowerCase(Locale.ROOT)))result.add(child);return result;}
    /** Descripción: Ejecuta la operación «directChildren» correspondiente a la responsabilidad de LayerAccumulator. Entradas: parent: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<Element> directChildren(Element parent){List<Element> result=new ArrayList<>();org.w3c.dom.Node child=parent.getFirstChild();while(child!=null){if(child instanceof Element e)result.add(e);child=child.getNextSibling();}return result;}
    /** Descripción: Ejecuta la operación «compactElement» correspondiente a la responsabilidad de LayerAccumulator. Entradas: e: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String compactElement(Element e){String type=semanticType(e);String predicate=first(e,"predicateExpression","conditionExpression","preconditionExpression");Element style=visualStyle(e);return (type+(predicate.isBlank()?"":" predicate="+predicate)+(style==null?"":" style="+semanticType(style))).trim();}
    /** Descripción: Ejecuta la operación «importedAssetPath» correspondiente a la responsabilidad de LayerAccumulator. Entradas: workspacePath: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String importedAssetPath(String workspacePath){String normalized=workspacePath==null?"":workspacePath.trim().replace('\\','/');while(normalized.startsWith("/"))normalized=normalized.substring(1);while(normalized.startsWith("../"))normalized=normalized.substring(3);return normalized.isBlank()?"":"external-source/"+normalized;}
    /** Descripción: Ejecuta la operación «representationKind» correspondiente a la responsabilidad de VisualDefinitionImportService. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private RepresentationKind representationKind(String value){String v=value.toLowerCase(Locale.ROOT);if(v.contains("tree"))return RepresentationKind.TREE;if(v.contains("matrix")||v.contains("crosstable"))return RepresentationKind.MATRIX;if(v.contains("table"))return RepresentationKind.TABLE;if(v.contains("form"))return RepresentationKind.FORM;if(v.contains("dashboard"))return RepresentationKind.DASHBOARD;return RepresentationKind.DIAGRAM;}
    /** Descripción: Ejecuta la operación «rootClasses» correspondiente a la responsabilidad de VisualDefinitionImportService. Entradas: m: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<String> rootClasses(MetaModel m){return m.classes().stream().filter(value->value.kind().name().equals("CLASS")).limit(1).map(MetaClass::id).toList();}
    /** Descripción: Ejecuta la operación «className» correspondiente a la responsabilidad de VisualDefinitionImportService. Entradas: m: dato de entrada necesario para la operación; id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private String className(MetaModel m,String id){return m.classes().stream().filter(value->value.id().equals(id)).map(MetaClass::name).findFirst().orElse(id);}
    /** Descripción: Ejecuta la operación «semanticType» correspondiente a la responsabilidad de VisualDefinitionImportService. Entradas: e: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String semanticType(Element e){return (local(e)+" "+first(e,"xsi:type","type")).toLowerCase(Locale.ROOT);}
    /** Descripción: Ejecuta la operación «local» correspondiente a la responsabilidad de VisualDefinitionImportService. Entradas: e: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String local(Element e){return e.getLocalName()==null?e.getTagName().replaceFirst("^.*:",""):e.getLocalName();}
    /** Descripción: Ejecuta la operación «first» correspondiente a la responsabilidad de VisualDefinitionImportService. Entradas: e: dato de entrada necesario para la operación; names: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String first(Element e,String...names){for(String name:names){String value=e.getAttribute(name).trim();if(!value.isBlank())return value;}return "";}
    /** Descripción: Ejecuta la operación «ancestorName» correspondiente a la responsabilidad de VisualDefinitionImportService. Entradas: e: dato de entrada necesario para la operación; fragment: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String ancestorName(Element e,String fragment){org.w3c.dom.Node p=e.getParentNode();while(p instanceof Element value){if(local(value).toLowerCase(Locale.ROOT).contains(fragment))return first(value,"name","label","title","id");p=p.getParentNode();}return "";}
    /** Descripción: Ejecuta la operación «describeElement» correspondiente a la responsabilidad de VisualDefinitionImportService. Entradas: source: dato de entrada necesario para la operación; e: dato de entrada necesario para la operación; type: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String describeElement(String source,Element e,String type){return source+" :: "+type+" :: "+first(e,"name","label","id","message","conditionExpression","auditExpression");}
    /** Descripción: Ejecuta la operación «jsonText» correspondiente a la responsabilidad de VisualDefinitionImportService. Entradas: node: dato de entrada necesario para la operación; keys: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String jsonText(JsonNode node,String...keys){for(String key:keys){JsonNode value=node.get(key);if(value!=null&&value.isValueNode()&&!value.asString().isBlank())return value.asString();}return "";}
    /** Descripción: Ejecuta la operación «compact» correspondiente a la responsabilidad de VisualDefinitionImportService. Entradas: bytes: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String compact(byte[] bytes){String value=new String(bytes,StandardCharsets.UTF_8).replaceAll("\\s+"," ").trim();return value.length()>800?value.substring(0,800)+"…":value;}
    /** Descripción: Ejecuta la operación «stem» correspondiente a la responsabilidad de VisualDefinitionImportService. Entradas: name: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String stem(String name){String value=name.replace('\\','/');if(value.contains("/"))value=value.substring(value.lastIndexOf('/')+1);int dot=value.indexOf('.');return dot>0?value.substring(0,dot):value;}
    /** Descripción: Ejecuta la operación «id» correspondiente a la responsabilidad de VisualDefinitionImportService. Entradas: parts: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String id(String...parts){String raw=String.join("-",parts).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._:-]+","-").replaceAll("^-+|-+$","");if(raw.isBlank())raw="imported";if(!Character.isLetter(raw.charAt(0)))raw="i-"+raw;return raw.length()>120?raw.substring(0,120):raw;}
    /** Descripción: Ejecuta la operación «annotations» correspondiente a la responsabilidad de VisualDefinitionImportService. Entradas: values: colección de elementos que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private Map<String,String> annotations(String...values){Map<String,String> result=new LinkedHashMap<>();for(int i=0;i+1<values.length;i+=2)if(values[i+1]!=null&&!values[i+1].isBlank())result.put("hydra.import."+values[i],values[i+1]);return result;}
    /** Descripción: Ejecuta la operación «putSummary» correspondiente a la responsabilidad de VisualDefinitionImportService. Entradas: map: dato de entrada necesario para la operación; key: identificador que delimita el recurso objetivo; values: colección de elementos que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void putSummary(Map<String,String> map,String key,List<String> values){if(values.isEmpty())return;String joined=String.join("\n",values);map.put(key,joined.length()>12000?joined.substring(0,12000)+"\n…":joined);}

    private static final class LayerAccumulator {
        final String id; final String name; final boolean defaultLayer; final boolean activeByDefault; final boolean userToggleable;
        final String representationId; final String source; final String externalType; final int order;
        final List<String> nodeMappingIds=new ArrayList<>(); final List<String> edgeMappingIds=new ArrayList<>(); final List<String> toolIds=new ArrayList<>();
        /** Descripción: Inicializa una instancia de LayerAccumulator con sus dependencias y datos de entrada. Entradas: id: identificador que delimita el recurso objetivo; name: dato de entrada necesario para la operación; defaultLayer: dato de entrada necesario para la operación; activeByDefault: dato de entrada necesario para la operación; userToggleable: dato de entrada necesario para la operación; representationId: identificador que delimita el recurso objetivo; source: dato de entrada necesario para la operación; externalType: dato de entrada necesario para la operación; order: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ LayerAccumulator(String id,String name,boolean defaultLayer,boolean activeByDefault,boolean userToggleable,String representationId,String source,String externalType,int order){
            this.id=id;this.name=name;this.defaultLayer=defaultLayer;this.activeByDefault=activeByDefault;this.userToggleable=userToggleable;this.representationId=representationId;this.source=source;this.externalType=externalType;this.order=order;
        }
    }
    private record RawFilter(String id,String name,String representationId,boolean activeByDefault,List<String> mappingExternalNames,String externalCondition,String source,String externalType) {}

}
