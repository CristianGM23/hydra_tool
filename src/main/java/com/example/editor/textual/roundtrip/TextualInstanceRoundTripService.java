package com.example.editor.textual.roundtrip;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.export.MetamodelExportFormat;
import com.example.editor.metamodel.roundtrip.SemanticFidelity;
import com.example.editor.metamodel.roundtrip.TextualSemanticIssue;
import com.example.editor.validation.model.CanonicalModelDocument;

/** Orquesta validación, serialización, relectura y equivalencia de instancias para Xtext/Langium. */
@Service
public class TextualInstanceRoundTripService {
    private final GeneratedTextualInstanceCodec codec;
    private final CanonicalModelSemanticComparator comparator;

    /** Descripción: Inicializa el servicio con el codec de sintaxis generada y el comparador canónico. Entradas: codec y comparator. Salida: deja el servicio preparado para ejecutar round-trips deterministas. */ public TextualInstanceRoundTripService(GeneratedTextualInstanceCodec codec,CanonicalModelSemanticComparator comparator){this.codec=codec;this.comparator=comparator;}

    /** Descripción: Ejecuta el round-trip completo de una instancia, incluyendo invariantes, multiarchivo, referencias diferidas, source maps y comparación semántica. Entradas: metamodel, source, format y strategy. Salida: devuelve evidencia y fidelidad sin ocultar limitaciones nativas. */
    public TextualInstanceRoundTripResult verify(MetaModel metamodel,CanonicalModelDocument source,MetamodelExportFormat format,TextualInstanceDocumentStrategy strategy){
        CanonicalModelInvariantReport invariants=comparator.validate(metamodel,source);List<TextualSemanticIssue> issues=new ArrayList<>();
        if(!invariants.valid()){for(String violation:invariants.violations())issues.add(new TextualSemanticIssue("CANONICAL_MODEL_INVARIANT",violation,violation,true));return unavailable(format,issues,Map.of("phase","SOURCE_INVARIANTS"));}
        GeneratedTextualInstanceCodec.EncodeResult encoded=codec.encode(metamodel,source,format,strategy,1L);issues.addAll(encoded.issues());if(!encoded.representable())return unavailable(format,issues,merge(encoded.metadata(),Map.of("phase","ENCODE")));
        GeneratedTextualInstanceCodec.DecodeResult decoded=codec.decode(metamodel,encoded.documents(),format,source.id(),source.metamodelEtag(),source.name());issues.addAll(decoded.issues());if(!decoded.valid())return new TextualInstanceRoundTripResult(format,SemanticFidelity.LOSSY,encoded.documents(),encoded.dependencies(),decoded.model(),decoded.sourceMap(),issues,new CanonicalModelSemanticReport(false,List.of("DECODE_FAILED")),merge(encoded.metadata(),decoded.metadata()));
        if(!new java.util.LinkedHashSet<>(encoded.dependencies()).equals(new java.util.LinkedHashSet<>(decoded.dependencies())))issues.add(new TextualSemanticIssue("INSTANCE_DOCUMENT_DEPENDENCY_MISMATCH","$.documents","Cross-document WORKSPACE_SCOPE dependency plan changed during reconstruction",true));
        CanonicalModelInvariantReport reconstructedInvariants=comparator.validate(metamodel,decoded.model());if(!reconstructedInvariants.valid())for(String violation:reconstructedInvariants.violations())issues.add(new TextualSemanticIssue("RECONSTRUCTED_MODEL_INVARIANT",violation,violation,true));
        long incompleteBindings=decoded.sourceMap().stream().filter(item->item.metamodelElementId().isBlank()||item.grammarRuleId().isBlank()).count();
        if(incompleteBindings>0)issues.add(new TextualSemanticIssue("INSTANCE_BINDING_COVERAGE_INCOMPLETE","$.sourceMap","Semantic source map contains "+incompleteBindings+" entries without metamodel/rule binding",true));
        CanonicalModelSemanticReport equivalence=comparator.compare(metamodel,source,decoded.model());SemanticFidelity fidelity=issues.stream().anyMatch(TextualSemanticIssue::blocksNativeRuntime)||!equivalence.equivalent()?SemanticFidelity.LOSSY:SemanticFidelity.EXACT;
        Map<String,String> metadata=merge(encoded.metadata(),decoded.metadata());metadata=new LinkedHashMap<>(metadata);metadata.put("sourceInvariantCount",Integer.toString(invariants.violations().size()));metadata.put("reconstructedInvariantCount",Integer.toString(reconstructedInvariants.violations().size()));metadata.put("semanticDifferenceCount",Integer.toString(equivalence.differences().size()));metadata.put("bindingCoverageIncomplete",Long.toString(incompleteBindings));metadata.put("documentDependencyCount",Integer.toString(encoded.dependencies().size()));
        return new TextualInstanceRoundTripResult(format,fidelity,encoded.documents(),encoded.dependencies(),decoded.model(),decoded.sourceMap(),issues,equivalence,metadata);
    }

    /** Descripción: Exige fidelidad exacta y falla de forma cerrada cuando la identidad, contención, referencias o source maps no pueden reconstruirse con seguridad. Entradas: metamodel, source, format y strategy. Salida: devuelve el resultado únicamente cuando es lossless. */ public TextualInstanceRoundTripResult requireLossless(MetaModel metamodel,CanonicalModelDocument source,MetamodelExportFormat format,TextualInstanceDocumentStrategy strategy){TextualInstanceRoundTripResult result=verify(metamodel,source,format,strategy);if(!result.lossless())throw new IllegalStateException("Textual instance round-trip is not lossless for "+format+": issues="+result.issues()+" differences="+result.equivalence().differences());return result;}
    /** Descripción: Construye un resultado no disponible conservando incidencias y metadatos de la fase que bloqueó el recorrido. Entradas: format, issues y metadata. Salida: devuelve evidencia fail-closed sin modelo reconstruido. */ private static TextualInstanceRoundTripResult unavailable(MetamodelExportFormat format,List<TextualSemanticIssue> issues,Map<String,String> metadata){return new TextualInstanceRoundTripResult(format,SemanticFidelity.UNAVAILABLE,List.of(),List.of(),null,List.of(),issues,new CanonicalModelSemanticReport(false,List.of("ROUND_TRIP_UNAVAILABLE")),metadata);}
    /** Descripción: Combina metadatos preservando el orden y dando prioridad a la segunda fuente para claves repetidas. Entradas: first y second. Salida: devuelve un mapa inmutable combinado. */ private static Map<String,String> merge(Map<String,String> first,Map<String,String> second){Map<String,String> result=new LinkedHashMap<>();if(first!=null)result.putAll(first);if(second!=null)result.putAll(second);return Map.copyOf(result);}
}
