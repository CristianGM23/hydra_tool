package com.example.editor.grammar.visualization;

import java.util.*;
import org.springframework.stereotype.Service;
import com.example.editor.grammar.GrammarProperties;
import com.example.editor.grammar.core.*;
import com.example.editor.grammar.application.GrammarSourceAnalyzer;
import com.example.editor.grammar.diagnostic.GrammarDiagnostic;
import com.example.editor.metamodel.core.*;
import com.example.editor.metamodel.importing.support.GeneratedGrammarImportSupport;

/**
 * Projects the provider-neutral grammar draft to HYDRA's canonical metamodel and a class-diagram view model.
 * Parsing/inference reuses the existing grammar-import semantic engine instead of duplicating it in React.
 */
@Service
public class GrammarClassDiagramProjectionService {
    private final GrammarProperties properties;
    private final Map<String,GrammarClassDiagramProjection> lastKnownGood;

    /** Descripción: Inicializa una instancia de GrammarClassDiagramProjectionService con sus dependencias y datos de entrada. Entradas: properties: configuración que gobierna el comportamiento. Salida: deja la instancia inicializada y lista para su uso. */ public GrammarClassDiagramProjectionService(GrammarProperties properties){
        this.properties=properties==null?new GrammarProperties():properties;
        this.lastKnownGood=Collections.synchronizedMap(new LinkedHashMap<>(16,0.75f,true){
            /** Descripción: Elimina o libera el recurso gestionado por «removeEldestEntry» de forma controlada. Entradas: eldest: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ @Override protected boolean removeEldestEntry(Map.Entry<String,GrammarClassDiagramProjection> eldest){
                return size()>GrammarClassDiagramProjectionService.this.properties.getMaximumDiagramCacheEntries();
            }
        });
    }

    /** Descripción: Ejecuta la operación «project» correspondiente a la responsabilidad de GrammarClassDiagramProjectionService. Entradas: grammar: dato de entrada necesario para la operación; analysis: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public GrammarClassDiagramProjection project(GrammarDefinitionModel grammar, GrammarAnalysisResult analysis){
        return project(grammar,grammar==null?List.of():grammar.documents(),analysis);
    }

    /**
     * Infiere estrictamente el metamodelo neutral desde las fuentes actuales sin utilizar jamás
     * el último resultado válido almacenado en caché. Una fuente que no produce ningún clasificador
     * semántico no puede certificarse como una inferencia válida.
     */
    /** Descripción: Ejecuta la operación «inferMetamodelStrict» correspondiente a la responsabilidad de GrammarClassDiagramProjectionService. Entradas: grammar: dato de entrada necesario para la operación; documents: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public MetaModel inferMetamodelStrict(GrammarDefinitionModel grammar,List<GrammarSourceDocument> documents){
        Objects.requireNonNull(grammar,"grammar");
        List<GrammarSourceDocument> frozen=documents==null||documents.isEmpty()?grammar.documents():List.copyOf(documents);
        GrammarAnalysisRequest request=new GrammarAnalysisRequest(
                grammar.id(),0,GrammarAnalysisMode.FULL,List.of(),frozen,grammar.toolchainProfileId());
        GrammarAnalysisResult analysis=new GrammarSourceAnalyzer(properties).analyze(grammar,request);
        if(analysis.hasErrors()){
            throw new IllegalArgumentException("Strict grammar inference rejected current sources with analysis errors");
        }
        GeneratedGrammarImportSupport.ParsedGrammar parsed=parse(grammar,frozen);
        MetaModel metamodel=parsed.metamodel();
        if(metamodel.classes().isEmpty()&&metamodel.enums().isEmpty()&&metamodel.dataTypes().isEmpty()){
            throw new IllegalArgumentException("Strict grammar inference produced no semantic classifiers from the current sources");
        }
        return metamodel;
    }

    /** Projects exactly the frozen documents used by analysis/compilation, never a potentially older persisted draft. */
    /** Descripción: Ejecuta la operación «project» correspondiente a la responsabilidad de GrammarClassDiagramProjectionService. Entradas: grammar: dato de entrada necesario para la operación; documents: dato de entrada necesario para la operación; analysis: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public GrammarClassDiagramProjection project(GrammarDefinitionModel grammar,List<GrammarSourceDocument> documents,GrammarAnalysisResult analysis){
        Objects.requireNonNull(grammar,"grammar");
        List<GrammarSourceDocument> frozen=documents==null||documents.isEmpty()?grammar.documents():List.copyOf(documents);
        GrammarAnalysisResult effective=analysis==null?emptyAnalysis(grammar,frozen):analysis;
        String sourceHash=effective.sourceHash().isBlank()?GrammarHashing.sources(frozen):effective.sourceHash();
        try{
            GeneratedGrammarImportSupport.ParsedGrammar parsed=parse(grammar,frozen);
            MetaModel metamodel=parsed.metamodel();
            List<GrammarClassDiagramNode> nodes=nodes(grammar,effective,metamodel);
            List<GrammarClassDiagramEdge> edges=edges(effective,metamodel,nodes);
            enforceLimits(nodes,edges);
            GrammarDiagramProjectionStatus status=nodes.isEmpty()?GrammarDiagramProjectionStatus.EMPTY:(effective.hasErrors()?GrammarDiagramProjectionStatus.PARTIAL:GrammarDiagramProjectionStatus.COMPLETE);
            List<GrammarProjectionWarning> warnings=parsed.warnings().stream().map(item->new GrammarProjectionWarning(item.code(),item.path(),item.message())).toList();
            String projectionHash=hash(nodes,edges);
            GrammarClassDiagramProjection result=new GrammarClassDiagramProjection(grammar.id(),effective.draftRevision(),sourceHash,sourceHash,status,metamodel,nodes,edges,warnings,projectionHash,
                    Map.of("technology",grammar.technology().name(),"inferenceEngine","HYDRA_GENERATED_GRAMMAR_IMPORT_SUPPORT","diagnosticOverlay",Integer.toString(effective.diagnostics().size())));
            if(status==GrammarDiagramProjectionStatus.COMPLETE)lastKnownGood.put(grammar.id(),result);
            return fallbackIfEmpty(grammar,effective,result);
        }catch(RuntimeException exception){
            GrammarClassDiagramProjection cached=lastKnownGood.get(grammar.id());
            if(cached!=null)return fallback(grammar,effective,cached,"Projection failed: "+safeMessage(exception));
            return new GrammarClassDiagramProjection(grammar.id(),effective.draftRevision(),sourceHash,sourceHash,GrammarDiagramProjectionStatus.FAILED,emptyMetamodel(grammar),List.of(),List.of(),
                    List.of(new GrammarProjectionWarning("GRAMMAR_DIAGRAM_PROJECTION_FAILED","$.grammar",safeMessage(exception))),"",Map.of("technology",grammar.technology().name()));
        }
    }

    /** Descripción: Ejecuta la operación «fallbackIfEmpty» correspondiente a la responsabilidad de GrammarClassDiagramProjectionService. Entradas: grammar: dato de entrada necesario para la operación; analysis: dato de entrada necesario para la operación; result: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private GrammarClassDiagramProjection fallbackIfEmpty(GrammarDefinitionModel grammar,GrammarAnalysisResult analysis,GrammarClassDiagramProjection result){
        if(result.status()!=GrammarDiagramProjectionStatus.EMPTY)return result;
        GrammarClassDiagramProjection cached=lastKnownGood.get(grammar.id());
        return cached==null?result:fallback(grammar,analysis,cached,"Current draft does not yield a safe structural projection");
    }

    /** Descripción: Ejecuta la operación «fallback» correspondiente a la responsabilidad de GrammarClassDiagramProjectionService. Entradas: grammar: dato de entrada necesario para la operación; analysis: dato de entrada necesario para la operación; cached: dato de entrada necesario para la operación; reason: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private GrammarClassDiagramProjection fallback(GrammarDefinitionModel grammar,GrammarAnalysisResult analysis,GrammarClassDiagramProjection cached,String reason){
        List<GrammarProjectionWarning> warnings=new ArrayList<>(cached.warnings());
        warnings.add(new GrammarProjectionWarning("GRAMMAR_DIAGRAM_LAST_KNOWN_GOOD","$.grammar",reason));
        return new GrammarClassDiagramProjection(grammar.id(),analysis.draftRevision(),analysis.sourceHash(),cached.sourceHash(),GrammarDiagramProjectionStatus.LAST_KNOWN_GOOD,
                cached.inferredMetamodel(),cached.nodes(),cached.edges(),warnings,cached.projectionHash(),Map.of("technology",grammar.technology().name(),"fallback","last-known-good"));
    }

    /** Descripción: Ejecuta la operación «parse» correspondiente a la responsabilidad de GrammarClassDiagramProjectionService. Entradas: grammar: dato de entrada necesario para la operación; documents: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private GeneratedGrammarImportSupport.ParsedGrammar parse(GrammarDefinitionModel grammar,List<GrammarSourceDocument> documents){
        List<GrammarSourceDocument> ordered=new ArrayList<>(documents);
        ordered.sort(Comparator.comparing((GrammarSourceDocument item)->!item.id().equals(grammar.entryDocumentId())&&!item.entryPoint()).thenComparing(GrammarSourceDocument::relativePath).thenComparing(GrammarSourceDocument::id));
        String combined=ordered.stream().map(item->"\n/* HYDRA_DOCUMENT "+safeComment(item.relativePath())+" */\n"+item.content()+"\n").reduce("",String::concat);
        return grammar.technology()==GrammarTechnology.ANTLR
                ?GeneratedGrammarImportSupport.parseAntlr(combined,grammar.name())
                :GeneratedGrammarImportSupport.parseTextual(combined,grammar.name(),"",grammar.technology()==GrammarTechnology.XTEXT?"xtext":"langium");
    }

    /** Descripción: Ejecuta la operación «nodes» correspondiente a la responsabilidad de GrammarClassDiagramProjectionService. Entradas: grammar: dato de entrada necesario para la operación; analysis: dato de entrada necesario para la operación; metamodel: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<GrammarClassDiagramNode> nodes(GrammarDefinitionModel grammar,GrammarAnalysisResult analysis,MetaModel metamodel){
        Map<String,String> names=new LinkedHashMap<>();
        metamodel.classes().forEach(item->names.put(item.id(),item.name()));metamodel.enums().forEach(item->names.put(item.id(),item.name()));metamodel.dataTypes().forEach(item->names.put(item.id(),item.name()));
        String rootId=metamodel.annotations().getOrDefault("rootClassId","");
        List<GrammarClassDiagramNode> result=new ArrayList<>();
        for(MetaClass item:metamodel.classes()){
            String rule=item.annotations().getOrDefault("sourceRule",item.name());GrammarDiagramSourceRef source=sourceForRule(analysis,rule);
            List<GrammarDiagramFeature> features=new ArrayList<>();
            for(MetaAttribute attribute:item.attributes()){
                GrammarDiagramSourceRef featureSource=sourceForFeature(analysis,source,attribute.name());
                features.add(new GrammarDiagramFeature(attribute.id(),attribute.name(),typeName(attribute.type(),names),multiplicity(attribute.multiplicity()),"ATTRIBUTE",featureSource,diagnosticsFor(analysis,rule,featureSource)));
            }
            metamodel.relations().stream().filter(rel->rel.sourceTypeId().equals(item.id())&&rel.kind()!=MetaRelationKind.INHERITANCE&&rel.kind()!=MetaRelationKind.IMPLEMENTATION)
                    .forEach(rel->{GrammarDiagramSourceRef featureSource=sourceForFeature(analysis,source,rel.name());features.add(new GrammarDiagramFeature(rel.id(),rel.name(),names.getOrDefault(rel.targetTypeId(),rel.targetTypeId()),multiplicity(rel.targetMultiplicity()),rel.kind().name(),featureSource,diagnosticsFor(analysis,rule,featureSource)));});
            int diagnosticCount=diagnosticsFor(analysis,rule,source);
            result.add(new GrammarClassDiagramNode(item.id(),item.id(),item.name(),item.kind().name(),item.id().equals(rootId),confidence(grammar,analysis,rule),diagnosticCount,features,source,item.annotations()));
        }
        for(MetaEnum item:metamodel.enums()){
            String rule=item.annotations().getOrDefault("sourceRule",item.name());GrammarDiagramSourceRef source=sourceForRule(analysis,rule);
            List<GrammarDiagramFeature> features=item.literals().stream().map(literal->new GrammarDiagramFeature(literal.id(),literal.name(),"",Integer.toString(literal.value()),"ENUM_LITERAL",source,diagnosticsFor(analysis,rule,source))).toList();
            result.add(new GrammarClassDiagramNode(item.id(),item.id(),item.name(),"ENUM",false,confidence(grammar,analysis,rule),diagnosticsFor(analysis,rule,source),features,source,item.annotations()));
        }
        for(MetaDataType item:metamodel.dataTypes()){
            String rule=item.annotations().getOrDefault("sourceRule",item.name());GrammarDiagramSourceRef source=sourceForRule(analysis,rule);
            result.add(new GrammarClassDiagramNode(item.id(),item.id(),item.name(),"DATA_TYPE",false,confidence(grammar,analysis,rule),diagnosticsFor(analysis,rule,source),List.of(),source,item.annotations()));
        }
        return List.copyOf(result);
    }

    /** Descripción: Ejecuta la operación «edges» correspondiente a la responsabilidad de GrammarClassDiagramProjectionService. Entradas: analysis: dato de entrada necesario para la operación; metamodel: dato de entrada necesario para la operación; nodes: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<GrammarClassDiagramEdge> edges(GrammarAnalysisResult analysis,MetaModel metamodel,List<GrammarClassDiagramNode> nodes){
        Set<String> visible=nodes.stream().map(GrammarClassDiagramNode::id).collect(java.util.stream.Collectors.toSet());
        List<GrammarClassDiagramEdge> result=new ArrayList<>();
        for(MetaRelation relation:metamodel.relations()){
            if(!visible.contains(relation.sourceTypeId())||!visible.contains(relation.targetTypeId()))continue;
            GrammarClassDiagramNode owner=nodes.stream().filter(node->node.id().equals(relation.sourceTypeId())).findFirst().orElse(null);
            GrammarDiagramSourceRef source=owner==null?GrammarDiagramSourceRef.empty():sourceForFeature(analysis,owner.source(),relation.name());
            String rule=owner==null?"":owner.source().ruleName();
            result.add(new GrammarClassDiagramEdge(relation.id(),relation.sourceTypeId(),relation.targetTypeId(),relation.kind().name(),relation.name(),multiplicity(relation.sourceMultiplicity()),multiplicity(relation.targetMultiplicity()),diagnosticsFor(analysis,rule,source),source,relation.annotations()));
        }
        return List.copyOf(result);
    }

    /** Descripción: Ejecuta la operación «enforceLimits» correspondiente a la responsabilidad de GrammarClassDiagramProjectionService. Entradas: nodes: dato de entrada necesario para la operación; edges: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void enforceLimits(List<GrammarClassDiagramNode> nodes,List<GrammarClassDiagramEdge> edges){
        if(nodes.size()>properties.getMaximumDiagramNodes())throw new IllegalArgumentException("Inferred diagram node limit exceeded");
        if(edges.size()>properties.getMaximumDiagramEdges())throw new IllegalArgumentException("Inferred diagram edge limit exceeded");
        long features=nodes.stream().mapToLong(node->node.features().size()).sum();
        if(features>properties.getMaximumDiagramFeatures())throw new IllegalArgumentException("Inferred diagram feature limit exceeded");
    }

    /** Descripción: Ejecuta la operación «sourceForRule» correspondiente a la responsabilidad de GrammarClassDiagramProjectionService. Entradas: analysis: dato de entrada necesario para la operación; rule: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static GrammarDiagramSourceRef sourceForRule(GrammarAnalysisResult analysis,String rule){
        List<GrammarSymbol> matches=analysis.symbols().stream().filter(item->item.kind().equals("RULE")&&item.name().equals(rule)).toList();
        if(matches.size()!=1)return GrammarDiagramSourceRef.empty();GrammarSymbol symbol=matches.getFirst();return new GrammarDiagramSourceRef(symbol.documentId(),symbol.range(),symbol.id(),rule);
    }
    /** Descripción: Ejecuta la operación «sourceForFeature» correspondiente a la responsabilidad de GrammarClassDiagramProjectionService. Entradas: analysis: dato de entrada necesario para la operación; owner: dato de entrada necesario para la operación; feature: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static GrammarDiagramSourceRef sourceForFeature(GrammarAnalysisResult analysis,GrammarDiagramSourceRef owner,String feature){
        if(owner.grammarElementId().isBlank())return owner;
        return analysis.symbols().stream().filter(item->item.kind().equals("ASSIGNMENT")&&item.parentId().equals(owner.grammarElementId())&&item.name().equals(feature)).findFirst()
                .map(item->new GrammarDiagramSourceRef(item.documentId(),item.range(),item.id(),owner.ruleName())).orElse(owner);
    }
    /** Descripción: Ejecuta la operación «diagnosticsFor» correspondiente a la responsabilidad de GrammarClassDiagramProjectionService. Entradas: analysis: dato de entrada necesario para la operación; rule: dato de entrada necesario para la operación; source: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static int diagnosticsFor(GrammarAnalysisResult analysis,String rule,GrammarDiagramSourceRef source){
        return (int)analysis.diagnostics().stream().filter(item->{
            if(!rule.isBlank()&&item.rule().equals(rule))return true;
            if(source==null||source.documentId().isBlank()||!source.documentId().equals(item.documentId()))return false;
            return rangesOverlap(source.range(),item.range());
        }).count();
    }
    /** Descripción: Ejecuta la operación «rangesOverlap» correspondiente a la responsabilidad de GrammarClassDiagramProjectionService. Entradas: left: dato de entrada necesario para la operación; right: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private static boolean rangesOverlap(TextRange left,TextRange right){
        if(left==null||right==null)return false;
        return compare(left.start(),right.end())<=0&&compare(right.start(),left.end())<=0;
    }
    /** Descripción: Ejecuta la operación «compare» correspondiente a la responsabilidad de GrammarClassDiagramProjectionService. Entradas: left: dato de entrada necesario para la operación; right: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static int compare(TextPosition left,TextPosition right){
        int line=Integer.compare(left.line(),right.line());
        return line!=0?line:Integer.compare(left.column(),right.column());
    }
    /** Descripción: Ejecuta la operación «confidence» correspondiente a la responsabilidad de GrammarClassDiagramProjectionService. Entradas: grammar: dato de entrada necesario para la operación; analysis: dato de entrada necesario para la operación; rule: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static double confidence(GrammarDefinitionModel grammar,GrammarAnalysisResult analysis,String rule){
        OptionalDouble value=analysis.semanticCandidate().bindings().stream().filter(binding->binding.semanticElementId().equals(rule)||binding.metadata().getOrDefault("sourceRule","").equals(rule)).mapToDouble(GrammarSemanticBinding::confidence).max();
        if(value.isPresent())return value.getAsDouble();return analysis.hasErrors()?0.55:(grammar.technology()==GrammarTechnology.ANTLR?0.62:0.78);
    }
    /** Descripción: Ejecuta la operación «typeName» correspondiente a la responsabilidad de GrammarClassDiagramProjectionService. Entradas: type: dato de entrada necesario para la operación; names: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String typeName(MetaTypeReference type,Map<String,String> names){if(type==null)return"";if(type.kind()==MetaTypeReferenceKind.EXTERNAL)return type.externalName();return names.getOrDefault(type.targetId(),type.targetId());}
    /** Descripción: Ejecuta la operación «multiplicity» correspondiente a la responsabilidad de GrammarClassDiagramProjectionService. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String multiplicity(Multiplicity value){if(value==null)return"";String upper=value.upperBound()==null?"*":Integer.toString(value.upperBound());return value.upperBound()!=null&&value.lowerBound()==value.upperBound()?"["+upper+"]":"["+value.lowerBound()+".."+upper+"]";}
    /** Descripción: Evalúa la condición «hash» sin modificar el estado externo. Entradas: nodes: dato de entrada necesario para la operación; edges: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private static String hash(List<GrammarClassDiagramNode> nodes,List<GrammarClassDiagramEdge> edges){
        StringBuilder b=new StringBuilder();
        nodes.stream().sorted(Comparator.comparing(GrammarClassDiagramNode::id)).forEach(n->{
            b.append("N\0").append(n.id()).append('\0').append(n.semanticId()).append('\0').append(n.name()).append('\0').append(n.kind()).append('\0')
                    .append(n.root()).append('\0').append(Double.toString(n.confidence())).append('\0').append(n.diagnosticCount()).append('\0');
            appendSource(b,n.source());appendMetadata(b,n.metadata());b.append('\n');
            n.features().stream().sorted(Comparator.comparing(GrammarDiagramFeature::id)).forEach(f->{
                b.append("F\0").append(f.id()).append('\0').append(f.name()).append('\0').append(f.type()).append('\0').append(f.multiplicity()).append('\0')
                        .append(f.kind()).append('\0').append(f.diagnosticCount()).append('\0');appendSource(b,f.source());b.append('\n');
            });
        });
        edges.stream().sorted(Comparator.comparing(GrammarClassDiagramEdge::id)).forEach(e->{
            b.append("E\0").append(e.id()).append('\0').append(e.sourceId()).append('\0').append(e.targetId()).append('\0').append(e.kind()).append('\0')
                    .append(e.label()).append('\0').append(e.sourceMultiplicity()).append('\0').append(e.targetMultiplicity()).append('\0').append(e.diagnosticCount()).append('\0');
            appendSource(b,e.source());appendMetadata(b,e.metadata());b.append('\n');
        });
        return GrammarHashing.sha256(b.toString());
    }
    /** Descripción: Ejecuta la operación «appendSource» correspondiente a la responsabilidad de GrammarClassDiagramProjectionService. Entradas: b: dato de entrada necesario para la operación; source: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private static void appendSource(StringBuilder b,GrammarDiagramSourceRef source){
        GrammarDiagramSourceRef value=source==null?GrammarDiagramSourceRef.empty():source;
        b.append(value.documentId()).append('\0').append(value.grammarElementId()).append('\0').append(value.ruleName()).append('\0')
                .append(value.range().start().line()).append(':').append(value.range().start().column()).append('-')
                .append(value.range().end().line()).append(':').append(value.range().end().column()).append('\0');
    }
    /** Descripción: Ejecuta la operación «appendMetadata» correspondiente a la responsabilidad de GrammarClassDiagramProjectionService. Entradas: b: dato de entrada necesario para la operación; metadata: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private static void appendMetadata(StringBuilder b,Map<String,String> metadata){
        if(metadata==null)return;
        metadata.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry->b.append(entry.getKey()).append('=').append(entry.getValue()).append('\0'));
    }
    /** Descripción: Ejecuta la operación «emptyAnalysis» correspondiente a la responsabilidad de GrammarClassDiagramProjectionService. Entradas: grammar: dato de entrada necesario para la operación; documents: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static GrammarAnalysisResult emptyAnalysis(GrammarDefinitionModel grammar,List<GrammarSourceDocument> documents){return new GrammarAnalysisResult(0,GrammarHashing.sources(documents),GrammarAnalysisMode.FAST,List.of(),List.of(),grammar.semanticBindings(),ProviderHealth.healthy("projection"),List.of(),Map.of());}
    /** Descripción: Ejecuta la operación «emptyMetamodel» correspondiente a la responsabilidad de GrammarClassDiagramProjectionService. Entradas: grammar: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static MetaModel emptyMetamodel(GrammarDefinitionModel grammar){return new MetaModel(MetaModel.CURRENT_SCHEMA_VERSION,"inferred:"+grammar.id(),grammar.name(),List.of(),List.of(),List.of(),List.of(),Map.of("sourceFormat",grammar.technology().name().toLowerCase(Locale.ROOT)));}
    /** Descripción: Ejecuta la operación «safeMessage» correspondiente a la responsabilidad de GrammarClassDiagramProjectionService. Entradas: exception: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String safeMessage(RuntimeException exception){String message=exception.getMessage();return message==null||message.isBlank()?exception.getClass().getSimpleName():message;}
    /** Descripción: Ejecuta la operación «safeComment» correspondiente a la responsabilidad de GrammarClassDiagramProjectionService. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String safeComment(String value){return value==null?"":value.replace("*/","* /");}
}
