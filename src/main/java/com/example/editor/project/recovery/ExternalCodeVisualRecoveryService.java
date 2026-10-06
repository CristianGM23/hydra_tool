package com.example.editor.project.recovery;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.example.editor.definition.CodeGenerationDefinition;
import com.example.editor.definition.EditorCapability;
import com.example.editor.definition.EditorCapabilityManifest;
import com.example.editor.definition.EditorContractVersions;
import com.example.editor.definition.EditorDefinitionModel;
import com.example.editor.definition.EditorImageFit;
import com.example.editor.definition.EditorNodeShape;
import com.example.editor.definition.EditorRuntimePolicy;
import com.example.editor.definition.NodeMapping;
import com.example.editor.definition.RepresentationDefinition;
import com.example.editor.definition.RepresentationKind;
import com.example.editor.definition.ToolPaletteDefinition;
import com.example.editor.metamodel.core.MetaClass;
import com.example.editor.metamodel.core.MetaModel;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Recuperación estática de configuración visual contenida en proyectos de código (GLSP/BESSER WME).
 * Prioriza siempre snapshots canónicos de HYDRA; en proyectos ajenos sólo materializa asociaciones explícitas
 * entre tipos visuales y EClasses. Nunca carga, compila ni ejecuta Java, TypeScript o Python importado.
 */
@Service
public class ExternalCodeVisualRecoveryService {
    private static final Pattern TYPE_ARRAY = Pattern.compile("(?is)\\b(?:hydraNodeTypes|HYDRA_NODE_TYPES|nodeTypes|modelTypes|elementTypes)\\b\\s*(?:=|:)\\s*\\[([^]]*)]");
    private static final Pattern QUOTED = Pattern.compile("[\\\"']([^\\\"']+)[\\\"']");
    private static final Pattern EXPLICIT_SEMANTIC = Pattern.compile("(?is)\\b(?:domainClass|targetClass|semanticType|modelType|className)\\b\\s*[:=]\\s*[\\\"']([^\\\"']+)[\\\"']");
    private final JsonMapper mapper;

    /** Descripción: Inicializa una instancia de ExternalCodeVisualRecoveryService con sus dependencias y datos de entrada. Entradas: mapper: componente de conversión utilizado durante el procesamiento. Salida: deja la instancia inicializada y lista para su uso. */ public ExternalCodeVisualRecoveryService(JsonMapper mapper) { this.mapper = mapper; }

    /** Descripción: Ejecuta la operación «recover» correspondiente a la responsabilidad de ExternalCodeVisualRecoveryService. Entradas: metamodel: dato de entrada necesario para la operación; entries: colección de elementos que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ List<RecoveredCodeEditor> recover(MetaModel metamodel, List<ExternalProjectArchiveEntry> entries) {
        List<RecoveredCodeEditor> canonical = recoverCanonical(metamodel, entries);
        if(!canonical.isEmpty()) return canonical;
        List<RecoveredCodeEditor> result = new ArrayList<>();
        RecoveredCodeEditor glsp = recoverExplicitCodeEditor(metamodel, entries, "ECLIPSE_GLSP"); if(glsp != null) result.add(glsp);
        RecoveredCodeEditor besser = recoverExplicitCodeEditor(metamodel, entries, "BESSER_WME"); if(besser != null) result.add(besser);
        return List.copyOf(result);
    }

    /** Descripción: Ejecuta la operación «recoverCanonical» correspondiente a la responsabilidad de ExternalCodeVisualRecoveryService. Entradas: metamodel: dato de entrada necesario para la operación; entries: colección de elementos que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private List<RecoveredCodeEditor> recoverCanonical(MetaModel metamodel, List<ExternalProjectArchiveEntry> entries) {
        List<RecoveredCodeEditor> result = new ArrayList<>(); Set<String> ids = new LinkedHashSet<>();
        for(ExternalProjectArchiveEntry entry : entries) {
            String path = lower(entry.path()).replace('\\','/');
            if(!(path.endsWith("/editor_definition.json") || path.endsWith("/editor-definition.json") || path.equals("editor_definition.json"))) continue;
            if(!StaticSourceInspectionPolicy.canonicalSnapshotAllowed(entry)) continue;
            try {
                EditorDefinitionModel editor = mapper.readValue(entry.bytes(), EditorDefinitionModel.class);
                if(!editor.metamodelId().equals(metamodel.id())) continue;
                if(ids.add(editor.id())) {
                    String technology = inferTechnology(entries, entry.path());
                    Map<String,String> annotations = new LinkedHashMap<>(editor.annotations());
                    annotations.put("hydra.import.source", entry.path()); annotations.put("hydra.import.technology", technology);
                    annotations.put("hydra.import.canonicalRoundTrip", "true");
                    EditorDefinitionModel rebound = new EditorDefinitionModel(editor.schemaVersion(),editor.id(),editor.metamodelId(),editor.name(),editor.representations(),editor.palette(),editor.forms(),editor.capabilityManifest(),editor.runtimePolicy(),editor.codeGeneration(),annotations);
                    result.add(new RecoveredCodeEditor(rebound, technology, List.of(entry.path()), List.of("Recovered a canonical editor-definition snapshot embedded by the external technology; no code was executed.")));
                }
            } catch(RuntimeException ignored) { /* A similarly named JSON resource is not automatically trusted as a HYDRA contract. */ }
        }
        return List.copyOf(result);
    }

    /** Descripción: Ejecuta la operación «recoverExplicitCodeEditor» correspondiente a la responsabilidad de ExternalCodeVisualRecoveryService. Entradas: metamodel: dato de entrada necesario para la operación; entries: colección de elementos que debe procesarse; technology: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private RecoveredCodeEditor recoverExplicitCodeEditor(MetaModel metamodel,List<ExternalProjectArchiveEntry> entries,String technology) {
        List<ExternalProjectArchiveEntry> evidence = entries.stream()
                .filter(entry -> StaticSourceInspectionPolicy.isInspectableTextPath(entry.path()))
                .filter(entry -> StaticSourceInspectionPolicy.inspectText(entry).isPresent())
                .filter(entry -> matchesTechnology(entry, technology)).toList();
        if(evidence.isEmpty()) return null;
        Set<String> explicitTypes = new LinkedHashSet<>(); List<String> sourcePaths = new ArrayList<>(); String diagramType="";
        for(ExternalProjectArchiveEntry entry : evidence) {
            String text = text(entry); sourcePaths.add(entry.path());
            Matcher arrays=TYPE_ARRAY.matcher(text); while(arrays.find()){Matcher q=QUOTED.matcher(arrays.group(1));while(q.find())explicitTypes.add(q.group(1));}
            Matcher semantic=EXPLICIT_SEMANTIC.matcher(text); while(semantic.find()) explicitTypes.add(semantic.group(1));
            if(lower(entry.path()).endsWith(".json")) {
                try { JsonNode root=mapper.readTree(entry.bytes()); if(diagramType.isBlank()&&root.has("diagramType"))diagramType=root.path("diagramType").asString(""); }
                catch(RuntimeException ignored) { /* Optional recovery evidence is ignored only after the canonical path has been preserved. */ }
            }
            if(diagramType.isBlank()) {
                Matcher d=Pattern.compile("(?is)\\b(?:HYDRA_(?:BESSER_)?DIAGRAM_TYPE|diagramType)\\b\\s*(?:=|:)\\s*[\\\"']([^\\\"']+)[\\\"']").matcher(text);
                if(d.find()) diagramType=d.group(1);
            }
        }
        List<MetaClass> classes = explicitTypes.stream().map(value -> resolveClass(metamodel,value)).filter(java.util.Objects::nonNull).distinct().toList();
        if(classes.isEmpty()) return null; // no guessing: visual strings that cannot be tied to EClasses stay as source evidence.
        String suffix=sha256(technology+"|"+String.join("|",sourcePaths)).substring(0,16);
        String repId="representation:external-"+suffix;
        List<NodeMapping> nodes=new ArrayList<>(); int index=0;
        for(MetaClass metaClass:classes) {
            if(metaClass.kind()==com.example.editor.metamodel.core.MetaClassKind.ABSTRACT_CLASS) continue;
            Map<String,String> annotations=Map.of("hydra.import.technology",technology,"hydra.import.staticCodeRecovery","true");
            nodes.add(new NodeMapping("node:external-"+suffix+"-"+(++index),metaClass.id(),metaClass.name(),EditorNodeShape.ROUNDED_RECTANGLE,220,140,80,50,"","",EditorImageFit.CONTAIN,false,List.of(),18,Map.of(),List.of(),annotations));
        }
        if(nodes.isEmpty()) return null;
        String name=(diagramType==null||diagramType.isBlank()?technology+" recovered diagram":diagramType);
        RepresentationDefinition representation=new RepresentationDefinition(repId,name,RepresentationKind.DIAGRAM,List.of(classes.get(0).id()),nodes,List.of(),List.of(),List.of(),List.of(),true,Map.of("hydra.import.technology",technology,"hydra.import.staticCodeRecovery","true"));
        EditorDefinitionModel editor=new EditorDefinitionModel(EditorContractVersions.EDITOR_DEFINITION_MODEL,"editor.external-"+suffix,metamodel.id(),name,List.of(representation),new ToolPaletteDefinition(List.of(),List.of()),List.of(),new EditorCapabilityManifest(Set.of(EditorCapability.MODEL_EDITING,EditorCapability.MULTIPLE_REPRESENTATIONS)),EditorRuntimePolicy.defaults(),CodeGenerationDefinition.disabled(),Map.of("hydra.import.technology",technology,"hydra.import.reviewRequired","true"));
        return new RecoveredCodeEditor(editor,technology,List.copyOf(sourcePaths),List.of("Recovered only explicit code-level semantic type mappings. Unresolved GLSP/BESSER registrations remain preserved for review."));
    }

    /** Descripción: Evalúa la condición «matchesTechnology» sin modificar el estado externo. Entradas: entry: dato de entrada necesario para la operación; technology: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean matchesTechnology(ExternalProjectArchiveEntry entry,String technology) {
        String path=lower(entry.path()), text=lower(text(entry));
        if("ECLIPSE_GLSP".equals(technology)) return path.contains("glsp")||text.contains("@eclipse-glsp")||text.contains("org.eclipse.glsp")||text.contains("hydra_glsp");
        return path.contains("besser")||text.contains("@besser/wme")||text.contains("hydra_besser_diagram_type")||text.contains("besser wme");
    }
    /** Descripción: Ejecuta la operación «inferTechnology» correspondiente a la responsabilidad de ExternalCodeVisualRecoveryService. Entradas: entries: colección de elementos que debe procesarse; source: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String inferTechnology(List<ExternalProjectArchiveEntry> entries,String source) {
        if(entries.stream().anyMatch(e -> matchesTechnology(e,"ECLIPSE_GLSP"))) return "ECLIPSE_GLSP";
        if(entries.stream().anyMatch(e -> matchesTechnology(e,"BESSER_WME"))) return "BESSER_WME";
        return "EXTERNAL_CANONICAL";
    }
    /** Descripción: Obtiene la información correspondiente a «resolveClass» dentro de ExternalCodeVisualRecoveryService. Entradas: metamodel: dato de entrada necesario para la operación; value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private MetaClass resolveClass(MetaModel metamodel,String value) { String token=value==null?"":value.replaceAll(".*[!:]","").trim(); List<MetaClass> matches=metamodel.classes().stream().filter(c->c.name().equalsIgnoreCase(token)||c.id().equalsIgnoreCase(value)).toList();return matches.size()==1?matches.get(0):null; }
    /** Lee únicamente fuentes UTF-8 textuales y acotadas; los binarios o fuentes sobredimensionadas no se inspeccionan. */
    /** Descripción: Ejecuta la operación «text» correspondiente a la responsabilidad de RecoveredCodeEditor. Entradas: entry: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String text(ExternalProjectArchiveEntry entry){return StaticSourceInspectionPolicy.inspectText(entry).orElse("");}
    /** Descripción: Ejecuta la operación «lower» correspondiente a la responsabilidad de ExternalCodeVisualRecoveryService. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String lower(String value){return value==null?"":value.toLowerCase(Locale.ROOT);}
    /** Descripción: Ejecuta la operación «sha256» correspondiente a la responsabilidad de ExternalCodeVisualRecoveryService. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String sha256(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}

    record RecoveredCodeEditor(EditorDefinitionModel editor,String technology,List<String> sources,List<String> diagnostics) { }
}
