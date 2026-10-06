package com.example.editor.definition.importing;

import java.io.ByteArrayOutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.example.editor.definition.EdgeMapping;
import com.example.editor.definition.EditorEdgeLineType;
import com.example.editor.definition.EditorImageFit;
import com.example.editor.definition.EditorNodeShape;
import com.example.editor.definition.NodeMapping;
import com.example.editor.definition.RepresentationDefinition;
import com.example.editor.definition.RepresentationKind;
import com.example.editor.metamodel.core.MetaClass;
import com.example.editor.metamodel.core.MetaRelation;
import com.example.editor.metamodel.importing.support.SecureXmlSupport;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Static importer for draw.io/diagrams.net mxGraph projects and custom libraries. */
@Component
public class DrawioVisualImportSupport {
    private static final int MAX_INFLATED_DIAGRAM_BYTES = 20 * 1024 * 1024;
    private static final int MAX_LIBRARY_ENTRIES = 2000;
    private static final int MAX_PAGE_CELLS = 20000;
    private final ObjectMapper mapper;
    private final VisualDefinitionImportAnalyzer analyzer;

    /** Descripción: Inicializa una instancia de DrawioVisualImportSupport con sus dependencias y datos de entrada. Entradas: mapper: componente de conversión utilizado durante el procesamiento; analyzer: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public DrawioVisualImportSupport(ObjectMapper mapper, VisualDefinitionImportAnalyzer analyzer) {
        this.mapper = mapper;
        this.analyzer = analyzer;
    }

    /** Descripción: Ejecuta la operación «recover» correspondiente a la responsabilidad de DrawioVisualImportSupport. Entradas: resources: dato de entrada necesario para la operación; draft: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ void recover(Map<String, byte[]> resources, VisualDefinitionImportDraft draft) {
        for(var entry : resources.entrySet()) {
            String source = entry.getKey();
            draft.artifacts.add(source);
            byte[] content = entry.getValue();
            Document document;
            try {
                document = SecureXmlSupport.parse(content, "draw.io resource " + source);
            } catch(RuntimeException exception) {
                draft.structuralProblem();
                draft.diag("ERROR", "INVALID_DRAWIO_XML", source, "", "", "Could not parse the draw.io XML resource.",
                        "The resource is not well-formed XML or violates the hardened XML parser policy.",
                        "Open and resave the file in diagrams.net, then import the resulting .drawio/.xml file.", List.of(), "EXACT");
                continue;
            }
            Element root = document.getDocumentElement();
            String rootName = local(root).toLowerCase(Locale.ROOT);
            if("mxlibrary".equals(rootName)) recoverLibrary(root, source, draft);
            else if("mxfile".equals(rootName)) recoverMxFile(root, source, draft);
            else if("mxgraphmodel".equals(rootName)) recoverGraph(root, source, stem(source), "", draft);
            else {
                draft.structuralProblem();
                draft.diag("ERROR", "UNSUPPORTED_DRAWIO_ROOT", source, "/", rootName,
                        "The XML root is not a draw.io project, graph model or custom library.",
                        "Supported roots are mxfile, mxGraphModel and mxlibrary.",
                        "Export the source from diagrams.net as .drawio/XML or a custom library XML.", List.of(), "EXACT");
            }
        }
    }

    /** Descripción: Ejecuta la operación «recoverMxFile» correspondiente a la responsabilidad de DrawioVisualImportSupport. Entradas: mxfile: archivo o contenido que debe procesarse; source: dato de entrada necesario para la operación; draft: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void recoverMxFile(Element mxfile, String source, VisualDefinitionImportDraft draft) {
        List<Element> diagrams = directChildren(mxfile, "diagram");
        if(diagrams.isEmpty()) {
            draft.structuralProblem();
            draft.diag("ERROR", "DRAWIO_NO_PAGES", source, "/mxfile", "", "The draw.io project contains no diagram pages.",
                    "An mxfile must contain at least one diagram page to recover a visual representation.",
                    "Add or restore a page in diagrams.net and save the project again.", List.of(), "EXACT");
            return;
        }
        Set<String> pageIds = new HashSet<>();
        for(int i=0;i<diagrams.size();i++) {
            Element diagram = diagrams.get(i); draft.elementInspected();
            String pageName = textOr(diagram.getAttribute("name"), "Page " + (i + 1));
            String pageId = diagram.getAttribute("id").trim();
            if(!pageId.isBlank() && !pageIds.add(pageId)) {
                draft.structuralProblem();
                String fixId = fixId("drawio-page-id", source, pageId, Integer.toString(i));
                draft.fix(fixId, "REGENERATE_DUPLICATE_DRAWIO_PAGE_ID", source, pageName, "Generate a unique page identifier",
                        "The draw.io project contains duplicate page id '" + pageId + "'. HYDRA uses the page order and a generated internal id instead.",
                        "SAFE", "EXACT", true, true);
                draft.diag("WARNING", "DUPLICATE_DRAWIO_PAGE_ID", source, "/mxfile/diagram[" + i + "]", pageId,
                        "A duplicate draw.io page id was normalized during import.", "Page ids must be unique inside an mxfile.",
                        "No action is required unless external references depend on the duplicate id.", List.of(fixId), "EXACT");
            }
            Element graph = firstDirectChild(diagram, "mxGraphModel");
            if(graph == null) {
                String encoded = diagram.getTextContent() == null ? "" : diagram.getTextContent().trim();
                if(encoded.isBlank()) {
                    draft.structuralProblem();
                    draft.diag("ERROR", "EMPTY_DRAWIO_PAGE", source, pageName, pageId, "The draw.io page contains no graph model.",
                            "The page has neither an uncompressed mxGraphModel nor compressed diagram data.",
                            "Resave the page in diagrams.net and retry the import.", List.of(), "EXACT");
                    continue;
                }
                try {
                    graph = SecureXmlSupport.parse(decodeCompressedXml(encoded), "compressed draw.io page " + pageName).getDocumentElement();
                    String fixId = fixId("drawio-decompress", source, pageId, Integer.toString(i));
                    draft.fix(fixId, "DECOMPRESS_DRAWIO_PAGE", source, pageName, "Decode the compressed draw.io page",
                            "The diagram payload was decoded using Base64, raw DEFLATE and URL decoding before static analysis.",
                            "SAFE", "EXACT", true, true);
                } catch(RuntimeException exception) {
                    draft.structuralProblem();
                    draft.diag("ERROR", "INVALID_DRAWIO_COMPRESSED_PAGE", source, pageName, pageId,
                            "The compressed draw.io page could not be decoded safely.",
                            "Compressed diagram data must be Base64-encoded raw DEFLATE and stay within HYDRA's decompression limit.",
                            "Open the project in diagrams.net and save it as uncompressed XML, or repair the damaged page.", List.of(), "EXACT");
                    continue;
                }
            }
            recoverGraph(graph, source, pageName, "", draft);
        }
    }

    /** Descripción: Ejecuta la operación «recoverLibrary» correspondiente a la responsabilidad de DrawioVisualImportSupport. Entradas: library: dato de entrada necesario para la operación; source: dato de entrada necesario para la operación; draft: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void recoverLibrary(Element library, String source, VisualDefinitionImportDraft draft) {
        String json = library.getTextContent() == null ? "" : library.getTextContent().trim();
        if(json.isBlank()) {
            draft.structuralProblem();
            draft.diag("ERROR", "EMPTY_DRAWIO_LIBRARY", source, "/mxlibrary", "", "The draw.io custom library is empty.",
                    "A custom library must contain a JSON array of shape/template entries.",
                    "Export the library again from diagrams.net.", List.of(), "EXACT");
            return;
        }
        try {
            JsonNode entries = mapper.readTree(json);
            if(entries == null || !entries.isArray()) throw new IllegalArgumentException("mxlibrary payload is not an array");
            if(entries.size() > MAX_LIBRARY_ENTRIES) throw new IllegalArgumentException("mxlibrary contains too many entries");
            for(int i=0;i<entries.size();i++) recoverLibraryEntry(entries.get(i), source, i, draft);
        } catch(JacksonException | IllegalArgumentException exception) {
            draft.structuralProblem();
            draft.diag("ERROR", "INVALID_DRAWIO_LIBRARY_JSON", source, "/mxlibrary", "", "The draw.io custom library JSON is invalid.",
                    "mxlibrary must contain a bounded JSON array whose entries define xml or image data with dimensions.",
                    "Open the library in diagrams.net and export it again.", List.of(), "EXACT");
        }
    }

    /** Descripción: Ejecuta la operación «recoverLibraryEntry» correspondiente a la responsabilidad de DrawioVisualImportSupport. Entradas: entry: dato de entrada necesario para la operación; source: dato de entrada necesario para la operación; index: dato de entrada necesario para la operación; draft: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void recoverLibraryEntry(JsonNode entry, String source, int index, VisualDefinitionImportDraft draft) {
        draft.elementInspected();
        String location = "/mxlibrary[" + index + "]";
        if(entry == null || !entry.isObject()) {
            draft.structuralProblem(); draft.diag("ERROR", "INVALID_DRAWIO_LIBRARY_ENTRY", source, location, "",
                    "A library entry is not an object.", "Each custom-library entry must describe XML or image data.",
                    "Remove or repair the invalid library entry.", List.of(), "EXACT"); return;
        }
        String title = jsonText(entry, "title", "name", "label");
        JsonNode widthNode = entry.get("w"); JsonNode heightNode = entry.get("h");
        boolean invalidSize = !positiveNumericNode(widthNode) || !positiveNumericNode(heightNode);
        double width = positiveNumber(widthNode, 220);
        double height = positiveNumber(heightNode, 140);
        if(invalidSize) {
            draft.invalidParameter();
            String fixId = fixId("library-size", source, location, title);
            draft.fix(fixId, "DEFAULT_DRAWIO_LIBRARY_SIZE", source, location, "Use safe default library dimensions",
                    "Missing or invalid custom-library dimensions are recovered as 220 × 140 pixels.", "SAFE", "EXACT", true, true);
        }
        JsonNode xmlNode = entry.get("xml");
        if(xmlNode != null && xmlNode.isTextual() && !xmlNode.asText().isBlank()) {
            String xml = xmlNode.asText().trim();
            try {
                byte[] bytes = xml.startsWith("<") ? xml.getBytes(StandardCharsets.UTF_8) : decodeCompressedXml(xml);
                Element graph = SecureXmlSupport.parse(bytes, "draw.io library entry " + location).getDocumentElement();
                if(!"mxgraphmodel".equalsIgnoreCase(local(graph))) throw new IllegalArgumentException("library xml is not mxGraphModel");
                recoverGraph(graph, source, title.isBlank()?"Library item "+(index+1):title, title, draft);
            } catch(RuntimeException exception) {
                draft.structuralProblem();
                draft.diag("ERROR", "INVALID_DRAWIO_LIBRARY_XML", source, location, title,
                        "The custom-library XML template could not be decoded.",
                        "Library xml entries may contain either an uncompressed mxGraphModel or its compressed form.",
                        "Re-export this library item from diagrams.net.", List.of(), "EXACT");
            }
            return;
        }
        JsonNode dataNode = entry.get("data");
        if(dataNode != null && dataNode.isTextual() && !dataNode.asText().isBlank()) {
            recoverImageLibraryEntry(dataNode.asText(), title, width, height, source, location, entry, draft);
            return;
        }
        draft.invalidParameter(); draft.structuralProblem();
        draft.diag("ERROR", "DRAWIO_LIBRARY_ENTRY_WITHOUT_CONTENT", source, location, title,
                "The custom-library entry defines neither xml nor image data.",
                "A draw.io custom-library entry requires an xml template or a data image.",
                "Repair or remove this library item.", List.of(), "EXACT");
    }

    /** Descripción: Ejecuta la operación «recoverImageLibraryEntry» correspondiente a la responsabilidad de DrawioVisualImportSupport. Entradas: data: dato de entrada necesario para la operación; title: dato de entrada necesario para la operación; width: dato de entrada necesario para la operación; height: dato de entrada necesario para la operación; source: dato de entrada necesario para la operación; location: dato de entrada necesario para la operación; entry: dato de entrada necesario para la operación; draft: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void recoverImageLibraryEntry(String data, String title, double width, double height, String source,
            String location, JsonNode entry, VisualDefinitionImportDraft draft) {
        String classId = analyzer.resolveClass(draft, title, source, location + "/title", false);
        if(classId.isBlank()) {
            draft.preserved.add(source + " " + location + " image library entry: " + title);
            return;
        }
        Map<String,String> styles = parseMxStyle(jsonText(entry, "style"));
        EditorNodeShape shape = EditorNodeShape.ROUNDED_RECTANGLE;
        String svgPath = firstSvgPath(data);
        if(!svgPath.isBlank()) { shape = EditorNodeShape.SVG_PATH; styles.put("svgPath", svgPath); }
        else {
            String fixId = fixId("drawio-image-materialize", source, location, title);
            draft.fix(fixId, "MATERIALIZE_DRAWIO_IMAGE_ASSET", source, location, "Materialize the library image as a project asset",
                    "The library item is an embedded raster/vector image. HYDRA preserves its fingerprint, but exact image rendering requires a project-relative asset.",
                    "REVIEW", "HIGH", false, false);
            draft.diag("WARNING", "DRAWIO_IMAGE_ASSET_NOT_MATERIALIZED", source, location, title,
                    "The library image was matched semantically but is not copied into project assets automatically.",
                    "Editor definitions reference project-relative image assets; embedding an arbitrary data URI would bypass the project asset boundary.",
                    "Review and materialize the trusted image into project assets if exact artwork is required.", List.of(fixId), "HIGH");
        }
        Map<String,String> annotations = new LinkedHashMap<>();
        annotations.put("hydra.import.source", source); annotations.put("hydra.import.drawio.location", location);
        annotations.put("hydra.import.drawio.imageSha256", sha256(data.getBytes(StandardCharsets.UTF_8)));
        draft.nodeMappings.add(new NodeMapping(importId(source,"library-node",location), classId,
                title.isBlank()?className(draft,classId):title, shape, Math.max(80,width), Math.max(50,height), 40, 30,
                "", "", EditorImageFit.CONTAIN, false, List.of(), 18, styles, List.of(), annotations));
    }

    /** Descripción: Ejecuta la operación «recoverGraph» correspondiente a la responsabilidad de DrawioVisualImportSupport. Entradas: graph: dato de entrada necesario para la operación; source: dato de entrada necesario para la operación; pageName: dato de entrada necesario para la operación; fallbackClassLabel: dato de entrada necesario para la operación; draft: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void recoverGraph(Element graph, String source, String pageName, String fallbackClassLabel, VisualDefinitionImportDraft draft) {
        Element root = firstDirectChild(graph, "root");
        if(root == null) {
            draft.structuralProblem();
            draft.diag("ERROR", "DRAWIO_GRAPH_WITHOUT_ROOT", source, pageName, "", "The mxGraphModel has no root element.",
                    "mxGraphModel/root is required to contain structural cells and diagram elements.",
                    "Repair or resave this diagram in diagrams.net.", List.of(), "EXACT"); return;
        }
        NodeList all = root.getElementsByTagName("mxCell");
        if(all.getLength() > MAX_PAGE_CELLS) {
            draft.structuralProblem();
            draft.diag("ERROR", "DRAWIO_TOO_MANY_CELLS", source, pageName, Integer.toString(all.getLength()),
                    "The draw.io page exceeds HYDRA's safe cell-count limit.", "Very large graphs can exhaust memory during import analysis.",
                    "Split the diagram into smaller pages before importing.", List.of(), "EXACT"); return;
        }
        List<Cell> cells = new ArrayList<>(); Map<String,Cell> byId = new LinkedHashMap<>(); Set<String> duplicates = new HashSet<>();
        for(int i=0;i<all.getLength();i++) {
            Element cellElement = (Element) all.item(i); draft.elementInspected();
            Cell cell = readCell(cellElement, i, fallbackClassLabel); cells.add(cell);
            if(cell.missingId) {
                draft.invalidParameter();
                String fixId = fixId("drawio-cell-id",source,pageName,Integer.toString(i));
                draft.fix(fixId,"GENERATE_DRAWIO_CELL_ID",source,pageName,"Generate a stable cell identifier",
                        "The imported cell has no id; HYDRA generates an internal import id without modifying the source file.","SAFE","EXACT",true,true);
            }
            if(byId.putIfAbsent(cell.id, cell) != null) duplicates.add(cell.id);
            if(cell.vertex && cell.edge) {
                draft.structuralProblem(); draft.invalidParameter();
                String fixId = fixId("drawio-cell-kind", source, pageName, cell.id);
                draft.fix(fixId, "CHOOSE_DRAWIO_CELL_KIND", source, pageName+"/cell["+cell.id+"]", "Choose whether the cell is a node or connector",
                        "The source marks the same mxCell as both vertex=1 and edge=1. HYDRA preserves it but does not guess which semantic role was intended.",
                        "REVIEW", "LOW", false, false);
                draft.diag("ERROR", "DRAWIO_CELL_VERTEX_EDGE_CONFLICT", source, pageName+"/cell["+cell.id+"]", cell.id,
                        "A draw.io cell is marked as both a vertex and an edge.",
                        "mxCell vertex and edge roles are mutually exclusive for semantic recovery.",
                        "Correct the cell in diagrams.net or choose the intended role before importing it semantically.", List.of(fixId), "EXACT");
            }
        }
        if(!duplicates.isEmpty()) {
            draft.structuralProblem(); draft.ambiguous();
            draft.diag("ERROR", "DUPLICATE_DRAWIO_CELL_ID", source, pageName, String.join(", ",duplicates),
                    "The page contains duplicate mxCell identifiers.", "Edges and parent references cannot be resolved deterministically when cell ids are duplicated.",
                    "Use diagrams.net to regenerate unique cell ids before relying on affected connectors.", List.of(), "EXACT");
        }
        validateStructuralCells(source,pageName,byId,draft);
        validateParents(source,pageName,cells,byId,draft);

        Map<String,String> classByCell = new HashMap<>(); List<NodeMapping> pageNodes = new ArrayList<>();
        for(Cell cell:cells) {
            if(!cell.vertex || cell.edge) continue;
            String rawClass = firstNonBlank(cell.data.get("hydraClassId"),cell.data.get("classId"),cell.data.get("metaclass"),cell.data.get("class"),cell.data.get("type"),cell.label);
            String classId = analyzer.resolveClass(draft, rawClass, source, pageName+"/cell["+cell.id+"]", true);
            if(classId.isBlank()) { draft.preserved.add(source+" :: "+pageName+" :: cell "+cell.id+" :: "+cell.label); continue; }
            classByCell.put(cell.id,classId);
            Geometry geometry = geometry(cell.element, source, pageName, cell.id, draft);
            Map<String,String> styles = parseMxStyle(cell.style);
            EditorNodeShape shape = drawioShape(cell.style);
            Map<String,String> annotations = drawioAnnotations(source,pageName,cell,geometry);
            NodeMapping mapping = new NodeMapping(importId(source,"drawio-node",pageName,cell.id),classId,
                    cell.label.isBlank()?className(draft,classId):stripHtml(cell.label),shape,geometry.width,geometry.height,
                    Math.min(80,Math.max(20,geometry.width)),Math.min(50,Math.max(20,geometry.height)),"","",EditorImageFit.CONTAIN,
                    false,List.of(),18,styles,List.of(),annotations);
            pageNodes.add(mapping); draft.nodeMappings.add(mapping);
        }

        List<EdgeMapping> pageEdges = new ArrayList<>();
        for(Cell cell:cells) {
            if(!cell.edge || cell.vertex) continue;
            String location=pageName+"/cell["+cell.id+"]";
            if(cell.source.isBlank() || cell.target.isBlank()) {
                draft.unresolved();
                String fixId=fixId("drawio-dangling-edge",source,pageName,cell.id);
                draft.fix(fixId,"CONNECT_DRAWIO_EDGE",source,location,"Connect the visual edge to semantic elements",
                        "The connector has no source/target cell ids. Free-floating mxPoint edges are valid visually but cannot define a metamodel relation automatically.",
                        "MANUAL","EXACT",false,false);
                draft.diag("WARNING","DRAWIO_EDGE_WITHOUT_SEMANTIC_ENDPOINTS",source,location,cell.id,
                        "A free-floating draw.io connector was preserved without a semantic edge mapping.",
                        "HYDRA requires source and target semantic elements to map a connector to a metamodel relation.",
                        "Connect it to mapped cells in diagrams.net or map the relation manually.",List.of(fixId),"EXACT");
                draft.preserved.add(source+" :: "+pageName+" :: free edge "+cell.id); continue;
            }
            if(!byId.containsKey(cell.source) || !byId.containsKey(cell.target)) {
                draft.structuralProblem(); draft.unresolved();
                draft.diag("ERROR","DRAWIO_DANGLING_EDGE_REFERENCE",source,location,cell.id,
                        "The connector references a missing source or target cell.",
                        "mxCell source/target ids must refer to cells in the same graph model.",
                        "Repair the connector endpoints in diagrams.net before importing.",List.of(),"EXACT"); continue;
            }
            String sourceClass=classByCell.getOrDefault(cell.source,""); String targetClass=classByCell.getOrDefault(cell.target,"");
            if(sourceClass.isBlank() || targetClass.isBlank()) {
                draft.unresolved();
                draft.diag("WARNING","DRAWIO_EDGE_ENDPOINT_NOT_MAPPED",source,location,cell.id,
                        "The connector endpoints are visual cells that could not both be mapped to metamodel classes.",
                        "A semantic relation cannot be inferred until both endpoint classes are known.",
                        "Resolve the node mappings first, then map this connector.",List.of(),"LOW"); continue;
            }
            String rawRelation=firstNonBlank(cell.data.get("hydraRelationId"),cell.data.get("relationId"),cell.data.get("relation"),cell.data.get("feature"));
            MetaRelation relation=rawRelation.isBlank()?analyzer.inferRelationByEndpoints(draft,sourceClass,targetClass,source,location):analyzer.resolveRelation(draft,rawRelation,source,location,true);
            if(relation==null) { draft.preserved.add(source+" :: "+pageName+" :: edge "+cell.id+" :: "+cell.label); continue; }
            Map<String,String> styles=parseMxStyle(cell.style);
            EdgeMapping mapping=new EdgeMapping(importId(source,"drawio-edge",pageName,cell.id), relation.id(), relation.sourceTypeId(), relation.targetTypeId(), drawioLineType(cell.style), "", styles, List.of(), drawioAnnotations(source,pageName,cell,new Geometry(0,0,0,0)));
            pageEdges.add(mapping); draft.edgeMappings.add(mapping);
        }
        List<String> roots=new ArrayList<>(new LinkedHashSet<>(classByCell.values()));
        if(roots.isEmpty()) roots.addAll(rootClasses(draft));
        draft.representations.add(new RepresentationDefinition(importId(source,"drawio-page",pageName),
                pageName.isBlank()?stem(source):pageName,RepresentationKind.DIAGRAM,roots,pageNodes,pageEdges,List.of(),true,
                Map.of("hydra.import.source",source,"hydra.import.technology","DRAWIO","hydra.import.drawio.page",pageName)));
    }

    /** Descripción: Valida las condiciones necesarias para «validateStructuralCells» y rechaza entradas no válidas. Entradas: source: dato de entrada necesario para la operación; pageName: dato de entrada necesario para la operación; byId: identificador que delimita el recurso objetivo; draft: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void validateStructuralCells(String source,String pageName,Map<String,Cell> byId,VisualDefinitionImportDraft draft) {
        boolean hasRoot=byId.containsKey("0"); boolean hasLayer=byId.containsKey("1") && "0".equals(byId.get("1").parent);
        if(hasRoot && hasLayer) return;
        draft.structuralProblem();
        String fixId=fixId("drawio-structural-cells",source,pageName,"root");
        draft.fix(fixId,"REBUILD_DRAWIO_STRUCTURAL_CELLS",source,pageName,"Recover the missing root/layer structure",
                "HYDRA can recover visual mappings without persisting the malformed mxGraph hierarchy; the source file should still be repaired.",
                "SAFE","HIGH",true,true);
        draft.diag("WARNING","DRAWIO_STRUCTURAL_CELLS_MISSING",source,pageName,"0/1",
                "The standard draw.io root/default-layer cells are missing or malformed.",
                "draw.io normally uses cell 0 as root and cell 1 with parent 0 as the default layer.",
                "HYDRA recovered independent visual mappings, but resave the source in diagrams.net to repair its graph structure.",List.of(fixId),"HIGH");
    }

    /** Descripción: Valida las condiciones necesarias para «validateParents» y rechaza entradas no válidas. Entradas: source: dato de entrada necesario para la operación; pageName: dato de entrada necesario para la operación; cells: dato de entrada necesario para la operación; byId: identificador que delimita el recurso objetivo; draft: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void validateParents(String source,String pageName,List<Cell> cells,Map<String,Cell> byId,VisualDefinitionImportDraft draft) {
        for(Cell cell:cells) {
            if(cell.parent.isBlank() || byId.containsKey(cell.parent)) continue;
            draft.structuralProblem();
            String fixId=fixId("drawio-parent",source,pageName,cell.id);
            draft.fix(fixId,"DETACH_MISSING_DRAWIO_PARENT",source,pageName+"/cell["+cell.id+"]","Detach the missing visual parent",
                    "The parent cell '"+cell.parent+"' does not exist. HYDRA recovers the semantic mapping independently of that broken container hierarchy.",
                    "SAFE","HIGH",true,true);
            draft.diag("WARNING","DRAWIO_MISSING_PARENT",source,pageName+"/cell["+cell.id+"]",cell.parent,
                    "A draw.io cell references a missing parent.","The original container/layer hierarchy is incomplete.",
                    "HYDRA detached the recovered mapping; repair the container relationship in diagrams.net if it is meaningful.",List.of(fixId),"HIGH");
        }
    }

    /** Descripción: Obtiene la información correspondiente a «readCell» dentro de DrawioVisualImportSupport. Entradas: element: dato de entrada necesario para la operación; index: dato de entrada necesario para la operación; fallbackLabel: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Cell readCell(Element element,int index,String fallbackLabel) {
        Element owner=element.getParentNode() instanceof Element parent && !"root".equalsIgnoreCase(local(parent)) ? parent : null;
        Map<String,String> data=new LinkedHashMap<>();
        copyAttributes(owner,data); copyAttributes(element,data);
        String originalId=element.getAttribute("id").trim(); boolean missingId=originalId.isBlank(); String id=missingId?"generated-"+index:originalId;
        String label=firstNonBlank(owner==null?"":firstAttr(owner,"label","value","name","title"),firstAttr(element,"value","label","name","title"),fallbackLabel);
        return new Cell(element,id,element.getAttribute("parent").trim(),element.getAttribute("source").trim(),element.getAttribute("target").trim(),
                "1".equals(element.getAttribute("vertex")),"1".equals(element.getAttribute("edge")),missingId,label,element.getAttribute("style"),data);
    }

    /** Descripción: Ejecuta la operación «geometry» correspondiente a la responsabilidad de DrawioVisualImportSupport. Entradas: cell: dato de entrada necesario para la operación; source: dato de entrada necesario para la operación; pageName: dato de entrada necesario para la operación; cellId: identificador que delimita el recurso objetivo; draft: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Geometry geometry(Element cell,String source,String pageName,String cellId,VisualDefinitionImportDraft draft) {
        Element geometry=firstDirectChild(cell,"mxGeometry"); double x=0,y=0,width=220,height=140; boolean repaired=false;
        if(geometry!=null) {
            x=parseDouble(geometry.getAttribute("x"),0); y=parseDouble(geometry.getAttribute("y"),0);
            width=parseDouble(geometry.getAttribute("width"),220); height=parseDouble(geometry.getAttribute("height"),140);
            if(width<=0){width=220;repaired=true;} if(height<=0){height=140;repaired=true;}
        } else repaired=true;
        if(repaired) {
            draft.invalidParameter(); String fixId=fixId("drawio-geometry",source,pageName,cellId);
            draft.fix(fixId,"DEFAULT_DRAWIO_GEOMETRY",source,pageName+"/cell["+cellId+"]","Repair invalid node geometry",
                    "Missing or non-positive node dimensions are recovered with safe default dimensions.","SAFE","EXACT",true,true);
            draft.diag("INFO","DRAWIO_GEOMETRY_NORMALIZED",source,pageName+"/cell["+cellId+"]",cellId,
                    "Invalid or missing draw.io geometry was normalized.","HYDRA requires positive default node dimensions.",
                    "No action is required unless exact source dimensions are important.",List.of(fixId),"EXACT");
        }
        return new Geometry(x,y,width,height);
    }

    /** Descripción: Ejecuta la operación «parseMxStyle» correspondiente a la responsabilidad de DrawioVisualImportSupport. Entradas: style: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Map<String,String> parseMxStyle(String style) {
        Map<String,String> raw=new LinkedHashMap<>(); if(style==null)return raw;
        for(String token:style.split(";")) { if(token.isBlank())continue;int eq=token.indexOf('=');if(eq<0)raw.putIfAbsent("shapeToken",token.trim());else raw.put(token.substring(0,eq).trim(),token.substring(eq+1).trim()); }
        Map<String,String> result=new LinkedHashMap<>();
        putColor(result,"fillColor",firstNonBlank(raw.get("fillColor"),raw.get("gradientColor")));
        putColor(result,"strokeColor",raw.get("strokeColor")); putColor(result,"labelColor",raw.get("fontColor"));
        if(validNumber(raw.get("strokeWidth")))result.put("strokeWidth",raw.get("strokeWidth"));
        if(validNumber(raw.get("fontSize")))result.put("labelFontSize",raw.get("fontSize"));
        if(validNumber(raw.get("opacity")))result.put("opacity",clampOpacity(raw.get("opacity")));
        if("1".equals(raw.get("dashed")))result.put("borderStyle","DASHED");
        if("1".equals(raw.get("rounded")))result.put("borderRadius","12");
        String start=arrow(raw.get("startArrow"));String end=arrow(raw.get("endArrow"));if(!start.isBlank())result.put("sourceDecoration",start);if(!end.isBlank())result.put("targetDecoration",end);
        return result;
    }

    /** Descripción: Ejecuta la operación «drawioShape» correspondiente a la responsabilidad de DrawioVisualImportSupport. Entradas: style: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private EditorNodeShape drawioShape(String style) {
        String value=(style==null?"":style).toLowerCase(Locale.ROOT);
        if(value.contains("ellipse"))return EditorNodeShape.ELLIPSE;if(value.contains("rhombus")||value.contains("diamond"))return EditorNodeShape.DIAMOND;
        if(value.contains("triangle"))return EditorNodeShape.TRIANGLE;if(value.contains("hexagon"))return EditorNodeShape.HEXAGON;
        if(value.contains("parallelogram"))return EditorNodeShape.PARALLELOGRAM;if(value.contains("trapezoid"))return EditorNodeShape.TRAPEZOID;
        if(value.contains("cylinder"))return EditorNodeShape.CYLINDER;if(value.contains("document"))return EditorNodeShape.DOCUMENT;
        if(value.contains("cloud"))return EditorNodeShape.CLOUD;if(value.contains("star"))return EditorNodeShape.STAR;
        if(value.contains("rounded=1"))return EditorNodeShape.ROUNDED_RECTANGLE;return EditorNodeShape.RECTANGLE;
    }
    /** Descripción: Ejecuta la operación «drawioLineType» correspondiente a la responsabilidad de DrawioVisualImportSupport. Entradas: style: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private EditorEdgeLineType drawioLineType(String style) {String v=(style==null?"":style).toLowerCase(Locale.ROOT);if(v.contains("orthogonaledgestyle"))return EditorEdgeLineType.ORTHOGONAL;if(v.contains("elbowedgestyle")||v.contains("entityrelationedgestyle"))return EditorEdgeLineType.STEP;if(v.contains("curved=1"))return EditorEdgeLineType.BEZIER;return EditorEdgeLineType.STRAIGHT;}
    /** Descripción: Ejecuta la operación «drawioAnnotations» correspondiente a la responsabilidad de DrawioVisualImportSupport. Entradas: source: dato de entrada necesario para la operación; pageName: dato de entrada necesario para la operación; cell: dato de entrada necesario para la operación; geometry: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Map<String,String> drawioAnnotations(String source,String pageName,Cell cell,Geometry geometry){Map<String,String> a=new LinkedHashMap<>();a.put("hydra.import.source",source);a.put("hydra.import.drawio.page",pageName);a.put("hydra.import.drawio.cellId",cell.id);if(!cell.parent.isBlank())a.put("hydra.import.drawio.parent",cell.parent);if(geometry.width>0){a.put("hydra.import.drawio.x",numberText(geometry.x));a.put("hydra.import.drawio.y",numberText(geometry.y));}return a;}

    /** Descripción: Ejecuta la operación «decodeCompressedXml» correspondiente a la responsabilidad de DrawioVisualImportSupport. Entradas: encoded: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private byte[] decodeCompressedXml(String encoded) {
        try {
            byte[] compressed=Base64.getDecoder().decode(encoded.replaceAll("\\s+","")); Inflater inflater=new Inflater(true);inflater.setInput(compressed);
            ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int total=0;
            while(!inflater.finished()) {int count=inflater.inflate(buffer);if(count==0){if(inflater.needsInput()||inflater.needsDictionary())break;}total+=count;if(total>MAX_INFLATED_DIAGRAM_BYTES)throw new IllegalArgumentException("inflated draw.io page exceeds safe limit");out.write(buffer,0,count);}inflater.end();
            String percentEncoded=out.toString(StandardCharsets.UTF_8);String xml=URLDecoder.decode(percentEncoded,StandardCharsets.UTF_8);return xml.getBytes(StandardCharsets.UTF_8);
        } catch(IllegalArgumentException|DataFormatException exception) { throw new IllegalArgumentException("Could not decode compressed draw.io XML",exception); }
    }

    /** Descripción: Ejecuta la operación «firstSvgPath» correspondiente a la responsabilidad de DrawioVisualImportSupport. Entradas: data: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String firstSvgPath(String data) {
        try {
            if(data==null||!data.startsWith("data:image/svg+xml"))return "";int comma=data.indexOf(',');if(comma<0)return "";
            String header=data.substring(0,comma);String payload=data.substring(comma+1);byte[] bytes=header.contains(";base64")?Base64.getDecoder().decode(payload):URLDecoder.decode(payload,StandardCharsets.UTF_8).getBytes(StandardCharsets.UTF_8);
            Document document=SecureXmlSupport.parse(bytes,"draw.io SVG library image");NodeList paths=document.getElementsByTagName("path");
            if(paths.getLength()==0)return "";String d=((Element)paths.item(0)).getAttribute("d").trim();return d.length()<=8000?d:"";
        }catch(RuntimeException exception){return "";}
    }

    /** Descripción: Ejecuta la operación «copyAttributes» correspondiente a la responsabilidad de DrawioVisualImportSupport. Entradas: element: dato de entrada necesario para la operación; target: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void copyAttributes(Element element,Map<String,String> target){if(element==null)return;var attrs=element.getAttributes();for(int i=0;i<attrs.getLength();i++){Node attr=attrs.item(i);target.putIfAbsent(attr.getNodeName(),attr.getNodeValue());}}
    /** Descripción: Ejecuta la operación «firstAttr» correspondiente a la responsabilidad de DrawioVisualImportSupport. Entradas: element: dato de entrada necesario para la operación; names: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String firstAttr(Element element,String...names){if(element==null)return "";for(String name:names){String v=element.getAttribute(name).trim();if(!v.isBlank())return v;}return "";}
    /** Descripción: Ejecuta la operación «jsonText» correspondiente a la responsabilidad de DrawioVisualImportSupport. Entradas: node: dato de entrada necesario para la operación; names: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String jsonText(JsonNode node,String...names){for(String name:names){JsonNode v=node.get(name);if(v!=null&&v.isValueNode()&&!v.asText().isBlank())return v.asText();}return "";}
    /** Descripción: Ejecuta la operación «directChildren» correspondiente a la responsabilidad de DrawioVisualImportSupport. Entradas: parent: dato de entrada necesario para la operación; name: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<Element> directChildren(Element parent,String name){List<Element> result=new ArrayList<>();Node child=parent.getFirstChild();while(child!=null){if(child instanceof Element e&&name.equalsIgnoreCase(local(e)))result.add(e);child=child.getNextSibling();}return result;}
    /** Descripción: Ejecuta la operación «firstDirectChild» correspondiente a la responsabilidad de DrawioVisualImportSupport. Entradas: parent: dato de entrada necesario para la operación; name: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Element firstDirectChild(Element parent,String name){for(Element child:directChildren(parent,name))return child;return null;}
    /** Descripción: Ejecuta la operación «local» correspondiente a la responsabilidad de DrawioVisualImportSupport. Entradas: e: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String local(Element e){return e.getLocalName()==null?e.getTagName().replaceFirst("^.*:",""):e.getLocalName();}
    /** Descripción: Ejecuta la operación «rootClasses» correspondiente a la responsabilidad de DrawioVisualImportSupport. Entradas: draft: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<String> rootClasses(VisualDefinitionImportDraft draft){return draft.metamodel.classes().stream().filter(value->value.kind().name().equals("CLASS")).limit(1).map(MetaClass::id).toList();}
    /** Descripción: Ejecuta la operación «className» correspondiente a la responsabilidad de DrawioVisualImportSupport. Entradas: draft: dato de entrada necesario para la operación; id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private String className(VisualDefinitionImportDraft draft,String id){return draft.metamodel.classes().stream().filter(value->value.id().equals(id)).map(MetaClass::name).findFirst().orElse(id);}
    /** Descripción: Ejecuta la operación «stem» correspondiente a la responsabilidad de DrawioVisualImportSupport. Entradas: name: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String stem(String name){String value=name.replace('\\','/');if(value.contains("/"))value=value.substring(value.lastIndexOf('/')+1);int dot=value.indexOf('.');return dot>0?value.substring(0,dot):value;}
    /** Descripción: Ejecuta la operación «stripHtml» correspondiente a la responsabilidad de DrawioVisualImportSupport. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String stripHtml(String value){return value==null?"":value.replaceAll("<[^>]+>"," ").replace("&nbsp;"," ").replace("&amp;","&").replace("&lt;","<").replace("&gt;",">").replaceAll("\\s+"," ").trim();}
    /** Descripción: Ejecuta la operación «firstNonBlank» correspondiente a la responsabilidad de DrawioVisualImportSupport. Entradas: values: colección de elementos que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String firstNonBlank(String...values){for(String value:values)if(value!=null&&!value.isBlank())return value.trim();return "";}
    /** Descripción: Ejecuta la operación «textOr» correspondiente a la responsabilidad de DrawioVisualImportSupport. Entradas: value: valor de entrada que debe procesarse; fallback: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String textOr(String value,String fallback){return value==null||value.isBlank()?fallback:value.trim();}
    /** Descripción: Ejecuta la operación «parseDouble» correspondiente a la responsabilidad de DrawioVisualImportSupport. Entradas: value: valor de entrada que debe procesarse; fallback: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private double parseDouble(String value,double fallback){try{return value==null||value.isBlank()?fallback:Double.parseDouble(value);}catch(NumberFormatException exception){return fallback;}}
    /** Descripción: Ejecuta la operación «positiveNumber» correspondiente a la responsabilidad de DrawioVisualImportSupport. Entradas: node: dato de entrada necesario para la operación; fallback: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private double positiveNumber(JsonNode node,double fallback){if(!positiveNumericNode(node))return fallback;return node.asDouble();}
    /** Descripción: Ejecuta la operación «positiveNumericNode» correspondiente a la responsabilidad de DrawioVisualImportSupport. Entradas: node: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean positiveNumericNode(JsonNode node){if(node==null||!node.isNumber())return false;double value=node.asDouble();return Double.isFinite(value)&&value>0;}
    /** Descripción: Ejecuta la operación «validNumber» correspondiente a la responsabilidad de DrawioVisualImportSupport. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean validNumber(String value){if(value==null||value.isBlank())return false;try{return Double.isFinite(Double.parseDouble(value));}catch(NumberFormatException exception){return false;}}
    /** Descripción: Ejecuta la operación «putColor» correspondiente a la responsabilidad de DrawioVisualImportSupport. Entradas: target: dato de entrada necesario para la operación; key: identificador que delimita el recurso objetivo; value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void putColor(Map<String,String> target,String key,String value){if(value!=null&&value.matches("#[0-9A-Fa-f]{6}"))target.put(key,value.toUpperCase(Locale.ROOT));}
    /** Descripción: Ejecuta la operación «clampOpacity» correspondiente a la responsabilidad de DrawioVisualImportSupport. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String clampOpacity(String value){double n=parseDouble(value,100);if(n>1)n=n/100.0;return Double.toString(Math.max(0.1,Math.min(1,n)));}
    /** Descripción: Ejecuta la operación «arrow» correspondiente a la responsabilidad de DrawioVisualImportSupport. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String arrow(String value){if(value==null||value.isBlank()||"none".equalsIgnoreCase(value))return "NONE";String v=value.toLowerCase(Locale.ROOT);if(v.contains("diamond"))return "DIAMOND";if(v.contains("oval")||v.contains("circle"))return "CIRCLE";return "ARROW";}
    /** Descripción: Ejecuta la operación «importId» correspondiente a la responsabilidad de DrawioVisualImportSupport. Entradas: parts: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String importId(String...parts){String raw=String.join("-",parts).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._:-]+","-").replaceAll("^-+|-+$","");if(raw.isBlank())raw="imported";if(!Character.isLetter(raw.charAt(0)))raw="i-"+raw;return raw.length()>120?raw.substring(0,120):raw;}
    /** Descripción: Ejecuta la operación «fixId» correspondiente a la responsabilidad de DrawioVisualImportSupport. Entradas: parts: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String fixId(String...parts){return importId(parts);}
    /** Descripción: Ejecuta la operación «numberText» correspondiente a la responsabilidad de DrawioVisualImportSupport. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String numberText(double value){return value==(long)value?Long.toString((long)value):Double.toString(value);}
    /** Descripción: Ejecuta la operación «sha256» correspondiente a la responsabilidad de DrawioVisualImportSupport. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String sha256(byte[] value){try{byte[] digest=MessageDigest.getInstance("SHA-256").digest(value);StringBuilder out=new StringBuilder();for(byte b:digest)out.append(String.format("%02x",b));return out.toString();}catch(NoSuchAlgorithmException exception){throw new IllegalStateException("SHA-256 must be available",exception);}}

    private record Geometry(double x,double y,double width,double height) {}
    private record Cell(Element element,String id,String parent,String source,String target,boolean vertex,boolean edge,boolean missingId,String label,String style,Map<String,String> data) {}
}
