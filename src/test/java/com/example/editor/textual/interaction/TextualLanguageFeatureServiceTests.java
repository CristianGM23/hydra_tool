package com.example.editor.textual.interaction;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.example.editor.grammar.core.*;
import com.example.editor.textual.source.*;
import com.example.editor.validation.model.*;

class TextualLanguageFeatureServiceTests {
    private final TextualLanguageFeatureService service=new TextualLanguageFeatureService();

    @Test void publishesNeutralCapabilitiesWithoutPretendingNativeEmbedding(){var states=service.capabilities();assertEquals(TextualInteractionCapability.values().length,states.size());assertTrue(states.stream().allMatch(TextualInteractionCapabilityState::available));assertTrue(states.stream().allMatch(s->s.mode()==TextualInteractionExecutionMode.HYDRA_NEUTRAL));}
    @Test void completionReusesCanonicalTypesFeaturesAndElementIds(){var items=service.completion(workspace(),new TextualInteractionRequest("doc-a",0,10,"c",20));assertTrue(items.stream().anyMatch(i->i.label().equals("c1")));assertTrue(items.stream().anyMatch(i->i.label().equals("Customer")));}
    @Test void hoverUsesSmallestSemanticSourceMapEntry(){var hover=service.hover(workspace(),new TextualInteractionRequest("doc-a",1,8,"",20));assertTrue(hover.found());assertEquals("c1:name",hover.semanticId());assertEquals("MODEL_FEATURE",hover.semanticKind());}
    @Test void definitionAndReferencesCrossDocuments(){var definition=service.definition(workspace(),new TextualInteractionRequest("doc-a",2,9,"",20)).orElseThrow();assertEquals("doc-b",definition.documentId());assertEquals("c2",definition.semanticId());var refs=service.references(workspace(),new TextualInteractionRequest("doc-b",0,10,"",20));assertTrue(refs.stream().anyMatch(r->r.documentId().equals("doc-a")&&r.semanticKind().equals("MODEL_RELATION")));}
    @Test void definitionUsesReferenceTargetWhenCursorIsImmediatelyBeforeCrossDocumentToken(){var ws=workspace();var adjacent=service.definition(ws,new TextualInteractionRequest("doc-a",2,9,"",20)).orElseThrow();var exact=service.definition(ws,new TextualInteractionRequest("doc-a",2,10,"",20)).orElseThrow();assertEquals("doc-b",adjacent.documentId());assertEquals("c2",adjacent.semanticId());assertEquals(exact,adjacent);}
    @Test void renameIsPreviewFirstAndCoversDeclarationAndReferences(){var plan=service.rename(workspace(),new TextualRenameRequest("doc-a",0,10,"customer-1"));assertTrue(plan.safe(),plan.conflicts().toString());assertEquals("c1",plan.oldId());assertEquals(2,plan.edits().size());assertEquals(Set.of("doc-a","doc-b"),new HashSet<>(plan.affectedDocuments()));}
    @Test void renameFailsClosedForInvalidOrConflictingIdentity(){var invalid=service.rename(workspace(),new TextualRenameRequest("doc-a",0,10,"../bad"));assertFalse(invalid.safe());var conflict=service.rename(workspace(),new TextualRenameRequest("doc-a",0,10,"c2"));assertFalse(conflict.safe());}
    @Test void formatSymbolsFoldingAndTokensShareOneIndex(){var workspace=workspace();assertFalse(service.format(workspace,"doc-a").isEmpty());assertEquals(1,service.documentSymbols(workspace,"doc-a").size());assertEquals(1,service.folding(workspace,"doc-a").size());assertTrue(service.semanticTokens(workspace,"doc-a").stream().anyMatch(t->t.tokenType().equals("reference")));}
    @Test void invalidationIsTransitiveAcrossCrossDocumentReferences(){var plan=service.invalidation(workspace(),List.of("doc-a"));assertEquals(Set.of("doc-a","doc-b"),new LinkedHashSet<>(plan.impactedDocuments()));assertTrue(plan.fullReparseRequired());}
    @Test void snapshotCompatibilityDistinguishesExactReparseAndMigration(){var exact=service.snapshotCompatibility(new TextualSnapshotCompatibilityRequest("s1","s1","g","g","h","h","xtext","xtext"));assertTrue(exact.reusable());var reparse=service.snapshotCompatibility(new TextualSnapshotCompatibilityRequest("s1","s2","g","g","h","h","xtext","langium"));assertTrue(reparse.reparseRequired());assertFalse(reparse.migrationRequired());var migration=service.snapshotCompatibility(new TextualSnapshotCompatibilityRequest("s1","s2","g","g","h1","h2","xtext","xtext"));assertTrue(migration.migrationRequired());}
    @Test void operationsAreBoundedAndDeterministic(){var workspace=workspace();var first=service.semanticTokens(workspace,"doc-a");var second=service.semanticTokens(workspace,"doc-a");assertEquals(first,second);assertTrue(first.size()<=5000);assertEquals(service.completion(workspace,new TextualInteractionRequest("doc-a",0,0,"",2)),service.completion(workspace,new TextualInteractionRequest("doc-a",0,0,"",2)));}

    private static TextualWorkspaceIndex workspace(){
        String a="Customer c1 {  \n  name = \"Ada\"  \n  owner = c2  \n}\n";
        String b="Customer c2 {\n  name = \"Bob\"\n  owner = c1\n}\n";
        var docs=List.of(new TextualModelSourceDocument("doc-a","a.dsl",a,7),new TextualModelSourceDocument("doc-b","b.dsl",b,7));
        var elements=List.of(new CanonicalModelElement("c1","Customer",Map.of("name","Ada"),Map.of()),new CanonicalModelElement("c2","Customer",Map.of("name","Bob"),Map.of()));
        var relations=List.of(new CanonicalModelRelation("rel1","owner","c1","c2",Map.of()),new CanonicalModelRelation("rel2","owner","c2","c1",Map.of()));
        var model=new CanonicalModelDocument(null,"model","meta","etag","demo",List.of("c1","c2"),elements,relations,Map.of());
        var map=List.of(
            entry("c1","MODEL_ELEMENT","doc-a",0,0,3,1,"Customer",Map.of()),
            entry("c1:name","MODEL_FEATURE","doc-a",1,2,1,14,"name",Map.of("ownerId","c1","property","name")),
            entry("rel1","MODEL_RELATION","doc-a",2,10,2,12,"owner",Map.of("sourceElementId","c1","targetElementId","c2","property","owner")),
            entry("c2","MODEL_ELEMENT","doc-b",0,0,3,1,"Customer",Map.of()),
            entry("c2:name","MODEL_FEATURE","doc-b",1,2,1,14,"name",Map.of("ownerId","c2","property","name")),
            entry("rel2","MODEL_RELATION","doc-b",2,10,2,12,"owner",Map.of("sourceElementId","c2","targetElementId","c1","property","owner"))
        );
        return new TextualWorkspaceIndex(7,docs,model,map);
    }
    private static TextualSemanticSourceMapEntry entry(String id,String kind,String doc,int sl,int sc,int el,int ec,String meta,Map<String,String>metadata){return new TextualSemanticSourceMapEntry(id,kind,doc,new TextRange(new TextPosition(sl,sc),new TextPosition(el,ec)),meta,"Customer",metadata);}
}
