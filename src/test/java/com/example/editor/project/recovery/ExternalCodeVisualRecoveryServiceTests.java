package com.example.editor.project.recovery;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.example.editor.metamodel.core.MetaClass;
import com.example.editor.metamodel.core.MetaClassKind;
import com.example.editor.metamodel.core.MetaModel;

import tools.jackson.databind.json.JsonMapper;

class ExternalCodeVisualRecoveryServiceTests {
    private final ExternalCodeVisualRecoveryService service = new ExternalCodeVisualRecoveryService(JsonMapper.builder().build());

    @Test
    void recoversOnlyExplicitGlspSemanticTypesWithoutExecutingCode() throws Exception {
        MetaModel metamodel = model();
        String source = "import '@eclipse-glsp/client';\nexport const nodeTypes=['Task','Decision','Unknown'];\nexport const diagramType='workflow';";
        var result = service.recover(metamodel, List.of(entry("workflow-glsp/client/src/config.ts", source)));
        assertThat(result).hasSize(1);
        var editor = result.get(0).editor();
        assertThat(result.get(0).technology()).isEqualTo("ECLIPSE_GLSP");
        assertThat(editor.representations()).hasSize(1);
        assertThat(editor.representations().get(0).nodeMappings()).extracting(value -> value.representationName())
                .containsExactlyInAnyOrder("Task", "Decision");
        assertThat(editor.annotations()).containsEntry("hydra.import.reviewRequired", "true");
    }

    @Test
    void refusesToInventMappingsWhenGlspTypesDoNotMapToMetamodelClasses() throws Exception {
        var result = service.recover(model(), List.of(entry("server/glsp-config.ts",
                "import '@eclipse-glsp/server-node'; const nodeTypes=['visual:rounded-task'];")));
        assertThat(result).isEmpty();
    }

    private static MetaModel model() {
        MetaClass task = new MetaClass("class:task","Task", MetaClassKind.CLASS,List.of(),List.of(),Map.of());
        MetaClass decision = new MetaClass("class:decision","Decision", MetaClassKind.CLASS,List.of(),List.of(),Map.of());
        return new MetaModel("2.1","workflow","Workflow",List.of(task,decision),List.of(),List.of(),List.of(),Map.of());
    }

    private static ExternalProjectArchiveEntry entry(String path,String text) throws Exception {
        byte[] bytes=text.getBytes(StandardCharsets.UTF_8);
        String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        return new ExternalProjectArchiveEntry(path,bytes,bytes.length,hash);
    }
}
