package com.example.editor.metamodel.importing;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import com.example.editor.metamodel.core.TransformationWarning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Comprueba etapas exactas, fallos y logs descargables.
 */
class MetamodelImportAnalysisJobServiceTests {

    @Test
    void startsWithOnlyFileReceivedCompleted() {
        CapturingExecutor executor = new CapturingExecutor();
        var service = service(successfulProcessor(), executor);
        var job = service.start("sample.json", "application/json", new byte[] { 1, 2, 3 }, MetamodelImportOptions.defaults());

        assertThat(job.status()).isEqualTo(MetamodelImportAnalysisStatus.QUEUED);
        assertThat(job.completedStages()).isEqualTo(1);
        assertThat(job.totalStages()).isEqualTo(MetamodelImportAnalysisStage.values().length);
        assertThat(job.progress()).isEqualTo(10);
        assertThat(job.stages().get(0).status()).isEqualTo(MetamodelImportAnalysisStageStatus.COMPLETED);
        assertThat(executor.runnable).isNotNull();
    }

    @Test
    void completesStagesSequentiallyAndReachesOneHundred() {
        CapturingExecutor executor = new CapturingExecutor();
        var service = service(successfulProcessor(), executor);
        var started = service.start("sample.json", "application/json", new byte[] { 1 }, MetamodelImportOptions.defaults());
        executor.run();
        var result = service.get(started.jobId());

        assertThat(result.status()).isEqualTo(MetamodelImportAnalysisStatus.COMPLETED);
        assertThat(result.progress()).isEqualTo(100);
        assertThat(result.completedStages()).isEqualTo(result.totalStages());
        assertThat(result.stages()).extracting(MetamodelImportAnalysisStageResponse::sequence).isSorted();
        assertThat(result.stages()).allMatch(item -> item.status() == MetamodelImportAnalysisStageStatus.COMPLETED || item.status() == MetamodelImportAnalysisStageStatus.SKIPPED);
    }

    @Test
    void usesCompletedStagesInsteadOfArbitraryPercentages() {
        CapturingExecutor executor = new CapturingExecutor();
        MetamodelImportAnalysisProcessor processor = (fileName, mediaType, content, options, listener) -> {
            listener.stageStarted(MetamodelImportAnalysisStage.CONFIGURATION, "Configuration", "start");
            listener.stageCompleted(MetamodelImportAnalysisStage.CONFIGURATION, "Configuration completed", "end");
            listener.stageStarted(MetamodelImportAnalysisStage.FILE_VALIDATION, "Validation", "start");
            return preview(List.of());
        };
        var service = service(processor, executor);
        var started = service.start("sample.json", "application/json", new byte[] { 1 }, MetamodelImportOptions.defaults());
        executor.run();
        var result = service.get(started.jobId());

        assertThat(result.progress()).isEqualTo(100);
        assertThat(result.activities()).extracting(MetamodelImportActivityMessage::progress).allMatch(value -> value >= 0 && value <= 100);
    }

    @Test
    void exposesStageLogAndCombinedLog() {
        CapturingExecutor executor = new CapturingExecutor();
        var service = service(successfulProcessor(), executor);
        var started = service.start("sample.json", "application/json", new byte[] { 1 }, MetamodelImportOptions.defaults());
        executor.run();
        var stageLog = service.getStageLog(started.jobId(), "FORMAT_DETECTION");
        var combined = service.getCombinedLog(started.jobId());

        assertThat(new String(stageLog.content(), StandardCharsets.UTF_8)).contains("Stage 4", "FORMAT_DETECTION", "json");
        assertThat(new String(combined.content(), StandardCharsets.UTF_8)).contains("Metamodel import analysis", "File received", "Format detection");
        assertThat(stageLog.fileName()).endsWith("format_detection.log");
    }

    @Test
    void classifiesInformationLossInsideStage() {
        CapturingExecutor executor = new CapturingExecutor();
        MetamodelImportAnalysisProcessor processor = (fileName, mediaType, content, options, listener) -> {
            listener.stageStarted(MetamodelImportAnalysisStage.PARSING, "Parsing", "");
            listener.log(MetamodelImportAnalysisStage.PARSING, MetamodelImportActivitySeverity.INFORMATION_LOSS, "IMPORT_LOSSY_TEST", "Information was approximated", "path=classes[0]");
            listener.stageCompleted(MetamodelImportAnalysisStage.PARSING, "Parsing completed", "");
            return preview(List.of(new TransformationWarning("IMPORT_LOSSY_TEST", "classes[0]", "Information was approximated")));
        };
        var service = service(processor, executor);
        var started = service.start("sample.puml", "text/plain", new byte[] { 1 }, MetamodelImportOptions.defaults());
        executor.run();
        var result = service.get(started.jobId());
        var parsing = result.stages().stream().filter(item -> item.stageId().equals("PARSING")).findFirst().orElseThrow();

        assertThat(parsing.activities()).anyMatch(item -> item.severity() == MetamodelImportActivitySeverity.INFORMATION_LOSS);
        assertThat(result.activities().stream().filter(item -> item.code().equals("IMPORT_LOSSY_TEST"))).hasSize(1);
    }

    @Test
    void marksCurrentStageAsFailed() {
        CapturingExecutor executor = new CapturingExecutor();
        MetamodelImportAnalysisProcessor processor = (fileName, mediaType, content, options, listener) -> {
            listener.stageStarted(MetamodelImportAnalysisStage.PARSING, "Parsing", "");
            throw new IllegalStateException("Broken grammar");
        };
        var service = service(processor, executor);
        var started = service.start("sample.xtext", "text/plain", new byte[] { 1 }, MetamodelImportOptions.defaults());
        executor.run();
        var result = service.get(started.jobId());
        var parsing = result.stages().stream().filter(item -> item.stageId().equals("PARSING")).findFirst().orElseThrow();

        assertThat(result.status()).isEqualTo(MetamodelImportAnalysisStatus.FAILED);
        assertThat(result.errorMessage()).isEqualTo("Broken grammar");
        assertThat(parsing.status()).isEqualTo(MetamodelImportAnalysisStageStatus.FAILED);
        assertThat(parsing.activities()).anyMatch(item -> item.severity() == MetamodelImportActivitySeverity.ERROR);
    }

    @Test
    void rejectsUnknownJobAndUnknownStage() {
        var service = service(successfulProcessor(), Runnable::run);
        var started = service.start("sample.json", "application/json", new byte[] { 1 }, MetamodelImportOptions.defaults());

        assertThatThrownBy(() -> service.get("missing")).hasMessageContaining("analysis job was not found");
        assertThatThrownBy(() -> service.getStageLog(started.jobId(), "missing")).hasMessageContaining("Unknown metamodel import analysis stage");
    }

    @Test
    void usesIndependentIdentifiersForConcurrentJobs() {
        CapturingExecutor executor = new CapturingExecutor();
        var service = service(successfulProcessor(), executor);
        var first = service.start("first.json", "application/json", new byte[] { 1 }, MetamodelImportOptions.defaults());
        var second = service.start("second.json", "application/json", new byte[] { 1 }, MetamodelImportOptions.defaults());

        assertThat(first.jobId()).isNotEqualTo(second.jobId());
    }

    private static MetamodelImportAnalysisJobService service(MetamodelImportAnalysisProcessor processor, Executor executor) {
        MetamodelImportProperties properties = new MetamodelImportProperties();
        return new MetamodelImportAnalysisJobService(processor, executor, properties, Clock.fixed(Instant.parse("2026-07-23T08:00:00Z"), ZoneOffset.UTC));
    }

    private static MetamodelImportAnalysisProcessor successfulProcessor() {
        return (fileName, mediaType, content, options, listener) -> {
            listener.stageStarted(MetamodelImportAnalysisStage.CONFIGURATION, "Configuration", "format=auto");
            listener.stageCompleted(MetamodelImportAnalysisStage.CONFIGURATION, "Configuration completed", "format=auto");
            listener.stageStarted(MetamodelImportAnalysisStage.FORMAT_DETECTION, "Detecting format", "");
            listener.log(MetamodelImportAnalysisStage.FORMAT_DETECTION, MetamodelImportActivitySeverity.INFO, "IMPORT_FORMAT_DETECTED", "JSON detected", "format=json");
            listener.stageCompleted(MetamodelImportAnalysisStage.FORMAT_DETECTION, "Format detected", "format=json");
            return preview(List.of());
        };
    }

    private static MetamodelImportPreview preview(List<TransformationWarning> warnings) {
        return new MetamodelImportPreview("json", "EXACT", null, null, null, warnings, null);
    }

    private static final class CapturingExecutor implements Executor {

        private Runnable runnable;

        @Override
        public void execute(Runnable command) {
            runnable = command;
        }

        private void run() {
            AtomicReference<Runnable> current = new AtomicReference<>(runnable);
            runnable = null;
            current.get().run();
        }
    }
}
