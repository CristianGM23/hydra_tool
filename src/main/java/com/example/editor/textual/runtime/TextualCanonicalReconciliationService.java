package com.example.editor.textual.runtime;

import java.util.*;
import org.springframework.stereotype.Service;
import com.example.editor.grammar.diagnostic.SourceTextIndex;
import com.example.editor.grammar.runtime.GrammarCompilationSnapshot;
import com.example.editor.textual.source.*;
import com.example.editor.validation.model.*;

/**
 * Safely projects canonical changes back to textual source.
 *
 * Only changes backed by an unambiguous source range are writable. Structural additions/removals,
 * class changes and cardinality changes are deliberately rejected instead of guessing a textual form.
 * Every candidate edit is reparsed and must be semantically equivalent to the requested canonical
 * document before it can be committed by the session service.
 */
@Service
public class TextualCanonicalReconciliationService {
    private final NeutralTextualModelParser parser = new NeutralTextualModelParser();

    /** Descripción: Ejecuta la operación «reconcile» correspondiente a la responsabilidad de TextualCanonicalReconciliationService. Entradas: nextRevision: dato de entrada necesario para la operación; before: dato de entrada necesario para la operación; candidate: dato de entrada necesario para la operación; documents: dato de entrada necesario para la operación; sourceMap: dato de entrada necesario para la operación; snapshot: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public TextualReconciliationResult reconcile(long nextRevision, CanonicalModelDocument before,
            CanonicalModelDocument candidate, List<TextualModelSourceDocument> documents,
            List<TextualSemanticSourceMapEntry> sourceMap, GrammarCompilationSnapshot snapshot) {
        if(before == null || candidate == null) return TextualReconciliationResult.unsafe("A valid canonical source and candidate are required");
        if(!Objects.equals(before.metamodelId(), candidate.metamodelId())) return TextualReconciliationResult.unsafe("The quick fix changes the metamodel identity");
        if(!new LinkedHashSet<>(before.rootElementIds()).equals(new LinkedHashSet<>(candidate.rootElementIds()))) return TextualReconciliationResult.unsafe("Root structure changes cannot be reconciled safely");

        Map<String, CanonicalModelElement> oldElements = elements(before);
        Map<String, CanonicalModelElement> newElements = elements(candidate);
        if(!oldElements.keySet().equals(newElements.keySet())) return TextualReconciliationResult.unsafe("Element creation/deletion requires an explicit textual serializer");

        List<TextualReconciliationEdit> edits = new ArrayList<>();
        for(String id : oldElements.keySet()) {
            CanonicalModelElement oldElement = oldElements.get(id), newElement = newElements.get(id);
            if(!oldElement.classId().equals(newElement.classId())) return TextualReconciliationResult.unsafe("Element type changes cannot be reconciled safely: " + id);
            LinkedHashSet<String> features = new LinkedHashSet<>();
            features.addAll(oldElement.attributes().keySet());
            features.addAll(newElement.attributes().keySet());
            for(String feature : features) {
                boolean oldPresent = oldElement.attributes().containsKey(feature), newPresent = newElement.attributes().containsKey(feature);
                if(oldPresent != newPresent) return TextualReconciliationResult.unsafe("Attribute creation/removal has no unambiguous source range: " + id + "." + feature);
                Object oldValue = oldElement.attributes().get(feature), newValue = newElement.attributes().get(feature);
                if(Objects.equals(oldValue, newValue)) continue;
                Optional<TextualSemanticSourceMapEntry> trace = sourceMap.stream().filter(item -> item.semanticKind().equals("MODEL_FEATURE")
                        && item.semanticId().equals(id + ":" + feature)).findFirst();
                if(trace.isEmpty()) return TextualReconciliationResult.unsafe("Missing source map for attribute: " + id + "." + feature);
                String serialized = serialize(newValue);
                if(serialized == null) return TextualReconciliationResult.unsafe("Unsupported textual value type for attribute: " + id + "." + feature);
                edits.add(new TextualReconciliationEdit(trace.get().documentId(), trace.get().range(), serialized, id + ":" + feature, "ATTRIBUTE_VALUE"));
            }
        }

        Map<RelationKey,List<CanonicalModelRelation>> oldRelations = relations(before);
        Map<RelationKey,List<CanonicalModelRelation>> newRelations = relations(candidate);
        LinkedHashSet<RelationKey> relationKeys = new LinkedHashSet<>(); relationKeys.addAll(oldRelations.keySet()); relationKeys.addAll(newRelations.keySet());
        for(RelationKey key : relationKeys) {
            List<CanonicalModelRelation> oldValues = oldRelations.getOrDefault(key, List.of());
            List<CanonicalModelRelation> newValues = newRelations.getOrDefault(key, List.of());
            List<String> oldTargets = oldValues.stream().map(CanonicalModelRelation::targetElementId).sorted().toList();
            List<String> newTargets = newValues.stream().map(CanonicalModelRelation::targetElementId).sorted().toList();
            if(oldTargets.equals(newTargets)) continue;
            if(oldValues.size() != 1 || newValues.size() != 1) return TextualReconciliationResult.unsafe("Reference cardinality changes cannot be reconciled safely: " + key.relationId());
            CanonicalModelRelation oldRelation = oldValues.get(0), newRelation = newValues.get(0);
            Optional<TextualSemanticSourceMapEntry> trace = sourceMap.stream().filter(item -> item.semanticKind().equals("MODEL_RELATION")
                    && item.metadata().getOrDefault("sourceElementId", "").equals(key.sourceId())
                    && (item.metamodelElementId().equals(key.relationId()) || item.metadata().getOrDefault("property", "").equals(key.relationId()))
                    && item.metadata().getOrDefault("targetElementId", "").equals(oldRelation.targetElementId())).findFirst();
            if(trace.isEmpty()) return TextualReconciliationResult.unsafe("Missing source map for reference: " + key.sourceId() + "." + key.relationId());
            edits.add(new TextualReconciliationEdit(trace.get().documentId(), trace.get().range(), newRelation.targetElementId(),
                    oldRelation.id(), "REFERENCE_TARGET"));
        }

        List<TextualModelSourceDocument> updated;
        try { updated = apply(documents, edits, nextRevision); }
        catch(IllegalArgumentException failure) { return TextualReconciliationResult.unsafe(failure.getMessage()); }
        TextualParseResult parsed = parser.parse(nextRevision, updated, snapshot);
        if(!parsed.valid()) return TextualReconciliationResult.unsafe("Reconciled text does not parse successfully");
        if(!semanticallyEquivalent(candidate, parsed.semanticModel())) return TextualReconciliationResult.unsafe("Reparsed text is not semantically equivalent to the quick-fix candidate");
        return new TextualReconciliationResult(true, "", updated, edits, parsed);
    }

    /** Descripción: Ejecuta la operación «semanticallyEquivalent» correspondiente a la responsabilidad de TextualCanonicalReconciliationService. Entradas: expected: dato de entrada necesario para la operación; actual: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean semanticallyEquivalent(CanonicalModelDocument expected, CanonicalModelDocument actual) {
        if(expected == null || actual == null || !Objects.equals(expected.metamodelId(), actual.metamodelId())) return false;
        if(!new LinkedHashSet<>(expected.rootElementIds()).equals(new LinkedHashSet<>(actual.rootElementIds()))) return false;
        Map<String,CanonicalModelElement> a=elements(expected), b=elements(actual);
        if(!a.keySet().equals(b.keySet())) return false;
        for(String id:a.keySet()) if(!a.get(id).classId().equals(b.get(id).classId()) || !a.get(id).attributes().equals(b.get(id).attributes())) return false;
        return relationTuples(expected).equals(relationTuples(actual));
    }

    /** Descripción: Actualiza el estado gestionado por TextualCanonicalReconciliationService mediante la operación «apply». Entradas: documents: dato de entrada necesario para la operación; edits: dato de entrada necesario para la operación; revision: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static List<TextualModelSourceDocument> apply(List<TextualModelSourceDocument> documents,
            List<TextualReconciliationEdit> edits, long revision) {
        Map<String,TextualModelSourceDocument> byId=new LinkedHashMap<>();
        for(TextualModelSourceDocument document:documents) byId.put(document.id(),document);
        Map<String,List<OffsetEdit>> grouped=new LinkedHashMap<>();
        for(TextualReconciliationEdit edit:edits){
            TextualModelSourceDocument document=byId.get(edit.documentId());
            if(document==null) throw new IllegalArgumentException("Source-map document no longer exists: "+edit.documentId());
            SourceTextIndex index=new SourceTextIndex(document.content());
            int start=index.offset(edit.range().start()), end=index.offset(edit.range().end());
            if(end<start) throw new IllegalArgumentException("Invalid reconciliation range for "+edit.semanticId());
            grouped.computeIfAbsent(edit.documentId(), ignored->new ArrayList<>()).add(new OffsetEdit(start,end,edit.replacement(),edit.semanticId()));
        }
        List<TextualModelSourceDocument> result=new ArrayList<>();
        for(TextualModelSourceDocument document:documents){
            List<OffsetEdit> values=grouped.getOrDefault(document.id(),List.of()).stream().sorted(Comparator.comparingInt(OffsetEdit::start).reversed()).toList();
            int previousStart=document.content().length()+1; StringBuilder text=new StringBuilder(document.content());
            for(OffsetEdit edit:values){
                if(edit.end()>previousStart) throw new IllegalArgumentException("Overlapping reconciliation edits are unsafe");
                if(edit.start()<0||edit.end()>text.length()) throw new IllegalArgumentException("Reconciliation range is outside the current document");
                text.replace(edit.start(),edit.end(),edit.replacement()); previousStart=edit.start();
            }
            result.add(new TextualModelSourceDocument(document.id(),document.relativePath(),text.toString(),revision));
        }
        return List.copyOf(result);
    }

    /** Descripción: Ejecuta la operación «serialize» correspondiente a la responsabilidad de TextualCanonicalReconciliationService. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String serialize(Object value){
        if(value instanceof String text) return "\""+text.replace("\\","\\\\").replace("\"","\\\"")+"\"";
        if(value instanceof Boolean || value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long || value instanceof Float || value instanceof Double) return String.valueOf(value);
        return null;
    }
    /** Descripción: Ejecuta la operación «elements» correspondiente a la responsabilidad de TextualCanonicalReconciliationService. Entradas: model: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static Map<String,CanonicalModelElement> elements(CanonicalModelDocument model){Map<String,CanonicalModelElement> result=new LinkedHashMap<>();model.elements().forEach(item->result.put(item.id(),item));return result;}
    /** Descripción: Ejecuta la operación «relations» correspondiente a la responsabilidad de TextualCanonicalReconciliationService. Entradas: model: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static Map<RelationKey,List<CanonicalModelRelation>> relations(CanonicalModelDocument model){Map<RelationKey,List<CanonicalModelRelation>> result=new LinkedHashMap<>();for(CanonicalModelRelation relation:model.relations())result.computeIfAbsent(new RelationKey(relation.sourceElementId(),relation.relationId()),ignored->new ArrayList<>()).add(relation);return result;}
    /** Descripción: Ejecuta la operación «relationTuples» correspondiente a la responsabilidad de TextualCanonicalReconciliationService. Entradas: model: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static Set<String> relationTuples(CanonicalModelDocument model){Set<String> result=new LinkedHashSet<>();model.relations().forEach(r->result.add(r.relationId()+"\u0000"+r.sourceElementId()+"\u0000"+r.targetElementId()));return result;}
    private record RelationKey(String sourceId,String relationId){}
    private record OffsetEdit(int start,int end,String replacement,String semanticId){}
}
