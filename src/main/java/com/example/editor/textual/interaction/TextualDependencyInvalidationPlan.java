package com.example.editor.textual.interaction;

import java.util.List;
import java.util.Map;

/** Cierre transitivo de documentos que deben reanalizarse tras cambios textuales. */
public record TextualDependencyInvalidationPlan(List<String> changedDocuments,List<String> impactedDocuments,List<String> unaffectedDocuments,boolean fullReparseRequired,Map<String,String> metadata){
    public TextualDependencyInvalidationPlan{changedDocuments=changedDocuments==null?List.of():List.copyOf(changedDocuments);impactedDocuments=impactedDocuments==null?List.of():List.copyOf(impactedDocuments);unaffectedDocuments=unaffectedDocuments==null?List.of():List.copyOf(unaffectedDocuments);metadata=metadata==null?Map.of():Map.copyOf(metadata);}
}
