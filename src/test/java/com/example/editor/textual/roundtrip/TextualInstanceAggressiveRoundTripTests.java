package com.example.editor.textual.roundtrip;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.example.editor.metamodel.core.MetaAttribute;
import com.example.editor.metamodel.core.MetaClass;
import com.example.editor.metamodel.core.MetaClassKind;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.core.MetaPrimitiveType;
import com.example.editor.metamodel.core.MetaRelation;
import com.example.editor.metamodel.core.MetaRelationKind;
import com.example.editor.metamodel.core.MetaTypeReference;
import com.example.editor.metamodel.core.Multiplicity;
import com.example.editor.metamodel.export.MetamodelExportFormat;
import com.example.editor.validation.model.CanonicalModelDocument;
import com.example.editor.validation.model.CanonicalModelElement;
import com.example.editor.validation.model.CanonicalModelRelation;

class TextualInstanceAggressiveRoundTripTests {
    @Test
    void remainsDeterministicAcrossManyRootsCrossDocumentCyclesAndRepeatedRuns(){
        MetaAttribute name=new MetaAttribute("node.name","name",MetaTypeReference.primitive(MetaPrimitiveType.STRING),Multiplicity.one(),false,true,"",Map.of());
        MetaClass node=new MetaClass("Node","Node",MetaClassKind.CLASS,List.of(name),List.of(),Map.of("root","true"));
        MetaRelation next=new MetaRelation("node.next","next","Node","Node",MetaRelationKind.REFERENCE,Multiplicity.one(),Multiplicity.optional(),"",false,true,Map.of());
        MetaModel metamodel=new MetaModel(null,"stress.mm","Stress",List.of(node),List.of(),List.of(),List.of(next),Map.of("rootClassId","Node"));
        List<CanonicalModelElement> elements=new ArrayList<>();List<CanonicalModelRelation> relations=new ArrayList<>();List<String> roots=new ArrayList<>();
        for(int index=0;index<40;index++){String id="n"+index;roots.add(id);elements.add(new CanonicalModelElement(id,"Node",Map.of("node.name",id),Map.of()));relations.add(new CanonicalModelRelation("r"+index,"node.next",id,"n"+((index+1)%40),Map.of()));}
        CanonicalModelDocument model=new CanonicalModelDocument(null,"stress.model","stress.mm","","Stress",roots,elements,relations,Map.of());
        CanonicalModelSemanticComparator comparator=new CanonicalModelSemanticComparator();TextualInstanceRoundTripService service=new TextualInstanceRoundTripService(new GeneratedTextualInstanceCodec(),comparator);
        for(MetamodelExportFormat format:List.of(MetamodelExportFormat.XTEXT,MetamodelExportFormat.LANGIUM)){
            var first=service.requireLossless(metamodel,model,format,TextualInstanceDocumentStrategy.ONE_DOCUMENT_PER_ROOT);
            var second=service.requireLossless(metamodel,model,format,TextualInstanceDocumentStrategy.ONE_DOCUMENT_PER_ROOT);
            assertThat(first.documents()).hasSize(40);
            assertThat(first.documents().stream().map(item->item.content()).toList()).containsExactlyElementsOf(second.documents().stream().map(item->item.content()).toList());
            assertThat(first.metadata()).containsEntry("crossDocumentReferences","40").containsEntry("requiresWorkspaceIndex","true");
            assertThat(first.dependencies()).hasSize(40);
            assertThat(first.dependencies()).containsExactlyElementsOf(second.dependencies());
            assertThat(first.sourceMap()).hasSize(second.sourceMap().size());
            assertThat(comparator.compare(metamodel,first.reconstructedModel(),second.reconstructedModel()).equivalent()).isTrue();
        }
        TextualInstanceProviderParityService parity=new TextualInstanceProviderParityService(service,comparator);
        assertThat(parity.requireSemanticParity(metamodel,model,TextualInstanceDocumentStrategy.ONE_DOCUMENT_PER_ROOT).semanticallyAligned()).isTrue();
    }
}
