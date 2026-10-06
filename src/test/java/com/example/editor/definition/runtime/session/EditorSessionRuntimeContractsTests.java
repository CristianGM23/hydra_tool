package com.example.editor.definition.runtime.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.example.editor.definition.runtime.session.diagram.EditorSessionDiagramDocument;
import com.example.editor.definition.runtime.session.diagram.EditorSessionDiagramEdgeView;
import com.example.editor.definition.runtime.session.diagram.EditorSessionDiagramNodeView;
import com.example.editor.definition.runtime.session.diagram.EditorSessionDiagramPoint;
import com.example.editor.definition.runtime.session.tool.EditorSessionToolExecutionRequest;
import com.example.editor.definition.runtime.session.tool.EditorSessionToolExecutionResponse;
import com.example.editor.validation.model.CanonicalModelDocument;

class EditorSessionRuntimeContractsTests {
    @Test void toolRequestNormalizesMissingValues(){var request=new EditorSessionToolExecutionRequest(null,null,null,null);assertThat(request.arguments()).isEmpty();}
    @Test void toolRequestRemovesDuplicateSelection(){var request=new EditorSessionToolExecutionRequest("representation:main","",List.of("element:one","element:one"),Map.of());assertThat(request.selectionElementIds()).containsExactly("element:one");}
    @Test void toolRequestCopiesArguments(){var values=new java.util.LinkedHashMap<String,Object>();values.put("name","Ada");var request=new EditorSessionToolExecutionRequest("representation:main","element:one",List.of(),values);values.clear();assertThat(request.arguments()).containsEntry("name","Ada");}
    @Test void diagramNodeSuppliesDefaultWidth(){assertThat(new EditorSessionDiagramNodeView("element:one",0,0,0,0).width()).isEqualTo(180);}
    @Test void diagramNodeSuppliesDefaultHeight(){assertThat(new EditorSessionDiagramNodeView("element:one",0,0,0,0).height()).isEqualTo(96);}
    @Test void diagramNodeRejectsBlankElementId(){assertThatThrownBy(()->new EditorSessionDiagramNodeView("",0,0,10,10)).isInstanceOf(IllegalArgumentException.class);}
    @Test void diagramNodeNormalizesRotation(){assertThat(new EditorSessionDiagramNodeView("element:one",0,0,180,96,-90).rotationDegrees()).isEqualTo(270);}
    @Test void diagramNodeWrapsLargeRotation(){assertThat(new EditorSessionDiagramNodeView("element:one",0,0,180,96,450).rotationDegrees()).isEqualTo(90);}
    @Test void diagramNodeDefaultsInvalidRotation(){assertThat(new EditorSessionDiagramNodeView("element:one",0,0,180,96,Double.NaN).rotationDegrees()).isZero();}
    @Test void diagramEdgeCopiesRoutingPoints(){var points=new java.util.ArrayList<>(List.of(new EditorSessionDiagramPoint(1,2)));var edge=new EditorSessionDiagramEdgeView("relation-instance:one",points);points.clear();assertThat(edge.points()).hasSize(1);}
    @Test void diagramRejectsUnsupportedSchema(){assertThatThrownBy(()->new EditorSessionDiagramDocument("2.0","session-one","people","people-editor","representation:main",List.of(),List.of())).isInstanceOf(IllegalArgumentException.class);}
    @Test void diagramCopiesNodeCollection(){var nodes=new java.util.ArrayList<>(List.of(new EditorSessionDiagramNodeView("element:one",0,0,180,96)));var diagram=new EditorSessionDiagramDocument("1.0","session-one","people","people-editor","representation:main",nodes,List.of());nodes.clear();assertThat(diagram.nodes()).hasSize(1);}
    @Test void executionResponseNormalizesMessages(){var response=new EditorSessionToolExecutionResponse(view(),null,null,null);assertThat(response.messages()).isEmpty();}

    private EditorSessionView view(){var model=new CanonicalModelDocument("1.0","model-one","people","","Model",List.of(),List.of(),List.of(),Map.of());var session=new EditorModelSession("1.0","session-one","people","people-editor","etag","representation:main",model,0,java.time.Instant.EPOCH,java.time.Instant.EPOCH,Map.of());return new EditorSessionView(session,"etag","Main",true,List.of(),List.of(),List.of(),null,false,false);}
}
