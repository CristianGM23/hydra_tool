package com.example.editor.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Guardrails estáticos de robustez para importación, persistencia y runtime. */
class RuntimeAndPersistenceHardeningTests {
    @Test void preparedImportReusesCompletedAnalysisInsteadOfReuploading(){
        String controller = text("src/main/java/com/example/editor/metamodel/importing/MetamodelImportController.java");
        String service = text("src/main/java/com/example/editor/metamodel/importing/MetamodelImportService.java");
        String frontend = text("frontend/src/metamodel-designer/MetamodelImportDialog.tsx");
        assertThat(controller).contains("/analysis-jobs/{jobId}/import", "analysisJobService.prepared(jobId)");
        assertThat(service).contains("importPrepared(MetamodelImportPreview preview");
        assertThat(frontend).contains("importPreparedMetamodel", "analysisJob.status === 'COMPLETED'");
    }

    @Test void resourceScopeExistsWithoutStartingUsersOrMongo(){
        String scope = text("src/main/java/com/example/editor/scope/ResourceScope.java");
        String context = text("src/main/java/com/example/editor/scope/WorkspaceContext.java");
        String factory = text("src/main/java/com/example/editor/persistence/CanonicalDocumentStoreFactory.java");
        assertThat(scope).contains("resourceKey", "workspaceId"); assertThat(context).contains("currentScope");
        assertThat(factory).contains("create(CanonicalDocumentStoreSpec<T> spec, ResourceScope scope)");
        assertThat(text("pom.xml").toLowerCase()).doesNotContain("mongodb", "spring-boot-starter-security");
    }

    @Test void completeProjectExportIsProviderDriven(){
        String service = text("src/main/java/com/example/editor/metamodel/export/project/MetamodelCompleteProjectExportService.java");
        String registry = text("src/main/java/com/example/editor/metamodel/export/project/MetamodelProjectExportProviderRegistry.java");
        assertThat(service).contains("providers.require(kindId)").doesNotContain("switch(kind)", "MetamodelProjectExportKind.fromId(kindId)");
        assertThat(registry).contains("List<MetamodelProjectExportProvider>", "Duplicate complete project export provider");
    }

    @Test void longLivedRuntimeStateIsBounded(){
        assertThat(text("src/main/java/com/example/editor/metamodel/importing/MetamodelImportProperties.java")).contains("analysisMaxRetainedJobs");
        assertThat(text("src/main/java/com/example/editor/event/flow/EventFlowProperties.java")).contains("maximumRuntimeDeployments");
        assertThat(text("src/main/java/com/example/editor/event/flow/runtime/EventFlowRuntimeService.java")).contains("EventFlowRuntimeDeploymentRegistry");
        assertThat(text("src/main/java/com/example/editor/validation/quickfix/QuickFixRuntimeService.java")).contains("BoundedHistoryStore");
        assertThat(text("src/main/java/com/example/editor/operation/runtime/OperationRuntimeService.java")).contains("BoundedHistoryStore");
        for(String path : new String[]{
                "src/main/java/com/example/editor/metamodel/canonical/JsonCanonicalMetamodelRepository.java",
                "src/main/java/com/example/editor/metamodel/target/JsonTargetConfigurationRepository.java",
                "src/main/java/com/example/editor/metamodel/projection/JsonProjectionManifestRepository.java",
                "src/main/java/com/example/editor/validation/persistence/AtomicJsonDocumentStore.java",
                "src/main/java/com/example/editor/validation/expression/persistence/JsonExpressionModelRepository.java"}){
            assertThat(text(path)).contains("StripedKeyLockRegistry").doesNotContain("ConcurrentHashMap<String, ReentrantLock>");
        }
    }

    private String text(String path){try{return Files.readString(Path.of(path));}catch(Exception exception){throw new AssertionError("Could not read " + path, exception);}}
}
