package com.example.editor.architecture;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Contratos estáticos de separación de responsabilidades en diseñadores y runtime. */
class DesignerArchitectureContractTests {

    @Test
    void eventFlowRuntimeDelegatesStateAndNodeSemanticsWithoutChangingItsPublicOrchestrator() throws Exception {
        String runtime = source("src/main/java/com/example/editor/event/flow/runtime/EventFlowRuntimeService.java");
        assertThat(runtime).contains("EventFlowRuntimeNodeExecutor nodeExecutor");
        assertThat(runtime).contains("EventFlowRuntimeDeployment");
        assertThat(runtime).doesNotContain("private static final class RuntimeState");
        assertThat(Files.isRegularFile(Path.of("src/main/java/com/example/editor/event/flow/runtime/EventFlowRuntimeState.java"))).isTrue();
        assertThat(Files.isRegularFile(Path.of("src/main/java/com/example/editor/event/flow/runtime/EventFlowRuntimeNodeExecutor.java"))).isTrue();
    }

    @Test
    void largeFrontendDesignersKeepTheirExtractedPartsAndReviewedTopLevelViews() throws Exception {
        String events = source("frontend/src/event-designer/EventDesigner.tsx");
        String flows = source("frontend/src/event-designer/flow/EventFlowDesigner.tsx");
        String gamification = source("frontend/src/gamification-designer/GamificationDesigner.tsx");
        String editors = source("frontend/src/editor-definition/EditorDefinitionDesigner.tsx");
        String workbench = source("frontend/src/application-shell/IndependentCapabilityWorkbench.tsx");

        assertThat(events).contains("EventDesignerParts", "'FORM'", "'STRUCTURE'", "'ARCHITECTURE'", "'SOURCE'");
        assertThat(events).doesNotContain("'FLOWS'", "from './flow/EventFlowDesigner'", "<EventFlowDesigner");
        assertThat(workbench).contains("moduleId === 'EVENT_FLOWS'", "IndependentEventFlowWorkbench", "LazyEventFlowDesigner");
        assertThat(flows).contains("EventFlowDesignerParts", "'FORM'", "'STRUCTURE'", "'DIAGRAM'", "'SOURCE'", "'RUNTIME'");
        assertThat(gamification).contains("GamificationDesignerParts", "'GUIDED'", "'FORM'", "'STRUCTURE'", "'DIAGRAM'", "'SOURCE'", "'LEDGER'");
        assertThat(editors).contains("EditorDefinitionDesignerParts", "'GUIDED'", "'DESIGN'", "'STRUCTURE'", "'RUNTIME'");
    }

    @Test
    void textualAndEmfaticImportersDelegateParsingToFocusedComponents() throws Exception {
        String textual = source("src/main/java/com/example/editor/metamodel/importing/support/TextualGrammarImportParser.java");
        String emfatic = source("src/main/java/com/example/editor/metamodel/importing/importers/EmfaticMetamodelImporter.java");

        assertThat(textual).contains("TextualGrammarScanner", "TextualGrammarSyntaxSupport", "TextualGrammarModelSupport");
        assertThat(emfatic).contains("new EmfaticSyntaxParser(text, source.fileName()).parse()");
        assertThat(Files.isRegularFile(Path.of("src/main/java/com/example/editor/metamodel/importing/importers/EmfaticSyntaxParser.java"))).isTrue();
    }

    @Test
    void ecoreAdapterKeepsThePublicBoundaryAndDelegatesProjectionDirections() throws Exception {
        String adapter = source("src/main/java/com/example/editor/metamodel/adapter/ecore/DefaultEcoreMetamodelAdapter.java");
        assertThat(adapter).contains("EcoreProjectionWriter projectionWriter", "EcoreProjectionReader projectionReader");
        assertThat(adapter).contains("projectionWriter.write(metamodel, ePackage, effective, warnings)");
        assertThat(adapter).contains("projectionReader.read(ePackage, artifactName)");
        assertThat(adapter).contains("public TransformationResult<EPackage> toEPackage", "public TransformationResult<MetaModel> fromEPackage");
    }

    private String source(String path) throws Exception {
        return Files.readString(Path.of(path));
    }
}
