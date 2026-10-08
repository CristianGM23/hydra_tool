package com.example.editor.textual.roundtrip;

import java.util.List;
import java.util.Map;

import com.example.editor.metamodel.export.MetamodelExportFormat;
import com.example.editor.metamodel.roundtrip.SemanticFidelity;
import com.example.editor.metamodel.roundtrip.TextualSemanticIssue;
import com.example.editor.textual.source.TextualModelSourceDocument;
import com.example.editor.textual.source.TextualSemanticSourceMapEntry;
import com.example.editor.validation.model.CanonicalModelDocument;

/** Evidencia completa del round-trip de una instancia textual generada. */
public record TextualInstanceRoundTripResult(
        MetamodelExportFormat format,
        SemanticFidelity fidelity,
        List<TextualModelSourceDocument> documents,
        List<TextualInstanceDocumentDependency> dependencies,
        CanonicalModelDocument reconstructedModel,
        List<TextualSemanticSourceMapEntry> sourceMap,
        List<TextualSemanticIssue> issues,
        CanonicalModelSemanticReport equivalence,
        Map<String,String> metadata) {
    public TextualInstanceRoundTripResult {
        fidelity=fidelity==null?SemanticFidelity.UNAVAILABLE:fidelity;
        documents=documents==null?List.of():List.copyOf(documents);
        dependencies=dependencies==null?List.of():List.copyOf(dependencies);
        sourceMap=sourceMap==null?List.of():List.copyOf(sourceMap);
        issues=issues==null?List.of():List.copyOf(issues);
        equivalence=equivalence==null?new CanonicalModelSemanticReport(false,List.of("EQUIVALENCE_UNAVAILABLE")):equivalence;
        metadata=metadata==null?Map.of():Map.copyOf(metadata);
    }
    /** Descripción: Determina si el source map cubre todos los elementos, atributos presentes y relaciones reconstruidas, además de conservar metamodelo y regla gramatical en cada entrada. Entradas: no requiere parámetros. Salida: devuelve true únicamente cuando la cobertura semántica del modelo reconstruido es completa. */ public boolean bindingCoverageComplete(){if(reconstructedModel==null)return false;if(reconstructedModel.elements().isEmpty())return true;if(sourceMap.isEmpty()||sourceMap.stream().anyMatch(item->item.metamodelElementId().isBlank()||item.grammarRuleId().isBlank()))return false;java.util.Set<String> elementBindings=new java.util.LinkedHashSet<>(),featureBindings=new java.util.LinkedHashSet<>(),relationBindings=new java.util.LinkedHashSet<>();for(TextualSemanticSourceMapEntry item:sourceMap){if("MODEL_ELEMENT".equals(item.semanticKind()))elementBindings.add(item.semanticId());else if("MODEL_FEATURE".equals(item.semanticKind()))featureBindings.add(item.semanticId());else if("MODEL_RELATION".equals(item.semanticKind()))relationBindings.add(item.semanticId());}for(var element:reconstructedModel.elements()){if(!elementBindings.contains(element.id()))return false;for(String attributeId:element.attributes().keySet())if(!featureBindings.contains(element.id()+":"+attributeId))return false;}for(var relation:reconstructedModel.relations())if(!relationBindings.contains(relation.id()))return false;return true;}
    /** Descripción: Determina si el round-trip preservó completamente la semántica de instancia y no registró bloqueos nativos. Entradas: no requiere parámetros. Salida: devuelve true únicamente para equivalencia completa, bindings completos y fidelidad sin pérdida. */ public boolean lossless(){return fidelity.lossless()&&equivalence.equivalent()&&bindingCoverageComplete()&&issues.stream().noneMatch(TextualSemanticIssue::blocksNativeRuntime);}
}
